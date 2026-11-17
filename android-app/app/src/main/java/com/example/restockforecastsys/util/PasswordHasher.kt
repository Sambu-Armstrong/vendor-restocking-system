package com.example.restockforecastsys.util

import java.security.MessageDigest
import java.security.SecureRandom

object PasswordHasher {

    private const val SALT_LENGTH = 16 // 16 bytes = 32 hex characters

    // Used during Registration: generates a new random salt and returns "salt:hash"
    fun hashPassword(password: String): String {
        val salt = generateSalt()
        val hash = hashWithSalt(password, salt)
        return "$salt:$hash"
    }

    // Used during Login: extracts the salt from the stored string and verifies the password
    fun verifyPassword(inputPassword: String, storedPassword: String): Boolean {
        val parts = storedPassword.split(":")

        // Fallback for any older unsalted accounts already in your database
        if (parts.size != 2) {
            return hashWithSalt(inputPassword, "") == storedPassword
        }

        val salt = parts[0]
        val expectedHash = parts[1]
        val actualHash = hashWithSalt(inputPassword, salt)

        return actualHash == expectedHash
    }

    private fun generateSalt(): String {
        val random = SecureRandom()
        val saltBytes = ByteArray(SALT_LENGTH)
        random.nextBytes(saltBytes)
        return saltBytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashWithSalt(password: String, salt: String): String {
        val combined = "$salt$password"
        val bytes = combined.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}