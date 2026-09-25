package ru.prorabprime.data.mapper

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import ru.prorabprime.contract.FieldProblemDto
import ru.prorabprime.contract.ObjectFieldDto
import ru.prorabprime.contract.ObjectLimits
import ru.prorabprime.contract.ObjectStatusDto
import ru.prorabprime.contract.SortFieldDto
import ru.prorabprime.contract.SortOrderDto
import ru.prorabprime.domain.model.ObjectDraft
import ru.prorabprime.domain.model.ObjectSort
import ru.prorabprime.domain.model.ObjectStatus

class MappersTest {

    @Test
    fun `every status survives the round trip`() {
        ObjectStatus.entries.forEach { assertThat(it.toDto().toDomain()).isEqualTo(it) }
        assertThat(
            ObjectStatusDto.entries.map {
                it.toDomain().name
            },
        ).containsExactlyElementsIn(ObjectStatusDto.entries.map { it.name })
    }

    @Test
    fun `every field and problem the server can name has a domain counterpart`() {
        assertThat(
            ObjectFieldDto.entries.map {
                it.toDomain().name
            },
        ).containsExactlyElementsIn(ObjectFieldDto.entries.map { it.name })
        assertThat(
            FieldProblemDto.entries.map {
                it.toDomain().name
            },
        ).containsExactlyElementsIn(FieldProblemDto.entries.map { it.name })
    }

    @Test
    fun `sort options ask for the documented parameters`() {
        assertThat(ObjectSort.ADDRESS_ASC.toQuery()).isEqualTo(SortFieldDto.ADDRESS to SortOrderDto.ASC)
        assertThat(ObjectSort.ADDRESS_DESC.toQuery()).isEqualTo(SortFieldDto.ADDRESS to SortOrderDto.DESC)
        assertThat(ObjectSort.CREATED_NEWEST.toQuery()).isEqualTo(SortFieldDto.CREATED to SortOrderDto.DESC)
        assertThat(ObjectSort.UPDATED_NEWEST.toQuery()).isEqualTo(SortFieldDto.UPDATED to SortOrderDto.DESC)
    }

    @Test
    fun `a draft becomes a request field for field`() {
        val draft = ObjectDraft("Кухня", "Тверская, 5", ObjectStatus.PAUSED, "Иван", "+7 900", "ключи у соседа")

        val request = draft.toRequestDto()

        assertThat(request.title).isEqualTo("Кухня")
        assertThat(request.status).isEqualTo(ObjectStatusDto.PAUSED)
        assertThat(request.notes).isEqualTo("ключи у соседа")
    }

    /** The domain repeats the server's column sizes because it cannot see :api-contract. */
    @Test
    fun `the domain's field limits match the contract's`() {
        assertThat(ObjectDraft.MAX_TITLE).isEqualTo(ObjectLimits.TITLE)
        assertThat(ObjectDraft.MAX_ADDRESS).isEqualTo(ObjectLimits.ADDRESS)
        assertThat(ObjectDraft.MAX_CLIENT_NAME).isEqualTo(ObjectLimits.CLIENT_NAME)
        assertThat(ObjectDraft.MAX_CLIENT_PHONE).isEqualTo(ObjectLimits.CLIENT_PHONE)
    }
}
