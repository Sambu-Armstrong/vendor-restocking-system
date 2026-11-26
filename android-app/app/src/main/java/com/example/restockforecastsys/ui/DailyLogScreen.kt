package com.example.restockforecastsys.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.restockforecastsys.data.entity.Product
import com.example.restockforecastsys.ui.products.ProductViewModel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyLogScreen(
    productViewModel: ProductViewModel,
    dailyLogViewModel: DailyLogViewModel,
    onNavigateToProducts: () -> Unit
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Morning (Opening)", "Evening (Sales & Waste)")

    val vendorProducts by productViewModel.vendorProducts.collectAsState()
    val recentLogs by dailyLogViewModel.recentLogs.collectAsState()
    val saveMessage by dailyLogViewModel.saveSuccessMessage.collectAsState()
    val isSaving by dailyLogViewModel.isSaving.collectAsState()
    val context = LocalContext.current

    val todayStr = remember { LocalDate.now().toString() }
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()

    // Map of productId -> input strings
    val morningInputs = remember { mutableStateMapOf<Int, String>() }
    val eveningSalesInputs = remember { mutableStateMapOf<Int, String>() }
    val eveningWasteInputs = remember { mutableStateMapOf<Int, String>() }

    LaunchedEffect(Unit) {
        productViewModel.loadVendorProducts()
        dailyLogViewModel.loadRecentLogs()
    }

    // Pre-fill fields if today's log already exists in the cloud
    LaunchedEffect(recentLogs, vendorProducts) {
        val todayLogs = recentLogs.filter { it.date == todayStr }
        todayLogs.forEach { log ->
            if (morningInputs[log.product_id].isNullOrEmpty() && log.opening_stock > 0) {
                morningInputs[log.product_id] = log.opening_stock.toString()
            }
            if (eveningSalesInputs[log.product_id].isNullOrEmpty() && log.sales > 0) {
                eveningSalesInputs[log.product_id] = log.sales.toString()
            }
            if (eveningWasteInputs[log.product_id].isNullOrEmpty() && log.waste > 0) {
                eveningWasteInputs[log.product_id] = log.waste.toString()
            }
        }
    }

    LaunchedEffect(saveMessage) {
        saveMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            dailyLogViewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Daily Stock & Sales Log", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Date: $todayStr",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }

            if (vendorProducts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "You haven't added any products to your stall yet.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onNavigateToProducts) {
                            Text("Go to My Products")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(
                        top = 16.dp,
                        bottom = (imeBottom - 64.dp).coerceAtLeast(16.dp)
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = if (selectedTabIndex == 0)
                                "Enter today's total opening stock (carried forward + new restock)."
                            else
                                "Enter today's units sold and any spoiled waste. Remaining stock will be carried forward automatically.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(vendorProducts) { product ->
                        val todayLog = recentLogs.find {
                            it.product_id == product.productId && it.date == todayStr
                        }
                        val loggedOpening = todayLog?.opening_stock ?: 0.0

                        ProductLogEntryCard(
                            product = product,
                            tabIndex = selectedTabIndex,
                            loggedOpeningStock = loggedOpening,
                            openingValue = morningInputs[product.productId] ?: "",
                            onOpeningChange = { morningInputs[product.productId] = it },
                            salesValue = eveningSalesInputs[product.productId] ?: "",
                            onSalesChange = { eveningSalesInputs[product.productId] = it },
                            wasteValue = eveningWasteInputs[product.productId] ?: "",
                            onWasteChange = { eveningWasteInputs[product.productId] = it }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (selectedTabIndex == 0) {
                                    val payload = mutableMapOf<Int, Double>()
                                    vendorProducts.forEach { p ->
                                        val v = morningInputs[p.productId]?.toDoubleOrNull()
                                        if (v != null && v >= 0) {
                                            payload[p.productId] = v
                                        }
                                    }
                                    if (payload.isEmpty()) {
                                        Toast.makeText(context, "Enter opening stock for at least one product", Toast.LENGTH_SHORT).show()
                                    } else {
                                        dailyLogViewModel.saveMorningStock(payload)
                                    }
                                } else {
                                    val payload = mutableMapOf<Int, Pair<Double, Double>>()
                                    var validationError: String? = null

                                    vendorProducts.forEach { p ->
                                        val s = eveningSalesInputs[p.productId]?.toDoubleOrNull()
                                        val w = eveningWasteInputs[p.productId]?.toDoubleOrNull() ?: 0.0
                                        val todayLog = recentLogs.find {
                                            it.product_id == p.productId && it.date == todayStr
                                        }
                                        val opening = todayLog?.opening_stock ?: 0.0

                                        if (s != null && s >= 0) {
                                            if (w < 0) {
                                                validationError = "${p.name}: Spoiled waste cannot be negative"
                                            } else if (opening > 0 && (s + w) > opening) {
                                                validationError = "${p.name}: Sold + Waste (${"%.1f".format(s + w)}) cannot exceed Opening Stock ($opening)"
                                            } else {
                                                payload[p.productId] = Pair(s, w)
                                            }
                                        }
                                    }

                                    if (validationError != null) {
                                        Toast.makeText(context, validationError, Toast.LENGTH_LONG).show()
                                    } else if (payload.isEmpty()) {
                                        Toast.makeText(context, "Enter units sold for at least one product", Toast.LENGTH_SHORT).show()
                                    } else {
                                        dailyLogViewModel.saveEveningSales(payload)
                                    }
                                }
                            },
                            enabled = !isSaving,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text(
                                text = if (selectedTabIndex == 0) "Save Opening Stock" else "Save Sales & Calculate Closing Stock",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ProductLogEntryCard(
    product: Product,
    tabIndex: Int,
    loggedOpeningStock: Double,
    openingValue: String,
    onOpeningChange: (String) -> Unit,
    salesValue: String,
    onSalesChange: (String) -> Unit,
    wasteValue: String,
    onWasteChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Unit: ${product.unitOfMeasure}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (tabIndex == 0) {
                OutlinedTextField(
                    value = openingValue,
                    onValueChange = onOpeningChange,
                    label = { Text("Opening Stock (${product.unitOfMeasure})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                val soldNum = salesValue.toDoubleOrNull() ?: 0.0
                val wasteNum = wasteValue.toDoubleOrNull() ?: 0.0
                val exceedsOpening = loggedOpeningStock > 0 && (soldNum + wasteNum) > loggedOpeningStock
                val projectedClosing = (loggedOpeningStock - soldNum - wasteNum).coerceAtLeast(0.0)

                Text(
                    text = "Today's Opening Stock: $loggedOpeningStock ${product.unitOfMeasure}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = salesValue,
                        onValueChange = onSalesChange,
                        label = { Text("Sold (${product.unitOfMeasure})") },
                        isError = exceedsOpening,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = wasteValue,
                        onValueChange = onWasteChange,
                        label = { Text("Spoiled / Waste") },
                        isError = exceedsOpening,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (exceedsOpening) {
                        "Error: Sold + Waste exceeds Opening Stock ($loggedOpeningStock ${product.unitOfMeasure})"
                    } else {
                        "Closing Stock (Carried Forward): ${"%.1f".format(projectedClosing)} ${product.unitOfMeasure}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (exceedsOpening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}