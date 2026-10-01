package ru.prorabprime.shared

import androidx.compose.runtime.Composable
import org.koin.compose.koinInject
import org.koin.core.module.Module
import ru.prorabprime.data.di.dataModule
import ru.prorabprime.designsystem.theme.ProrabTheme
import ru.prorabprime.feature.finance.financeModule
import ru.prorabprime.feature.map.mapModule
import ru.prorabprime.feature.materials.materialsModule
import ru.prorabprime.feature.objects.objectsModule
import ru.prorabprime.feature.settings.settingsModule
import ru.prorabprime.feature.sync.syncModule
import ru.prorabprime.feature.tasks.tasksModule
import ru.prorabprime.shared.navigation.AppNavDisplay
import ru.prorabprime.ui.di.uiModule

/** The whole app's UI. Koin must already be started, with [appModules] and a platform module. */
@Composable
fun App() {
    ProrabTheme {
        AppNavDisplay(notifier = koinInject())
    }
}

/**
 * Every platform-neutral Koin module. Koin has no aggregation step, so a module missing here
 * fails at startup rather than at compile time — which is why the list lives in one place.
 */
val appModules: List<Module> =
    listOf(
        dataModule,
        uiModule,
        objectsModule,
        settingsModule,
        financeModule,
        materialsModule,
        mapModule,
        tasksModule,
        syncModule,
    )
