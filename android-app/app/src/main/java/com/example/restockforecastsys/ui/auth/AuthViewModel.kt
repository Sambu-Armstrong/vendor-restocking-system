package com.example.restockforecastsys.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.restockforecastsys.LoginRequest
import com.example.restockforecastsys.RegisterRequest
import com.example.restockforecastsys.RetrofitClient
import com.example.restockforecastsys.UpdateProfileRequest
import com.example.restockforecastsys.VendorProfileDto
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

    var vendorProfile: VendorProfileDto? by mutableStateOf(null)
        private set

    var profileLoading: Boolean by mutableStateOf(false)
        private set

    var profileStatusMessage: String? by mutableStateOf(null)
        private set

    fun resetState() {
        authState = AuthState.Idle
    }

    fun clearProfileStatus() {
        profileStatusMessage = null
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

    fun loadProfile() {
        val token = sessionManager.getToken() ?: return
        profileLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.getProfile("Bearer $token")
                if (response.isSuccessful) {
                    vendorProfile = response.body()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                profileLoading = false
            }
        }
    }

    fun updateProfile(
        firstName: String,
        lastName: String,
        phone: String,
        businessName: String,
        location: String,
        stallSize: String
    ) {
        val token = sessionManager.getToken()
        if (token.isNullOrEmpty()) {
            profileStatusMessage = "Session expired. Please log in again."
            return
        }

        profileLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = UpdateProfileRequest(
                    first_name = firstName.trim(),
                    last_name = lastName.trim(),
                    phone = phone.trim(),
                    business_name = businessName.trim(),
                    location = location,
                    stall_size = stallSize
                )
                val response = RetrofitClient.apiService.updateProfile("Bearer $token", request)
                if (response.isSuccessful) {
                    val updated = response.body()
                    vendorProfile = updated
                    updated?.first_name?.let { sessionManager.saveUserName(it) }
                    profileStatusMessage = "Profile updated successfully"
                } else {
                    profileStatusMessage = "Failed to update profile"
                }
            } catch (e: Exception) {
                profileStatusMessage = "Network error. Check Wi-Fi connection."
            } finally {
                profileLoading = false
            }
        }
    }

    fun logout() {
        vendorProfile = null
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