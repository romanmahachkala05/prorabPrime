package ru.prorabprime.designsystem.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.stringResource
import ru.prorabprime.core.designsystem.resources.Res
import ru.prorabprime.core.designsystem.resources.designsystem_cancel
import ru.prorabprime.designsystem.theme.ProrabTheme
import ru.prorabprime.ui.DialogModel
import ru.prorabprime.ui.UiText
import ru.prorabprime.ui.resolve

/** Renders a screen's [DialogModel]; nothing when it is null. */
@Composable
fun DialogHost(
    dialog: DialogModel?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        is DialogModel.Confirmation -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(dialog.title.resolve()) },
            text = { Text(dialog.message.resolve()) },
            confirmButton = {
                TextButton(
                    onClick = onConfirm,
                    colors = if (dialog.destructive) {
                        ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    } else {
                        ButtonDefaults.textButtonColors()
                    },
                ) { Text(dialog.confirmLabel.resolve()) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.designsystem_cancel)) }
            },
        )

        null -> Unit
    }
}

@Preview
@Composable
private fun ConfirmationPreview() {
    ProrabTheme {
        DialogHost(
            dialog = DialogModel.Confirmation(
                title = UiText.Raw("Удалить объект?"),
                message = UiText.Raw("Объект и все его фото будут удалены."),
                confirmLabel = UiText.Raw("Удалить"),
                destructive = true,
            ),
            onConfirm = {},
            onDismiss = {},
        )
    }
}
