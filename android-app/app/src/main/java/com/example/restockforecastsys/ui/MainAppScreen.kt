package com.example.restockforecastsys.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.restockforecastsys.ui.auth.AuthViewModel
import com.example.restockforecastsys.ui.products.ProductViewModel
import com.example.restockforecastsys.ui.products.ProductsScreen

@Composable
fun MainAppScreen(
    authViewModel: AuthViewModel,
    productViewModel: ProductViewModel,
    dailyLogViewModel: DailyLogViewModel,
    onLogout: () -> Unit
) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    selected = currentDestination?.hierarchy?.any { it.route == "home_tab" } == true,
                    onClick = {
                        navController.navigate("home_tab") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )

                NavigationBarItem(
                    icon = { Icon(Icons.Filled.EditNote, contentDescription = "Daily Log") },
                    label = { Text("Daily Log") },
                    selected = currentDestination?.hierarchy?.any { it.route == "log_tab" } == true,
                    onClick = {
                        navController.navigate("log_tab") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )

                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Analytics, contentDescription = "Forecast") },
                    label = { Text("Forecast") },
                    selected = currentDestination?.hierarchy?.any { it.route == "forecast_tab" } == true,
                    onClick = {
                        navController.navigate("forecast_tab") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home_tab",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home_tab") {
                HomeScreen(
                    viewModel = authViewModel,
                    productViewModel = productViewModel,
                    dailyLogViewModel = dailyLogViewModel,
                    onLogoutSuccess = onLogout,
                    onNavigateToDailyLog = {
                        navController.navigate("log_tab") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToProducts = { navController.navigate("products") },
                    onNavigateToForecast = {
                        navController.navigate("forecast_tab") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable("products") {
                ProductsScreen(
                    viewModel = productViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("log_tab") {
                DailyLogScreen(
                    productViewModel = productViewModel,
                    dailyLogViewModel = dailyLogViewModel,
                    onNavigateToProducts = { navController.navigate("products") }
                )
            }
            composable("forecast_tab") {
                Text(
                    text = "Forecast & Analytics Screen (Phase 3 & 4 Tomorrow)",
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}