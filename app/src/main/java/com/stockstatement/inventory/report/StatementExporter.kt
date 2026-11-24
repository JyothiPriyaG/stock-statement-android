package com.stockstatement.inventory.report

import com.stockstatement.inventory.model.DailyStock
import com.stockstatement.inventory.model.PackUnit
import com.stockstatement.inventory.model.PRODUCT_CATEGORIES
import java.time.LocalDate

object StatementExporter {
    fun csv(date: LocalDate, stocks: List<DailyStock>): String = buildString {
        appendLine(csvRow(listOf("STOCK STATEMENT", date.toString())))
        appendLine()
        appendLine(csvRow(groupedHeader()))
        appendLine(csvRow(unitHeader()))

        orderedStocks(stocks).groupBy { it.category }.forEach { (category, rows) ->
            appendLine(csvRow(listOf(category)))
            rows.forEach { stock -> appendLine(csvRow(stockRow(stock))) }
        }

        appendLine(csvRow(totalRow(stocks)))
        appendLine()
        appendLine(
            csvRow(
                listOf(
                    "SALES TOTAL",
                    stocks.sumOf { stock -> PackUnit.entries.sumOf(stock::amountFor) }.toString()
                )
            )
        )
    }

    fun html(date: LocalDate, stocks: List<DailyStock>): String = buildString {
        append(
            """
            <!doctype html>
            <html><head><meta charset="utf-8">
            <style>
              @page { size: A4 landscape; margin: 8mm; }
              body { font-family: sans-serif; font-size: 7pt; color: #222; }
              h1 { font-size: 16pt; margin: 0 0 3px; }
              .date { margin-bottom: 8px; }
              table { width: 100%; border-collapse: collapse; table-layout: fixed; }
              col.product { width: 16%; }
              col.quantity { width: 4.2%; }
              th, td { border: 1px solid #777; padding: 3px 2px; text-align: center; overflow-wrap: anywhere; }
              th { background: #e8eee8; }
              th.product, td.product { text-align: left; }
              .category { background: #dce5dc; font-weight: bold; text-align: left; }
              tfoot { font-weight: bold; }
              thead { display: table-header-group; }
            </style></head><body>
            """.trimIndent()
        )
        append("<h1>STOCK STATEMENT</h1><div class=\"date\">Date: ${escape(date.toString())}</div>")
        append("<table><colgroup><col class=\"product\">")
        repeat(20) { append("<col class=\"quantity\">") }
        append("</colgroup><thead><tr><th class=\"product\" rowspan=\"2\">Product</th>")
        append(
            "<th colspan=\"4\">Opening Balance</th>" +
                "<th colspan=\"4\">Receipts</th>" +
                "<th colspan=\"4\">Closing Balance</th>" +
                "<th colspan=\"4\">Sales</th>" +
                "<th colspan=\"4\">Amount</th></tr><tr>"
        )
        repeat(5) {
            PackUnit.entries.forEach { unit -> append("<th>${unit.label}</th>") }
        }
        append("</tr></thead><tbody>")

        orderedStocks(stocks).groupBy { it.category }.forEach { (category, rows) ->
            append("<tr><td class=\"category\" colspan=\"21\">${escape(category)}</td></tr>")
            rows.forEach { stock ->
                append("<tr><td class=\"product\">${escape(stock.productName)}</td>")
                append(stockRow(stock).drop(1).joinToString("") { value -> "<td>$value</td>" })
                append("</tr>")
            }
        }
        append("</tbody><tfoot><tr><td class=\"product\">TOTAL</td>")
        append(totalRow(stocks).drop(1).joinToString("") { value -> "<td>$value</td>" })
        append(
            "</tr></tfoot></table><h3>SALES TOTAL: " +
                stocks.sumOf { stock -> PackUnit.entries.sumOf(stock::amountFor) } +
                "</h3></body></html>"
        )
    }

    private fun groupedHeader() = buildList {
        add("Product")
        addAll(listOf("Opening Balance", "", "", ""))
        addAll(listOf("Receipts", "", "", ""))
        addAll(listOf("Closing Balance", "", "", ""))
        addAll(listOf("Sales", "", "", ""))
        addAll(listOf("Amount", "", "", ""))
    }

    private fun unitHeader() = buildList {
        add("")
        repeat(5) { PackUnit.entries.forEach { add(it.label) } }
    }

    private fun stockRow(stock: DailyStock) = buildList {
        add(stock.productName)
        PackUnit.entries.forEach { add(stock.opening[it].toString()) }
        PackUnit.entries.forEach { add(stock.receipts[it].toString()) }
        PackUnit.entries.forEach { add(stock.closing[it].toString()) }
        PackUnit.entries.forEach { add(stock.salesFor(it).toString()) }
        PackUnit.entries.forEach { add(stock.amountFor(it).toString()) }
    }

    private fun totalRow(stocks: List<DailyStock>) = buildList {
        add("TOTAL")
        repeat(12) { add("") }
        PackUnit.entries.forEach { unit -> add(stocks.sumOf { it.salesFor(unit) }.toString()) }
        PackUnit.entries.forEach { unit -> add(stocks.sumOf { it.amountFor(unit) }.toString()) }
    }

    private fun orderedStocks(stocks: List<DailyStock>) = stocks.sortedWith(
        compareBy<DailyStock> { categoryOrder(it.category) }
            .thenBy { it.category.lowercase() }
            .thenBy { it.productName.lowercase() }
    )

    private fun categoryOrder(category: String) = PRODUCT_CATEGORIES.indexOfFirst { it.equals(category, true) }
        .let { if (it < 0) PRODUCT_CATEGORIES.size else it }

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
