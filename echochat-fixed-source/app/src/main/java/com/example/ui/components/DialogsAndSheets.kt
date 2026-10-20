package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.PostEntity
import com.example.ui.EchoChatViewModel
import com.example.data.model.UserProfileEntity
import com.example.util.AppLanguage
import com.example.util.EchoStrings

@Composable
fun CreateChannelDialog(
    language: AppLanguage,
    onDismiss: () -> Unit,
    onCreate: (name: String, desc: String, icon: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("forum") }

    val icons = listOf(
        "forum" to Icons.Default.Forum,
        "code" to Icons.Default.Code,
        "palette" to Icons.Default.Palette,
        "smart_toy" to Icons.Default.SmartToy
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = EchoStrings.get("create_channel_title", language),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(EchoStrings.get("channel_name_label", language)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_channel_name_input"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text(EchoStrings.get("channel_desc_label", language)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )

                Text(
                    text = EchoStrings.get("channel_icon_label", language),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    icons.forEach { (key, iconVector) ->
                        val isSelected = selectedIcon == key
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { selectedIcon = key },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = iconVector,
                                contentDescription = key,
                                tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreate(name.trim(), desc.trim(), selectedIcon)
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("confirm_create_channel_button")
            ) {
                Text(EchoStrings.get("create", language))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(EchoStrings.get("cancel", language))
            }
        }
    )
}

@Composable
fun StartDirectMessageDialog(
    language: AppLanguage,
    onDismiss: () -> Unit,
    onStartChat: (username: String, onComplete: (error: String?) -> Unit) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val isPersian = language == AppLanguage.PERSIAN

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Text(
                text = EchoStrings.get("new_dm", language),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (isPersian)
                        "نام کاربری دقیق شخص را وارد کن. ایمیل یا نام نمایشی لازم نیست."
                    else
                        "Enter the person's exact username. Email addresses and display names are not used for lookup.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it.removePrefix("@").lowercase().filter { ch ->
                            ch in 'a'..'z' || ch in '0'..'9' || ch == '_'
                        }.take(32)
                        error = null
                    },
                    label = { Text(if (isPersian) "نام کاربری" else "Username") },
                    placeholder = { Text("example_user") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading
                )
                if (!error.isNullOrBlank()) {
                    Text(
                        text = error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
                if (isLoading) {
                    Text(
                        text = if (isPersian) "در حال اتصال به حساب..." else "Connecting to account…",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isLoading = true
                    error = null
                    onStartChat(username.trim()) { failure ->
                        isLoading = false
                        if (failure == null) onDismiss() else error = failure
                    }
                },
                enabled = !isLoading && username.matches(Regex("^[a-z0-9_]{3,32}$"))
            ) {
                Text(if (isLoading) {
                    if (isPersian) "لطفاً صبر کنید" else "Please wait"
                } else {
                    EchoStrings.get("create", language)
                })
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text(EchoStrings.get("cancel", language))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileAndSettingsSheet(
    profile: UserProfileEntity?,
    currentLanguage: AppLanguage,
    isDarkMode: Boolean?,
    onDismiss: () -> Unit,
    onSaveProfile: (displayName: String, bio: String, avatarColor: Long) -> Unit,
    onToggleLanguage: () -> Unit,
    onToggleTheme: () -> Unit,
    sheetState: SheetState,
    accountState: com.example.ui.AccountUiState = com.example.ui.AccountUiState(),
    onSignInWithGoogle: () -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    var displayName by remember(profile) { mutableStateOf(profile?.displayName ?: "کاربر اکو") }
    var bio by remember(profile) { mutableStateOf(profile?.bio ?: "علاقه‌مند به تکنولوژی 🚀") }
    var avatarColor by remember(profile) { mutableStateOf(profile?.avatarColor ?: 0xFF4F46E5) }

    val colorOptions = listOf(
        0xFF4F46E5, // Indigo
        0xFF7C3AED, // Violet
        0xFFEC4899, // Pink
        0xFF10B981, // Emerald
        0xFFF59E0B, // Amber
        0xFF3B82F6  // Blue
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = EchoStrings.get("profile_title", currentLanguage),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // Avatar Preview & Color Selector
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color(avatarColor)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayName.take(1).ifEmpty { "E" },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                }

                Column {
                    Text(
                        text = EchoStrings.get("avatar_color_label", currentLanguage),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        colorOptions.forEach { colorVal ->
                            val isSelected = avatarColor == colorVal
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorVal))
                                    .border(
                                        width = if (isSelected) 2.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        shape = CircleShape
                                    )
                                    .clickable { avatarColor = colorVal }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text(EchoStrings.get("display_name_label", currentLanguage)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("display_name_input"),
                singleLine = true
            )

            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text(EchoStrings.get("bio_label", currentLanguage)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2
            )

            // Settings Section
            Text(
                text = EchoStrings.get("tab_settings", currentLanguage),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Language & Theme Toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onToggleLanguage,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = if (currentLanguage == AppLanguage.PERSIAN) "English" else "فارسی")
                }

                OutlinedButton(
                    onClick = onToggleTheme,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isDarkMode == true) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = if (isDarkMode == true) EchoStrings.get("light_mode", currentLanguage) else EchoStrings.get("dark_mode", currentLanguage))
                }
            }

            // Cloud Account: hidden entirely when no Firebase project is linked, so this
            // never shows a sign-in control that could not possibly work. See
            // app/FIREBASE_SETUP.md to enable it.
            if (accountState.isCloudAvailable) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = EchoStrings.get("cloud_account_title", currentLanguage),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (accountState.isSignedIn) {
                        Text(
                            text = "${EchoStrings.get("cloud_account_signed_in_as", currentLanguage)}: " +
                                (accountState.displayName ?: accountState.email ?: ""),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        accountState.username?.let { handle ->
                            Text(
                                text = (if (currentLanguage == AppLanguage.PERSIAN) "نام کاربری شما: @" else "Your username: @") + handle,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        OutlinedButton(
                            onClick = onSignOut,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(EchoStrings.get("cloud_account_sign_out", currentLanguage))
                        }
                    } else {
                        OutlinedButton(
                            onClick = onSignInWithGoogle,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(EchoStrings.get("cloud_account_sign_in", currentLanguage))
                        }
                    }
                    accountState.lastError?.let { error ->
                        Text(
                            text = error,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Button(
                onClick = {
                    onSaveProfile(displayName.trim(), bio.trim(), avatarColor)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .testTag("save_profile_button")
            ) {
                Text(EchoStrings.get("save_profile", currentLanguage))
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}


@Composable
fun CommentsDialog(
    post: PostEntity,
    viewModel: EchoChatViewModel,
    language: AppLanguage,
    onDismiss: () -> Unit
) {
    val comments by viewModel.commentsForPost(post.id).collectAsStateWithLifecycle(initialValue = emptyList())
    var draft by remember(post.id) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(EchoStrings.get("comments_title", language)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = post.authorName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (comments.isEmpty()) {
                    Text(
                        text = EchoStrings.get("no_comments", language),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 240.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(comments, key = { it.id }) { comment ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(comment.authorName, style = MaterialTheme.typography.labelMedium)
                                Text(comment.content, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 2_000) draft = it },
                    placeholder = { Text(EchoStrings.get("comment_hint", language)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                enabled = draft.isNotBlank(),
                onClick = {
                    viewModel.createComment(post.id, draft)
                    draft = ""
                }
            ) {
                Text(EchoStrings.get("send_comment", language))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(EchoStrings.get("cancel", language))
            }
        }
    )
}
