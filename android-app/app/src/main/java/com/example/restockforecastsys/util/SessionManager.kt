package com.example.restockforecastsys.util

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    // Creates a secure local file named "auth_prefs" to store session data
    private val prefs: SharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USER_ID = "USER_ID"
    }

    // Called after successful login or registration
    fun saveUserId(userId: Int) {
        prefs.edit().putInt(KEY_USER_ID, userId).apply()
    }

    // Retrieves the current logged-in user. Returns -1 if no one is logged in.
    fun getUserId(): Int {
        return prefs.getInt(KEY_USER_ID, -1)
    }

    // Called when the user clicks Logout
    fun clearSession() {
        prefs.edit().remove(KEY_USER_ID).apply()
    }

    // A quick check to see which screen to show when the app opens
    fun isLoggedIn(): Boolean {
        return getUserId() != -1
    }
}