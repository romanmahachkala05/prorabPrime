package ru.prorabprime.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ObjectDraftTest {

    @Test
    fun `normalizing trims every field and nulls blank optional ones`() {
        val draft = ObjectDraft(
            title = "  ",
            address = "  ул. Ленина, 1 ",
            clientName = " Иван ",
            clientPhone = "",
            notes = "\n",
        )

        assertThat(draft.normalized()).isEqualTo(
            ObjectDraft(title = null, address = "ул. Ленина, 1", clientName = "Иван", clientPhone = null, notes = null),
        )
    }

    @Test
    fun `a draft with an address and nothing too long is valid`() {
        assertThat(ObjectDraft(address = "Тверская, 5").validate()).isEmpty()
    }

    @Test
    fun `a blank address is required`() {
        assertThat(ObjectDraft(address = " ").validate()).containsExactly(ObjectField.ADDRESS, FieldProblem.REQUIRED)
    }

    @Test
    fun `values over the column sizes are too long`() {
        val draft = ObjectDraft(
            title = "т".repeat(ObjectDraft.MAX_TITLE + 1),
            address = "а".repeat(ObjectDraft.MAX_ADDRESS + 1),
            clientName = "к".repeat(ObjectDraft.MAX_CLIENT_NAME + 1),
            clientPhone = "1".repeat(ObjectDraft.MAX_CLIENT_PHONE + 1),
        )

        assertThat(draft.validate()).containsExactly(
            ObjectField.TITLE,
            FieldProblem.TOO_LONG,
            ObjectField.ADDRESS,
            FieldProblem.TOO_LONG,
            ObjectField.CLIENT_NAME,
            FieldProblem.TOO_LONG,
            ObjectField.CLIENT_PHONE,
            FieldProblem.TOO_LONG,
        )
    }

    @Test
    fun `values exactly at the column sizes are accepted`() {
        val draft = ObjectDraft(
            title = "т".repeat(ObjectDraft.MAX_TITLE),
            address = "а".repeat(ObjectDraft.MAX_ADDRESS),
            clientName = "к".repeat(ObjectDraft.MAX_CLIENT_NAME),
            clientPhone = "1".repeat(ObjectDraft.MAX_CLIENT_PHONE),
        )

        assertThat(draft.validate()).isEmpty()
    }
}
