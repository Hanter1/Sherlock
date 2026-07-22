package com.sherlock.bot.ui.chat

import androidx.compose.ui.text.LinkAnnotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMarkdownTest {

    @Test
    fun stripsCodeAndBoldMarkers() {
        val plain = ChatMarkdown.plainVisible("Ник `durov` · найдено: *3*")
        assertEquals("Ник durov · найдено: 3", plain)
        assertFalse(plain.contains('`'))
        assertFalse(plain.contains('*'))
    }

    @Test
    fun annotatedContainsUrlLinkAnnotation() {
        val annotated = ChatMarkdown.toAnnotatedString("См. https://github.com/durov и всё")
        val links = annotated.getLinkAnnotations(0, annotated.length)
        assertEquals(1, links.size)
        val url = (links.first().item as LinkAnnotation.Url).url
        assertEquals("https://github.com/durov", url)
        assertTrue(annotated.text.contains("github.com"))
        assertFalse(annotated.text.contains("https://github.com/durov"))
    }

    @Test
    fun markdownLabelLinkShowsLabelNotUrl() {
        val annotated = ChatMarkdown.toAnnotatedString(
            "• [Google BY](https://www.google.by/search?q=test&hl=be)",
        )
        val links = annotated.getLinkAnnotations(0, annotated.length)
        assertEquals(1, links.size)
        assertEquals("https://www.google.by/search?q=test&hl=be", (links.first().item as LinkAnnotation.Url).url)
        assertTrue(annotated.text.contains("Google BY"))
        assertFalse(annotated.text.contains("https://"))
        assertEquals("• Google BY", ChatMarkdown.plainVisible("• [Google BY](https://www.google.by/search?q=test&hl=be)"))
    }

    @Test
    fun annotatedDropsMarkersFromVisibleText() {
        val annotated = ChatMarkdown.toAnnotatedString("Отчёт по нику `user` · *ok*")
        assertEquals("Отчёт по нику user · ok", annotated.text)
        assertTrue(annotated.spanStyles.isNotEmpty())
    }
}
