package com.goreecloud.homeassistant

import org.junit.Assert.*
import org.junit.Test

class EndpointRulesTest {
    @Test fun normalizedTrustedHttps() {
        assertEquals("https://home.example.com:8443",
            EndpointRules.normalize(" https://home.example.com:8443/ "))
    }

    @Test fun rejectsCleartextAndEmbeddedCredentials() {
        for (url in listOf("http://home.example.com", "https://user:pass@home.example.com",
            "https://home.example.com/path", "https://home.example.com/?token=secret",
            "https://home.example.com/#fragment", "not-a-url")) {
            assertThrows(IllegalArgumentException::class.java) { EndpointRules.normalize(url) }
        }
    }

    @Test fun onlyAllowsKnownEntityActions() {
        assertEquals("turn_off", EndpointRules.actionFor("light", "on"))
        assertEquals("turn_on", EndpointRules.actionFor("input_boolean", "off"))
        assertEquals("turn_on", EndpointRules.actionFor("scene", "scening"))
        assertNull(EndpointRules.actionFor("lock", "locked"))
        assertNull(EndpointRules.actionFor("light", "unavailable"))
    }

    @Test fun preventsUnexpectedServiceTargets() {
        assertTrue(EndpointRules.safeId("light.living_room", "light"))
        assertFalse(EndpointRules.safeId("lock.front", "lock"))
        assertFalse(EndpointRules.safeId("light.good/../../x", "light"))
        assertFalse(EndpointRules.safeId("switch.living_room", "light"))
    }
}
