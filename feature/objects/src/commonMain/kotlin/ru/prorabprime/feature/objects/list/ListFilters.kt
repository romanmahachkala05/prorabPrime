package ru.prorabprime.feature.objects.list

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import ru.prorabprime.designsystem.components.FilterChip
import ru.prorabprime.designsystem.components.SearchField
import ru.prorabprime.designsystem.icons.ProrabIcons
import ru.prorabprime.designsystem.theme.Spacing
import ru.prorabprime.domain.model.ObjectSort
import ru.prorabprime.domain.model.ObjectStatus
import ru.prorabprime.feature.objects.components.label
import ru.prorabprime.feature.objects.resources.Res
import ru.prorabprime.feature.objects.resources.objectslist_clear_search
import ru.prorabprime.feature.objects.resources.objectslist_filter_all
import ru.prorabprime.feature.objects.resources.objectslist_search
import ru.prorabprime.feature.objects.resources.objectslist_sort_address_asc
import ru.prorabprime.feature.objects.resources.objectslist_sort_address_desc
import ru.prorabprime.feature.objects.resources.objectslist_sort_created
import ru.prorabprime.feature.objects.resources.objectslist_sort_updated

/** The status chips, scrolling sideways, and the sort icon that stays at the end. */
@Composable
internal fun FilterRow(state: ObjectsListState, onEvent: (ObjectsListEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            FilterChip(
                selected = state.statuses.isEmpty(),
                onClick = { onEvent(ObjectsListEvent.FiltersCleared) },
                label = { Text(stringResource(Res.string.objectslist_filter_all)) },
            )
            ObjectStatus.entries.forEach { status ->
                FilterChip(
                    selected = status in state.statuses,
                    onClick = { onEvent(ObjectsListEvent.StatusToggled(status)) },
                    label = { Text(stringResource(status.label)) },
                )
            }
        }
        SortMenu(state.sort, onSelect = { onEvent(ObjectsListEvent.SortSelected(it)) })
    }
}

@Composable
internal fun SearchField(
    text: String,
    onEvent: (ObjectsListEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    SearchField(
        value = text,
        onValueChange = { onEvent(ObjectsListEvent.SearchChanged(it)) },
        placeholder = stringResource(Res.string.objectslist_search),
        clearDescription = stringResource(Res.string.objectslist_clear_search),
        modifier = modifier,
        autoFocus = true,
    )
}

internal val ObjectSort.label: StringResource
    get() = when (this) {
        ObjectSort.ADDRESS_ASC -> Res.string.objectslist_sort_address_asc
        ObjectSort.ADDRESS_DESC -> Res.string.objectslist_sort_address_desc
        ObjectSort.CREATED_NEWEST -> Res.string.objectslist_sort_created
        ObjectSort.UPDATED_NEWEST -> Res.string.objectslist_sort_updated
    }

@Composable
private fun SortMenu(selected: ObjectSort, onSelect: (ObjectSort) -> Unit) {
    // Whether the menu is open is view state with no meaning beyond this composable.
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(ProrabIcons.Sort, contentDescription = stringResource(selected.label))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ObjectSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(stringResource(sort.label)) },
                    leadingIcon = { if (sort == selected) Icon(Icons.Default.Check, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onSelect(sort)
                    },
                )
            }
        }
    }
}
