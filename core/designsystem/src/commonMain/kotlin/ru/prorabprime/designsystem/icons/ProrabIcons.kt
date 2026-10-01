package ru.prorabprime.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The few icons the core set lacks, drawn from the Material path data. */
object ProrabIcons {

    /** A clock: waiting to be sent. */
    val Schedule: ImageVector by lazy {
        icon(
            "Schedule",
            "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2zM12,20c-4.42,0 " +
                "-8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8zM12.5,7H11v6l5.25,3.15 0.75,-1.23 -4.5,-2.67z",
        )
    }

    val Camera: ImageVector by lazy {
        icon(
            "Camera",
            "M12,12m-3.2,0a3.2,3.2 0 1,1 6.4,0a3.2,3.2 0 1,1 -6.4,0M9,2L7.17,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 " +
                "0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2h-3.17L15,2H9zM12,17c-2.76,0 -5,-2.24 " +
                "-5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5z",
        )
    }

    val Map: ImageVector by lazy {
        icon(
            "Map",
            "M20.5,3l-0.16,0.03L15,5.1 9,3 3.36,4.9c-0.21,0.07 -0.36,0.25 -0.36,0.48V20.5c0,0.28 0.22,0.5 " +
                "0.5,0.5l0.16,-0.03L9,18.9l6,2.1 5.64,-1.9c0.21,-0.07 0.36,-0.25 0.36,-0.48V3.5c0,-0.28 " +
                "-0.22,-0.5 -0.5,-0.5zM15,19l-6,-2.11V5l6,2.11V19z",
        )
    }

    val Wallet: ImageVector by lazy {
        icon(
            "Wallet",
            "M21,18v1c0,1.1 -0.9,2 -2,2H5c-1.11,0 -2,-0.9 -2,-2V5c0,-1.1 0.89,-2 2,-2h14c1.1,0 2,0.9 2,2v1h-9c" +
                "-1.11,0 -2,0.9 -2,2v8c0,1.1 0.89,2 2,2h9zM12,16h10V8H12v8zM16,13.5c-0.83,0 -1.5,-0.67 -1.5,-1.5s" +
                "0.67,-1.5 1.5,-1.5 1.5,0.67 1.5,1.5 -0.67,1.5 -1.5,1.5z",
        )
    }

    val CheckCircle: ImageVector by lazy {
        icon(
            "CheckCircle",
            "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM10,17l-5,-5 1.41,-1.41L10," +
                "14.17l7.59,-7.59L19,8l-9,9z",
        )
    }

    val Tasks: ImageVector by lazy {
        icon(
            "Tasks",
            "M19,3h-4.18C14.4,1.84 13.3,1 12,1c-1.3,0 -2.4,0.84 -2.82,2H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2," +
                "2h14c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2zM12,3c0.55,0 1,0.45 1,1s-0.45,1 -1,1 -1,-0.45 " +
                "-1,-1 0.45,-1 1,-1zM14,17H7v-2h7v2zM17,13H7v-2h10v2zM17,9H7V7h10v2z",
        )
    }

    val Alarm: ImageVector by lazy {
        icon(
            "Alarm",
            "M22,5.72l-4.6,-3.86 -1.29,1.53 4.6,3.86L22,5.72zM7.88,3.39L6.6,1.86 2,5.71l1.29,1.53 4.59,-3.85z" +
                "M12.5,8H11v6l4.75,2.85 0.75,-1.23 -4,-2.37V8zM12,4c-4.97,0 -9,4.03 -9,9s4.02,9 9,9c4.97,0 9,-4.03 " +
                "9,-9s-4.03,-9 -9,-9zM12,20c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7 -3.13,7 -7,7z",
        )
    }

    private fun icon(name: String, pathData: String): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black)).build()
}
