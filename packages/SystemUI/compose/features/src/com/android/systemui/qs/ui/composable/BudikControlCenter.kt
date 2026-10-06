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

package com.android.systemui.qs.ui.composable

import android.content.Context
import android.os.Build
import android.os.SystemProperties
import android.service.quicksettings.Tile
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.animation.Expandable
import com.android.compose.animation.scene.ContentScope
import com.android.systemui.brightness.domain.model.GammaBrightness
import com.android.systemui.brightness.ui.viewmodel.BrightnessSliderViewModel
import com.android.systemui.brightness.ui.viewmodel.Drag
import com.android.systemui.budik.ui.compose.Budik
import com.android.systemui.budik.ui.compose.BudikIcons
import com.android.systemui.budik.ui.compose.BudikKey
import com.android.systemui.budik.ui.compose.BudikSheetHandle
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.media.remedia.ui.compose.Media
import com.android.systemui.media.remedia.ui.compose.MediaPresentationStyle
import com.android.systemui.qs.flags.QsDetailedView
import com.android.systemui.qs.footer.ui.viewmodel.FooterActionsButtonViewModel
import com.android.systemui.qs.panels.ui.compose.TileListener
import com.android.systemui.qs.panels.ui.compose.infinitegrid.SmallTileContent
import com.android.systemui.qs.panels.ui.viewmodel.DetailsViewModel
import com.android.systemui.qs.panels.ui.viewmodel.IconProvider
import com.android.systemui.qs.panels.ui.viewmodel.TileUiState
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.toIconProvider
import com.android.systemui.qs.panels.ui.viewmodel.toUiState
import com.android.systemui.qs.panels.ui.viewmodel.toolbar.ToolbarViewModel
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.qs.ui.viewmodel.QuickSettingsContainerViewModel
import com.android.systemui.res.R
import com.android.systemui.util.policy.PolicyRestriction
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Tiles in the first row: icon-only keys with the label under the key. */
private const val SMALL_KEYS = 6
/** Tiles per row below: large keys with the label inside. */
private const val LARGE_KEYS_PER_ROW = 4
private const val BRIGHTNESS_SEGMENTS = 20

/**
 * Budík "Řídicí centrum" (export/mockups/{light,dark}/05_quick_settings.png): a dark sheet with a
 * monospace header, a row of six icon keys, large keys below, a segmented brightness slider and a
 * footer with the logo, version and edit / settings / power keys.
 */
@Composable
fun ContentScope.BudikControlCenter(
    containerViewModel: QuickSettingsContainerViewModel,
    toolbarViewModelFactory: ToolbarViewModel.Factory,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Budik.colors
    val tiles = containerViewModel.tileGridViewModel.tileViewModels
    val toolbarViewModel =
        rememberViewModel("BudikControlCenter.Toolbar") { toolbarViewModelFactory.create() }

    TileListener(tiles) { true }

    val sheetShape = RoundedCornerShape(topStart = Budik.RadiusL, topEnd = Budik.RadiusL)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(c.ccPanel, sheetShape)
                .then(if (c.isDark) Modifier.border(1.dp, c.ccBorder, sheetShape) else Modifier)
                .padding(start = 16.dp, end = 16.dp, top = 14.dp)
    ) {
        Header()
        Spacer(Modifier.height(14.dp))

        Column(
            Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
        ) {
            if (containerViewModel.showMedia) {
                Media(
                    viewModelFactory = containerViewModel.mediaViewModelFactory,
                    presentationStyle = MediaPresentationStyle.Compact,
                    behavior = QuickSettingsContainerViewModel.mediaUiBehavior,
                    onDismissed = containerViewModel::onMediaSwipeToDismiss,
                    modifier = Modifier,
                    location = Media.Location.QS,
                )
                Spacer(Modifier.height(12.dp))
            }

            val details = containerViewModel.detailsViewModel
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tiles.take(SMALL_KEYS).forEach { tile ->
                    SmallKey(tile, details, Modifier.weight(1f))
                }
                repeat(SMALL_KEYS - tiles.size.coerceAtMost(SMALL_KEYS)) {
                    Spacer(Modifier.weight(1f))
                }
            }

            val large: List<TileViewModel?> = tiles.drop(SMALL_KEYS) + listOf(null)
            large.chunked(LARGE_KEYS_PER_ROW).forEach { row ->
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { tile ->
                        if (tile != null) {
                            LargeKey(tile, details, Modifier.weight(1f))
                        } else {
                            DoneKey(onDone, Modifier.weight(1f))
                        }
                    }
                    repeat(LARGE_KEYS_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            if (containerViewModel.isBrightnessSliderVisible) {
                Spacer(Modifier.height(14.dp))
                SegmentedBrightness(containerViewModel.brightnessSliderViewModel)
            }

            Spacer(Modifier.height(16.dp))
            Footer(toolbarViewModel)
            Spacer(Modifier.height(12.dp))
        }
        BudikSheetHandle(Modifier.align(Alignment.CenterHorizontally), color = c.ccText)
    }
}

/** Title and grab bar; network and battery are shown once, in the status icons above. */
@Composable
private fun Header() {
    val c = Budik.colors
    val style = Budik.meta(11.sp, c.ccText, caps = true)
    Box(Modifier.fillMaxWidth().height(20.dp)) {
        Text(
            text = "ŘÍDICÍ CENTRUM",
            style = style,
            maxLines = 1,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Box(
            Modifier.align(Alignment.Center)
                .size(32.dp, 4.dp)
                .background(c.ccHandle, RoundedCornerShape(2.dp))
        )
    }
}

/** The connected network as shown by the internet (or Wi-Fi) tile, if it is on. */
@Composable
private fun rememberNetworkLabel(tiles: List<TileViewModel>): String? {
    val tile = tiles.firstOrNull { it.spec.spec == "internet" } ?: tiles.firstOrNull {
        it.spec.spec == "wifi"
    }
    val label by
        produceState<String?>(null, tile) {
            tile?.state?.collect { state ->
                value =
                    state.secondaryLabel?.toString()?.takeIf {
                        state.state == Tile.STATE_ACTIVE && it.isNotBlank()
                    }
            }
        }
    return label
}

@Composable
private fun rememberTileState(tile: TileViewModel): Pair<TileUiState, IconProvider> {
    val resources = LocalResources.current
    val uiState by
        produceState(tile.currentState.toUiState(resources), tile, resources) {
            tile.state.collect { value = it.toUiState(resources) }
        }
    val icon by
        produceState(tile.currentState.toIconProvider(), tile) {
            tile.state.collect { value = it.toIconProvider() }
        }
    return uiState to icon
}

/**
 * Click: toggle for dual target tiles, otherwise the tile's main action (or its details view).
 * Long click: details/dialog for dual target tiles, otherwise the tile's settings.
 */
private fun onKeyClick(tile: TileViewModel, ui: TileUiState, details: DetailsViewModel) {
    if (!ui.handlesMainClick) return
    if (ui.handlesToggleClick) {
        tile.toggleClick()
    } else if (!(QsDetailedView.isEnabled && details.onTileClicked(tile.spec))) {
        tile.mainClick(null)
    }
}

private fun onKeyLongClick(tile: TileViewModel, ui: TileUiState, details: DetailsViewModel) {
    if (ui.handlesToggleClick && ui.handlesMainClick) {
        if (!(QsDetailedView.isEnabled && details.onTileClicked(tile.spec))) {
            tile.mainClick(null)
        }
    } else if (ui.handlesSettingsClick) {
        tile.settingsClick(null)
    }
}

private class KeyColors(val face: Color, val shadow: Color, val content: Color)

@Composable
private fun keyColors(ui: TileUiState): KeyColors {
    val c = Budik.colors
    return when (ui.visualState) {
        Tile.STATE_ACTIVE -> KeyColors(Budik.Mint, Budik.MintInk, Budik.Ink)
        Tile.STATE_UNAVAILABLE -> KeyColors(c.ccUnavailable, Color.Black, c.ccOnUnavailable)
        else -> KeyColors(c.ccKeyOff, c.ccKeyOffShadow, c.ccOnKeyOff)
    }
}

private fun Context.tileIcon(icon: IconProvider): Icon =
    icon.icon?.let {
        if (it is QSTileImpl.ResourceIcon) Icon.Resource(it.resId, null)
        else Icon.Loaded(it.getDrawable(this), null)
    } ?: Icon.Resource(R.drawable.ic_error_outline, null)

@Composable
private fun SmallKey(tile: TileViewModel, details: DetailsViewModel, modifier: Modifier) {
    val (ui, icon) = rememberTileState(tile)
    val colors = keyColors(ui)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BudikKey(
            face = colors.face,
            shadow = colors.shadow,
            onClick = { onKeyClick(tile, ui, details) },
            onLongClick = { onKeyLongClick(tile, ui, details) },
            modifier = Modifier.fillMaxWidth().height(47.dp),
        ) {
            SmallTileContent(
                iconProvider = { tileIcon(icon) },
                color = colors.content,
                modifier = Modifier.align(Alignment.Center),
                size = { 22.dp },
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = ui.label,
            style = Budik.meta(10.sp, Budik.colors.ccText),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LargeKey(tile: TileViewModel, details: DetailsViewModel, modifier: Modifier) {
    val (ui, icon) = rememberTileState(tile)
    val colors = keyColors(ui)
    BudikKey(
        face = colors.face,
        shadow = colors.shadow,
        onClick = { onKeyClick(tile, ui, details) },
        onLongClick = { onKeyLongClick(tile, ui, details) },
        modifier = modifier.height(63.dp),
    ) {
        SmallTileContent(
            iconProvider = { tileIcon(icon) },
            color = colors.content,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 9.dp, end = 9.dp),
            size = { 20.dp },
        )
        Text(
            text = ui.label,
            style = Budik.body(13.sp, colors.content, FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 9.dp, end = 6.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun DoneKey(onDone: () -> Unit, modifier: Modifier) {
    BudikKey(
        face = Budik.Navy,
        shadow = Budik.NavyInk,
        onClick = onDone,
        modifier = modifier.height(63.dp),
    ) {
        Text(
            text = "Hotovo",
            style = Budik.body(13.sp, Color.White, FontWeight.Bold),
            maxLines = 1,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 9.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun SegmentedBrightness(viewModel: BrightnessSliderViewModel) {
    val gamma = viewModel.currentBrightness.value
    if (gamma == BrightnessSliderViewModel.initialValue.value) return // not loaded yet
    val c = Budik.colors
    val min = viewModel.minBrightness.value
    val max = viewModel.maxBrightness.value
    val restriction by
        viewModel.policyRestriction.collectAsStateWithLifecycle(
            initialValue = PolicyRestriction.NoRestriction
        )
    val coroutineScope = rememberCoroutineScope()
    var dragValue by remember { mutableStateOf<Int?>(null) }
    val fraction =
        if (max > min) ((dragValue ?: gamma) - min).toFloat() / (max - min) else 0f
    val keyFace = if (c.isDark) c.ccKeyOff else Color.White
    val keyShadow = if (c.isDark) c.ccKeyOffShadow else c.ccKeyOffShadow

    fun emit(drag: Drag) {
        coroutineScope.launch { viewModel.onDrag(drag) }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.fillMaxWidth()
                .height(55.dp)
                .budikKeySurface(keyFace, keyShadow)
                .padding(horizontal = 14.dp),
    ) {
        Icon(
            imageVector = BudikIcons.Brightness,
            contentDescription = null,
            tint = c.ccOnKeyOff,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Canvas(
            Modifier.weight(1f).height(26.dp).pointerInput(min, max, restriction) {
                val restricted = restriction
                fun valueAt(x: Float) =
                    (min + (x / size.width).coerceIn(0f, 1f) * (max - min)).roundToInt()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (restricted is PolicyRestriction.Restricted) {
                        viewModel.showPolicyRestrictionDialog(restricted)
                        return@awaitEachGesture
                    }
                    var value = valueAt(down.position.x)
                    val slop =
                        awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                            change.consume()
                        }
                    if (slop != null) {
                        value = valueAt(slop.position.x)
                        dragValue = value
                        emit(Drag.Dragging(GammaBrightness(value)))
                        horizontalDrag(slop.id) { change ->
                            value = valueAt(change.position.x)
                            dragValue = value
                            emit(Drag.Dragging(GammaBrightness(value)))
                            change.consume()
                        }
                        emit(Drag.Stopped(GammaBrightness(value)))
                    } else {
                        // A tap (released without moving) sets the brightness at that segment.
                        val up = currentEvent.changes.firstOrNull { it.id == down.id }
                        if (up == null || up.pressed || up.isConsumed) return@awaitEachGesture
                        emit(Drag.Stopped(GammaBrightness(value)))
                    }
                    viewModel.emitBrightnessTouchForFalsing()
                    dragValue = null
                }
            }
        ) {
            val gap = 2.dp.toPx()
            val segment = (size.width - gap * (BRIGHTNESS_SEGMENTS - 1)) / BRIGHTNESS_SEGMENTS
            val filled = (fraction * BRIGHTNESS_SEGMENTS).roundToInt()
            for (i in 0 until BRIGHTNESS_SEGMENTS) {
                drawRect(
                    color = if (i < filled) Budik.Mint else c.ccSegmentOff,
                    topLeft = Offset(i * (segment + gap), 0f),
                    size = Size(segment, size.height),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "${(fraction * 100).roundToInt()}",
            style = Budik.meta(14.sp, c.ccOnKeyOff),
            textAlign = TextAlign.End,
            modifier = Modifier.width(30.dp),
        )
    }
}

/** A static keycap surface (no press state), shadow included in the element's height. */
private fun Modifier.budikKeySurface(face: Color, shadow: Color): Modifier =
    drawBehind {
            val depth = Budik.KeyDepth.toPx()
            val corner = CornerRadius(Budik.RadiusS.toPx())
            val faceSize = Size(size.width, size.height - depth)
            drawRoundRect(shadow, Offset(0f, depth), faceSize, corner)
            drawRoundRect(face, Offset.Zero, faceSize, corner)
        }
        .padding(bottom = Budik.KeyDepth)

@Composable
private fun Footer(toolbarViewModel: ToolbarViewModel) {
    val c = Budik.colors
    val editViewModel =
        rememberViewModel("BudikControlCenter.Edit") {
            toolbarViewModel.editModeButtonViewModelFactory.create()
        }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Image(
            painter = rememberVectorPainter(BudikIcons.LogoOnDark),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = remember { versionLabel() },
            style = Budik.meta(11.sp, c.ccText),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (editViewModel.isEditButtonVisible) {
            BudikKey(
                face = c.ccKeyOff,
                shadow = c.ccKeyOffShadow,
                onClick = editViewModel::onButtonClick,
                modifier = Modifier.size(40.dp, 37.dp),
            ) {
                FooterIcon(BudikIcons.Edit, Modifier.align(Alignment.Center))
            }
            Spacer(Modifier.width(8.dp))
        }
        toolbarViewModel.settingsButtonViewModel?.let {
            FooterExpandableKey(it, BudikIcons.Tune)
            Spacer(Modifier.width(8.dp))
        }
        FooterExpandableKey(toolbarViewModel.powerButtonViewModel, BudikIcons.Power)
    }
}

@Composable
private fun FooterIcon(icon: ImageVector, modifier: Modifier = Modifier) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = Budik.colors.ccOnKeyOff,
        modifier = modifier.size(20.dp),
    )
}

/** Footer key whose action launches from the key (settings, power menu). */
@Composable
private fun FooterExpandableKey(model: FooterActionsButtonViewModel, icon: ImageVector) {
    val c = Budik.colors
    Box(
        Modifier.size(40.dp, 37.dp).drawBehind {
            val depth = Budik.KeyDepth.toPx()
            drawRoundRect(
                c.ccKeyOffShadow,
                Offset(0f, depth),
                Size(size.width, size.height - depth),
                CornerRadius(Budik.RadiusS.toPx()),
            )
        }
    ) {
        Expandable(
            color = c.ccKeyOff,
            shape = RoundedCornerShape(Budik.RadiusS),
            onClick = model.onClick,
            modifier = Modifier.size(40.dp, 34.dp),
            useModifierBasedImplementation = true,
            defaultMinSize = false,
        ) {
            Box(Modifier.size(40.dp, 34.dp), contentAlignment = Alignment.Center) {
                FooterIcon(icon)
            }
        }
    }
}

private fun versionLabel(): String {
    val version = SystemProperties.get("ro.budikos.version").substringBefore('-')
    val device = SystemProperties.get("ro.product.vendor.device").ifEmpty { Build.DEVICE }
    return listOf("Budík OS $version".trim(), device).joinToString(" · ")
}
