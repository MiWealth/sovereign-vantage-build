package com.miwealth.sovereignvantage.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.miwealth.sovereignvantage.R
import com.miwealth.sovereignvantage.core.BiometricAuth
import com.miwealth.sovereignvantage.ui.theme.*

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    
    // Pre-populate email from saved session
    LaunchedEffect(uiState.savedEmail) {
        if (uiState.savedEmail.isNotBlank() && email.isBlank()) {
            email = uiState.savedEmail
        }
    }
    
    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) {
            onLoginSuccess()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VintageColors.EmeraldDeep)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo — gold-framed emblem with bevelled depth
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(20.dp),
                        ambientColor = VintageColors.Gold.copy(alpha = 0.3f),
                        spotColor = VintageColors.Gold.copy(alpha = 0.2f)
                    )
                    .clip(RoundedCornerShape(20.dp))
                    .drawBehind {
                        // Outer bevel highlight
                        drawRoundRect(
                            brush = Brush.linearGradient(
                                colors = listOf(VintageColors.GoldLight, VintageColors.Gold, VintageColors.GoldDark),
                                start = Offset.Zero,
                                end = Offset(size.width, size.height)
                            ),
                            cornerRadius = CornerRadius(20.dp.toPx()),
                            style = Stroke(width = 3.dp.toPx())
                        )
                        // Inner shadow bevel
                        drawRoundRect(
                            brush = Brush.linearGradient(
                                colors = listOf(VintageColors.GoldDark.copy(alpha = 0.6f), VintageColors.GoldLight.copy(alpha = 0.3f)),
                                start = Offset(size.width, size.height),
                                end = Offset.Zero
                            ),
                            topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(
                                size.width - 4.dp.toPx(),
                                size.height - 4.dp.toPx()
                            ),
                            cornerRadius = CornerRadius(18.dp.toPx()),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                VintageColors.Gold.copy(alpha = 0.18f),
                                VintageColors.Gold.copy(alpha = 0.04f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SV",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = VintageColors.Gold
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Sovereign Vantage",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = VintageColors.Gold
            )
            
            Text(
                text = "Arthur Edition V5.17.0",
                style = MaterialTheme.typography.bodyMedium,
                color = VintageColors.TextSecondary
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Email Field — gold-outlined
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null, tint = VintageColors.GoldDark)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VintageColors.Gold,
                    unfocusedBorderColor = VintageColors.GoldDark.copy(alpha = 0.5f),
                    focusedLabelColor = VintageColors.Gold,
                    unfocusedLabelColor = VintageColors.TextTertiary,
                    cursorColor = VintageColors.Gold,
                    focusedTextColor = VintageColors.TextPrimary,
                    unfocusedTextColor = VintageColors.TextPrimary
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Password Field — gold-outlined
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = VintageColors.GoldDark)
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = VintageColors.GoldDark.copy(alpha = 0.7f)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VintageColors.Gold,
                    unfocusedBorderColor = VintageColors.GoldDark.copy(alpha = 0.5f),
                    focusedLabelColor = VintageColors.Gold,
                    unfocusedLabelColor = VintageColors.TextTertiary,
                    cursorColor = VintageColors.Gold,
                    focusedTextColor = VintageColors.TextPrimary,
                    unfocusedTextColor = VintageColors.TextPrimary
                )
            )
            
            Spacer(modifier = Modifier.height(28.dp))
            
            // ═══════════════════════════════════════════════
            // SIGN IN BUTTON — Embossed gold with 3D depth
            // ═══════════════════════════════════════════════
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(
                        elevation = 10.dp,
                        shape = RoundedCornerShape(12.dp),
                        ambientColor = VintageColors.GoldDark.copy(alpha = 0.4f),
                        spotColor = VintageColors.GoldDark.copy(alpha = 0.3f)
                    )
            ) {
                Button(
                    onClick = { viewModel.login(email, password) },
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            // Top highlight (light catching the top edge)
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        VintageColors.GoldLight.copy(alpha = 0.6f),
                                        Color.Transparent
                                    ),
                                    endY = size.height * 0.3f
                                ),
                                cornerRadius = CornerRadius(12.dp.toPx())
                            )
                        },
                    enabled = !uiState.isLoading && email.isNotBlank() && password.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = VintageColors.EmeraldDeep,
                        disabledContainerColor = Color.Transparent,
                        disabledContentColor = VintageColors.EmeraldDeep.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = if (!uiState.isLoading && email.isNotBlank() && password.isNotBlank()) {
                                        listOf(VintageColors.GoldLight, VintageColors.Gold, VintageColors.GoldDark)
                                    } else {
                                        listOf(
                                            VintageColors.Gold.copy(alpha = 0.25f),
                                            VintageColors.GoldDark.copy(alpha = 0.2f)
                                        )
                                    }
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = 1.dp,
                                brush = Brush.verticalGradient(
                                    colors = listOf(VintageColors.GoldLight, VintageColors.GoldDark.copy(alpha = 0.5f))
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = VintageColors.EmeraldDeep,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Sign In",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = VintageColors.EmeraldDeep
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // ═══════════════════════════════════════════════
            // BIOMETRIC BUTTON — Wired to Android BiometricPrompt
            // ═══════════════════════════════════════════════
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(
                        elevation = 6.dp,
                        shape = RoundedCornerShape(12.dp),
                        ambientColor = VintageColors.GoldDark.copy(alpha = 0.2f),
                        spotColor = VintageColors.GoldDark.copy(alpha = 0.15f)
                    )
            ) {
                OutlinedButton(
                    onClick = {
                        // Wire to REAL Android BiometricPrompt
                        val activity = context as? FragmentActivity
                        if (activity != null) {
                            BiometricAuth(activity).authenticate(
                                onSuccess = {
                                    // Biometric verified by Android OS → restore session
                                    viewModel.biometricLogin()
                                },
                                onError = { errorMsg ->
                                    viewModel.setBiometricError(errorMsg)
                                }
                            )
                        } else {
                            viewModel.setBiometricError("Biometric not available on this device")
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = VintageColors.Gold
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.5.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(VintageColors.GoldLight, VintageColors.Gold, VintageColors.GoldDark)
                        )
                    )
                ) {
                    Icon(
                        Icons.Default.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Use Biometric",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            
            // Error Message
            if (uiState.error != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Security Notice
            Text(
                text = "\uD83D\uDD12 Your keys, your crypto\nWe cannot access your funds \u2014 by design",
                style = MaterialTheme.typography.bodySmall,
                color = VintageColors.TextMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}
