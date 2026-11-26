package com.example.restockforecastsys

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

// --- DATA MODELS (JSON Envelopes: Request = sent to Flask, Response/Dto = received from Flask) ---

data class RegisterRequest(
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String,
    val password: String,
    val location: String,
    val businessName: String,
    val stallSize: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

// Nullable fields (? = null) prevent crashes if login fails and Flask sends only an error message
data class AuthResponse(
    val message: String,
    val token: String? = null,
    val vendor_id: Int? = null,
    val first_name: String? = null
)

data class VendorProfileDto(
    val first_name: String,
    val last_name: String,
    val email: String,
    val phone: String,
    val business_name: String,
    val location: String,
    val stall_size: String
)

data class UpdateProfileRequest(
    val first_name: String,
    val last_name: String,
    val phone: String,
    val business_name: String,
    val location: String,
    val stall_size: String
)

data class CloudProductDto(
    val product_id: Int,
    val display_name: String,
    val model_name: String,
    val current_price: Double
)

data class AddProductRequest(
    val vendor_id: Int,
    val display_name: String,
    val model_name: String,
    val current_price: Double = 0.0
)

data class DailyLogDto(
    val id: Int,
    val vendor_id: Int,
    val product_id: Int,
    val product_name: String,
    val date: String,
    val opening_stock: Double,
    val closing_stock: Double,
    val sales: Double,
    val waste: Double
)

// Holds one product's stock/sales numbers inside the bulk daily log list
data class DailyLogEntryPayload(
    val product_id: Int,
    val opening_stock: Double = 0.0,
    val sales: Double = 0.0,
    val waste: Double = 0.0
)

// Bundles all product entries into a single request so the app makes one network call instead of many
data class SaveDailyLogRequest(
    val vendor_id: Int,
    val mode: String, // "morning" or "evening"
    val entries: List<DailyLogEntryPayload>
)

// Used when Flask only returns a confirmation text message
data class GenericResponse(
    val message: String
)

data class ForecastDto(
    val product_id: Int,
    val product_name: String,
    val target_date: String,
    val predicted_demand: Int,
    val waste_risk: Double,
    val recommended_qty: Int,
    val current_stock: Double
)

// --- API ENDPOINTS (Routes appended to BASE_URL; 'suspend' runs them in background coroutines) ---

interface ApiService {
    @POST("register") // @Body converts Kotlin object to JSON
    suspend fun registerUser(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("login")
    suspend fun loginUser(@Body request: LoginRequest): Response<AuthResponse>

    @GET("profile") // @Header attaches "Bearer <token>" for Flask JWT verification
    suspend fun getProfile(@Header("Authorization") token: String): Response<VendorProfileDto>

    @PUT("profile")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Body request: UpdateProfileRequest
    ): Response<VendorProfileDto>

    @GET("products/{vendor_id}") // @Path injects vendorId directly into the URL
    suspend fun getVendorProducts(@Path("vendor_id") vendorId: Int): Response<List<CloudProductDto>>

    @POST("products")
    suspend fun addVendorProduct(@Body request: AddProductRequest): Response<GenericResponse>

    @DELETE("products/{vendor_id}/{product_id}")
    suspend fun deleteVendorProduct(
        @Path("vendor_id") vendorId: Int,
        @Path("product_id") productId: Int
    ): Response<GenericResponse>

    @GET("daily-logs/{vendor_id}")
    suspend fun getDailyLogs(@Path("vendor_id") vendorId: Int): Response<List<DailyLogDto>>

    @POST("daily-logs")
    suspend fun saveDailyLogs(@Body request: SaveDailyLogRequest): Response<GenericResponse>

    @GET("forecast") // Protected route: requires JWT token to identify vendor & check subscription
    suspend fun getForecasts(@Header("Authorization") token: String): Response<List<ForecastDto>>
}