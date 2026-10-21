package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.EchoDatabase
import com.example.data.model.ChannelEntity
import com.example.data.model.CommentEntity
import com.example.data.model.MessageEntity
import com.example.data.model.PostEntity
import com.example.data.model.StoryEntity
import com.example.data.model.UserProfileEntity
import com.example.data.repository.EchoChatRepository
import com.example.util.AppLanguage
import com.example.util.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import com.example.data.local.EchoChatDao
import com.example.data.remote.ApiUser
import com.example.data.remote.PhpApiClient

@OptIn(ExperimentalCoroutinesApi::class)
class EchoChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: EchoChatRepository
    private val userPreferences = UserPreferences(application)

    private lateinit var dao: EchoChatDao
    private val api = PhpApiClient(application)
    private var remoteSyncJob: Job? = null

    private val _accountState = MutableStateFlow(AccountUiState())
    val accountState: StateFlow<AccountUiState> = _accountState.asStateFlow()

    val currentServerUrl: String
        get() = api.currentServerUrl

    init {
        val database = EchoDatabase.getInstance(application)
        dao = database.chatDao()
        repository = EchoChatRepository(dao, viewModelScope)

        if (!api.isConfigured) {
            _accountState.value = AccountUiState(isCloudAvailable = false)
        } else {
            _accountState.value = AccountUiState(isCloudAvailable = false, isBusy = true)
            viewModelScope.launch {
                try {
                    api.checkHealth()
                    if (api.hasToken) {
                        try {
                            acceptSignedInUser(api.currentUser())
                        } catch (error: Exception) {
                            _accountState.value = AccountUiState(
                                isCloudAvailable = true,
                                lastError = "نشست قبلی معتبر نیست یا قابل بررسی نیست؛ دوباره وارد شوید. / Please sign in again."
                            )
                        }
                    } else {
                        _accountState.value = AccountUiState(isCloudAvailable = true)
                    }
                } catch (error: Exception) {
                    _accountState.value = AccountUiState(
                        isCloudAvailable = false,
                        lastError = error.message ?: "ارتباط با سرور برقرار نشد. / Could not connect to the server."
                    )
                }
            }
        }
    }

    /** Save and verify a self-hosted PHP API URL before enabling sign-in. */
    fun configureServer(url: String) {
        val previousUrl = api.currentServerUrl
        try {
            api.configureBaseUrl(url)
        } catch (error: Exception) {
            _accountState.value = _accountState.value.copy(
                isBusy = false,
                lastError = error.message ?: "آدرس سرور معتبر نیست."
            )
            return
        }

        if (!previousUrl.equals(api.currentServerUrl, ignoreCase = true)) {
            remoteSyncJob?.cancel()
            api.clearLocalSession()
        }
        _accountState.value = AccountUiState(isCloudAvailable = false, isBusy = true)
        viewModelScope.launch {
            try {
                api.checkHealth()
                if (api.hasToken) {
                    val user = runCatching { api.currentUser() }.getOrNull()
                    if (user != null) {
                        acceptSignedInUser(user)
                        return@launch
                    }
                }
                _accountState.value = AccountUiState(
                    isCloudAvailable = true,
                    lastMessage = "اتصال به سرور برقرار شد. / Server connection established."
                )
            } catch (error: Exception) {
                _accountState.value = AccountUiState(
                    isCloudAvailable = false,
                    lastError = error.message ?: "سرور پاسخ نداد. / The server did not respond."
                )
            }
        }
    }

    fun signInWithEmail(email: String, password: String) {
        if (!api.isConfigured) {
            _accountState.value = _accountState.value.copy(lastError = "ابتدا آدرس سرور را تنظیم کنید.")
            return
        }
        _accountState.value = _accountState.value.copy(isBusy = true, lastError = null, lastMessage = null)
        viewModelScope.launch {
            try {
                acceptSignedInUser(api.login(email, password))
            } catch (error: Exception) {
                _accountState.value = _accountState.value.copy(
                    isBusy = false,
                    lastError = error.message ?: "ورود ناموفق بود. / Sign-in failed."
                )
            }
        }
    }

    fun createAccountWithEmail(email: String, password: String, displayName: String, username: String) {
        if (!api.isConfigured) {
            _accountState.value = _accountState.value.copy(lastError = "ابتدا آدرس سرور را تنظیم کنید.")
            return
        }
        _accountState.value = _accountState.value.copy(isBusy = true, lastError = null, lastMessage = null)
        viewModelScope.launch {
            try {
                acceptSignedInUser(api.register(email, password, displayName, username))
            } catch (error: Exception) {
                _accountState.value = _accountState.value.copy(
                    isBusy = false,
                    lastError = error.message ?: "ثبت‌نام ناموفق بود. / Registration failed."
                )
            }
        }
    }

    fun signOut() {
        remoteSyncJob?.cancel()
        _accountState.value = _accountState.value.copy(isBusy = true, lastError = null, lastMessage = null)
        viewModelScope.launch {
            runCatching { api.logout() }
            runCatching {
                dao.deleteAllDirectMessages()
                dao.deleteAllPrivateChannelsExceptLocalAssistant()
                dao.deleteAllComments()
                dao.deleteAllPosts()
                dao.deleteAllStories()
                dao.deleteAllProfiles()
            }
            _accountState.value = AccountUiState(isCloudAvailable = api.isConfigured)
        }
    }

    private suspend fun acceptSignedInUser(user: ApiUser) {
        val app = getApplication<Application>()
        val sessionPrefs = app.getSharedPreferences("echochat_account_cache", android.content.Context.MODE_PRIVATE)
        val previousUserId = sessionPrefs.getString("last_active_uid", null)
        if (previousUserId != user.id) {
            if (previousUserId == null) {
                dao.deleteAllDirectMessages()
                dao.deleteAllPrivateChannelsExceptLocalAssistant()
            } else {
                dao.deleteAllDirectMessages()
                dao.deleteAllPrivateChannelsExceptLocalAssistant()
                dao.deleteAllComments()
                dao.deleteAllPosts()
                dao.deleteAllStories()
                dao.deleteAllProfiles()
            }
            sessionPrefs.edit().putString("last_active_uid", user.id).apply()
        }

        _accountState.value = AccountUiState(
            isCloudAvailable = true,
            isSignedIn = true,
            userId = user.id,
            username = user.username,
            displayName = user.displayName,
            email = user.email,
            isBusy = false
        )
        startRemoteSync(user)
    }

    private fun startRemoteSync(user: ApiUser) {
        remoteSyncJob?.cancel()
        remoteSyncJob = viewModelScope.launch {
            while (kotlinx.coroutines.currentCoroutineContext().isActive &&
                api.currentToken() != null &&
                _accountState.value.userId == user.id
            ) {
                try {
                    synchronizeRemoteData(user)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Keep the chat UI available during temporary network outages.
                    // The next polling pass retries queued sends and remote reads.
                }
                kotlinx.coroutines.delay(5000)
            }
        }
    }

    private suspend fun synchronizeRemoteData(user: ApiUser) {
        val rooms = api.getRooms()
        val conversations = api.getConversations()
        val channelIds = linkedSetOf<String>()
        if (rooms.isNotEmpty()) dao.deletePublicChannelsNotInIds(rooms.map { it.id })

        for (room in rooms) {
            val existing = dao.getChannelByIdOnce(room.id)
            dao.insertChannel(
                ChannelEntity(
                    id = room.id,
                    name = room.name,
                    description = room.description,
                    iconName = room.iconName,
                    isDirectMessage = false,
                    unreadCount = existing?.unreadCount ?: 0,
                    lastMessage = room.lastMessage,
                    lastMessageTime = room.lastMessageTime,
                    createdAt = existing?.createdAt ?: 0L
                )
            )
            channelIds.add(room.id)
        }

        for (conversation in conversations) {
            val name = conversation.peerDisplayName.ifBlank { "@" + conversation.peerUsername }
            val existing = dao.getChannelByIdOnce(conversation.id)
            dao.insertChannel(
                ChannelEntity(
                    id = conversation.id,
                    name = name,
                    description = "گفتگوی خصوصی",
                    iconName = "person",
                    isDirectMessage = true,
                    peerName = name,
                    peerId = conversation.peerId,
                    peerAvatarColor = existing?.peerAvatarColor ?: 0xFF8B5CF6,
                    unreadCount = existing?.unreadCount ?: 0,
                    lastMessage = conversation.lastMessage,
                    lastMessageTime = conversation.lastMessageTime,
                    createdAt = if (conversation.createdAt > 0) conversation.createdAt else (existing?.createdAt ?: 0L)
                )
            )
            channelIds.add(conversation.id)
        }

        // Retry local outgoing messages which have not yet been acknowledged by the API.
        for (channelId in channelIds) {
            val pending = repository.getMessagesForChannel(channelId).first()
                .filter { !it.isSynced && it.senderId == user.id }
            for (message in pending) {
                try {
                    api.sendMessage(channelId, message.id, message.content)
                    dao.markMessagesSynced(listOf(message.id))
                } catch (_: Exception) {
                    // Keep it queued; the next poll will retry it.
                }
            }
        }

        // The API is authoritative. Upsert remote history into Room so Compose flows update.
        for (channelId in channelIds) {
            val remoteMessages = api.getMessages(channelId)
            if (remoteMessages.isNotEmpty()) {
                dao.upsertMessages(
                    remoteMessages.map { message ->
                        MessageEntity(
                            id = message.id,
                            channelId = channelId,
                            senderId = message.senderId,
                            senderName = message.senderName,
                            content = message.content,
                            timestamp = message.timestamp,
                            isOutgoing = message.senderId == user.id,
                            status = if (message.senderId == user.id) "sent" else "delivered",
                            isSynced = true
                        )
                    }
                )
                val latest = remoteMessages.last()
                dao.updateChannelLastMessage(channelId, latest.content, latest.timestamp)
            }
        }

        // Keep the social timeline authoritative on the server as well as on this device.
        val remotePosts = api.getPosts()
        if (remotePosts.isEmpty()) {
            dao.deleteAllPosts()
        } else {
            dao.deletePostsNotInIds(remotePosts.map { it.id })
            dao.upsertPosts(remotePosts.map { it.toEntity() })
        }

        val remoteStories = api.getStories()
        dao.deleteAllStories()
        if (remoteStories.isNotEmpty()) {
            dao.insertStories(remoteStories.map { it.toEntity() })
        }
    }
 
    val channels: StateFlow<List<ChannelEntity>> = repository.allChannels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userProfile: StateFlow<UserProfileEntity?> = repository.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val posts: StateFlow<List<PostEntity>> = repository.allPosts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedPosts: StateFlow<List<PostEntity>> = repository.savedPosts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stories: StateFlow<List<StoryEntity>> = repository.allStories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Bottom Navigation Tab: 0 = Chats, 1 = Explore, 2 = Saved, 3 = Profile
    private val _currentMainTab = MutableStateFlow(0)
    val currentMainTab: StateFlow<Int> = _currentMainTab.asStateFlow()

    // Explore Category Filter: "all", "trending", "tech", "design", "ai"
    private val _selectedExploreCategory = MutableStateFlow("all")
    val selectedExploreCategory: StateFlow<String> = _selectedExploreCategory.asStateFlow()

    private val _selectedChannelId = MutableStateFlow("general")
    val selectedChannelId: StateFlow<String> = _selectedChannelId.asStateFlow()

    private val _currentLanguage = MutableStateFlow(AppLanguage.PERSIAN)
    val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

    private val _isDarkMode = MutableStateFlow<Boolean?>(null) // null = follow system
    val isDarkMode: StateFlow<Boolean?> = _isDarkMode.asStateFlow()

    init {
        viewModelScope.launch {
            userPreferences.language.collect { _currentLanguage.value = it }
        }
        viewModelScope.launch {
            userPreferences.darkMode.collect { _isDarkMode.value = it }
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    private val _activeChatCategoryTab = MutableStateFlow(0) // 0: All, 1: Channels, 2: DMs
    val activeChatCategoryTab: StateFlow<Int> = _activeChatCategoryTab.asStateFlow()

    private val _replyingTo = MutableStateFlow<MessageEntity?>(null)
    val replyingTo: StateFlow<MessageEntity?> = _replyingTo.asStateFlow()

    // Active Channel details
    val activeChannel: StateFlow<ChannelEntity?> = _selectedChannelId
        .flatMapLatest { id -> repository.getChannel(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Messages for active channel
    val messages: StateFlow<List<MessageEntity>> = _selectedChannelId
        .flatMapLatest { id -> repository.getMessagesForChannel(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search messages across every locally stored conversation.
    val searchedMessages: StateFlow<List<MessageEntity>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(emptyList()) else repository.searchMessages(query.trim())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Typing user indicator for active channel
    val typingUser: StateFlow<String?> = combine(
        _selectedChannelId,
        repository.typingStatus
    ) { currentId, statusMap ->
        statusMap[currentId]
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Filtered channels based on search and chat category tab
    val filteredChannels: StateFlow<List<ChannelEntity>> = combine(
        channels,
        _searchQuery,
        _activeChatCategoryTab
    ) { allChannels, query, tab ->
        var list = allChannels
        if (tab == 1) {
            list = list.filter { !it.isDirectMessage }
        } else if (tab == 2) {
            list = list.filter { it.isDirectMessage }
        }

        if (query.isNotBlank()) {
            list = list.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true) ||
                it.lastMessage.contains(query, ignoreCase = true)
            }
        }
        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered posts based on explore category and search query
    val filteredPosts: StateFlow<List<PostEntity>> = combine(
        posts,
        _selectedExploreCategory,
        _searchQuery
    ) { allPosts, category, query ->
        var list = allPosts
        if (category != "all") {
            list = if (category == "trending") {
                list.sortedByDescending { it.likesCount }
            } else {
                list.filter { it.category == category }
            }
        }

        if (query.isNotBlank()) {
            list = list.filter {
                it.content.contains(query, ignoreCase = true) ||
                it.authorName.contains(query, ignoreCase = true) ||
                it.tags.contains(query, ignoreCase = true)
            }
        }
        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setMainTab(tab: Int) {
        _currentMainTab.value = tab
    }

    fun setExploreCategory(category: String) {
        _selectedExploreCategory.value = category
    }

    fun selectChannel(channelId: String) {
        _selectedChannelId.value = channelId
        _replyingTo.value = null
        viewModelScope.launch {
            repository.clearChannelUnread(channelId)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleSearch(active: Boolean) {
        _isSearchActive.value = active
        if (!active) _searchQuery.value = ""
    }

    fun setActiveChatCategoryTab(tab: Int) {
        _activeChatCategoryTab.value = tab
    }

    fun setReplyingTo(message: MessageEntity?) {
        _replyingTo.value = message
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return
        val currentChannel = _selectedChannelId.value
        val reply = _replyingTo.value
        viewModelScope.launch {
            val account = _accountState.value
            val messageId = repository.sendMessage(
                channelId = currentChannel,
                content = content,
                replyToId = reply?.id,
                replyToSnippet = reply?.content?.take(40),
                senderId = account.userId ?: "local-device",
                senderName = account.displayName?.takeIf { it.isNotBlank() } ?: "Echo User"
            )
            _replyingTo.value = null
            if (account.isSignedIn && currentChannel != "bot_echo") {
                runCatching { api.sendMessage(currentChannel, messageId, content.trim()) }
                    .onSuccess { dao.markMessagesSynced(listOf(messageId)) }
                // If the network call fails, the polling worker retries this local message.
            }
        }
    }

    fun reactToMessage(messageId: String, emoji: String) {
        viewModelScope.launch {
            repository.setReaction(messageId, emoji)
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            repository.deleteMessage(messageId)
        }
    }

    fun clearChat() {
        val currentChannel = _selectedChannelId.value
        viewModelScope.launch {
            repository.clearChat(currentChannel)
        }
    }

    fun createChannel(
        name: String,
        desc: String,
        icon: String = "forum",
        onComplete: (String?) -> Unit = {}
    ) {
        val account = _accountState.value
        if (!account.isSignedIn) {
            onComplete("برای ساخت کانال ابتدا وارد حساب کاربری شوید.")
            return
        }

        val cleanName = name.trim()
        if (cleanName.length < 2) {
            onComplete("نام کانال باید حداقل دو نویسه داشته باشد.")
            return
        }

        viewModelScope.launch {
            try {
                // Create remotely first. Never show a channel as created before the API confirms it.
                val room = api.createRoom(cleanName, desc.trim(), icon)
                val now = System.currentTimeMillis()
                dao.insertChannel(
                    ChannelEntity(
                        id = room.id,
                        name = room.name,
                        description = room.description,
                        iconName = room.iconName,
                        isDirectMessage = false,
                        lastMessage = room.lastMessage,
                        lastMessageTime = room.lastMessageTime,
                        createdAt = now
                    )
                )
                _selectedChannelId.value = room.id
                _currentMainTab.value = 0
                onComplete(null)
            } catch (error: Exception) {
                onComplete(error.message ?: "ساخت کانال ناموفق بود. دوباره تلاش کنید.")
            }
        }
    }

    fun startDirectMessage(username: String, onComplete: (String?) -> Unit) {
        val account = _accountState.value
        if (!account.isSignedIn || account.userId.isNullOrBlank()) {
            onComplete("برای شروع گفتگوی خصوصی ابتدا وارد حساب شوید.")
            return
        }
        viewModelScope.launch {
            try {
                val conversation = api.openConversation(username)
                val peerName = conversation.peerDisplayName.ifBlank { "@" + conversation.peerUsername }
                repository.startDirectMessage(
                    conversationId = conversation.id,
                    peerId = conversation.peerId,
                    peerName = peerName
                )
                val existing = dao.getChannelByIdOnce(conversation.id)
                dao.insertChannel(
                    (existing ?: ChannelEntity(id = conversation.id)).copy(
                        name = peerName,
                        description = "گفتگوی خصوصی",
                        iconName = "person",
                        isDirectMessage = true,
                        peerName = peerName,
                        peerId = conversation.peerId,
                        lastMessage = conversation.lastMessage,
                        lastMessageTime = conversation.lastMessageTime
                    )
                )
                _selectedChannelId.value = conversation.id
                _currentMainTab.value = 0
                _replyingTo.value = null
                onComplete(null)
            } catch (error: Exception) {
                onComplete(error.message ?: "Could not open the private conversation.")
            }
        }
    }

    // --- Social Actions: authenticated API first, then update Room for Compose observers. ---
    fun createPost(
        content: String,
        tags: String,
        category: String,
        onComplete: (String?) -> Unit = {}
    ) {
        if (!_accountState.value.isSignedIn) {
            onComplete("برای انتشار پست ابتدا وارد حساب کاربری شوید.")
            return
        }
        viewModelScope.launch {
            try {
                val post = api.createPost(content, tags, category)
                dao.insertPost(post.toEntity())
                onComplete(null)
            } catch (error: Exception) {
                onComplete(error.message ?: "انتشار پست ناموفق بود. دوباره تلاش کنید.")
            }
        }
    }

    fun toggleLikePost(postId: String, currentLiked: Boolean, currentCount: Int) {
        viewModelScope.launch {
            runCatching { api.togglePostLike(postId) }
                .onSuccess { dao.insertPost(it.toEntity()) }
        }
    }

    fun toggleBookmarkPost(postId: String, currentBookmarked: Boolean) {
        viewModelScope.launch {
            runCatching { api.togglePostBookmark(postId) }
                .onSuccess { dao.insertPost(it.toEntity()) }
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            runCatching { api.deletePost(postId) }
                .onSuccess { dao.deletePost(postId) }
        }
    }

    fun markStoryViewed(storyId: String) {
        viewModelScope.launch {
            dao.markStoryViewed(storyId)
            runCatching { api.markStoryViewed(storyId) }
        }
    }

    fun createStory(title: String) {
        viewModelScope.launch {
            runCatching { api.createStory(title) }
                .onSuccess { dao.insertStory(it.toEntity()) }
        }
    }

    fun commentsForPost(postId: String): Flow<List<CommentEntity>> =
        repository.getCommentsForPost(postId)

    fun refreshComments(postId: String) {
        viewModelScope.launch {
            runCatching { api.getComments(postId) }
                .onSuccess { comments ->
                    dao.deleteCommentsForPost(postId)
                    if (comments.isNotEmpty()) {
                        dao.insertComments(comments.map { it.toEntity() })
                    }
                }
        }
    }

    fun createComment(postId: String, content: String) {
        viewModelScope.launch {
            runCatching { api.createComment(postId, content) }
                .onSuccess { dao.insertCommentAndIncrementCount(it.toEntity()) }
        }
    }

    private fun ApiPost.toEntity() = PostEntity(
        id = id,
        authorId = authorId,
        authorName = authorName,
        authorRole = authorRole,
        authorAvatarColor = authorAvatarColor,
        content = content,
        tags = tags,
        category = category,
        likesCount = likesCount,
        commentsCount = commentsCount,
        isLiked = isLiked,
        isBookmarked = isBookmarked,
        timestamp = timestamp
    )

    private fun ApiStory.toEntity() = StoryEntity(
        id = id,
        authorName = authorName,
        authorAvatarColor = authorAvatarColor,
        storyTitle = storyTitle,
        isViewed = isViewed,
        timestamp = timestamp
    )

    private fun ApiComment.toEntity() = CommentEntity(
        id = id,
        postId = postId,
        authorId = authorId,
        authorName = authorName,
        content = content,
        timestamp = timestamp
    )

    fun toggleLanguage() {
        setLanguage(if (_currentLanguage.value == AppLanguage.PERSIAN) AppLanguage.ENGLISH else AppLanguage.PERSIAN)
    }

    fun setLanguage(lang: AppLanguage) {
        _currentLanguage.value = lang
        viewModelScope.launch { userPreferences.setLanguage(lang) }
    }

    fun toggleTheme() {
        _isDarkMode.value = when (_isDarkMode.value) {
            null -> true
            true -> false
            false -> null
        }
        viewModelScope.launch { userPreferences.setDarkMode(_isDarkMode.value) }
    }

    fun updateProfile(displayName: String, bio: String, avatarColor: Long) {
        viewModelScope.launch {
            repository.updateProfile(
                displayName = displayName,
                bio = bio,
                avatarColor = avatarColor,
                languageCode = _currentLanguage.value.code,
                isDarkMode = _isDarkMode.value
            )
        }
    }
}

/**
 * UI-facing snapshot of cloud account state.
 *
 * isCloudAvailable indicates whether the self-hosted PHP API URL has been tested.
 * Chat authentication and message synchronization do not require a Firebase project.
 */
data class AccountUiState(
    val isCloudAvailable: Boolean = false,
    val isSignedIn: Boolean = false,
    val userId: String? = null,
    val username: String? = null,
    val displayName: String? = null,
    val email: String? = null,
    val isBusy: Boolean = false,
    val lastError: String? = null,
    val lastMessage: String? = null
)
