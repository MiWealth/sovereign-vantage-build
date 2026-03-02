/**
 * SOVEREIGN VANTAGE V5.17.0 "ARTHUR EDITION"
 * QUIZ COMPOSABLE — Interactive quiz-taking UI
 *
 * Shows one question at a time. User taps an option → correct answer
 * and explanation are revealed. Navigation between questions. Final
 * score summary with pass/fail feedback.
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miwealth.sovereignvantage.education.QuizQuestion

// Imperial theme colours (consistent with EducationScreen)
private val Gold = Color(0xFFFFD700)
private val GoldDark = Color(0xFFB8860B)
private val GreenDark = Color(0xFF010A04)
private val GreenMid = Color(0xFF0A2F12)
private val GreenAccent = Color(0xFF1A4D2E)
private val TextPri = Color(0xFFF5F5F5)
private val TextSec = Color(0xFFB0B0B0)
private val CorrectGreen = Color(0xFF2E7D32)
private val IncorrectRed = Color(0xFFC62828)

// ============================================================================
// QUIZ HOST — decides between active quiz and score summary
// ============================================================================

@Composable
fun QuizHost(
    state: LessonDetailUiState,
    onSelectAnswer: (Int, Int) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    if (state.quizComplete) {
        QuizScoreSummary(
            state = state,
            onRetry = onRetry,
            onDismiss = onCancel
        )
    } else {
        QuizActiveScreen(
            state = state,
            onSelectAnswer = onSelectAnswer,
            onNext = onNext,
            onPrevious = onPrevious,
            onFinish = onFinish,
            onCancel = onCancel
        )
    }
}

// ============================================================================
// ACTIVE QUIZ SCREEN
// ============================================================================

@Composable
fun QuizActiveScreen(
    state: LessonDetailUiState,
    onSelectAnswer: (Int, Int) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit
) {
    val question = state.currentQuestion ?: return
    val isRevealed = question.id in state.revealedQuestions
    val selectedIndex = state.selectedAnswers[question.id]
    val total = state.quizQuestions.size
    val current = state.currentQuestionIndex + 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GreenDark)
            .padding(16.dp)
    ) {
        // Header: progress + cancel
        QuizHeader(
            current = current,
            total = total,
            answeredCount = state.answeredCount,
            onCancel = onCancel
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Question + options (scrollable)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // Question text
            Text(
                text = question.question,
                color = TextPri,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 26.sp,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // Options
            question.options.forEachIndexed { index, option ->
                QuizOptionCard(
                    index = index,
                    text = option,
                    isSelected = selectedIndex == index,
                    isRevealed = isRevealed,
                    isCorrect = index == question.correctOptionIndex,
                    onClick = {
                        if (!isRevealed) onSelectAnswer(question.id, index)
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Explanation (revealed after answering)
            AnimatedVisibility(
                visible = isRevealed && question.explanation.isNotBlank(),
                enter = fadeIn() + expandVertically()
            ) {
                ExplanationCard(
                    explanation = question.explanation,
                    wasCorrect = selectedIndex == question.correctOptionIndex
                )
            }

            // Fallback if no explanation available
            AnimatedVisibility(
                visible = isRevealed && question.explanation.isBlank()
            ) {
                Text(
                    text = "Correct answer: ${question.options[question.correctOptionIndex]}",
                    color = Gold,
                    fontSize = 14.sp,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(top = 12.dp, start = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Navigation buttons
        QuizNavigation(
            canGoBack = state.currentQuestionIndex > 0,
            isLastQuestion = state.isLastQuestion,
            isRevealed = isRevealed,
            allAnswered = state.answeredCount == total,
            onPrevious = onPrevious,
            onNext = onNext,
            onFinish = onFinish
        )
    }
}

// ============================================================================
// QUIZ HEADER
// ============================================================================

@Composable
private fun QuizHeader(
    current: Int,
    total: Int,
    answeredCount: Int,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Question $current of $total",
                color = Gold,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$answeredCount answered",
                color = TextSec,
                fontSize = 12.sp
            )
        }
        IconButton(onClick = onCancel) {
            Icon(Icons.Default.Close, "Cancel quiz", tint = TextSec)
        }
    }

    // Progress bar
    LinearProgressIndicator(
        progress = { current.toFloat() / total.toFloat() },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp)),
        color = Gold,
        trackColor = GreenAccent
    )
}

// ============================================================================
// OPTION CARD
// ============================================================================

@Composable
private fun QuizOptionCard(
    index: Int,
    text: String,
    isSelected: Boolean,
    isRevealed: Boolean,
    isCorrect: Boolean,
    onClick: () -> Unit
) {
    val label = ('A' + index).toString()

    val (borderColor, bgColor) = when {
        isRevealed && isCorrect -> CorrectGreen to CorrectGreen.copy(alpha = 0.15f)
        isRevealed && isSelected && !isCorrect -> IncorrectRed to IncorrectRed.copy(alpha = 0.15f)
        isSelected -> Gold to Gold.copy(alpha = 0.1f)
        else -> GreenAccent to Color.Transparent
    }

    val icon = when {
        isRevealed && isCorrect -> Icons.Default.CheckCircle
        isRevealed && isSelected && !isCorrect -> Icons.Default.Cancel
        else -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(enabled = !isRevealed) { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Letter badge
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isSelected || (isRevealed && isCorrect))
                        borderColor.copy(alpha = 0.3f)
                    else GreenMid
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (isSelected || (isRevealed && isCorrect)) borderColor else TextSec,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = text,
            color = TextPri,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )

        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = borderColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ============================================================================
// EXPLANATION CARD
// ============================================================================

@Composable
private fun ExplanationCard(explanation: String, wasCorrect: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        if (wasCorrect) CorrectGreen.copy(alpha = 0.12f) else IncorrectRed.copy(alpha = 0.12f),
                        GreenMid.copy(alpha = 0.5f)
                    )
                )
            )
            .border(
                1.dp,
                if (wasCorrect) CorrectGreen.copy(alpha = 0.3f) else IncorrectRed.copy(alpha = 0.3f),
                RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (wasCorrect) Icons.Default.Lightbulb else Icons.Default.Info,
                contentDescription = null,
                tint = Gold,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (wasCorrect) "Correct!" else "Incorrect — here's why:",
                color = if (wasCorrect) CorrectGreen else IncorrectRed,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = explanation,
            color = TextPri,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
    }
}

// ============================================================================
// NAVIGATION
// ============================================================================

@Composable
private fun QuizNavigation(
    canGoBack: Boolean,
    isLastQuestion: Boolean,
    isRevealed: Boolean,
    allAnswered: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Back button
        OutlinedButton(
            onClick = onPrevious,
            enabled = canGoBack,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Gold),
            border = ButtonDefaults.outlinedButtonBorder(enabled = canGoBack).copy(
                brush = Brush.linearGradient(
                    listOf(if (canGoBack) GoldDark else GreenAccent, if (canGoBack) GoldDark else GreenAccent)
                )
            )
        ) {
            Icon(Icons.Default.ArrowBack, "Previous", modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Back")
        }

        // Next / Finish
        if (isLastQuestion && allAnswered) {
            Button(
                onClick = onFinish,
                colors = ButtonDefaults.buttonColors(containerColor = Gold)
            ) {
                Text("Finish Quiz", color = GreenDark, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.Done, "Finish", tint = GreenDark, modifier = Modifier.size(18.dp))
            }
        } else {
            Button(
                onClick = onNext,
                enabled = isRevealed && !isLastQuestion,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldDark,
                    disabledContainerColor = GreenAccent
                )
            ) {
                Text(
                    "Next",
                    color = if (isRevealed && !isLastQuestion) GreenDark else TextSec,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.ArrowForward, "Next", modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ============================================================================
// SCORE SUMMARY
// ============================================================================

@Composable
fun QuizScoreSummary(
    state: LessonDetailUiState,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val total = state.quizQuestions.size
    val correct = state.correctCount
    val score = if (total > 0) (correct * 100) / total else 0
    val passed = score >= (state.lesson?.passingScore ?: 70)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GreenDark)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Result icon
        Icon(
            imageVector = if (passed) Icons.Default.EmojiEvents else Icons.Default.Refresh,
            contentDescription = null,
            tint = if (passed) Gold else IncorrectRed,
            modifier = Modifier.size(72.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (passed) "Well Done!" else "Not Quite — Keep Learning",
            color = if (passed) Gold else TextPri,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "$correct / $total correct ($score%)",
            color = TextPri,
            fontSize = 18.sp
        )

        Text(
            text = "Passing score: ${state.lesson?.passingScore ?: 70}%",
            color = TextSec,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Score breakdown bar
        LinearProgressIndicator(
            progress = { score / 100f },
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = if (passed) Gold else IncorrectRed,
            trackColor = GreenAccent
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Action buttons
        if (passed) {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Gold)
            ) {
                Text("Continue", color = GreenDark, fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = GoldDark)
            ) {
                Icon(Icons.Default.Refresh, "Retry", tint = GreenDark, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Try Again", color = GreenDark, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onDismiss) {
                Text("Review Lesson First", color = Gold)
            }
        }
    }
}
