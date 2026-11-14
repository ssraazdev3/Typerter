package com.example.ui.auth

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.ui.theme.BrassAmber
import com.example.ui.theme.DarkDeskBackground
import com.example.ui.theme.DarkDeskOutline
import com.example.ui.theme.DarkDeskSurface
import com.example.ui.theme.PaperInk
import com.example.ui.theme.PaperIvory
import com.example.ui.theme.PlatenDark
import com.example.ui.theme.PlatenHighlight
import com.example.ui.theme.SpecialEliteFamily

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    onContinueWithoutAccount: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkDeskBackground)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        // Subtle candlelit radial glow behind the paper sheet
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        BrassAmber.copy(alpha = 0.16f),
                        BrassAmber.copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.32f),
                    radius = size.minDimension * 0.75f
                )
            )
        }

        Column(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Illuminated paper card emerging from a mini platen roller
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFDCD6C9),
                                    PaperIvory,
                                    Color(0xFFFFFCF5)
                                )
                            )
                        )
                        .padding(horizontal = 24.dp, vertical = 28.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "TYPERTER",
                            style = MaterialTheme.typography.labelSmall,
                            color = PaperInk.copy(alpha = 0.55f),
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "Once upon a time\nthere was a quiet page...",
                            fontFamily = SpecialEliteFamily,
                            fontSize = 24.sp,
                            lineHeight = 32.sp,
                            color = PaperInk
                        )
                        Text(
                            text = "Mechanical switches, calibrated key resistance, illuminated focus mode, and instant cloud sync across every device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PaperInk.copy(alpha = 0.72f)
                        )
                    }
                }

                // Platen roller bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(PlatenHighlight, PlatenDark, Color(0xFF0A0A0C))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 44.dp, height = 6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(BrassAmber.copy(alpha = 0.85f))
                    )
                }
            }

            // Feature highlights
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkDeskOutline, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                color = DarkDeskSurface
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FeatureRow(
                        icon = Icons.Rounded.Keyboard,
                        title = "Mechanical Sound & Key Resistance",
                        subtitle = "4 switch profiles with 35g–90g haptic actuation"
                    )
                    FeatureRow(
                        icon = Icons.Rounded.GraphicEq,
                        title = "Focus Spotlight & Ambient Audio",
                        subtitle = "Candlelit page illumination with offline soundscapes"
                    )
                    FeatureRow(
                        icon = Icons.Rounded.CloudSync,
                        title = "Cross-Device Sync & Offline Ready",
                        subtitle = "Auto-saved locally & synced to your Google account"
                    )
                    FeatureRow(
                        icon = Icons.Rounded.OfflineBolt,
                        title = "Export Anywhere",
                        subtitle = "Markdown, paginated PDF, Word DOCX & plain text"
                    )
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            Button(
                onClick = {
                    errorMessage = null
                    isLoading = true
                    GoogleAuthHelper.onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        onAuthSuccess = {
                            isLoading = false
                            onAuthSuccess()
                        },
                        onAuthError = { msg ->
                            isLoading = false
                            errorMessage = msg
                        },
                        scope = coroutineScope,
                        onAuthCancelled = {
                            isLoading = false
                        }
                    )
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("google_sign_in_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrassAmber,
                    contentColor = DarkDeskBackground
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = DarkDeskBackground,
                        strokeWidth = 2.2.dp
                    )
                } else {
                    Text(
                        text = "Sign in with Google to Sync Notes",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
            }
            OutlinedButton(
                onClick = onContinueWithoutAccount,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("continue_without_account_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SaveAlt,
                    contentDescription = null,
                    tint = BrassAmber,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "Write Without Account (Phone Storage)",
                    color = BrassAmber,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(BrassAmber.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BrassAmber,
                modifier = Modifier.size(19.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
