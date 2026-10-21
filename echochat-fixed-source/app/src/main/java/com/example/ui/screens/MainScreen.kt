package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ChannelEntity
import com.example.data.model.MessageEntity
import com.example.data.model.PostEntity
import com.example.ui.EchoChatViewModel
import com.example.ui.components.ChannelListItem
import com.example.ui.components.CreateChannelDialog
import com.example.ui.components.CommentsDialog
import com.example.ui.components.MessageBubble
import com.example.ui.components.MessageInputBar
import com.example.ui.components.ProfileAndSettingsSheet
import com.example.ui.components.StartDirectMessageDialog
import com.example.ui.components.TypingIndicator
import com.example.util.AppLanguage
import com.example.util.EchoStrings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: EchoChatViewModel,
    modifier: Modifier = Modifier
) {
    val channels by viewModel.filteredChannels.collectAsStateWithLifecycle()
    val allChannels by viewModel.channels.collectAsStateWithLifecycle()
    val searchedMessages by viewModel.searchedMessages.collectAsStateWithLifecycle()
    val activeChannel by viewModel.activeChannel.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val selectedChannelId by viewModel.selectedChannelId.collectAsStateWithLifecycle()
    val currentLanguage by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val isSearchActive by viewModel.isSearchActive.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val activeChatCategoryTab by viewModel.activeChatCategoryTab.collectAsStateWithLifecycle()
    val replyingTo by viewModel.replyingTo.collectAsStateWithLifecycle()
    val typingUser by viewModel.typingUser.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val accountState by viewModel.accountState.collectAsStateWithLifecycle()
    val localContext = androidx.compose.ui.platform.LocalContext.current

    // Social & Explore state
    val currentMainTab by viewModel.currentMainTab.collectAsStateWithLifecycle()
    val stories by viewModel.stories.collectAsStateWithLifecycle()
    val filteredPosts by viewModel.filteredPosts.collectAsStateWithLifecycle()
    val savedPosts by viewModel.savedPosts.collectAsStateWithLifecycle()
    val selectedExploreCategory by viewModel.selectedExploreCategory.collectAsStateWithLifecycle()
    val allPosts by viewModel.posts.collectAsStateWithLifecycle()

    var showCreateChannelDialog by remember { mutableStateOf(false) }
    var showStartDmDialog by remember { mutableStateOf(false) }
    var showCreatePostDialog by remember { mutableStateOf(false) }
    var selectedPostForComments by remember { mutableStateOf<PostEntity?>(null) }
    var showMessageSearchResults by remember { mutableStateOf(false) }
    var showProfileSheet by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }

    // For compact screens, whether we are currently viewing the chat room
    var isViewingChatRoomOnCompact by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    // Handle system back on compact screen when inside a chat
    BackHandler(enabled = isViewingChatRoomOnCompact) {
        isViewingChatRoomOnCompact = false
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 760.dp
        // Scale the channel list rail with the available width instead of a fixed
        // size, so it looks right on a small tablet and on an ultra-wide desktop window.
        val channelListWidth = (maxWidth * 0.32f).coerceIn(280.dp, 380.dp)

        Scaffold(
            topBar = {
                EchoTopAppBar(
                    activeChannel = if ((isWideScreen || isViewingChatRoomOnCompact) && currentMainTab == 0) activeChannel else null,
                    currentLanguage = currentLanguage,
                    isDarkMode = isDarkMode,
                    isSearchActive = isSearchActive,
                    searchQuery = searchQuery,
                    userAvatarColor = userProfile?.avatarColor ?: 0xFF4F46E5,
                    userName = userProfile?.displayName ?: "Echo",
                    mainTab = currentMainTab,
                    onBackClick = { isViewingChatRoomOnCompact = false },
                    onToggleLanguage = { viewModel.toggleLanguage() },
                    onToggleTheme = { viewModel.toggleTheme() },
                    onSearchClick = { viewModel.toggleSearch(true) },
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onSearchSubmit = { if (searchQuery.isNotBlank()) showMessageSearchResults = true },
                    onCloseSearch = { viewModel.toggleSearch(false) },
                    onOpenProfile = { showProfileSheet = true },
                    onClearChat = { viewModel.clearChat() }
                )
            },
            bottomBar = {
                // Show bottom nav on compact screens when NOT currently inside an active chat conversation
                if (!isViewingChatRoomOnCompact && !isWideScreen) {
                    EchoBottomNavigationBar(
                        currentTab = currentMainTab,
                        language = currentLanguage,
                        onTabSelect = { tabIndex ->
                            viewModel.setMainTab(tabIndex)
                        }
                    )
                }
            },
            floatingActionButton = {
                if (!isViewingChatRoomOnCompact || isWideScreen) {
                    if (currentMainTab == 0) {
                        FloatingActionButton(
                            onClick = { showCreateChannelDialog = true },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White,
                            modifier = Modifier.testTag("create_channel_fab")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = EchoStrings.get("new_channel", currentLanguage)
                            )
                        }
                    } else if (currentMainTab == 1) {
                        FloatingActionButton(
                            onClick = { showCreatePostDialog = true },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White,
                            modifier = Modifier.testTag("create_post_fab")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = EchoStrings.get("create_post", currentLanguage)
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            if (isWideScreen) {
                // Wide Screen: Navigation Rail on the left + Content Area
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        NavigationRailItem(
                            selected = currentMainTab == 0,
                            onClick = { viewModel.setMainTab(0) },
                            icon = { Icon(Icons.Default.Forum, contentDescription = null) },
                            label = { Text(EchoStrings.get("nav_chats", currentLanguage)) }
                        )
                        NavigationRailItem(
                            selected = currentMainTab == 1,
                            onClick = { viewModel.setMainTab(1) },
                            icon = { Icon(Icons.Default.Explore, contentDescription = null) },
                            label = { Text(EchoStrings.get("nav_explore", currentLanguage)) }
                        )
                        NavigationRailItem(
                            selected = currentMainTab == 2,
                            onClick = { viewModel.setMainTab(2) },
                            icon = { Icon(Icons.Default.Bookmark, contentDescription = null) },
                            label = { Text(EchoStrings.get("nav_saved", currentLanguage)) }
                        )
                        NavigationRailItem(
                            selected = currentMainTab == 3,
                            onClick = { viewModel.setMainTab(3) },
                            icon = { Icon(Icons.Default.Person, contentDescription = null) },
                            label = { Text(EchoStrings.get("nav_profile", currentLanguage)) }
                        )
                    }

                    // Content for Selected Main Tab
                    when (currentMainTab) {
                        0 -> {
                            // Dual Panel Messenger
                            Row(modifier = Modifier.fillMaxSize()) {
                                Column(
                                    modifier = Modifier
                                        .width(channelListWidth)
                                        .fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.surface)
                                ) {
                                    CategoryTabs(
                                        activeTab = activeChatCategoryTab,
                                        language = currentLanguage,
                                        onTabSelected = { viewModel.setActiveChatCategoryTab(it) }
                                    )
                                    ChannelListContent(
                                        channels = channels,
                                        selectedChannelId = selectedChannelId,
                                        language = currentLanguage,
                                        onChannelSelected = { viewModel.selectChannel(it) },
                                        onNewDmClick = { showStartDmDialog = true }
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                )

                                Box(modifier = Modifier.weight(1f)) {
                                    ChatRoomContent(
                                        activeChannel = activeChannel,
                                        messages = messages,
                                        typingUser = typingUser,
                                        inputText = inputText,
                                        replyingTo = replyingTo,
                                        language = currentLanguage,
                                        onTextChanged = { inputText = it },
                                        onSendMessage = {
                                            viewModel.sendMessage(inputText)
                                            inputText = ""
                                        },
                                        onReply = { viewModel.setReplyingTo(it) },
                                        onCancelReply = { viewModel.setReplyingTo(null) },
                                        onDelete = { viewModel.deleteMessage(it) },
                                        onReact = { id, emoji -> viewModel.reactToMessage(id, emoji) }
                                    )
                                }
                            }
                        }
                        1 -> {
                            ExploreScreen(
                                stories = stories,
                                posts = filteredPosts,
                                selectedCategory = selectedExploreCategory,
                                language = currentLanguage,
                                onCategorySelected = { viewModel.setExploreCategory(it) },
                                onLikePost = { id, liked, count -> viewModel.toggleLikePost(id, liked, count) },
                                onBookmarkPost = { id, bookmarked -> viewModel.toggleBookmarkPost(id, bookmarked) },
                                onStoryClick = { viewModel.markStoryViewed(it) },
                                onCreatePostClick = { showCreatePostDialog = true },
                                onCreateStory = { viewModel.createStory(it) },
                                onCommentsClick = { selectedPostForComments = it; viewModel.refreshComments(it.id) }
                            )
                        }
                        2 -> {
                            SavedPostsScreen(
                                savedPosts = savedPosts,
                                language = currentLanguage,
                                onLikePost = { id, liked, count -> viewModel.toggleLikePost(id, liked, count) },
                                onBookmarkPost = { id, bookmarked -> viewModel.toggleBookmarkPost(id, bookmarked) },
                                onCommentsClick = { selectedPostForComments = it; viewModel.refreshComments(it.id) }
                            )
                        }
                        3 -> {
                            ProfileScreen(
                                profile = userProfile,
                                userPosts = allPosts,
                                language = currentLanguage,
                                isDarkMode = isDarkMode,
                                onEditProfileClick = { showProfileSheet = true },
                                onToggleLanguage = { viewModel.toggleLanguage() },
                                onToggleTheme = { viewModel.toggleTheme() },
                                onClearChat = { viewModel.clearChat() },
                                onLikePost = { id, liked, count -> viewModel.toggleLikePost(id, liked, count) },
                                onBookmarkPost = { id, bookmarked -> viewModel.toggleBookmarkPost(id, bookmarked) },
                                onCommentsClick = { selectedPostForComments = it; viewModel.refreshComments(it.id) }
                            )
                        }
                    }
                }
            } else {
                // Compact Screen (Mobile)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    when (currentMainTab) {
                        0 -> {
                            // Chat Tab: Toggle between List and Chat Room
                            AnimatedContent(
                                targetState = isViewingChatRoomOnCompact,
                                transitionSpec = {
                                    if (targetState) {
                                        (slideInHorizontally { width -> width } + fadeIn())
                                            .togetherWith(slideOutHorizontally { width -> -width } + fadeOut())
                                    } else {
                                        (slideInHorizontally { width -> -width } + fadeIn())
                                            .togetherWith(slideOutHorizontally { width -> width } + fadeOut())
                                    }
                                },
                                label = "chat_screen_transition"
                            ) { viewingChat ->
                                if (viewingChat) {
                                    ChatRoomContent(
                                        activeChannel = activeChannel,
                                        messages = messages,
                                        typingUser = typingUser,
                                        inputText = inputText,
                                        replyingTo = replyingTo,
                                        language = currentLanguage,
                                        onTextChanged = { inputText = it },
                                        onSendMessage = {
                                            viewModel.sendMessage(inputText)
                                            inputText = ""
                                        },
                                        onReply = { viewModel.setReplyingTo(it) },
                                        onCancelReply = { viewModel.setReplyingTo(null) },
                                        onDelete = { viewModel.deleteMessage(it) },
                                        onReact = { id, emoji -> viewModel.reactToMessage(id, emoji) }
                                    )
                                } else {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        CategoryTabs(
                                            activeTab = activeChatCategoryTab,
                                            language = currentLanguage,
                                            onTabSelected = { viewModel.setActiveChatCategoryTab(it) }
                                        )

                                        ChannelListContent(
                                            channels = channels,
                                            selectedChannelId = selectedChannelId,
                                            language = currentLanguage,
                                            onChannelSelected = { channelId ->
                                                viewModel.selectChannel(channelId)
                                                isViewingChatRoomOnCompact = true
                                            },
                                            onNewDmClick = { showStartDmDialog = true }
                                        )
                                    }
                                }
                            }
                        }
                        1 -> {
                            ExploreScreen(
                                stories = stories,
                                posts = filteredPosts,
                                selectedCategory = selectedExploreCategory,
                                language = currentLanguage,
                                onCategorySelected = { viewModel.setExploreCategory(it) },
                                onLikePost = { id, liked, count -> viewModel.toggleLikePost(id, liked, count) },
                                onBookmarkPost = { id, bookmarked -> viewModel.toggleBookmarkPost(id, bookmarked) },
                                onStoryClick = { viewModel.markStoryViewed(it) },
                                onCreatePostClick = { showCreatePostDialog = true },
                                onCreateStory = { viewModel.createStory(it) },
                                onCommentsClick = { selectedPostForComments = it; viewModel.refreshComments(it.id) }
                            )
                        }
                        2 -> {
                            SavedPostsScreen(
                                savedPosts = savedPosts,
                                language = currentLanguage,
                                onLikePost = { id, liked, count -> viewModel.toggleLikePost(id, liked, count) },
                                onBookmarkPost = { id, bookmarked -> viewModel.toggleBookmarkPost(id, bookmarked) },
                                onCommentsClick = { selectedPostForComments = it; viewModel.refreshComments(it.id) }
                            )
                        }
                        3 -> {
                            ProfileScreen(
                                profile = userProfile,
                                userPosts = allPosts,
                                language = currentLanguage,
                                isDarkMode = isDarkMode,
                                onEditProfileClick = { showProfileSheet = true },
                                onToggleLanguage = { viewModel.toggleLanguage() },
                                onToggleTheme = { viewModel.toggleTheme() },
                                onClearChat = { viewModel.clearChat() },
                                onLikePost = { id, liked, count -> viewModel.toggleLikePost(id, liked, count) },
                                onBookmarkPost = { id, bookmarked -> viewModel.toggleBookmarkPost(id, bookmarked) },
                                onCommentsClick = { selectedPostForComments = it; viewModel.refreshComments(it.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialogs & Sheets
    if (showCreateChannelDialog) {
        CreateChannelDialog(
            language = currentLanguage,
            onDismiss = { showCreateChannelDialog = false },
            onCreate = { name, desc, icon, onComplete ->
                viewModel.createChannel(name, desc, icon, onComplete)
            }
        )
    }

    if (showStartDmDialog) {
        StartDirectMessageDialog(
            language = currentLanguage,
            onDismiss = { showStartDmDialog = false },
            onStartChat = { username, onComplete ->
                viewModel.startDirectMessage(username) { error ->
                    if (error == null) isViewingChatRoomOnCompact = true
                    onComplete(error)
                }
            }
        )
    }

    if (showCreatePostDialog) {
        CreatePostDialog(
            language = currentLanguage,
            onDismiss = { showCreatePostDialog = false },
            onPublish = { content, tags, category, onComplete ->
                viewModel.createPost(content, tags, category, onComplete)
            }
        )
    }

    if (showProfileSheet) {
        ProfileAndSettingsSheet(
            profile = userProfile,
            currentLanguage = currentLanguage,
            isDarkMode = isDarkMode,
            onDismiss = { showProfileSheet = false },
            onSaveProfile = { name, bio, color ->
                viewModel.updateProfile(name, bio, color)
            },
            onToggleLanguage = { viewModel.toggleLanguage() },
            onToggleTheme = { viewModel.toggleTheme() },
            sheetState = sheetState,
            accountState = accountState,
            onSignOut = { viewModel.signOut() }
        )
    }

    if (showMessageSearchResults) {
        AlertDialog(
            onDismissRequest = { showMessageSearchResults = false },
            title = { Text(EchoStrings.get("message_search_results_title", currentLanguage)) },
            text = {
                if (searchedMessages.isEmpty()) {
                    Text(
                        EchoStrings.get("no_message_results", currentLanguage),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(searchedMessages, key = { it.id }) { message ->
                            val channelName = allChannels.firstOrNull { it.id == message.channelId }?.name
                                ?: message.channelId
                            TextButton(
                                onClick = {
                                    viewModel.selectChannel(message.channelId)
                                    viewModel.setMainTab(0)
                                    viewModel.toggleSearch(false)
                                    showMessageSearchResults = false
                                    isViewingChatRoomOnCompact = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        channelName,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(message.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        message.senderName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMessageSearchResults = false }) {
                    Text(EchoStrings.get("close", currentLanguage))
                }
            }
        )
    }

    selectedPostForComments?.let { post ->
        CommentsDialog(
            post = post,
            viewModel = viewModel,
            language = currentLanguage,
            onDismiss = { selectedPostForComments = null }
        )
    }
}

@Composable
fun EchoBottomNavigationBar(
    currentTab: Int,
    language: AppLanguage,
    onTabSelect: (Int) -> Unit
) {
    NavigationBar(
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        NavigationBarItem(
            selected = currentTab == 0,
            onClick = { onTabSelect(0) },
            icon = { Icon(Icons.Default.Forum, contentDescription = EchoStrings.get("nav_chats", language)) },
            label = { Text(EchoStrings.get("nav_chats", language), fontSize = 11.sp) },
            modifier = Modifier.testTag("nav_chats_tab")
        )
        NavigationBarItem(
            selected = currentTab == 1,
            onClick = { onTabSelect(1) },
            icon = { Icon(Icons.Default.Explore, contentDescription = EchoStrings.get("nav_explore", language)) },
            label = { Text(EchoStrings.get("nav_explore", language), fontSize = 11.sp) },
            modifier = Modifier.testTag("nav_explore_tab")
        )
        NavigationBarItem(
            selected = currentTab == 2,
            onClick = { onTabSelect(2) },
            icon = { Icon(Icons.Default.Bookmark, contentDescription = EchoStrings.get("nav_saved", language)) },
            label = { Text(EchoStrings.get("nav_saved", language), fontSize = 11.sp) },
            modifier = Modifier.testTag("nav_saved_tab")
        )
        NavigationBarItem(
            selected = currentTab == 3,
            onClick = { onTabSelect(3) },
            icon = { Icon(Icons.Default.Person, contentDescription = EchoStrings.get("nav_profile", language)) },
            label = { Text(EchoStrings.get("nav_profile", language), fontSize = 11.sp) },
            modifier = Modifier.testTag("nav_profile_tab")
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EchoTopAppBar(
    activeChannel: ChannelEntity?,
    currentLanguage: AppLanguage,
    isDarkMode: Boolean?,
    isSearchActive: Boolean,
    searchQuery: String,
    userAvatarColor: Long,
    userName: String,
    mainTab: Int,
    onBackClick: () -> Unit,
    onToggleLanguage: () -> Unit,
    onToggleTheme: () -> Unit,
    onSearchClick: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onCloseSearch: () -> Unit,
    onOpenProfile: () -> Unit,
    onClearChat: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val userAvatarDescription = EchoStrings.get("user_avatar", currentLanguage)

    val tabTitle = when (mainTab) {
        1 -> EchoStrings.get("explore_title", currentLanguage)
        2 -> EchoStrings.get("saved_posts_title", currentLanguage)
        3 -> EchoStrings.get("profile_title", currentLanguage)
        else -> EchoStrings.get("app_title", currentLanguage)
    }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        ),
        navigationIcon = {
            if (activeChannel != null) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = EchoStrings.get("back", currentLanguage)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .padding(start = 12.dp, end = 4.dp)
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (mainTab == 1) Icons.Default.Explore else Icons.Default.Forum,
                        contentDescription = "Echo Logo",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        title = {
            if (isSearchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text(EchoStrings.get("search_hint", currentLanguage), fontSize = 13.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_text_input"),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onSearchSubmit) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = EchoStrings.get("search_messages", currentLanguage)
                                )
                            }
                            IconButton(onClick = onCloseSearch) {
                                Icon(Icons.Default.Close, contentDescription = EchoStrings.get("close", currentLanguage))
                            }
                        }
                    }
                )
            } else if (activeChannel != null) {
                Column {
                    Text(
                        text = activeChannel.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = activeChannel.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (activeChannel.isDirectMessage) Color(0xFF10B981)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Column {
                    Text(
                        text = tabTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = EchoStrings.get("app_subtitle", currentLanguage),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        actions = {
            if (!isSearchActive) {
                IconButton(onClick = onSearchClick) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = EchoStrings.get("search_hint", currentLanguage)
                    )
                }

                // Fast Language Toggle Pill (FA | EN)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onToggleLanguage() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("language_toggle_pill")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Language",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (currentLanguage == AppLanguage.PERSIAN) "FA" else "EN",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Theme Toggle
                IconButton(onClick = onToggleTheme) {
                    Icon(
                        imageVector = if (isDarkMode == true) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = EchoStrings.get("theme_toggle", currentLanguage)
                    )
                }

                // User Avatar
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(userAvatarColor))
                        .clickable { onOpenProfile() }
                        .testTag("user_profile_avatar")
                        .semantics { contentDescription = userAvatarDescription },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = userName.take(1).ifEmpty { "U" },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                // Overflow Menu
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = EchoStrings.get("more_options", currentLanguage))
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(EchoStrings.get("profile_title", currentLanguage)) },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onOpenProfile()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(EchoStrings.get("clear_chat", currentLanguage)) },
                            leadingIcon = { Icon(Icons.Default.ClearAll, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onClearChat()
                            }
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun CategoryTabs(
    activeTab: Int,
    language: AppLanguage,
    onTabSelected: (Int) -> Unit
) {
    val tabs = listOf(
        EchoStrings.get("tab_chats", language),
        EchoStrings.get("tab_channels", language),
        EchoStrings.get("tab_contacts", language)
    )

    ScrollableTabRow(
        selectedTabIndex = activeTab,
        edgePadding = 12.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = activeTab == index,
                onClick = { onTabSelected(index) },
                text = {
                    Text(
                        text = title,
                        fontWeight = if (activeTab == index) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                }
            )
        }
    }
}

@Composable
fun ChannelListContent(
    channels: List<ChannelEntity>,
    selectedChannelId: String,
    language: AppLanguage,
    onChannelSelected: (String) -> Unit,
    onNewDmClick: () -> Unit
) {
    if (channels.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = EchoStrings.get("no_channels_found", language),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp)
        ) {
            items(channels, key = { it.id }) { channel ->
                ChannelListItem(
                    channel = channel,
                    isSelected = channel.id == selectedChannelId,
                    onClick = { onChannelSelected(channel.id) },
                    language = language
                )
            }
        }
    }
}

@Composable
fun ChatRoomContent(
    activeChannel: ChannelEntity?,
    messages: List<MessageEntity>,
    typingUser: String?,
    inputText: String,
    replyingTo: MessageEntity?,
    language: AppLanguage,
    onTextChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onReply: (MessageEntity) -> Unit,
    onCancelReply: () -> Unit,
    onDelete: (String) -> Unit,
    onReact: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Channel Info Header Bar
        if (activeChannel != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outline)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (activeChannel.isDirectMessage) activeChannel.name else activeChannel.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Messages List
        Box(modifier = Modifier.weight(1f)) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = EchoStrings.get("no_messages_yet", language),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            language = language,
                            onReply = onReply,
                            onDelete = onDelete,
                            onReact = onReact
                        )
                    }
                }
            }
        }

        // Live Typing indicator
        AnimatedVisibility(
            visible = !typingUser.isNullOrBlank(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (!typingUser.isNullOrBlank()) {
                TypingIndicator(typingUser = typingUser, language = language)
            }
        }

        // Message input bar
        MessageInputBar(
            inputText = inputText,
            onTextChanged = onTextChanged,
            onSend = onSendMessage,
            replyingTo = replyingTo,
            onCancelReply = onCancelReply,
            language = language
        )
    }
}
