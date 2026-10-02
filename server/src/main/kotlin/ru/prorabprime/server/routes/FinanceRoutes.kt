package ru.prorabprime.server.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import org.koin.ktor.ext.inject
import ru.prorabprime.contract.ApiParams
import ru.prorabprime.contract.ApiPaths
import ru.prorabprime.contract.ExtraWorkRequestDto
import ru.prorabprime.contract.FinanceTermsDto
import ru.prorabprime.contract.IdDto
import ru.prorabprime.contract.PaymentRequestDto
import ru.prorabprime.server.auth.owner
import ru.prorabprime.server.service.ExtraWorkService
import ru.prorabprime.server.service.FinanceService
import ru.prorabprime.server.service.PaymentService

fun Route.financeRoutes() {
    val finance by inject<FinanceService>()
    val paymentService by inject<PaymentService>()
    val extraWorkService by inject<ExtraWorkService>()

    get(ApiPaths.OBJECT_FINANCE) {
        call.respond(finance.get(call.owner, call.uuidParam(ApiParams.ID)).getOrThrow().toDto())
    }
    put(ApiPaths.OBJECT_FINANCE_TERMS) {
        val overview = finance.setTerms(
            call.owner,
            call.uuidParam(ApiParams.ID),
            call.receive<FinanceTermsDto>(),
        ).getOrThrow()
        call.respond(overview.toDto())
    }
    post(ApiPaths.OBJECT_PAYMENTS) {
        val created = paymentService.create(
            call.owner,
            call.uuidParam(ApiParams.ID),
            call.receive<PaymentRequestDto>(),
        ).getOrThrow()
        call.respond(HttpStatusCode.Created, IdDto(created.id.toString()))
    }
    get(ApiPaths.OBJECT_PAYMENT_HISTORY) {
        call.respond(paymentService.history(call.owner, call.uuidParam(ApiParams.ID)).getOrThrow().map { it.toDto() })
    }
    put(ApiPaths.PAYMENT) {
        paymentService.update(call.owner, call.uuidParam(ApiParams.ID), call.receive<PaymentRequestDto>()).getOrThrow()
        call.respond(HttpStatusCode.NoContent)
    }
    delete(ApiPaths.PAYMENT) {
        paymentService.delete(call.owner, call.uuidParam(ApiParams.ID)).getOrThrow()
        call.respond(HttpStatusCode.NoContent)
    }
    post(ApiPaths.OBJECT_EXTRA_WORKS) {
        val request = call.receive<ExtraWorkRequestDto>()
        val created = extraWorkService.create(call.owner, call.uuidParam(ApiParams.ID), request).getOrThrow()
        call.respond(HttpStatusCode.Created, IdDto(created.id.toString()))
    }
    put(ApiPaths.EXTRA_WORK) {
        extraWorkService.update(
            call.owner,
            call.uuidParam(ApiParams.ID),
            call.receive<ExtraWorkRequestDto>(),
        ).getOrThrow()
        call.respond(HttpStatusCode.NoContent)
    }
    delete(ApiPaths.EXTRA_WORK) {
        extraWorkService.delete(call.owner, call.uuidParam(ApiParams.ID)).getOrThrow()
        call.respond(HttpStatusCode.NoContent)
    }
}
