package ru.prorabprime.feature.map

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The StateHolder is built inside the `viewModel` lambda, as in every feature. */
val mapModule = module {
    viewModel {
        PlacePickerViewModel(
            stateHolder = PlacePickerStateHolder(),
            findAddress = get(),
            pickedPlace = get(),
        )
    }
    viewModel {
        MapViewModel(
            stateHolder = MapStateHolder(),
            observeObjects = get(),
            geocodeObject = get(),
            notifier = get(),
        )
    }
}
