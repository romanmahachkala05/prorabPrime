package ru.prorabprime.feature.finance

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data class FinanceNavKey(
    val objectId: String,
) : NavKey
