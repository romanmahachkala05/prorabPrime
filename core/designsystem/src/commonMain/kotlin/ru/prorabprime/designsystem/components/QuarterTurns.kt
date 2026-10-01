package ru.prorabprime.designsystem.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints

/**
 * Shows the content turned clockwise by [turns] quarter turns. A sideways turn swaps the sides it
 * is laid out in, so a picture turned on its side still fits the space it was given.
 */
fun Modifier.quarterTurns(turns: Int): Modifier {
    val normalized = turns.mod(FULL_TURN)
    return if (normalized == 0) {
        this
    } else {
        layout { measurable, constraints ->
            val sideways = normalized % 2 == 1
            val placeable = measurable.measure(if (sideways) constraints.swapped() else constraints)
            val width = if (sideways) placeable.height else placeable.width
            val height = if (sideways) placeable.width else placeable.height
            layout(width, height) {
                placeable.placeWithLayer((width - placeable.width) / 2, (height - placeable.height) / 2) {
                    rotationZ = QUARTER * normalized
                }
            }
        }
    }
}

private fun Constraints.swapped() = Constraints(
    minWidth = minHeight,
    maxWidth = maxHeight,
    minHeight = minWidth,
    maxHeight = maxWidth,
)

private const val FULL_TURN = 4
private const val QUARTER = 90f
