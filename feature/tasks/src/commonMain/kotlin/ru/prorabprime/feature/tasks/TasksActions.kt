package ru.prorabprime.feature.tasks

import ru.prorabprime.domain.usecase.DeleteTaskUseCase
import ru.prorabprime.domain.usecase.ObserveDayTasksUseCase
import ru.prorabprime.domain.usecase.ObserveOverdueTasksUseCase
import ru.prorabprime.domain.usecase.SaveTaskUseCase

/** The use cases the tasks screen calls, in one collaborator to keep the ViewModel's constructor short. */
internal class TasksActions(
    val observeDay: ObserveDayTasksUseCase,
    val observeOverdue: ObserveOverdueTasksUseCase,
    val saveTask: SaveTaskUseCase,
    val deleteTask: DeleteTaskUseCase,
)
