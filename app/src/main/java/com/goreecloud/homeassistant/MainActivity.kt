package com.goreecloud.homeassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.launch

private val iris = Color(0xFF6053CD)
private val aqua = Color(0xFF167F83)
private val lightScheme = lightColorScheme(
    primary = iris, secondary = aqua,
    background = Color(0xFFF1F2FA),
    surface = Color(0xFFFAFAFF),
    surfaceVariant = Color(0xFFE4E6F3),
    onBackground = Color(0xFF202536)
)
private val darkScheme = darkColorScheme(
    primary = Color(0xFFBDB4FF), secondary = Color(0xFF81D7D6),
    background = Color(0xFF111423),
    surface = Color(0xFF1B2031),
    surfaceVariant = Color(0xFF303449)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = SecretStore(this)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkScheme else lightScheme) {
                GoreeAssistant(store)
            }
        }
    }
}

@Composable
private fun GoreeAssistant(store: SecretStore) {
    val client = remember { HaClient() }
    val scope = rememberCoroutineScope()
    var storeError by remember { mutableStateOf<String?>(null) }
    var connection by remember {
        mutableStateOf(runCatching { store.load() }.getOrElse {
            storeError = "Secure storage needs a new connection."
            null
        })
    }
    var settingUp by remember { mutableStateOf(connection == null) }
    var entities by remember { mutableStateOf<List<HaEntity>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var liveConnected by remember { mutableStateOf(false) }
    var liveAuthRejected by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var foreground by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    suspend fun refresh(config: HaConnection) {
        busy = true
        connected = false
        try {
            entities = client.entities(config)
            connected = true
            message = null
        } catch (e: Exception) {
            message = e.message ?: "Could not reach Home Assistant."
        } finally {
            busy = false
        }
    }

    LaunchedEffect(connection, settingUp, foreground) {
        val config = connection
        liveConnected = false
        if (config != null && !settingUp && foreground) {
            if (entities.isEmpty()) refresh(config)
            val active = AtomicBoolean(true)
            val pending = Channel<Unit>(Channel.CONFLATED)
            try {
                coroutineScope {
                    launch {
                        HaLiveFeed().observe(
                            connection = config,
                            onSubscribed = {
                                if (active.get()) {
                                    scope.launch { if (active.get()) liveConnected = true }
                                    pending.trySend(Unit) // refresh after each successful reconnect
                                }
                            },
                            onEntityChange = { if (active.get()) pending.trySend(Unit) },
                            onDisconnected = {
                                if (active.get()) scope.launch { if (active.get()) liveConnected = false }
                            },
                            onAuthenticationRejected = {
                                if (active.get()) scope.launch {
                                    if (active.get()) {
                                        connected = false
                                        liveAuthRejected = true
                                        message = "Home Assistant rejected this login. Change your connection."
                                    }
                                }
                            }
                        )
                    }
                    launch {
                        for (change in pending) {
                            delay(700) // debounce bursts; REST verifies the authoritative state
                            while (busy && isActive) delay(200)
                            if (isActive && !liveAuthRejected) refresh(config)
                        }
                    }
                }
            } finally {
                active.set(false)
                pending.close()
                liveConnected = false
            }
        }
    }

    if (settingUp) {
        SetupScreen(
            initialUrl = connection?.baseUrl.orEmpty(),
            busy = busy,
            error = message ?: storeError,
            canCancel = connection != null,
            onCancel = { settingUp = false; message = null },
            onConnect = { url, token ->
                scope.launch {
                    busy = true
                    message = null
                    try {
                        val normalized = EndpointRules.normalize(url)
                        require(token.isNotBlank()) { "Enter a Home Assistant access token." }
                        val config = HaConnection(normalized, token.trim())
                        val live = client.entities(config) // prove access before storing
                        store.save(config)
                        entities = live
                        connection = config
                        connected = true
                        liveAuthRejected = false
                        settingUp = false
                        storeError = null
                    } catch (e: Exception) {
                        connected = false
                        message = e.message ?: "Connection failed."
                    } finally {
                        busy = false
                    }
                }
            }
        )
    } else {
        Dashboard(
            entities = entities,
            busy = busy,
            connected = connected,
            liveConnected = liveConnected,
            message = message,
            onRefresh = {
                connection?.let { c -> scope.launch { refresh(c) } }
            },
            onControl = { entity ->
                val config = connection
                if (config != null && connected && !busy) {
                    scope.launch {
                        busy = true
                        connected = false
                        try {
                            client.activate(config, entity)
                            // Command accepted is not proof the entity switched.
                            entities = client.entities(config)
                            message = null
                            connected = true
                        } catch (e: Exception) {
                            message = e.message ?: "Could not confirm the device state."
                        } finally {
                            busy = false
                        }
                    }
                }
            },
            onChange = { settingUp = true; liveAuthRejected = false; message = null },
            onDisconnect = {
                try {
                    liveAuthRejected = false
                    store.clear()
                    connection = null
                    entities = emptyList()
                    connected = false
                    settingUp = true
                    message = null
                } catch (_: Exception) {
                    message = "Failed to clear encrypted connection."
                }
            }
        )
    }
}

@Composable
private fun SetupScreen(
    initialUrl: String,
    busy: Boolean,
    error: String?,
    canCancel: Boolean,
    onCancel: () -> Unit,
    onConnect: (String, String) -> Unit
) {
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    var token by remember { mutableStateOf("") }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Text("GoreeCloud", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                Text("Your home, in hand.", fontSize = 32.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground)
                Text("Connect securely to your own Home Assistant instance.",
                    style = MaterialTheme.typography.bodyLarge)
            }
            item {
                ElevatedCard(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Connect your home", style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = url, onValueChange = { url = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("HTTPS server address") },
                            placeholder = { Text("https://home.example.com") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            singleLine = true,
                            enabled = !busy
                        )
                        OutlinedTextField(
                            value = token, onValueChange = { token = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Home Assistant access token") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true, enabled = !busy
                        )
                        Button(
                            onClick = { onConnect(url, token) },
                            enabled = !busy && url.isNotBlank() && token.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) { Text(if (busy) "Verifying connection…" else "Connect securely") }
                        if (canCancel) TextButton(onClick = onCancel) { Text("Cancel") }
                    }
                }
            }
            if (error != null) item {
                Text(error, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium)
            }
            item {
                Text("Private by design", style = MaterialTheme.typography.titleMedium)
                Text("Requires a valid HTTPS certificate. The token is stored with Android Keystore encryption; it is never transmitted to GoreeCloud. Manual long-lived tokens are for development until OAuth sign-in is implemented.",
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Dashboard(
    entities: List<HaEntity>,
    busy: Boolean,
    connected: Boolean,
    liveConnected: Boolean,
    message: String?,
    onRefresh: () -> Unit,
    onControl: (HaEntity) -> Unit,
    onChange: () -> Unit,
    onDisconnect: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    val labels = listOf("Home", "Devices", "Scenes", "Settings")
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                labels.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Text(listOf("⌂", "▦", "✦", "⚙")[index], fontSize = 22.sp) },
                        label = { Text(label) },
                        alwaysShowLabel = true
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("GoreeCloud", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                        Text(labels[tab], style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold)
                    }
                    if (tab != 3) OutlinedButton(onClick = onRefresh, enabled = !busy) {
                        Text(if (busy) "Loading…" else "Refresh")
                    }
                }
            }
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (connected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (connected) "●" else "○",
                            color = if (connected) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Text(if (connected && liveConnected) "Connected · live updates active"
                            else if (connected) "Connected · manual refresh / live reconnecting"
                            else "Offline or unverified · controls disabled",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (message != null) item {
                Text(message, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium)
            }
            if (tab == 0) {
                item {
                    ElevatedCard(shape = RoundedCornerShape(26.dp)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp)) {
                            Text("At a glance", style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.SpaceEvenly,
                                modifier = Modifier.fillMaxWidth()) {
                                Metric("${entities.count { it.domain == "light" && it.state == "on" }}",
                                    "Lights on")
                                Metric("${entities.count { it.domain == "switch" && it.state == "on" }}",
                                    "Switches on")
                                Metric("${entities.count { it.domain == "sensor" }}", "Sensors")
                            }
                        }
                    }
                }
                item { SectionTitle("Quick controls") }
            } else if (tab == 1) {
                item { SectionTitle("All visible entities") }
            } else if (tab == 2) {
                item { SectionTitle("Scenes · tap to activate") }
            } else {
                item {
                    ElevatedCard(shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Connection", style = MaterialTheme.typography.titleLarge)
                            Text("Change your secure Home Assistant endpoint or disconnect.")
                            Button(onClick = onChange, modifier = Modifier.fillMaxWidth()) {
                                Text("Change connection")
                            }
                            OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                                Text("Disconnect this device")
                            }
                            Text("No telemetry or cloud sync is included. Local credentials are encrypted and never backed up.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (tab != 3) {
                val visible = when (tab) {
                    0 -> entities.filter { EndpointRules.actionFor(it.domain, it.state) != null }.take(8)
                    1 -> entities.filter { it.domain != "scene" }
                    else -> entities.filter { it.domain == "scene" }
                }
                if (visible.isEmpty()) item {
                    Text(if (busy) "Loading devices…" else "No entities are available in this view.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(8.dp))
                }
                items(visible, key = { it.id }) { entity ->
                    EntityCard(entity, busy, connected, onControl)
                }
            }
        }
    }
}

@Composable
private fun Metric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun EntityCard(
    entity: HaEntity,
    busy: Boolean,
    connected: Boolean,
    onControl: (HaEntity) -> Unit
) {
    val action = EndpointRules.actionFor(entity.domain, entity.state)
    ElevatedCard(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer) {
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    Text(when (entity.domain) {
                        "light" -> "☼"
                        "switch" -> "⏻"
                        "sensor" -> "◷"
                        "binary_sensor" -> "◉"
                        "climate" -> "◌"
                        "scene" -> "✦"
                        else -> "◆"
                    }, fontSize = 25.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(entity.name, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text(entity.state + (if (entity.unit.isBlank()) "" else " " + entity.unit),
                    style = MaterialTheme.typography.bodyMedium)
            }
            if (action != null) {
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = connected && !busy && EndpointRules.safeId(entity.id, entity.domain),
                    onClick = { onControl(entity) },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(if (entity.domain == "scene") "Activate"
                        else if (action == "turn_off") "Turn off" else "Turn on")
                }
            }
        }
    }
}
