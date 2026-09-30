package ru.prorabprime.feature.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ru.prorabprime.designsystem.components.DialogHost
import ru.prorabprime.designsystem.components.ErrorMessage
import ru.prorabprime.designsystem.components.LoadingBox
import ru.prorabprime.designsystem.theme.Spacing
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.PaymentSide
import ru.prorabprime.feature.finance.resources.Res
import ru.prorabprime.feature.finance.resources.finance_back
import ru.prorabprime.feature.finance.resources.finance_client
import ru.prorabprime.feature.finance.resources.finance_crew
import ru.prorabprime.feature.finance.resources.finance_history
import ru.prorabprime.feature.finance.resources.finance_paid_client
import ru.prorabprime.feature.finance.resources.finance_paid_crew
import ru.prorabprime.feature.finance.resources.finance_title

@Composable
fun FinanceScreen(
    objectId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: FinanceViewModel = koinViewModel(key = "finance-$objectId") { parametersOf(ObjectId(objectId)) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    FinanceContent(state, viewModel::onEvent, onBack, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FinanceContent(
    state: FinanceState,
    onEvent: (FinanceEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.finance_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.finance_back))
                    }
                },
                actions = {
                    if (state.status == FinanceStatus.Content) {
                        IconButton(onClick = { onEvent(FinanceEvent.ShowHistory) }) {
                            Icon(Icons.AutoMirrored.Filled.List, stringResource(Res.string.finance_history))
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (val status = state.status) {
            FinanceStatus.Content -> state.finance?.let { Body(it, onEvent, Modifier.padding(padding)) }

            FinanceStatus.Loading -> LoadingBox(Modifier.padding(padding))

            is FinanceStatus.Error -> ErrorMessage(
                status.message,
                onRetry = { onEvent(FinanceEvent.Retry) },
                modifier = Modifier.padding(padding),
            )
        }
    }
    FinanceEditorHost(state.editor, onEvent)
    state.history?.let { HistorySheet(it, onClose = { onEvent(FinanceEvent.HideHistory) }) }
    DialogHost(
        dialog = state.dialog,
        onConfirm = { onEvent(FinanceEvent.DialogConfirmed) },
        onDismiss = { onEvent(FinanceEvent.DialogDismissed) },
    )
}

@Composable
private fun Body(
    finance: FinanceUi,
    onEvent: (FinanceEvent) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        SideSection(
            title = Res.string.finance_client,
            paidLabel = Res.string.finance_paid_client,
            card = finance.client,
            payments = finance.clientPayments,
            onEditTotals = { onEvent(TermsEvent.Open) },
            onAddPayment = { onEvent(PaymentEvent.Add(PaymentSide.CLIENT)) },
            onOpenPayment = { onEvent(PaymentEvent.Edit(it.id)) },
        )
        SideSection(
            title = Res.string.finance_crew,
            paidLabel = Res.string.finance_paid_crew,
            card = finance.crew,
            payments = finance.crewPayments,
            onEditTotals = { onEvent(TermsEvent.Open) },
            onAddPayment = { onEvent(PaymentEvent.Add(PaymentSide.CREW)) },
            onOpenPayment = { onEvent(PaymentEvent.Edit(it.id)) },
        )
        ExtraWorksSection(
            finance = finance,
            onAdd = { onEvent(WorkEvent.Add) },
            onOpen = { onEvent(WorkEvent.Edit(it.id)) },
        )
    }
}
