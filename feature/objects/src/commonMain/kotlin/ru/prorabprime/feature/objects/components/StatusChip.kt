package ru.prorabprime.feature.objects.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import ru.prorabprime.domain.model.ObjectStatus
import ru.prorabprime.feature.objects.resources.Res
import ru.prorabprime.feature.objects.resources.objects_status_done
import ru.prorabprime.feature.objects.resources.objects_status_in_progress
import ru.prorabprime.feature.objects.resources.objects_status_paused
import ru.prorabprime.feature.objects.resources.objects_status_planned

internal val ObjectStatus.label: StringResource
    get() = when (this) {
        ObjectStatus.PLANNED -> Res.string.objects_status_planned
        ObjectStatus.IN_PROGRESS -> Res.string.objects_status_in_progress
        ObjectStatus.DONE -> Res.string.objects_status_done
        ObjectStatus.PAUSED -> Res.string.objects_status_paused
    }

@Composable
internal fun StatusChip(status: ObjectStatus, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (status) {
        ObjectStatus.PLANNED -> colors.secondaryContainer to colors.onSecondaryContainer
        ObjectStatus.IN_PROGRESS -> colors.primaryContainer to colors.onPrimaryContainer
        ObjectStatus.DONE -> DONE_GREEN to Color.White
        ObjectStatus.PAUSED -> colors.surfaceVariant to colors.onSurfaceVariant
    }
    Surface(color = container, contentColor = content, shape = PILL, modifier = modifier) {
        Text(
            stringResource(status.label),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private val DONE_GREEN = Color(0xFF2E7D32)
private val PILL = RoundedCornerShape(percent = 50)
