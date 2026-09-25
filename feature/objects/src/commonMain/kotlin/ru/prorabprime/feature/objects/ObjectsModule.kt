package ru.prorabprime.feature.objects

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.feature.objects.details.IObjectDetailsStateHolder
import ru.prorabprime.feature.objects.details.ObjectDetailsErrorHandler
import ru.prorabprime.feature.objects.details.ObjectDetailsStateHolder
import ru.prorabprime.feature.objects.details.ObjectDetailsViewModel
import ru.prorabprime.feature.objects.edit.IObjectEditStateHolder
import ru.prorabprime.feature.objects.edit.ObjectEditArgs
import ru.prorabprime.feature.objects.edit.ObjectEditErrorHandler
import ru.prorabprime.feature.objects.edit.ObjectEditStateHolder
import ru.prorabprime.feature.objects.edit.ObjectEditViewModel
import ru.prorabprime.feature.objects.list.IObjectsListStateHolder
import ru.prorabprime.feature.objects.list.ObjectsListErrorHandler
import ru.prorabprime.feature.objects.list.ObjectsListStateHolder
import ru.prorabprime.feature.objects.list.ObjectsListViewModel

/**
 * The objects screens' graph. Each StateHolder is built inside its `viewModel` lambda, so the
 * ViewModel and its ErrorHandler share one state object. Screen arguments arrive as Koin
 * parameters, never through SavedStateHandle (ARCHITECTURE.md §5).
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
    viewModel { (objectId: ObjectId) ->
        val stateHolder: IObjectDetailsStateHolder = ObjectDetailsStateHolder()
        ObjectDetailsViewModel(
            objectId = objectId,
            stateHolder = stateHolder,
            errorHandler = ObjectDetailsErrorHandler(stateHolder, get()),
            observeObject = get(),
            refreshObjects = get(),
            deleteObject = get(),
            notifier = get(),
        )
    }
    viewModel { (args: ObjectEditArgs) ->
        val stateHolder: IObjectEditStateHolder = ObjectEditStateHolder(isNew = args.objectId == null)
        ObjectEditViewModel(
            args = args,
            savedState = get(),
            stateHolder = stateHolder,
            errorHandler = ObjectEditErrorHandler(stateHolder, get()),
            observeObject = get(),
            createObject = get(),
            updateObject = get(),
        )
    }
}
