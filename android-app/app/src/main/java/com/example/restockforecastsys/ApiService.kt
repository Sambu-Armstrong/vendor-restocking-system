package com.example.restockforecastsys
// Change this if your package name is different

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

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

data class AuthResponse(
    val message: String,
    val token: String? = null,
    val vendor_id: Int? = null
)

interface ApiService {
    @POST("register")
    suspend fun registerUser(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("login")
    suspend fun loginUser(@Body request: LoginRequest): Response<AuthResponse>
}