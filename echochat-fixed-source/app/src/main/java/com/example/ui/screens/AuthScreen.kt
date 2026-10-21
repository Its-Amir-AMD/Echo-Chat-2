package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.unit.sp
import com.example.ui.AccountUiState
import com.example.ui.EchoChatViewModel
import com.example.util.AppLanguage

@Composable
fun AuthScreen(
    viewModel: EchoChatViewModel,
    accountState: AccountUiState,
    language: AppLanguage,
    cloudAvailable: Boolean
) {
    val isPersian = language == AppLanguage.PERSIAN
    var registerMode by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf(viewModel.currentServerUrl) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.8f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(58.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(29.dp)
                                )
                            }
                        }
                        Text(
                            text = "EchoChat",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (isPersian) "پیام‌ها و جوامع تو، در یکجا" else "Your conversations and communities, together",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!cloudAvailable) {
                        Text(
                            text = if (isPersian) "اتصال به سرور EchoChat" else "Connect to EchoChat server",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isPersian)
                                "برای ورود و گفتگو، آدرس بک‌اند PHP/MySQL خودت را وارد کن. این نسخه به Firebase نیاز ندارد."
                            else
                                "Enter your self-hosted PHP/MySQL API URL to sign in and chat. This version does not require Firebase.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it.trim() },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(if (isPersian) "آدرس سرور (HTTPS)" else "Server URL (HTTPS)") },
                            placeholder = { Text("https://example.com/echochat-api") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Done
                            )
                        )
                        Text(
                            text = if (isPersian)
                                "آدرس باید به پوشه‌ای برسد که index.php داخل آن است؛ انتهای آدرس /health را دستی اضافه نکن."
                            else
                                "Use the folder containing index.php; do not append /health to the URL.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (!accountState.lastError.isNullOrBlank()) {
                            Text(
                                text = accountState.lastError.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (!accountState.lastMessage.isNullOrBlank()) {
                            Text(
                                text = accountState.lastMessage.orEmpty(),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Button(
                            onClick = { viewModel.configureServer(serverUrl) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !accountState.isBusy && serverUrl.isNotBlank()
                        ) {
                            Text(if (isPersian) "بررسی اتصال و ذخیره" else "Test connection and save")
                        }
                        if (accountState.isBusy) CircularProgressIndicator()
                        Text(
                            text = if (isPersian)
                                "ابتدا بک‌اند پوشهٔ server را روی هاست HTTPS نصب کن؛ راهنما در README مخزن است."
                            else
                                "Deploy the server folder to HTTPS hosting first; the repository README has the steps.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text(
                            text = if (registerMode) {
                                if (isPersian) "ساخت حساب جدید" else "Create your account"
                            } else {
                                if (isPersian) "به اکوچت خوش آمدید" else "Welcome back"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isPersian)
                                "برای همگام‌سازی پیام‌ها در دستگاه‌های مختلف وارد شوید."
                            else
                                "Sign in to sync messages between devices.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (registerMode) {
                            OutlinedTextField(
                                value = displayName,
                                onValueChange = { displayName = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(if (isPersian) "نام نمایشی" else "Display name") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                            )
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it.lowercase().filter { ch -> ch in 'a'..'z' || ch in '0'..'9' || ch == '_' }.take(24) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(if (isPersian) "نام کاربری یکتا" else "Unique username") },
                                supportingText = { Text(if (isPersian) "۳ تا ۲۴ حرف انگلیسی، عدد یا _" else "3–24 letters, numbers, or underscores") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                            )
                        }
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it.trimStart() },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(if (isPersian) "ایمیل" else "Email") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            )
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(if (isPersian) "رمز عبور" else "Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            )
                        )

                        if (!accountState.lastError.isNullOrBlank()) {
                            Text(
                                text = accountState.lastError.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (!accountState.lastMessage.isNullOrBlank()) {
                            Text(
                                text = accountState.lastMessage.orEmpty(),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Button(
                            onClick = {
                                if (registerMode) {
                                    viewModel.createAccountWithEmail(email, password, displayName, username)
                                } else {
                                    viewModel.signInWithEmail(email, password)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !accountState.isBusy &&
                                email.contains("@") &&
                                password.isNotBlank() &&
                                (!registerMode || (
                                    password.length >= 8 &&
                                    displayName.isNotBlank() &&
                                    username.matches(Regex("^[a-z0-9_]{3,24}$"))
                                ))
                        ) {
                            Text(
                                if (registerMode) {
                                    if (isPersian) "ثبت‌نام" else "Create account"
                                } else {
                                    if (isPersian) "ورود" else "Sign in"
                                }
                            )
                        }

                        TextButton(
                            onClick = {
                                registerMode = !registerMode
                                password = ""
                            },
                            enabled = !accountState.isBusy
                        ) {
                            Text(
                                if (registerMode) {
                                    if (isPersian) "از قبل حساب دارید؟ ورود" else "Already have an account? Sign in"
                                } else {
                                    if (isPersian) "حساب ندارید؟ ثبت‌نام کنید" else "New here? Create an account"
                                }
                            )
                        }
                        if (accountState.isBusy) {
                            Spacer(modifier = Modifier.height(2.dp))
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}
