package com.example.restockforecastsys

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.restockforecastsys.data.database.AppDatabase
import com.example.restockforecastsys.data.repository.AuthRepository
import com.example.restockforecastsys.ui.DailyLogViewModel
import com.example.restockforecastsys.ui.auth.AuthViewModel
import com.example.restockforecastsys.ui.auth.LoginScreen
import com.example.restockforecastsys.ui.auth.RegisterScreen
import com.example.restockforecastsys.util.SessionManager

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Initialize local Room database and DAOs
        val database = AppDatabase.getDatabase(applicationContext, lifecycleScope)
        val authRepository = AuthRepository(
            userDao = database.userDao(),
            roleDao = database.roleDao(),
            vendorDao = database.vendorDao()
        )

        val productRepository = com.example.restockforecastsys.data.repository.ProductRepository(database.productDao())

        // 2. Initialize SessionManager
        val sessionManager = SessionManager(applicationContext)

        // 3. Initialize ViewModels
        val authViewModel = AuthViewModel(authRepository, sessionManager)
        val productViewModel = com.example.restockforecastsys.ui.products.ProductViewModel(productRepository, sessionManager)
        val dailyLogViewModel = DailyLogViewModel(sessionManager)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    val startDestination = remember {
                        if (authViewModel.isUserLoggedIn()) "main_app" else "login"
                    }

                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
                        composable("login") {
                            LoginScreen(
                                viewModel = authViewModel,
                                onNavigateToRegister = { navController.navigate("register") },
                                onLoginSuccess = {
                                    navController.navigate("main_app") { popUpTo("login") { inclusive = true } }
                                }
                            )
                        }

                        composable("register") {
                            RegisterScreen(
                                viewModel = authViewModel,
                                onNavigateToLogin = { navController.popBackStack() },
                                onRegisterSuccess = {
                                    navController.navigate("main_app") { popUpTo("login") { inclusive = true } }
                                }
                            )
                        }

                        composable("main_app") {
                            com.example.restockforecastsys.ui.MainAppScreen(
                                authViewModel = authViewModel,
                                productViewModel = productViewModel,
                                dailyLogViewModel = dailyLogViewModel,
                                onLogout = {
                                    navController.navigate("login") { popUpTo("main_app") { inclusive = true } }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}