package ru.prorabprime.feature.materials

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import ru.prorabprime.domain.model.ObjectId

/**
 * The StateHolder is built inside the `viewModel` lambda, so the ViewModel and its ErrorHandler
 * share one state object. The object id arrives as a Koin parameter.
 */
val materialsModule = module {
    factory { MaterialsActions(get(), get(), get(), get()) }
    viewModel { (objectId: ObjectId) ->
        val stateHolder: IMaterialsStateHolder = MaterialsStateHolder()
        MaterialsViewModel(
            objectId = objectId,
            stateHolder = stateHolder,
            errorHandler = MaterialsErrorHandler(stateHolder, get()),
            actions = get(),
        )
    }
}
