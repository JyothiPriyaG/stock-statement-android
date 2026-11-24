package com.stockstatement.inventory

import android.database.sqlite.SQLiteException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.stockstatement.inventory.data.InventoryDatabase
import com.stockstatement.inventory.model.DailyStock
import com.stockstatement.inventory.model.PackUnit
import com.stockstatement.inventory.model.Product
import com.stockstatement.inventory.model.ProductPrices
import com.stockstatement.inventory.model.PRODUCT_CATEGORIES
import com.stockstatement.inventory.report.StatementExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class AppTab { DAILY, PRODUCTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryApp(
    database: InventoryDatabase,
    onPrint: (LocalDate, List<DailyStock>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedTab by remember { mutableStateOf(AppTab.DAILY) }
    var products by remember { mutableStateOf(emptyList<Product>()) }
    var stocks by remember { mutableStateOf(emptyList<DailyStock>()) }
    var reloadKey by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var editedProduct by remember { mutableStateOf<Product?>(null) }
    var isAddingProduct by remember { mutableStateOf(false) }
    var productToDelete by remember { mutableStateOf<Product?>(null) }

    LaunchedEffect(reloadKey) {
        try {
            products = withContext(Dispatchers.IO) { database.getProducts() }
        } catch (error: SQLiteException) {
            message = error.localizedMessage ?: "Could not load products."
            messageIsError = true
        }
    }
    LaunchedEffect(selectedDate, reloadKey) {
        isLoading = true
        try {
            stocks = withContext(Dispatchers.IO) { database.getDailyStock(selectedDate) }
        } catch (error: SQLiteException) {
            message = error.localizedMessage ?: "Could not load this day's stock."
            messageIsError = true
        } finally {
            isLoading = false
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            output.write(
                                StatementExporter.csv(selectedDate, stocks).toByteArray(Charsets.UTF_8)
                            )
                        } ?: throw IOException("Could not open the selected file.")
                    }
                    message = "Spreadsheet saved."
                    messageIsError = false
                } catch (error: IOException) {
                    message = error.localizedMessage ?: "Could not save the spreadsheet."
                    messageIsError = true
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stock Statement") },
                actions = {
                    if (selectedTab == AppTab.DAILY) {
                        TextButton(onClick = {
                            csvLauncher.launch("stock-statement-$selectedDate.csv")
                        }) {
                            Text("CSV")
                        }
                        TextButton(onClick = { onPrint(selectedDate, stocks) }) {
                            Text("Print")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                Tab(
                    selected = selectedTab == AppTab.DAILY,
                    onClick = { selectedTab = AppTab.DAILY },
                    text = { Text("Daily stock") }
                )
                Tab(
                    selected = selectedTab == AppTab.PRODUCTS,
                    onClick = { selectedTab = AppTab.PRODUCTS },
                    text = { Text("Products") }
                )
            }

            if (message != null) {
                Text(
                    text = message.orEmpty(),
                    color = if (messageIsError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            when (selectedTab) {
                AppTab.DAILY -> DailyStockScreen(
                    date = selectedDate,
                    stocks = stocks,
                    loading = isLoading,
                    saving = isSaving,
                    onDateClick = { showDatePicker = true },
                    onPreviousDay = { selectedDate = selectedDate.minusDays(1) },
                    onNextDay = { selectedDate = selectedDate.plusDays(1) },
                    onStockChange = { changed ->
                        stocks = stocks.map { if (it.productId == changed.productId) changed else it }
                    },
                    onSave = {
                        scope.launch {
                            isSaving = true
                            try {
                                withContext(Dispatchers.IO) {
                                    database.saveDailyStock(selectedDate, stocks)
                                }
                                message = "Stock saved for $selectedDate."
                                messageIsError = false
                            } catch (error: SQLiteException) {
                                message = error.localizedMessage ?: "Could not save this day's stock."
                                messageIsError = true
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    onSaveAndNext = {
                        scope.launch {
                            isSaving = true
                            try {
                                withContext(Dispatchers.IO) {
                                    database.saveDailyStock(selectedDate, stocks)
                                }
                                selectedDate = selectedDate.plusDays(1)
                                message = "Saved. The next day's opening balances use these closing balances."
                                messageIsError = false
                            } catch (error: SQLiteException) {
                                message = error.localizedMessage ?: "Could not save this day's stock."
                                messageIsError = true
                            } finally {
                                isSaving = false
                            }
                        }
                    }
                )

                AppTab.PRODUCTS -> ProductListScreen(
                    products = products,
                    onAdd = { isAddingProduct = true },
                    onEdit = { editedProduct = it },
                    onDelete = { productToDelete = it }
                )
            }
        }
    }

    if (showDatePicker) {
        val initialMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val datePickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = initialMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) {
                    Text("Select")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (isAddingProduct || editedProduct != null) {
        ProductEditorDialog(
            product = editedProduct,
            onDismiss = {
                isAddingProduct = false
                editedProduct = null
            },
            onSave = { product ->
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { database.saveProduct(product) }
                        isAddingProduct = false
                        editedProduct = null
                        reloadKey++
                        message = "Product saved."
                        messageIsError = false
                    } catch (error: SQLiteException) {
                        message = error.localizedMessage ?: "Could not save the product."
                        messageIsError = true
                    }
                }
            }
        )
    }

    productToDelete?.let { product ->
        AlertDialog(
            onDismissRequest = { productToDelete = null },
            title = { Text("Delete ${product.name}?") },
            text = { Text("Its saved stock rows will also be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { database.deleteProduct(product.id) }
                            productToDelete = null
                            reloadKey++
                            message = "Product deleted."
                            messageIsError = false
                        } catch (error: SQLiteException) {
                            message = error.localizedMessage ?: "Could not delete the product."
                            messageIsError = true
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { productToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DailyStockScreen(
    date: LocalDate,
    stocks: List<DailyStock>,
    loading: Boolean,
    saving: Boolean,
    onDateClick: () -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onStockChange: (DailyStock) -> Unit,
    onSave: () -> Unit,
    onSaveAndNext: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onPreviousDay) { Text("‹") }
            TextButton(onClick = onDateClick) {
                Text(date.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())))
            }
            TextButton(onClick = onNextDay) { Text("›") }
        }

        if (loading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Loading stock…")
            }
        } else if (stocks.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Add products to start your stock statement.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp, end = 12.dp, bottom = 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(stocks, key = { it.productId }) { stock ->
                    DailyStockCard(stock = stock, onChange = onStockChange)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onSave,
                enabled = stocks.isNotEmpty() && !loading && !saving,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (saving) "Saving…" else "Save day")
            }
            Button(
                onClick = onSaveAndNext,
                enabled = stocks.isNotEmpty() && !loading && !saving,
                modifier = Modifier.weight(1f)
            ) {
                Text("Save & next day")
            }
        }
    }
}

@Composable
private fun DailyStockCard(stock: DailyStock, onChange: (DailyStock) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(stock.productName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(stock.category, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(8.dp))
            PackUnit.entries.forEach { unit ->
                Column(modifier = Modifier.padding(vertical = 3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            unit.label,
                            modifier = Modifier.width(24.dp),
                            fontWeight = FontWeight.Bold
                        )
                        QuantityInput("OB", stock.opening[unit]) { onChange(stock.withOpening(unit, it)) }
                        Spacer(Modifier.width(4.dp))
                        QuantityInput("Receipts", stock.receipts[unit]) { onChange(stock.withReceipts(unit, it)) }
                        Spacer(Modifier.width(4.dp))
                        QuantityInput("CB", stock.closing[unit]) { onChange(stock.withClosing(unit, it)) }
                    }
                    Row(
                        modifier = Modifier.padding(start = 28.dp, top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("Sales: ${stock.salesFor(unit)}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Amount: ${formatAmount(stock.amountFor(unit))}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuantityInput(label: String, value: Long, onChange: (Long) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { input ->
            if (input.isEmpty() || input.all(Char::isDigit)) onChange(input.toLongOrNull() ?: 0L)
        },
        modifier = Modifier.width(82.dp),
        label = { Text(label, fontSize = 10.sp, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
private fun ProductListScreen(
    products: List<Product>,
    onAdd: () -> Unit,
    onEdit: (Product) -> Unit,
    onDelete: (Product) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Button(onClick = onAdd) { Text("Add product") }
        }
        if (products.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No products yet.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp, end = 12.dp, bottom = 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(products, key = { it.id }) { product ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(product.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${product.category} · " + PackUnit.entries.joinToString("  ") {
                                        "${it.label} ${formatAmount(product.prices[it])}"
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            TextButton(onClick = { onEdit(product) }) { Text("Edit") }
                            TextButton(onClick = { onDelete(product) }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductEditorDialog(
    product: Product?,
    onDismiss: () -> Unit,
    onSave: (Product) -> Unit
) {
    var name by remember(product) { mutableStateOf(product?.name.orEmpty()) }
    var category by remember(product) { mutableStateOf(product?.category ?: "Wine") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val priceText = remember(product) {
        mutableStateOf(PackUnit.entries.associateWith { product?.prices?.get(it)?.toString().orEmpty() })
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .heightIn(max = 640.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    if (product == null) "Add product" else "Edit product",
                    style = MaterialTheme.typography.titleLarge
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it }
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        PRODUCT_CATEGORIES.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    category = option
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }
                Text("Unit prices (₹)", style = MaterialTheme.typography.titleSmall)
                PackUnit.entries.forEach { unit ->
                    OutlinedTextField(
                        value = priceText.value[unit].orEmpty(),
                        onValueChange = { value ->
                            if (value.isEmpty() || value.all(Char::isDigit)) {
                                priceText.value = priceText.value + (unit to value)
                            }
                        },
                        label = { Text("${unit.label} price") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                if (error != null) {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = {
                        val parsedPrices = PackUnit.entries.mapNotNull { unit ->
                            val text = priceText.value[unit].orEmpty()
                            val value = text.toLongOrNull()
                            if (text.isNotBlank() && (value == null || value < 0)) null
                            else unit to (value ?: 0L)
                        }.toMap()
                        if (name.isBlank()) {
                            error = "Enter a product name."
                        } else if (PackUnit.entries.any { priceText.value[it].isNullOrBlank() }) {
                            error = "Enter a price for each unit (use 0 if not sold)."
                        } else if (parsedPrices.size != PackUnit.entries.size) {
                            error = "Enter a valid whole-rupee price for each unit."
                        } else {
                            error = null
                            onSave(
                                Product(
                                    id = product?.id ?: 0L,
                                    name = name.trim(),
                                    category = category,
                                    prices = ProductPrices(
                                        q = parsedPrices.getValue(PackUnit.Q),
                                        p = parsedPrices.getValue(PackUnit.P),
                                        n = parsedPrices.getValue(PackUnit.N),
                                        d = parsedPrices.getValue(PackUnit.D)
                                    )
                                )
                            )
                        }
                    }) { Text("Save") }
                }
            }
        }
    }
}

private fun formatAmount(amount: Long): String =
    "₹" + java.text.NumberFormat.getIntegerInstance(Locale("en", "IN")).format(amount)
