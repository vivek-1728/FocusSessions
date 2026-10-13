package com.focussessions.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.focussessions.app.domain.model.Task
import com.focussessions.app.ui.theme.*

@Composable
fun TaskSection(
    tasks: List<Task>,
    onAddTask: (String) -> Unit,
    onToggleTask: (Task) -> Unit,
    onDeleteTask: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var newTaskText    by remember { mutableStateOf("") }
    var showInput      by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(showInput) {
        if (showInput) focusRequester.requestFocus()
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(containerColor = BackgroundCard)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {

            // Header
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector        = Icons.Rounded.CheckCircleOutline,
                        contentDescription = null,
                        tint               = TextSecondary,
                        modifier           = Modifier.size(18.dp)
                    )
                    Text(
                        text  = "Tasks",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color      = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    if (tasks.isNotEmpty()) {
                        val done = tasks.count { it.isCompleted }
                        Text(
                            text  = "$done/${tasks.size}",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                        )
                    }
                }
                IconButton(
                    onClick  = { showInput = !showInput },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(BackgroundElevated)
                ) {
                    Icon(
                        imageVector        = if (showInput) Icons.Rounded.Close else Icons.Rounded.Add,
                        contentDescription = if (showInput) "Cancel" else "Add task",
                        tint               = if (showInput) TextPrimary else TextSecondary,
                        modifier           = Modifier.size(16.dp)
                    )
                }
            }

            // Input field
            AnimatedVisibility(
                visible = showInput,
                enter   = expandVertically(spring(Spring.DampingRatioLowBouncy)) + fadeIn(),
                exit    = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value         = newTaskText,
                            onValueChange = { newTaskText = it },
                            placeholder   = {
                                Text(
                                    "What are you working on?",
                                    style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted)
                                )
                            },
                            modifier      = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            colors        = TextFieldDefaults.colors(
                                focusedContainerColor   = BackgroundElevated,
                                unfocusedContainerColor = BackgroundElevated,
                                focusedIndicatorColor   = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor        = TextPrimary,
                                unfocusedTextColor      = TextPrimary,
                                cursorColor             = TextPrimary
                            ),
                            shape           = RoundedCornerShape(12.dp),
                            singleLine      = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (newTaskText.isNotBlank()) {
                                        onAddTask(newTaskText)
                                        newTaskText = ""
                                        showInput   = false
                                    }
                                }
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary)
                        )
                        IconButton(
                            onClick  = {
                                if (newTaskText.isNotBlank()) {
                                    onAddTask(newTaskText)
                                    newTaskText = ""
                                    showInput   = false
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AccentPrimary)
                        ) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = "Confirm",
                                tint     = BackgroundDeep,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Task list
            if (tasks.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier         = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text  = "Add tasks to stay focused",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                    )
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    tasks.forEach { task ->
                        key(task.id) {
                            TaskItem(
                                task     = task,
                                onToggle = { onToggleTask(task) },
                                onDelete = { onDeleteTask(task.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskItem(
    task: Task,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    var showDelete by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (showDelete) BackgroundElevated else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null
            ) { showDelete = !showDelete }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Checkbox
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    if (task.isCompleted) AccentPrimary
                    else BackgroundOverlay
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = null,
                    onClick           = onToggle
                )
        ) {
            if (task.isCompleted) {
                Icon(
                    imageVector        = Icons.Rounded.Check,
                    contentDescription = null,
                    tint               = BackgroundDeep,
                    modifier           = Modifier.size(12.dp)
                )
            }
        }

        Text(
            text     = task.title,
            style    = MaterialTheme.typography.bodyMedium.copy(
                color          = if (task.isCompleted) TextMuted else TextPrimary,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough
                                 else TextDecoration.None
            ),
            modifier = Modifier
                .weight(1f)
                .alpha(if (task.isCompleted) 0.5f else 1f)
        )

        AnimatedVisibility(
            visible = showDelete,
            enter   = fadeIn() + scaleIn(),
            exit    = fadeOut() + scaleOut()
        ) {
            IconButton(
                onClick  = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector        = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete task",
                    tint               = ErrorColor.copy(alpha = 0.8f),
                    modifier           = Modifier.size(16.dp)
                )
            }
        }
    }
}
