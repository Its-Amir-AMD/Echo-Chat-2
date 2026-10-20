package com.example.data.repository

import com.example.ai.EchoAiService
import com.example.data.local.EchoChatDao
import com.example.data.model.ChannelEntity
import com.example.data.model.CommentEntity
import com.example.data.model.MessageEntity
import com.example.data.model.PostEntity
import com.example.data.model.StoryEntity
import com.example.data.model.UserProfileEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class EchoChatRepository(
    private val dao: EchoChatDao,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    val allChannels: Flow<List<ChannelEntity>> = dao.getAllChannels()
    val userProfile: Flow<UserProfileEntity?> = dao.getUserProfile()
    val allPosts: Flow<List<PostEntity>> = dao.getAllPosts()
    val savedPosts: Flow<List<PostEntity>> = dao.getSavedPosts()
    val allStories: Flow<List<StoryEntity>> = dao.getAllStories()

    // Real-time typing indicators for active channels
    private val _typingStatus = MutableStateFlow<Map<String, String?>>(emptyMap())
    val typingStatus = _typingStatus.asStateFlow()

    fun getChannel(channelId: String): Flow<ChannelEntity?> = dao.getChannelById(channelId)

    fun getMessagesForChannel(channelId: String): Flow<List<MessageEntity>> =
        dao.getMessagesForChannel(channelId)

    fun searchMessages(query: String): Flow<List<MessageEntity>> = dao.searchMessages(query)

    suspend fun clearChannelUnread(channelId: String) = dao.clearChannelUnread(channelId)

    suspend fun sendMessage(
        channelId: String,
        content: String,
        replyToId: String? = null,
        replyToSnippet: String? = null
    ) {
        val now = System.currentTimeMillis()
        val userMsgId = UUID.randomUUID().toString()

        val outgoingMsg = MessageEntity(
            id = userMsgId,
            channelId = channelId,
            senderId = "me",
            senderName = "شما / You",
            senderAvatarColor = 0xFF4F46E5,
            content = content.trim(),
            timestamp = now,
            isOutgoing = true,
            status = "read",
            replyToId = replyToId,
            replyToSnippet = replyToSnippet
        )

        dao.insertMessage(outgoingMsg)
        dao.updateChannelLastMessage(channelId, content.trim(), now)

        // Only the assistant channel automatically responds. Human conversations must
        // never fabricate replies from other users.
        triggerAssistantReply(channelId, content.trim(), userMsgId)
    }

    private fun triggerAssistantReply(channelId: String, userText: String, replyToId: String) {
        if (channelId != "bot_echo") return

        scope.launch {
            val assistantName = "Echo AI"
            _typingStatus.value = _typingStatus.value + (channelId to assistantName)
            try {
                val replyText = EchoAiService().generateReply(userText)
                val now = System.currentTimeMillis()
                val reply = MessageEntity(
                    id = UUID.randomUUID().toString(),
                    channelId = channelId,
                    senderId = "bot_echo",
                    senderName = assistantName,
                    senderAvatarColor = 0xFF6366F1,
                    content = replyText,
                    timestamp = now,
                    isOutgoing = false,
                    status = "read",
                    replyToId = replyToId,
                    replyToSnippet = userText.take(40).let { if (userText.length > 40) "$it..." else it }
                )
                dao.insertMessage(reply)
                dao.updateChannelLastMessage(channelId, replyText, now)
            } catch (_: Exception) {
                // Do not represent a canned response as AI output.
                val now = System.currentTimeMillis()
                val explanation = "پاسخ هوش مصنوعی در دسترس نیست. تنظیمات Firebase AI و App Check را بررسی کنید. / AI unavailable: check Firebase AI and App Check configuration."
                dao.insertMessage(
                    MessageEntity(
                        id = UUID.randomUUID().toString(),
                        channelId = channelId,
                        senderId = "system",
                        senderName = "EchoChat",
                        senderAvatarColor = 0xFF64748B,
                        content = explanation,
                        timestamp = now,
                        isOutgoing = false,
                        status = "failed",
                        replyToId = replyToId,
                        replyToSnippet = userText.take(40)
                    )
                )
                dao.updateChannelLastMessage(channelId, explanation, now)
            } finally {
                _typingStatus.value = _typingStatus.value - channelId
            }
        }
    }

    suspend fun setReaction(messageId: String, reaction: String?) {
        dao.updateMessageReaction(messageId, reaction)
    }

    suspend fun deleteMessage(messageId: String) {
        dao.deleteMessageById(messageId)
    }

    suspend fun clearChat(channelId: String) {
        dao.deleteMessagesForChannel(channelId)
        dao.updateChannelLastMessage(channelId, "", System.currentTimeMillis())
    }

    suspend fun createChannel(name: String, description: String, iconName: String = "forum") {
        val id = "channel_" + UUID.randomUUID().toString().take(8)
        val newChannel = ChannelEntity(
            id = id,
            name = name,
            description = description,
            iconName = iconName,
            isDirectMessage = false,
            lastMessage = "",
            lastMessageTime = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis()
        )
        dao.insertChannel(newChannel)
    }

    suspend fun startDirectMessage(peerName: String, peerAvatarColor: Long = 0xFF8B5CF6) {
        val id = "dm_" + UUID.randomUUID().toString().take(8)
        val newDm = ChannelEntity(
            id = id,
            name = peerName,
            description = "گفتگوی دونفره",
            iconName = "person",
            isDirectMessage = true,
            peerName = peerName,
            peerAvatarColor = peerAvatarColor,
            lastMessage = "",
            lastMessageTime = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis()
        )
        dao.insertChannel(newDm)
    }

    // --- Social Posts Operations ---
    suspend fun createPost(
        content: String,
        tags: String = "",
        category: String = "tech"
    ) {
        require(content.isNotBlank()) { "Post content must not be empty." }
        val profile = dao.getUserProfileOnce()
        val newPost = PostEntity(
            id = "post_" + UUID.randomUUID().toString().take(8),
            authorId = "me",
            authorName = profile?.displayName?.takeIf { it.isNotBlank() } ?: "Echo User",
            authorRole = "عضو اکو / Echo Member",
            authorAvatarColor = profile?.avatarColor ?: 0xFF4F46E5,
            content = content.trim(),
            tags = tags.trim(),
            category = category,
            likesCount = 0,
            commentsCount = 0,
            sharesCount = 0,
            isLiked = false,
            isBookmarked = false,
            timestamp = System.currentTimeMillis(),
            cardColor = 0xFF1E293B
        )
        dao.insertPost(newPost)
    }

    suspend fun toggleLikePost(postId: String, currentLiked: Boolean, currentCount: Int) {
        // Use persisted state because UI values can be stale after rapid taps.
        dao.togglePostLike(postId)
    }

    fun getCommentsForPost(postId: String): kotlinx.coroutines.flow.Flow<List<CommentEntity>> =
        dao.getCommentsForPost(postId)

    suspend fun createComment(postId: String, content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        require(trimmed.length <= 2_000) { "Comment is too long." }
        val profile = dao.getUserProfileOnce()
        dao.insertCommentAndIncrementCount(
            CommentEntity(
                id = UUID.randomUUID().toString(),
                postId = postId,
                authorId = "me",
                authorName = profile?.displayName?.takeIf { it.isNotBlank() } ?: "Echo User",
                content = trimmed,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun createStory(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        require(trimmed.length <= 500) { "Story text is too long." }
        val profile = dao.getUserProfileOnce()
        dao.insertStory(
            StoryEntity(
                id = "story_" + UUID.randomUUID().toString().take(12),
                authorName = profile?.displayName?.takeIf { it.isNotBlank() } ?: "Echo User",
                authorAvatarColor = profile?.avatarColor ?: 0xFF4F46E5,
                storyTitle = trimmed,
                isViewed = true,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun toggleBookmarkPost(postId: String, currentBookmarked: Boolean) {
        dao.updatePostBookmark(postId, !currentBookmarked)
    }

    suspend fun deletePost(postId: String) {
        dao.deletePost(postId)
    }

    suspend fun markStoryViewed(storyId: String) {
        dao.markStoryViewed(storyId)
    }

    suspend fun updateProfile(
        displayName: String,
        bio: String,
        avatarColor: Long,
        languageCode: String,
        isDarkMode: Boolean?
    ) {
        val existing = dao.getUserProfileOnce() ?: UserProfileEntity()
        dao.insertOrUpdateProfile(
            existing.copy(
                displayName = displayName.trim().ifEmpty { existing.displayName },
                bio = bio.trim(),
                avatarColor = avatarColor,
                languageCode = languageCode,
                isDarkMode = isDarkMode
            )
        )
    }
}
