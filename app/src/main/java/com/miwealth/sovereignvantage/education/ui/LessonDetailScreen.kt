/**
 * SOVEREIGN VANTAGE V5.17.0 "ARTHUR EDITION"
 * LESSON DETAIL SCREEN — Full lesson view with content and quiz access
 *
 * Displays lesson content, objectives, topics, and provides "Take Quiz"
 * button. When quiz is active, delegates to QuizHost composable.
 *
 * Imperial theme (emerald/gold) consistent with all Sovereign Vantage UI.
 *
 * Copyright © 2025-2026 MiWealth Pty Ltd. All rights reserved.
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

package com.miwealth.sovereignvantage.education.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.miwealth.sovereignvantage.education.Difficulty
import com.miwealth.sovereignvantage.ui.theme.*

// =============================================================================
// MAIN SCREEN
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonDetailScreen(
    lessonId: Int,
    onNavigateBack: () -> Unit,
    viewModel: LessonDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(lessonId) {
        viewModel.loadLesson(lessonId)
    }
    
    // If quiz is active, show quiz UI
    if (uiState.isQuizActive) {
        QuizHost(
            state = uiState,
            onSelectAnswer = { qId, optIdx -> viewModel.selectAnswer(qId, optIdx) },
            onNext = { viewModel.nextQuestion() },
            onPrevious = { viewModel.previousQuestion() },
            onFinish = { viewModel.finishQuiz() },
            onCancel = { viewModel.cancelQuiz() },
            onRetry = { viewModel.startQuiz() }
        )
        return
    }
    
    Scaffold(
        topBar = {
            Column {
            TopAppBar(
                title = {
                    Text(
                        uiState.lesson?.title ?: "Lesson",
                        color = VintageColors.Gold,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = VintageColors.Gold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VintageColors.EmeraldDeep)
            )
            Spacer(modifier = Modifier.fillMaxWidth().height(1.5.dp).background(brush = Brush.horizontalGradient(colors = listOf(Color.Transparent, VintageColors.GoldDark, VintageColors.Gold, VintageColors.GoldDark, Color.Transparent))))
            }
        },
        containerColor = VintageColors.EmeraldDeep
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = VintageColors.Gold)
                }
            }
            uiState.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Error: ${uiState.error}",
                        color = VintageColors.LossRed,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            }
            uiState.lesson != null -> {
                LessonContent(
                    uiState = uiState,
                    onStartLesson = { viewModel.startLesson() },
                    onTakeQuiz = { viewModel.startQuiz() },
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

// =============================================================================
// LESSON CONTENT
// =============================================================================

@Composable
private fun LessonContent(
    uiState: LessonDetailUiState,
    onStartLesson: () -> Unit,
    onTakeQuiz: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lesson = uiState.lesson ?: return
    val module = uiState.module
    val progress = uiState.progress
    val scrollState = rememberScrollState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Module badge
        if (module != null) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = VintageColors.Gold.copy(alpha = 0.15f)
            ) {
                Text(
                    "Module ${module.id}: ${module.name}",
                    style = MaterialTheme.typography.labelMedium,
                    color = VintageColors.Gold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        // Lesson header
        Text(
            lesson.title,
            style = MaterialTheme.typography.headlineSmall,
            color = VintageColors.TextPrimary,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Meta row: difficulty, duration, XP
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DifficultyChip(lesson.difficulty)
            MetaChip(Icons.Default.Schedule, "${lesson.durationMinutes} min")
            MetaChip(Icons.Default.Star, "${lesson.calculateXpReward()} XP")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Progress indicator
        if (progress != null) {
            val statusColor = when {
                progress.completed -> VintageColors.ProfitGreen
                progress.quizScore != null -> VintageColors.Gold
                else -> VintageColors.TextTertiary
            }
            val statusText = when {
                progress.completed -> "Completed (${progress.quizScore}%)"
                progress.quizScore != null -> "Quiz attempted (${progress.quizScore}%)"
                progress.started -> "In progress"
                else -> "Not started"
            }
            Text(
                statusText,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
                fontStyle = if (progress.completed) FontStyle.Normal else FontStyle.Italic
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Description
        Text(
            lesson.description,
            style = MaterialTheme.typography.bodyLarge,
            color = VintageColors.TextPrimary,
            lineHeight = 24.sp
        )
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Prerequisites warning
        if (!uiState.prerequisitesMet && uiState.missingPrerequisites.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = VintageColors.LossRed.copy(alpha = 0.1f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, null, tint = VintageColors.LossRed, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Prerequisites Required", color = VintageColors.LossRed, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    uiState.missingPrerequisites.forEach { prereq ->
                        Text(
                            "• ${prereq.title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = VintageColors.TextSecondary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Learning Objectives
        SectionCard(title = "Learning Objectives", icon = Icons.Default.CheckCircle) {
            lesson.objectives.forEachIndexed { index, objective ->
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        "${index + 1}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VintageColors.Gold,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(24.dp)
                    )
                    Text(
                        objective,
                        style = MaterialTheme.typography.bodyMedium,
                        color = VintageColors.TextSecondary
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Topics Covered
        SectionCard(title = "Topics Covered", icon = Icons.Default.MenuBook) {
            lesson.topics.forEach { topic ->
                Row(
                    modifier = Modifier.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ChevronRight,
                        null,
                        tint = VintageColors.Gold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        topic,
                        style = MaterialTheme.typography.bodyMedium,
                        color = VintageColors.TextSecondary
                    )
                }
            }
        }
        
        // Lesson content (if populated)
        if (lesson.content.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            SectionCard(title = "Lesson Content", icon = Icons.Default.Article) {
                Text(
                    lesson.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VintageColors.TextPrimary,
                    lineHeight = 22.sp
                )
            }
        }
        
        // Practical Exercises
        if (lesson.practicalExercises.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            SectionCard(title = "Practical Exercises", icon = Icons.Default.Build) {
                lesson.practicalExercises.forEachIndexed { index, exercise ->
                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            "${index + 1}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VintageColors.Gold,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(24.dp)
                        )
                        Text(
                            exercise,
                            style = MaterialTheme.typography.bodyMedium,
                            color = VintageColors.TextSecondary
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Start/Continue lesson button
            if (progress == null || !progress.started) {
                Button(
                    onClick = onStartLesson,
                    enabled = uiState.prerequisitesMet,
                    colors = ButtonDefaults.buttonColors(containerColor = VintageColors.EmeraldDark),
                    modifier = Modifier.weight(1f)
                        .border(1.dp, VintageColors.GoldDark, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("START LESSON", letterSpacing = 0.5.sp)
                }
            }
            
            // Take Quiz button
            Button(
                onClick = onTakeQuiz,
                enabled = uiState.prerequisitesMet,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VintageColors.Gold,
                    contentColor = VintageColors.EmeraldDeep
                ),
                modifier = Modifier.weight(1f)
                    .border(1.dp, VintageColors.GoldDark, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Quiz, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (progress?.quizScore != null) "RETAKE QUIZ" else "TAKE QUIZ",
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
        
        // Passing score reminder
        Text(
            "Passing score: ${lesson.passingScore}% • ${lesson.assessmentType.name.replace('_', ' ')}",
            style = MaterialTheme.typography.bodySmall,
            color = VintageColors.TextMuted,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

// =============================================================================
// HELPER COMPOSABLES
// =============================================================================

@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = VintageColors.EmeraldDark
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = VintageColors.Gold, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = VintageColors.Gold,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun DifficultyChip(difficulty: Difficulty) {
    val (color, label) = when (difficulty) {
        Difficulty.BEGINNER -> Pair(VintageColors.ProfitGreen, "Beginner")
        Difficulty.INTERMEDIATE -> Pair(VintageColors.Gold, "Intermediate")
        Difficulty.ADVANCED -> Pair(VintageColors.GoldDark, "Advanced")
        Difficulty.EXPERT -> Pair(VintageColors.LossRed, "Expert")
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun MetaChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = VintageColors.TextTertiary, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = VintageColors.TextSecondary
        )
    }
}
