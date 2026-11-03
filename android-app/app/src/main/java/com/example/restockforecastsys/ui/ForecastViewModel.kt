package com.example.restockforecastsys.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.restockforecastsys.ForecastDto
import com.example.restockforecastsys.RetrofitClient
import com.example.restockforecastsys.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ForecastViewModel(
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _forecasts = MutableStateFlow<List<ForecastDto>>(emptyList())
    val forecasts: StateFlow<List<ForecastDto>> = _forecasts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun loadForecasts() {
        val token = sessionManager.getToken()
        if (token.isNullOrEmpty()) {
            _errorMessage.value = "Authentication token missing. Please log in again."
            return
        }

        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Prepend "Bearer " as required by JWT standard and your Flask code
                val response = RetrofitClient.apiService.getForecasts("Bearer $token")

                if (response.isSuccessful) {
                    _forecasts.value = response.body().orEmpty()
                } else if (response.code() == 401) {
                    _errorMessage.value = "Session expired. Please log out and log in again."
                } else if (response.code() == 403) {
                    _errorMessage.value = "Active subscription required to view forecasts."
                } else {
                    _errorMessage.value = "Failed to load: ${response.message()}"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = "Network error. Check Wi-Fi connection."
            } finally {
                _isLoading.value = false
            }
        }
    }
}