package com.example.babyallergycheck.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ChildEntity::class, FoodEntity::class, FoodRecordEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class BabyDatabase : RoomDatabase() {
    abstract fun dao(): BabyDao

    companion object {
        @Volatile
        private var instance: BabyDatabase? = null

        fun get(context: Context): BabyDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    BabyDatabase::class.java,
                    "baby_allergy_check.db",
                ).build().also { instance = it }
            }
    }
}
