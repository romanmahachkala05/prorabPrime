package ru.prorabprime.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * While something is saving, the whole screen stops listening: every tap, including the bar's back
 * arrow, ends here, and a faint veil shows that it is so. Put it last in a [Box] around the screen.
 */
@Composable
fun BusyOverlay(active: Boolean, modifier: Modifier = Modifier) {
    if (!active) return
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = VEIL_ALPHA))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            },
    )
}

/** A screen that stops listening while [busy]; wraps the screen's Scaffold. */
@Composable
fun BusyScreen(
    busy: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier) {
        content()
        BusyOverlay(busy)
    }
}

private const val VEIL_ALPHA = 0.06f
