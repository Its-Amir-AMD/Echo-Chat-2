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
import com.example.data.remote.FirebaseAuthManager
import com.example.data.remote.FirestoreSyncManager
import com.example.data.repository.EchoChatRepository
import com.example.util.AppLanguage
import com.example.util.UserPreferences
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class EchoChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: EchoChatRepository
    private val userPreferences = UserPreferences(application)

    // Cloud sync is entirely optional. These are only usable once a real Firebase
    // project is linked (see app/FIREBASE_SETUP.md); until then they stay inert and
    // every screen keeps working against local Room data exactly as before.
    private val isFirebaseConfigured: Boolean
    private val authManager: FirebaseAuthManager?
    private val syncManager: FirestoreSyncManager?

    private val _accountState = MutableStateFlow(AccountUiState())
    val accountState: StateFlow<AccountUiState> = _accountState.asStateFlow()

    init {
        val database = EchoDatabase.getInstance(application)
        repository = EchoChatRepository(database.chatDao(), viewModelScope)

        isFirebaseConfigured = runCatching { FirebaseApp.getApps(application).isNotEmpty() }
            .getOrDefault(false)

        // Avoid briefly exposing the local/demo UI while Firebase restores a saved session.
        if (isFirebaseConfigured) {
            _accountState.value = AccountUiState(isCloudAvailable = true, isBusy = true)
        }

        if (isFirebaseConfigured) {
            val newAuthManager = FirebaseAuthManager()
            val newSyncManager = FirestoreSyncManager(database.chatDao(), viewModelScope)
            authManager = newAuthManager
            syncManager = newSyncManager
            newAuthManager.authStateFlow()
                .catch { /* Treat any auth plumbing failure as signed-out, never crash the UI. */ }
                .onEach { user ->
                    if (user != null) {
                        val sessionPrefs = application.getSharedPreferences(
                            "echochat_account_cache",
                            android.content.Context.MODE_PRIVATE
                        )
                        val previousUserId = sessionPrefs.getString("last_active_uid", null)
                        if (previousUserId != user.uid) {
                            try {
                                if (previousUserId == null) {
                                    // Keep locally authored profile/feed content on first sign-in,
                                    // but never show old demo/local-only direct conversations.
                                    newSyncManager.clearUnbackedPrivateConversations()
                                } else {
                                    // A real account switch must not expose the previous user's
                                    // cached private messages or account-local social content.
                                    newSyncManager.clearLocalAccountScopedCache()
                                }
                                sessionPrefs.edit().putString("last_active_uid", user.uid).apply()
                            } catch (_: Exception) {
                                _accountState.value = AccountUiState(
                                    isCloudAvailable = true,
                                    isSignedIn = false,
                                    lastError = "Could not safely switch account data on this device. Sign out and retry."
                                )
                                newAuthManager.signOut()
                                return@onEach
                            }
                        }

                        newSyncManager.start(user.uid, user.displayName)
                        val registrationPending = newAuthManager.isRegistrationInProgress
                        _accountState.value = AccountUiState(
                            isCloudAvailable = true,
                            isSignedIn = !registrationPending,
                            userId = user.uid,
                            displayName = user.displayName,
                            email = user.email,
                            isBusy = registrationPending
                        )
                        if (!registrationPending) {
                            viewModelScope.launch {
                                runCatching {
                                    newSyncManager.ensureOwnDirectoryProfile(user.uid, user.displayName)
                                }.onSuccess { username ->
                                    if (_accountState.value.userId == user.uid) {
                                        _accountState.value = _accountState.value.copy(
                                            username = username,
                                            isBusy = false
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        newSyncManager.stop()
                        val previous = _accountState.value
                        _accountState.value = AccountUiState(
                            isCloudAvailable = true,
                            isSignedIn = false,
                            lastError = previous.lastError,
                            lastMessage = previous.lastMessage
                        )
                    }
                }
                .launchIn(viewModelScope)
        } else {
            authManager = null
            syncManager = null
            _accountState.value = AccountUiState(isCloudAvailable = false, isSignedIn = false)
        }
    }

    /** Starts Google sign-in. The Web client ID is read from google-services.json. */
    fun signInWithGoogle(context: android.content.Context) {
        val manager = authManager ?: run {
            _accountState.value = _accountState.value.copy(
                isBusy = false,
                lastError = "Cloud authentication is not configured for this build."
            )
            return
        }
        _accountState.value = _accountState.value.copy(isBusy = true, lastError = null, lastMessage = null)
        viewModelScope.launch {
            runCatching { manager.signInWithGoogle(context) }
                .onFailure { e ->
                    _accountState.value = _accountState.value.copy(
                        isBusy = false,
                        lastError = e.message ?: "Google sign-in failed."
                    )
                }
        }
    }

    fun signInWithEmail(email: String, password: String) {
        val manager = authManager ?: run {
            _accountState.value = _accountState.value.copy(lastError = "Firebase is not configured.")
            return
        }
        _accountState.value = _accountState.value.copy(isBusy = true, lastError = null, lastMessage = null)
        viewModelScope.launch {
            runCatching { manager.signInWithEmail(email, password) }
                .onFailure { e ->
                    _accountState.value = _accountState.value.copy(
                        isBusy = false,
                        lastError = e.message ?: "Sign-in failed."
                    )
                }
        }
    }

    fun createAccountWithEmail(email: String, password: String, displayName: String, username: String) {
        val manager = authManager ?: run {
            _accountState.value = _accountState.value.copy(lastError = "Firebase is not configured.")
            return
        }
        _accountState.value = _accountState.value.copy(isBusy = true, lastError = null, lastMessage = null)
        viewModelScope.launch {
            runCatching { manager.createAccountWithEmail(email, password, displayName, username) }
                .onSuccess { user -> updateSignedInAccount(user, username) }
                .onFailure { e ->
                    _accountState.value = _accountState.value.copy(
                        isBusy = false,
                        lastError = e.message ?: "Account creation failed."
                    )
                }
        }
    }

    fun sendPasswordResetEmail(email: String) {
        val manager = authManager ?: run {
            _accountState.value = _accountState.value.copy(lastError = "Firebase is not configured.")
            return
        }
        _accountState.value = _accountState.value.copy(isBusy = true, lastError = null, lastMessage = null)
        viewModelScope.launch {
            runCatching { manager.sendPasswordResetEmail(email) }
                .onSuccess {
                    _accountState.value = _accountState.value.copy(
                        isBusy = false,
                        lastMessage = "If this email is registered, a password-reset message has been sent."
                    )
                }
                .onFailure { e ->
                    _accountState.value = _accountState.value.copy(
                        isBusy = false,
                        lastError = e.message ?: "Password reset failed."
                    )
                }
        }
    }

    fun signOut() {
        _accountState.value = _accountState.value.copy(lastError = null, lastMessage = null)
        authManager?.signOut()
        syncManager?.stop()
    }

    private fun updateSignedInAccount(
        user: com.google.firebase.auth.FirebaseUser,
        username: String? = null
    ) {
        _accountState.value = AccountUiState(
            isCloudAvailable = true,
            isSignedIn = true,
            userId = user.uid,
            username = username,
            displayName = user.displayName,
            email = user.email,
            isBusy = false
        )
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
            repository.sendMessage(
                channelId = currentChannel,
                content = content,
                replyToId = reply?.id,
                replyToSnippet = reply?.content?.take(40),
                senderId = _accountState.value.userId ?: "local-device",
                senderName = _accountState.value.displayName?.takeIf { it.isNotBlank() } ?: "Echo User"
            )
            _replyingTo.value = null
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

    fun createChannel(name: String, desc: String, icon: String = "forum") {
        viewModelScope.launch {
            repository.createChannel(name, desc, icon)
        }
    }

    fun startDirectMessage(username: String, onComplete: (String?) -> Unit) {
        val manager = syncManager
        val account = _accountState.value
        val currentUserId = account.userId
        if (manager == null || currentUserId.isNullOrBlank()) {
            onComplete("You must be signed in to start a private conversation.")
            return
        }

        viewModelScope.launch {
            try {
                val ensuredUsername = manager.ensureOwnDirectoryProfile(
                    currentUserId,
                    account.displayName?.takeIf { it.isNotBlank() } ?: account.username ?: "Echo User"
                )
                if (_accountState.value.userId == currentUserId) {
                    _accountState.value = _accountState.value.copy(username = ensuredUsername)
                }
                val peer = manager.findPublicUserByUsername(username)
                    ?: throw IllegalArgumentException("No account was found for @" + username + ".")
                require(peer.uid != currentUserId) { "You cannot start a conversation with your own account." }

                val conversationId = manager.openDirectConversation(
                    currentUserId = currentUserId,
                    currentDisplayName = account.displayName?.takeIf { it.isNotBlank() }
                        ?: account.username
                        ?: "Echo User",
                    peer = peer
                )
                repository.startDirectMessage(
                    conversationId = conversationId,
                    peerId = peer.uid,
                    peerName = peer.displayName.ifBlank { "@" + peer.username }
                )
                _selectedChannelId.value = conversationId
                _currentMainTab.value = 0
                _replyingTo.value = null
                onComplete(null)
            } catch (error: Exception) {
                onComplete(error.message ?: "Could not open the private conversation.")
            }
        }
    }

    // --- Social Actions ---
    fun createPost(content: String, tags: String, category: String) {
        viewModelScope.launch {
            repository.createPost(content, tags, category)
        }
    }

    fun toggleLikePost(postId: String, currentLiked: Boolean, currentCount: Int) {
        viewModelScope.launch {
            repository.toggleLikePost(postId, currentLiked, currentCount)
        }
    }

    fun toggleBookmarkPost(postId: String, currentBookmarked: Boolean) {
        viewModelScope.launch {
            repository.toggleBookmarkPost(postId, currentBookmarked)
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            repository.deletePost(postId)
        }
    }

    fun markStoryViewed(storyId: String) {
        viewModelScope.launch { repository.markStoryViewed(storyId) }
    }

    fun createStory(title: String) {
        viewModelScope.launch { repository.createStory(title) }
    }

    fun commentsForPost(postId: String): Flow<List<CommentEntity>> =
        repository.getCommentsForPost(postId)

    fun createComment(postId: String, content: String) {
        viewModelScope.launch { repository.createComment(postId, content) }
    }

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
 * [isCloudAvailable] is false whenever no Firebase project is linked (no
 * google-services.json, or Firebase failed to initialize) — in that case the
 * profile/settings screen should hide sign-in controls entirely rather than show a
 * button that cannot work. See app/FIREBASE_SETUP.md.
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
