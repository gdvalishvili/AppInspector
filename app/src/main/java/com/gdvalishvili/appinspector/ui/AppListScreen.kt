package com.gdvalishvili.appinspector.ui

import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gdvalishvili.appinspector.R
import com.gdvalishvili.appinspector.data.AppFilter
import com.gdvalishvili.appinspector.data.SortOrder
import com.gdvalishvili.appinspector.data.WorkProfileState
import com.gdvalishvili.appinspector.model.AppEntry
import com.gdvalishvili.appinspector.ui.components.AppRow
import com.gdvalishvili.appinspector.util.AppLanguage
import com.gdvalishvili.appinspector.util.openAppInfo
import com.gdvalishvili.appinspector.ui.components.RemovedRow
import com.gdvalishvili.appinspector.util.openStorePage
import androidx.compose.material3.Checkbox
import com.gdvalishvili.appinspector.data.ListSettings
import com.gdvalishvili.appinspector.util.categoryTitle
import androidx.compose.material3.HorizontalDivider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(viewModel: AppListViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val languageChosen by viewModel.languageChosen.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentLanguage = LocalConfiguration.current.locales[0].language

    // Reload whenever the screen comes back (e.g. after enabling Usage access)
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    // First launch: ask for the language once
    if (languageChosen == false && AppLanguage.isSupported()) {
        LanguageDialog(onChoose = viewModel::chooseLanguage)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name))
                        if (viewModel.runningInProfile) {
                            Text(
                                text = stringResource(R.string.running_in_profile),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                actions = {
                    if (AppLanguage.isSupported()) {
                        val toGeorgian = currentLanguage != AppLanguage.GEORGIAN
                        TextButton(onClick = {
                            viewModel.chooseLanguage(
                                if (toGeorgian) AppLanguage.GEORGIAN else AppLanguage.ENGLISH
                            )
                        }) {
                            Text(
                                stringResource(
                                    if (toGeorgian) R.string.lang_short_ka else R.string.lang_short_en
                                )
                            )
                        }
                    }
                    ViewOptionsMenu(
                        settings = state.settings,
                        onShowCategory = viewModel::setShowCategory,
                        onShowSource = viewModel::setShowSource,
                    )
                },
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            SearchField(viewModel.query, viewModel::onQueryChange)
            Spacer(Modifier.height(8.dp))
            FilterRow(
                current = state.settings.filter,
                counts = state.counts,
                showWork = state.workProfile != WorkProfileState.NONE,
                onFilter = viewModel::setFilter,
            )
            if (state.settings.showCategory && state.settings.filter != AppFilter.REMOVED) {
                CategoryRow(
                    selected = state.settings.category,
                    counts = state.categoryCounts,
                    onSelect = viewModel::setCategory,
                )
            }
            if (!state.hasUsageAccess) {
                Spacer(Modifier.height(8.dp))
                UsageAccessBanner {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            }
            if (state.workProfile == WorkProfileState.PAUSED) {
                Text(
                    text = stringResource(R.string.work_paused),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            // Count on the left, sort on the right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.count_shown, state.apps.size, state.totalCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            val showRemoved = state.settings.filter == AppFilter.REMOVED
            Text(
                text = stringResource(
                    R.string.count_shown,
                    if (showRemoved) state.removed.size else state.apps.size,
                    if (showRemoved) state.counts[AppFilter.REMOVED] ?: 0 else state.totalCount,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (!showRemoved) SortChip(state.settings.sort, viewModel::setSort)

            // Thin progress bar while refreshing (keeps the old list visible)
            Box(Modifier.fillMaxWidth().height(2.dp)) {
                if (state.isLoading && state.hasData) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }

            if (!state.hasData) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else { AppList(state) { app -> openAppInfo(context, app)
                }
            }
        }
    }
}

@Composable
private fun AppList(state: AppListUiState, onClick: (AppEntry) -> Unit) {
    val context = LocalContext.current
    val showRemoved = state.settings.filter == AppFilter.REMOVED
    val isEmpty = if (showRemoved) state.removed.isEmpty() else state.apps.isEmpty()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isEmpty) {
            item {
                Text(
                    text = stringResource(R.string.empty_list),
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
        if (showRemoved) {
            items(state.removed, key = { "removed:" + it.key }) { app ->
                RemovedRow(app = app, onClick = { openStorePage(context, app.packageName) })
            }
        } else {
            items(state.apps, key = { it.key }) { app ->
                AppRow(
                    app = app,
                    now = state.now,
                    showCategory = state.settings.showCategory,
                    showSource = state.settings.showSource,
                    onClick = { onClick(app) },
                )
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onChange("") }) {
                    Icon(
                        painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.clear_search),
                    )
                }
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
    )
}

/** Single-choice chips: exactly one filter is selected. */
@Composable
private fun FilterRow(
    current: AppFilter,
    counts: Map<AppFilter, Int>,
    showWork: Boolean,
    onFilter: (AppFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppFilter.entries
            .filter { it != AppFilter.WORK || showWork }
            .forEach { filter ->
                FilterChip(
                    selected = current == filter,
                    onClick = { onFilter(filter) },
                    label = {
                        Text(
                            stringResource(
                                R.string.chip_with_count,
                                stringResource(filterLabelRes(filter)),
                                counts[filter] ?: 0,
                            )
                        )
                    },
                )
            }
    }
}

@StringRes
private fun filterLabelRes(filter: AppFilter): Int = when (filter) {
    AppFilter.ALL -> R.string.filter_all
    AppFilter.USER -> R.string.filter_user
    AppFilter.SYSTEM -> R.string.filter_system
    AppFilter.WORK -> R.string.filter_work
    AppFilter.REMOVED -> R.string.filter_removed
}

@Composable
private fun SortChip(current: SortOrder, onSelect: (SortOrder) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { expanded = true },
            label = {
                Text(stringResource(R.string.sort_label, stringResource(sortLabelRes(current))))
            },
            leadingIcon = {
                Icon(
                    painterResource(R.drawable.ic_sort),
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(stringResource(sortLabelRes(order))) },
                    onClick = {
                        onSelect(order)
                        expanded = false
                    },
                    trailingIcon = { if (order == current) Text("✓") },
                )
            }
        }
    }
}

@StringRes
private fun sortLabelRes(order: SortOrder): Int = when (order) {
    SortOrder.NAME -> R.string.sort_name
    SortOrder.INSTALLED -> R.string.sort_installed
    SortOrder.UPDATED -> R.string.sort_updated
    SortOrder.LAST_USED -> R.string.sort_last_used
    SortOrder.RECENTLY_USED -> R.string.sort_recently_used
}

@Composable
private fun UsageAccessBanner(onEnable: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.usage_banner_text),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = onEnable) { Text(stringResource(R.string.usage_banner_button)) }
        }
    }
}

/** Shown once on first launch. No dismiss: the user must pick a language. */
@Composable
private fun LanguageDialog(onChoose: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text(stringResource(R.string.lang_dialog_title)) },
        text = { Text(stringResource(R.string.lang_dialog_text)) },
        confirmButton = {
            TextButton(onClick = { onChoose(AppLanguage.GEORGIAN) }) {
                Text(stringResource(R.string.lang_georgian))
            }
        },
        dismissButton = {
            TextButton(onClick = { onChoose(AppLanguage.ENGLISH) }) {
                Text(stringResource(R.string.lang_english))
            }
        },
    )
}

/** ⋮ menu with checkable view options. */
@Composable
private fun ViewOptionsMenu(
    settings: ListSettings,
    onShowCategory: (Boolean) -> Unit,
    onShowSource: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.view_options),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.show_category)) },
                onClick = { onShowCategory(!settings.showCategory) },
                leadingIcon = { Checkbox(checked = settings.showCategory, onCheckedChange = null) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.show_source)) },
                onClick = { onShowSource(!settings.showSource) },
                leadingIcon = { Checkbox(checked = settings.showSource, onCheckedChange = null) },
            )
        }
    }
}

/** Single-choice category chips; counts follow the main filter. */
@Composable
private fun CategoryRow(selected: Int?, counts: Map<Int, Int>, onSelect: (Int?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.category_all)) },
        )
        counts.entries.sortedByDescending { it.value }.forEach { (category, count) ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(category) },
                label = { Text(stringResource(R.string.chip_with_count, categoryTitle(category), count)) },
            )
        }
    }
}