package com.example.data.remote

import com.example.data.local.EchoChatDao
import com.example.data.model.ChannelEntity
import com.example.data.model.CloudPublicUser
import com.example.data.model.DirectConversationDocument
import com.example.data.model.MessageEntity
import com.google.firebase.Firebase
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale
import java.util.UUID

/**
 * Room is the offline cache. Public rooms live under /channels, while private
 * conversations have explicit memberIds under /conversations. Usernames are resolved
 * through exact /usernames/{handle} document reads; email addresses are never exposed
 * in the public directory.
 */
class FirestoreSyncManager(
    private val dao: EchoChatDao,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val db get() = Firebase.firestore

    private var channelListPushJob: Job? = null
    private var channelsPullListener: ListenerRegistration? = null
    private var conversationsPullListener: ListenerRegistration? = null
    private val messagePushJobs = mutableMapOf<String, Job>()
    private val messagePullListeners = mutableMapOf<String, ListenerRegistration>()

    private val remoteChannels = java.util.concurrent.ConcurrentHashMap<String, ChannelEntity>()
    private val remoteChannelListInitialized = java.util.concurrent.atomic.AtomicBoolean(false)
    private val remoteConversations = java.util.concurrent.ConcurrentHashMap<String, DirectConversationDocument>()
    private val uploadingMessageIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /**
     * Erases device-local private/social state before another account is shown on this
     * device. Public room history is intentionally retained and refreshed from the cloud.
     */
    suspend fun clearLocalAccountScopedCache() {
        dao.deleteAllDirectMessages()
        dao.deleteAllPrivateChannelsExceptLocalAssistant()
        dao.deleteAllComments()
        dao.deleteAllPosts()
        dao.deleteAllStories()
        dao.deleteAllProfiles()
    }

    /** Start the live cloud listeners for the signed-in account. */
    fun start(userId: String, displayName: String?) {
        require(userId.isNotBlank()) { "A signed-in user id is required to start sync." }
        stop()

        channelListPushJob = dao.getAllChannels()
            .map { channels -> channels }
            .catch { /* Local cache stays available if the network is temporarily unavailable. */ }
            .onEach { channels ->
                channels.forEach { channel ->
                    // Local-only assistant/legacy DM channels have no remote peer membership.
                    if (channel.isDirectMessage && channel.peerId.isBlank()) return@forEach
                    if (channel.isDirectMessage) {
                        pushConversationPreview(channel)
                    } else {
                        pushChannelDocument(channel)
                    }
                    ensureMessagePusher(channel.id, userId, channel.isDirectMessage)
                    ensureMessagePullListener(channel.id, userId, channel.isDirectMessage)
                }
            }
            .launchIn(scope)

        channelsPullListener = db.collection("channels")
            .whereEqualTo("isDirectMessage", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val receivedChannels = snapshot.documents
                    .mapNotNull { it.toObject(ChannelEntity::class.java) }
                    .filterNot { it.isDirectMessage }

                receivedChannels.forEach { remoteChannels[it.id] = it }
                remoteChannelListInitialized.set(true)

                scope.launch {
                    receivedChannels.forEach { dao.insertChannel(it) }

                    // Upload only local public rooms absent from the initial server snapshot.
                    val localChannels = dao.getAllChannels().first().filterNot { it.isDirectMessage }
                    localChannels.forEach { pushChannelDocument(it) }
                }
            }

        conversationsPullListener = db.collection("conversations")
            .whereArrayContains("memberIds", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val conversations = snapshot.documents.mapNotNull {
                    it.toObject(DirectConversationDocument::class.java)
                }.filter { it.id.isNotBlank() && userId in it.memberIds && it.memberIds.size == 2 }

                conversations.forEach { remoteConversations[it.id] = it }
                scope.launch {
                    conversations.forEach { conversation ->
                        val peerId = conversation.memberIds.firstOrNull { it != userId } ?: return@forEach
                        val peerName = conversation.memberNames[peerId]
                            ?.takeIf { it.isNotBlank() }
                            ?: runCatching { loadPublicUser(peerId)?.displayName }.getOrNull()
                            ?: "Echo User"

                        dao.insertChannel(
                            ChannelEntity(
                                id = conversation.id,
                                name = peerName,
                                description = "گفتگوی خصوصی",
                                iconName = "person",
                                isDirectMessage = true,
                                peerName = peerName,
                                peerId = peerId,
                                lastMessage = conversation.lastMessage,
                                lastMessageTime = conversation.lastMessageTime,
                                createdAt = conversation.createdAt
                            )
                        )
                    }
                }
            }
    }

    /**
     * Ensures the account has a public, non-email directory card and returns its unique
     * handle. Google accounts receive an automatically generated handle if needed.
     */
    suspend fun ensureOwnDirectoryProfile(userId: String, displayName: String?): String {
        val profileRef = db.collection("publicUsers").document(userId)
        val existing = profileRef.get().await()
        val existingUsername = existing.getString("username")
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.matches(Regex("^[a-z0-9_]{3,32}$")) }

        val generated = buildString {
            append("u_")
            append(userId.lowercase(Locale.ROOT).take(8))
            append("_")
            append(userId.lowercase(Locale.ROOT).takeLast(8))
        }
        val candidate = existingUsername ?: generated
        val usernameRef = db.collection("usernames").document(candidate)

        return db.runTransaction { transaction ->
            val profile = transaction.get(profileRef)
            val selectedUsername = profile.getString("username")
                ?.lowercase(Locale.ROOT)
                ?.takeIf { it.matches(Regex("^[a-z0-9_]{3,32}$")) }
                ?: candidate
            val selectedRef = db.collection("usernames").document(selectedUsername)
            val usernameDoc = if (selectedUsername == candidate) {
                transaction.get(usernameRef)
            } else {
                transaction.get(selectedRef)
            }
            val currentOwner = usernameDoc.getString("uid")
            if (usernameDoc.exists() && currentOwner != userId) {
                throw IllegalStateException("Unable to reserve a public username. Choose another handle.")
            }

            val name = displayName?.trim()?.takeIf { it.isNotEmpty() }
                ?: profile.getString("displayName")?.takeIf { it.isNotEmpty() }
                ?: selectedUsername
            transaction.set(
                selectedRef,
                mapOf("uid" to userId, "usernameLower" to selectedUsername)
            )
            transaction.set(
                profileRef,
                mapOf(
                    "uid" to userId,
                    "username" to selectedUsername,
                    "usernameLower" to selectedUsername,
                    "displayName" to name,
                    "createdAt" to (profile.getLong("createdAt") ?: System.currentTimeMillis())
                )
            )
            selectedUsername
        }.await()
    }

    /** Resolve one username with an exact document read; no public directory listing. */
    suspend fun findPublicUserByUsername(username: String): CloudPublicUser? {
        val normalized = username.trim().removePrefix("@").lowercase(Locale.ROOT)
        require(normalized.matches(Regex("^[a-z0-9_]{3,32}$"))) {
            "Enter a valid username (3–32 letters, numbers, or underscores)."
        }

        val usernameSnapshot = db.collection("usernames").document(normalized).get().await()
        val uid = usernameSnapshot.getString("uid") ?: return null
        val profileSnapshot = db.collection("publicUsers").document(uid).get().await()
        if (!profileSnapshot.exists()) return null
        return CloudPublicUser(
            uid = uid,
            username = profileSnapshot.getString("username") ?: normalized,
            displayName = profileSnapshot.getString("displayName") ?: normalized,
            createdAt = profileSnapshot.getLong("createdAt") ?: 0L
        )
    }

    /** Creates or opens the deterministic two-member conversation for these two accounts. */
    suspend fun openDirectConversation(
        currentUserId: String,
        currentDisplayName: String,
        peer: CloudPublicUser
    ): String {
        require(currentUserId.isNotBlank() && peer.uid.isNotBlank()) { "Both accounts are required." }
        require(currentUserId != peer.uid) { "You cannot start a conversation with your own account." }

        val memberIds = listOf(currentUserId, peer.uid).sorted()
        val conversationId = "dm_" + memberIds.joinToString("_")
        val ref = db.collection("conversations").document(conversationId)
        val existing = ref.get().await()

        if (!existing.exists()) {
            val members = mapOf(
                currentUserId to currentDisplayName.ifBlank { "Echo User" },
                peer.uid to peer.displayName.ifBlank { peer.username }
            )
            val conversation = DirectConversationDocument(
                id = conversationId,
                memberIds = memberIds,
                memberNames = members,
                createdAt = System.currentTimeMillis()
            )
            try {
                ref.set(conversation).await()
            } catch (createError: Exception) {
                // Another device may have opened the same conversation at the same time.
                val raced = runCatching { ref.get().await() }.getOrNull()
                val racedMembers = raced?.get("memberIds") as? List<*>
                if (raced?.exists() != true || !racedMembers.orEmpty().containsAll(memberIds)) {
                    throw createError
                }
            }
        }

        val confirmed = ref.get().await()
        val confirmedMembers = confirmed.get("memberIds") as? List<*>
        require(confirmed.exists() && confirmedMembers.orEmpty().containsAll(memberIds)) {
            "The private conversation could not be created."
        }
        confirmed.toObject(DirectConversationDocument::class.java)?.let {
            remoteConversations[conversationId] = it
        }
        return conversationId
    }

    private suspend fun loadPublicUser(userId: String): CloudPublicUser? {
        val profile = db.collection("publicUsers").document(userId).get().await()
        if (!profile.exists()) return null
        return CloudPublicUser(
            uid = userId,
            username = profile.getString("username") ?: "",
            displayName = profile.getString("displayName") ?: "",
            createdAt = profile.getLong("createdAt") ?: 0L
        )
    }

    private suspend fun pushChannelDocument(channel: ChannelEntity) {
        if (!remoteChannelListInitialized.get()) return
        val remote = remoteChannels[channel.id]
        if (remote == channel) return

        // Existing server metadata is authoritative at login. Only newer real message
        // previews may update it; local seed data can never overwrite server history.
        if (remote != null) {
            val hasNewerMessage = channel.lastMessage.isNotBlank() &&
                channel.lastMessage != remote.lastMessage &&
                channel.lastMessageTime > remote.lastMessageTime
            if (!hasNewerMessage) return

            // Rules permit only preview fields to change on an existing public channel.
            runCatching {
                db.collection("channels").document(channel.id).update(
                    mapOf(
                        "lastMessage" to channel.lastMessage.take(10_000),
                        "lastMessageTime" to channel.lastMessageTime
                    )
                ).await()
                remoteChannels[channel.id] = remote.copy(
                    lastMessage = channel.lastMessage.take(10_000),
                    lastMessageTime = channel.lastMessageTime
                )
            }
            return
        }

        runCatching {
            db.collection("channels").document(channel.id).set(channel).await()
                .also { remoteChannels[channel.id] = channel }
        }
    }

    private suspend fun pushConversationPreview(channel: ChannelEntity) {
        if (channel.lastMessage.isBlank()) return
        val remote = remoteConversations[channel.id] ?: return
        if (channel.lastMessage == remote.lastMessage || channel.lastMessageTime <= remote.lastMessageTime) return

        runCatching {
            db.collection("conversations").document(channel.id).update(
                mapOf(
                    "lastMessage" to channel.lastMessage.take(10_000),
                    "lastMessageTime" to channel.lastMessageTime
                )
            ).await()
            remoteConversations[channel.id] = remote.copy(
                lastMessage = channel.lastMessage.take(10_000),
                lastMessageTime = channel.lastMessageTime
            )
        }
    }

    private fun messageCollection(channelId: String, isDirectMessage: Boolean) =
        if (isDirectMessage) {
            db.collection("conversations").document(channelId).collection("messages")
        } else {
            db.collection("channels").document(channelId).collection("messages")
        }

    private fun ensureMessagePusher(channelId: String, userId: String, isDirectMessage: Boolean) {
        val listenerKey = (if (isDirectMessage) "dm:" else "room:") + channelId
        if (messagePushJobs.containsKey(listenerKey)) return

        messagePushJobs[listenerKey] = dao.getMessagesForChannel(channelId)
            .catch { /* A single broken conversation must not stop other listeners. */ }
            .onEach { messages ->
                val ownedMessages = messages
                    .filter {
                        it.senderId == userId && !it.isSynced && it.id !in uploadingMessageIds
                    }
                    .takeLast(MAX_MESSAGES_PUSHED_PER_SYNC)
                if (ownedMessages.isEmpty()) return@onEach

                val ids = ownedMessages.map { it.id }
                uploadingMessageIds.addAll(ids)
                try {
                    var retryDelayMs = INITIAL_RETRY_DELAY_MS
                    // Keep pending messages queued and retry while this account's sync job is active.
                    while (currentCoroutineContext().isActive) {
                        try {
                            val batch = db.batch()
                            ownedMessages.forEach { message ->
                                batch.set(
                                    messageCollection(channelId, isDirectMessage).document(message.id),
                                    message.copy(isOutgoing = false, isSynced = true)
                                )
                            }
                            batch.commit().await()
                            dao.markMessagesSynced(ids)
                            break
                        } catch (error: Exception) {
                            // A cancelled collector exits on its next delay; ordinary network
                            // failures back off so offline messages are eventually retried.
                            delay(retryDelayMs)
                            retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
                        }
                    }
                } finally {
                    uploadingMessageIds.removeAll(ids.toSet())
                }
            }
            .launchIn(scope)
    }

    private fun ensureMessagePullListener(channelId: String, userId: String, isDirectMessage: Boolean) {
        val listenerKey = (if (isDirectMessage) "dm:" else "room:") + channelId
        if (messagePullListeners.containsKey(listenerKey)) return

        val registration = messageCollection(channelId, isDirectMessage)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val remoteMessages = snapshot.documents
                    .mapNotNull { it.toObject(MessageEntity::class.java) }
                    .map { it.copy(isOutgoing = it.senderId == userId, isSynced = true) }
                if (remoteMessages.isEmpty()) return@addSnapshotListener
                scope.launch { dao.upsertMessages(remoteMessages) }
            }
        messagePullListeners[listenerKey] = registration
    }

    /** Remove subscriptions on sign-out so a previous account cannot keep syncing. */
    fun stop() {
        channelListPushJob?.cancel()
        channelListPushJob = null
        channelsPullListener?.remove()
        channelsPullListener = null
        conversationsPullListener?.remove()
        conversationsPullListener = null
        messagePushJobs.values.forEach { it.cancel() }
        messagePushJobs.clear()
        messagePullListeners.values.forEach { it.remove() }
        messagePullListeners.clear()
        remoteChannels.clear()
        remoteChannelListInitialized.set(false)
        remoteConversations.clear()
        uploadingMessageIds.clear()
    }

    private companion object {
        const val MAX_MESSAGES_PUSHED_PER_SYNC = 500
        const val INITIAL_RETRY_DELAY_MS = 1_000L
        const val MAX_RETRY_DELAY_MS = 30_000L
    }
}
