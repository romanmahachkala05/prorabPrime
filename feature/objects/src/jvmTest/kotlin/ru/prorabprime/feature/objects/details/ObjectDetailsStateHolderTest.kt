package ru.prorabprime.feature.objects.details

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import ru.prorabprime.domain.model.LocalImageRef
import ru.prorabprime.ui.DialogModel
import ru.prorabprime.ui.UiText

class ObjectDetailsStateHolderTest {

    private val holder = ObjectDetailsStateHolder()
    private val image = LocalImageRef("content://a")

    @Test
    fun `an upload goes from uploading to failed and back`() {
        holder.startUpload(image)
        assertThat(holder.state.value.uploads.single().isFailed).isFalse()

        holder.failUpload(image, UiText.Raw("нет сети"))
        assertThat(holder.state.value.uploads.single().failure).isEqualTo(UiText.Raw("нет сети"))

        holder.startUpload(image)
        assertThat(holder.state.value.uploads.single().isFailed).isFalse()
    }

    @Test
    fun `removing an upload leaves the others`() {
        holder.startUpload(image)
        holder.startUpload(LocalImageRef("content://b"))

        holder.removeUpload(image)

        assertThat(holder.state.value.uploads.map { it.image.value }).containsExactly("content://b")
    }

    @Test
    fun `dismissing a dialog forgets its action`() {
        holder.askToConfirm(
            DialogModel.Confirmation(UiText.Raw("?"), UiText.Raw("?"), UiText.Raw("ok")),
            ObjectDetailsAction.DeleteObject,
        )

        holder.dismissDialog()

        assertThat(holder.state.value.dialog).isNull()
        assertThat(holder.state.value.pendingAction).isNull()
    }
}
