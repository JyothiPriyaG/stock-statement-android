package com.stockstatement.inventory.model

import java.time.LocalDate

data class StarterProduct(
    val name: String,
    val category: String,
    val closingOnSourceDate: UnitValues,
    val prices: ProductPrices = ProductPrices()
)

object StarterInventory {
    val sourceDate: LocalDate = LocalDate.of(2026, 11, 23)

    val products = listOf(
        StarterProduct("100 Pipers", "Whisky", UnitValues(q = 9, p = 1, n = 38)),
        StarterProduct("Blender Pride", "Whisky", UnitValues(q = 32, p = 29, n = 58), ProductPrices(q = 1350, p = 690, n = 370)),
        StarterProduct("Mc Sing Ultra", "Whisky", UnitValues(q = 16, p = 29, n = 39)),
        StarterProduct("Royal Challenge", "Whisky", UnitValues(q = 29, p = 13, n = 29)),
        StarterProduct("Anti-Quityblue", "Whisky", UnitValues(p = 31, n = 49)),
        StarterProduct("Royal Stag", "Whisky", UnitValues(q = 36, p = 10, n = 430), ProductPrices(q = 520, n = 240)),
        StarterProduct("B 10 Reserve", "Whisky", UnitValues()),
        StarterProduct("B 7", "Whisky", UnitValues(q = 21, p = 28, n = 43), ProductPrices(q = 740, n = 210)),
        StarterProduct("I Conic", "Whisky", UnitValues(q = 24, p = 24, n = 200)),
        StarterProduct("After Dark", "Whisky", UnitValues()),
        StarterProduct("M.C. Whisky", "Whisky", UnitValues(q = 45, p = 130, n = 520), ProductPrices(p = 390, n = 210)),
        StarterProduct("Imperial Blue", "Whisky", UnitValues(q = 23, p = 88, n = 400), ProductPrices(q = 740, p = 390, n = 180)),
        StarterProduct("A/c Primium", "Whisky", UnitValues(q = 17, p = 10)),
        StarterProduct("O C Whisky", "Whisky", UnitValues(q = 12, p = 50, n = 320)),
        StarterProduct("A/c Black", "Whisky", UnitValues(n = 440)),
        StarterProduct("8 PM Whisky", "Whisky", UnitValues(q = 20, p = 53, n = 48)),
        StarterProduct("Mangeera", "Whisky", UnitValues(n = 830)),
        StarterProduct("Silver Strips", "Whisky", UnitValues(n = 820)),
        StarterProduct("Hyderabad", "Whisky", UnitValues(n = 240)),
        StarterProduct("Cheep", "Whisky", UnitValues(n = 500)),
        StarterProduct("Manson House", "Brandy", UnitValues(q = 54, p = 125, n = 558), ProductPrices(p = 400, n = 220)),
        StarterProduct("Morph", "Brandy", UnitValues(q = 12, p = 29, n = 93)),
        StarterProduct("Kyron", "Brandy", UnitValues(q = 12, p = 3, n = 34)),
        StarterProduct("Bols", "Brandy", UnitValues(q = 8, p = 23, n = 42)),
        StarterProduct("C.N.B. Green", "Brandy", UnitValues(q = 10, n = 17)),
        StarterProduct("C.N.B. Red", "Brandy", UnitValues(q = 13, p = 20, n = 45)),
        StarterProduct("Old Admiral", "Brandy", UnitValues(q = 30, n = 2000)),
        StarterProduct("M.C. Brandy", "Brandy", UnitValues(q = 25)),
        StarterProduct("White Hall Brandy", "Brandy", UnitValues()),
        StarterProduct("Black & Gold", "Brandy", UnitValues(q = 3, p = 20, n = 58), ProductPrices(q = 740)),
        StarterProduct("Bacardi White", "Rum", UnitValues()),
        StarterProduct("Bacardi Lemon", "Rum", UnitValues()),
        StarterProduct("O.M. Rum", "Rum", UnitValues(n = 53)),
        StarterProduct("M.C. Rum", "Rum", UnitValues(q = 20, n = 41)),
        StarterProduct("Smirnoff", "Vodka", UnitValues(q = 14, n = 68)),
        StarterProduct("M.M. Green", "Vodka", UnitValues(q = 14, p = 24, n = 56), ProductPrices(q = 900)),
        StarterProduct("M.M. Orange", "Vodka", UnitValues(q = 24, p = 13, n = 50), ProductPrices(p = 470, n = 260)),
        StarterProduct("Prince Port", "Wine", UnitValues()),
        StarterProduct("Siddu", "Wine", UnitValues(q = 3, n = 48)),
        StarterProduct("Kyra", "Wine", UnitValues(q = 12, n = 49)),
        StarterProduct("King Fisher", "Beer", UnitValues()),
        StarterProduct("Strong", "Beer", UnitValues(q = 1240), ProductPrices(q = 210)),
        StarterProduct("K.F. Legar", "Beer", UnitValues(q = 220)),
        StarterProduct("K.F. Ultra", "Beer", UnitValues(q = 105), ProductPrices(q = 230)),
        StarterProduct("Storm", "Beer", UnitValues(q = 70), ProductPrices(q = 230)),
        StarterProduct("Budweiser", "Beer", UnitValues(q = 24), ProductPrices(q = 280)),
        StarterProduct("Magnum", "Beer", UnitValues(q = 21), ProductPrices(q = 310)),
        StarterProduct("Knock Out", "Beer", UnitValues()),
        StarterProduct("Block Buster", "Beer", UnitValues()),
        StarterProduct("Karjura", "Beer", UnitValues(q = 294), ProductPrices(q = 190)),
        StarterProduct("Brezer", "Beer", UnitValues(n = 165), ProductPrices(n = 140)),
        StarterProduct("Bacardi +", "Beer", UnitValues(n = 50), ProductPrices(n = 160)),
        StarterProduct("Martin", "Beer", UnitValues(q = 11), ProductPrices(q = 180)),
        StarterProduct("Skeleton", "Beer", UnitValues(q = 158), ProductPrices(q = 160))
    )
}
