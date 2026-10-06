package com.example.restockforecastsys.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.restockforecastsys.ui.auth.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: AuthViewModel,
    onLogoutSuccess: () -> Unit,
    onNavigateToDailyLog: () -> Unit,
    onNavigateToProducts: () -> Unit,
    onNavigateToForecast: () -> Unit,
    onNavigateToSubscription: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vendor Dashboard", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Welcome back",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            NavCard(
                icon = Icons.Default.ShoppingCart,
                label = "My Products",
                description = "Choose which products you sell",
                onClick = onNavigateToProducts
            )
            NavCard(
                icon = Icons.Default.DateRange,
                label = "Daily Log",
                description = "Log opening and closing stock",
                onClick = onNavigateToDailyLog
            )
            NavCard(
                icon = Icons.Default.Analytics,
                label = "Forecast & Waste Report",
                description = "View demand forecasts and restocking recommendations",
                onClick = onNavigateToForecast
            )
            NavCard(
                icon = Icons.Default.CreditCard,
                label = "Subscription",
                description = "Manage your subscription plan",
                onClick = onNavigateToSubscription
            )
        }
    }
}

@Composable
fun NavCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(text = label, fontWeight = FontWeight.Bold)
                Text(text = description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}