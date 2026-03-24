/**
 * SOVEREIGN VANTAGE V5.17.0 "ARTHUR EDITION"
 * 76-LESSON INSTITUTIONAL TRADING PROGRAMME - UI
 * 
 * Jetpack Compose UI for the education module.
 * Imperial theme with gold accents on dark green.
 * 
 * Copyright 2025-2026 MiWealth Pty Ltd
 */


package com.miwealth.sovereignvantage.education.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import com.miwealth.sovereignvantage.ui.theme.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.miwealth.sovereignvantage.education.*

// ============================================================================
// THEME COLORS (Imperial)
// ============================================================================

private val ImperialGold = VintageColors.GoldBright
private val ImperialGoldDark = VintageColors.GoldDark
private val ImperialGreen = VintageColors.EmeraldDeep
private val ImperialGreenLight = VintageColors.EmeraldDark
private val ImperialGreenAccent = VintageColors.EmeraldAccent
private val TextPrimary = VintageColors.TextPrimary
private val TextSecondary = VintageColors.TextTertiary

// ============================================================================
// MAIN SCREEN
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EducationScreen(
    viewModel: EducationViewModel = hiltViewModel(),
    onLessonClick: (Int) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        containerColor = ImperialGreen,
        topBar = {
            Column {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "TRADING PROGRAMME",
                            color = ImperialGold,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            "76-Lesson Institutional Curriculum",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ImperialGreen
                ),
                actions = {
                    IconButton(onClick = { viewModel.toggleView() }) {
                        Icon(
                            if (uiState.showModuleView) Icons.Default.List else Icons.Default.GridView,
                            contentDescription = "Toggle View",
                            tint = ImperialGold
                        )
                    }
                }
            )
            Spacer(modifier = Modifier.fillMaxWidth().height(1.5.dp).background(brush = Brush.horizontalGradient(colors = listOf(Color.Transparent, ImperialGoldDark, ImperialGold, ImperialGoldDark, Color.Transparent))))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Progress Summary Card
            item {
                ProgressSummaryCard(summary = uiState.progressSummary)
            }
            
            // Next Recommended Lesson
            uiState.nextLesson?.let { lesson ->
                item {
                    NextLessonCard(
                        lesson = lesson,
                        onClick = { onLessonClick(lesson.id) }
                    )
                }
            }
            
            // Module List or Lesson List
            if (uiState.showModuleView) {
                items(uiState.modules) { moduleWithProgress ->
                    ModuleCard(
                        module = moduleWithProgress.module,
                        progress = moduleWithProgress.progress,
                        isExpanded = moduleWithProgress.isExpanded,
                        onExpandClick = { viewModel.toggleModule(moduleWithProgress.module.id) },
                        onLessonClick = onLessonClick
                    )
                }
            } else {
                items(uiState.availableLessons) { lesson ->
                    LessonListItem(
                        lesson = lesson,
                        progress = uiState.lessonProgress[lesson.id],
                        onClick = { onLessonClick(lesson.id) }
                    )
                }
            }
            
            // Bottom spacing
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

// ============================================================================
// PROGRESS SUMMARY CARD
// ============================================================================

@Composable
fun ProgressSummaryCard(summary: ProgressSummary?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ImperialGreenLight),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                "Your Progress",
                color = ImperialGold,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Progress Bar
            val progress = summary?.overallProgressPercent ?: 0f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ImperialGreenAccent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress / 100f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(ImperialGoldDark, ImperialGold)
                            )
                        )
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${summary?.completedLessons ?: 0} of ${summary?.totalLessons ?: 76} lessons",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
                Text(
                    "${progress.toInt()}%",
                    color = ImperialGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    icon = Icons.Default.EmojiEvents,
                    value = "${summary?.certificationsEarned?.size ?: 0}",
                    label = "Certificates"
                )
                StatItem(
                    icon = Icons.Default.Star,
                    value = "${summary?.totalXpEarned ?: 0}",
                    label = "XP Earned"
                )
                StatItem(
                    icon = Icons.Default.LocalFireDepartment,
                    value = "${summary?.currentStreak ?: 0}",
                    label = "Day Streak"
                )
                StatItem(
                    icon = Icons.Default.Schedule,
                    value = "${summary?.estimatedRemainingHours ?: 190}h",
                    label = "Remaining"
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = ImperialGold, modifier = Modifier.size(24.dp))
        Text(value, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, color = TextSecondary, fontSize = 11.sp)
    }
}

// ============================================================================
// NEXT LESSON CARD
// ============================================================================

@Composable
fun NextLessonCard(
    lesson: Lesson,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(VintageColors.GoldDark.copy(alpha = 0.3f), ImperialGold.copy(alpha = 0.1f))
                    )
                )
                .border(1.dp, ImperialGold.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(ImperialGold),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Start",
                        tint = ImperialGreen,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "CONTINUE LEARNING",
                        color = ImperialGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        lesson.title,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "${lesson.durationMinutes} min • ${lesson.difficulty.displayName}",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
                
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = ImperialGold,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

// ============================================================================
// MODULE CARD
// ============================================================================

@Composable
fun ModuleCard(
    module: Module,
    progress: Float,
    isExpanded: Boolean,
    onExpandClick: () -> Unit,
    onLessonClick: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ImperialGreenLight),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header (clickable to expand)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExpandClick)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Module Number Badge
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (progress >= 1f) ImperialGold else ImperialGreenAccent
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (progress >= 1f) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Completed",
                            tint = ImperialGreen,
                            modifier = Modifier.size(28.dp)
                        )
                    } else {
                        Text(
                            "${module.id}",
                            color = if (progress > 0f) ImperialGold else TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        module.name,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Mini progress bar
                        Box(
                            modifier = Modifier
                                .width(60.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(ImperialGreenAccent)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .fillMaxHeight()
                                    .background(ImperialGold)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "${(progress * 100).toInt()}% • ${module.lessons.size} lessons",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
                
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = ImperialGold
                )
            }
            
            // Expanded Lesson List
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                ) {
                    HorizontalDivider(color = ImperialGreenAccent, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    module.lessons.forEach { lesson ->
                        LessonMiniItem(
                            lesson = lesson,
                            onClick = { onLessonClick(lesson.id) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

// ============================================================================
// LESSON ITEMS
// ============================================================================

@Composable
fun LessonMiniItem(
    lesson: Lesson,
    isCompleted: Boolean = false,
    isLocked: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isCompleted) ImperialGold.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(enabled = !isLocked, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status Icon
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isCompleted -> ImperialGold
                        isLocked -> ImperialGreenAccent
                        else -> ImperialGreenAccent
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                isCompleted -> Icon(
                    Icons.Default.Check,
                    contentDescription = "Completed",
                    tint = ImperialGreen,
                    modifier = Modifier.size(16.dp)
                )
                isLocked -> Icon(
                    Icons.Default.Lock,
                    contentDescription = "Locked",
                    tint = TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                else -> Text(
                    "${lesson.id}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                lesson.title,
                color = if (isLocked) TextSecondary else TextPrimary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${lesson.durationMinutes} min",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
        
        // Difficulty Badge
        DifficultyBadge(difficulty = lesson.difficulty)
    }
}

@Composable
fun LessonListItem(
    lesson: Lesson,
    progress: StudentProgress?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = ImperialGreenLight),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Lesson Number
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when (progress?.status) {
                            ProgressStatus.COMPLETED, ProgressStatus.CERTIFIED -> ImperialGold
                            ProgressStatus.IN_PROGRESS -> VintageColors.GoldDark.copy(alpha = 0.5f)
                            else -> ImperialGreenAccent
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (progress?.status == ProgressStatus.COMPLETED || progress?.status == ProgressStatus.CERTIFIED) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = ImperialGreen,
                        modifier = Modifier.size(28.dp)
                    )
                } else {
                    Text(
                        "${lesson.id}",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    lesson.title,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "${lesson.durationMinutes} min",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    DifficultyBadge(difficulty = lesson.difficulty)
                }
            }
            
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = ImperialGold
            )
        }
    }
}

// ============================================================================
// HELPER COMPOSABLES
// ============================================================================

@Composable
fun DifficultyBadge(difficulty: Difficulty) {
    val (color, bgColor) = when (difficulty) {
        Difficulty.BEGINNER -> Pair(VintageColors.EmeraldAccent, VintageColors.EmeraldAccent.copy(alpha = 0.2f))
        Difficulty.INTERMEDIATE -> Pair(VintageColors.Gold, VintageColors.Gold.copy(alpha = 0.2f))
        Difficulty.ADVANCED -> Pair(VintageColors.GoldDark, VintageColors.GoldDark.copy(alpha = 0.2f))
        Difficulty.EXPERT -> Pair(VintageColors.LossRed, VintageColors.LossRed.copy(alpha = 0.2f))
    }
    
    Text(
        difficulty.displayName,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
