package com.example.data.model

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import androidx.room.Index

// Default values make entities safe to create for local/offline state. Room persists
// each column by name; remote data is mapped explicitly by the PHP API client.
@Entity(tableName = "channels", indices = [Index(value = ["lastMessageTime"])])
data class ChannelEntity(
    @PrimaryKey val id: String = "",
    val name: String = "",
    val description: String = "",
    val iconName: String = "forum",
    val isDirectMessage: Boolean = false,
    val peerName: String = "",
    @ColumnInfo(defaultValue = "''") val peerId: String = "",
    val peerAvatarColor: Long = 0xFF4F46E5,
    val unreadCount: Int = 0,
    val lastMessage: String = "",
    val lastMessageTime: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "messages", indices = [Index(value = ["channelId", "timestamp"])])
data class MessageEntity(
    @PrimaryKey val id: String = "",
    val channelId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderAvatarColor: Long = 0xFF4F46E5,
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isOutgoing: Boolean = false,
    val status: String = "read", // "sent", "delivered", "read"
    val replyToId: String? = null,
    val replyToSnippet: String? = null,
    val reaction: String? = null,
    // Queue state: true after the PHP API acknowledges this message.
    @ColumnInfo(defaultValue = "0") val isSynced: Boolean = false
)

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val userId: String = "me",
    val displayName: String = "کاربر اکو / Echo User",
    val bio: String = "علاقه‌مند به تکنولوژی، هوش مصنوعی و ارتباطات مدرن 🚀",
    val avatarColor: Long = 0xFF4F46E5,
    val languageCode: String = "fa",
    val isDarkMode: Boolean? = null,
    val postsCount: Int = 0,
    val followersCount: Int = 0,
    val followingCount: Int = 0
)

@Entity(tableName = "social_posts", indices = [Index(value = ["category", "timestamp"]), Index(value = ["authorId", "timestamp"])])
data class PostEntity(
    @PrimaryKey val id: String,
    val authorId: String,
    val authorName: String,
    val authorRole: String = "عضو اکو / Echo Member",
    val authorAvatarColor: Long = 0xFF4F46E5,
    val content: String,
    val tags: String = "", // comma-separated e.g. "کاتلین,اندروید"
    val category: String = "tech", // "trending", "tech", "design", "ai"
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val sharesCount: Int = 0,
    val isLiked: Boolean = false,
    val isBookmarked: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val cardColor: Long = 0xFF1E293B
)

@Entity(tableName = "post_comments", indices = [Index(value = ["postId", "timestamp"])])
data class CommentEntity(
    @PrimaryKey val id: String,
    val postId: String,
    val authorId: String,
    val authorName: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_stories", indices = [Index(value = ["timestamp"])])
data class StoryEntity(
    @PrimaryKey val id: String,
    val authorName: String,
    val authorAvatarColor: Long = 0xFF4F46E5,
    val storyTitle: String,
    val isViewed: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
\n