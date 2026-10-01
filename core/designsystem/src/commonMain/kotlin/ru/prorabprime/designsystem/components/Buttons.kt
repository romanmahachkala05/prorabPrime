package ru.prorabprime.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton as MaterialFilledIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton as MaterialOutlinedButton
import androidx.compose.material3.TextButton as MaterialTextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

/*
 * The app's buttons: one height, one corner radius (the theme's), so a screen never mixes a pill with
 * a rectangle. Filled is the one main action of a screen, outlined the others, text the quiet ones.
 */

private val ButtonHeight = 48.dp
private val ButtonPadding = PaddingValues(horizontal = 20.dp)
private val IconButtonSize = 48.dp

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** The action is under way: the button greys out and shows a spinner in place of its label. */
    loading: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    MaterialButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = ButtonHeight),
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.small,
        contentPadding = ButtonPadding,
        content = { Busy(loading, content) },
    )
}

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** The action is under way: the button greys out and shows a spinner in place of its label. */
    loading: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    MaterialOutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = ButtonHeight),
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.small,
        contentPadding = ButtonPadding,
        border = ButtonDefaults.outlinedButtonBorder(enabled).copy(width = 1.dp),
        content = { Busy(loading, content) },
    )
}

@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** The action is under way: the button greys out and shows a spinner in place of its label. */
    loading: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    MaterialTextButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = ButtonHeight),
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.small,
        content = { Busy(loading, content) },
    )
}

@Composable
fun FilledIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialFilledIconButton(
        onClick = onClick,
        modifier = modifier.size(IconButtonSize),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        content = content,
    )
}

/** The label stays in place (invisible) while it spins, so the button keeps its size. */
@Composable
private fun Busy(loading: Boolean, content: @Composable RowScope.() -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier.alpha(if (loading) 0f else 1f),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(SpinnerSize),
                strokeWidth = SpinnerStroke,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA),
            )
        }
    }
}

private val SpinnerSize = 20.dp
private val SpinnerStroke = 2.dp
private const val DISABLED_ALPHA = 0.38f
