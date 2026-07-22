package com.sherlock.bot.domain

import com.sherlock.bot.data.SearchMode
import org.junit.Assert.assertEquals
import org.junit.Test

class QueryClassifierTest {

    @Test
    fun detectsEmail() {
        assertEquals(SearchMode.EMAIL, QueryClassifier.detectMode("name@mail.ru"))
        assertEquals(SearchMode.EMAIL, QueryClassifier.detectMode("user.name+tag@example.com"))
    }

    @Test
    fun detectsPhone() {
        assertEquals(SearchMode.PHONE, QueryClassifier.detectMode("+79001234567"))
        assertEquals(SearchMode.PHONE, QueryClassifier.detectMode("8 (900) 123-45-67"))
        assertEquals(SearchMode.PHONE, QueryClassifier.detectMode("9001234567"))
    }

    @Test
    fun detectsFullName() {
        assertEquals(SearchMode.FULL_NAME, QueryClassifier.detectMode("Иванов Иван"))
        assertEquals(SearchMode.FULL_NAME, QueryClassifier.detectMode("Иванов Иван Петрович"))
        assertEquals(SearchMode.FULL_NAME, QueryClassifier.detectMode("John Smith"))
    }

    @Test
    fun detectsUsername() {
        assertEquals(SearchMode.USERNAME, QueryClassifier.detectMode("durov"))
        assertEquals(SearchMode.USERNAME, QueryClassifier.detectMode("@octocat"))
        assertEquals(SearchMode.USERNAME, QueryClassifier.detectMode("user_name.01"))
    }

    @Test
    fun atUsernameIsNotEmail() {
        assertEquals(SearchMode.USERNAME, QueryClassifier.detectMode("@telegram"))
    }

    @Test
    fun unknownReturnsNone() {
        assertEquals(SearchMode.NONE, QueryClassifier.detectMode("а"))
        assertEquals(SearchMode.NONE, QueryClassifier.detectMode("!!!"))
        assertEquals(SearchMode.NONE, QueryClassifier.detectMode(""))
    }
}
