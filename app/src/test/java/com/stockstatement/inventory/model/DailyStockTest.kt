package com.stockstatement.inventory.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DailyStockTest {
    @Test
    fun salesAndAmountAreCalculatedSeparatelyForEachUnit() {
        val stock = DailyStock(
            productId = 1,
            productName = "Sample",
            category = "Wine",
            opening = UnitValues(q = 10, p = 8, n = 6, d = 4),
            receipts = UnitValues(q = 3, p = 2, n = 1, d = 5),
            closing = UnitValues(q = 9, p = 4, n = 5, d = 3),
            prices = ProductPrices(q = 100, p = 50, n = 20, d = 10)
        )

        assertEquals(UnitValues(q = 4, p = 6, n = 2, d = 6), stock.sales)
        assertEquals(400L, stock.amountFor(PackUnit.Q))
        assertEquals(300L, stock.amountFor(PackUnit.P))
        assertEquals(40L, stock.amountFor(PackUnit.N))
        assertEquals(60L, stock.amountFor(PackUnit.D))
    }

    @Test
    fun negativeSalesRemainVisibleWhenClosingExceedsAvailableStock() {
        val stock = DailyStock(
            productId = 1,
            productName = "Sample",
            category = "Wine",
            opening = UnitValues(q = 1),
            receipts = UnitValues(),
            closing = UnitValues(q = 2),
            prices = ProductPrices(q = 100)
        )

        assertEquals(-1L, stock.salesFor(PackUnit.Q))
        assertEquals(-100L, stock.amountFor(PackUnit.Q))
    }
}
