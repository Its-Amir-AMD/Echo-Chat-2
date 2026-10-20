package com.example.data.remote

import com.example.data.local.EchoChatDao
import com.example.data.model.ChannelEntity
import com.example.data.model.MessageEntity
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Bridges the local Room database with a Firestore-backed cloud account so the same
 * conversation history can follow a signed-in user across devices.
 *
 * Design: Room stays the single source of truth for the UI (every screen already reads
 * from DAO Flows, which is what keeps the app instant and usable offline). This manager
 * only adds two one-way bridges on top of that, so nothing in the ViewModel, Repository,
 * or Compose layer has to change to benefit from sync:
 *
 *  - push: one long-lived collector per channel mirrors that channel's local message list
 *    up to Firestore every time it changes locally. The channel list itself is pushed the
 *    same way. Each channel gets exactly one collector for the lifetime of a sync session,
 *    tracked in [messagePushJobs], so repeated channel-list updates never pile up listeners.
 *  - pull: real-time Firestore listeners write remote documents into Room as they arrive,
 *    so other devices' activity shows up through the exact same DAO Flows the UI already
 *    reads from.
 *
 * This class is deliberately inert until [start] is called with a real, non-blank
 * [userId] from [FirebaseAuthManager]. In local-only mode (no sign-in, or no
 * google-services.json configured) it is simply never started and the app behaves
 * exactly as it did before — this is additive, not a replacement for local storage.
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

    /** Begin two-way sync for [userId]. Safe to call again after [stop]. */
    fun start(userId: String) {
        require(userId.isNotBlank()) { "A signed-in user id is required to start sync." }
        stop()

        val userDoc = db.collection("users").document(userId)

        // --- Push: local channel list -> Firestore. Also ensures a message pusher and a
        // message pull-listener exist for every channel we currently know about locally. ---
        channelListPushJob = dao.getAllChannels()
            .catch { /* Local data is still fully usable without sync; swallow and keep local-only. */ }
            .onEach { channels ->
                channels.forEach { channel ->
                    pushChannelDocument(userDoc, channel)
                    ensureMessagePusher(userDoc, channel.id)
                    ensureMessagePullListener(userDoc, channel.id)
                }
            }
            .launchIn(scope)

        // --- Pull: remote channel list -> Room ---
        channelsPullListener = userDoc.collection("channels").addSnapshotListener { snapshot, _ ->
            val remoteChannels = snapshot?.documents.orEmpty().mapNotNull { it.toObject(ChannelEntity::class.java) }
            if (remoteChannels.isEmpty()) return@addSnapshotListener
            scope.launch {
                remoteChannels.forEach { channel ->
                    dao.insertChannel(channel)
                    ensureMessagePusher(userDoc, channel.id)
                    ensureMessagePullListener(userDoc, channel.id)
                }
            }
        }
    }

    private fun pushChannelDocument(userDoc: DocumentReference, channel: ChannelEntity) {
        scope.launch {
            runCatching { userDoc.collection("channels").document(channel.id).set(channel).await() }
        }
    }

    /** Starts exactly one long-lived push collector per channel, the first time it is seen. */
    private fun ensureMessagePusher(userDoc: DocumentReference, channelId: String) {
        if (messagePushJobs.containsKey(channelId)) return
        messagePushJobs[channelId] = dao.getMessagesForChannel(channelId)
            .catch { /* Keep syncing other channels even if one fails. */ }
            .onEach { messages ->
                val batch = db.batch()
                messages.takeLast(MAX_MESSAGES_PUSHED_PER_SYNC).forEach { message ->
                    val ref = userDoc.collection("channels").document(channelId)
                        .collection("messages").document(message.id)
                    batch.set(ref, message)
                }
                runCatching { batch.commit().await() }
            }
            .launchIn(scope)
    }

    /** Starts exactly one Firestore listener per channel, the first time it is seen. */
    private fun ensureMessagePullListener(userDoc: DocumentReference, channelId: String) {
        if (messagePullListeners.containsKey(channelId)) return
        val registration = userDoc.collection("channels").document(channelId)
            .collection("messages")
            .addSnapshotListener { snapshot, _ ->
                val remoteMessages = snapshot?.documents.orEmpty().mapNotNull { it.toObject(MessageEntity::class.java) }
                if (remoteMessages.isEmpty()) return@addSnapshotListener
                // insertMessages uses OnConflictStrategy.IGNORE, so a message already present
                // locally with the same id keeps its local copy. This correctly brings in new
                // messages from other devices; a later edit to reaction/status made remotely
                // after the message already synced once will not overwrite the local row.
                scope.launch { dao.insertMessages(remoteMessages) }
            }
        messagePullListeners[channelId] = registration
    }

    /** Stop all active listeners and pushers, e.g. on sign-out. */
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
