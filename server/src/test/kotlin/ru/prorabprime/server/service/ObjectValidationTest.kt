package ru.prorabprime.server.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import ru.prorabprime.contract.FieldErrorDto
import ru.prorabprime.contract.FieldProblemDto
import ru.prorabprime.contract.ObjectFieldDto
import ru.prorabprime.contract.ObjectLimits
import ru.prorabprime.contract.ObjectRequestDto
import ru.prorabprime.contract.ObjectStatusDto
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.ServiceException

class ObjectValidationTest {

    private fun request(
        title: String? = null,
        address: String = "Тверская, 5",
        clientPhone: String? = null,
    ) = ObjectRequestDto(title = title, address = address, status = ObjectStatusDto.PLANNED, clientPhone = clientPhone)

    private fun Result<*>.fieldErrors() =
        ((exceptionOrNull() as ServiceException).error as ServiceError.Validation).fieldErrors

    @Test
    fun `trims values and nulls blank optional fields`() {
        val fields = validateObject(
            request(title = "  ", address = " Тверская, 5 ", clientPhone = " +7 900 "),
        ).getOrThrow()

        assertThat(fields.title).isNull()
        assertThat(fields.address).isEqualTo("Тверская, 5")
        assertThat(fields.clientPhone).isEqualTo("+7 900")
        assertThat(fields.status).isEqualTo(ObjectStatusDto.PLANNED)
    }

    @Test
    fun `a blank address is required`() {
        assertThat(validateObject(request(address = "   ")).fieldErrors())
            .containsExactly(FieldErrorDto(ObjectFieldDto.ADDRESS, FieldProblemDto.REQUIRED))
    }

    @Test
    fun `every over-long field is reported`() {
        val result = validateObject(
            request(
                title = "т".repeat(ObjectLimits.TITLE + 1),
                address = "а".repeat(ObjectLimits.ADDRESS + 1),
                clientPhone = "1".repeat(ObjectLimits.CLIENT_PHONE + 1),
            ),
        )

        assertThat(result.fieldErrors()).containsExactly(
            FieldErrorDto(ObjectFieldDto.ADDRESS, FieldProblemDto.TOO_LONG),
            FieldErrorDto(ObjectFieldDto.TITLE, FieldProblemDto.TOO_LONG),
            FieldErrorDto(ObjectFieldDto.CLIENT_PHONE, FieldProblemDto.TOO_LONG),
        )
    }
}
