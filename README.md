# GoreeCloud Home Assistant

**Development foundation, not released or deployed.**

I am building a privacy-first, genuinely native Android client for Home Assistant, followed by an independent web app. The backend is the upstream Home Assistant Container image, not a GoreeCloud-authored automation engine.

Initial source includes Kotlin/Jetpack Compose UI, manual HTTPS and long-lived-token onboarding, Keystore-encrypted local credentials, authenticated entity state reads, allowlisted light/switch/input_boolean toggles and scene activation, Android build CI, and a VPS Compose blueprint. These features require build, device, and deployment verification.

## Build

Requires JDK 17, Android SDK 36, and Gradle 8.13.

    gradle :app:testDebugUnitTest :app:assembleDebug

The repository does not contain a Gradle wrapper JAR. CI provisions Gradle separately. A CI debug APK is **not** a release; GoreeCloud APK delivery requires uploading a standalone verified installer to GoreeCloud/Artifacts on Google Drive.

## Safety and limitations

Use a valid, trusted HTTPS private-network or reviewed TLS gateway endpoint. Never publicly expose TCP 8123 without a separate security review. Manually provisioned long-lived tokens are a **development bridge**; Home Assistant's OAuth/IndieAuth flow is required for production onboarding. State snapshots refresh on demand; there is no real-time WebSocket, home LAN discovery from the VPS, runtime-verified APK, or deployed backend yet.

See [architecture and deployment](docs/ARCHITECTURE.md), [VPS Compose blueprint](deploy/compose.yaml), [GC UI](https://github.com/GoreeCloud/gc-ui), and [Home Assistant REST API](https://developers.home-assistant.io/docs/api/rest/).
