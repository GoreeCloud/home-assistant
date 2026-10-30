package com.goreecloud.homeassistant

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HaSocketWireTest {
    @Test fun authMessageContainsOnlyExpectedFields() {
        val msg = JSONObject(HaSocketWire.auth("synthetic-test-token"))
        assertEquals("auth", msg.getString("type"))
        assertEquals("synthetic-test-token", msg.getString("access_token"))
        assertEquals(2, msg.length())
    }

    @Test fun requestsEntitySubscription() {
        val msg = JSONObject(HaSocketWire.subscribe())
        assertEquals("subscribe_entities", msg.getString("type"))
        assertEquals(1, msg.getInt("id"))
    }

    @Test fun handlesEntityEventsButNotUnrelatedFrames() {
        assertTrue(HaSocketWire.entityEvent(HaSocketWire.parse(
            """{"type":"event","id":1,"event":{"c":{"light.office":{"+":{"s":"on"}}}}}"""
        )))
        assertTrue(HaSocketWire.entityEvent(HaSocketWire.parse(
            """{"type":"event","id":1,"event":{"a":{"sensor.temp":{"s":"23"}}}}"""
        )))
        assertTrue(HaSocketWire.entityEvent(HaSocketWire.parse(
            """{"type":"event","id":1,"event":{"r":["light.removed"]}}"""
        )))
        assertFalse(HaSocketWire.entityEvent(HaSocketWire.parse(
            """{"type":"result","id":1,"success":true}"""
        )))
        assertFalse(HaSocketWire.entityEvent(HaSocketWire.parse(
            """{"type":"event","id":9,"event":{"c":{}}}"""
        )))
    }

    @Test fun rejectsOverlargeFrames() {
        assertThrows(IllegalArgumentException::class.java) {
            HaSocketWire.parse("x".repeat(HaSocketWire.maxFrameCharacters + 1))
        }
    }
}
