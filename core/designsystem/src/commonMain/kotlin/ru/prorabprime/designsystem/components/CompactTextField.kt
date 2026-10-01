package ru.prorabprime.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import ru.prorabprime.designsystem.theme.Spacing

/** A one-line field two thirds as tall as a form field, for a side input that must not eat its sheet. */
@Composable
fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        modifier = modifier.fillMaxWidth().heightIn(min = FieldHeight),
        decorationBox = { inner ->
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = FieldHeight)
                    .background(colors.surfaceContainerLowest, shape)
                    .border(1.dp, colors.outlineVariant, shape)
                    .padding(horizontal = Spacing.s),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                inner()
            }
        },
    )
}

private val FieldHeight = 38.dp
