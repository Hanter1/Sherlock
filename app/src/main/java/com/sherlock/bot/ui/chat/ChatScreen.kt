package com.sherlock.bot.ui.chat

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.sherlock.bot.data.BotAction
import com.sherlock.bot.data.ChatMessage
import com.sherlock.bot.data.SearchMode

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0B1220),
                        Color(0xFF101A2C),
                        Color(0xFF0A101A),
                    ),
                ),
            ),
    ) {
        // Subtle atmospheric pattern
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x33C9A227), Color.Transparent),
                        radius = 900f,
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            TopBar(pendingMode = state.pendingMode)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.messages, key = { it.id }) { message ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically { it / 4 },
                    ) {
                        MessageBubble(
                            message = message,
                            onAction = viewModel::onAction,
                        )
                    }
                }
            }
            Composer(
                value = state.input,
                enabled = !state.isBusy,
                onValueChange = viewModel::onInputChange,
                onSend = viewModel::send,
            )
        }
    }
}

@Composable
private fun TopBar(pendingMode: SearchMode) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color(0xFF1C2738))
                .border(1.dp, Color(0xFFC9A227), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = Color(0xFFC9A227),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Sherlock Bot",
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFFE8D5A3),
            )
            Text(
                text = when (pendingMode) {
                    SearchMode.NONE -> "OSINT · открытые источники"
                    SearchMode.USERNAME -> "ждёт никнейм…"
                    SearchMode.PHONE -> "ждёт телефон…"
                    SearchMode.EMAIL -> "ждёт email…"
                    SearchMode.FULL_NAME -> "ждёт ФИО…"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    onAction: (String) -> Unit,
) {
    val align = if (message.fromBot) Alignment.CenterStart else Alignment.CenterEnd
    val bubbleColor = if (message.fromBot) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val shape = if (message.fromBot) {
        RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.fromBot) Alignment.Start else Alignment.End,
    ) {
        if (message.fromBot) {
            Text(
                text = "Sherlock",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFC9A227),
                modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
            )
        }
        Box(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .clip(shape)
                .background(bubbleColor)
                .border(
                    width = if (message.fromBot) 1.dp else 0.dp,
                    color = Color(0x22C9A227),
                    shape = shape,
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = align,
        ) {
            if (message.isTyping) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LinkifiedText(text = message.text)
            }
        }
        if (message.actions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            ActionKeyboard(actions = message.actions, onAction = onAction)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionKeyboard(
    actions: List<BotAction>,
    onAction: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        actions.forEach { action ->
            Text(
                text = action.label,
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFE8D5A3),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1C2738))
                    .border(1.dp, Color(0x55C9A227), RoundedCornerShape(12.dp))
                    .clickable { onAction(action.id) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun LinkifiedText(text: String) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val urlRegex = Regex("""https?://[^\s)]+""")
    val annotated = buildAnnotatedString {
        var last = 0
        for (match in urlRegex.findAll(text)) {
            append(text.substring(last, match.range.first))
            val url = match.value.trimEnd('.', ',', ';')
            pushStringAnnotation(tag = "URL", annotation = url)
            withStyle(
                SpanStyle(
                    color = Color(0xFFC9A227),
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.Medium,
                ),
            ) {
                append(url)
            }
            pop()
            last = match.range.last + 1
        }
        if (last < text.length) append(text.substring(last))
    }

    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onBackground,
        ),
        onClick = { offset ->
            annotated.getStringAnnotations("URL", offset, offset)
                .firstOrNull()
                ?.let { ann ->
                    runCatching { uriHandler.openUri(ann.item) }
                        .recoverCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(ann.item)),
                            )
                        }
                }
        },
    )
}

@Composable
private fun Composer(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xCC0B1220))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                placeholder = {
                    Text("Сообщение Sherlock Bot…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                shape = RoundedCornerShape(22.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFC9A227),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedContainerColor = Color(0xFF141C2B),
                    unfocusedContainerColor = Color(0xFF141C2B),
                    cursorColor = Color(0xFFC9A227),
                    focusedTextColor = Color(0xFFE8D5A3),
                    unfocusedTextColor = Color(0xFFE8D5A3),
                ),
            )
            IconButton(
                onClick = onSend,
                enabled = enabled && value.isNotBlank(),
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (enabled && value.isNotBlank()) Color(0xFFC9A227) else Color(0xFF334155),
                    ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Отправить",
                    tint = Color(0xFF0B1220),
                )
            }
        }
        Text(
            text = "Только публичные источники · без утечек и закрытых баз",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        )
    }
}
