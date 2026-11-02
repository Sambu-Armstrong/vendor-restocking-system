package com.example.restockforecastsys.ui.auth

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    viewModel: AuthViewModel,
    onNavigateToLogin: () -> Unit,
    onRegisterSuccess: () -> Unit
) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var businessName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    // New Dropdown States
    var locationExpanded by remember { mutableStateOf(false) }
    var selectedLocation by remember { mutableStateOf("") }
    val locations = listOf("Kawangware", "Machakos", "Kakamega", "Mombasa")

    var sizeExpanded by remember { mutableStateOf(false) }
    var selectedSize by remember { mutableStateOf("") }
    val stallSizes = listOf("Small", "Medium", "Large")

    val context = LocalContext.current
    val authState = viewModel.authState

    // FIX: Safely observe state. Only trigger success navigation AFTER the backend confirms.
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Success -> {
                Toast.makeText(context, "Registration successful", Toast.LENGTH_SHORT).show()
                viewModel.resetState()
                onRegisterSuccess()
            }
            is AuthState.Error -> {
                Toast.makeText(context, authState.message, Toast.LENGTH_SHORT).show()
                viewModel.resetState()
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Create Vendor Account", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(value = firstName, onValueChange = { firstName = it }, label = { Text("First Name") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = lastName, onValueChange = { lastName = it }, label = { Text("Last Name") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = businessName, onValueChange = { businessName = it }, label = { Text("Business Name") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))

        // Location Dropdown
        ExposedDropdownMenuBox(expanded = locationExpanded, onExpandedChange = { locationExpanded = !locationExpanded }) {
            OutlinedTextField(
                value = selectedLocation.ifEmpty { "Select Location" },
                onValueChange = {}, readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = locationExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = locationExpanded, onDismissRequest = { locationExpanded = false }) {
                locations.forEach { loc ->
                    DropdownMenuItem(text = { Text(loc) }, onClick = { selectedLocation = loc; locationExpanded = false })
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Stall Size Dropdown
        ExposedDropdownMenuBox(expanded = sizeExpanded, onExpandedChange = { sizeExpanded = !sizeExpanded }) {
            OutlinedTextField(
                value = selectedSize.ifEmpty { "Select Stall Size" },
                onValueChange = {}, readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sizeExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = sizeExpanded, onDismissRequest = { sizeExpanded = false }) {
                stallSizes.forEach { size ->
                    DropdownMenuItem(text = { Text(size) }, onClick = { selectedSize = size; sizeExpanded = false })
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password (Min 8 chars)") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = confirmPassword, onValueChange = { confirmPassword = it }, label = { Text("Confirm Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (firstName.isBlank() || lastName.isBlank() || email.isBlank() || selectedLocation.isBlank() || selectedSize.isBlank()) {
                    Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                } else if (password.length < 8 || password != confirmPassword) {
                    Toast.makeText(context, "Passwords must match and be 8+ chars", Toast.LENGTH_SHORT).show()
                } else {
                    // We will update the ViewModel signature to match this during the Retrofit wiring
                    viewModel.registerVendor(firstName, lastName, email, phone, password, businessName, selectedLocation, selectedSize)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = authState !is AuthState.Loading
        ) {
            if (authState is AuthState.Loading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            else Text("Register")
        }
    }
}