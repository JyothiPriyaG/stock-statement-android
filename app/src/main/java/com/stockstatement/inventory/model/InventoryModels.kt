package com.stockstatement.inventory.model

import java.time.LocalDate

enum class PackUnit(val label: String) {
    Q("Q"),
    P("P"),
    N("N"),
    D("D")
}

data class UnitValues(
    val q: Long = 0,
    val p: Long = 0,
    val n: Long = 0,
    val d: Long = 0
) {
    operator fun get(unit: PackUnit): Long = when (unit) {
        PackUnit.Q -> q
        PackUnit.P -> p
        PackUnit.N -> n
        PackUnit.D -> d
    }

    fun with(unit: PackUnit, value: Long) = when (unit) {
        PackUnit.Q -> copy(q = value)
        PackUnit.P -> copy(p = value)
        PackUnit.N -> copy(n = value)
        PackUnit.D -> copy(d = value)
    }
}

data class ProductPrices(
    val q: Long = 0,
    val p: Long = 0,
    val n: Long = 0,
    val d: Long = 0
) {
    operator fun get(unit: PackUnit): Long = when (unit) {
        PackUnit.Q -> q
        PackUnit.P -> p
        PackUnit.N -> n
        PackUnit.D -> d
    }
}

data class Product(
    val id: Long = 0,
    val name: String,
    val category: String,
    val prices: ProductPrices
)

data class DailyStock(
    val productId: Long,
    val productName: String,
    val category: String,
    val opening: UnitValues,
    val receipts: UnitValues,
    val closing: UnitValues,
    val prices: ProductPrices
) {
    val sales: UnitValues
        get() = UnitValues(
            q = salesFor(PackUnit.Q),
            p = salesFor(PackUnit.P),
            n = salesFor(PackUnit.N),
            d = salesFor(PackUnit.D)
        )

    fun salesFor(unit: PackUnit): Long =
        opening[unit] + receipts[unit] - closing[unit]

    fun amountFor(unit: PackUnit): Long = salesFor(unit) * prices[unit]

    fun withOpening(unit: PackUnit, value: Long) = copy(opening = opening.with(unit, value))
    fun withReceipts(unit: PackUnit, value: Long) = copy(receipts = receipts.with(unit, value))
    fun withClosing(unit: PackUnit, value: Long) = copy(closing = closing.with(unit, value))
}

data class DatedStock(val date: LocalDate, val stock: DailyStock)

val PRODUCT_CATEGORIES = listOf("Whisky", "Brandy", "Rum", "Vodka", "Wine", "Beer", "Other")
