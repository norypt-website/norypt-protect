package com.norypt.protect.timeline

import android.content.Context
import android.telephony.TelephonyManager

/**
 * What can be known about the SIM(s) without READ_PHONE_STATE: whether each slot holds a card,
 * and which carrier the default SIM belongs to. Enough to see a card removed, inserted, or
 * swapped for one from another carrier — the three things a tamper timeline needs.
 */
internal data class SimState(
    /** One of [SimSnapshot.PRESENT], [SimSnapshot.ABSENT], [SimSnapshot.TRANSIENT] per slot. */
    val slots: List<String>,
    /** Carrier name of the default SIM while it is READY; blank otherwise. */
    val operator: String,
    val country: String,
) {
    /** A slot mid-initialisation says nothing yet; a snapshot containing one is not comparable. */
    val transient: Boolean get() = slots.any { it == SimSnapshot.TRANSIENT }
}

internal object SimSnapshot {
    const val PRESENT = "PRESENT"
    const val ABSENT = "ABSENT"
    const val TRANSIENT = "TRANSIENT"

    private const val SECTION_SEP = '|'
    private const val SLOT_SEP = ','

    fun encode(state: SimState): String =
        listOf(state.slots.joinToString(SLOT_SEP.toString()), clean(state.operator), clean(state.country))
            .joinToString(SECTION_SEP.toString())

    fun decode(raw: String?): SimState {
        if (raw.isNullOrEmpty()) return SimState(emptyList(), "", "")
        val sections = raw.split(SECTION_SEP)
        val slots = sections.getOrElse(0) { "" }.split(SLOT_SEP).filter { it.isNotEmpty() }
        return SimState(slots, sections.getOrElse(1) { "" }, sections.getOrElse(2) { "" })
    }

    /** Human description of what changed between two comparable snapshots, or null if nothing did. */
    fun describeChange(before: SimState, after: SimState): String? {
        val parts = mutableListOf<String>()
        val slotCount = maxOf(before.slots.size, after.slots.size)
        for (i in 0 until slotCount) {
            val b = before.slots.getOrNull(i)
            val a = after.slots.getOrNull(i)
            if (b == a) continue
            parts += when (a) {
                ABSENT -> "Slot ${i + 1}: SIM removed"
                PRESENT -> "Slot ${i + 1}: SIM inserted"
                null -> "Slot ${i + 1}: slot no longer reported"
                else -> "Slot ${i + 1}: ${b ?: "none"} → $a"
            }
        }
        val carrierKnownBoth = before.operator.isNotBlank() && after.operator.isNotBlank()
        if (carrierKnownBoth && (before.operator != after.operator || before.country != after.country)) {
            parts += "Carrier changed: ${carrier(before)} → ${carrier(after)}"
        }
        return if (parts.isEmpty()) null else parts.joinToString("; ")
    }

    fun presence(simStateCode: Int): String = when (simStateCode) {
        TelephonyManager.SIM_STATE_ABSENT -> ABSENT
        TelephonyManager.SIM_STATE_UNKNOWN, TelephonyManager.SIM_STATE_NOT_READY -> TRANSIENT
        else -> PRESENT
    }

    /** Current snapshot. Empty slot list on devices without telephony. */
    fun current(ctx: Context): SimState = runCatching {
        val tm = ctx.getSystemService(TelephonyManager::class.java) ?: return SimState(emptyList(), "", "")
        val slots = (0 until tm.activeModemCount).map { presence(tm.getSimState(it)) }
        val ready = tm.simState == TelephonyManager.SIM_STATE_READY
        SimState(
            slots = slots,
            operator = if (ready) tm.simOperatorName.orEmpty() else "",
            country = if (ready) tm.simCountryIso.orEmpty() else "",
        )
    }.getOrDefault(SimState(emptyList(), "", ""))

    private fun carrier(s: SimState): String =
        if (s.country.isBlank()) s.operator else "${s.operator} (${s.country.uppercase()})"

    private fun clean(s: String): String = s.replace(SECTION_SEP, ' ').replace(SLOT_SEP, ' ').trim()
}
