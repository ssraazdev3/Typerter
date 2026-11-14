package com.example.ui.security

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.data.model.EditorPreferences
import com.example.security.AppLockSecurity
import com.example.ui.theme.BrassAmber
import com.example.ui.theme.DarkDeskBackground
import com.example.ui.theme.DarkDeskOutline
import com.example.ui.theme.DarkDeskSurface
import com.example.ui.theme.SpecialEliteFamily

@Composable
fun AppLockScreen(
    preferences: EditorPreferences,
    userEmail: String?,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val biometricCapability = remember(context) { AppLockSecurity.biometricStatus(context) }
    val canUseBiometrics = preferences.biometricLockEnabled &&
        biometricCapability.isAvailable &&
        activity != null

    var passwordInput by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun triggerBiometric() {
        if (activity == null) return
        errorMessage = null
        AppLockSecurity.launchBiometricPrompt(
            activity = activity,
            title = "Unlock Typerter",
            subtitle = "Use your fingerprint to enter your notes",
            negativeButtonText = "Use Password",
            onSuccess = onUnlocked,
            onError = { msg -> errorMessage = msg }
        )
    }

    fun submitPassword() {
        if (passwordInput.isEmpty()) {
            errorMessage = "Enter your app password."
            return
        }
        val valid = AppLockSecurity.verifyPassword(
            password = passwordInput,
            saltBase64 = preferences.appPasswordSalt,
            expectedHashBase64 = preferences.appPasswordHash
        )
        if (valid) {
            errorMessage = null
            passwordInput = ""
            onUnlocked()
        } else {
            errorMessage = "Incorrect password. Please try again."
        }
    }

    LaunchedEffect(canUseBiometrics) {
        if (canUseBiometrics) {
            triggerBiometric()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkDeskBackground)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        BrassAmber.copy(alpha = 0.14f),
                        BrassAmber.copy(alpha = 0.03f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.28f),
                    radius = size.minDimension * 0.7f
                )
            )
        }

        Surface(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .border(1.dp, DarkDeskOutline, RoundedCornerShape(22.dp)),
            shape = RoundedCornerShape(22.dp),
            color = DarkDeskSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(BrassAmber.copy(alpha = 0.14f))
                        .border(1.dp, BrassAmber.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Typerter Locked",
                        tint = BrassAmber,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Typerter",
                        fontFamily = SpecialEliteFamily,
                        fontSize = 28.sp,
                        color = Color(0xFFF3EFE6)
                    )
                    Text(
                        text = "Enter your password${if (canUseBiometrics) " or use fingerprint" else ""} to unlock",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFA9A298),
                        textAlign = TextAlign.Center
                    )
                    if (!userEmail.isNullOrBlank()) {
                        Text(
                            text = userEmail,
                            style = MaterialTheme.typography.labelSmall,
                            color = BrassAmber.copy(alpha = 0.85f)
                        )
                    }
                }

                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = {
                        passwordInput = it
                        errorMessage = null
                    },
                    label = { Text("App Password") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submitPassword() }),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (showPassword) "Hide password" else "Show password"
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrassAmber,
                        focusedLabelColor = BrassAmber,
                        cursorColor = BrassAmber,
                        focusedTextColor = Color(0xFFF3EFE6),
                        unfocusedTextColor = Color(0xFFF3EFE6)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("app_lock_password_input")
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("app_lock_error_text")
                    )
                }

                Button(
                    onClick = { submitPassword() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("app_lock_unlock_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrassAmber,
                        contentColor = DarkDeskBackground
                    )
                ) {
                    Text(
                        text = "Unlock App",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }

                if (canUseBiometrics) {
                    OutlinedButton(
                        onClick = { triggerBiometric() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("app_lock_fingerprint_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Fingerprint,
                                contentDescription = "Unlock with Fingerprint",
                                tint = BrassAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Text("Unlock with Fingerprint", color = BrassAmber)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
    }
}
