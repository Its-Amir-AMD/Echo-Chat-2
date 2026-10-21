package com.example.data.remote

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhpApiClientTest {

    private fun freshClient(): PhpApiClient {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("echochat_php_backend", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        return PhpApiClient(context)
    }

    @Test
    fun defaultsToUserDeskApi() {
        val client = freshClient()

        assertEquals("https://chat.userdesk.ir/chat", client.currentServerUrl)
        assertTrue(client.isConfigured)
        assertFalse(client.hasToken)
    }

    @Test
    fun normalizesApiFolderUrl() {
        val client = freshClient()

        client.configureBaseUrl("https://chat.userdesk.ir/chat/")

        assertEquals("https://chat.userdesk.ir/chat", client.currentServerUrl)
    }

    @Test
    fun rejectsPublicCleartextHttp() {
        val client = freshClient()
        val error = try {
            client.configureBaseUrl("http://chat.userdesk.ir/chat")
            null
        } catch (exception: IllegalArgumentException) {
            exception
        }

        assertNotNull(error)
        assertTrue(error!!.message.orEmpty().contains("HTTPS"))
    }
}
