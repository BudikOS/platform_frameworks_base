/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.shade.ui.composable

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.overscroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.approachLayout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.LowestZIndexContentPicker
import com.android.compose.modifiers.thenIf
import com.android.mechanics.behavior.VerticalExpandContainerSpec
import com.android.mechanics.behavior.verticalExpandContainerBackground
import com.android.mechanics.compose.modifier.motionDriver
import com.android.systemui.budik.ui.compose.Budik
import com.android.systemui.qs.ui.composable.TileRevealFlag
import com.android.systemui.res.R
import com.android.systemui.scene.ui.composable.LocalSceneContainerPreloadedResources
import com.android.systemui.shade.ui.ShadeColors.shadePanelScrimBehind
import com.android.systemui.shade.ui.composable.OverlayShade.Colors
import com.android.systemui.shade.ui.composable.OverlayShade.Dimensions
import com.android.systemui.shade.ui.composable.OverlayShade.rememberShadeExpansionMotion
import kotlin.math.min

/** Renders a lightweight shade UI container, as an overlay. */
@Composable
fun ContentScope.OverlayShade(
    panelElement: ElementKey,
    alignmentOnWideScreens: Alignment.Horizontal,
    statusBarHeightPx: Int,
    enableTransparency: Boolean,
    onScrimClicked: () -> Unit,
    modifier: Modifier = Modifier,
    onBackgroundPlaced: (bounds: Rect, topCornerRadius: Float, bottomCornerRadius: Float) -> Unit =
        { _, _, _ ->
        },
    dimBehind: Boolean = false,
    header: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val isFullWidth = LocalSceneContainerPreloadedResources.current.isFullWidthShade
    val panelSpec = rememberShadeExpansionMotion(isFullWidth)
    val panelCornerRadiusPx = with(LocalDensity.current) { panelSpec.radius.toPx() }
    val panelWidth = Dimensions.PanelWidth
    val panelAlignment =
        when {
            isFullWidth -> Alignment.TopCenter
            alignmentOnWideScreens == Alignment.End -> Alignment.TopEnd
            else -> Alignment.TopStart
        }

    Box(modifier) {
        OverlayScrim(showBackgroundColor = enableTransparency, onClicked = onScrimClicked)

        Box(
            modifier =
                Modifier.fillMaxSize()
                    .panelContainerPadding(isFullWidth, alignmentOnWideScreens, statusBarHeightPx),
            contentAlignment = panelAlignment,
        ) {
            Panel(
                enableTransparency = enableTransparency,
                spec = panelSpec,
                isFullScreen = isFullWidth,
                dimBehind = dimBehind,
                modifier =
                    Modifier.overscroll(verticalOverscrollEffect)
                        .element(panelElement)
                        .thenIf(TileRevealFlag.isEnabled) {
                            Modifier.motionDriver(
                                contentScope = this@OverlayShade,
                                label = "OverlayShade",
                            )
                        }
                        // Budík: on phones the sheet covers the whole screen (iOS like).
                        .thenIf(isFullWidth) { Modifier.fillMaxSize() }
                        .thenIf(!isFullWidth) { Modifier.width(panelWidth) }
                        // TODO(440566878): Investigate if this can be optimized by replacing with
                        // onLayoutRectChanged.
                        .onPlaced { coordinates ->
                            val bounds = coordinates.boundsInWindow()
                            val isTopRounded = panelSpec.isFloating
                            val bottomCornerRadius: Float =
                                if (isTopRounded) {
                                    min(panelCornerRadiusPx, bounds.height / 2)
                                } else {
                                    min(panelCornerRadiusPx, bounds.height)
                                }
                            val topCornerRadius = if (isTopRounded) bottomCornerRadius else 0f
                            onBackgroundPlaced(bounds, topCornerRadius, bottomCornerRadius)
                        },
                header = header.takeIf { isFullWidth },
                content = {
                    Box(
                        Modifier
                            // Prevent this element from resizing during the container reveal
                            // animation. The parent clips the content, so remeasuring the
                            // children (especially text) on every frame is unnecessary and
                            // can cause performance issues (jank).
                            .approachLayout(
                                isMeasurementApproachInProgress = { layoutState.isTransitioning() }
                            ) { measurable, constraints ->
                                if (layoutState.currentTransition == null) {
                                    return@approachLayout measurable.measure(constraints).run {
                                        layout(width, height) { place(0, 0) }
                                    }
                                }

                                // Make sure that this layout node has the same size than when we
                                // are at rest.
                                val widthAtRest = lookaheadSize.width
                                val fixedWidthConstraints =
                                    constraints.copy(minWidth = widthAtRest, maxWidth = widthAtRest)
                                measurable.measure(fixedWidthConstraints).run {
                                    layout(width, height) { place(IntOffset.Zero) }
                                }
                            }
                    ) {
                        content()
                    }
                },
            )
        }

        if (!isFullWidth) {
            header()
        }
    }
}

@Composable
private fun ContentScope.Panel(
    enableTransparency: Boolean,
    spec: VerticalExpandContainerSpec,
    isFullScreen: Boolean,
    dimBehind: Boolean,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)?,
    content: @Composable () -> Unit,
) {
    if (!isFullScreen) {
        Box(
            modifier =
                modifier
                    .disableSwipesWhenScrolling()
                    .verticalExpandContainerBackground(
                        Colors.panelBackground(enableTransparency),
                        spec,
                    )
        ) {
            Column {
                header?.invoke()
                content()
            }
        }
        return
    }

    // Budík: either an opaque full screen sheet with rounded bottom corners and a hard 0 x 3 dp
    // edge below it, or ([dimBehind]) the screen dimming with the finger under content that
    // floats on it. No gradient, no blur, no offscreen layer: a frame costs one full screen fill.
    val background = Colors.panelBackground(enableTransparency)
    val edge = Budik.colors.border
    Box(
        modifier =
            modifier
                .disableSwipesWhenScrolling()
                .then(
                    if (dimBehind) {
                        Modifier.drawBehind {
                            drawRect(background, alpha = DimAlpha * shownFraction())
                        }
                    } else {
                        Modifier.drawBehind { drawSheet(background, edge) }.clipToBounds()
                    }
                )
    ) {
        Column(Modifier.fillMaxSize()) {
            header?.invoke()
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
    }
}

/** Opacity of the dimmed screen behind a [dimBehind] panel when it is fully shown. */
private const val DimAlpha = 0.94f

/**
 * How much of this content is shown, from 0 (hidden) to 1 (shown); follows the finger while a
 * transition to or from it runs. Read it in the draw or layer phase only.
 */
fun ContentScope.shownFraction(): Float {
    val transition =
        layoutState.currentTransitions.lastOrNull { it.isTransitioningFromOrTo(contentKey) }
            ?: return 1f
    val progress = transition.progress.coerceIn(0f, 1f)
    return if (transition.toContent == contentKey) progress else 1f - progress
}

/** The sheet's background with rounded bottom corners, the hard edge only where it shows. */
private fun DrawScope.drawSheet(background: Color, edge: Color) {
    val corner = CornerRadius(Budik.RadiusL.toPx())
    val bottom = Size(size.width, corner.y * 2f)
    val bottomTop = size.height - bottom.height
    drawRoundRect(edge, Offset(0f, bottomTop + Budik.KeyDepth.toPx()), bottom, corner)
    drawRect(background, size = Size(size.width, size.height - corner.y))
    drawRoundRect(background, Offset(0f, bottomTop), bottom, corner)
}

@Composable
@ReadOnlyComposable
@SuppressLint("ConfigurationScreenWidthHeight")
private fun getHalfScreenWidth() = LocalConfiguration.current.screenWidthDp.dp / 2

@Composable
private fun Modifier.panelContainerPadding(
    isFullWidthPanel: Boolean,
    alignment: Alignment.Horizontal,
    statusBarHeightPx: Int,
): Modifier {
    if (isFullWidthPanel) {
        return this
    }
    // On wide screens, the shade panel width is limited to half the screen width.
    val halfScreenWidth = getHalfScreenWidth()
    val (startPadding, endPadding) =
        when (alignment) {
            Alignment.Start -> Dimensions.PanelPaddingHorizontal to halfScreenWidth
            Alignment.End -> halfScreenWidth to Dimensions.PanelPaddingHorizontal
            else -> Dimensions.PanelPaddingHorizontal to Dimensions.PanelPaddingHorizontal
        }
    val paddings = PaddingValues(start = startPadding, end = endPadding)
    val layoutDirection = LocalLayoutDirection.current
    return windowInsetsPadding(
        WindowInsets.safeDrawing
            .union(WindowInsets(top = statusBarHeightPx))
            .union(
                WindowInsets(
                    left = paddings.calculateLeftPadding(layoutDirection),
                    right = paddings.calculateRightPadding(layoutDirection),
                )
            )
    )
}

object OverlayShade {
    object Elements {
        val Scrim = ElementKey("OverlayShadeScrim", contentPicker = LowestZIndexContentPicker)
    }

    object Colors {
        val ScrimBackground: Color
            @Composable
            @ReadOnlyComposable
            get() = Color(shadePanelScrimBehind(LocalContext.current))

        @Composable
        @ReadOnlyComposable
        fun panelBackground(transparencyEnabled: Boolean): Color {
            // Budík: opaque bg token (#F5F6F7 / #0E0F11), no translucent blur colour.
            return Budik.colors.bg
        }
    }

    object Dimensions {
        val PanelCornerRadius: Dp
            @Composable
            @ReadOnlyComposable
            get() = dimensionResource(R.dimen.overlay_shade_panel_shape_radius)

        val PanelPaddingHorizontal: Dp
            @Composable
            @ReadOnlyComposable
            get() = dimensionResource(R.dimen.shade_panel_margin_horizontal)

        val PanelWidth: Dp
            @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.shade_panel_width)
    }

    @Composable
    fun rememberShadeExpansionMotion(isFullWidth: Boolean): VerticalExpandContainerSpec {
        val radius = Dimensions.PanelCornerRadius
        return remember(radius, isFullWidth) {
            VerticalExpandContainerSpec(isFloating = !isFullWidth, radius = radius)
        }
    }
}
