package ru.prorabprime.feature.finance

import ru.prorabprime.domain.usecase.DeleteExtraWorkUseCase
import ru.prorabprime.domain.usecase.DeletePaymentUseCase
import ru.prorabprime.domain.usecase.ObserveFinanceUseCase
import ru.prorabprime.domain.usecase.ObservePaymentHistoryUseCase
import ru.prorabprime.domain.usecase.SaveExtraWorkUseCase
import ru.prorabprime.domain.usecase.SaveFinanceTermsUseCase
import ru.prorabprime.domain.usecase.SavePaymentUseCase

/** The use cases the finance screen calls, in one collaborator to keep the ViewModel's constructor short. */
internal class FinanceActions(
    val observeFinance: ObserveFinanceUseCase,
    val observeHistory: ObservePaymentHistoryUseCase,
    val saveTerms: SaveFinanceTermsUseCase,
    val savePayment: SavePaymentUseCase,
    val deletePayment: DeletePaymentUseCase,
    val saveExtraWork: SaveExtraWorkUseCase,
    val deleteExtraWork: DeleteExtraWorkUseCase,
)
