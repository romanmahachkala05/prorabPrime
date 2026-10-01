package ru.prorabprime.domain.usecase

import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.FieldProblem
import ru.prorabprime.domain.model.MaterialDraft
import ru.prorabprime.domain.model.MaterialId
import ru.prorabprime.domain.model.MaterialStatus
import ru.prorabprime.domain.model.ObjectField
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.asAppError
import ru.prorabprime.testing.FakeMaterialsRepository

class MaterialUseCasesTest {

    private val repository = FakeMaterialsRepository()
    private val save = SaveMaterialUseCase(repository)

    @Test
    fun `the status moves along its three states and starts over`() {
        assertThat(MaterialStatus.NOT_CHOSEN.next()).isEqualTo(MaterialStatus.CHOSEN)
        assertThat(MaterialStatus.CHOSEN.next()).isEqualTo(MaterialStatus.IN_APARTMENT)
        assertThat(MaterialStatus.IN_APARTMENT.next()).isEqualTo(MaterialStatus.NOT_CHOSEN)
    }

    @Test
    fun `a material is normalized and stored`() = runTest {
        assertThat(save.create(ObjectId("o"), MaterialDraft(" Плитка ")).isSuccess).isTrue()
        assertThat(save.update(MaterialId("m"), MaterialDraft("Плитка", MaterialStatus.CHOSEN)).isSuccess).isTrue()

        assertThat(repository.added).containsExactly(ObjectId("o") to MaterialDraft("Плитка"))
        assertThat(
            repository.updated,
        ).containsExactly(MaterialId("m") to MaterialDraft("Плитка", MaterialStatus.CHOSEN))
    }

    @Test
    fun `a blank or oversized title never reaches the repository`() = runTest {
        val blank = save.create(ObjectId("o"), MaterialDraft("  "))
        val long = save.update(MaterialId("m"), MaterialDraft("я".repeat(MaterialDraft.MAX_TITLE + 1)))

        assertThat(blank.exceptionOrNull()?.asAppError())
            .isEqualTo(AppError.Validation(persistentMapOf(ObjectField.MATERIAL_TITLE to FieldProblem.REQUIRED)))
        assertThat(long.exceptionOrNull()?.asAppError())
            .isEqualTo(AppError.Validation(persistentMapOf(ObjectField.MATERIAL_TITLE to FieldProblem.TOO_LONG)))
        assertThat(repository.added).isEmpty()
        assertThat(repository.updated).isEmpty()
    }

    @Test
    fun `deleting and adding the defaults pass through, failures included`() = runTest {
        assertThat(DeleteMaterialUseCase(repository)(MaterialId("m")).isSuccess).isTrue()
        assertThat(AddDefaultMaterialsUseCase(repository)(ObjectId("o")).isSuccess).isTrue()
        assertThat(repository.deleted).containsExactly(MaterialId("m"))
        assertThat(repository.defaultsAdded).isEqualTo(1)

        repository.writeError = AppError.NotFound
        assertThat(AddDefaultMaterialsUseCase(repository)(ObjectId("o")).exceptionOrNull()?.asAppError())
            .isEqualTo(AppError.NotFound)
    }
}
