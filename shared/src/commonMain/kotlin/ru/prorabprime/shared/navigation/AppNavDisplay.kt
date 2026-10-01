package ru.prorabprime.shared.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import ru.prorabprime.designsystem.components.AppSnackbarHost
import ru.prorabprime.designsystem.components.show
import ru.prorabprime.designsystem.components.topBarColor
import ru.prorabprime.designsystem.theme.Spacing
import ru.prorabprime.feature.finance.FinanceNavKey
import ru.prorabprime.feature.finance.FinanceScreen
import ru.prorabprime.feature.map.MapNavKey
import ru.prorabprime.feature.map.MapScreen
import ru.prorabprime.feature.map.PlacePickerNavKey
import ru.prorabprime.feature.map.PlacePickerScreen
import ru.prorabprime.feature.materials.MaterialsNavKey
import ru.prorabprime.feature.materials.MaterialsScreen
import ru.prorabprime.feature.objects.details.ObjectDetailsNavKey
import ru.prorabprime.feature.objects.details.ObjectDetailsScreen
import ru.prorabprime.feature.objects.edit.ObjectEditNavKey
import ru.prorabprime.feature.objects.edit.ObjectEditScreen
import ru.prorabprime.feature.objects.list.ObjectsListNavKey
import ru.prorabprime.feature.objects.list.ObjectsListScreen
import ru.prorabprime.feature.objects.viewer.PhotoViewerNavKey
import ru.prorabprime.feature.objects.viewer.PhotoViewerScreen
import ru.prorabprime.feature.settings.SettingsNavKey
import ru.prorabprime.feature.settings.SettingsScreen
import ru.prorabprime.feature.sync.SyncStatusHost
import ru.prorabprime.feature.tasks.TasksNavKey
import ru.prorabprime.feature.tasks.TasksScreen
import ru.prorabprime.ui.SnackbarNotifier
import ru.prorabprime.ui.load

/**
 * One back stack for the whole app, and the one Snackbar host every screen's messages end up
 * in. The only place that knows every feature's key; features navigate through the callbacks
 * wired here.
 */
@Composable
fun AppNavDisplay(notifier: SnackbarNotifier, modifier: Modifier = Modifier) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(notifier) {
        notifier.messages.collect { snackbarHostState.show(it, it.text.load()) }
    }

    val backStack = rememberNavBackStack(NAV_KEYS, ObjectsListNavKey)
    Scaffold(modifier = modifier, snackbarHost = { AppSnackbarHost(snackbarHostState) }) {
        // No padding from this Scaffold: each screen has its own, with its own top bar. On a wide
        // screen (the web client) the app stays a phone-shaped column in the middle.
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            if (maxWidth > MAX_CONTENT_WIDTH) {
                // The bar goes on across the whole window, so the column does not look cut out of it.
                Box(Modifier.fillMaxWidth().height(BAR_HEIGHT).background(topBarColor()))
            }
            Box(Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxHeight()) {
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.pop() },
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = appEntries(backStack),
                    modifier = Modifier.fillMaxSize(),
                )
                SyncStatusHost(
                    onOpenSettings = { backStack.add(SettingsNavKey) },
                    // Above the place a Snackbar takes, so the two never lie over one another.
                    modifier = Modifier.align(Alignment.BottomStart)
                        .padding(start = Spacing.m, bottom = SNACKBAR_CLEARANCE)
                        .navigationBarsPadding(),
                )
            }
        }
    }
}

private val MAX_CONTENT_WIDTH = 840.dp
private val BAR_HEIGHT = 64.dp
private val SNACKBAR_CLEARANCE = 72.dp

/** Every screen of the app, with the callbacks that move between them. */
private fun appEntries(backStack: NavBackStack<NavKey>) = entryProvider<NavKey> {
    entry<ObjectsListNavKey> {
        ObjectsListScreen(
            onOpenObject = { backStack.add(ObjectDetailsNavKey(it)) },
            onCreateObject = { backStack.add(ObjectEditNavKey()) },
            onOpenSettings = { backStack.add(SettingsNavKey) },
            onOpenMap = { backStack.add(MapNavKey) },
            onOpenTasks = { backStack.add(TasksNavKey) },
        )
    }
    entry<ObjectDetailsNavKey> { key ->
        ObjectDetailsScreen(
            objectId = key.objectId,
            onEdit = { backStack.add(ObjectEditNavKey(key.objectId)) },
            onOpenPhoto = { backStack.add(PhotoViewerNavKey(key.objectId, it)) },
            onOpenFinance = { backStack.add(FinanceNavKey(key.objectId)) },
            onOpenMaterials = { backStack.add(MaterialsNavKey(key.objectId)) },
            onClose = { backStack.pop() },
        )
    }
    entry<FinanceNavKey> { key -> FinanceScreen(objectId = key.objectId, onBack = { backStack.pop() }) }
    entry<MapNavKey> {
        MapScreen(onOpenObject = { backStack.add(ObjectDetailsNavKey(it)) }, onBack = { backStack.pop() })
    }
    entry<TasksNavKey> { TasksScreen(onBack = { backStack.pop() }) }
    entry<MaterialsNavKey> { key -> MaterialsScreen(objectId = key.objectId, onBack = { backStack.pop() }) }
    entry<PhotoViewerNavKey> { key ->
        PhotoViewerScreen(objectId = key.objectId, photoId = key.photoId, onBack = { backStack.pop() })
    }
    entry<ObjectEditNavKey> { key ->
        ObjectEditScreen(
            objectId = key.objectId,
            // The form gives way to the new object's card, so back goes to the list.
            onCreated = { id ->
                backStack.pop()
                backStack.add(ObjectDetailsNavKey(id))
            },
            onBack = { backStack.pop() },
            onPickOnMap = { latitude, longitude -> backStack.add(PlacePickerNavKey(latitude, longitude)) },
        )
    }
    entry<PlacePickerNavKey> { key ->
        PlacePickerScreen(
            latitude = key.latitude,
            longitude = key.longitude,
            onDone = { backStack.pop() },
            onBack = { backStack.pop() },
        )
    }
    entry<SettingsNavKey> { SettingsScreen(onBack = { backStack.pop() }) }
}

/** Never pops the last screen: the system back gesture closes the app from there instead. */
private fun NavBackStack<NavKey>.pop() {
    if (size > 1) removeAt(lastIndex)
}

/**
 * Every [NavKey] the back stack can hold, registered for saving. Off Android there is no
 * reflection fallback to find a key's serializer, so each one is listed.
 */
private val NAV_KEYS = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(ObjectsListNavKey::class, ObjectsListNavKey.serializer())
            subclass(ObjectDetailsNavKey::class, ObjectDetailsNavKey.serializer())
            subclass(ObjectEditNavKey::class, ObjectEditNavKey.serializer())
            subclass(PhotoViewerNavKey::class, PhotoViewerNavKey.serializer())
            subclass(SettingsNavKey::class, SettingsNavKey.serializer())
            subclass(FinanceNavKey::class, FinanceNavKey.serializer())
            subclass(MaterialsNavKey::class, MaterialsNavKey.serializer())
            subclass(MapNavKey::class, MapNavKey.serializer())
            subclass(PlacePickerNavKey::class, PlacePickerNavKey.serializer())
            subclass(TasksNavKey::class, TasksNavKey.serializer())
        }
    }
}
