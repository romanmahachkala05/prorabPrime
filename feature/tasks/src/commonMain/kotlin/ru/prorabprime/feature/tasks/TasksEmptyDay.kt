package ru.prorabprime.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import ru.prorabprime.designsystem.components.OutlinedButton
import ru.prorabprime.designsystem.theme.Spacing
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.feature.tasks.resources.Res
import ru.prorabprime.feature.tasks.resources.tasks_empty
import ru.prorabprime.feature.tasks.resources.tasks_next_plan

/** A day with nothing on it, and, when there is one, the nearest plan with a way to go to its day. */
@Composable
internal fun EmptyDay(nextPlan: NextPlanUi?, onGoTo: (LocalDay) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(Res.string.tasks_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (nextPlan != null) {
            Text(
                pluralStringResource(Res.plurals.tasks_next_plan, nextPlan.daysAway, nextPlan.daysAway),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.m),
            )
            OutlinedButton(onClick = { onGoTo(nextPlan.day) }, modifier = Modifier.padding(top = Spacing.s)) {
                Text(
                    nextPlan.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(" · ${nextPlan.day.format()}", maxLines = 1)
            }
        }
    }
}
