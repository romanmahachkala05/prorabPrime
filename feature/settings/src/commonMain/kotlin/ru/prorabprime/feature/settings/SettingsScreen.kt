package ru.prorabprime.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import ru.prorabprime.designsystem.components.BusyScreen
import ru.prorabprime.designsystem.components.Button
import ru.prorabprime.designsystem.components.LoadingBox
import ru.prorabprime.designsystem.components.OutlinedButton
import ru.prorabprime.designsystem.components.TopAppBar
import ru.prorabprime.designsystem.theme.ProrabTheme
import ru.prorabprime.designsystem.theme.Spacing
import ru.prorabprime.domain.model.Account
import ru.prorabprime.feature.settings.resources.Res
import ru.prorabprime.feature.settings.resources.settings_account
import ru.prorabprime.feature.settings.resources.settings_address_hint
import ru.prorabprime.feature.settings.resources.settings_address_label
import ru.prorabprime.feature.settings.resources.settings_back
import ru.prorabprime.feature.settings.resources.settings_check
import ru.prorabprime.feature.settings.resources.settings_check_running
import ru.prorabprime.feature.settings.resources.settings_check_succeeded
import ru.prorabprime.feature.settings.resources.settings_save
import ru.prorabprime.feature.settings.resources.settings_storage
import ru.prorabprime.feature.settings.resources.settings_storage_unlimited
import ru.prorabprime.feature.settings.resources.settings_title
import ru.prorabprime.feature.settings.resources.settings_token_label
import ru.prorabprime.feature.settings.resources.settings_trash
import ru.prorabprime.feature.settings.resources.settings_unit_bytes
import ru.prorabprime.feature.settings.resources.settings_unit_gigabytes
import ru.prorabprime.feature.settings.resources.settings_unit_kilobytes
import ru.prorabprime.feature.settings.resources.settings_unit_megabytes
import ru.prorabprime.ui.UiText
import ru.prorabprime.ui.resolve

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenTrash: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A public function cannot take an internal type, so koinViewModel()'s default lives on
    // the private overload and SettingsViewModel stays internal.
    SettingsScreen(onBack = onBack, onOpenTrash = onOpenTrash, modifier = modifier, viewModel = koinViewModel())
}

@Composable
private fun SettingsScreen(
    onBack: () -> Unit,
    onOpenTrash: () -> Unit,
    modifier: Modifier,
    viewModel: SettingsViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsContent(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        onOpenTrash = onOpenTrash,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(
    state: SettingsState,
    onEvent: (SettingsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenTrash: () -> Unit = {},
) {
    BusyScreen(state.isSaving || state.check == ConnectionCheck.Running, modifier) {
        Scaffold(
            modifier = Modifier,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.settings_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.settings_back),
                            )
                        }
                    },
                )
            },
        ) { padding ->
            when (state.status) {
                SettingsStatus.Content -> SettingsForm(state, onEvent, onOpenTrash, Modifier.padding(padding))
                SettingsStatus.Loading -> LoadingBox(Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun SettingsForm(
    state: SettingsState,
    onEvent: (SettingsEvent) -> Unit,
    onOpenTrash: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        state.account?.let { AccountInfo(it) }
        OutlinedTextField(
            value = state.baseUrl,
            onValueChange = { onEvent(SettingsEvent.BaseUrlChanged(it)) },
            label = { Text(stringResource(Res.string.settings_address_label)) },
            placeholder = { Text(stringResource(Res.string.settings_address_hint)) },
            isError = state.addressError != null,
            supportingText = state.addressError?.let { error -> { Text(error.resolve()) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.apiToken,
            onValueChange = { onEvent(SettingsEvent.TokenChanged(it)) },
            label = { Text(stringResource(Res.string.settings_token_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        CheckRow(state.check, onCheck = { onEvent(SettingsEvent.CheckClicked) })
        Button(
            onClick = { onEvent(SettingsEvent.SaveClicked) },
            loading = state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(Res.string.settings_save)) }
        OutlinedButton(onClick = onOpenTrash, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.settings_trash))
        }
    }
}

/** Whose the token is, and how much of the room for pictures it has used. */
@Composable
private fun AccountInfo(account: Account) {
    val units = ByteUnits(
        bytes = stringResource(Res.string.settings_unit_bytes),
        kilobytes = stringResource(Res.string.settings_unit_kilobytes),
        megabytes = stringResource(Res.string.settings_unit_megabytes),
        gigabytes = stringResource(Res.string.settings_unit_gigabytes),
    )
    val used = formatBytes(account.usedBytes, units)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(Res.string.settings_account, account.name), style = MaterialTheme.typography.titleMedium)
        val limit = account.limitBytes
        if (limit == null) {
            Text(stringResource(Res.string.settings_storage_unlimited, used))
        } else {
            Text(stringResource(Res.string.settings_storage, used, formatBytes(limit, units)))
            LinearProgressIndicator(
                progress = { (account.usedBytes.toFloat() / limit).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CheckRow(check: ConnectionCheck, onCheck: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        OutlinedButton(
            onClick = onCheck,
            loading = check == ConnectionCheck.Running,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.settings_check))
        }
        when (check) {
            ConnectionCheck.Idle -> Unit

            ConnectionCheck.Running -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Text(stringResource(Res.string.settings_check_running))
            }

            ConnectionCheck.Succeeded -> Text(
                stringResource(Res.string.settings_check_succeeded),
                color = MaterialTheme.colorScheme.primary,
            )

            is ConnectionCheck.Failed -> Text(check.message.resolve(), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Preview
@Composable
private fun SettingsContentPreview() {
    ProrabTheme {
        SettingsContent(
            state = SettingsState(
                status = SettingsStatus.Content,
                baseUrl = "http://192.168.1.10:8080",
                apiToken = "secret",
                check = ConnectionCheck.Failed(UiText.Raw("Сервер не отвечает по этому адресу.")),
            ),
            onEvent = {},
            onBack = {},
        )
    }
}
