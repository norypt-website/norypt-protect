package com.norypt.protect.triggers

import com.norypt.protect.admin.Tier
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerRegistryTest {

    @Test
    fun `registry exists and is not null`() {
        assertNotNull(TriggerRegistry.all)
    }

    @Test
    fun `no duplicate trigger IDs in registry`() {
        val ids = TriggerRegistry.all.map { it.id }
        val uniqueIds = ids.toSet()
        assertTrue(
            "Duplicate trigger IDs found: ${ids.groupBy { it }.filter { it.value.size > 1 }.keys}",
            ids.size == uniqueIds.size
        )
    }

    @Test
    fun `every registered trigger requires DeviceAdmin or DeviceOwner tier`() {
        val invalidTiers = TriggerRegistry.all.filter { it.requiredTier == Tier.None }
        assertTrue(
            "Triggers with requiredTier=None: ${invalidTiers.map { it.id }}",
            invalidTiers.isEmpty()
        )
    }
}
