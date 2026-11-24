package com.stockstatement.inventory.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.stockstatement.inventory.model.DailyStock
import com.stockstatement.inventory.model.Product
import com.stockstatement.inventory.model.ProductPrices
import com.stockstatement.inventory.model.StarterInventory
import com.stockstatement.inventory.model.UnitValues
import java.time.LocalDate

class InventoryDatabase(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE products (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                category TEXT NOT NULL,
                price_q INTEGER NOT NULL,
                price_p INTEGER NOT NULL,
                price_n INTEGER NOT NULL,
                price_d INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE daily_stock (
                product_id INTEGER NOT NULL REFERENCES products(id) ON DELETE CASCADE,
                date TEXT NOT NULL,
                opening_q INTEGER NOT NULL, opening_p INTEGER NOT NULL,
                opening_n INTEGER NOT NULL, opening_d INTEGER NOT NULL,
                receipts_q INTEGER NOT NULL, receipts_p INTEGER NOT NULL,
                receipts_n INTEGER NOT NULL, receipts_d INTEGER NOT NULL,
                closing_q INTEGER NOT NULL, closing_p INTEGER NOT NULL,
                closing_n INTEGER NOT NULL, closing_d INTEGER NOT NULL,
                price_q INTEGER NOT NULL, price_p INTEGER NOT NULL,
                price_n INTEGER NOT NULL, price_d INTEGER NOT NULL,
                PRIMARY KEY (product_id, date)
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                "CREATE TABLE app_metadata (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)"
            )
        }
    }

    fun initializeStarterInventory(): Boolean {
        val db = writableDatabase
        var imported = false
        db.beginTransaction()
        try {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS app_metadata (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)"
            )
            val alreadyInitialized = db.query(
                "app_metadata",
                arrayOf("value"),
                "key = ?",
                arrayOf(STARTER_IMPORT_KEY),
                null,
                null,
                null
            ).use { it.moveToFirst() }

            if (!alreadyInitialized) {
                val hasProducts = db.rawQuery("SELECT EXISTS(SELECT 1 FROM products LIMIT 1)", null)
                    .use { it.moveToFirst() && it.getInt(0) == 1 }
                if (!hasProducts) {
                    StarterInventory.products.forEach { starter ->
                        val productId = db.insertOrThrow(
                            "products",
                            null,
                            ContentValues().apply {
                                put("name", starter.name)
                                put("category", starter.category)
                                put("price_q", starter.prices.q)
                                put("price_p", starter.prices.p)
                                put("price_n", starter.prices.n)
                                put("price_d", starter.prices.d)
                            }
                        )
                        val closing = starter.closingOnSourceDate
                        db.insertOrThrow(
                            "daily_stock",
                            null,
                            ContentValues().apply {
                                put("product_id", productId)
                                put("date", StarterInventory.sourceDate.toString())
                                put("opening_q", closing.q)
                                put("opening_p", closing.p)
                                put("opening_n", closing.n)
                                put("opening_d", closing.d)
                                put("receipts_q", 0)
                                put("receipts_p", 0)
                                put("receipts_n", 0)
                                put("receipts_d", 0)
                                put("closing_q", closing.q)
                                put("closing_p", closing.p)
                                put("closing_n", closing.n)
                                put("closing_d", closing.d)
                                put("price_q", starter.prices.q)
                                put("price_p", starter.prices.p)
                                put("price_n", starter.prices.n)
                                put("price_d", starter.prices.d)
                            }
                        )
                    }
                    imported = true
                }
                db.insertOrThrow(
                    "app_metadata",
                    null,
                    ContentValues().apply {
                        put("key", STARTER_IMPORT_KEY)
                        put("value", "1")
                    }
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return imported
    }

    fun getProducts(): List<Product> {
        val result = mutableListOf<Product>()
        readableDatabase.query(
            "products",
            null,
            null,
            null,
            null,
            null,
            "CASE category " +
                "WHEN 'Whisky' THEN 0 WHEN 'Brandy' THEN 1 WHEN 'Rum' THEN 2 " +
                "WHEN 'Vodka' THEN 3 WHEN 'Wine' THEN 4 WHEN 'Beer' THEN 5 ELSE 6 END, name COLLATE NOCASE"
        ).use { cursor ->
            while (cursor.moveToNext()) result += cursor.toProduct()
        }
        return result
    }

    fun saveProduct(product: Product): Long {
        val values = ContentValues().apply {
            put("name", product.name.trim())
            put("category", product.category)
            put("price_q", product.prices.q)
            put("price_p", product.prices.p)
            put("price_n", product.prices.n)
            put("price_d", product.prices.d)
        }
        return if (product.id == 0L) {
            writableDatabase.insertOrThrow("products", null, values)
        } else {
            writableDatabase.update("products", values, "id = ?", arrayOf(product.id.toString()))
            product.id
        }
    }

    fun deleteProduct(productId: Long) {
        writableDatabase.delete("products", "id = ?", arrayOf(productId.toString()))
    }

    fun getDailyStock(date: LocalDate): List<DailyStock> {
        val db = readableDatabase
        return getProducts().map { product ->
            val saved = db.query(
                "daily_stock",
                null,
                "product_id = ? AND date = ?",
                arrayOf(product.id.toString(), date.toString()),
                null,
                null,
                null
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.toDailyStock(product) else null
            }
            saved ?: DailyStock(
                productId = product.id,
                productName = product.name,
                category = product.category,
                opening = previousClosing(db, product.id, date),
                receipts = UnitValues(),
                closing = previousClosing(db, product.id, date),
                prices = product.prices
            )
        }
    }

    fun saveDailyStock(date: LocalDate, stocks: List<DailyStock>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            stocks.forEach { stock ->
                val values = ContentValues().apply {
                    put("product_id", stock.productId)
                    put("date", date.toString())
                    putValues("opening", stock.opening)
                    putValues("receipts", stock.receipts)
                    putValues("closing", stock.closing)
                    putPrices(stock.prices)
                }
                db.insertWithOnConflict(
                    "daily_stock",
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun previousClosing(db: SQLiteDatabase, productId: Long, date: LocalDate): UnitValues {
        return db.query(
            "daily_stock",
            arrayOf("closing_q", "closing_p", "closing_n", "closing_d"),
            "product_id = ? AND date < ?",
            arrayOf(productId.toString(), date.toString()),
            null,
            null,
            "date DESC",
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                UnitValues(
                    q = cursor.getLong(0),
                    p = cursor.getLong(1),
                    n = cursor.getLong(2),
                    d = cursor.getLong(3)
                )
            } else {
                UnitValues()
            }
        }
    }

    private fun ContentValues.putValues(prefix: String, values: UnitValues) {
        put("${prefix}_q", values.q)
        put("${prefix}_p", values.p)
        put("${prefix}_n", values.n)
        put("${prefix}_d", values.d)
    }

    private fun ContentValues.putPrices(prices: ProductPrices) {
        put("price_q", prices.q)
        put("price_p", prices.p)
        put("price_n", prices.n)
        put("price_d", prices.d)
    }

    private fun android.database.Cursor.toProduct() = Product(
        id = getLong(getColumnIndexOrThrow("id")),
        name = getString(getColumnIndexOrThrow("name")),
        category = getString(getColumnIndexOrThrow("category")),
        prices = ProductPrices(
            q = getLong(getColumnIndexOrThrow("price_q")),
            p = getLong(getColumnIndexOrThrow("price_p")),
            n = getLong(getColumnIndexOrThrow("price_n")),
            d = getLong(getColumnIndexOrThrow("price_d"))
        )
    )

    private fun android.database.Cursor.toDailyStock(product: Product) = DailyStock(
        productId = product.id,
        productName = product.name,
        category = product.category,
        opening = UnitValues(
            q = getLong(getColumnIndexOrThrow("opening_q")),
            p = getLong(getColumnIndexOrThrow("opening_p")),
            n = getLong(getColumnIndexOrThrow("opening_n")),
            d = getLong(getColumnIndexOrThrow("opening_d"))
        ),
        receipts = UnitValues(
            q = getLong(getColumnIndexOrThrow("receipts_q")),
            p = getLong(getColumnIndexOrThrow("receipts_p")),
            n = getLong(getColumnIndexOrThrow("receipts_n")),
            d = getLong(getColumnIndexOrThrow("receipts_d"))
        ),
        closing = UnitValues(
            q = getLong(getColumnIndexOrThrow("closing_q")),
            p = getLong(getColumnIndexOrThrow("closing_p")),
            n = getLong(getColumnIndexOrThrow("closing_n")),
            d = getLong(getColumnIndexOrThrow("closing_d"))
        ),
        prices = ProductPrices(
            q = getLong(getColumnIndexOrThrow("price_q")),
            p = getLong(getColumnIndexOrThrow("price_p")),
            n = getLong(getColumnIndexOrThrow("price_n")),
            d = getLong(getColumnIndexOrThrow("price_d"))
        )
    )

    companion object {
        private const val DATABASE_NAME = "stock-statement.db"
        private const val DATABASE_VERSION = 2
        private const val STARTER_IMPORT_KEY = "starter_inventory_imported"
    }
}
