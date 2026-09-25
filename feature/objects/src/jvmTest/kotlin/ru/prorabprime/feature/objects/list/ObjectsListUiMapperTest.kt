package ru.prorabprime.feature.objects.list

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import ru.prorabprime.domain.model.ServerFilePath
import ru.prorabprime.testing.anObjectSummary

class ObjectsListUiMapperTest {

    @Test
    fun `an object with a title shows the address underneath`() {
        val card = anObjectSummary(title = "Кухня", address = "Тверская, 5").toCardUi()

        assertThat(card.title).isEqualTo("Кухня")
        assertThat(card.address).isEqualTo("Тверская, 5")
    }

    @Test
    fun `an object without a title shows the address once`() {
        val card = anObjectSummary(title = null, address = "Тверская, 5").toCardUi()

        assertThat(card.title).isEqualTo("Тверская, 5")
        assertThat(card.address).isNull()
    }

    @Test
    fun `the cover and photo count carry over`() {
        val card = anObjectSummary(coverThumbPath = "/files/o/p_thumb.jpg", photoCount = 3).toCardUi()

        assertThat(card.cover).isEqualTo(ServerFilePath("/files/o/p_thumb.jpg"))
        assertThat(card.photoCount).isEqualTo(3)
    }
}
