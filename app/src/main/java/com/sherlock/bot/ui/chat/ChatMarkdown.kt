package com.sherlock.bot.ui.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

/**
 * Lightweight chat markdown: `code`, *bold*, [label](url), and bare http(s) links.
 */
object ChatMarkdown {

    private val tokenRegex = Regex(
        """(\[[^\]\n]+]\(https?://[^)\s]+\)|`[^`]+`|\*[^*\n]+\*|https?://[^\s)\]>]+)""",
    )

    fun toAnnotatedString(
        text: String,
        linkColor: Color = Color(0xFFC9A227),
        codeColor: Color = Color(0xFFE8D5A3),
        codeBackground: Color = Color(0x33415566),
        onLinkClick: ((String) -> Unit)? = null,
    ): AnnotatedString = buildAnnotatedString {
        var cursor = 0
        for (match in tokenRegex.findAll(text)) {
            if (match.range.first > cursor) {
                append(text.substring(cursor, match.range.first))
            }
            val token = match.value
            when {
                token.startsWith('[') && token.contains("](http") -> {
                    val label = token.substringAfter('[').substringBefore(']')
                    val url = token.substringAfter("](").substringBefore(')')
                    appendMarkdownLink(label, url, linkColor, onLinkClick)
                }
                token.startsWith('`') && token.endsWith('`') && token.length >= 2 -> {
                    val code = token.substring(1, token.lastIndex)
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            color = codeColor,
                            background = codeBackground,
                            fontWeight = FontWeight.Medium,
                        ),
                    ) {
                        append(code)
                    }
                }
                token.startsWith('*') && token.endsWith('*') && token.length >= 2 -> {
                    val bold = token.substring(1, token.lastIndex)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(bold)
                    }
                }
                token.startsWith("http") -> {
                    val url = token.trimEnd('.', ',', ';', ')', ']')
                    val trailing = token.removePrefix(url)
                    appendMarkdownLink(shortenUrlLabel(url), url, linkColor, onLinkClick)
                    if (trailing.isNotEmpty()) append(trailing)
                }
                else -> append(token)
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }

    /** Visible plain text after stripping markers (for tests / share previews). */
    fun plainVisible(text: String): String = buildString {
        var cursor = 0
        for (match in tokenRegex.findAll(text)) {
            if (match.range.first > cursor) {
                append(text.substring(cursor, match.range.first))
            }
            val token = match.value
            when {
                token.startsWith('[') && token.contains("](http") ->
                    append(token.substringAfter('[').substringBefore(']'))
                token.startsWith('`') && token.endsWith('`') && token.length >= 2 ->
                    append(token.substring(1, token.lastIndex))
                token.startsWith('*') && token.endsWith('*') && token.length >= 2 ->
                    append(token.substring(1, token.lastIndex))
                token.startsWith("http") ->
                    append(shortenUrlLabel(token.trimEnd('.', ',', ';', ')', ']')))
                else -> append(token.trimEnd('.', ',', ';', ')', ']'))
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) append(text.substring(cursor))
    }

    private fun AnnotatedString.Builder.appendMarkdownLink(
        label: String,
        url: String,
        linkColor: Color,
        onLinkClick: ((String) -> Unit)?,
    ) {
        val styles = TextLinkStyles(
            style = SpanStyle(
                color = linkColor,
                textDecoration = TextDecoration.Underline,
                fontWeight = FontWeight.Medium,
            ),
        )
        val annotation = if (onLinkClick != null) {
            LinkAnnotation.Url(
                url = url,
                styles = styles,
                linkInteractionListener = { onLinkClick(url) },
            )
        } else {
            LinkAnnotation.Url(url = url, styles = styles)
        }
        withLink(annotation) {
            append(label)
        }
    }

    /** Host (+ short path) for bare URLs so report body stays readable. */
    fun shortenUrlLabel(url: String): String {
        val bare = url.removePrefix("https://").removePrefix("http://")
        val slash = bare.indexOf('/')
        if (slash < 0) return bare.take(40)
        val host = bare.substring(0, slash)
        val path = bare.substring(slash)
        return if (path.length <= 18) host + path else host + path.take(16) + "…"
    }
}
