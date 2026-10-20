package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.EchoDatabase
import com.example.data.model.CommentEntity
import com.example.data.model.PostEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EchoChatDaoTest {

    private fun createDatabase(): EchoDatabase =
        Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            EchoDatabase::class.java
        ).allowMainThreadQueries().build()

    @Test
    fun togglingLikeUpdatesPersistedStateAndCount() = runBlocking {
        val database = createDatabase()
        try {
            val dao = database.chatDao()
            dao.insertPost(
                PostEntity(
                    id = "post-like-test",
                    authorId = "me",
                    authorName = "Test user",
                    content = "Test post"
                )
            )

            dao.togglePostLike("post-like-test")
            val liked = requireNotNull(dao.getPostById("post-like-test"))
            assertTrue(liked.isLiked)
            assertEquals(1, liked.likesCount)

            dao.togglePostLike("post-like-test")
            val unliked = requireNotNull(dao.getPostById("post-like-test"))
            assertFalse(unliked.isLiked)
            assertEquals(0, unliked.likesCount)
        } finally {
            database.close()
        }
    }

    @Test
    fun creatingCommentPersistsItAndIncrementsPostCount() = runBlocking {
        val database = createDatabase()
        try {
            val dao = database.chatDao()
            dao.insertPost(
                PostEntity(
                    id = "post-comment-test",
                    authorId = "me",
                    authorName = "Test user",
                    content = "Post with comments"
                )
            )

            dao.insertCommentAndIncrementCount(
                CommentEntity(
                    id = "comment-test",
                    postId = "post-comment-test",
                    authorId = "me",
                    authorName = "Test user",
                    content = "Persisted comment"
                )
            )

            val post = requireNotNull(dao.getPostById("post-comment-test"))
            assertEquals(1, post.commentsCount)
            assertEquals(1, dao.getCommentsForPost("post-comment-test").first().size)
        } finally {
            database.close()
        }
    }
}
