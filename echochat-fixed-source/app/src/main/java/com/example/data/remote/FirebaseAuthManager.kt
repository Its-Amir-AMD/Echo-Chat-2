package com.example.data.remote

import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
private val auth: FirebaseAuth
    get() = FirebaseAuth.getInstance()
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Thin wrapper around Firebase Authentication with Google Sign-In.
 *
 * This boundary is intentionally separate from [EchoChatRepository] and [EchoChatDao]:
 * the rest of the app keeps working against local Room data whether or not a real
 * Firebase project is linked. When `google-services.json` is missing or invalid,
 * every call here fails safely and the caller can continue in local-only mode.
 *
 * To enable cloud sign-in:
 *  1. Create a Firebase project at https://console.firebase.google.com
 *  2. Add an Android app with applicationId "com.aistudio.echochat.vpkzqr"
 *  3. Enable the "Google" sign-in provider in Authentication settings
 *  4. Download google-services.json into the app/ module directory
 *  5. Add your Web client ID (from the Firebase/Google Cloud console) as
 *     WEB_CLIENT_ID below, or supply it via BuildConfig.
 */
class FirebaseAuthManager(
    private val webClientId: String = DEFAULT_WEB_CLIENT_ID
) {
    private val auth: FirebaseAuth
        get() = Firebase.auth

    val currentUser: FirebaseUser?
        get() = runCatching { auth.currentUser }.getOrNull()

    val isSignedIn: Boolean
        get() = currentUser != null

    /** Emits the current user on every auth state change; null when signed out. */
    fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        val authInstance = runCatching { auth }.getOrNull()
        if (authInstance == null) {
            trySend(null)
            close()
            return@callbackFlow
        }
        authInstance.addAuthStateListener(listener)
        awaitClose { authInstance.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    /**
     * Launches the Credential Manager Google Sign-In flow and exchanges the resulting
     * ID token for a Firebase session. Returns the signed-in [FirebaseUser].
     *
     * Throws if Firebase is not configured, Google Play Services is unavailable, or the
     * user cancels the picker — callers should catch and show a friendly local-mode
     * fallback rather than crash.
     */
    suspend fun signInWithGoogle(context: Context): FirebaseUser {
        require(webClientId.isNotBlank() && webClientId != DEFAULT_WEB_CLIENT_ID) {
            "Set a real OAuth Web Client ID (from the Firebase console) before enabling Google sign-in."
        }

        val nonce = generateNonce()
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setNonce(nonce)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val credentialManager = CredentialManager.create(context)
        val result = try {
            credentialManager.getCredential(context, request)
        } catch (e: GetCredentialException) {
            throw IllegalStateException("Google sign-in was cancelled or unavailable.", e)
        }

        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
        val authResult = auth.signInWithCredential(firebaseCredential).await()
        return authResult.user
            ?: throw IllegalStateException("Firebase did not return a signed-in user.")
    }

    fun signOut() {
        runCatching { auth.signOut() }
    }

    private fun generateNonce(): String {
        // android.util.Base64 is used instead of java.util.Base64, which only exists
        // from API 26 — this app supports minSdk 24.
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val rawNonce = android.util.Base64.encodeToString(
            bytes,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        )
        // Google ID tokens carry a SHA-256 hash of the nonce, not the raw value.
        val digest = MessageDigest.getInstance("SHA-256").digest(rawNonce.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        // Replace with the OAuth 2.0 Web Client ID from the Firebase console
        // (Authentication > Sign-in method > Google > Web SDK configuration).
        const val DEFAULT_WEB_CLIENT_ID = "REPLACE_WITH_FIREBASE_WEB_CLIENT_ID"
    }
}
