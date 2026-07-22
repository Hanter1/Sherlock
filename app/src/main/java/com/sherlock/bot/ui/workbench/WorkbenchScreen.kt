package com.sherlock.bot.ui.workbench

import android.app.Activity
import android.content.Intent
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sherlock.bot.data.AppInfo
import com.sherlock.bot.data.AppSettings
import com.sherlock.bot.data.BotAction
import com.sherlock.bot.data.ChatMessage
import com.sherlock.bot.data.ChatSearch
import com.sherlock.bot.data.ScanPreset
import com.sherlock.bot.data.SearchMode
import com.sherlock.bot.ui.chat.ChatMarkdown
import com.sherlock.bot.ui.theme.Cabinet
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private data class ModeTab(val actionId: String, val label: String, val mode: SearchMode)

private val MODE_TABS = listOf(
    ModeTab("username", "Никнейм", SearchMode.USERNAME),
    ModeTab("compare", "Сравнить", SearchMode.COMPARE),
    ModeTab("phone", "Телефон", SearchMode.PHONE),
    ModeTab("email", "Email", SearchMode.EMAIL),
    ModeTab("name", "ФИО", SearchMode.FULL_NAME),
)

private fun resolveJournalSelection(entries: List<ChatMessage>, id: String): String {
    val index = entries.indexOfFirst { it.id == id }
    if (index < 0) return id
    val selected = entries[index]
    if (selected.fromBot) return id
    return entries.drop(index + 1)
        .firstOrNull { it.fromBot && ChatSearch.isReportLike(it.text) }
        ?.id
        ?: id
}

private fun pendingModeLabel(mode: SearchMode): String = when (mode) {
    SearchMode.USERNAME -> "НИКНЕЙМ"
    SearchMode.COMPARE -> "СРАВНЕНИЕ"
    SearchMode.PHONE -> "ТЕЛЕФОН"
    SearchMode.EMAIL -> "EMAIL"
    SearchMode.FULL_NAME -> "ФИО"
    SearchMode.NONE -> "—"
}

@Composable
fun WorkbenchScreen(viewModel: WorkbenchViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val haptic = LocalHapticFeedback.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var selectedReportId by remember { mutableStateOf<String?>(null) }
    var journalOpen by remember { mutableStateOf(false) }
    var focusJournalFilter by remember { mutableStateOf(false) }

    val journal = remember(state.visibleMessages) {
        state.visibleMessages.filter(ChatSearch::isJournalWorthy)
    }
    val statusText = state.scanProgress?.let { progress ->
        "Скан ${progress.username} · ${progress.shortLabel}"
    } ?: state.messages
        .lastOrNull { it.isTyping }
        ?.text
        ?.lineSequence()
        ?.firstOrNull()
    val activeReport = remember(journal, selectedReportId, state.pinnedMessageId) {
        selectedReportId?.let { id -> journal.find { it.id == id && it.fromBot } }
            ?: state.pinnedMessage?.takeIf { msg ->
                msg.fromBot && ChatSearch.isReportLike(msg.text) && journal.any { it.id == msg.id }
            }
            ?: journal.lastOrNull { it.fromBot && ChatSearch.isReportLike(it.text) }
    }

    LaunchedEffect(state.messages.size) {
        val latest = state.messages.lastOrNull {
            it.fromBot && !it.isTyping && ChatSearch.isReportLike(it.text)
        }
        if (latest != null) {
            selectedReportId = latest.id
        }
    }

    LaunchedEffect(state.hideInRecents) {
        activity?.window?.let { window ->
            if (state.hideInRecents) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.shareEvents.collectLatest { text ->
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Sherlock Bot — отчёт")
                        putExtra(Intent.EXTRA_TEXT, text)
                    },
                    "Поделиться отчётом",
                ),
            )
        }
    }
    LaunchedEffect(Unit) {
        viewModel.shareFileEvents.collectLatest { event ->
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = event.mimeType
                        putExtra(Intent.EXTRA_SUBJECT, event.subject)
                        putExtra(Intent.EXTRA_STREAM, event.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    "Экспорт отчёта",
                ),
            )
        }
    }
    LaunchedEffect(Unit) {
        viewModel.toastEvents.collectLatest {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.hapticEvents.collectLatest {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    LaunchedEffect(state.celebrateScan) {
        if (state.celebrateScan) {
            kotlinx.coroutines.delay(1200)
            viewModel.consumeCelebrate()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerContainerColor = Cabinet.Panel,
            ) {
                JournalPanel(
                    entries = journal,
                    selectedId = activeReport?.id,
                    searchQuery = state.searchQuery,
                    pinnedId = state.pinnedMessageId,
                    focusFilter = focusJournalFilter,
                    onFilterFocused = { focusJournalFilter = false },
                    onSearch = viewModel::onSearchQueryChange,
                    onSelect = { id ->
                        selectedReportId = resolveJournalSelection(journal, id)
                        scope.launch { drawerState.close() }
                    },
                    onClear = viewModel::requestClearHistory,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Cabinet.Bg)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            val wide = maxWidth >= 720.dp
            Column(Modifier.fillMaxSize()) {
                CabinetHeader(
                    isOnline = state.isOnline,
                    isBusy = state.isBusy,
                    pendingMode = state.pendingMode,
                    scanProgress = state.scanProgress,
                    onOpenJournal = {
                        if (wide) journalOpen = !journalOpen
                        else scope.launch { drawerState.open() }
                    },
                    onSearch = {
                        if (wide) journalOpen = true
                        else scope.launch { drawerState.open() }
                        focusJournalFilter = true
                    },
                    onSettings = viewModel::openSettings,
                    onAbout = viewModel::openAbout,
                    onCancel = viewModel::cancelScan,
                )
                ScanProgressBar(progress = state.scanProgress)
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (wide && journalOpen) {
                        JournalPanel(
                            entries = journal,
                            selectedId = activeReport?.id,
                            searchQuery = state.searchQuery,
                            pinnedId = state.pinnedMessageId,
                            focusFilter = focusJournalFilter,
                            onFilterFocused = { focusJournalFilter = false },
                            onSearch = viewModel::onSearchQueryChange,
                            onSelect = { selectedReportId = resolveJournalSelection(journal, it) },
                            onClear = viewModel::requestClearHistory,
                            modifier = Modifier
                                .width(280.dp)
                                .fillMaxHeight()
                                .background(Cabinet.BgElevated)
                                .border(width = 1.dp, color = Cabinet.Line),
                        )
                    }
                    WorkbenchMain(
                        state = state,
                        activeReport = activeReport,
                        statusText = statusText,
                        onMode = { actionId ->
                            if (!state.isBusy) viewModel.onAction("", actionId)
                        },
                        onInputChange = viewModel::onInputChange,
                        onSend = viewModel::send,
                        onCancel = viewModel::cancelScan,
                        onAction = { actionId ->
                            val id = activeReport?.id ?: return@WorkbenchMain
                            viewModel.onAction(id, actionId)
                        },
                        onUnpin = viewModel::unpinMessage,
                        onJumpPinned = {
                            state.pinnedMessageId?.let { selectedReportId = it }
                            viewModel.jumpToPinned()
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }

    if (state.showAbout) {
        CabinetDialog(
            title = "О приложении",
            onDismiss = viewModel::closeAbout,
        ) {
            Text(
                text = AppInfo.aboutText(context),
                color = Cabinet.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    if (state.showSettings) {
        SettingsDialog(
            state = state,
            viewModel = viewModel,
        )
    }
    if (state.showDisclaimer) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {
                TextButton(onClick = viewModel::acceptDisclaimer) {
                    Text("Принимаю", color = Cabinet.Accent)
                }
            },
            title = { Text("Правовой дисклеймер", color = Cabinet.Text) },
            text = {
                Text(
                    text = AppSettings.DISCLAIMER_TEXT,
                    color = Cabinet.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            containerColor = Cabinet.Panel,
        )
    }
    if (state.showClearConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissClearConfirm,
            confirmButton = {
                TextButton(onClick = viewModel::confirmClearHistory) {
                    Text("Очистить", color = Cabinet.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissClearConfirm) {
                    Text("Отмена", color = Cabinet.TextSecondary)
                }
            },
            title = { Text("Очистить журнал?", color = Cabinet.Text) },
            text = {
                Text(
                    text = "История запросов и отчётов будет удалена с устройства.",
                    color = Cabinet.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            containerColor = Cabinet.Panel,
        )
    }
    if (state.showEmailConsent) {
        AlertDialog(
            onDismissRequest = viewModel::declineEmailConsent,
            confirmButton = {
                TextButton(onClick = viewModel::acceptEmailConsent) {
                    Text("Разрешить", color = Cabinet.Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::declineEmailConsent) {
                    Text("Отмена", color = Cabinet.TextSecondary)
                }
            },
            title = { Text("Email и третьи стороны", color = Cabinet.Text) },
            text = {
                Text(
                    text = AppSettings.EMAIL_CONSENT_TEXT,
                    color = Cabinet.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            containerColor = Cabinet.Panel,
        )
    }
    if (state.showSharePiiConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSharePiiConfirm,
            confirmButton = {
                TextButton(onClick = viewModel::confirmShareWithRedaction) {
                    Text("С маскированием", color = Cabinet.Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::confirmShareAsIs) {
                    Text("Как есть", color = Cabinet.Danger)
                }
            },
            title = { Text("В отчёте есть ПДн", color = Cabinet.Text) },
            text = {
                Text(
                    text = "Найдены телефон и/или email. Можно замаскировать перед передачей во внешнее приложение.",
                    color = Cabinet.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            containerColor = Cabinet.Panel,
        )
    }
}

@Composable
private fun CabinetHeader(
    isOnline: Boolean,
    isBusy: Boolean,
    pendingMode: SearchMode,
    scanProgress: ScanProgressUi?,
    onOpenJournal: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Cabinet.BgElevated)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onOpenJournal) {
            Icon(Icons.Default.Menu, contentDescription = "Журнал", tint = Cabinet.Text)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "SHERLOCK",
                style = MaterialTheme.typography.titleLarge,
                color = Cabinet.Text,
            )
            Text(
                text = when {
                    !isOnline -> "НЕТ СЕТИ · локально: телефон / ФИО"
                    isBusy -> scanProgress?.shortLabel ?: "ВЫПОЛНЯЕТСЯ ЗАПРОС"
                    pendingMode != SearchMode.NONE -> "РЕЖИМ · ${pendingModeLabel(pendingMode)}"
                    else -> "ОТКРЫТЫЕ ИСТОЧНИКИ · РБ"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (!isOnline) Cabinet.Danger else Cabinet.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StatusPill(online = isOnline)
        if (isBusy) {
            Text(
                text = "СТОП",
                style = MaterialTheme.typography.labelLarge,
                color = Cabinet.AccentOn,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Cabinet.Accent)
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        IconButton(onClick = onSearch, enabled = !isBusy) {
            Icon(Icons.Default.Search, contentDescription = "Фильтр журнала", tint = Cabinet.TextSecondary)
        }
        IconButton(onClick = onSettings, enabled = !isBusy) {
            Icon(Icons.Default.Settings, null, tint = Cabinet.TextSecondary)
        }
        IconButton(onClick = onAbout, enabled = !isBusy) {
            Icon(Icons.Default.Info, null, tint = Cabinet.TextSecondary)
        }
    }
}

@Composable
private fun StatusPill(online: Boolean) {
    val color = if (online) Cabinet.Success else Cabinet.Danger
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(50))
                .background(color),
        )
        Text(
            text = if (online) "ОНЛАЙН" else "ОФЛАЙН",
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
private fun ScanProgressBar(progress: ScanProgressUi?) {
    val fraction = progress
        ?.takeIf { it.total > 0 }
        ?.let { (it.done.toFloat() / it.total).coerceIn(0f, 1f) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (fraction != null) 3.dp else 2.dp)
            .background(if (fraction != null) Cabinet.Line else Cabinet.Accent),
    ) {
        if (fraction != null) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(Cabinet.Accent),
            )
        }
    }
}

@Composable
private fun WorkbenchMain(
    state: WorkbenchUiState,
    activeReport: ChatMessage?,
    statusText: String?,
    onMode: (String) -> Unit,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onAction: (String) -> Unit,
    onUnpin: () -> Unit,
    onJumpPinned: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "КАБИНЕТ ЗАПРОСОВ",
            style = MaterialTheme.typography.labelMedium,
            color = Cabinet.TextMuted,
        )
        ModeTabs(
            active = state.pendingMode,
            enabled = !state.isBusy,
            onMode = onMode,
        )
        QueryBar(
            value = state.input,
            isBusy = state.isBusy,
            pendingMode = state.pendingMode,
            onValueChange = onInputChange,
            onSend = onSend,
            onCancel = onCancel,
        )
        state.pinnedMessage?.let { pinned ->
            PinnedStrip(
                preview = ChatSearch.preview(pinned.text),
                onClick = onJumpPinned,
                onUnpin = onUnpin,
            )
        }
        if (!statusText.isNullOrBlank()) {
            StatusBanner(text = statusText)
        }
        ReportCard(
            message = activeReport,
            celebrate = state.celebrateScan,
            actionsEnabled = !state.isBusy,
            onAction = onAction,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

@Composable
private fun ModeTabs(
    active: SearchMode,
    enabled: Boolean,
    onMode: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MODE_TABS.forEach { tab ->
            val selected = active == tab.mode
            Text(
                text = tab.label.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    !enabled -> Cabinet.TextMuted
                    selected -> Cabinet.AccentOn
                    else -> Cabinet.TextSecondary
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (selected) Cabinet.Accent else Cabinet.BgElevated)
                    .border(
                        width = if (selected) 0.dp else 1.dp,
                        color = Cabinet.Line,
                        shape = RoundedCornerShape(4.dp),
                    )
                    .clickable(enabled = enabled) { onMode(tab.actionId) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun QueryBar(
    value: String,
    isBusy: Boolean,
    pendingMode: SearchMode,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            enabled = !isBusy,
            singleLine = false,
            maxLines = 3,
            placeholder = {
                Text(
                    text = when (pendingMode) {
                        SearchMode.USERNAME -> "никнейм, напр. durov"
                        SearchMode.COMPARE -> "два ника: alice bob"
                        SearchMode.PHONE -> "+375291234567"
                        SearchMode.EMAIL -> "name@example.com"
                        SearchMode.FULL_NAME -> "Иванов Иван"
                        SearchMode.NONE -> "запрос или /help"
                    },
                    color = Cabinet.TextMuted,
                )
            },
            shape = RoundedCornerShape(8.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (!isBusy) onSend() }),
            colors = fieldColors(),
        )
        IconButton(
            onClick = if (isBusy) onCancel else onSend,
            enabled = isBusy || value.isNotBlank(),
        ) {
            Icon(
                imageVector = if (isBusy) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                contentDescription = if (isBusy) "Стоп" else "Отправить",
                tint = if (isBusy) Cabinet.Accent else Cabinet.Text,
            )
        }
    }
}

@Composable
private fun StatusBanner(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = Cabinet.Text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Cabinet.AccentSoft)
            .border(1.dp, Cabinet.Accent.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
private fun PinnedStrip(
    preview: String,
    onClick: () -> Unit,
    onUnpin: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Cabinet.AccentSoft)
            .border(1.dp, Cabinet.Accent.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Default.PushPin, null, tint = Cabinet.Accent, modifier = Modifier.size(16.dp))
        Text(
            text = preview,
            style = MaterialTheme.typography.bodyMedium,
            color = Cabinet.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Default.Close,
            contentDescription = "Открепить",
            tint = Cabinet.TextMuted,
            modifier = Modifier
                .size(18.dp)
                .clickable(onClick = onUnpin),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportCard(
    message: ChatMessage?,
    celebrate: Boolean,
    actionsEnabled: Boolean,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Cabinet.Panel)
            .border(
                width = if (celebrate) 1.5.dp else 1.dp,
                color = if (celebrate) Cabinet.Accent else Cabinet.Line,
                shape = RoundedCornerShape(10.dp),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Cabinet.PanelHigh)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (celebrate) "ОТЧЁТ · ГОТОВО" else "ОТЧЁТ",
                style = MaterialTheme.typography.labelLarge,
                color = if (celebrate) Cabinet.Accent else Cabinet.TextMuted,
            )
            Text(
                text = "ОТКРЫТЫЕ ДАННЫЕ",
                style = MaterialTheme.typography.labelMedium,
                color = Cabinet.TextMuted,
            )
        }
        HorizontalDivider(color = Cabinet.Line)
        if (message == null) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = "НЕТ ОТЧЁТА",
                    style = MaterialTheme.typography.labelLarge,
                    color = Cabinet.Accent,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "1. Выберите режим сверху",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Cabinet.TextSecondary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "2. Введите запрос в поле",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Cabinet.TextSecondary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "3. Enter или ▶ — результат здесь",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Cabinet.TextSecondary,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(14.dp),
            ) {
                Text(
                    text = ChatMarkdown.toAnnotatedString(
                        text = message.text,
                        linkColor = Cabinet.Accent,
                        codeColor = Cabinet.Text,
                        codeBackground = Cabinet.BgElevated,
                    ),
                    style = MaterialTheme.typography.bodyLarge.copy(color = Cabinet.Text),
                )
            }
            if (message.actions.isNotEmpty()) {
                HorizontalDivider(color = Cabinet.Line)
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    message.actions.forEach { action ->
                        ActionChip(
                            action = action,
                            enabled = actionsEnabled,
                            onClick = { onAction(action.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionChip(
    action: BotAction,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = action.label,
        style = MaterialTheme.typography.labelLarge,
        color = if (enabled) Cabinet.Text else Cabinet.TextMuted,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Cabinet.BgElevated)
            .border(1.dp, Cabinet.LineStrong, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun JournalPanel(
    entries: List<ChatMessage>,
    selectedId: String?,
    searchQuery: String,
    pinnedId: String?,
    focusFilter: Boolean,
    onFilterFocused: () -> Unit,
    onSearch: (String) -> Unit,
    onSelect: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val filterFocus = remember { FocusRequester() }
    LaunchedEffect(focusFilter) {
        if (focusFilter) {
            filterFocus.requestFocus()
            onFilterFocused()
        }
    }
    Column(modifier = modifier.padding(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.History, null, tint = Cabinet.Accent, modifier = Modifier.size(18.dp))
            Text(
                text = if (entries.isEmpty()) "ЖУРНАЛ" else "ЖУРНАЛ · ${entries.size}",
                style = MaterialTheme.typography.labelLarge,
                color = Cabinet.Text,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "ОЧИСТИТЬ",
                style = MaterialTheme.typography.labelMedium,
                color = Cabinet.Danger,
                modifier = Modifier.clickable(onClick = onClear),
            )
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearch,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(filterFocus),
            singleLine = true,
            placeholder = { Text("Фильтр…", color = Cabinet.TextMuted) },
            shape = RoundedCornerShape(8.dp),
            colors = fieldColors(),
        )
        Spacer(Modifier.height(10.dp))
        if (entries.isEmpty()) {
            Text(
                text = "Пока пусто — выполните запрос",
                style = MaterialTheme.typography.bodyMedium,
                color = Cabinet.TextMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(entries.asReversed(), key = { it.id }) { msg ->
                val selected = msg.id == selectedId
                val pinned = msg.id == pinnedId
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) Cabinet.AccentSoft else Cabinet.Panel)
                        .border(
                            1.dp,
                            if (selected) Cabinet.Accent else Cabinet.Line,
                            RoundedCornerShape(8.dp),
                        )
                        .clickable { onSelect(msg.id) }
                        .padding(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = ChatSearch.journalMeta(msg),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (msg.fromBot) Cabinet.Accent else Cabinet.TextMuted,
                            modifier = Modifier.weight(1f),
                        )
                        if (pinned) {
                            Icon(
                                Icons.Default.PushPin,
                                null,
                                tint = Cabinet.Accent,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                    Text(
                        text = ChatSearch.journalTitle(msg),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Cabinet.Text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CabinetDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть", color = Cabinet.Accent)
            }
        },
        title = { Text(title, color = Cabinet.Text) },
        text = content,
        containerColor = Cabinet.Panel,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsDialog(
    state: WorkbenchUiState,
    viewModel: WorkbenchViewModel,
) {
    AlertDialog(
        onDismissRequest = viewModel::closeSettings,
        confirmButton = {
            TextButton(onClick = viewModel::closeSettings) {
                Text("Готово", color = Cabinet.Accent)
            }
        },
        title = { Text("Настройки", color = Cabinet.Text) },
        text = {
            val scroll = rememberScrollState()
            Column(
                modifier = Modifier.verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Параллелизм скана", color = Cabinet.TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppSettings.ALLOWED_PARALLEL.sorted().forEach { value ->
                        val selected = value == state.maxParallel
                        Text(
                            text = value.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) Cabinet.AccentOn else Cabinet.Text,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) Cabinet.Accent else Cabinet.BgElevated)
                                .border(1.dp, Cabinet.Line, RoundedCornerShape(6.dp))
                                .clickable { viewModel.setMaxParallel(value) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
                Text("Пресет площадок", color = Cabinet.TextSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScanPreset.entries.forEach { preset ->
                        val selected = preset == state.scanPreset
                        Text(
                            text = preset.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) Cabinet.AccentOn else Cabinet.Text,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) Cabinet.Accent else Cabinet.BgElevated)
                                .border(1.dp, Cabinet.Line, RoundedCornerShape(6.dp))
                                .clickable { viewModel.setScanPreset(preset) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                SettingsSwitch(
                    label = "Instagram / X в скане",
                    checked = state.includeBotProtected,
                    onCheckedChange = viewModel::setIncludeBotProtected,
                )
                SettingsSwitch(
                    label = "Скрывать в недавних",
                    checked = state.hideInRecents,
                    onCheckedChange = viewModel::setHideInRecents,
                )
                SettingsSwitch(
                    label = "Сохранять историю на диск",
                    checked = state.persistHistory,
                    onCheckedChange = viewModel::setPersistHistory,
                )
                Text("Email-поиск", color = Cabinet.TextSecondary)
                SettingsSwitch(
                    label = "MX / SPF / DMARC (DoH)",
                    checked = state.emailLookupMx,
                    onCheckedChange = viewModel::setEmailLookupMx,
                )
                SettingsSwitch(
                    label = "Gravatar (MD5 email)",
                    checked = state.emailLookupGravatar,
                    onCheckedChange = viewModel::setEmailLookupGravatar,
                )
                SettingsSwitch(
                    label = "Маскировать ПДн при share/copy",
                    checked = state.redactPiiOnShare,
                    onCheckedChange = viewModel::setRedactPiiOnShare,
                )
                Text(
                    "Remote-каталог (${state.catalogSource} v${state.catalogVersion})",
                    color = Cabinet.TextSecondary,
                )
                OutlinedTextField(
                    value = state.catalogUrl,
                    onValueChange = viewModel::onCatalogUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("https://…/osint_sites.json", color = Cabinet.TextMuted) },
                    shape = RoundedCornerShape(8.dp),
                    colors = fieldColors(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniBtn("Сохранить", viewModel::saveCatalogUrl)
                    MiniBtn("Обновить", viewModel::updateRemoteCatalog)
                    MiniBtn("Asset", viewModel::resetCatalogToAsset)
                }
                SettingsSwitch(
                    label = "Каталог: любой HTTPS-хост",
                    checked = state.catalogAllowAnyHost,
                    onCheckedChange = viewModel::setCatalogAllowAnyHost,
                )
                Text(
                    "Кэш ников: ${state.usernameCacheSummary}",
                    color = Cabinet.TextMuted,
                )
                MiniBtn("Очистить кэш", viewModel::clearUsernameCache)
            }
        },
        containerColor = Cabinet.Panel,
    )
}

@Composable
private fun SettingsSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Cabinet.Text, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Cabinet.AccentOn,
                checkedTrackColor = Cabinet.Accent,
                uncheckedThumbColor = Cabinet.TextSecondary,
                uncheckedTrackColor = Cabinet.Line,
            ),
        )
    }
}

@Composable
private fun MiniBtn(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = Cabinet.Text,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Cabinet.BgElevated)
            .border(1.dp, Cabinet.Line, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Cabinet.Text,
    unfocusedTextColor = Cabinet.Text,
    disabledTextColor = Cabinet.TextMuted,
    focusedBorderColor = Cabinet.Accent,
    unfocusedBorderColor = Cabinet.Line,
    cursorColor = Cabinet.Accent,
    focusedContainerColor = Cabinet.BgElevated,
    unfocusedContainerColor = Cabinet.BgElevated,
    disabledContainerColor = Cabinet.Panel,
)
