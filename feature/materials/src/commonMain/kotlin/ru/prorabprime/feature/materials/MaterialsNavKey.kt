package ru.prorabprime.feature.materials

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data class MaterialsNavKey(
    val objectId: String,
) : NavKey
