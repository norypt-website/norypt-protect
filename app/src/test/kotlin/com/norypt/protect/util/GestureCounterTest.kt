package com.norypt.protect.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureCounterTest {

    @Test
    fun `five events within window fires true`() {
        val counter = GestureCounter(threshold = 5, windowMs = 3000)
        val now = 1_000_000L
        assertFalse(counter.onEvent(now))
        assertFalse(counter.onEvent(now + 500))
        assertFalse(counter.onEvent(now + 1000))
        assertFalse(counter.onEvent(now + 1500))
        assertTrue(counter.onEvent(now + 2000))
    }

    @Test
    fun `five events spread beyond window does not fire`() {
        val counter = GestureCounter(threshold = 5, windowMs = 3000)
        val now = 1_000_000L
        counter.onEvent(now)
        counter.onEvent(now + 1000)
        counter.onEvent(now + 2000)
        counter.onEvent(now + 3000)
        // Fifth event is 3001 ms after first — first is evicted
        val result = counter.onEvent(now + 3001)
        assertFalse(result)
    }

    @Test
    fun `reset clears state so subsequent events start fresh`() {
        val counter = GestureCounter(threshold = 3, windowMs = 5000)
        val now = 1_000_000L
        counter.onEvent(now)
        counter.onEvent(now + 100)
        counter.reset()
        // After reset, two more events should NOT fire (need 3)
        assertFalse(counter.onEvent(now + 200))
        assertFalse(counter.onEvent(now + 300))
    }

    @Test
    fun `threshold of 1 fires on first event`() {
        val counter = GestureCounter(threshold = 1, windowMs = 3000)
        assertTrue(counter.onEvent(System.currentTimeMillis()))
    }

    @Test
    fun `constructor params are honoured`() {
        val counter = GestureCounter(threshold = 2, windowMs = 500)
        val now = 1_000_000L
        assertFalse(counter.onEvent(now))
        // Second event within 500 ms window
        assertTrue(counter.onEvent(now + 400))
        counter.reset()
        // Second event outside 500 ms window
        counter.onEvent(now + 1000)
        assertFalse(counter.onEvent(now + 1501))
    }
}
