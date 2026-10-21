package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.ChannelEntity
import com.example.data.model.CommentEntity
import com.example.data.model.MessageEntity
import com.example.data.model.PostEntity
import com.example.data.model.StoryEntity
import com.example.data.model.UserProfileEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

@Database(
    entities = [
        ChannelEntity::class,
        MessageEntity::class,
        UserProfileEntity::class,
        PostEntity::class,
        StoryEntity::class,
        CommentEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class EchoDatabase : RoomDatabase() {
    abstract fun chatDao(): EchoChatDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS social_posts (
                        id TEXT NOT NULL, authorId TEXT NOT NULL, authorName TEXT NOT NULL,
                        authorRole TEXT NOT NULL, authorAvatarColor INTEGER NOT NULL,
                        content TEXT NOT NULL, tags TEXT NOT NULL, category TEXT NOT NULL,
                        likesCount INTEGER NOT NULL, commentsCount INTEGER NOT NULL,
                        sharesCount INTEGER NOT NULL, isLiked INTEGER NOT NULL,
                        isBookmarked INTEGER NOT NULL, timestamp INTEGER NOT NULL,
                        cardColor INTEGER NOT NULL, PRIMARY KEY(id)
                    )""".trimIndent()
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS user_stories (
                        id TEXT NOT NULL, authorName TEXT NOT NULL,
                        authorAvatarColor INTEGER NOT NULL, storyTitle TEXT NOT NULL,
                        isViewed INTEGER NOT NULL, timestamp INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )""".trimIndent()
                )
                // Version 2 introduced the profile counters. Preserve profile fields
                // while explicitly initializing legacy statistics to zero.
                db.execSQL(
                    """CREATE TABLE user_profile_new (
                        userId TEXT NOT NULL PRIMARY KEY,
                        displayName TEXT NOT NULL,
                        bio TEXT NOT NULL,
                        avatarColor INTEGER NOT NULL,
                        languageCode TEXT NOT NULL,
                        isDarkMode INTEGER,
                        postsCount INTEGER NOT NULL,
                        followersCount INTEGER NOT NULL,
                        followingCount INTEGER NOT NULL
                    )""".trimIndent()
                )
                db.execSQL(
                    """INSERT INTO user_profile_new (
                        userId, displayName, bio, avatarColor, languageCode, isDarkMode,
                        postsCount, followersCount, followingCount
                    )
                    SELECT userId, displayName, bio, avatarColor, languageCode, isDarkMode, 0, 0, 0
                    FROM user_profile""".trimIndent()
                )
                db.execSQL("DROP TABLE user_profile")
                db.execSQL("ALTER TABLE user_profile_new RENAME TO user_profile")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS post_comments (
                        id TEXT NOT NULL, postId TEXT NOT NULL, authorId TEXT NOT NULL,
                        authorName TEXT NOT NULL, content TEXT NOT NULL, timestamp INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )""".trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_post_comments_postId_timestamp ON post_comments(postId, timestamp)"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_channels_lastMessageTime ON channels(lastMessageTime)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_channelId_timestamp ON messages(channelId, timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_social_posts_category_timestamp ON social_posts(category, timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_social_posts_authorId_timestamp ON social_posts(authorId, timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_user_stories_timestamp ON user_stories(timestamp)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Remove only the fixed sample records shipped by previous demo builds.
                db.execSQL("DELETE FROM messages WHERE channelId = 'dm_sara'")
                db.execSQL("DELETE FROM messages WHERE senderId IN ('ali', 'reza')")
                db.execSQL("DELETE FROM messages WHERE senderId = 'system' AND channelId = 'general'")
                db.execSQL("DELETE FROM messages WHERE senderId = 'bot_echo' AND channelId = 'general'")
                db.execSQL("DELETE FROM messages WHERE channelId = 'bot_echo' AND content LIKE 'سلام! من دستیار هوشمند اکو هستم%'")
                db.execSQL("DELETE FROM post_comments WHERE postId IN ('post_1', 'post_2', 'post_3', 'post_4')")
                db.execSQL("DELETE FROM social_posts WHERE id IN ('post_1', 'post_2', 'post_3', 'post_4')")
                db.execSQL("DELETE FROM user_stories WHERE id IN ('story_1', 'story_2', 'story_3', 'story_4')")
                db.execSQL("DELETE FROM channels WHERE id = 'dm_sara'")
                db.execSQL("UPDATE channels SET lastMessage = '', lastMessageTime = 0 WHERE id IN ('general', 'tech', 'design', 'bot_echo')")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Track PHP API acknowledgement independently of message delivery/read receipts.
                db.execSQL("ALTER TABLE messages ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE channels ADD COLUMN peerId TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile
        private var INSTANCE: EchoDatabase? = null

        fun getInstance(context: Context): EchoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EchoDatabase::class.java,
                    "echochat_database"
                )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Populate default seed data
                            CoroutineScope(Dispatchers.IO).launch {
                                val dao = getInstance(context).chatDao()
                                seedInitialData(dao)
                            }
                        }
                    })
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun seedInitialData(dao: EchoChatDao) {
            val now = System.currentTimeMillis()

            // These are public room definitions, not fabricated users or messages.
            val publicChannels = listOf(
                ChannelEntity(
                    id = "general",
                    name = "گفتگوی عمومی / General",
                    description = "بحث و گفتگو پیرامون موضوعات مختلف",
                    iconName = "forum",
                    isDirectMessage = false,
                    lastMessage = "",
                    lastMessageTime = now,
                    createdAt = now
                ),
                ChannelEntity(
                    id = "tech",
                    name = "توسعه و فناوری / Tech Talk",
                    description = "برنامه‌نویسی اندروید، کاتلین و جت‌پک کامپوز",
                    iconName = "code",
                    isDirectMessage = false,
                    lastMessage = "",
                    lastMessageTime = now,
                    createdAt = now
                ),
                ChannelEntity(
                    id = "design",
                    name = "طراحی و رابط کاربری / UI & UX",
                    description = "اصول Material 3، رنگ‌ها و تجربه کاربری مدرن",
                    iconName = "palette",
                    isDirectMessage = false,
                    lastMessage = "",
                    lastMessageTime = now,
                    createdAt = now
                ),
                ChannelEntity(
                    id = "bot_echo",
                    name = "دستیار هوشمند اکو / Echo Bot",
                    description = "گفتگو با دستیار هوش مصنوعی",
                    iconName = "smart_toy",
                    isDirectMessage = true,
                    peerName = "Echo AI",
                    peerAvatarColor = 0xFF6366F1,
                    lastMessage = "",
                    lastMessageTime = now,
                    createdAt = now
                )
            )
            dao.insertChannels(publicChannels)

            // The feed and stories start empty; content must come from real users or the server.
            dao.insertOrUpdateProfile(
                UserProfileEntity(
                    userId = "me",
                    displayName = "کاربر اکو چت",
                    bio = "",
                    avatarColor = 0xFF4F46E5,
                    languageCode = "fa",
                    postsCount = 0,
                    followersCount = 0,
                    followingCount = 0
                )
            )
        }
    }
}
