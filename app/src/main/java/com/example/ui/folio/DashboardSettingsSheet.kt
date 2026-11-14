package com.example.ui.folio

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.data.model.EditorPreferences
import com.example.data.model.ThemeMode
import com.example.security.AppLockSecurity
import com.example.ui.theme.BrassAmber

private enum class PasswordDialogMode {
    CREATE,
    ENABLE_WITH_FINGERPRINT,
    CHANGE,
    DISABLE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardSettingsSheet(
    visible: Boolean,
    preferences: EditorPreferences,
    userId: String,
    userDisplayName: String?,
    userEmail: String?,
    isLocalMode: Boolean = false,
    localStoragePath: String = "",
    hasPendingSync: Boolean,
    totalNotesCount: Int,
    onDismiss: () -> Unit,
    onUpdatePreferences: ((EditorPreferences) -> EditorPreferences) -> Unit,
    onSuppressAutoLock: () -> Unit = {},
    onLockNow: () -> Unit,
    onConnectGoogleAccount: () -> Unit = {},
    onSignOut: () -> Unit
) {
    if (!visible) return

    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val biometricCapability = remember(context) { AppLockSecurity.biometricStatus(context) }

    var passwordDialogMode by remember { mutableStateOf<PasswordDialogMode?>(null) }
    var currentPasswordInput by remember { mutableStateOf("") }
    var newPasswordInput by remember { mutableStateOf("") }
    var confirmPasswordInput by remember { mutableStateOf("") }
    var showPasswords by remember { mutableStateOf(false) }
    var dialogError by remember { mutableStateOf<String?>(null) }

    fun openPasswordDialog(mode: PasswordDialogMode) {
        currentPasswordInput = ""
        newPasswordInput = ""
        confirmPasswordInput = ""
        showPasswords = false
        dialogError = null
        passwordDialogMode = mode
    }

    val hasConfiguredPassword = preferences.appPasswordHash.isNotBlank() &&
        preferences.appPasswordSalt.isNotBlank()

    if (passwordDialogMode != null) {
        val mode = passwordDialogMode!!
        AlertDialog(
            onDismissRequest = { passwordDialogMode = null },
            title = {
                Text(
                    when (mode) {
                        PasswordDialogMode.CREATE -> "Set App Entry Password"
                        PasswordDialogMode.ENABLE_WITH_FINGERPRINT -> "Set Backup Password for Fingerprint"
                        PasswordDialogMode.CHANGE -> "Change App Password"
                        PasswordDialogMode.DISABLE -> "Disable App Security Lock"
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (mode == PasswordDialogMode.CHANGE || mode == PasswordDialogMode.DISABLE) {
                        OutlinedTextField(
                            value = currentPasswordInput,
                            onValueChange = {
                                currentPasswordInput = it
                                dialogError = null
                            },
                            label = { Text("Current Password") },
                            singleLine = true,
                            visualTransformation = if (showPasswords) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("security_current_password_input")
                        )
                    }

                    if (mode != PasswordDialogMode.DISABLE) {
                        OutlinedTextField(
                            value = newPasswordInput,
                            onValueChange = {
                                newPasswordInput = it
                                dialogError = null
                            },
                            label = {
                                Text(if (mode == PasswordDialogMode.CHANGE) "New Password (min 4 chars)" else "Password (min 4 chars)")
                            },
                            singleLine = true,
                            visualTransformation = if (showPasswords) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { showPasswords = !showPasswords }) {
                                    Icon(
                                        imageVector = if (showPasswords) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = "Toggle password visibility"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("security_new_password_input")
                        )

                        OutlinedTextField(
                            value = confirmPasswordInput,
                            onValueChange = {
                                confirmPasswordInput = it
                                dialogError = null
                            },
                            label = { Text("Confirm Password") },
                            singleLine = true,
                            visualTransformation = if (showPasswords) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("security_confirm_password_input")
                        )
                    } else {
                        Text(
                            text = "Confirm your current password to turn off app entry security.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (dialogError != null) {
                        Text(
                            text = dialogError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (mode) {
                            PasswordDialogMode.DISABLE -> {
                                if (!AppLockSecurity.verifyPassword(
                                        currentPasswordInput,
                                        preferences.appPasswordSalt,
                                        preferences.appPasswordHash
                                    )
                                ) {
                                    dialogError = "Current password does not match."
                                    return@TextButton
                                }
                                onUpdatePreferences {
                                    it.copy(
                                        appLockEnabled = false,
                                        biometricLockEnabled = false
                                    )
                                }
                                passwordDialogMode = null
                            }
                            PasswordDialogMode.CHANGE -> {
                                if (!AppLockSecurity.verifyPassword(
                                        currentPasswordInput,
                                        preferences.appPasswordSalt,
                                        preferences.appPasswordHash
                                    )
                                ) {
                                    dialogError = "Current password is incorrect."
                                    return@TextButton
                                }
                                if (newPasswordInput.length < 4) {
                                    dialogError = "Use at least 4 characters."
                                    return@TextButton
                                }
                                if (newPasswordInput != confirmPasswordInput) {
                                    dialogError = "Passwords do not match."
                                    return@TextButton
                                }
                                val (salt, hash) = AppLockSecurity.createSaltedHash(newPasswordInput)
                                onUpdatePreferences {
                                    it.copy(
                                        appLockEnabled = true,
                                        appPasswordSalt = salt,
                                        appPasswordHash = hash
                                    )
                                }
                                passwordDialogMode = null
                            }
                            PasswordDialogMode.CREATE, PasswordDialogMode.ENABLE_WITH_FINGERPRINT -> {
                                if (newPasswordInput.length < 4) {
                                    dialogError = "Use at least 4 characters."
                                    return@TextButton
                                }
                                if (newPasswordInput != confirmPasswordInput) {
                                    dialogError = "Passwords do not match."
                                    return@TextButton
                                }
                                val (salt, hash) = AppLockSecurity.createSaltedHash(newPasswordInput)
                                val enableBio = mode == PasswordDialogMode.ENABLE_WITH_FINGERPRINT &&
                                    biometricCapability.isAvailable
                                onUpdatePreferences {
                                    it.copy(
                                        appLockEnabled = true,
                                        biometricLockEnabled = enableBio || it.biometricLockEnabled,
                                        appPasswordSalt = salt,
                                        appPasswordHash = hash
                                    )
                                }
                                passwordDialogMode = null
                            }
                        }
                    },
                    modifier = Modifier.testTag("security_dialog_confirm")
                ) {
                    Text(if (mode == PasswordDialogMode.DISABLE) "Turn Off Lock" else "Save Password")
                }
            },
            dismissButton = {
                TextButton(onClick = { passwordDialogMode = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "App Settings & Security",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            // Account Information Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val initial = (userDisplayName?.firstOrNull() ?: userEmail?.firstOrNull() ?: 'T')
                            .uppercaseChar()
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(BrassAmber.copy(alpha = 0.18f))
                                .border(1.dp, BrassAmber.copy(alpha = 0.6f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initial.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = BrassAmber
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    isLocalMode -> "Local Phone Storage"
                                    !userDisplayName.isNullOrBlank() -> userDisplayName
                                    else -> userEmail?.substringBefore('@') ?: "Typerter Writer"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isLocalMode) "No account · Stored on phone" else (userEmail ?: "Google Account"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.AccountCircle,
                            contentDescription = "Account",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                    AccountInfoRow(
                        label = "Storage Mode",
                        value = when {
                            isLocalMode -> "Phone Storage (Offline Files)"
                            hasPendingSync -> "Offline Queue · Syncing"
                            else -> "Cloud Synced"
                        },
                        trailingIcon = if (hasPendingSync || isLocalMode) Icons.Rounded.CloudOff else Icons.Rounded.CloudDone
                    )
                    if (isLocalMode && localStoragePath.isNotBlank()) {
                        AccountInfoRow(
                            label = "Folder",
                            value = ".../" + localStoragePath.substringAfterLast("Android/", localStoragePath).takeLast(32)
                        )
                    }
                    AccountInfoRow(
                        label = "Notes Stored",
                        value = "$totalNotesCount pages (${if (isLocalMode) ".json + .md" else "cloud"})"
                    )
                    if (!isLocalMode) {
                        AccountInfoRow(
                            label = "Account ID",
                            value = if (userId.length > 14) "${userId.take(10)}...${userId.takeLast(4)}" else userId
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onUpdatePreferences {
                                    it.copy(
                                        themeMode = if (it.themeMode == ThemeMode.DARK_DESK) {
                                            ThemeMode.LIGHT_STUDIO
                                        } else {
                                            ThemeMode.DARK_DESK
                                        }
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (preferences.themeMode == ThemeMode.DARK_DESK) {
                                    Icons.Rounded.LightMode
                                } else {
                                    Icons.Rounded.DarkMode
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(if (preferences.themeMode == ThemeMode.DARK_DESK) "Light UI" else "Dark UI")
                        }

                        if (isLocalMode) {
                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    onConnectGoogleAccount()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_connect_google_button")
                            ) {
                                Text("Google Sync")
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    onSignOut()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_sign_out_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                                    contentDescription = "Sign out",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("Sign Out")
                            }
                        }
                    }
                }
            }

            // Security Setup Card (Password + Fingerprint)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = "App Entry Security",
                            tint = BrassAmber
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "App Entry Security",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = when {
                                    preferences.appLockEnabled && preferences.biometricLockEnabled ->
                                        "Protected with Password + Fingerprint"
                                    preferences.appLockEnabled ->
                                        "Protected with App Password"
                                    else ->
                                        "Protect Typerter launch with password or fingerprint"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (preferences.appLockEnabled) BrassAmber else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = if (preferences.appLockEnabled) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                            contentDescription = null,
                            tint = if (preferences.appLockEnabled) BrassAmber else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                    // Password Lock Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Password Lock",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (hasConfiguredPassword) "Password configured" else "Require password to open app",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = preferences.appLockEnabled,
                            onCheckedChange = { wantEnabled ->
                                if (wantEnabled) {
                                    if (hasConfiguredPassword) {
                                        onUpdatePreferences { it.copy(appLockEnabled = true) }
                                    } else {
                                        openPasswordDialog(PasswordDialogMode.CREATE)
                                    }
                                } else {
                                    openPasswordDialog(PasswordDialogMode.DISABLE)
                                }
                            },
                            modifier = Modifier.testTag("security_password_switch")
                        )
                    }

                    // Set / Change Password Action Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                openPasswordDialog(
                                    if (hasConfiguredPassword) PasswordDialogMode.CHANGE else PasswordDialogMode.CREATE
                                )
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                            .testTag("security_change_password_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (hasConfiguredPassword) "Change App Password" else "Create App Password",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        if (hasConfiguredPassword) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = BrassAmber,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    // Fingerprint Unlock Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Fingerprint,
                                contentDescription = null,
                                tint = if (biometricCapability.isAvailable) BrassAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = "Fingerprint Unlock",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = biometricCapability.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = preferences.biometricLockEnabled,
                            enabled = biometricCapability.isAvailable,
                            onCheckedChange = { wantBio ->
                                if (!wantBio) {
                                    onUpdatePreferences { it.copy(biometricLockEnabled = false) }
                                } else if (!hasConfiguredPassword) {
                                    openPasswordDialog(PasswordDialogMode.ENABLE_WITH_FINGERPRINT)
                                } else if (activity != null) {
                                    onSuppressAutoLock()
                                    AppLockSecurity.launchBiometricPrompt(
                                        activity = activity,
                                        title = "Enable Fingerprint Unlock",
                                        subtitle = "Verify your fingerprint to activate biometric unlock",
                                        negativeButtonText = "Cancel",
                                        onSuccess = {
                                            onUpdatePreferences {
                                                it.copy(
                                                    appLockEnabled = true,
                                                    biometricLockEnabled = true
                                                )
                                            }
                                        },
                                        onError = {}
                                    )
                                } else {
                                    onUpdatePreferences {
                                        it.copy(appLockEnabled = true, biometricLockEnabled = true)
                                    }
                                }
                            },
                            modifier = Modifier.testTag("security_fingerprint_switch")
                        )
                    }

                    if (preferences.appLockEnabled) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-Lock When Minimized",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Require unlock again after leaving Typerter",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = preferences.lockOnBackground,
                                onCheckedChange = { lockBg ->
                                    onUpdatePreferences { it.copy(lockOnBackground = lockBg) }
                                }
                            )
                        }

                        Button(
                            onClick = {
                                onDismiss()
                                onLockNow()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("security_lock_now_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Lock Typerter Now", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountInfoRow(
    label: String,
    value: String,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = BrassAmber,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
