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

import androidx.compose.ui.unit.dp
import com.android.compose.animation.scene.FixedDistance
import com.android.compose.animation.scene.TransitionBuilder
import com.android.systemui.shade.ui.composable.OverlayShade

/**
 * Budík: the one transition of the notification shade and the control centre. The screen dims under
 * the panel with the finger and the panel's content fades in and settles from slightly above
 * (OverlayShade). The transition moves and re-measures nothing, so a frame costs one full screen
 * fill plus the content. Both panels use it with the same spec, so they feel alike.
 */
fun TransitionBuilder.budikPanelTransition() {
    distance = FixedDistance(PanelSwipeDistance)
    fade(OverlayShade.Elements.Scrim)
}

private val PanelSwipeDistance = 320.dp
