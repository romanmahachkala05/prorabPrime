package ru.prorabprime.feature.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import ru.prorabprime.domain.model.ServerSettings
import ru.prorabprime.ui.UiText

class SettingsStateHolderTest {

    private val holder = SettingsStateHolder()

    @Test
    fun `starts loading`() {
        assertThat(holder.state.value.status).isEqualTo(SettingsStatus.Loading)
    }

    @Test
    fun `showing settings fills the fields and shows the form`() {
        holder.showSettings(ServerSettings("http://h:1", "t"))

        assertThat(holder.state.value).isEqualTo(
            SettingsState(status = SettingsStatus.Content, baseUrl = "http://h:1", apiToken = "t"),
        )
    }

    @Test
    fun `editing the address clears its error and the last check`() {
        holder.showAddressError(UiText.Raw("bad"))
        holder.setCheck(ConnectionCheck.Succeeded)

        holder.setBaseUrl("http://h:2")

        assertThat(holder.state.value.addressError).isNull()
        assertThat(holder.state.value.check).isEqualTo(ConnectionCheck.Idle)
    }

    @Test
    fun `editing the token clears the last check`() {
        holder.setCheck(ConnectionCheck.Failed(UiText.Raw("no")))

        holder.setToken("new")

        assertThat(holder.state.value.check).isEqualTo(ConnectionCheck.Idle)
        assertThat(holder.state.value.apiToken).isEqualTo("new")
    }
}
