package com.example.restockforecastsys.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.restockforecastsys.DailyLogDto
import com.example.restockforecastsys.DailyLogEntryPayload
import com.example.restockforecastsys.RetrofitClient
import com.example.restockforecastsys.SaveDailyLogRequest
import com.example.restockforecastsys.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DailyLogViewModel(
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _recentLogs = MutableStateFlow<List<DailyLogDto>>(emptyList())
    val recentLogs: StateFlow<List<DailyLogDto>> = _recentLogs.asStateFlow()

    private val _saveSuccessMessage = MutableStateFlow<String?>(null)
    val saveSuccessMessage: StateFlow<String?> = _saveSuccessMessage.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    fun clearMessage() {
        _saveSuccessMessage.value = null
    }

    fun loadRecentLogs() {
        val vendorId = sessionManager.getUserId()
        if (vendorId == -1) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.getDailyLogs(vendorId)
                if (response.isSuccessful) {
                    _recentLogs.value = response.body().orEmpty()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveMorningStock(entries: Map<Int, Double>) {
        val vendorId = sessionManager.getUserId()
        if (vendorId == -1 || entries.isEmpty()) return

        _isSaving.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val payload = entries.map { (productId, openingQty) ->
                    DailyLogEntryPayload(
                        product_id = productId,
                        opening_stock = openingQty
                    )
                }
                val request = SaveDailyLogRequest(
                    vendor_id = vendorId,
                    mode = "morning",
                    entries = payload
                )
                val response = RetrofitClient.apiService.saveDailyLogs(request)
                if (response.isSuccessful) {
                    _saveSuccessMessage.value = "Opening stock synced to cloud!"
                    loadRecentLogs()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _saveSuccessMessage.value = "Failed to sync. Check connection."
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun saveEveningSales(entries: Map<Int, Pair<Double, Double>>) {
        val vendorId = sessionManager.getUserId()
        if (vendorId == -1 || entries.isEmpty()) return

        _isSaving.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val payload = entries.map { (productId, pair) ->
                    DailyLogEntryPayload(
                        product_id = productId,
                        sales = pair.first,
                        waste = pair.second
                    )
                }
                val request = SaveDailyLogRequest(
                    vendor_id = vendorId,
                    mode = "evening",
                    entries = payload
                )
                val response = RetrofitClient.apiService.saveDailyLogs(request)
                if (response.isSuccessful) {
                    _saveSuccessMessage.value = "Evening sales & waste saved to cloud!"
                    loadRecentLogs()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _saveSuccessMessage.value = "Failed to sync. Check connection."
            } finally {
                _isSaving.value = false
            }
        }
    }
}