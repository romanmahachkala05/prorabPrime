package ru.prorabprime.feature.finance

import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineScope
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.ExtraWorkDraft
import ru.prorabprime.domain.model.ExtraWorkId
import ru.prorabprime.domain.model.Money
import ru.prorabprime.domain.model.ObjectField
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.asAppError
import ru.prorabprime.domain.usecase.DeleteExtraWorkUseCase
import ru.prorabprime.domain.usecase.SaveExtraWorkUseCase
import ru.prorabprime.feature.finance.resources.Res
import ru.prorabprime.feature.finance.resources.finance_delete
import ru.prorabprime.feature.finance.resources.finance_delete_extra_message
import ru.prorabprime.feature.finance.resources.finance_delete_extra_title
import ru.prorabprime.feature.finance.resources.finance_saved
import ru.prorabprime.ui.DialogModel
import ru.prorabprime.ui.UiText
import ru.prorabprime.ui.launchCatching

/** The extra work form: opening, typing, saving, and asking before a delete. */
internal class WorkEditorHandler(
    private val objectId: ObjectId,
    private val scope: CoroutineScope,
    private val stateHolder: IFinanceStateHolder,
    private val errorHandler: IFinanceErrorHandler,
    private val saveWork: SaveExtraWorkUseCase,
    private val deleteWork: DeleteExtraWorkUseCase,
) {
    fun onEvent(event: WorkEvent) {
        when (event) {
            WorkEvent.Add -> stateHolder.openEditor(FinanceEditorUi.Work())

            is WorkEvent.Edit -> open(event.workId)

            is WorkEvent.TitleChanged -> edit {
                it.copy(title = event.text, errors = it.without(ObjectField.WORK_TITLE))
            }

            is WorkEvent.AmountChanged -> edit {
                it.copy(amountText = event.text, errors = it.without(ObjectField.WORK_AMOUNT))
            }

            is WorkEvent.StatusChanged -> edit { it.copy(status = event.status) }

            WorkEvent.Save -> save()

            is WorkEvent.Delete -> {
                stateHolder.closeEditor()
                stateHolder.askToConfirm(DELETE_DIALOG, FinanceAction.DeleteWork(event.workId))
            }

            WorkEvent.Dismiss -> stateHolder.closeEditor()
        }
    }

    /** Runs once the user confirmed [FinanceAction.DeleteWork]. */
    fun delete(workId: String) {
        scope.launchCatching(TAG, onFailure = { errorHandler.onActionFailure(it.asAppError()) }) {
            deleteWork(ExtraWorkId(workId)).onFailure { errorHandler.onActionFailure(it.asAppError()) }
        }
    }

    private fun edit(transform: (FinanceEditorUi.Work) -> FinanceEditorUi.Work) =
        stateHolder.editForm { (it as? FinanceEditorUi.Work)?.let(transform) ?: it }

    private fun FinanceEditorUi.Work.without(field: ObjectField) = (errors - field).toImmutableMap()

    private fun open(workId: String) {
        val work = stateHolder.state.value.finance?.extraWorks?.find { it.id == workId }?.work ?: return
        stateHolder.openEditor(
            FinanceEditorUi.Work(
                workId = workId,
                title = work.title,
                amountText = Money.toInput(work.amountKopecks),
                status = work.status,
            ),
        )
    }

    private fun save() {
        val editor = stateHolder.state.value.editor as? FinanceEditorUi.Work ?: return
        if (editor.isSaving) return
        val draft = ExtraWorkDraft(editor.title, Money.parseRubles(editor.amountText), editor.status)
        edit { it.copy(isSaving = true) }
        scope.launchCatching(TAG, onFailure = { onSaveFailure(it.asAppError()) }) {
            val result = if (editor.workId == null) {
                saveWork.create(objectId, draft)
            } else {
                saveWork.update(ExtraWorkId(editor.workId), draft)
            }
            result
                .onSuccess {
                    stateHolder.closeEditor()
                    errorHandler.onSaved(UiText.Resource(Res.string.finance_saved))
                }
                .onFailure { onSaveFailure(it.asAppError()) }
        }
    }

    private suspend fun onSaveFailure(error: AppError) {
        edit { it.copy(isSaving = false) }
        if (error is AppError.Validation) {
            edit { it.copy(errors = error.fieldErrors) }
        } else {
            errorHandler.onActionFailure(error)
        }
    }

    private companion object {
        const val TAG = "WorkEditorHandler"
        val DELETE_DIALOG = DialogModel.Confirmation(
            title = UiText.Resource(Res.string.finance_delete_extra_title),
            message = UiText.Resource(Res.string.finance_delete_extra_message),
            confirmLabel = UiText.Resource(Res.string.finance_delete),
            destructive = true,
        )
    }
}
