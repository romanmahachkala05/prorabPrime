package ru.prorabprime.feature.objects.edit

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The form: creates an object when [objectId] is null, edits that object otherwise. */
@Serializable
data class ObjectEditNavKey(
    val objectId: String? = null,
) : NavKey
