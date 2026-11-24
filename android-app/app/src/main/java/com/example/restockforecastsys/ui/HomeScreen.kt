package com.example.restockforecastsys.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.restockforecastsys.DailyLogDto
import com.example.restockforecastsys.ui.auth.AuthViewModel
import com.example.restockforecastsys.ui.products.ProductViewModel
import java.time.LocalDate
import androidx.compose.material.icons.filled.AccountCircle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: AuthViewModel,
    productViewModel: ProductViewModel,
    dailyLogViewModel: DailyLogViewModel,
    onLogoutSuccess: () -> Unit,
    onNavigateToDailyLog: () -> Unit,
    onNavigateToProducts: () -> Unit,
    onNavigateToForecast: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val vendorName = viewModel.getUserName()
    val vendorProducts by productViewModel.vendorProducts.collectAsState()
    val recentLogs by dailyLogViewModel.recentLogs.collectAsState()
    val todayStr = remember { LocalDate.now().toString() }

    LaunchedEffect(Unit) {
        productViewModel.loadVendorProducts()
        dailyLogViewModel.loadRecentLogs()
    }

    val todaySalesTotal = recentLogs
        .filter { it.date == todayStr }
        .sumOf { it.sales }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Welcome, $vendorName",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Fresh Produce Restock Overview",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Edit Profile"
                        )
                    }
                    IconButton(onClick = {
                        viewModel.logout()
                        onLogoutSuccess()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Log Out"
                        )
                    }

                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Summary KPI Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        onClick = onNavigateToProducts
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "My Catalog",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${vendorProducts.size} Products",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap to manage →",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        onClick = onNavigateToDailyLog
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Today's Sales",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${"%.1f".format(todaySalesTotal)} Units",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Log stock/sales →",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            // Quick Actions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateToProducts,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("My Products")
                    }
                    Button(
                        onClick = onNavigateToDailyLog,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Log Today")
                    }
                }
            }

            // Current Stock Levels Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Current Stock Levels",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (vendorProducts.isEmpty()) {
                            Text(
                                text = "No products selected yet. Tap 'My Products' to build your catalog.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            vendorProducts.forEach { product ->
                                val latestLog = recentLogs.firstOrNull { it.product_id == product.productId }
                                val currentStock = latestLog?.closing_stock ?: 0.0

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = product.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${"%.1f".format(currentStock)} ${product.unitOfMeasure}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (currentStock > 0)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Sales & Waste Trend Graph Section
            if (recentLogs.isNotEmpty()) {
                item {
                    SalesAndWasteTrendCard(logs = recentLogs)
                }
            }

            // Recent Sales & Stock Logs Header
            item {
                Text(
                    text = "Recent Sales & Stock Logs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (recentLogs.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "No daily logs recorded yet. Use the Daily Log tab to record opening stock and sales.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(recentLogs.take(10)) { log ->
                    RecentLogCard(log = log)
                }
            }
        }
    }
}

@Composable
fun SalesAndWasteTrendCard(logs: List<DailyLogDto>) {
    // Group by date ascending and take up to the last 7 logged days
    val dailyPoints = remember(logs) {
        logs.groupBy { it.date }
            .map { (date, entries) ->
                Triple(
                    date,
                    entries.sumOf { it.sales }.toFloat(),
                    entries.sumOf { it.waste }.toFloat()
                )
            }
            .sortedBy { it.first }
            .takeLast(7)
    }

    if (dailyPoints.isEmpty()) return

    val maxVal = remember(dailyPoints) {
        (dailyPoints.maxOfOrNull { maxOf(it.second, it.third) } ?: 10f).coerceAtLeast(10f)
    }

    val salesColor = Color(0xFF2E7D32) // Green
    val wasteColor = Color(0xFFD32F2F) // Red

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
                Column {
                    Text(
                        text = "Sales vs. Spoilage Trend",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Last ${dailyPoints.size} logged days (Total Units)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendDot(color = salesColor, label = "Sales")
                    LegendDot(color = wasteColor, label = "Waste")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    val stepX = if (dailyPoints.size > 1) {
                        canvasWidth / (dailyPoints.size - 1).toFloat()
                    } else {
                        canvasWidth / 2f
                    }

                    // Horizontal guide lines
                    val gridLines = 4
                    for (i in 0..gridLines) {
                        val y = canvasHeight - (canvasHeight / gridLines) * i
                        drawLine(
                            color = Color.LightGray.copy(alpha = 0.5f),
                            start = Offset(0f, y),
                            end = Offset(canvasWidth, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    val salesPath = Path()
                    val wastePath = Path()

                    dailyPoints.forEachIndexed { index, (_, salesTotal, wasteTotal) ->
                        val x = if (dailyPoints.size > 1) index * stepX else canvasWidth / 2f
                        val salesY = (1f - (salesTotal / maxVal)) * canvasHeight
                        val wasteY = (1f - (wasteTotal / maxVal)) * canvasHeight

                        if (index == 0) {
                            salesPath.moveTo(x, salesY)
                            wastePath.moveTo(x, wasteY)
                        } else {
                            salesPath.lineTo(x, salesY)
                            wastePath.lineTo(x, wasteY)
                        }

                        drawCircle(
                            color = salesColor,
                            radius = 4.dp.toPx(),
                            center = Offset(x, salesY)
                        )
                        drawCircle(
                            color = wasteColor,
                            radius = 4.dp.toPx(),
                            center = Offset(x, wasteY)
                        )
                    }

                    if (dailyPoints.size > 1) {
                        drawPath(
                            path = salesPath,
                            color = salesColor,
                            style = Stroke(
                                width = 3.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                        drawPath(
                            path = wastePath,
                            color = wasteColor,
                            style = Stroke(
                                width = 2.5.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // X-axis date labels (MM-DD)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                dailyPoints.forEach { (dateStr, _, _) ->
                    val shortDate = if (dateStr.length >= 10) dateStr.substring(5, 10) else dateStr
                    Text(
                        text = shortDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun RecentLogCard(log: DailyLogDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = log.product_name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = log.date,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LogMetricItem(label = "Opening", value = "%.1f".format(log.opening_stock))
                LogMetricItem(label = "Sold", value = "%.1f".format(log.sales))
                LogMetricItem(label = "Waste", value = "%.1f".format(log.waste))
                LogMetricItem(label = "Closing", value = "%.1f".format(log.closing_stock))
            }
        }
    }
}

@Composable
fun LogMetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}