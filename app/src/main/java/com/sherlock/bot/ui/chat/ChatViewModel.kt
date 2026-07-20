package com.sherlock.bot.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sherlock.bot.data.ChatMessage
import com.sherlock.bot.data.SearchMode
import com.sherlock.bot.domain.BotInteractor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isBusy: Boolean = false,
    val pendingMode: SearchMode = SearchMode.NONE,
)

class ChatViewModel(
    private val bot: BotInteractor = BotInteractor(),
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        _state.update { it.copy(messages = listOf(bot.welcome())) }
    }

    fun onInputChange(value: String) {
        _state.update { it.copy(input = value) }
    }

    fun send() {
        val text = _state.value.input.trim()
        if (text.isEmpty() || _state.value.isBusy) return

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
        respond {
            bot.handleUserText(text, _state.value.pendingMode)
        }
    }

    fun onAction(actionId: String) {
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            showTyping()
            delay(350)
            val (message, mode) = bot.handleAction(actionId)
            hideTyping()
            _state.update {
                it.copy(
                    messages = it.messages + message,
                    pendingMode = mode,
                    isBusy = false,
                )
            }
        }
    }

    private fun respond(block: suspend () -> Pair<List<ChatMessage>, SearchMode>) {
        viewModelScope.launch {
            showTyping()
            delay(400)
            val (replies, mode) = block()
            hideTyping()
            _state.update {
                it.copy(
                    messages = it.messages + replies,
                    pendingMode = mode,
                    isBusy = false,
                )
            }
        }
    }

    private fun showTyping() {
        val typing = ChatMessage(
            id = "typing",
            text = "Sherlock печатает…",
            fromBot = true,
            isTyping = true,
        )
        _state.update { state ->
            state.copy(messages = state.messages.filterNot { it.isTyping } + typing)
        }
    }

    private fun hideTyping() {
        _state.update { state ->
            state.copy(messages = state.messages.filterNot { it.isTyping })
        }
    }
}
