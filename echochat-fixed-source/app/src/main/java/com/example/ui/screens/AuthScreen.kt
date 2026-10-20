package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
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
    val context = LocalContext.current
    var registerMode by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "EchoChat",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (!cloudAvailable) {
                        Text(
                            text = if (isPersian) "اتصال سرور هنوز تنظیم نشده است" else "Cloud backend is not configured",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isPersian)
                                "برای جلوگیری از نمایش اطلاعات نمایشی، ورود تا زمان اتصال Firebase غیرفعال است. یک پروژهٔ Firebase بسازید، ورود ایمیل/رمز عبور و Firestore را فعال کنید و فایل google-services.json را در پوشهٔ app قرار دهید."
                            else
                                "Sign-in is disabled until Firebase is connected, so the app will not present local demo data as a real messenger. Create a Firebase project, enable Email/Password authentication and Cloud Firestore, then place google-services.json in the app module.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://console.firebase.google.com/"))
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isPersian) "باز کردن Firebase Console" else "Open Firebase Console")
                        }
                        Text(
                            text = if (isPersian) "پس از تنظیم Firebase، پروژه را دوباره Build کنید." else "After Firebase setup, rebuild the app.",
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

                        OutlinedButton(
                            onClick = { viewModel.signInWithGoogle(context) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !accountState.isBusy
                        ) {
                            Text(if (isPersian) "ورود با حساب Google" else "Continue with Google")
                        }

                        TextButton(
                            onClick = { viewModel.sendPasswordResetEmail(email) },
                            enabled = !accountState.isBusy && email.contains("@")
                        ) {
                            Text(if (isPersian) "فراموشی رمز عبور" else "Forgot password?")
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
