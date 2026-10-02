package ru.prorabprime.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * UI text that does not know where it will be rendered; only a Composable resolves it.
 * `@Immutable` because the compiler cannot infer stability for a sealed interface.
 */
@Immutable
sealed interface UiText {
    data class Raw(
        val value: String,
    ) : UiText

    data class Resource(
        val id: StringResource,
        /** [ImmutableList], not `List`, so the `@Immutable` above is a fact rather than a claim. */
        val args: ImmutableList<Any> = persistentListOf(),
    ) : UiText
}

@Suppress("SpreadOperator") // How `stringResource` takes format arguments; a few at most.
@Composable
fun UiText.resolve(): String = when (this) {
    is UiText.Raw -> value
    is UiText.Resource -> stringResource(id, *args.toTypedArray())
}

/** For resolving outside composition, such as in a `LaunchedEffect`. */
@Suppress("SpreadOperator")
suspend fun UiText.load(): String = when (this) {
    is UiText.Raw -> value
    is UiText.Resource -> getString(id, *args.toTypedArray())
}
