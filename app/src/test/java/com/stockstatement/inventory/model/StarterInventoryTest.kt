package com.stockstatement.inventory.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StarterInventoryTest {
    @Test
    fun starterBalancesComeFromScreenshotDateAndAreEditableProducts() {
        assertEquals(LocalDate.of(2026, 11, 23), StarterInventory.sourceDate)
        assertTrue(StarterInventory.products.size > 40)
        assertTrue(StarterInventory.products.any { it.name == "Blender Pride" })
        assertTrue(StarterInventory.products.any { it.name == "Strong" && it.category == "Beer" })
        assertEquals(UnitValues(q = 32, p = 29, n = 58), StarterInventory.products
            .first { it.name == "Blender Pride" }.closingOnSourceDate)
    }
}
