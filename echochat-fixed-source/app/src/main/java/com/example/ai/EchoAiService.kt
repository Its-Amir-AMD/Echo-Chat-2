package com.example.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.type.GenerativeBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Small boundary around Firebase AI Logic. Firebase configuration and App Check
 * must be completed in the linked project before requests can succeed.
 */
class EchoAiService {
    suspend fun generateReply(prompt: String): String = withContext(Dispatchers.IO) {
        val cleanPrompt = prompt.trim()
        require(cleanPrompt.isNotEmpty()) { "Prompt must not be empty." }
        require(cleanPrompt.length <= MAX_PROMPT_LENGTH) {
            "Prompt is too long. Limit it to $MAX_PROMPT_LENGTH characters."
        }

        val model = FirebaseAI.getInstance(
            Firebase.app,
            GenerativeBackend.googleAI()
        ).generativeModel(MODEL_NAME)
        val response = model.generateContent(
            "You are Echo, a helpful assistant inside the EchoChat Android app. " +
                "Reply in the same language as the user, preferably Persian when the prompt is Persian. " +
                "Be clear and helpful. User message:\n$cleanPrompt"
        )
        response.text?.trim()?.takeIf(String::isNotEmpty)
            ?: throw IllegalStateException("The AI provider returned an empty response.")
    }

    private companion object {
        const val MODEL_NAME = "gemini-3.8-flash"
        const val MAX_PROMPT_LENGTH = 8_000
    }
}
