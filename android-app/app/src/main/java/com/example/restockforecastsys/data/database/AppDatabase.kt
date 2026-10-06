package com.example.restockforecastsys.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.restockforecastsys.data.dao.RoleDao
import com.example.restockforecastsys.data.dao.UserDao
import com.example.restockforecastsys.data.dao.VendorDao
import com.example.restockforecastsys.data.entity.Role
import com.example.restockforecastsys.data.entity.User
import com.example.restockforecastsys.data.entity.Vendor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.restockforecastsys.data.dao.ProductDao
import com.example.restockforecastsys.data.entity.Product

@Database(
    entities = [Role::class, User::class, Vendor::class, Product::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun roleDao(): RoleDao
    abstract fun userDao(): UserDao
    abstract fun vendorDao(): VendorDao
    abstract fun productDao(): ProductDao


    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "restock_forecast_db"
                )
                    .addCallback(AppDatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class AppDatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        seedDatabase(database.roleDao())
                    }
                }
            }

            suspend fun seedDatabase(roleDao: RoleDao) {
                // Seed baseline roles required for foreign keys
                roleDao.insert(Role(roleName = "vendor"))
                roleDao.insert(Role(roleName = "admin"))
            }
        }
    }
}