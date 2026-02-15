/**
 * WIDGET CONFIGURATION ACTIVITY
 * 
 * Sovereign Vantage: Arthur Edition V5.5.71
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * Allows users to configure widget theme before adding to home screen.
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */
package com.miwealth.sovereignvantage.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class WidgetConfigActivity : ComponentActivity() {
    
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set result to CANCELED in case user backs out
        setResult(RESULT_CANCELED)
        
        // Get widget ID from intent
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        
        setContent {
            WidgetConfigScreen(
                currentTheme = TradingWidgetProvider.getTheme(this),
                onThemeSelected = { theme ->
                    saveAndFinish(theme)
                },
                onCancel = {
                    finish()
                }
            )
        }
    }
    
    private fun saveAndFinish(theme: WidgetTheme) {
        // Save theme preference
        TradingWidgetProvider.setTheme(this, theme)
        
        // Update the widget
        val appWidgetManager = AppWidgetManager.getInstance(this)
        TradingWidgetProvider.requestUpdate(this)
        
        // Return success
        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(RESULT_OK, resultValue)
        finish()
    }
}

@Composable
fun WidgetConfigScreen(
    currentTheme: WidgetTheme,
    onThemeSelected: (WidgetTheme) -> Unit,
    onCancel: () -> Unit
) {
    var selectedTheme by remember { mutableStateOf(currentTheme) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // Title
            Text(
                text = "Widget Theme",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Choose your widget style",
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Theme options
            ThemeOption(
                title = "Transparent",
                description = "Discrete • Blends with any wallpaper",
                isSelected = selectedTheme == WidgetTheme.TRANSPARENT,
                backgroundColor = Color(0xBF000000),
                borderColor = if (selectedTheme == WidgetTheme.TRANSPARENT) 
                    Color(0xFFFFD700) else Color(0xFF4A5568),
                textColor = Color.White,
                onClick = { selectedTheme = WidgetTheme.TRANSPARENT }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            ThemeOption(
                title = "Imperial Green + Gold",
                description = "Premium • Matches app theme",
                isSelected = selectedTheme == WidgetTheme.IMPERIAL,
                backgroundColor = Color(0xFF021508),
                borderColor = if (selectedTheme == WidgetTheme.IMPERIAL) 
                    Color(0xFFFFD700) else Color(0xFF4A5568),
                textColor = Color(0xFFFFD700),
                onClick = { selectedTheme = WidgetTheme.IMPERIAL }
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text("Cancel")
                }
                
                Button(
                    onClick = { onThemeSelected(selectedTheme) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFD700),
                        contentColor = Color.Black
                    )
                ) {
                    Text("Add Widget", fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun ThemeOption(
    title: String,
    description: String,
    isSelected: Boolean,
    backgroundColor: Color,
    borderColor: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = onClick,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = Color(0xFFFFD700),
                        unselectedColor = Color.Gray
                    )
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Column {
                    Text(
                        text = title,
                        color = textColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = description,
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
            
            // Preview
            Spacer(modifier = Modifier.height(12.dp))
            
            WidgetPreview(
                backgroundColor = backgroundColor,
                textColor = textColor
            )
        }
    }
}

@Composable
fun WidgetPreview(
    backgroundColor: Color,
    textColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor.copy(alpha = 0.5f))
            .padding(12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "+$2,847.50",
                color = Color(0xFF22C55E),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "↑ 4.23%",
                color = Color(0xFF22C55E),
                fontSize = 11.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(value = "3", label = "POS", textColor = textColor)
                StatItem(value = "12", label = "TRADES", textColor = textColor)
                StatItem(value = "78%", label = "MARGIN", textColor = Color(0xFF22C55E))
            }
        }
    }
}

@Composable
fun StatItem(value: String, label: String, textColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 8.sp
        )
    }
}
