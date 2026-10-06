package com.example.restockforecastsys.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.restockforecastsys.data.entity.User
import com.example.restockforecastsys.data.repository.AuthRepository
import com.example.restockforecastsys.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    var authState: AuthState by mutableStateOf(AuthState.Idle as AuthState)
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
                val user = authRepository.login(email.trim(), password)
                sessionManager.saveUserId(user.userId)
                authState = AuthState.Success(user)
            } catch (e: Exception) {
                authState = AuthState.Error(e.message ?: "Authentication failed.")
            }
        }
    }

    fun registerVendor(
        name: String,
        email: String,
        phone: String,
        password: String,
        businessName: String
    ) {
        if (name.isBlank() || email.isBlank() || phone.isBlank() || password.isBlank() || businessName.isBlank()) {
            authState = AuthState.Error("All fields are required.")
            return
        }

        authState = AuthState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val user = authRepository.registerVendor(
                    name = name.trim(),
                    email = email.trim(),
                    phone = phone.trim(),
                    password = password,
                    businessName = businessName.trim()
                )
                sessionManager.saveUserId(user.userId)
                authState = AuthState.Success(user)
            } catch (e: Exception) {
                authState = AuthState.Error(e.message ?: "Registration failed.")
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
}