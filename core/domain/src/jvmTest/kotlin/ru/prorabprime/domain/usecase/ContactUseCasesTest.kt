package ru.prorabprime.domain.usecase

import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.ContactDraft
import ru.prorabprime.domain.model.ContactId
import ru.prorabprime.domain.model.ContactRole
import ru.prorabprime.domain.model.FieldProblem
import ru.prorabprime.domain.model.ObjectField
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.asAppError
import ru.prorabprime.testing.FakeContactsRepository

class ContactUseCasesTest {

    private val repository = FakeContactsRepository()
    private val save = SaveContactUseCase(repository)
    private val delete = DeleteContactUseCase(repository)

    @Test
    fun `creating stores the normalized draft`() = runTest {
        val result = save.create(ObjectId("o"), ContactDraft(" Анна ", " ", ContactRole.CLIENT))

        assertThat(result.isSuccess).isTrue()
        assertThat(repository.created)
            .containsExactly(ObjectId("o") to ContactDraft("Анна", null, ContactRole.CLIENT))
    }

    @Test
    fun `a blank name fails without reaching the repository`() = runTest {
        val created = save.create(ObjectId("o"), ContactDraft(name = " "))
        val updated = save.update(ContactId("c"), ContactDraft(name = ""))

        val expected = AppError.Validation(persistentMapOf(ObjectField.CONTACT_NAME to FieldProblem.REQUIRED))
        assertThat(created.exceptionOrNull()?.asAppError()).isEqualTo(expected)
        assertThat(updated.exceptionOrNull()?.asAppError()).isEqualTo(expected)
        assertThat(repository.created).isEmpty()
        assertThat(repository.updated).isEmpty()
    }

    @Test
    fun `an oversized phone is too long`() = runTest {
        val result = save.create(ObjectId("o"), ContactDraft("Анна", "1".repeat(ContactDraft.MAX_PHONE + 1)))

        assertThat(result.exceptionOrNull()?.asAppError()).isEqualTo(
            AppError.Validation(persistentMapOf(ObjectField.CONTACT_PHONE to FieldProblem.TOO_LONG)),
        )
    }

    @Test
    fun `updating and deleting pass through, failures included`() = runTest {
        assertThat(save.update(ContactId("c"), ContactDraft("Анна")).isSuccess).isTrue()
        assertThat(delete(ContactId("c")).isSuccess).isTrue()
        assertThat(repository.updated).containsExactly(ContactId("c") to ContactDraft("Анна"))
        assertThat(repository.deleted).containsExactly(ContactId("c"))

        repository.error = AppError.NotFound
        assertThat(delete(ContactId("c")).exceptionOrNull()?.asAppError()).isEqualTo(AppError.NotFound)
    }
}
