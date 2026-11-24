package com.example.restockforecastsys.ui.products

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.restockforecastsys.data.catalog.CatalogItem
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
    var selectedCatalogItemForPrice by remember { mutableStateOf<CatalogItem?>(null) }
    var editingProductPrice by remember { mutableStateOf<Product?>(null) }
    var priceInput by remember { mutableStateOf("") }
    val context = LocalContext.current

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
                            onEditPrice = {
                                priceInput = if (product.currentPrice > 0) product.currentPrice.toString() else ""
                                editingProductPrice = product
                            },
                            onDelete = { viewModel.removeProduct(product) }
                        )
                    }
                }
            }
        }
    }

    // Step 1: Pick Product from Catalog
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
                                    showCatalogDialog = false
                                    priceInput = ""
                                    selectedCatalogItemForPrice = catalogItem
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

    // Step 2: Set Price When Adding a New Product
    selectedCatalogItemForPrice?.let { item ->
        AlertDialog(
            onDismissRequest = { selectedCatalogItemForPrice = null },
            title = { Text("Set Selling Price") },
            text = {
                Column {
                    Text("Enter your selling price for ${item.name} (KES per ${item.defaultUnitOfMeasure}):")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = priceInput,
                        onValueChange = { priceInput = it },
                        label = { Text("Price (KES)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val parsed = priceInput.toDoubleOrNull()
                    if (parsed == null || parsed <= 0.0) {
                        Toast.makeText(context, "Enter a valid price greater than 0", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.addProductFromCatalog(item, parsed)
                        selectedCatalogItemForPrice = null
                    }
                }) {
                    Text("Save Product")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedCatalogItemForPrice = null }) { Text("Cancel") }
            }
        )
    }

    // Edit Price for an Existing Product
    editingProductPrice?.let { product ->
        AlertDialog(
            onDismissRequest = { editingProductPrice = null },
            title = { Text("Update Selling Price") },
            text = {
                Column {
                    Text("Update price for ${product.name} (KES per ${product.unitOfMeasure}):")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = priceInput,
                        onValueChange = { priceInput = it },
                        label = { Text("Price (KES)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val parsed = priceInput.toDoubleOrNull()
                    if (parsed == null || parsed <= 0.0) {
                        Toast.makeText(context, "Enter a valid price greater than 0", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.updateProductPrice(product, parsed)
                        editingProductPrice = null
                    }
                }) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingProductPrice = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun ProductItemCard(
    product: Product,
    onEditPrice: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = product.name, fontWeight = FontWeight.Bold)
                Text(
                    text = "Price: KES ${"%.2f".format(product.currentPrice)} / ${product.unitOfMeasure}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Shelf Life: ${product.shelfLifeDays} days",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row {
                IconButton(onClick = onEditPrice) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Price", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}