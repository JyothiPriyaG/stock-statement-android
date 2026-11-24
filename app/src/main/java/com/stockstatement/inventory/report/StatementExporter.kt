package com.stockstatement.inventory.report

import com.stockstatement.inventory.model.DailyStock
import com.stockstatement.inventory.model.PackUnit
import com.stockstatement.inventory.model.PRODUCT_CATEGORIES
import java.time.LocalDate

object StatementExporter {
    private val orderedCategories = PRODUCT_CATEGORIES

    fun csv(date: LocalDate, stocks: List<DailyStock>): String = buildString {
        appendLine(csvRow(listOf("STOCK STATEMENT", date.toString())))
        appendLine()
        appendLine(csvRow(buildList {
            add("Category")
            add("Brand")
            PackUnit.entries.forEach { unit ->
                addAll(listOf("${unit.label} OB", "${unit.label} Receipts", "${unit.label} CB", "${unit.label} Sales", "${unit.label} Amount"))
            }
        }))

        orderedStocks(stocks).forEach { stock ->
            appendLine(csvRow(buildList {
                add(stock.category)
                add(stock.productName)
                PackUnit.entries.forEach { unit ->
                    add(stock.opening[unit].toString())
                    add(stock.receipts[unit].toString())
                    add(stock.closing[unit].toString())
                    add(stock.salesFor(unit).toString())
                    add(stock.amountFor(unit).toString())
                }
            }))
        }
        appendLine(csvRow(buildList {
            add("")
            add("TOTAL")
            PackUnit.entries.forEach { unit ->
                add("")
                add("")
                add("")
                add(stocks.sumOf { it.salesFor(unit) }.toString())
                add(stocks.sumOf { it.amountFor(unit) }.toString())
            }
        }))
        appendLine()
        appendLine(csvRow(listOf("SALES TOTAL", stocks.sumOf { stock -> PackUnit.entries.sumOf(stock::amountFor) }.toString())))
    }

    fun html(date: LocalDate, stocks: List<DailyStock>): String = buildString {
        append(
            """
            <!doctype html>
            <html><head><meta charset="utf-8">
            <style>
              @page { size: A4 landscape; margin: 10mm; }
              body { font-family: sans-serif; font-size: 8pt; color: #222; }
              h1 { font-size: 17pt; margin: 0 0 4px; }
              .date { margin-bottom: 12px; }
              table { width: 100%; border-collapse: collapse; table-layout: fixed; }
              th, td { border: 1px solid #777; padding: 3px 2px; text-align: right; overflow-wrap: anywhere; }
              th { background: #e8eee8; }
              th:first-child, th:nth-child(2), td:first-child, td:nth-child(2) { text-align: left; }
              .category { background: #dce5dc; font-weight: bold; text-align: left; }
              tfoot { font-weight: bold; }
            </style></head><body>
            """.trimIndent()
        )
        append("<h1>STOCK STATEMENT</h1><div class=\"date\">Date: ${escape(date.toString())}</div>")
        append("<table><colgroup><col style=\"width:8%\"><col style=\"width:16%\">")
        repeat(20) { append("<col style=\"width:3.8%\">") }
        append("</colgroup><thead><tr><th rowspan=\"2\">Category</th><th rowspan=\"2\">Brand</th>")
        listOf("O.B.", "Receipts", "C.B.", "Sales", "Amount").forEach { group ->
            append("<th colspan=\"4\">$group</th>")
        }
        append("</tr><tr>")
        repeat(5) { PackUnit.entries.forEach { unit -> append("<th>${unit.label}</th>") } }
        append("</tr></thead><tbody>")

        orderedStocks(stocks).groupBy { it.category }.forEach { (category, rows) ->
            append("<tr><td class=\"category\" colspan=\"22\">${escape(category)}</td></tr>")
            rows.forEach { stock ->
                append("<tr><td>${escape(stock.category)}</td><td>${escape(stock.productName)}</td>")
                PackUnit.entries.forEach { unit -> append("<td>${stock.opening[unit]}</td>") }
                PackUnit.entries.forEach { unit -> append("<td>${stock.receipts[unit]}</td>") }
                PackUnit.entries.forEach { unit -> append("<td>${stock.closing[unit]}</td>") }
                PackUnit.entries.forEach { unit -> append("<td>${stock.salesFor(unit)}</td>") }
                PackUnit.entries.forEach { unit -> append("<td>${stock.amountFor(unit)}</td>") }
                append("</tr>")
            }
        }
        append("</tbody><tfoot><tr><td colspan=\"2\">TOTAL</td>")
        PackUnit.entries.forEach { append("<td></td>") }
        PackUnit.entries.forEach { append("<td></td>") }
        PackUnit.entries.forEach { append("<td></td>") }
        PackUnit.entries.forEach { unit -> append("<td>${stocks.sumOf { it.salesFor(unit) }}</td>") }
        PackUnit.entries.forEach { unit -> append("<td>${stocks.sumOf { it.amountFor(unit) }}</td>") }
        append(
            "</tr></tfoot></table><h3>SALES TOTAL: " +
                stocks.sumOf { stock -> PackUnit.entries.sumOf(stock::amountFor) } +
                "</h3></body></html>"
        )
    }

    private fun orderedStocks(stocks: List<DailyStock>) = stocks.sortedWith(
        compareBy<DailyStock> { categoryOrder(it.category) }
            .thenBy { it.category.lowercase() }
            .thenBy { it.productName.lowercase() }
    )

    private fun categoryOrder(category: String) = orderedCategories.indexOfFirst { it.equals(category, true) }
        .let { if (it < 0) orderedCategories.size else it }

    private fun csvRow(values: List<String>) = values.joinToString(",") { value ->
        "\"${value.replace("\"", "\"\"")}\""
    }

    private fun escape(value: String) = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
