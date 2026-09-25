package ru.prorabprime.shared.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
        notifier.messages.collect { snackbarHostState.showSnackbar(it.load()) }
    }

    val backStack = rememberNavBackStack(NAV_KEYS, ObjectsListNavKey)
    Scaffold(modifier = modifier, snackbarHost = { SnackbarHost(snackbarHostState) }) {
        // No padding from this Scaffold: each screen has its own, with its own top bar.
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.pop() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<ObjectsListNavKey> {
                    ObjectsListScreen(
                        onOpenObject = { backStack.add(ObjectDetailsNavKey(it)) },
                        onCreateObject = { backStack.add(ObjectEditNavKey()) },
                        onOpenSettings = { backStack.add(SettingsNavKey) },
                    )
                }
                entry<ObjectDetailsNavKey> { key ->
                    ObjectDetailsScreen(
                        objectId = key.objectId,
                        onEdit = { backStack.add(ObjectEditNavKey(key.objectId)) },
                        onOpenPhoto = { backStack.add(PhotoViewerNavKey(key.objectId, it)) },
                        onClose = { backStack.pop() },
                    )
                }
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
                    )
                }
                entry<SettingsNavKey> { SettingsScreen(onBack = { backStack.pop() }) }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
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
        }
    }
}
