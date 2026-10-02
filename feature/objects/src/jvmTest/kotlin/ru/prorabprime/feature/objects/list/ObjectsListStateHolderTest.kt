package ru.prorabprime.feature.objects.list

import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test
import ru.prorabprime.domain.model.ObjectStatus
import ru.prorabprime.ui.UiText

class ObjectsListStateHolderTest {

    private val holder = ObjectsListStateHolder()
    private val card = ObjectCardUi("1", "Тверская, 5", null, ObjectStatus.DONE, 0, null)

    @Test
    fun `objects are content and end a refresh`() {
        holder.setRefreshing(true)

        holder.showObjects(persistentListOf(card))

        assertThat(holder.state.value.status).isEqualTo(ObjectsListStatus.Content)
        assertThat(holder.state.value.isRefreshing).isFalse()
    }

    @Test
    fun `no objects without a search is empty, with one is nothing found`() {
        holder.showObjects(persistentListOf())
        assertThat(holder.state.value.status).isEqualTo(ObjectsListStatus.Empty)

        holder.setSearch("лен")
        holder.showObjects(persistentListOf())
        assertThat(holder.state.value.status).isEqualTo(ObjectsListStatus.NothingFound)
    }

    @Test
    fun `an error ends a refresh`() {
        holder.setRefreshing(true)

        holder.showError(UiText.Raw("нет сети"))

        assertThat(holder.state.value.status).isEqualTo(ObjectsListStatus.Error(UiText.Raw("нет сети")))
        assertThat(holder.state.value.isRefreshing).isFalse()
    }
}
