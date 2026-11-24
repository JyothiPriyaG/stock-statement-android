package com.stockstatement.inventory

import android.database.sqlite.SQLiteException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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

private enum class AppPage { STATEMENT, PRODUCTS }

private val productColumnWidth = 176.dp
private val valueColumnWidth = 58.dp
private val statementTableWidth = productColumnWidth + valueColumnWidth * 20
private val gridLine = Color(0xFF8A918A)
private val headingBackground = Color(0xFFE7ECE7)
private val categoryBackground = Color(0xFFDCE5DC)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryApp(
    database: InventoryDatabase,
    onPrint: (LocalDate, List<DailyStock>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var page by remember { mutableStateOf(AppPage.STATEMENT) }
    var isEditing by remember { mutableStateOf(false) }
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

    LaunchedEffect(selectedDate, reloadKey) {
        isLoading = true
        try {
            val importedStarter = withContext(Dispatchers.IO) {
                database.initializeStarterInventory()
            }
            products = withContext(Dispatchers.IO) { database.getProducts() }
            stocks = withContext(Dispatchers.IO) { database.getDailyStock(selectedDate) }
            if (importedStarter) {
                message = "Starter balances from 23 Nov are ready to review. Check quantities and unit prices."
                messageIsError = false
            }
        } catch (error: SQLiteException) {
            message = error.localizedMessage ?: "Could not load the stock statement."
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

    fun saveDay(advanceToNextDay: Boolean) {
        scope.launch {
            isSaving = true
            try {
                withContext(Dispatchers.IO) {
                    database.saveDailyStock(selectedDate, stocks)
                }
                isEditing = false
                if (advanceToNextDay) {
                    selectedDate = selectedDate.plusDays(1)
                    message = "Saved. Closing balances are ready as the next day's opening balances."
                } else {
                    message = "Stock saved for $selectedDate."
                }
                messageIsError = false
            } catch (error: SQLiteException) {
                message = error.localizedMessage ?: "Could not save this day's stock."
                messageIsError = true
            } finally {
                isSaving = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (page == AppPage.STATEMENT) "Stock Statement" else "Products") },
                actions = {
                    if (page == AppPage.STATEMENT) {
                        TextButton(onClick = { isEditing = !isEditing }) {
                            Text(if (isEditing) "Done" else "Edit")
                        }
                    } else {
                        TextButton(onClick = { page = AppPage.STATEMENT }) { Text("Sheet") }
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
            if (message != null) {
                Text(
                    text = message.orEmpty(),
                    color = if (messageIsError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            when (page) {
                AppPage.STATEMENT -> StatementScreen(
                    date = selectedDate,
                    stocks = stocks,
                    loading = isLoading,
                    editing = isEditing,
                    saving = isSaving,
                    onDateClick = { showDatePicker = true },
                    onPreviousDay = { selectedDate = selectedDate.minusDays(1) },
                    onNextDay = { selectedDate = selectedDate.plusDays(1) },
                    onStockChange = { changed ->
                        stocks = stocks.map { if (it.productId == changed.productId) changed else it }
                    },
                    onProducts = { page = AppPage.PRODUCTS },
                    onExport = { csvLauncher.launch("stock-statement-$selectedDate.csv") },
                    onPrint = { onPrint(selectedDate, stocks) },
                    onDiscard = {
                        isEditing = false
                        reloadKey++
                    },
                    onSave = { saveDay(false) },
                    onSaveAndNext = { saveDay(true) }
                )

                AppPage.PRODUCTS -> ProductListScreen(
                    products = products,
                    onBack = { page = AppPage.STATEMENT },
                    onAdd = {
                        isAddingProduct = true
                        editedProduct = null
                    },
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
                        selectedDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("Select") }
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
private fun StatementScreen(
    date: LocalDate,
    stocks: List<DailyStock>,
    loading: Boolean,
    editing: Boolean,
    saving: Boolean,
    onDateClick: () -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onStockChange: (DailyStock) -> Unit,
    onProducts: () -> Unit,
    onExport: () -> Unit,
    onPrint: () -> Unit,
    onDiscard: () -> Unit,
    onSave: () -> Unit,
    onSaveAndNext: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onPreviousDay) { Text("Previous") }
            TextButton(onClick = onDateClick) {
                Text(date.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())))
            }
            TextButton(onClick = onNextDay) { Text("Next") }
        }

        if (loading) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("Loading stock…")
            }
        } else if (stocks.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("Add products to start your stock statement.")
            }
        } else {
            SpreadsheetTable(
                stocks = stocks,
                editing = editing,
                modifier = Modifier.weight(1f),
                onStockChange = onStockChange
            )
        }

        if (editing) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDiscard,
                    enabled = !saving,
                    modifier = Modifier.weight(1f)
                ) { Text("Discard") }
                OutlinedButton(
                    onClick = onSave,
                    enabled = stocks.isNotEmpty() && !loading && !saving,
                    modifier = Modifier.weight(1f)
                ) { Text(if (saving) "Saving…" else "Save day") }
                Button(
                    onClick = onSaveAndNext,
                    enabled = stocks.isNotEmpty() && !loading && !saving,
                    modifier = Modifier.weight(1.2f)
                ) { Text("Save & next") }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = onProducts) { Text("Products") }
                TextButton(onClick = onExport) { Text("Spreadsheet") }
                TextButton(onClick = onPrint) { Text("Print") }
            }
        }
    }
}

@Composable
private fun SpreadsheetTable(
    stocks: List<DailyStock>,
    editing: Boolean,
    modifier: Modifier = Modifier,
    onStockChange: (DailyStock) -> Unit
) {
    val horizontalState = rememberScrollState()
    val verticalState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(horizontalState)
            .verticalScroll(verticalState)
    ) {
        Column(modifier = Modifier.width(statementTableWidth)) {
            SpreadsheetHeader()
            stocks.groupBy { it.category }.forEach { (category, categoryStocks) ->
                Box(
                    modifier = Modifier
                        .width(statementTableWidth)
                        .height(30.dp)
                        .background(categoryBackground)
                        .border(1.dp, gridLine)
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        category.uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                categoryStocks.forEach { stock ->
                    SpreadsheetProductRow(
                        stock = stock,
                        editing = editing,
                        onChange = onStockChange
                    )
                }
            }
            SpreadsheetTotals(stocks)
        }
    }
}

@Composable
private fun SpreadsheetHeader() {
    Row(verticalAlignment = Alignment.Top) {
        HeaderCell("Product", productColumnWidth, height = 68.dp, bold = true)
        Column {
            Row {
                HeaderCell("Opening Balance", valueColumnWidth * 4, height = 34.dp, bold = true)
                HeaderCell("Receipts", valueColumnWidth * 4, height = 34.dp, bold = true)
                HeaderCell("Closing Balance", valueColumnWidth * 4, height = 34.dp, bold = true)
                HeaderCell("Sales", valueColumnWidth * 4, height = 34.dp, bold = true)
                HeaderCell("Amount", valueColumnWidth * 4, height = 34.dp, bold = true)
            }
            Row {
                repeat(5) {
                    PackUnit.entries.forEach { unit ->
                        HeaderCell(unit.label, valueColumnWidth, height = 34.dp, bold = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(label: String, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp, bold: Boolean) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .background(headingBackground)
            .border(1.dp, gridLine)
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            textAlign = TextAlign.Center,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun SpreadsheetProductRow(
    stock: DailyStock,
    editing: Boolean,
    onChange: (DailyStock) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(productColumnWidth)
                .height(38.dp)
                .background(Color.White)
                .border(1.dp, gridLine)
                .padding(horizontal = 5.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(stock.productName, maxLines = 1, fontSize = 11.sp)
        }
        PackUnit.entries.forEach { unit ->
            QuantityCell(
                stock.productId,
                "opening-${unit.name}",
                stock.opening[unit],
                editing
            ) { onChange(stock.withOpening(unit, it)) }
        }
        PackUnit.entries.forEach { unit ->
            QuantityCell(
                stock.productId,
                "receipts-${unit.name}",
                stock.receipts[unit],
                editing
            ) { onChange(stock.withReceipts(unit, it)) }
        }
        PackUnit.entries.forEach { unit ->
            QuantityCell(
                stock.productId,
                "closing-${unit.name}",
                stock.closing[unit],
                editing
            ) { onChange(stock.withClosing(unit, it)) }
        }
        PackUnit.entries.forEach { unit ->
            ValueCell(stock.salesFor(unit).toString())
        }
        PackUnit.entries.forEach { unit ->
            ValueCell(formatAmount(stock.amountFor(unit)))
        }
    }
}

@Composable
private fun QuantityCell(
    productId: Long,
    field: String,
    value: Long,
    editing: Boolean,
    onChange: (Long) -> Unit
) {
    var entry by remember(productId, field, editing) { mutableStateOf(value.toString()) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value, focused) {
        if (!focused) entry = value.toString()
    }

    if (editing) {
        Box(
            modifier = Modifier
                .width(valueColumnWidth)
                .height(38.dp)
                .background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.primary)
                .padding(horizontal = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicTextField(
                value = entry,
                onValueChange = { input ->
                    if (input.isEmpty() || input.all(Char::isDigit)) {
                        entry = input
                        if (input.isEmpty()) onChange(0) else input.toLongOrNull()?.let(onChange)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    } else {
        ValueCell(value.toString())
    }
}

@Composable
private fun ValueCell(value: String) {
    Box(
        modifier = Modifier
            .width(valueColumnWidth)
            .height(38.dp)
            .background(Color.White)
            .border(1.dp, gridLine)
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(value, textAlign = TextAlign.Center, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun SpreadsheetTotals(stocks: List<DailyStock>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(productColumnWidth)
                .height(40.dp)
                .background(headingBackground)
                .border(1.dp, gridLine)
                .padding(horizontal = 5.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text("TOTAL", fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
        repeat(12) { ValueCell("") }
        PackUnit.entries.forEach { unit ->
            ValueCell(stocks.sumOf { it.salesFor(unit) }.toString())
        }
        PackUnit.entries.forEach { unit ->
            ValueCell(formatAmount(stocks.sumOf { it.amountFor(unit) }))
        }
    }
}

@Composable
private fun ProductListScreen(
    products: List<Product>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Product) -> Unit,
    onDelete: (Product) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) { Text("Statement") }
            TextButton(onClick = onAdd) { Text("Add product") }
        }
        if (products.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("No products yet.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(products, key = { it.id }) { product ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(product.name, style = MaterialTheme.typography.titleSmall)
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
    var priceText by remember(product) {
        mutableStateOf(PackUnit.entries.associateWith {
            product?.prices?.get(it)?.toString().orEmpty()
        })
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
                modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
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
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded)
                        },
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
                        value = priceText[unit].orEmpty(),
                        onValueChange = { value ->
                            if (value.isEmpty() || value.all(Char::isDigit)) {
                                priceText = priceText + (unit to value)
                            }
                        },
                        label = { Text("${unit.label} price") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = {
                        val parsedPrices = PackUnit.entries.mapNotNull { unit ->
                            val text = priceText[unit].orEmpty()
                            val value = text.toLongOrNull()
                            if (text.isNotBlank() && (value == null || value < 0)) null
                            else unit to (value ?: 0L)
                        }.toMap()
                        if (name.isBlank()) {
                            error = "Enter a product name."
                        } else if (PackUnit.entries.any { priceText[it].isNullOrBlank() }) {
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
    java.text.NumberFormat.getIntegerInstance(Locale("en", "IN")).format(amount)
