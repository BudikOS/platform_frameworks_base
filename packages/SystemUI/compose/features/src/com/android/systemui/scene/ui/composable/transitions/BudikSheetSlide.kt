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

package com.android.systemui.scene.ui.composable.transitions

import com.android.compose.animation.scene.Edge
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.TransitionBuilder
import com.android.compose.animation.scene.UserActionDistance

/**
 * Budík: the full screen shade sheet slides in from the top edge and follows the finger, like on
 * iOS. Only its placement changes per frame: the sheet keeps its size, so nothing is re-measured
 * or re-recorded (the AOSP container reveal animates the size and alpha of the panel instead).
 */
fun TransitionBuilder.budikSheetSlide(container: ElementKey) {
    // The swipe distance is exactly the height of the sheet.
    distance = UserActionDistance { fromContent, toContent, _ ->
        (container.targetSize(toContent) ?: container.targetSize(fromContent))
            ?.height
            ?.toFloat() ?: 0f
    }
    translate(container, Edge.Top)
}
