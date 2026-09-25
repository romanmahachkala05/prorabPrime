package ru.prorabprime.feature.objects.details

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data class ObjectDetailsNavKey(
    val objectId: String,
) : NavKey
