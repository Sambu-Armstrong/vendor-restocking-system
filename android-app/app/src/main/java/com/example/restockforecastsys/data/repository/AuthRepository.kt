package com.example.restockforecastsys.data.repository

import com.example.restockforecastsys.data.dao.RoleDao
import com.example.restockforecastsys.data.dao.UserDao
import com.example.restockforecastsys.data.dao.VendorDao
import com.example.restockforecastsys.data.entity.Role
import com.example.restockforecastsys.data.entity.User
import com.example.restockforecastsys.data.entity.Vendor
import com.example.restockforecastsys.util.PasswordHasher

class AuthRepository(
    private val userDao: UserDao,
    private val roleDao: RoleDao,
    private val vendorDao: VendorDao
) {

    suspend fun registerVendor(
        firstName: String,
        lastName: String,
        email: String,
        phone: String,
        password: String,
        businessName: String
    ): User {
        // 1. Check if email already exists
        val existingUser = userDao.getByEmail(email)
        if (existingUser != null) {
            throw Exception("An account with this email already exists.")
        }

        // 2. Fetch the 'vendor' roleId, or insert it if not present
        val role = roleDao.getByName("vendor")
        val roleId = if (role != null) {
            role.roleId
        } else {
            val newRoleId = roleDao.insert(Role(roleName = "vendor"))
            newRoleId.toInt()
        }

        // 3. Hash password with SALT and build User entity
        val saltedHashedPassword = PasswordHasher.hashPassword(password)
        val newUser = User(
            firstName = firstName,
            lastName = lastName,
            email = email,
            phone = phone,
            password = saltedHashedPassword,
            dateRegistered = System.currentTimeMillis(),
            roleId = roleId
        )

        // 4. Insert User and obtain generated userId
        val generatedUserId = userDao.insert(newUser).toInt()

        // 5. Insert Vendor entry linking to the new userId
        val newVendor = Vendor(
            userId = generatedUserId,
            businessName = businessName,
            locationId = null
        )
        vendorDao.insert(newVendor)

        return newUser.copy(userId = generatedUserId)
    }

    suspend fun login(email: String, password: String): User {
        val user = userDao.getByEmail(email)
            ?: throw Exception("No account found with this email.")

        // Verify input password against the stored salted hash
        val isPasswordValid = PasswordHasher.verifyPassword(
            inputPassword = password,
            storedPassword = user.password
        )

        if (isPasswordValid) {
            return user
        } else {
            throw Exception("Incorrect password.")
        }
    }
}