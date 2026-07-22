package com.sherlock.bot.data

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ExternalLinksTest {

    @Test
    fun canonicalizesInstagramProfilePath() {
        val uri = Uri.parse("https://instagram.com/durov")
        assertEquals(
            "https://www.instagram.com/durov/",
            ExternalLinks.preferInstagramWebUri(uri).toString(),
        )
    }

    @Test
    fun keepsInstagramPostPaths() {
        val uri = Uri.parse("https://www.instagram.com/p/AbCd123/")
        assertEquals(uri.toString(), ExternalLinks.preferInstagramWebUri(uri).toString())
    }
}
