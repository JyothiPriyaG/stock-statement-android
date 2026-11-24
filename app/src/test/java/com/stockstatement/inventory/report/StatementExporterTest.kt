package com.stockstatement.inventory.report

import com.stockstatement.inventory.model.DailyStock
import com.stockstatement.inventory.model.ProductPrices
import com.stockstatement.inventory.model.UnitValues
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatementExporterTest {
    @Test
    fun csvQuotesProductNamesAndIncludesCalculatedUnitTotals() {
        val stock = DailyStock(
            productId = 1,
            productName = "Red, \"Reserve\"",
            category = "Wine",
            opening = UnitValues(q = 4),
            receipts = UnitValues(q = 2),
            closing = UnitValues(q = 3),
            prices = ProductPrices(q = 250)
        )

        val csv = StatementExporter.csv(LocalDate.parse("2026-11-24"), listOf(stock))

        assertTrue(csv.contains("\"Red, \"\"Reserve\"\"\""))
        assertTrue(csv.contains("\"Product\",\"Opening Balance\""))
        assertTrue(csv.contains("\"\",\"Q\",\"P\",\"N\",\"D\""))
        assertTrue(csv.contains("\"SALES TOTAL\",\"750\""))
    }

    @Test
    fun printHtmlEscapesProductNames() {
        val stock = DailyStock(
            productId = 1,
            productName = "<script>alert(1)</script>",
            category = "Wine",
            opening = UnitValues(),
            receipts = UnitValues(),
            closing = UnitValues(),
            prices = ProductPrices()
        )

        val html = StatementExporter.html(LocalDate.parse("2026-11-24"), listOf(stock))

        assertTrue(html.contains("&lt;script&gt;"))
        assertTrue(!html.contains("<script>alert"))
    }

    @Test
    fun printGridUsesProductColumnAndCategoryBands() {
        val stock = DailyStock(
            productId = 1,
            productName = "Sample",
            category = "Wine",
            opening = UnitValues(),
            receipts = UnitValues(),
            closing = UnitValues(),
            prices = ProductPrices()
        )

        val html = StatementExporter.html(LocalDate.parse("2026-11-24"), listOf(stock))

        assertTrue(html.contains(">Product</th>"))
        assertTrue(html.contains(">Opening Balance</th>"))
        assertTrue(html.contains(">Closing Balance</th>"))
        assertTrue(html.contains("class=\"category\" colspan=\"21\">Wine</td>"))
        assertFalse(html.contains(">Category</th>"))
        assertFalse(html.contains(">Brand</th>"))
    }
}
