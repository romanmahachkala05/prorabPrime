package ru.prorabprime.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import ru.prorabprime.core.designsystem.resources.Res
import ru.prorabprime.core.designsystem.resources.designsystem_pending
import ru.prorabprime.designsystem.icons.ProrabIcons
import ru.prorabprime.designsystem.theme.Spacing

/** A small clock: this was made or changed on the phone and the server does not have it yet. */
@Composable
fun PendingMark(modifier: Modifier = Modifier) {
    Icon(
        ProrabIcons.Schedule,
        contentDescription = stringResource(Res.string.designsystem_pending),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(MARK_SIZE),
    )
}

/** The mark on a plate, to lay over a picture. */
@Composable
fun PendingBadge(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = PLATE_ALPHA),
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        PendingMark(Modifier.padding(Spacing.xs))
    }
}

/** A line of text that carries the mark after it while [pending]. */
@Composable
fun PendingTitle(
    text: String,
    pending: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(text, modifier = Modifier.weight(1f, fill = false))
        if (pending) PendingMark()
    }
}

private val MARK_SIZE = 14.dp
private const val PLATE_ALPHA = 0.85f
