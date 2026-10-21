package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
    onCreateStory: (String, (String?) -> Unit) -> Unit = { _, done -> done(null) },
    onCommentsClick: (PostEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showCreateStoryDialog by remember { mutableStateOf(false) }
    var selectedStory by remember { mutableStateOf<StoryEntity?>(null) }
    var exploreSearch by remember { mutableStateOf("") }

    val categories = listOf(
        "all" to EchoStrings.get("filter_all", language),
        "trending" to EchoStrings.get("filter_trending", language),
        "tech" to EchoStrings.get("filter_tech", language),
        "design" to EchoStrings.get("filter_design", language),
        "ai" to EchoStrings.get("filter_ai", language)
    )
    val visiblePosts = remember(posts, exploreSearch) {
        val q = exploreSearch.trim()
        if (q.isEmpty()) posts else posts.filter {
            it.content.contains(q, ignoreCase = true) ||
                it.authorName.contains(q, ignoreCase = true) ||
                it.tags.contains(q, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = EchoStrings.get("explore_title", language),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (language == AppLanguage.PERSIAN)
                            "گفتگوها، ایده‌ها و آدم‌های تازه را کشف کن"
                        else "Discover conversations, ideas and people",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onCreatePostClick,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("explore_create_post_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (language == AppLanguage.PERSIAN) "پست جدید" else "New post")
                }
            }
        }

        item {
            OutlinedTextField(
                value = exploreSearch,
                onValueChange = { exploreSearch = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("explore_search_input"),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                placeholder = {
                    Text(if (language == AppLanguage.PERSIAN) "جستجو در پست‌ها و موضوعات..." else "Search posts and topics...")
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
        }

        item {
            Column(modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(top = 18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = EchoStrings.get("stories_title", language),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (language == AppLanguage.PERSIAN) "لحظه‌های کوتاه جامعهٔ اکو" else "Quick moments from the Echo community",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(66.dp)
                                .clickable { showCreateStoryDialog = true }
                                .testTag("create_story_item")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = if (language == AppLanguage.PERSIAN) "استوری جدید" else "New story",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(25.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(7.dp))
                            Text(
                                text = EchoStrings.get("my_story", language),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    items(stories, key = { "story_" + it.id }) { story ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(66.dp)
                                .clickable {
                                    onStoryClick(story.id)
                                    selectedStory = story
                                }
                                .testTag("story_item_" + story.id)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = if (story.isViewed) 1.dp else 2.dp,
                                        color = if (story.isViewed) MaterialTheme.colorScheme.outline
                                        else MaterialTheme.colorScheme.primary,
                                        shape = CircleShape
                                    )
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(Color(story.authorAvatarColor)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = story.authorName.take(1).ifBlank { "E" },
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(7.dp))
                            Text(
                                text = story.authorName,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .padding(top = 2.dp, bottom = 8.dp)
            ) {
                Text(
                    text = if (language == AppLanguage.PERSIAN) "برای تو" else "For you",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories, key = { it.first }) { (key, label) ->
                        FilterChip(
                            selected = selectedCategory == key,
                            onClick = { onCategorySelected(key) },
                            label = { Text(label, maxLines = 1) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
        }

        if (visiblePosts.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 38.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Explore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (exploreSearch.isNotBlank()) {
                                if (language == AppLanguage.PERSIAN) "نتیجه‌ای پیدا نشد" else "No results found"
                            } else EchoStrings.get("no_posts_yet", language),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (language == AppLanguage.PERSIAN)
                                "اولین پست را منتشر کن و گفتگو را شروع کن."
                            else "Publish the first post and start a conversation.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = onCreatePostClick) {
                            Text(if (language == AppLanguage.PERSIAN) "ایجاد پست" else "Create a post")
                        }
                    }
                }
            }
        } else {
            items(visiblePosts, key = { "post_" + it.id }) { post ->
                PostCard(
                    post = post,
                    language = language,
                    onLike = { onLikePost(post.id, post.isLiked, post.likesCount) },
                    onBookmark = { onBookmarkPost(post.id, post.isBookmarked) },
                    onShare = { shareText(context, post.content, EchoStrings.get("share", language)) },
                    onCommentClick = { onCommentsClick(post) },
                    modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp)
                )
            }
        }
    }

    if (showCreateStoryDialog) {
        CreateStoryDialog(
            language = language,
            onDismiss = { showCreateStoryDialog = false },
            onPublish = { text, complete ->
                onCreateStory(text, complete)
            }
        )
    }

    selectedStory?.let { story ->
        AlertDialog(
            onDismissRequest = { selectedStory = null },
            title = {
                Text(
                    text = story.authorName + " · " + EchoStrings.get("view_story", language),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = story.storyTitle,
                        style = MaterialTheme.typography.titleLarge,
                        lineHeight = 30.sp,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedStory = null }) {
                    Text(EchoStrings.get("close", language))
                }
            }
        )
    }
}

@Composable
private fun CreateStoryDialog(
    language: AppLanguage,
    onDismiss: () -> Unit,
    onPublish: (String, (String?) -> Unit) -> Unit
) {
    var content by remember { mutableStateOf("") }
    var isPublishing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!isPublishing) onDismiss() },
        title = { Text(EchoStrings.get("new_story_title", language), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { if (it.length <= 500) content = it },
                    placeholder = { Text(EchoStrings.get("story_content_hint", language)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6
                )
                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (content.isNotBlank() && !isPublishing) {
                        isPublishing = true
                        errorMessage = null
                        onPublish(content.trim()) { error ->
                            isPublishing = false
                            if (error == null) onDismiss() else errorMessage = error
                        }
                    }
                },
                enabled = content.isNotBlank() && !isPublishing
            ) {
                Text(if (isPublishing) {
                    if (language == AppLanguage.PERSIAN) "در حال انتشار..." else "Publishing..."
                } else EchoStrings.get("publish_story", language))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isPublishing) { Text(EchoStrings.get("cancel", language)) }
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
    val timeFormatted = remember(post.timestamp, language) {
        SimpleDateFormat("MMM d · HH:mm", Locale.getDefault()).format(Date(post.timestamp))
    }
    val likeColor by animateColorAsState(
        targetValue = if (post.isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "likeColor"
    )
    val categoryLabel = when (post.category) {
        "design" -> EchoStrings.get("filter_design", language)
        "ai" -> EchoStrings.get("filter_ai", language)
        else -> EchoStrings.get("filter_tech", language)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("post_card_" + post.id),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(post.authorAvatarColor)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = post.authorName.take(1).ifBlank { "E" },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
                Spacer(modifier = Modifier.width(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = post.authorName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "@" + post.authorName.lowercase().replace(" ", "_"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = categoryLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = post.content,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 25.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (post.tags.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(post.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }.take(8)) { tag ->
                        Surface(
                            shape = RoundedCornerShape(9.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "#" + tag,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onLike() }
                        .heightIn(min = 44.dp)
                        .padding(horizontal = 8.dp)
                        .testTag("like_post_button_" + post.id),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (post.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (language == AppLanguage.PERSIAN) "پسندیدن" else "Like",
                        tint = likeColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = post.likesCount.toString(), color = likeColor, style = MaterialTheme.typography.labelLarge)
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onCommentClick() }
                        .heightIn(min = 44.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = if (language == AppLanguage.PERSIAN) "دیدگاه‌ها" else "Comments",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = post.commentsCount.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                }
                IconButton(onClick = onShare, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Share, contentDescription = EchoStrings.get("share", language), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(
                    onClick = onBookmark,
                    modifier = Modifier.size(44.dp).testTag("bookmark_post_button_" + post.id)
                ) {
                    Icon(
                        imageVector = if (post.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = EchoStrings.get("bookmark", language),
                        tint = if (post.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
    onPublish: (content: String, tags: String, category: String, onComplete: (String?) -> Unit) -> Unit
) {
    var content by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("tech") }
    var isPublishing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val categories = listOf(
        "tech" to EchoStrings.get("filter_tech", language),
        "design" to EchoStrings.get("filter_design", language),
        "ai" to EchoStrings.get("filter_ai", language)
    )

    AlertDialog(
        onDismissRequest = { if (!isPublishing) onDismiss() },
        title = { Text(EchoStrings.get("create_post_title", language), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { if (it.length <= 10000) content = it },
                    placeholder = { Text(EchoStrings.get("post_content_hint", language)) },
                    modifier = Modifier.fillMaxWidth().height(140.dp).testTag("post_content_input"),
                    maxLines = 7
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { if (it.length <= 500) tags = it },
                    placeholder = { Text(EchoStrings.get("post_tags_hint", language)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    text = EchoStrings.get("post_category_label", language),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    categories.forEach { (key, label) ->
                        FilterChip(
                            selected = selectedCategory == key,
                            onClick = { selectedCategory = key },
                            label = { Text(label, maxLines = 1) },
                            shape = RoundedCornerShape(11.dp)
                        )
                    }
                }
                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (content.isNotBlank() && !isPublishing) {
                        isPublishing = true
                        errorMessage = null
                        onPublish(content.trim(), tags.trim(), selectedCategory) { error ->
                            isPublishing = false
                            if (error == null) onDismiss() else errorMessage = error
                        }
                    }
                },
                enabled = content.isNotBlank() && !isPublishing,
                modifier = Modifier.testTag("publish_post_button")
            ) {
                Text(if (isPublishing) {
                    if (language == AppLanguage.PERSIAN) "در حال انتشار..." else "Publishing..."
                } else EchoStrings.get("publish", language))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isPublishing) { Text(EchoStrings.get("cancel", language)) }
        }
    )
}
