package ru.prorabprime.feature.sync

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import ru.prorabprime.domain.model.ChangeAction
import ru.prorabprime.domain.model.ChangeKind
import ru.prorabprime.domain.model.FailedChange
import ru.prorabprime.feature.sync.resources.Res
import ru.prorabprime.feature.sync.resources.sync_action_create
import ru.prorabprime.feature.sync.resources.sync_action_delete
import ru.prorabprime.feature.sync.resources.sync_action_update
import ru.prorabprime.feature.sync.resources.sync_kind_contact
import ru.prorabprime.feature.sync.resources.sync_kind_cover
import ru.prorabprime.feature.sync.resources.sync_kind_extra_work
import ru.prorabprime.feature.sync.resources.sync_kind_material
import ru.prorabprime.feature.sync.resources.sync_kind_object
import ru.prorabprime.feature.sync.resources.sync_kind_payment
import ru.prorabprime.feature.sync.resources.sync_kind_photo
import ru.prorabprime.feature.sync.resources.sync_kind_task
import ru.prorabprime.feature.sync.resources.sync_kind_terms
import ru.prorabprime.feature.sync.resources.sync_reason_not_found
import ru.prorabprime.feature.sync.resources.sync_reason_photo_too_large
import ru.prorabprime.feature.sync.resources.sync_reason_photo_unsupported
import ru.prorabprime.feature.sync.resources.sync_reason_server
import ru.prorabprime.feature.sync.resources.sync_reason_unknown
import ru.prorabprime.feature.sync.resources.sync_reason_validation

private val ChangeAction.label: StringResource
    get() = when (this) {
        ChangeAction.CREATE -> Res.string.sync_action_create
        ChangeAction.UPDATE -> Res.string.sync_action_update
        ChangeAction.DELETE -> Res.string.sync_action_delete
    }

private val ChangeKind.label: StringResource
    get() = when (this) {
        ChangeKind.OBJECT -> Res.string.sync_kind_object
        ChangeKind.COVER -> Res.string.sync_kind_cover
        ChangeKind.PHOTO -> Res.string.sync_kind_photo
        ChangeKind.CONTACT -> Res.string.sync_kind_contact
        ChangeKind.TERMS -> Res.string.sync_kind_terms
        ChangeKind.PAYMENT -> Res.string.sync_kind_payment
        ChangeKind.EXTRA_WORK -> Res.string.sync_kind_extra_work
        ChangeKind.MATERIAL -> Res.string.sync_kind_material
        ChangeKind.TASK -> Res.string.sync_kind_task
    }

/** "Создание: объект «Кухня»". */
@Composable
internal fun describe(change: FailedChange): String {
    val what = stringResource(change.kind.label)
    val name = change.title?.let { " «$it»" }.orEmpty()
    return "${stringResource(change.action.label)}: $what$name"
}

@Composable
internal fun reasonText(reason: String): String = when {
    reason == "validation" -> stringResource(Res.string.sync_reason_validation)
    reason == "not_found" -> stringResource(Res.string.sync_reason_not_found)
    reason == "photo_too_large" -> stringResource(Res.string.sync_reason_photo_too_large)
    reason == "photo_unsupported" -> stringResource(Res.string.sync_reason_photo_unsupported)
    reason.startsWith("server_") -> stringResource(Res.string.sync_reason_server, reason.removePrefix("server_"))
    else -> stringResource(Res.string.sync_reason_unknown)
}
