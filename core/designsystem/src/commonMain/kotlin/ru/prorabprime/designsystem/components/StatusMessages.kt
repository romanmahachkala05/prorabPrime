package ru.prorabprime.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.stringResource
import ru.prorabprime.core.designsystem.resources.Res
import ru.prorabprime.core.designsystem.resources.designsystem_retry
import ru.prorabprime.designsystem.theme.ProrabTheme
import ru.prorabprime.designsystem.theme.Spacing
import ru.prorabprime.ui.UiText
import ru.prorabprime.ui.resolve

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** A failure that replaced the screen's content, with a way to try again. */
@Composable
fun ErrorMessage(
    message: UiText,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CenteredColumn(modifier) {
        Text(message.resolve(), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Button(onClick = onRetry) { Text(stringResource(Res.string.designsystem_retry)) }
    }
}

/** Nothing to show yet, and optionally the one thing to do about it. */
@Composable
fun EmptyMessage(
    message: UiText,
    modifier: Modifier = Modifier,
    actionLabel: UiText? = null,
    onAction: () -> Unit = {},
) {
    CenteredColumn(modifier) {
        Text(
            message.resolve(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null) TextButton(onClick = onAction) { Text(actionLabel.resolve()) }
    }
}

@Composable
private fun CenteredColumn(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.fillMaxSize().padding(Spacing.l), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) { content() }
    }
}

@Preview
@Composable
private fun ErrorMessagePreview() {
    ProrabTheme { ErrorMessage(UiText.Raw("Сервер не отвечает."), onRetry = {}) }
}

@Preview
@Composable
private fun EmptyMessagePreview() {
    ProrabTheme { EmptyMessage(UiText.Raw("Пока нет ни одного объекта"), actionLabel = UiText.Raw("Добавить")) }
}
