package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PostEntity
import com.example.data.model.StoryEntity
import com.example.util.AppLanguage
import com.example.util.EchoStrings
import com.example.util.shareText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExploreScreen(
    stories: List<StoryEntity>,
    posts: List<PostEntity>,
    selectedCategory: String,
    language: AppLanguage,
    onCategorySelected: (String) -> Unit,
    onLikePost: (String, Boolean, Int) -> Unit,
    onBookmarkPost: (String, Boolean) -> Unit,
    onStoryClick: (String) -> Unit,
    onCreatePostClick: () -> Unit,
    onCreateStory: (String) -> Unit = {},
    onCommentsClick: (PostEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showCreateStoryDialog by remember { mutableStateOf(false) }
    var selectedStory by remember { mutableStateOf<StoryEntity?>(null) }

    val categories = listOf(
        "all" to EchoStrings.get("filter_all", language),
        "trending" to EchoStrings.get("filter_trending", language),
        "tech" to EchoStrings.get("filter_tech", language),
        "design" to EchoStrings.get("filter_design", language),
        "ai" to EchoStrings.get("filter_ai", language)
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Stories Section ---
        item {
            Column(modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) {
                Text(
                    text = EchoStrings.get("stories_title", language),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    // Add your story button
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                showCreateStoryDialog = true
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Story",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = EchoStrings.get("my_story", language),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Existing stories
                    items(stories, key = { it.id }) { story ->
                        val storyBorderBrush = if (!story.isViewed) {
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF6366F1), Color(0xFFEC4899), Color(0xFFF59E0B))
                            )
                        } else {
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF64748B), Color(0xFF475569))
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    onStoryClick(story.id)
                                    selectedStory = story
                                }
                                .testTag("story_item_${story.id}")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(CircleShape)
                                    .border(2.5.dp, storyBorderBrush, CircleShape)
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(Color(story.authorAvatarColor)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = story.authorName.take(1),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = story.authorName,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // --- Category Filters ---
        item {
            ScrollableTabRow(
                selectedTabIndex = categories.indexOfFirst { it.first == selectedCategory }.coerceAtLeast(0),
                edgePadding = 16.dp,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                categories.forEach { (key, label) ->
                    val isSelected = selectedCategory == key
                    Tab(
                        selected = isSelected,
                        onClick = { onCategorySelected(key) },
                        text = {
                            Text(
                                text = label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // --- Posts Feed ---
        if (posts.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = EchoStrings.get("no_posts_yet", language),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            items(posts, key = { it.id }) { post ->
                PostCard(
                    post = post,
                    language = language,
                    onLike = { onLikePost(post.id, post.isLiked, post.likesCount) },
                    onBookmark = { onBookmarkPost(post.id, post.isBookmarked) },
                    onShare = {
                        shareText(context, post.content, EchoStrings.get("share", language))
                    },
                    onCommentClick = { onCommentsClick(post) }
                )
            }
        }
    }

    if (showCreateStoryDialog) {
        CreateStoryDialog(
            language = language,
            onDismiss = { showCreateStoryDialog = false },
            onPublish = { text ->
                onCreateStory(text)
                showCreateStoryDialog = false
            }
        )
    }

    selectedStory?.let { story ->
        AlertDialog(
            onDismissRequest = { selectedStory = null },
            title = { Text("${story.authorName} · ${EchoStrings.get("view_story", language)}") },
            text = { Text(story.storyTitle, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = { selectedStory = null }) {
                    Text(EchoStrings.get("cancel", language))
                }
            }
        )
    }
}

@Composable
private fun CreateStoryDialog(
    language: AppLanguage,
    onDismiss: () -> Unit,
    onPublish: (String) -> Unit
) {
    var content by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(EchoStrings.get("new_story_title", language)) },
        text = {
            OutlinedTextField(
                value = content,
                onValueChange = { if (it.length <= 500) content = it },
                placeholder = { Text(EchoStrings.get("story_content_hint", language)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6
            )
        },
        confirmButton = {
            Button(
                onClick = { if (content.isNotBlank()) onPublish(content.trim()) },
                enabled = content.isNotBlank()
            ) {
                Text(EchoStrings.get("publish_story", language))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(EchoStrings.get("cancel", language)) }
        }
    )
}

@Composable
fun PostCard(
    post: PostEntity,
    language: AppLanguage,
    onLike: () -> Unit,
    onBookmark: () -> Unit,
    onShare: () -> Unit,
    onCommentClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val timeFormatted = remember(post.timestamp) {
        SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(post.timestamp))
    }

    val likeColor by animateColorAsState(
        targetValue = if (post.isLiked) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "likeColor"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("post_card_${post.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Author row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(post.authorAvatarColor)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = post.authorName.take(1),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = post.authorName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = post.authorRole,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = timeFormatted,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Post content
            Text(
                text = post.content,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Tags row
            if (post.tags.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    post.tags.split(",").forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "#${tag.trim()}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom social action bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Like Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onLike() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("like_post_button_${post.id}")
                ) {
                    Icon(
                        imageVector = if (post.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        tint = likeColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = post.likesCount.toString(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = likeColor
                    )
                }

                // Comment Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onCommentClick() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = "Comments",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = post.commentsCount.toString(),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Share Button
                IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Bookmark Button
                IconButton(
                    onClick = onBookmark,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("bookmark_post_button_${post.id}")
                ) {
                    Icon(
                        imageVector = if (post.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Bookmark",
                        tint = if (post.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CreatePostDialog(
    language: AppLanguage,
    onDismiss: () -> Unit,
    onPublish: (content: String, tags: String, category: String) -> Unit
) {
    var content by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("tech") }

    val categories = listOf(
        "tech" to EchoStrings.get("filter_tech", language),
        "design" to EchoStrings.get("filter_design", language),
        "ai" to EchoStrings.get("filter_ai", language)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = EchoStrings.get("create_post_title", language),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    placeholder = { Text(EchoStrings.get("post_content_hint", language), fontSize = 13.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .testTag("post_content_input"),
                    maxLines = 6
                )

                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    placeholder = { Text(EchoStrings.get("post_tags_hint", language), fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text(
                    text = EchoStrings.get("post_category_label", language),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { (catKey, catLabel) ->
                        val isSelected = selectedCategory == catKey
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedCategory = catKey }
                        ) {
                            Text(
                                text = catLabel,
                                fontSize = 12.sp,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (content.isNotBlank()) {
                        onPublish(content.trim(), tags.trim(), selectedCategory)
                    }
                },
                enabled = content.isNotBlank(),
                modifier = Modifier.testTag("publish_post_button")
            ) {
                Text(EchoStrings.get("publish", language))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(EchoStrings.get("cancel", language))
            }
        }
    )
}
