package ru.prorabprime.feature.finance

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test
import ru.prorabprime.designsystem.theme.ProrabTheme
import ru.prorabprime.domain.model.ExtraWork
import ru.prorabprime.domain.model.ExtraWorkId
import ru.prorabprime.domain.model.ExtraWorkStatus
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.domain.model.Payment
import ru.prorabprime.domain.model.PaymentId
import ru.prorabprime.domain.model.PaymentMethod
import ru.prorabprime.domain.model.PaymentSide
import ru.prorabprime.ui.UiText

@OptIn(ExperimentalTestApi::class)
class FinanceContentTest {

    private val events = mutableListOf<FinanceEvent>()
    private var backs = 0

    private val payment = Payment(
        PaymentId("p1"),
        PaymentSide.CLIENT,
        300_000,
        PaymentMethod.TRANSFER,
        LocalDay.of(2026, 9, 25),
        "аванс",
    )
    private val work = ExtraWork(ExtraWorkId("w1"), "Штробление", 50_000, ExtraWorkStatus.AGREED)

    private val finance = FinanceUi(
        clientTotalKopecks = 1_000_000,
        crewTotalKopecks = null,
        client = SideCardUi("10 500 ₽", "9 999 ₽", "7 500 ₽", isOverpaid = false),
        crew = SideCardUi(null, "0 ₽", null, isOverpaid = false),
        clientPayments = persistentListOf(PaymentUi(payment, "3 000 ₽", "25.09.2026")),
        extraWorks = persistentListOf(ExtraWorkUi(work, "500 ₽")),
        extrasAgreed = "500 ₽",
        extrasPending = "700 ₽",
    )

    private fun androidx.compose.ui.test.ComposeUiTest.show(state: FinanceState) = setContent {
        ProrabTheme { FinanceContent(state, { events += it }, { backs++ }) }
    }

    @Test
    fun `both sides, the payments and the extra works are shown`() = runComposeUiTest {
        show(FinanceState(status = FinanceStatus.Content, finance = finance))

        onNodeWithText("Заказчик").assertIsDisplayed()
        onNodeWithText("10 500 ₽").assertExists()
        onNodeWithText("не указана").assertExists()
        onNodeWithText("3 000 ₽").assertExists()
        onNodeWithText("Бригада").assertExists()
        onNodeWithText("Штробление").assertExists()
        onNodeWithText("Согласовано").assertExists()
    }

    @Test
    fun `tapping a payment opens it and adding sends the side`() = runComposeUiTest {
        show(FinanceState(status = FinanceStatus.Content, finance = finance))

        onNodeWithText("3 000 ₽").performScrollTo().performClick()
        onAllNodesWithText("Добавить платёж")[1].performScrollTo().performClick()

        assertThat(events).containsExactly(PaymentEvent.Edit("p1"), PaymentEvent.Add(PaymentSide.CREW)).inOrder()
    }

    @Test
    fun `extra works open and add through their events`() = runComposeUiTest {
        show(FinanceState(status = FinanceStatus.Content, finance = finance))

        onNodeWithText("Штробление").performScrollTo().performClick()
        onNodeWithContentDescription("Добавить работу").performScrollTo().performClick()

        assertThat(events).containsExactly(WorkEvent.Edit("w1"), WorkEvent.Add).inOrder()
    }

    @Test
    fun `an open payment form shows its fields and saves`() = runComposeUiTest {
        val editor = FinanceEditorUi.Payment(amountText = "100", day = LocalDay.of(2026, 9, 25))
        show(FinanceState(status = FinanceStatus.Content, finance = finance, editor = editor))

        onNodeWithText("Сумма, ₽ *").assertIsDisplayed()
        onNodeWithText("25.09.2026").assertIsDisplayed()
        onNodeWithText("Сохранить").performClick()

        assertThat(events).containsExactly(PaymentEvent.Save)
    }

    @Test
    fun `an error offers a retry and the history button opens the sheet`() = runComposeUiTest {
        show(FinanceState(status = FinanceStatus.Error(UiText.Raw("Сервер не отвечает"))))

        onNodeWithText("Сервер не отвечает").assertIsDisplayed()
        onNodeWithText("Повторить").performClick()

        assertThat(events).containsExactly(FinanceEvent.Retry)
    }

    @Test
    fun `the history button asks for the sheet and back leaves`() = runComposeUiTest {
        show(FinanceState(status = FinanceStatus.Content, finance = finance))

        onNodeWithContentDescription("История платежей").performClick()
        onNodeWithContentDescription("Назад").performClick()

        assertThat(events).containsExactly(FinanceEvent.ShowHistory)
        assertThat(backs).isEqualTo(1)
    }
}
