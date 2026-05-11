package com.example.babyallergycheck.data

import kotlinx.coroutines.flow.Flow

class BabyRepository(
    private val dao: BabyDao,
) {
    val children: Flow<List<ChildEntity>> = dao.observeChildren()
    val foods: Flow<List<FoodEntity>> = dao.observeFoods()

    suspend fun seedDefaultsIfNeeded() {
        if (dao.foodCount() == 0) {
            dao.insertFoods(BabyMasters.defaultFoods)
        }
    }

    suspend fun addChild(name: String): Long {
        val nextNumber = nextChildNumber()
        return dao.insertChild(ChildEntity(number = nextNumber, name = name.trim()))
    }

    suspend fun updateChild(child: ChildEntity, name: String) {
        dao.updateChild(child.copy(name = name.trim()))
    }

    suspend fun deleteChildIfAllowed(id: Long): Boolean {
        if (dao.childCount() <= 1) return false
        dao.deleteRecordsForChild(id)
        dao.deleteChild(id)
        return true
    }

    suspend fun createFood(
        phaseCode: String,
        categoryCode: String,
        name: String,
    ) {
        val nextSerial = (dao.maxSerial(phaseCode, categoryCode) ?: 0) + 1
        dao.insertFood(
            FoodEntity(
                code = BabyMasters.codeFor(phaseCode, categoryCode, nextSerial),
                phaseCode = phaseCode,
                categoryCode = categoryCode,
                serial = nextSerial,
                name = name.trim(),
            ),
        )
    }

    suspend fun updateFood(
        food: FoodEntity,
        phaseCode: String,
        categoryCode: String,
        name: String,
    ) {
        if (food.phaseCode == phaseCode && food.categoryCode == categoryCode) {
            dao.updateFood(food.copy(name = name.trim()))
            return
        }

        val nextSerial = (dao.maxSerial(phaseCode, categoryCode) ?: 0) + 1
        dao.updateFood(
            food.copy(
                phaseCode = phaseCode,
                categoryCode = categoryCode,
                serial = nextSerial,
                code = BabyMasters.codeFor(phaseCode, categoryCode, nextSerial),
                name = name.trim(),
            ),
        )
    }

    suspend fun updateAttemptDate(
        childId: Long,
        foodId: Long,
        attempt: Int,
        date: String?,
    ) {
        val current = dao.getRecord(childId, foodId) ?: FoodRecordEntity(childId = childId, foodId = foodId)
        val next = when (attempt) {
            1 -> current.copy(firstDate = date, updatedAt = System.currentTimeMillis())
            else -> current.copy(secondDate = date, updatedAt = System.currentTimeMillis())
        }
        dao.upsertRecord(next)
    }

    suspend fun updateAttemptMemo(
        childId: Long,
        foodId: Long,
        attempt: Int,
        memo: String,
        reaction: Boolean,
    ) {
        val current = dao.getRecord(childId, foodId) ?: FoodRecordEntity(childId = childId, foodId = foodId)
        val next = when (attempt) {
            1 -> current.copy(
                firstMemo = memo.trim(),
                firstReaction = reaction,
                updatedAt = System.currentTimeMillis(),
            )

            else -> current.copy(
                secondMemo = memo.trim(),
                secondReaction = reaction,
                updatedAt = System.currentTimeMillis(),
            )
        }
        dao.upsertRecord(next)
    }

    fun observeFoodRows(childId: Long, phaseCode: String): Flow<List<FoodWithRecord>> =
        dao.observeFoodRows(childId, phaseCode)

    suspend fun getFood(id: Long): FoodEntity? = dao.getFood(id)

    suspend fun exportRows(childId: Long): List<ExportFoodRow> = dao.exportRows(childId)

    suspend fun getChild(id: Long): ChildEntity? = dao.getChild(id)

    private suspend fun nextChildNumber(): String {
        val used = dao.getChildren().mapNotNull { it.number.toIntOrNull() }.toSet()
        val next = (1..99).firstOrNull { it !in used } ?: 99
        return next.toString().padStart(2, '0')
    }
}
