package ru.prorabprime.feature.expenses

import ru.prorabprime.domain.usecase.ObserveExpensesUseCase
import ru.prorabprime.domain.usecase.ObserveObjectsUseCase
import ru.prorabprime.domain.usecase.SavePaymentUseCase
import ru.prorabprime.domain.usecase.SetReceiptUseCase
import ru.prorabprime.domain.usecase.UploadPhotoUseCase

/** The use cases the expenses screen calls, in one collaborator to keep the ViewModel constructor short. */
internal class ExpensesActions(
    val observeExpenses: ObserveExpensesUseCase,
    val observeObjects: ObserveObjectsUseCase,
    val savePayment: SavePaymentUseCase,
    val uploadPhoto: UploadPhotoUseCase,
    val setReceipt: SetReceiptUseCase,
)
