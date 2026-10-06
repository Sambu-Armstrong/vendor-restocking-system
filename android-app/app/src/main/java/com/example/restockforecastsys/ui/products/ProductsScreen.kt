package com.example.restockforecastsys.ui.products

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.restockforecastsys.data.catalog.ProductCatalog
import com.example.restockforecastsys.data.entity.Product

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    viewModel: ProductViewModel,
    onNavigateBack: () -> Unit
) {
    val vendorProducts by viewModel.vendorProducts.collectAsState()
    var showCatalogDialog by remember { mutableStateOf(false) }

    // NEW: Forces the ViewModel to grab the correct Vendor ID when the screen opens
    LaunchedEffect(Unit) {
        viewModel.loadVendorProducts()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Products", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCatalogDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Product")
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
            if (vendorProducts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No products added yet. Tap '+' to select from the catalog.")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vendorProducts) { product ->
                        ProductItemCard(
                            product = product,
                            onDelete = { viewModel.removeProduct(product) }
                        )
                    }
                }
            }
        }
    }

    if (showCatalogDialog) {
        AlertDialog(
            onDismissRequest = { showCatalogDialog = false },
            title = { Text("Select Product") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ProductCatalog.items) { catalogItem ->
                        if (vendorProducts.none { it.name == catalogItem.name }) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    viewModel.addProductFromCatalog(catalogItem)
                                    showCatalogDialog = false
                                }
                            ) {
                                Text(
                                    text = catalogItem.name,
                                    modifier = Modifier.padding(16.dp),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCatalogDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun ProductItemCard(product: Product, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = product.name, fontWeight = FontWeight.Bold)
                Text(text = "Shelf Life: ${product.shelfLifeDays} days", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}