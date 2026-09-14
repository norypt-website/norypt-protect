package com.norypt.protect.timeline

import android.telephony.TelephonyManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimSnapshotTest {

    private val ready = SimState(listOf(SimSnapshot.PRESENT), "Vodafone UK", "gb")

    @Test
    fun `encode and decode round-trip`() {
        assertEquals(ready, SimSnapshot.decode(SimSnapshot.encode(ready)))
        val dual = SimState(listOf(SimSnapshot.PRESENT, SimSnapshot.ABSENT), "Orange", "fr")
        assertEquals(dual, SimSnapshot.decode(SimSnapshot.encode(dual)))
    }

    @Test
    fun `decoding nothing yields an empty state`() {
        val empty = SimSnapshot.decode(null)
        assertTrue(empty.slots.isEmpty())
        assertEquals("", empty.operator)
    }

    @Test
    fun `identical snapshots describe no change`() {
        assertNull(SimSnapshot.describeChange(ready, ready.copy()))
    }

    @Test
    fun `a removed card is reported`() {
        val after = SimState(listOf(SimSnapshot.ABSENT), "", "")
        assertEquals("Slot 1: SIM removed", SimSnapshot.describeChange(ready, after))
    }

    @Test
    fun `an inserted card is reported`() {
        val before = SimState(listOf(SimSnapshot.ABSENT), "", "")
        assertEquals("Slot 1: SIM inserted", SimSnapshot.describeChange(before, ready))
    }

    @Test
    fun `a swap between carriers is reported even though both cards read present`() {
        val after = SimState(listOf(SimSnapshot.PRESENT), "Orange", "fr")
        assertEquals("Carrier changed: Vodafone UK (GB) → Orange (FR)", SimSnapshot.describeChange(ready, after))
    }

    @Test
    fun `a SIM PIN prompt at boot is not a carrier change`() {
        // Locked card: present, but the operator is unknown until the PIN is entered.
        val locked = SimState(listOf(SimSnapshot.PRESENT), "", "")
        assertNull(SimSnapshot.describeChange(ready, locked))
        assertNull(SimSnapshot.describeChange(locked, ready))
    }

    @Test
    fun `second slot changes are attributed to the right slot`() {
        val before = SimState(listOf(SimSnapshot.PRESENT, SimSnapshot.ABSENT), "Vodafone UK", "gb")
        val after = SimState(listOf(SimSnapshot.PRESENT, SimSnapshot.PRESENT), "Vodafone UK", "gb")
        assertEquals("Slot 2: SIM inserted", SimSnapshot.describeChange(before, after))
    }

    @Test
    fun `a snapshot with an initialising slot is flagged transient`() {
        assertTrue(SimState(listOf(SimSnapshot.PRESENT, SimSnapshot.TRANSIENT), "", "").transient)
        assertFalse(ready.transient)
    }

    @Test
    fun `presence maps platform states to three classes`() {
        assertEquals(SimSnapshot.ABSENT, SimSnapshot.presence(TelephonyManager.SIM_STATE_ABSENT))
        assertEquals(SimSnapshot.TRANSIENT, SimSnapshot.presence(TelephonyManager.SIM_STATE_NOT_READY))
        assertEquals(SimSnapshot.TRANSIENT, SimSnapshot.presence(TelephonyManager.SIM_STATE_UNKNOWN))
        assertEquals(SimSnapshot.PRESENT, SimSnapshot.presence(TelephonyManager.SIM_STATE_READY))
        assertEquals(SimSnapshot.PRESENT, SimSnapshot.presence(TelephonyManager.SIM_STATE_PIN_REQUIRED))
    }
}
