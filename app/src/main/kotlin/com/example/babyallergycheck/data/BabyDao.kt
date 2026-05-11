package com.example.babyallergycheck.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BabyDao {
    @Query("SELECT * FROM children ORDER BY number")
    fun observeChildren(): Flow<List<ChildEntity>>

    @Query("SELECT * FROM children WHERE id = :id LIMIT 1")
    suspend fun getChild(id: Long): ChildEntity?

    @Query("SELECT * FROM children ORDER BY number")
    suspend fun getChildren(): List<ChildEntity>

    @Insert
    suspend fun insertChild(child: ChildEntity): Long

    @Update
    suspend fun updateChild(child: ChildEntity)

    @Query("DELETE FROM children WHERE id = :id")
    suspend fun deleteChild(id: Long)

    @Query("DELETE FROM food_records WHERE childId = :childId")
    suspend fun deleteRecordsForChild(childId: Long)

    @Query("SELECT COUNT(*) FROM children")
    suspend fun childCount(): Int

    @Query("SELECT * FROM foods WHERE isActive = 1 ORDER BY phaseCode, categoryCode, serial")
    fun observeFoods(): Flow<List<FoodEntity>>

    @Query("SELECT * FROM foods WHERE id = :id LIMIT 1")
    suspend fun getFood(id: Long): FoodEntity?

    @Query("SELECT COUNT(*) FROM foods")
    suspend fun foodCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFoods(foods: List<FoodEntity>)

    @Insert
    suspend fun insertFood(food: FoodEntity): Long

    @Update
    suspend fun updateFood(food: FoodEntity)

    @Query("SELECT MAX(serial) FROM foods WHERE phaseCode = :phaseCode AND categoryCode = :categoryCode")
    suspend fun maxSerial(phaseCode: String, categoryCode: String): Int?

    @Query(
        """
        SELECT
            foods.id AS foodId,
            foods.code AS code,
            foods.phaseCode AS phaseCode,
            foods.categoryCode AS categoryCode,
            foods.serial AS serial,
            foods.name AS name,
            food_records.firstDate AS firstDate,
            food_records.secondDate AS secondDate,
            food_records.firstMemo AS firstMemo,
            food_records.secondMemo AS secondMemo,
            food_records.firstReaction AS firstReaction,
            food_records.secondReaction AS secondReaction
        FROM foods
        LEFT JOIN food_records
            ON food_records.foodId = foods.id
            AND food_records.childId = :childId
        WHERE foods.isActive = 1
            AND foods.phaseCode = :phaseCode
        ORDER BY foods.categoryCode, foods.serial
        """,
    )
    fun observeFoodRows(childId: Long, phaseCode: String): Flow<List<FoodWithRecord>>

    @Query(
        """
        SELECT
            foods.code AS code,
            foods.phaseCode AS phaseCode,
            foods.categoryCode AS categoryCode,
            foods.serial AS serial,
            foods.name AS name,
            food_records.firstDate AS firstDate,
            food_records.secondDate AS secondDate,
            food_records.firstMemo AS firstMemo,
            food_records.secondMemo AS secondMemo,
            food_records.firstReaction AS firstReaction,
            food_records.secondReaction AS secondReaction
        FROM foods
        LEFT JOIN food_records
            ON food_records.foodId = foods.id
            AND food_records.childId = :childId
        WHERE foods.isActive = 1
        ORDER BY foods.phaseCode, foods.categoryCode, foods.serial
        """,
    )
    suspend fun exportRows(childId: Long): List<ExportFoodRow>

    @Query("SELECT * FROM food_records WHERE childId = :childId AND foodId = :foodId LIMIT 1")
    suspend fun getRecord(childId: Long, foodId: Long): FoodRecordEntity?

    @Upsert
    suspend fun upsertRecord(record: FoodRecordEntity)
}
