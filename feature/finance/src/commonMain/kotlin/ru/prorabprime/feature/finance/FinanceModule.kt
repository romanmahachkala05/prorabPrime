package ru.prorabprime.feature.finance

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import ru.prorabprime.domain.model.ObjectId

/**
 * The StateHolder is built inside the `viewModel` lambda, so the ViewModel and its ErrorHandler
 * share one state object. The object id arrives as a Koin parameter.
 */
val financeModule = module {
    factory { FinanceActions(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { (objectId: ObjectId) ->
        val stateHolder: IFinanceStateHolder = FinanceStateHolder()
        FinanceViewModel(
            objectId = objectId,
            stateHolder = stateHolder,
            errorHandler = FinanceErrorHandler(stateHolder, get()),
            actions = get(),
        )
    }
}
