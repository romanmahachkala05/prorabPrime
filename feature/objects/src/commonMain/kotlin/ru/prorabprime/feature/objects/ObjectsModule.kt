package ru.prorabprime.feature.objects

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import ru.prorabprime.feature.objects.list.IObjectsListStateHolder
import ru.prorabprime.feature.objects.list.ObjectsListErrorHandler
import ru.prorabprime.feature.objects.list.ObjectsListStateHolder
import ru.prorabprime.feature.objects.list.ObjectsListViewModel

/**
 * The objects screens' graph. Each StateHolder is built inside its `viewModel` lambda, so the
 * ViewModel and its ErrorHandler share one state object.
 */
val objectsModule = module {
    viewModel {
        val stateHolder: IObjectsListStateHolder = ObjectsListStateHolder()
        ObjectsListViewModel(
            stateHolder = stateHolder,
            errorHandler = ObjectsListErrorHandler(stateHolder, get()),
            observeObjects = get(),
            refreshObjects = get(),
            observeObjectSort = get(),
            saveObjectSort = get(),
        )
    }
}
