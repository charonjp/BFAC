package com.example.babyallergycheck.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "children",
    indices = [Index(value = ["number"], unique = true)],
)
data class ChildEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val number: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "foods",
    indices = [Index(value = ["code"], unique = true)],
)
data class FoodEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val code: String,
    val phaseCode: String,
    val categoryCode: String,
    val serial: Int,
    val name: String,
    val isActive: Boolean = true,
)

@Entity(
    tableName = "food_records",
    primaryKeys = ["childId", "foodId"],
)
data class FoodRecordEntity(
    val childId: Long,
    val foodId: Long,
    val firstDate: String? = null,
    val secondDate: String? = null,
    val firstMemo: String = "",
    val secondMemo: String = "",
    val firstReaction: Boolean = false,
    val secondReaction: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

data class FoodWithRecord(
    val foodId: Long,
    val code: String,
    val phaseCode: String,
    val categoryCode: String,
    val serial: Int,
    val name: String,
    val firstDate: String?,
    val secondDate: String?,
    val firstMemo: String?,
    val secondMemo: String?,
    val firstReaction: Boolean?,
    val secondReaction: Boolean?,
)

data class ExportFoodRow(
    val code: String,
    val phaseCode: String,
    val categoryCode: String,
    val serial: Int,
    val name: String,
    val firstDate: String?,
    val secondDate: String?,
    val firstMemo: String?,
    val secondMemo: String?,
    val firstReaction: Boolean?,
    val secondReaction: Boolean?,
)
