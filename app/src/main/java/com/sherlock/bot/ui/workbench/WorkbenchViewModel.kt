package com.sherlock.bot.ui.workbench

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sherlock.bot.SherlockApp
import com.sherlock.bot.data.AppSettings
import com.sherlock.bot.data.CatalogRepository
import com.sherlock.bot.data.ChatHistoryCodec
import com.sherlock.bot.data.ChatHistoryStore
import com.sherlock.bot.data.ChatMessage
import com.sherlock.bot.data.ChatSearch
import com.sherlock.bot.data.ChatSnapshot
import com.sherlock.bot.data.NetworkMonitor
import com.sherlock.bot.data.OsintCatalog
import com.sherlock.bot.data.OsintEngine
import com.sherlock.bot.data.OsintResult
import com.sherlock.bot.data.PiiRedactor
import com.sherlock.bot.data.ReportExporter
import com.sherlock.bot.data.SearchMode
import com.sherlock.bot.data.SiteCheckProgress
import com.sherlock.bot.data.UsernameDiskCache
import com.sherlock.bot.data.UsernameReportMerge
import com.sherlock.bot.data.UsernameSessionCache
import com.sherlock.bot.domain.BotInteractor
import com.sherlock.bot.domain.QueryClassifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

data class ScanProgressUi(
    val username: String,
    val done: Int,
    val total: Int,
    val site: String,
) {
    val shortLabel: String
        get() = if (total > 0) "$done/$total · $site" else site
}

data class WorkbenchUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isBusy: Boolean = false,
    val pendingMode: SearchMode = SearchMode.NONE,
    val historyLoaded: Boolean = false,
    val isOnline: Boolean = true,
    val showAbout: Boolean = false,
    val showSettings: Boolean = false,
    val showDisclaimer: Boolean = false,
    val showClearConfirm: Boolean = false,
    val showEmailConsent: Boolean = false,
    val showSharePiiConfirm: Boolean = false,
    val celebrateScan: Boolean = false,
    val scanProgress: ScanProgressUi? = null,
    val maxParallel: Int = AppSettings.DEFAULT_PARALLEL,
    val includeBotProtected: Boolean = true,
    val emailLookupMx: Boolean = true,
    val emailLookupGravatar: Boolean = true,
    val redactPiiOnShare: Boolean = false,
    val hideInRecents: Boolean = true,
    val persistHistory: Boolean = true,
    val usernameCacheEntries: Int = 0,
    val catalogUrl: String = "",
    val catalogSource: String = "asset",
    val catalogVersion: Int = 0,
    val searchQuery: String = "",
    val searchOpen: Boolean = false,
    val pinnedMessageId: String? = null,
    val scrollToMessageId: String? = null,
) {
    val visibleMessages: List<ChatMessage>
        get() = ChatSearch.filter(messages, searchQuery)

    val pinnedMessage: ChatMessage?
        get() = pinnedMessageId?.let { id -> messages.find { it.id == id } }
}

data class ShareFileEvent(
    val uri: Uri,
    val mimeType: String,
    val subject: String,
)

class WorkbenchViewModel(
    application: Application,
    private val networkMonitor: NetworkMonitor = NetworkMonitor(application),
    private val appSettings: AppSettings = AppSettings(application),
    private val sessionCache: UsernameSessionCache = UsernameSessionCache(),
    private val diskCache: UsernameDiskCache = UsernameDiskCache(
        file = File(application.filesDir, "username_cache.json"),
    ),
    private val osint: OsintEngine = OsintEngine(
        sessionCache = sessionCache,
        diskCache = diskCache,
        maxParallel = { appSettings.maxParallel },
        includeBotProtected = { appSettings.includeBotProtected },
        emailLookupMx = { appSettings.emailLookupMx },
        emailLookupGravatar = { appSettings.emailLookupGravatar },
    ),
    private val bot: BotInteractor = BotInteractor(
        osint = osint,
        isOnline = { networkMonitor.isOnline() },
    ),
    private val historyStore: ChatHistoryStore = ChatHistoryStore(application),
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(
        WorkbenchUiState(
            isOnline = networkMonitor.isOnline(),
            maxParallel = appSettings.maxParallel,
            includeBotProtected = appSettings.includeBotProtected,
            emailLookupMx = appSettings.emailLookupMx,
            emailLookupGravatar = appSettings.emailLookupGravatar,
            redactPiiOnShare = appSettings.redactPiiOnShare,
            hideInRecents = appSettings.hideInRecents,
            persistHistory = appSettings.persistHistory,
            usernameCacheEntries = bot.usernameCacheSize(),
            showDisclaimer = !appSettings.disclaimerAccepted,
            pinnedMessageId = appSettings.pinnedMessageId,
            pendingMode = appSettings.pendingMode,
            catalogUrl = appSettings.catalogUrl,
            catalogSource = runCatching { OsintCatalog.info().source }.getOrDefault("asset"),
            catalogVersion = runCatching { OsintCatalog.info().version }.getOrDefault(0),
        ),
    )
    val state: StateFlow<WorkbenchUiState> = _state.asStateFlow()

    private val _shareEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val shareEvents: SharedFlow<String> = _shareEvents.asSharedFlow()

    private val _shareFileEvents = MutableSharedFlow<ShareFileEvent>(extraBufferCapacity = 1)
    val shareFileEvents: SharedFlow<ShareFileEvent> = _shareFileEvents.asSharedFlow()

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    private val _hapticEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val hapticEvents: SharedFlow<Unit> = _hapticEvents.asSharedFlow()

    private var scanJob: Job? = null
    private val lastProgress = CopyOnWriteArrayList<SiteCheckProgress>()
    private var lastScanUsername: String = ""
    private var pendingEmailQuery: String? = null
    private var pendingShareText: String? = null
    private var pendingShareAsShare: Boolean = true

    init {
        viewModelScope.launch {
            networkMonitor.observeOnline().collect { online ->
                _state.update { it.copy(isOnline = online) }
            }
        }

        viewModelScope.launch {
            _state
                .map { it.pendingMode }
                .distinctUntilChanged()
                .collect { mode ->
                    appSettings.pendingMode = mode
                }
        }

        viewModelScope.launch {
            val snapshot = historyStore.load()
            if (snapshot != null && snapshot.messages.isNotEmpty()) {
                bot.replaceReports(snapshot.reports)
                val pinned = appSettings.pinnedMessageId
                    ?.takeIf { id -> snapshot.messages.any { it.id == id } }
                if (pinned == null && appSettings.pinnedMessageId != null) {
                    appSettings.pinnedMessageId = null
                }
                _state.update {
                    it.copy(
                        messages = snapshot.messages,
                        pendingMode = snapshot.pendingMode,
                        historyLoaded = true,
                        pinnedMessageId = pinned,
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        messages = listOf(bot.welcome()),
                        historyLoaded = true,
                    )
                }
            }
        }

        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            _state
                .map { ChatSnapshot(it.messages, it.pendingMode, bot.allReports()) to it.historyLoaded }
                .distinctUntilChanged()
                .debounce(400)
                .collectLatest { (snapshot, loaded) ->
                    if (!loaded) return@collectLatest
                    if (!appSettings.persistHistory) return@collectLatest
                    historyStore.save(snapshot)
                }
        }
    }

    fun onInputChange(value: String) {
        _state.update { it.copy(input = value) }
    }

    fun send() {
        val text = _state.value.input.trim()
        if (text.isEmpty() || _state.value.isBusy) return

        val isClear = text.equals("/clear", true) || text.equals("очистить", true)
        val isAbout = text.equals("/about", true) || text.equals("о приложении", true)
        val isSettings = text.equals("/settings", true) || text.equals("настройки", true)
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = text,
            fromBot = false,
        )
        _state.update {
            it.copy(
                messages = it.messages + userMessage,
                input = "",
                isBusy = true,
            )
        }
        if (isClear) {
            requestClearHistory()
            _state.update { it.copy(isBusy = false) }
            return
        }
        if (isAbout) {
            openAbout()
            _state.update { it.copy(isBusy = false) }
            return
        }
        if (isSettings) {
            openSettings()
            _state.update { it.copy(isBusy = false) }
            return
        }
        if (requiresEmailConsent(text)) {
            pendingEmailQuery = text
            _state.update { it.copy(isBusy = false, showEmailConsent = true) }
            return
        }
        respond(text)
    }

    fun acceptDisclaimer() {
        appSettings.disclaimerAccepted = true
        _state.update { it.copy(showDisclaimer = false) }
    }

    fun acceptEmailConsent() {
        appSettings.emailLookupConsent = true
        val query = pendingEmailQuery
        pendingEmailQuery = null
        _state.update { it.copy(showEmailConsent = false) }
        if (query != null) {
            _state.update { it.copy(isBusy = true) }
            respond(query)
        }
    }

    fun declineEmailConsent() {
        pendingEmailQuery = null
        _state.update { it.copy(showEmailConsent = false) }
        _toastEvents.tryEmit("Email-поиск отменён")
    }

    private fun requiresEmailConsent(text: String): Boolean {
        if (appSettings.emailLookupConsent) return false
        val trimmed = text.trim()
        if (trimmed.startsWith("/email", ignoreCase = true)) {
            return trimmed.substringAfter(" ", "").trim().isNotBlank()
        }
        val mode = _state.value.pendingMode.takeIf { it != SearchMode.NONE }
            ?: QueryClassifier.detectMode(trimmed)
        return mode == SearchMode.EMAIL
    }

    fun setHideInRecents(value: Boolean) {
        appSettings.hideInRecents = value
        _state.update { it.copy(hideInRecents = value) }
    }

    fun setPersistHistory(value: Boolean) {
        appSettings.persistHistory = value
        _state.update { it.copy(persistHistory = value) }
        if (!value) {
            viewModelScope.launch {
                historyStore.clear()
                _toastEvents.tryEmit("История на диске удалена")
            }
        } else {
            _toastEvents.tryEmit("История снова сохраняется на диск")
        }
    }

    fun onCatalogUrlChange(value: String) {
        _state.update { it.copy(catalogUrl = value) }
    }

    fun saveCatalogUrl() {
        appSettings.catalogUrl = _state.value.catalogUrl
        _toastEvents.tryEmit("URL каталога сохранён")
    }

    fun updateRemoteCatalog() {
        if (_state.value.isBusy) return
        viewModelScope.launch {
            appSettings.catalogUrl = _state.value.catalogUrl
            _toastEvents.tryEmit("Обновляю каталог…")
            val repo = (getApplication<Application>() as? SherlockApp)?.catalogRepository
                ?: CatalogRepository(getApplication())
            val result = withContext(Dispatchers.IO) {
                repo.updateFromConfiguredUrl()
            }
            when (result) {
                is CatalogRepository.UpdateResult.Ok -> {
                    refreshSettingsState()
                    _toastEvents.tryEmit(
                        "Каталог v${result.version}: ${result.siteCount} площадок",
                    )
                }
                is CatalogRepository.UpdateResult.Failed -> {
                    _toastEvents.tryEmit("Каталог: ${result.reason}")
                }
            }
        }
    }

    fun resetCatalogToAsset() {
        viewModelScope.launch {
            val repo = (getApplication<Application>() as? SherlockApp)?.catalogRepository
                ?: CatalogRepository(getApplication())
            withContext(Dispatchers.IO) { repo.clearRemote() }
            refreshSettingsState()
            _toastEvents.tryEmit("Каталог: встроенный asset")
        }
    }

    fun toggleSearch() {
        _state.update {
            val open = !it.searchOpen
            it.copy(
                searchOpen = open,
                searchQuery = if (open) it.searchQuery else "",
            )
        }
    }

    fun onSearchQueryChange(value: String) {
        _state.update { it.copy(searchQuery = value) }
    }

    fun pinMessage(messageId: String) {
        val exists = _state.value.messages.any { it.id == messageId }
        if (!exists) {
            _toastEvents.tryEmit("Сообщение не найдено")
            return
        }
        appSettings.pinnedMessageId = messageId
        _state.update { it.copy(pinnedMessageId = messageId) }
        _toastEvents.tryEmit("Отчёт закреплён")
    }

    fun unpinMessage() {
        appSettings.pinnedMessageId = null
        _state.update { it.copy(pinnedMessageId = null) }
        _toastEvents.tryEmit("Закрепление снято")
    }

    fun jumpToPinned() {
        val id = _state.value.pinnedMessageId ?: return
        _state.update {
            it.copy(
                searchOpen = false,
                searchQuery = "",
                scrollToMessageId = id,
            )
        }
    }

    fun consumeScrollToMessage() {
        _state.update { it.copy(scrollToMessageId = null) }
    }

    fun openAbout() {
        _state.update { it.copy(showAbout = true) }
    }

    fun closeAbout() {
        _state.update { it.copy(showAbout = false) }
    }

    fun openSettings() {
        refreshSettingsState()
        _state.update { it.copy(showSettings = true) }
    }

    fun closeSettings() {
        _state.update { it.copy(showSettings = false) }
    }

    fun setMaxParallel(value: Int) {
        if (value !in AppSettings.ALLOWED_PARALLEL) return
        appSettings.maxParallel = value
        _state.update { it.copy(maxParallel = value) }
    }

    fun setIncludeBotProtected(value: Boolean) {
        appSettings.includeBotProtected = value
        _state.update { it.copy(includeBotProtected = value) }
    }

    fun setEmailLookupMx(value: Boolean) {
        appSettings.emailLookupMx = value
        _state.update { it.copy(emailLookupMx = value) }
    }

    fun setEmailLookupGravatar(value: Boolean) {
        appSettings.emailLookupGravatar = value
        _state.update { it.copy(emailLookupGravatar = value) }
    }

    fun setRedactPiiOnShare(value: Boolean) {
        appSettings.redactPiiOnShare = value
        _state.update { it.copy(redactPiiOnShare = value) }
    }

    fun clearUsernameCache() {
        bot.clearUsernameCaches()
        refreshSettingsState()
        _toastEvents.tryEmit("Кэш ников очищен")
    }

    fun consumeCelebrate() {
        _state.update { it.copy(celebrateScan = false) }
    }

    fun cancelScan() {
        if (!_state.value.isBusy) return
        scanJob?.cancel()
    }

    fun onAction(messageId: String, actionId: String) {
        if (_state.value.isBusy) return
        when (actionId) {
            "share" -> emitShareOrCopy(messageId, asShare = true)
            "copy" -> emitShareOrCopy(messageId, asShare = false)
            "about" -> openAbout()
            "settings" -> openSettings()
            "clear_history" -> requestClearHistory()
            "pin_report" -> pinMessage(messageId)
            "report_found", "report_no_errors", "report_full" -> {
                val reportId = reportIdForMessage(messageId)
                val report = bot.reportFor(reportId) ?: run {
                    _toastEvents.tryEmit("Нет отчёта для фильтра")
                    return
                }
                val filter = bot.filterFromAction(actionId) ?: return
                val message = bot.formatUsernameReport(
                    result = report,
                    filter = filter,
                    reportId = reportId ?: UUID.randomUUID().toString(),
                )
                _state.update { it.copy(messages = it.messages + message) }
            }
            "export_md" -> exportReport(asJson = false, messageId = messageId)
            "export_json" -> exportReport(asJson = true, messageId = messageId)
            "rescan" -> {
                val report = bot.reportFor(reportIdForMessage(messageId)) ?: run {
                    _toastEvents.tryEmit("Нет отчёта для повтора")
                    return
                }
                runRescan(report.username)
            }
            "rescan_errors" -> {
                val reportId = reportIdForMessage(messageId)
                val report = bot.reportFor(reportId) ?: run {
                    _toastEvents.tryEmit("Нет отчёта для повтора")
                    return
                }
                if (UsernameReportMerge.failedSiteNames(report).isEmpty()) {
                    _toastEvents.tryEmit("Нет ошибок / неуверенных площадок")
                    return
                }
                runRescanErrors(report, reportId ?: UUID.randomUUID().toString())
            }
            else -> {
                _state.update { it.copy(isBusy = true) }
                viewModelScope.launch {
                    showStatus("Выполняется…")
                    val handled = bot.handleAction(actionId)
                    hideStatus()
                    if (handled != null) {
                        val (message, mode) = handled
                        _state.update {
                            it.copy(
                                messages = it.messages + message,
                                pendingMode = mode,
                                isBusy = false,
                            )
                        }
                    } else {
                        _state.update { it.copy(isBusy = false) }
                    }
                }
            }
        }
    }

    fun dismissSharePiiConfirm() {
        pendingShareText = null
        _state.update { it.copy(showSharePiiConfirm = false) }
    }

    fun confirmShareWithRedaction() {
        val text = pendingShareText ?: return
        pendingShareText = null
        _state.update { it.copy(showSharePiiConfirm = false) }
        deliverShareOrCopy(PiiRedactor.redact(text), pendingShareAsShare)
    }

    fun confirmShareAsIs() {
        val text = pendingShareText ?: return
        pendingShareText = null
        _state.update { it.copy(showSharePiiConfirm = false) }
        deliverShareOrCopy(text, pendingShareAsShare)
    }

    private fun emitShareOrCopy(messageId: String, asShare: Boolean) {
        val text = _state.value.messages.find { it.id == messageId }?.text ?: return
        if (appSettings.redactPiiOnShare) {
            deliverShareOrCopy(PiiRedactor.redact(text), asShare)
            return
        }
        if (PiiRedactor.containsPii(text)) {
            pendingShareText = text
            pendingShareAsShare = asShare
            _state.update { it.copy(showSharePiiConfirm = true) }
            return
        }
        deliverShareOrCopy(text, asShare)
    }

    private fun deliverShareOrCopy(text: String, asShare: Boolean) {
        if (asShare) {
            _shareEvents.tryEmit(text)
        } else {
            val clipboard = getApplication<Application>()
                .getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText("Sherlock Bot", text))
            _toastEvents.tryEmit("Скопировано в буфер")
        }
    }

    fun requestClearHistory() {
        _state.update { it.copy(showClearConfirm = true, isBusy = false) }
    }

    fun dismissClearConfirm() {
        _state.update { it.copy(showClearConfirm = false) }
    }

    fun confirmClearHistory() {
        _state.update { it.copy(showClearConfirm = false) }
        clearHistory()
    }

    fun clearHistory() {
        scanJob?.cancel()
        viewModelScope.launch {
            historyStore.clear()
            bot.clearReports()
            appSettings.pinnedMessageId = null
            val welcome = bot.welcome()
            _state.update {
                it.copy(
                    messages = listOf(welcome),
                    pendingMode = SearchMode.NONE,
                    input = "",
                    isBusy = false,
                    historyLoaded = true,
                    pinnedMessageId = null,
                    searchQuery = "",
                    searchOpen = false,
                    scanProgress = null,
                )
            }
            if (appSettings.persistHistory) {
                historyStore.save(
                    ChatSnapshot(
                        messages = listOf(welcome),
                        pendingMode = SearchMode.NONE,
                        reports = emptyMap(),
                    ),
                )
            }
        }
    }

    private fun runRescan(username: String) {
        scanJob?.cancel()
        lastProgress.clear()
        lastScanUsername = username
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = "/username $username · без кэша",
            fromBot = false,
        )
        _state.update {
            it.copy(
                messages = it.messages + userMessage,
                isBusy = true,
            )
        }
        scanJob = viewModelScope.launch {
            showStatus("Повторный скан `$username`…")
            try {
                val reportBeforeIds = bot.allReports().keys.toSet()
                val reply = bot.rescanUsername(username) { progress ->
                    lastProgress.add(progress)
                    showStatus(
                        text = bot.formatScanProgress(
                            username = username,
                            lines = lastProgress.map { bot.progressLine(it) },
                            done = progress.done,
                            total = progress.total,
                        ),
                        progress = progress,
                        username = username,
                    )
                }
                hideStatus()
                val produced = reply.reportId != null && reply.reportId !in reportBeforeIds
                if (produced) _hapticEvents.tryEmit(Unit)
                refreshSettingsState()
                _state.update {
                    val messages = it.messages + reply
                    val pinId = if (produced) reply.id else it.pinnedMessageId
                    if (produced) appSettings.pinnedMessageId = reply.id
                    it.copy(
                        messages = messages,
                        pendingMode = SearchMode.NONE,
                        isBusy = false,
                        celebrateScan = produced,
                        pinnedMessageId = pinId,
                    )
                }
            } catch (e: CancellationException) {
                hideStatus()
                val cancelled = bot.cancelledScanMessage(username, lastProgress.toList())
                _state.update {
                    it.copy(
                        messages = it.messages + cancelled,
                        isBusy = false,
                    )
                }
                throw e
            }
        }
    }

    private fun runRescanErrors(report: OsintResult.UsernameReport, reportId: String) {
        scanJob?.cancel()
        lastProgress.clear()
        lastScanUsername = report.username
        val sites = UsernameReportMerge.failedSiteNames(report).size
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = "/username ${report.username} · добить ошибки ($sites)",
            fromBot = false,
        )
        _state.update {
            it.copy(
                messages = it.messages + userMessage,
                isBusy = true,
            )
        }
        scanJob = viewModelScope.launch {
            showStatus("Добиваю ошибки `$lastScanUsername`…")
            try {
                val reply = bot.rescanFailedSites(report, reportId) { progress ->
                    lastProgress.add(progress)
                    showStatus(
                        text = bot.formatScanProgress(
                            username = report.username,
                            lines = lastProgress.map { bot.progressLine(it) },
                            done = progress.done,
                            total = progress.total,
                        ),
                        progress = progress,
                        username = report.username,
                    )
                }
                hideStatus()
                _hapticEvents.tryEmit(Unit)
                refreshSettingsState()
                _state.update {
                    appSettings.pinnedMessageId = reply.id
                    it.copy(
                        messages = it.messages + reply,
                        pendingMode = SearchMode.NONE,
                        isBusy = false,
                        celebrateScan = true,
                        pinnedMessageId = reply.id,
                    )
                }
            } catch (e: CancellationException) {
                hideStatus()
                val cancelled = bot.cancelledScanMessage(report.username, lastProgress.toList())
                _state.update {
                    it.copy(
                        messages = it.messages + cancelled,
                        isBusy = false,
                    )
                }
                throw e
            } catch (e: Exception) {
                hideStatus()
                _toastEvents.tryEmit(e.message ?: "Не удалось добить ошибки")
                _state.update { it.copy(isBusy = false) }
            }
        }
    }

    private fun exportReport(asJson: Boolean, messageId: String) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val exportDir = File(app.cacheDir, "exports")
            val message = _state.value.messages.find { it.id == messageId }
            val report = bot.reportFor(message?.reportId ?: reportIdForMessage(messageId))
            val file = withContext(Dispatchers.IO) {
                if (asJson) {
                    if (report == null) return@withContext null
                    val content = ReportExporter.toJson(report)
                    ReportExporter.writeExport(exportDir, report.username, "json", content)
                } else {
                    val content = if (report != null) {
                        ReportExporter.toMarkdown(report)
                    } else {
                        val text = message?.text ?: return@withContext null
                        ReportExporter.toMarkdownFromText("Sherlock Bot — отчёт", text)
                    }
                    val base = report?.username ?: "report"
                    ReportExporter.writeExport(exportDir, base, "md", content)
                }
            }
            if (file == null) {
                _toastEvents.tryEmit(if (asJson) "Нет JSON-отчёта" else "Нечего экспортировать")
                return@launch
            }
            val uri = FileProvider.getUriForFile(
                app,
                "${app.packageName}.fileprovider",
                file,
            )
            _shareFileEvents.tryEmit(
                ShareFileEvent(
                    uri = uri,
                    mimeType = if (asJson) "application/json" else "text/markdown",
                    subject = "Sherlock Bot — ${file.name}",
                ),
            )
            _toastEvents.tryEmit("Файл готов: ${file.name}")
        }
    }

    private fun respond(text: String) {
        scanJob?.cancel()
        lastProgress.clear()
        lastScanUsername = text.removePrefix("/username").trim().ifBlank {
            text.trim().removePrefix("@")
        }

        scanJob = viewModelScope.launch {
            showStatus("Выполняется…")
            try {
                val reportBeforeIds = bot.allReports().keys.toSet()
                val (replies, mode) = bot.handleUserText(
                    text = text,
                    pendingMode = _state.value.pendingMode,
                    onScanProgress = { progress: SiteCheckProgress ->
                        lastProgress.add(progress)
                        showStatus(
                            text = bot.formatScanProgress(
                                username = lastScanUsername,
                                lines = lastProgress.map { bot.progressLine(it) },
                                done = progress.done,
                                total = progress.total,
                            ),
                            progress = progress,
                            username = lastScanUsername,
                        )
                    },
                )
                hideStatus()
                val producedUsernameReport = replies.any { msg ->
                    msg.reportId != null && msg.reportId !in reportBeforeIds
                }
                val isCompareReport = replies.any { it.text.contains("—— Сравнение ников ——") }
                if ((producedUsernameReport || isCompareReport) && replies.isNotEmpty()) {
                    _hapticEvents.tryEmit(Unit)
                }
                refreshSettingsState()
                _state.update {
                    val messages = it.messages + replies
                    val autoPin = replies.lastOrNull { msg ->
                        msg.fromBot && ChatSearch.isReportLike(msg.text)
                    }
                    val pinId = autoPin?.id ?: it.pinnedMessageId
                    if (autoPin != null) {
                        appSettings.pinnedMessageId = autoPin.id
                    }
                    it.copy(
                        messages = messages,
                        pendingMode = mode,
                        isBusy = false,
                        celebrateScan = (producedUsernameReport || isCompareReport) && replies.isNotEmpty(),
                        pinnedMessageId = pinId,
                    )
                }
            } catch (e: CancellationException) {
                hideStatus()
                val cancelled = bot.cancelledScanMessage(lastScanUsername, lastProgress.toList())
                _state.update {
                    it.copy(
                        messages = it.messages + cancelled,
                        isBusy = false,
                    )
                }
                throw e
            }
        }
    }

    private fun reportIdForMessage(messageId: String): String? {
        val message = _state.value.messages.find { it.id == messageId } ?: return null
        return message.reportId ?: messageId.takeIf { bot.reportFor(it) != null }
    }

    private fun refreshSettingsState() {
        val info = runCatching { OsintCatalog.info() }.getOrNull()
        _state.update {
            it.copy(
                maxParallel = appSettings.maxParallel,
                includeBotProtected = appSettings.includeBotProtected,
                emailLookupMx = appSettings.emailLookupMx,
                emailLookupGravatar = appSettings.emailLookupGravatar,
                redactPiiOnShare = appSettings.redactPiiOnShare,
                hideInRecents = appSettings.hideInRecents,
                persistHistory = appSettings.persistHistory,
                usernameCacheEntries = bot.usernameCacheSize(),
                catalogUrl = appSettings.catalogUrl,
                catalogSource = info?.source ?: it.catalogSource,
                catalogVersion = info?.version ?: it.catalogVersion,
            )
        }
    }

    private fun showStatus(
        text: String,
        progress: SiteCheckProgress? = null,
        username: String = "",
    ) {
        val status = ChatMessage(
            id = ChatHistoryCodec.STATUS_MESSAGE_ID,
            text = text,
            fromBot = true,
            isTyping = true,
        )
        _state.update { state ->
            state.copy(
                messages = state.messages.filterNot { it.id == ChatHistoryCodec.STATUS_MESSAGE_ID } + status,
                scanProgress = progress?.let {
                    ScanProgressUi(
                        username = username.ifBlank { lastScanUsername },
                        done = it.done,
                        total = it.total,
                        site = it.site,
                    )
                },
            )
        }
    }

    private fun hideStatus() {
        _state.update { state ->
            state.copy(
                messages = state.messages.filterNot { it.id == ChatHistoryCodec.STATUS_MESSAGE_ID },
                scanProgress = null,
            )
        }
    }

    class Factory(
        private val application: Application,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(WorkbenchViewModel::class.java)) {
                return WorkbenchViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
