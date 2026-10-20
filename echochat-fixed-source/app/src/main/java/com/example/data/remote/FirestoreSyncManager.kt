package com.example.data.remote

import com.example.data.local.EchoChatDao
import com.example.data.model.ChannelEntity
import com.example.data.model.MessageEntity
import com.google.firebase.Firebase
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Room remains the offline cache while public channel metadata and messages are shared
 * through top-level Firestore collections. A message is only uploaded by the account
 * that authored it; incoming messages are merged into Room using REPLACE so reactions
 * and other remote edits do not get stuck behind an old local copy.
 *
 * Direct messages are intentionally excluded until membership-backed conversation
 * documents and their security rules are implemented. They must never be written to
 * a public collection.
 */
class FirestoreSyncManager(
    private val dao: EchoChatDao,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val db get() = Firebase.firestore

    private var channelListPushJob: Job? = null
    private var channelsPullListener: ListenerRegistration? = null
    private val messagePushJobs = mutableMapOf<String, Job>()
    private val messagePullListeners = mutableMapOf<String, ListenerRegistration>()

    /** Begin two-way sync for public channels. Safe to call after a previous session. */
    fun start(userId: String) {
        require(userId.isNotBlank()) { "A signed-in user id is required to start sync." }
        stop()

        channelListPushJob = dao.getAllChannels()
            .map { channels -> channels.filterNot { it.isDirectMessage } }
            .catch { /* Local cache stays available if the network is temporarily unavailable. */ }
            .onEach { channels ->
                channels.forEach { channel ->
                    pushChannelDocument(channel)
                    ensureMessagePusher(channel.id, userId)
                    ensureMessagePullListener(channel.id, userId)
                }
            }
            .launchIn(scope)

        channelsPullListener = db.collection("channels")
            .whereEqualTo("isDirectMessage", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val remoteChannels = snapshot.documents
                    .mapNotNull { it.toObject(ChannelEntity::class.java) }
                    .filterNot { it.isDirectMessage }
                if (remoteChannels.isEmpty()) return@addSnapshotListener
                scope.launch {
                    remoteChannels.forEach { channel -> dao.insertChannel(channel) }
                    // The Room Flow above starts message listeners for newly received channels.
                }
            }
    }

    private suspend fun pushChannelDocument(channel: ChannelEntity) {
        runCatching {
            db.collection("channels").document(channel.id).set(channel).await()
        }
    }

    /** Upload only messages authored by this account, never seeded or remote messages. */
    private fun ensureMessagePusher(channelId: String, userId: String) {
        if (messagePushJobs.containsKey(channelId)) return
        messagePushJobs[channelId] = dao.getMessagesForChannel(channelId)
            .catch { /* A single broken channel must not stop the other live listeners. */ }
            .onEach { messages ->
                val ownedMessages = messages
                    .filter { it.senderId == userId }
                    .takeLast(MAX_MESSAGES_PUSHED_PER_SYNC)
                if (ownedMessages.isEmpty()) return@onEach

                val batch = db.batch()
                ownedMessages.forEach { message ->
                    val ref = db.collection("channels").document(channelId)
                        .collection("messages").document(message.id)
                    // isOutgoing is a device-local presentation detail, not shared data.
                    batch.set(ref, message.copy(isOutgoing = false))
                }
                runCatching { batch.commit().await() }
            }
            .launchIn(scope)
    }

    /** Listen to shared messages and derive outgoing/incoming presentation per device. */
    private fun ensureMessagePullListener(channelId: String, userId: String) {
        if (messagePullListeners.containsKey(channelId)) return
        val registration = db.collection("channels").document(channelId)
            .collection("messages")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val remoteMessages = snapshot.documents
                    .mapNotNull { it.toObject(MessageEntity::class.java) }
                    .map { it.copy(isOutgoing = it.senderId == userId) }
                if (remoteMessages.isEmpty()) return@addSnapshotListener
                scope.launch { dao.upsertMessages(remoteMessages) }
            }
        messagePullListeners[channelId] = registration
    }

    /** Remove subscriptions on sign-out so one account cannot keep syncing afterward. */
    fun stop() {
        channelListPushJob?.cancel()
        channelListPushJob = null
        messagePushJobs.values.forEach { it.cancel() }
        messagePushJobs.clear()
        channelsPullListener?.remove()
        channelsPullListener = null
        messagePullListeners.values.forEach { it.remove() }
        messagePullListeners.clear()
    }

    private companion object {
        const val MAX_MESSAGES_PUSHED_PER_SYNC = 500
    }
}
