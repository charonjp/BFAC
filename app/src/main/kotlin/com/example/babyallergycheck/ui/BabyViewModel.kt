package com.example.babyallergycheck.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.babyallergycheck.data.BabyDatabase
import com.example.babyallergycheck.data.BabyMasters
import com.example.babyallergycheck.data.BabyRepository
import com.example.babyallergycheck.data.ChildEntity
import com.example.babyallergycheck.data.ExportFoodRow
import com.example.babyallergycheck.data.FoodEntity
import com.example.babyallergycheck.data.FoodWithRecord
import com.example.babyallergycheck.export.ExportManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val children: List<ChildEntity> = emptyList(),
    val selectedChildId: Long? = null,
    val selectedPhaseCode: String = BabyMasters.phases.first().code,
    val searchQuery: String = "",
    val selectedCategoryCode: String? = null,
    val rows: List<FoodWithRecord> = emptyList(),
)

class BabyViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = BabyRepository(BabyDatabase.get(application).dao())

    private val selectedChildId = MutableStateFlow<Long?>(null)
    private val selectedPhaseCode = MutableStateFlow(BabyMasters.phases.first().code)
    private val searchQuery = MutableStateFlow("")
    private val selectedCategoryCode = MutableStateFlow<String?>(null)
    private val _message = MutableStateFlow<String?>(null)

    val message: StateFlow<String?> = _message.asStateFlow()

    val children: StateFlow<List<ChildEntity>> =
        repository.children.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val foods: StateFlow<List<FoodEntity>> =
        repository.foods.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val visibleRows: StateFlow<List<FoodWithRecord>> =
        combine(selectedChildId, selectedPhaseCode) { childId, phaseCode -> childId to phaseCode }
            .flatMapLatest { (childId, phaseCode) ->
                if (childId == null) flowOf(emptyList()) else repository.observeFoodRows(childId, phaseCode)
            }
            .combine(searchQuery) { rows, query ->
                if (query.isBlank()) rows else rows.filter { it.name.contains(query.trim(), ignoreCase = true) }
            }
            .combine(selectedCategoryCode) { rows, categoryCode ->
                categoryCode?.let { code -> rows.filter { it.categoryCode == code } } ?: rows
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val mainUiState: StateFlow<MainUiState> =
        combine(
            children,
            selectedChildId,
            selectedPhaseCode,
            searchQuery,
            selectedCategoryCode,
            visibleRows,
        ) { children, childId, phaseCode, query, categoryCode, rows ->
            MainUiState(
                children = children,
                selectedChildId = childId,
                selectedPhaseCode = phaseCode,
                searchQuery = query,
                selectedCategoryCode = categoryCode,
                rows = rows,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.seedDefaultsIfNeeded()
        }
        viewModelScope.launch {
            children.collect { list ->
                val current = selectedChildId.value
                if (list.isEmpty()) {
                    selectedChildId.value = null
                } else if (current == null || list.none { it.id == current }) {
                    selectedChildId.value = list.first().id
                }
            }
        }
    }

    fun selectChild(id: Long) {
        selectedChildId.value = id
    }

    fun selectPhase(code: String) {
        selectedPhaseCode.value = code
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setCategoryFilter(code: String?) {
        selectedCategoryCode.value = code
    }

    fun addChild(name: String) {
        if (name.isBlank()) {
            _message.value = "名前を入力してください"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val id = repository.addChild(name)
            selectedChildId.value = id
        }
    }

    fun updateChild(child: ChildEntity, name: String) {
        if (name.isBlank()) {
            _message.value = "名前を入力してください"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateChild(child, name)
            _message.value = "子供マスタを更新しました"
        }
    }

    fun deleteChild(child: ChildEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val deleted = repository.deleteChildIfAllowed(child.id)
            _message.value = if (deleted) "子供を削除しました" else "最低1人の登録が必要です"
        }
    }

    fun createFood(phaseCode: String, categoryCode: String, name: String) {
        if (name.isBlank()) {
            _message.value = "食材名を入力してください"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.createFood(phaseCode, categoryCode, name)
            _message.value = "食材を登録しました"
        }
    }

    fun updateFood(food: FoodEntity, phaseCode: String, categoryCode: String, name: String) {
        if (name.isBlank()) {
            _message.value = "食材名を入力してください"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateFood(food, phaseCode, categoryCode, name)
            _message.value = "食材マスタを更新しました"
        }
    }

    fun updateAttemptDate(foodId: Long, attempt: Int, date: String?) {
        val childId = selectedChildId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateAttemptDate(childId, foodId, attempt, date)
        }
    }

    fun updateAttemptMemo(foodId: Long, attempt: Int, memo: String, reaction: Boolean) {
        val childId = selectedChildId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateAttemptMemo(childId, foodId, attempt, memo, reaction)
        }
    }

    fun exportCsv() {
        exportCurrent { child, rows ->
            ExportManager.exportCsv(getApplication(), child, rows)
            "CSVをダウンロードに出力しました"
        }
    }

    fun exportPdf() {
        exportCurrent { child, rows ->
            ExportManager.exportPdf(getApplication(), child, rows)
            "PDFをダウンロードに出力しました"
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun exportCurrent(exporter: (ChildEntity, List<ExportFoodRow>) -> String) {
        val childId = selectedChildId.value
        if (childId == null) {
            _message.value = "子供を登録してください"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val child = repository.getChild(childId) ?: error("子供が見つかりません")
                val rows = repository.exportRows(childId)
                exporter(child, rows)
            }.onSuccess { message ->
                _message.value = message
            }.onFailure { error ->
                _message.value = "出力に失敗しました: ${error.message.orEmpty()}"
            }
        }
    }
}
