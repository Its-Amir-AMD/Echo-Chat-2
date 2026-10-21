package com.example.data.remote

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ApiUser(
    val id: String,
    val username: String,
    val displayName: String,
    val email: String,
    val createdAt: Long = 0L
)

data class ApiRoom(
    val id: String,
    val name: String,
    val description: String,
    val iconName: String,
    val lastMessage: String,
    val lastMessageTime: Long
)

data class ApiConversation(
    val id: String,
    val peerId: String,
    val peerUsername: String,
    val peerDisplayName: String,
    val lastMessage: String,
    val lastMessageTime: Long,
    val createdAt: Long
)

data class ApiMessage(
    val id: String,
    val channelId: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val timestamp: Long
)

/**
 * Small HTTPS-only REST client for the self-hosted EchoChat PHP API.
 * The base URL and bearer token are stored only in app-private preferences.
 */
class PhpApiClient(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "echochat_php_backend",
        Context.MODE_PRIVATE
    )
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(22, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val currentServerUrl: String
        get() = prefs.getString(KEY_BASE_URL, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_BASE_URL

    val isConfigured: Boolean
        get() = currentServerUrl.isNotBlank()

    val hasToken: Boolean
        get() = prefs.getString(KEY_TOKEN, null)?.isNotBlank() == true

    val cachedUser: ApiUser?
        get() {
            val id = prefs.getString(KEY_USER_ID, null) ?: return null
            val username = prefs.getString(KEY_USERNAME, null) ?: return null
            return ApiUser(
                id = id,
                username = username,
                displayName = prefs.getString(KEY_DISPLAY_NAME, username).orEmpty(),
                email = prefs.getString(KEY_EMAIL, "").orEmpty()
            )
        }

    fun configureBaseUrl(rawUrl: String) {
        val normalized = rawUrl.trim().trimEnd('/')
        require(normalized.isNotBlank()) { "آدرس سرور را وارد کنید." }
        val uri = runCatching { java.net.URI(normalized) }.getOrNull()
            ?: throw IllegalArgumentException("آدرس سرور معتبر نیست.")
        require(uri.scheme == "https" || (uri.scheme == "http" && uri.host in LOCAL_HTTP_HOSTS)) {
            "برای امنیت، آدرس سرور باید HTTPS باشد. HTTP فقط برای localhost مجاز است."
        }
        require(!uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "آدرس سرور معتبر نیست."
        }
        require(!normalized.endsWith("/index.php", ignoreCase = true)) {
            "آدرس پوشهٔ API را وارد کنید، نه آدرس index.php."
        }
        prefs.edit().putString(KEY_BASE_URL, normalized).apply()
    }

    fun currentToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun saveAuthenticatedSession(token: String, user: ApiUser) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER_ID, user.id)
            .putString(KEY_USERNAME, user.username)
            .putString(KEY_DISPLAY_NAME, user.displayName)
            .putString(KEY_EMAIL, user.email)
            .apply()
    }

    fun clearLocalSession() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_USERNAME)
            .remove(KEY_DISPLAY_NAME)
            .remove(KEY_EMAIL)
            .apply()
    }

    suspend fun checkHealth(): String {
        val data = request("GET", "/health", authenticated = false)
        return data.optString("database", "unknown")
    }

    suspend fun login(email: String, password: String): ApiUser {
        require(email.contains("@")) { "ایمیل معتبر وارد کنید." }
        require(password.isNotEmpty()) { "رمز عبور را وارد کنید." }
        val data = request("POST", "/auth/login", JSONObject()
            .put("email", email.trim())
            .put("password", password), authenticated = false)
        return saveAuthResponse(data)
    }

    suspend fun register(email: String, password: String, displayName: String, username: String): ApiUser {
        require(email.contains("@")) { "ایمیل معتبر وارد کنید." }
        require(password.length >= 8) { "رمز عبور باید حداقل ۸ نویسه باشد." }
        require(displayName.trim().isNotEmpty()) { "نام نمایشی را وارد کنید." }
        require(username.matches(Regex("^[a-z0-9_]{3,24}$"))) {
            "نام کاربری باید ۳ تا ۲۴ حرف انگلیسی، عدد یا زیرخط باشد."
        }
        val data = request("POST", "/auth/register", JSONObject()
            .put("email", email.trim())
            .put("password", password)
            .put("display_name", displayName.trim())
            .put("username", username.lowercase()), authenticated = false)
        return saveAuthResponse(data)
    }

    private fun saveAuthResponse(data: JSONObject): ApiUser {
        val token = data.optString("token")
        if (token.length != 64) throw IOException("سرور توکن ورود معتبر برنگرداند.")
        val user = parseUser(data.getJSONObject("user"))
        saveAuthenticatedSession(token, user)
        return user
    }

    suspend fun currentUser(): ApiUser {
        val data = request("GET", "/auth/me")
        val user = parseUser(data.getJSONObject("user"))
        val token = currentToken() ?: throw IOException("نشست ورود پیدا نشد.")
        saveAuthenticatedSession(token, user)
        return user
    }

    suspend fun logout() {
        try {
            if (hasToken && isConfigured) request("POST", "/auth/logout", JSONObject())
        } finally {
            clearLocalSession()
        }
    }

    suspend fun getRooms(): List<ApiRoom> {
        val array = request("GET", "/rooms").getJSONArray("rooms")
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(ApiRoom(
                    id = item.getString("id"),
                    name = item.optString("name"),
                    description = item.optString("description"),
                    iconName = item.optString("icon_name", "forum"),
                    lastMessage = item.optString("last_message"),
                    lastMessageTime = item.optLong("last_message_time")
                ))
            }
        }
    }

    suspend fun getConversations(): List<ApiConversation> {
        val array = request("GET", "/conversations").getJSONArray("conversations")
        return buildList {
            for (i in 0 until array.length()) add(parseConversation(array.getJSONObject(i)))
        }
    }

    suspend fun openConversation(username: String): ApiConversation {
        val data = request("POST", "/conversations", JSONObject()
            .put("username", username.trim().removePrefix("@").lowercase()))
        return parseConversation(data.getJSONObject("conversation"))
    }

    suspend fun getMessages(channelId: String): List<ApiMessage> {
        val query = if (channelId.startsWith("dm_")) {
            "conversation_id=" + encode(channelId)
        } else {
            "room_id=" + encode(channelId)
        }
        val array = request("GET", "/messages?$query").getJSONArray("messages")
        return buildList {
            for (i in 0 until array.length()) add(parseMessage(array.getJSONObject(i)))
        }
    }

    suspend fun sendMessage(channelId: String, clientMessageId: String, content: String): ApiMessage {
        val data = request("POST", "/messages", JSONObject()
            .put("channel_id", channelId)
            .put("client_message_id", clientMessageId)
            .put("content", content))
        return parseMessage(data.getJSONObject("message"))
    }

    private suspend fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        authenticated: Boolean = true
    ): JSONObject = withContext(Dispatchers.IO) {
        val base = currentServerUrl
        if (base.isBlank()) throw IOException("هنوز آدرس سرور EchoChat تنظیم نشده است.")
        val builder = Request.Builder().url(base + path).header("Accept", "application/json")
        val token = currentToken()
        if (authenticated) {
            if (token.isNullOrBlank()) throw IOException("برای ادامه وارد حساب خود شوید.")
            builder.header("Authorization", "Bearer $token")
        }
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post((body ?: JSONObject()).toString().toRequestBody(jsonMediaType))
            else -> throw IllegalArgumentException("Unsupported HTTP method.")
        }
        val request = builder.build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val envelope = try {
                JSONObject(raw)
            } catch (_: Exception) {
                throw IOException("پاسخ سرور معتبر نیست (HTTP " + response.code + ").")
            }
            if (!response.isSuccessful || !envelope.optBoolean("ok", false)) {
                val message = envelope.optString("error").ifBlank {
                    "درخواست ناموفق بود (HTTP " + response.code + ")."
                }
                throw IOException(message)
            }
            envelope.optJSONObject("data") ?: JSONObject()
        }
    }

    private fun parseUser(item: JSONObject) = ApiUser(
        id = item.getString("id"),
        username = item.getString("username"),
        displayName = item.optString("display_name", item.optString("username")),
        email = item.optString("email"),
        createdAt = item.optLong("created_at_ms")
    )

    private fun parseConversation(item: JSONObject) = ApiConversation(
        id = item.getString("id"),
        peerId = item.getString("peer_id"),
        peerUsername = item.optString("peer_username"),
        peerDisplayName = item.optString("peer_display_name", item.optString("peer_username")),
        lastMessage = item.optString("last_message"),
        lastMessageTime = item.optLong("last_message_time"),
        createdAt = item.optLong("created_at")
    )

    private fun parseMessage(item: JSONObject) = ApiMessage(
        id = item.getString("id"),
        channelId = item.getString("channel_id"),
        senderId = item.getString("sender_id"),
        senderName = item.optString("sender_name"),
        content = item.optString("content"),
        timestamp = item.optLong("timestamp")
    )

    private fun encode(value: String) = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    companion object {
        private const val DEFAULT_BASE_URL = "https://chat.userdesk.ir/chat"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_TOKEN = "access_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_EMAIL = "email"
        private val LOCAL_HTTP_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2", "::1")
    }
}
