package com.example.babyallergycheck.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.babyallergycheck.data.BabyMasters
import com.example.babyallergycheck.data.ChildEntity
import com.example.babyallergycheck.data.FoodEntity
import com.example.babyallergycheck.data.FoodWithRecord
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private enum class AppScreen {
    Main,
    Settings,
}

private enum class SettingsSection {
    Menu,
    Children,
    Foods,
}

@Composable
fun BabyApp(
    viewModel: BabyViewModel,
) {
    val uiState by viewModel.mainUiState.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var screen by rememberSaveable { mutableStateOf(AppScreen.Main) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        if (uiState.children.isEmpty()) {
            FirstChildScreen(
                snackbarHostState = snackbarHostState,
                onAddChild = viewModel::addChild,
            )
            return@Surface
        }

        when (screen) {
            AppScreen.Main -> MainScreen(
                uiState = uiState,
                snackbarHostState = snackbarHostState,
                onOpenSettings = { screen = AppScreen.Settings },
                onSelectChild = viewModel::selectChild,
                onSelectPhase = viewModel::selectPhase,
                onSearchChange = viewModel::setSearchQuery,
                onCategoryChange = viewModel::setCategoryFilter,
                onDateChange = viewModel::updateAttemptDate,
                onMemoChange = viewModel::updateAttemptMemo,
            )

            AppScreen.Settings -> SettingsScreen(
                viewModel = viewModel,
                snackbarHostState = snackbarHostState,
                onBack = { screen = AppScreen.Main },
            )
        }
    }
}

@Composable
private fun FirstChildScreen(
    snackbarHostState: SnackbarHostState,
    onAddChild: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "最初に子供を登録",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "最低1人の登録が必要です。番号は01から自動採番します。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("子供の名前") },
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onAddChild(name) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("登録してはじめる")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    uiState: MainUiState,
    snackbarHostState: SnackbarHostState,
    onOpenSettings: () -> Unit,
    onSelectChild: (Long) -> Unit,
    onSelectPhase: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onDateChange: (Long, Int, String?) -> Unit,
    onMemoChange: (Long, Int, String, Boolean) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { BabyMasters.phases.size })
    val selectedPhaseIndex = BabyMasters.phases.indexOfFirst { it.code == uiState.selectedPhaseCode }.coerceAtLeast(0)
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedPhaseIndex) {
        if (pagerState.currentPage != selectedPhaseIndex) {
            pagerState.animateScrollToPage(selectedPhaseIndex)
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        onSelectPhase(BabyMasters.phases[pagerState.currentPage].code)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("離乳食チェック") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "設定")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ChildHeader(
                children = uiState.children,
                selectedChildId = uiState.selectedChildId,
                onSelectChild = onSelectChild,
            )
            Text(
                text = BabyMasters.phaseLabel(uiState.selectedPhaseCode),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            TabRow(selectedTabIndex = selectedPhaseIndex) {
                BabyMasters.phases.forEachIndexed { index, phase ->
                    Tab(
                        selected = selectedPhaseIndex == index,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                            onSelectPhase(phase.code)
                        },
                        text = { Text("${phase.label}\n${phase.ageLabel}") },
                    )
                }
            }
            SearchAndFilters(
                searchQuery = uiState.searchQuery,
                selectedCategoryCode = uiState.selectedCategoryCode,
                onSearchChange = onSearchChange,
                onCategoryChange = onCategoryChange,
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) {
                FoodList(
                    rows = uiState.rows,
                    onDateChange = onDateChange,
                    onMemoChange = onMemoChange,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChildHeader(
    children: List<ChildEntity>,
    selectedChildId: Long?,
    onSelectChild: (Long) -> Unit,
) {
    val selected = children.firstOrNull { it.id == selectedChildId } ?: children.first()
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        if (children.size == 1) {
            Text(
                text = selected.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "子供番号 ${selected.number}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
            ) {
                OutlinedTextField(
                    value = "${selected.name}  (${selected.number})",
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    readOnly = true,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    label = { Text("子供") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    children.forEach { child ->
                        DropdownMenuItem(
                            text = { Text("${child.number}  ${child.name}") },
                            onClick = {
                                onSelectChild(child.id)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchAndFilters(
    searchQuery: String,
    selectedCategoryCode: String?,
    onSearchChange: (String) -> Unit,
    onCategoryChange: (String?) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("検索") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "検索をクリア")
                    }
                }
            },
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(
                selected = selectedCategoryCode == null,
                onClick = { onCategoryChange(null) },
                label = { Text("すべて") },
            )
            BabyMasters.categories.forEach { category ->
                FilterChip(
                    selected = selectedCategoryCode == category.code,
                    onClick = { onCategoryChange(category.code) },
                    label = { Text(category.label) },
                )
            }
        }
    }
}

@Composable
private fun FoodList(
    rows: List<FoodWithRecord>,
    onDateChange: (Long, Int, String?) -> Unit,
    onMemoChange: (Long, Int, String, Boolean) -> Unit,
) {
    if (rows.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "該当する食材がありません",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(rows, key = { it.foodId }) { row ->
            FoodRecordCard(
                row = row,
                onDateChange = onDateChange,
                onMemoChange = onMemoChange,
            )
        }
    }
}

@Composable
private fun FoodRecordCard(
    row: FoodWithRecord,
    onDateChange: (Long, Int, String?) -> Unit,
    onMemoChange: (Long, Int, String, Boolean) -> Unit,
) {
    val status = statusStyle(row)
    var memoDialog by remember { mutableStateOf<MemoTarget?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = status.background),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = BabyMasters.categoryLabel(row.categoryCode),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = row.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatusChip(status)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AttemptEditor(
                    title = "1回目",
                    date = row.firstDate,
                    memo = row.firstMemo.orEmpty(),
                    reaction = row.firstReaction == true,
                    modifier = Modifier.weight(1f),
                    onDateChange = { onDateChange(row.foodId, 1, it) },
                    onOpenMemo = {
                        memoDialog = MemoTarget(
                            attempt = 1,
                            title = "${row.name} 1回目メモ",
                            memo = row.firstMemo.orEmpty(),
                            reaction = row.firstReaction == true,
                        )
                    },
                )
                AttemptEditor(
                    title = "2回目",
                    date = row.secondDate,
                    memo = row.secondMemo.orEmpty(),
                    reaction = row.secondReaction == true,
                    modifier = Modifier.weight(1f),
                    onDateChange = { onDateChange(row.foodId, 2, it) },
                    onOpenMemo = {
                        memoDialog = MemoTarget(
                            attempt = 2,
                            title = "${row.name} 2回目メモ",
                            memo = row.secondMemo.orEmpty(),
                            reaction = row.secondReaction == true,
                        )
                    },
                )
            }
        }
    }

    memoDialog?.let { target ->
        MemoDialog(
            target = target,
            onDismiss = { memoDialog = null },
            onSave = { memo, reaction ->
                onMemoChange(row.foodId, target.attempt, memo, reaction)
                memoDialog = null
            },
        )
    }
}

@Composable
private fun AttemptEditor(
    title: String,
    date: String?,
    memo: String,
    reaction: Boolean,
    modifier: Modifier = Modifier,
    onDateChange: (String?) -> Unit,
    onOpenMemo: () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DateButton(
                date = date,
                modifier = Modifier.weight(1f),
                onDateChange = onDateChange,
            )
            IconButton(
                onClick = onOpenMemo,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.NoteAlt,
                    contentDescription = "$title メモ",
                    tint = when {
                        reaction -> MaterialTheme.colorScheme.error
                        memo.isNotBlank() -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateButton(
    date: String?,
    modifier: Modifier = Modifier,
    onDateChange: (String?) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val label = date?.let { formatDisplayDate(it) } ?: "日付"

    OutlinedButton(
        onClick = { showPicker = true },
        modifier = modifier.height(40.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Icon(
            imageVector = Icons.Default.CalendarMonth,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    if (showPicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = date?.toLocalDateMillis() ?: todayMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { onDateChange(it.toIsoDate()) }
                        showPicker = false
                    },
                ) {
                    Text("決定")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { onDateChange(null); showPicker = false }) {
                        Text("クリア")
                    }
                    TextButton(onClick = { showPicker = false }) {
                        Text("キャンセル")
                    }
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private data class MemoTarget(
    val attempt: Int,
    val title: String,
    val memo: String,
    val reaction: Boolean,
)

@Composable
private fun MemoDialog(
    target: MemoTarget,
    onDismiss: () -> Unit,
    onSave: (String, Boolean) -> Unit,
) {
    var memo by remember(target) { mutableStateOf(target.memo) }
    var reaction by remember(target) { mutableStateOf(target.reaction) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(target.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("反応あり")
                    Switch(checked = reaction, onCheckedChange = { reaction = it })
                }
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    label = { Text("症状・様子・医師相談など") },
                    minLines = 4,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(memo, reaction) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        },
    )
}

@Composable
private fun StatusChip(status: StatusStyle) {
    AssistChip(
        onClick = {},
        label = { Text(status.label) },
        colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
            containerColor = status.chip,
            labelColor = status.text,
        ),
        border = null,
    )
}

private data class StatusStyle(
    val label: String,
    val background: Color,
    val chip: Color,
    val text: Color,
)

@Composable
private fun statusStyle(row: FoodWithRecord): StatusStyle {
    val first = !row.firstDate.isNullOrBlank()
    val second = !row.secondDate.isNullOrBlank()
    val reaction = row.firstReaction == true || row.secondReaction == true
    val baseLabel = when {
        !first && !second -> "未実施"
        first && !second -> "1回目済"
        !first && second -> "2回目済"
        else -> "完了"
    }
    if (reaction) {
        return StatusStyle(
            label = "$baseLabel・反応あり",
            background = Color(0xFFFFF0EE),
            chip = Color(0xFFFFD8D2),
            text = Color(0xFF8C1D18),
        )
    }
    return when {
        !first && !second -> StatusStyle(baseLabel, Color(0xFFFFFFFF), Color(0xFFECE4DC), Color(0xFF5E524C))
        first && !second -> StatusStyle(baseLabel, Color(0xFFF2FBF7), Color(0xFFCBEEDD), Color(0xFF155F38))
        !first && second -> StatusStyle(baseLabel, Color(0xFFFFF8E3), Color(0xFFFFE7A3), Color(0xFF765100))
        else -> StatusStyle(baseLabel, Color(0xFFEFF8FF), Color(0xFFCFEAFF), Color(0xFF174E77))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    viewModel: BabyViewModel,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
) {
    var section by rememberSaveable { mutableStateOf(SettingsSection.Menu) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        when (section) {
                            SettingsSection.Menu -> "設定"
                            SettingsSection.Children -> "子供マスタ"
                            SettingsSection.Foods -> "食材マスタ"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (section == SettingsSection.Menu) onBack() else section = SettingsSection.Menu
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (section) {
                SettingsSection.Menu -> SettingsMenu(
                    onChildren = { section = SettingsSection.Children },
                    onFoods = { section = SettingsSection.Foods },
                    onPdf = viewModel::exportPdf,
                    onCsv = viewModel::exportCsv,
                )

                SettingsSection.Children -> ChildMasterScreen(viewModel)
                SettingsSection.Foods -> FoodMasterScreen(viewModel)
            }
        }
    }
}

@Composable
private fun SettingsMenu(
    onChildren: () -> Unit,
    onFoods: () -> Unit,
    onPdf: () -> Unit,
    onCsv: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SettingsButton(Icons.Default.Person, "子供マスタ", onChildren)
        SettingsButton(Icons.Default.Restaurant, "食材マスタ", onFoods)
        SettingsButton(Icons.Default.PictureAsPdf, "現在までの内容をPDFに出力", onPdf)
        SettingsButton(Icons.Default.TableChart, "現在までの内容をCSVに出力", onCsv)
    }
}

@Composable
private fun SettingsButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ChildMasterScreen(
    viewModel: BabyViewModel,
) {
    val children by viewModel.children.collectAsState()
    var editing by remember { mutableStateOf<ChildEntity?>(null) }
    var adding by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(children, key = { it.id }) { child ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            ) {
                ListItem(
                    headlineContent = { Text(child.name) },
                    supportingContent = { Text("番号 ${child.number}") },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { editing = child }) {
                                Icon(Icons.Default.Edit, contentDescription = "編集")
                            }
                            IconButton(onClick = { viewModel.deleteChild(child) }) {
                                Icon(Icons.Default.Delete, contentDescription = "削除")
                            }
                        }
                    },
                )
            }
        }
        item {
            Button(
                onClick = { adding = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("子供を追加")
            }
        }
    }

    if (adding) {
        ChildEditDialog(
            title = "子供を追加",
            initialName = "",
            onDismiss = { adding = false },
            onSave = {
                viewModel.addChild(it)
                adding = false
            },
        )
    }
    editing?.let { child ->
        ChildEditDialog(
            title = "子供を修正",
            initialName = child.name,
            onDismiss = { editing = null },
            onSave = {
                viewModel.updateChild(child, it)
                editing = null
            },
        )
    }
}

@Composable
private fun ChildEditDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("名前") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FoodMasterScreen(
    viewModel: BabyViewModel,
) {
    val foods by viewModel.foods.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var phaseFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<FoodEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    val filtered = foods.filter { food ->
        (query.isBlank() || food.name.contains(query.trim(), ignoreCase = true) || food.code.contains(query.trim())) &&
            (phaseFilter == null || food.phaseCode == phaseFilter) &&
            (categoryFilter == null || food.categoryCode == categoryFilter)
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("新規登録") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("食材検索") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(selected = phaseFilter == null, onClick = { phaseFilter = null }, label = { Text("全期") })
                BabyMasters.phases.forEach { phase ->
                    FilterChip(
                        selected = phaseFilter == phase.code,
                        onClick = { phaseFilter = phase.code },
                        label = { Text(phase.label) },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = categoryFilter == null, onClick = { categoryFilter = null }, label = { Text("全分類") })
                BabyMasters.categories.forEach { category ->
                    FilterChip(
                        selected = categoryFilter == category.code,
                        onClick = { categoryFilter = category.code },
                        label = { Text(category.label) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 92.dp),
            ) {
                items(filtered, key = { it.id }) { food ->
                    ListItem(
                        headlineContent = { Text(food.name) },
                        supportingContent = {
                            Text("${BabyMasters.phaseLabel(food.phaseCode)} / ${BabyMasters.categoryLabel(food.categoryCode)} / ${food.code}")
                        },
                        trailingContent = {
                            IconButton(onClick = { editing = food }) {
                                Icon(Icons.Default.Edit, contentDescription = "編集")
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (creating) {
        FoodEditDialog(
            food = null,
            onDismiss = { creating = false },
            onSave = { phase, category, name ->
                viewModel.createFood(phase, category, name)
                creating = false
            },
        )
    }
    editing?.let { food ->
        FoodEditDialog(
            food = food,
            onDismiss = { editing = null },
            onSave = { phase, category, name ->
                viewModel.updateFood(food, phase, category, name)
                editing = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodEditDialog(
    food: FoodEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var name by remember(food?.id) { mutableStateOf(food?.name.orEmpty()) }
    var phaseCode by remember(food?.id) { mutableStateOf(food?.phaseCode ?: BabyMasters.phases.first().code) }
    var categoryCode by remember(food?.id) { mutableStateOf(food?.categoryCode ?: BabyMasters.categories.first().code) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (food == null) "食材を新規登録" else "食材を修正") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MasterDropdown(
                    label = "期",
                    selectedCode = phaseCode,
                    items = BabyMasters.phases.map { it.code to "${it.ageLabel}（${it.label}）" },
                    onSelected = { phaseCode = it },
                )
                MasterDropdown(
                    label = "分類",
                    selectedCode = categoryCode,
                    items = BabyMasters.categories.map { it.code to it.label },
                    onSelected = { categoryCode = it },
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("食材名") },
                    singleLine = true,
                )
                food?.let {
                    Text(
                        text = "現在のコード: ${it.code}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(phaseCode, categoryCode, name) }) {
                Text(if (food == null) "登録" else "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MasterDropdown(
    label: String,
    selectedCode: String,
    items: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = items.firstOrNull { it.first == selectedCode }?.second.orEmpty()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            items.forEach { (code, itemLabel) ->
                DropdownMenuItem(
                    text = { Text(itemLabel) },
                    onClick = {
                        onSelected(code)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun formatDisplayDate(value: String): String =
    runCatching {
        val date = LocalDate.parse(value)
        "${date.monthValue}/${date.dayOfMonth}"
    }.getOrDefault(value)

private fun String.toLocalDateMillis(): Long =
    runCatching {
        LocalDate.parse(this)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    }.getOrDefault(todayMillis())

private fun todayMillis(): Long =
    LocalDate.now()
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

private fun Long.toIsoDate(): String =
    Instant.ofEpochMilli(this)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .format(DateTimeFormatter.ISO_DATE)
