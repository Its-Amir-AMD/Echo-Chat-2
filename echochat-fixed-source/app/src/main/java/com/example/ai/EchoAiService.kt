package com.example.ai

/**
 * Server-backed Echo assistant adapter.
 *
 * The provider credentials stay on the PHP server and are never bundled into the APK.
 * Pass a suspend network function from the repository's configured API client.
 */
class EchoAiService(
    private val requestReply: suspend (String) -> String
) {
    suspend fun generateReply(prompt: String): String {
        val cleanPrompt = prompt.trim()
        require(cleanPrompt.isNotEmpty()) { "Prompt must not be empty." }
        require(cleanPrompt.length <= MAX_PROMPT_LENGTH) {
            "Prompt is too long. Limit it to $MAX_PROMPT_LENGTH characters."
        }
        return requestReply(cleanPrompt).trim().takeIf(String::isNotEmpty)
            ?: throw IllegalStateException("The AI provider returned an empty response.")
    }

    private companion object {
        const val MAX_PROMPT_LENGTH = 8_000
    }
}
