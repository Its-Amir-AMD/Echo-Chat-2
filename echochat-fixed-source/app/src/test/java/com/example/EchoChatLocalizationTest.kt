package com.example

import androidx.compose.ui.unit.LayoutDirection
import com.example.util.AppLanguage
import com.example.util.EchoStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class EchoChatLocalizationTest {

    @Test
    fun testPersianLayoutDirectionIsRTL() {
        assertEquals(LayoutDirection.Rtl, AppLanguage.PERSIAN.layoutDirection)
    }

    @Test
    fun testEnglishLayoutDirectionIsLTR() {
        assertEquals(LayoutDirection.Ltr, AppLanguage.ENGLISH.layoutDirection)
    }

    @Test
    fun testEchoStringsContainKeyPhrases() {
        val faAppTitle = EchoStrings.get("app_title", AppLanguage.PERSIAN)
        val enAppTitle = EchoStrings.get("app_title", AppLanguage.ENGLISH)

        assertEquals("اکو چت", faAppTitle)
        assertEquals("EchoChat", enAppTitle)

        assertNotNull(EchoStrings.get("send", AppLanguage.PERSIAN))
        assertNotNull(EchoStrings.get("send", AppLanguage.ENGLISH))
        assertNotNull(EchoStrings.get("tab_channels", AppLanguage.PERSIAN))
        assertNotNull(EchoStrings.get("tab_channels", AppLanguage.ENGLISH))
        assertEquals("نتایج جستجوی پیام‌ها", EchoStrings.get("message_search_results_title", AppLanguage.PERSIAN))
        assertEquals("Message search results", EchoStrings.get("message_search_results_title", AppLanguage.ENGLISH))
        assertEquals("استوری من", EchoStrings.get("my_story", AppLanguage.PERSIAN))
        assertEquals("My story", EchoStrings.get("my_story", AppLanguage.ENGLISH))
    }

    @Test
    fun testNewlyLocalizedStringsHaveBothLanguages() {
        val keysRequiringBothLanguages = listOf(
            "post_category_label",
            "channel_icon_label",
            "contact_name_label",
            "suggested_contacts_label",
            "avatar_color_label",
            "my_shared_posts_title",
            "cloud_account_title",
            "cloud_account_sign_in",
            "cloud_account_sign_out"
        )
        keysRequiringBothLanguages.forEach { key ->
            val fa = EchoStrings.get(key, AppLanguage.PERSIAN)
            val en = EchoStrings.get(key, AppLanguage.ENGLISH)
            assertNotNull("Missing Persian string for key: $key", fa)
            assertNotNull("Missing English string for key: $key", en)
            assert(fa.isNotBlank()) { "Persian string for key $key must not be blank" }
            assert(en.isNotBlank()) { "English string for key $key must not be blank" }
        }
    }
}
