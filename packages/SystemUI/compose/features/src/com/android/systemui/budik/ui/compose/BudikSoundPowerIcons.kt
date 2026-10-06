/*
 * Copyright (C) 2026 Budík OS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.budik.ui.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Budík line icons for the volume dialog and the power menu (export/icons/ui, notification), same
 * geometry rules as [BudikIcons]: 24 dp grid, stroke 2, round caps and joins, tinted by the caller.
 */
object BudikSoundPowerIcons {
    val Ring by lazy { stroke("M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 1.5h-15z", "M10 20.5h4") }
    val Vibrate by lazy {
        stroke(
            "M10 4H14A2 2 0 0 1 16 6V18A2 2 0 0 1 14 20H10A2 2 0 0 1 8 18V6A2 2 0 0 1 10 4Z",
            "M4.5 9v6M19.5 9v6",
        )
    }
    val Mute by lazy { stroke("M4 9.5h3.5L12 5.5v13l-4.5-4H4z", "M16 9.5l5 5M21 9.5l-5 5") }
    val Volume by lazy {
        stroke(
            "M4 9.5h3.5L12 5.5v13l-4.5-4H4z",
            "M15.5 9a4 4 0 0 1 0 6",
            "M18 6.5a7.5 7.5 0 0 1 0 11",
        )
    }
    val Media by lazy {
        stroke(
            "M9 18V5.5l10-2V16",
            "M4 18A2.5 2.5 0 1 0 9 18A2.5 2.5 0 1 0 4 18Z",
            "M14 16A2.5 2.5 0 1 0 19 16A2.5 2.5 0 1 0 14 16Z",
        )
    }
    val Alarm by lazy {
        stroke(
            "M4.5 13A7.5 7.5 0 1 0 19.5 13A7.5 7.5 0 1 0 4.5 13Z",
            "M12 9.5V13l2.5 2",
            "M4 5.5 6.5 3",
            "M20 5.5 17.5 3",
        )
    }
    val Call by lazy {
        stroke(
            "M8 3.5H5.5A1.5 1.5 0 0 0 4 5c0 8.3 6.7 15 15 15a1.5 1.5 0 0 0 1.5-1.5V16l-4-1.5-2 2" +
                "a12 12 0 0 1-6.5-6.5l2-2z"
        )
    }
    val Restart by lazy { stroke("M19.5 12a7.5 7.5 0 1 1-2.2-5.3", "M19.5 4v4h-4") }
    val Screenshot by lazy { stroke("M4 9V4h5M15 4h5v5M20 15v5h-5M9 20H4v-5") }
    val Recovery by lazy {
        stroke(
            "M5.5 4.5H18.5A2 2 0 0 1 20.5 6.5V14.5A2 2 0 0 1 18.5 16.5H5.5A2 2 0 0 1 3.5 14.5" +
                "V6.5A2 2 0 0 1 5.5 4.5Z",
            "M9 20h6M12 16.5V20",
            "M7.5 10.5l2 2-2 2",
        )
    }
    val Lock by lazy {
        stroke(
            "M7 10.5H17A2 2 0 0 1 19 12.5V18.5A2 2 0 0 1 17 20.5H7A2 2 0 0 1 5 18.5V12.5" +
                "A2 2 0 0 1 7 10.5Z",
            "M8 10.5V8a4 4 0 0 1 8 0v2.5",
        )
    }
    val Emergency by lazy { stroke("M12 4v16M5.1 8l13.8 8M18.9 8 5.1 16") }
    val System by lazy {
        stroke(
            "M9.5 3.5H14.5A2.5 2.5 0 0 1 17 6V18A2.5 2.5 0 0 1 14.5 20.5H9.5A2.5 2.5 0 0 1 7 18" +
                "V6A2.5 2.5 0 0 1 9.5 3.5Z",
            "M10.5 17h3",
        )
    }
    val Back by lazy { stroke("M19 12H5M11 6l-6 6 6 6") }

    private fun stroke(vararg paths: String): ImageVector =
        ImageVector.Builder(
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            )
            .apply {
                paths.forEach {
                    addPath(
                        pathData = addPathNodes(it),
                        stroke = SolidColor(Color.White),
                        strokeLineWidth = 2f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
            .build()
}
