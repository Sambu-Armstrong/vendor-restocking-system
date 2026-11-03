package com.example.restockforecastsys.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.restockforecastsys.LoginRequest
import com.example.restockforecastsys.RegisterRequest
import com.example.restockforecastsys.RetrofitClient
import com.example.restockforecastsys.data.repository.AuthRepository
import com.example.restockforecastsys.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    var authState: AuthState by mutableStateOf(AuthState.Idle)
        private set

    fun resetState() {
        authState = AuthState.Idle
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            authState = AuthState.Error("Please enter both email and password.")
            return
        }

        authState = AuthState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = LoginRequest(email.trim(), password)
                val response = RetrofitClient.apiService.loginUser(request)

                if (response.isSuccessful) {
                    val body = response.body()
                    val vendorId = body?.vendor_id

                    if (vendorId != null) {
                        sessionManager.clearSession()
                        sessionManager.saveUserId(vendorId)
                        body.first_name?.let {
                            sessionManager.saveUserName(it)
                        }
                        body.token?.let {
                            sessionManager.saveToken(it)
                        }
                    }
                    authState = AuthState.Success
                } else {
                    authState = AuthState.Error("Invalid email or password.")
                }
            } catch (e: Exception) {
                authState = AuthState.Error(e.message ?: "Network error. Check Wi-Fi connection.")
            }
        }
    }

    fun registerVendor(
        firstName: String,
        lastName: String,
        email: String,
        phone: String,
        password: String,
        businessName: String,
        location: String,
        stallSize: String
    ) {
        authState = AuthState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = RegisterRequest(
                    firstName = firstName.trim(),
                    lastName = lastName.trim(),
                    email = email.trim(),
                    phone = phone.trim(),
                    password = password,
                    location = location,
                    businessName = businessName.trim(),
                    stallSize = stallSize
                )

                val response = RetrofitClient.apiService.registerUser(request)

                if (response.isSuccessful) {
                    authState = AuthState.Success
                } else {
                    authState = AuthState.Error("Registration failed. Email might already exist.")
                }
            } catch (e: Exception) {
                authState = AuthState.Error(e.message ?: "Network error. Check Wi-Fi connection.")
            }
        }
    }

    fun logout() {
        sessionManager.clearSession()
        authState = AuthState.Idle
    }

    fun isUserLoggedIn(): Boolean {
        return sessionManager.isLoggedIn()
    }

    fun getUserName(): String {
        return sessionManager.getUserName() ?: "Vendor"
    }
}