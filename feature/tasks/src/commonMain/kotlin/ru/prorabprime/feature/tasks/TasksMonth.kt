package ru.prorabprime.feature.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import ru.prorabprime.designsystem.theme.Spacing
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.feature.tasks.resources.Res
import ru.prorabprime.feature.tasks.resources.tasks_next_month
import ru.prorabprime.feature.tasks.resources.tasks_previous_month

/** The month of the day on screen, with a dot under every day that has tasks; a tap picks the day. */
@Composable
internal fun MonthGrid(state: TasksState, onEvent: (TasksEvent) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onEvent(TasksEvent.PreviousMonth) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(Res.string.tasks_previous_month))
            }
            Text(
                monthTitle(state.month),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onEvent(TasksEvent.NextMonth) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(Res.string.tasks_next_month))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            WEEKDAYS.forEach { name ->
                Text(
                    stringResource(name),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        monthWeeks(state.month).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                week.forEach { day ->
                    Box(Modifier.weight(1f).height(CELL_SIZE + 4.dp), contentAlignment = Alignment.Center) {
                        if (day != null) DayCell(day, state, onEvent)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDay,
    state: TasksState,
    onEvent: (TasksEvent) -> Unit,
) {
    val selected = day == state.day
    val mark = state.marks[day]
    val colors = MaterialTheme.colorScheme
    val description = day.format()
    // A day with open work is a solid disc, one with everything done a pale disc, an empty one is bare.
    val (fill, ink) = when {
        mark == null -> Color.Transparent to colors.onSurface
        mark.open > 0 -> colors.primary to colors.onPrimary
        else -> colors.secondaryContainer to colors.onSecondaryContainer
    }
    val ring = when {
        selected -> Modifier.border(BorderStroke(SELECTED_RING, colors.onSurface), CircleShape)
        day == state.today -> Modifier.border(BorderStroke(1.dp, colors.outline), CircleShape)
        else -> Modifier
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(CELL_SIZE)
            .clip(CircleShape)
            .background(fill)
            .then(ring)
            .clickable { onEvent(TasksEvent.DayPicked(day)) }
            .semantics { contentDescription = description },
    ) {
        Text(
            day.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (mark != null) FontWeight.Bold else FontWeight.Normal,
            color = ink,
        )
    }
}

/** The weeks of the month starting at [first], Monday first; a null is a cell of the neighboring month. */
internal fun monthWeeks(first: LocalDay): List<List<LocalDay?>> {
    val lead = first.dayOfWeek - 1
    val length = LocalDay.daysInMonth(first.year, first.month)
    val cells: List<LocalDay?> = List(lead) { null } + List(length) { first.plusDays(it) }
    val tail = (DAYS_IN_WEEK - cells.size % DAYS_IN_WEEK) % DAYS_IN_WEEK
    return (cells + List(tail) { null }).chunked(DAYS_IN_WEEK)
}

private const val DAYS_IN_WEEK = 7
private val CELL_SIZE = 40.dp
private val SELECTED_RING = 2.dp
private val CELL_GAP = 2.dp
