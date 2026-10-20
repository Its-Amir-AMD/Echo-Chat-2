package com.example.data.remote

import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.firestore
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
        get() = FirebaseAuth.getInstance()

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

    /** Sign in with an existing email/password account. */
    suspend fun signInWithEmail(email: String, password: String): FirebaseUser {
        require(email.isNotBlank()) { "Enter your email address." }
        require(password.isNotEmpty()) { "Enter your password." }
        return auth.signInWithEmailAndPassword(email.trim(), password).await().user
            ?: throw IllegalStateException("Firebase did not return a signed-in user.")
    }

    /** Create an account. Firebase must have the Email/Password provider enabled. */
    suspend fun createAccountWithEmail(
        email: String,
        password: String,
        displayName: String,
        username: String
    ): FirebaseUser {
        require(email.isNotBlank()) { "Enter your email address." }
        require(password.length >= 8) { "Use a password with at least 8 characters." }
        val normalizedUsername = username.trim().lowercase(java.util.Locale.ROOT)
        require(normalizedUsername.matches(Regex("^[a-z0-9_]{3,24}$"))) {
            "Username must be 3–24 characters using only letters, numbers, and underscores."
        }

        val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user
            ?: throw IllegalStateException("Firebase did not create the account.")
        try {
            val name = displayName.trim().ifEmpty { normalizedUsername }
            user.updateProfile(
                UserProfileChangeRequest.Builder().setDisplayName(name).build()
            ).await()
            reserveUsername(user, normalizedUsername, name)
            return auth.currentUser ?: user
        } catch (error: Exception) {
            // Do not leave a half-registered account when a username reservation failed.
            runCatching { user.delete().await() }
            auth.signOut()
            throw error
        }
    }

    private suspend fun reserveUsername(user: FirebaseUser, username: String, displayName: String) {
        val firestore = com.google.firebase.Firebase.firestore
        val usernameRef = firestore.collection("usernames").document(username)
        val profileRef = firestore.collection("publicUsers").document(user.uid)
        firestore.runTransaction { transaction ->
            val existing = transaction.get(usernameRef)
            if (existing.exists() && existing.getString("uid") != user.uid) {
                throw IllegalArgumentException("That username is already taken. Choose another one.")
            }
            transaction.set(usernameRef, mapOf("uid" to user.uid, "usernameLower" to username))
            transaction.set(
                profileRef,
                mapOf(
                    "uid" to user.uid,
                    "username" to username,
                    "usernameLower" to username,
                    "displayName" to displayName,
                    "createdAt" to System.currentTimeMillis()
                )
            )
            null
        }.await()
    }

    /** Send Firebase's standard password-reset email. */
    suspend fun sendPasswordResetEmail(email: String) {
        require(email.isNotBlank()) { "Enter your email address first." }
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    /**
     * Launches the Credential Manager Google Sign-In flow and exchanges the resulting
     * ID token for a Firebase session. Returns the signed-in [FirebaseUser].
     *
     * Throws if Firebase is not configured, Google Play Services is unavailable, or the
     * user cancels the picker — callers should catch and show a friendly local-mode
     * fallback rather than crash.
     */
    suspend fun signInWithGoogle(context: Context): FirebaseUser {
        val resolvedWebClientId = resolveWebClientId(context)
        require(resolvedWebClientId.isNotBlank() && resolvedWebClientId != DEFAULT_WEB_CLIENT_ID) {
            "Google sign-in is not configured. Add a valid google-services.json from your Firebase project."
        }

        val nonce = generateNonce()
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(resolvedWebClientId)
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

    private fun resolveWebClientId(context: Context): String {
        // The Google Services Gradle plugin creates this resource from google-services.json.
        val resourceId = context.resources.getIdentifier(
            "default_web_client_id", "string", context.packageName
        )
        return if (resourceId != 0) context.getString(resourceId) else webClientId
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
