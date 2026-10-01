package ru.prorabprime.designsystem.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import ru.prorabprime.ui.AppMessage
import ru.prorabprime.ui.MessageKind

/** What a Snackbar says and how it went, so the host can color it. */
class AppSnackbarVisuals(
    override val message: String,
    val kind: MessageKind,
) : SnackbarVisuals {
    override val actionLabel: String? = null
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration = SnackbarDuration.Short
}

suspend fun SnackbarHostState.show(message: AppMessage, text: String) =
    showSnackbar(AppSnackbarVisuals(text, message.kind))

/** Green when it went well, red when it did not. */
@Composable
fun AppSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data -> AppSnackbar(data) }
}

@Composable
private fun AppSnackbar(data: SnackbarData) {
    val success = (data.visuals as? AppSnackbarVisuals)?.kind == MessageKind.Success
    Snackbar(
        snackbarData = data,
        containerColor = if (success) SuccessGreen else MaterialTheme.colorScheme.error,
        contentColor = if (success) Color.White else MaterialTheme.colorScheme.onError,
        shape = MaterialTheme.shapes.small,
    )
}

private val SuccessGreen = Color(0xFF2E7D32)
