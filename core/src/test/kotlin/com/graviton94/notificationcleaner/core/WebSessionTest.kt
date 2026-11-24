package com.graviton94.notificationcleaner.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebSessionTest {
    private val t0 = 1_700_000_000_000L
    private fun parse(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test fun snapshotHasTheBoardAndHud() {
        val w = WebSession(state = Engine().newGame(t0, 11), now = t0)
        val s = parse(w.snapshot(t0))
        assertEquals(7, s.getValue("rows").jsonArray.size)
        assertEquals("0", s.getValue("wallet").jsonPrimitive.content)
        assertTrue(s.getValue("deckApps").jsonArray.isNotEmpty())
    }

    @Test fun hintedSwipeReplaysAsBeatsAndRefillsTheBoard() {
        val w = WebSession(state = Engine().newGame(t0, 11), now = t0)
        val id = parse(w.hint(t0)).getValue("id").jsonPrimitive.long
        val o = parse(w.swipe(id, t0 + 1_000))
        assertEquals("true", o.getValue("ok").jsonPrimitive.content)
        val kinds = o.getValue("beats").jsonArray.map { it.jsonObject.getValue("t").jsonPrimitive.content }
        assertEquals("swiped", kinds.first())
        assertTrue("spawned" in kinds)
        assertEquals(7, o.getValue("state").jsonObject.getValue("rows").jsonArray.size)
        // the save round-trips the session
        assertEquals(w.state, SaveCodec.decode(w.save()))
    }

    @Test fun refusedSwipeSaysWhy() {
        val w = WebSession(state = Engine().newGame(t0, 11), now = t0)
        val o = parse(w.swipe(9_999, t0))
        assertEquals("false", o.getValue("ok").jsonPrimitive.content)
        assertEquals("gone", o.getValue("reason").jsonPrimitive.content)
    }
}
