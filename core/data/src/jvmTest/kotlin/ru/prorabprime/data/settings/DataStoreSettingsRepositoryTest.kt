package ru.prorabprime.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.prorabprime.domain.model.ObjectSort
import ru.prorabprime.domain.model.ServerSettings

class DataStoreSettingsRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val defaults = ServerSettings("http://192.168.1.10:8080", "default-token")

    private fun repository(scope: kotlinx.coroutines.CoroutineScope) = DataStoreSettingsRepository(
        PreferenceDataStoreFactory.createWithPath(scope = scope) {
            folder.root.resolve("settings.preferences_pb").toOkioPath()
        },
        defaults,
    )

    @Test
    fun `nothing saved reads as the build defaults and the default sort`() = runTest {
        val settings = repository(backgroundScope)

        assertThat(settings.serverSettings.first()).isEqualTo(defaults)
        assertThat(settings.objectSort.first()).isEqualTo(ObjectSort.DEFAULT)
    }

    @Test
    fun `saved values replace the defaults`() = runTest {
        val settings = repository(backgroundScope)

        settings.saveServerSettings(ServerSettings("http://10.0.0.2:8080", "mine"))
        settings.saveObjectSort(ObjectSort.ADDRESS_ASC)

        assertThat(settings.serverSettings.first()).isEqualTo(ServerSettings("http://10.0.0.2:8080", "mine"))
        assertThat(settings.objectSort.first()).isEqualTo(ObjectSort.ADDRESS_ASC)
    }
}
