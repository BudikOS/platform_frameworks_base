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
import android.service.quicksettings.Tile
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
import com.android.systemui.budik.ui.compose.BudikSoundPowerIcons
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.media.remedia.ui.viewmodel.MediaCardViewModel
import com.android.systemui.media.remedia.ui.viewmodel.MediaCarouselVisibility
import com.android.systemui.media.remedia.ui.viewmodel.MediaSecondaryActionViewModel
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
import com.android.systemui.shade.ui.composable.shownFraction
import com.android.systemui.util.policy.PolicyRestriction
import com.android.systemui.volume.panel.component.volume.slider.ui.viewmodel.AudioStreamSliderViewModel
import kotlinx.coroutines.launch

/** Columns of the control centre grid; every module is a multiple of one square cell. */
internal const val CC_COLUMNS = 4
internal val CcGap = 12.dp

/** Tiles shown as round keys in the connectivity module, in the user's order. */
private val CONNECTIVITY_SPECS = setOf("internet", "wifi", "bt", "cell", "airplane", "hotspot")
private const val CONNECTIVITY_KEYS = 4
private const val TILES_BEFORE_SLIDERS = 4

/** Control centre colours, following the system light / dark mode. */
@Immutable
internal class CcColors(
    val module: Color,
    val moduleShadow: Color,
    val key: Color,
    val keyShadow: Color,
    val onKey: Color,
    val onKeyDim: Color,
    val text: Color,
    val textDim: Color,
)

internal val ccColors: CcColors
    @Composable
    @ReadOnlyComposable
    get() {
        val c = Budik.colors
        return if (c.isDark) {
            CcColors(
                module = c.surface,
                moduleShadow = c.keyShadow,
                key = Color(0xFF2A2D32),
                keyShadow = c.keyShadow,
                onKey = c.fg,
                onKeyDim = c.fg3,
                text = c.fg,
                textDim = c.fg3,
            )
        } else {
            CcColors(
                module = c.surface,
                moduleShadow = c.border,
                key = c.bg,
                keyShadow = c.border,
                onKey = c.fg,
                onKeyDim = c.fg3,
                text = c.fg,
                textDim = c.fg3,
            )
        }
    }

/**
 * Budík "Řídicí centrum", laid out like the iOS / HyperOS control centre from the top of the
 * screen: a connectivity module and the media module side by side, a row of tiles, the brightness
 * and volume sliders next to more tiles, the remaining tiles, then edit / settings / power. Every
 * module sits in a grid of square cells and is measured once; while the panel opens or closes the
 * modules only fade and settle with the finger, row by row. A tap on empty space closes it.
 */
@Composable
fun ContentScope.BudikControlCenter(
    containerViewModel: QuickSettingsContainerViewModel,
    volumeViewModel: AudioStreamSliderViewModel?,
    toolbarViewModelFactory: ToolbarViewModel.Factory,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tiles = containerViewModel.tileGridViewModel.tileViewModels
    val details = containerViewModel.detailsViewModel
    val blocks = rememberCcBlocks()
    TileListener(tiles) { true }

    val connectivity =
        tiles.filter { it.spec.spec in CONNECTIVITY_SPECS }.take(CONNECTIVITY_KEYS)
    val others = tiles - connectivity.toSet()
    val modules = buildList {
        if (connectivity.isNotEmpty()) add(CcModule.Connectivity(connectivity))
        if (blocks.isShown(CcBlock.MEDIA)) add(CcModule.Media)
        others.take(TILES_BEFORE_SLIDERS).forEach { add(CcModule.Key(it)) }
        if (blocks.isShown(CcBlock.BRIGHTNESS) && containerViewModel.isBrightnessSliderVisible) {
            add(CcModule.Brightness)
        }
        if (volumeViewModel != null && blocks.isShown(CcBlock.VOLUME)) {
            add(CcModule.Volume(volumeViewModel))
        }
        others.drop(TILES_BEFORE_SLIDERS).forEach { add(CcModule.Key(it)) }
    }

    val shown = { shownFraction() }
    Box(
        modifier.fillMaxSize().clickable(interactionSource = null, indication = null, onClick = onDismiss)
    ) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState(), overscrollEffect = null)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
        ) {
            ModuleGrid(modules.map { it.span }, shown) {
                modules.forEach { module ->
                    when (module) {
                        is CcModule.Connectivity -> ConnectivityModule(module.tiles, details)
                        CcModule.Media -> MediaModule(containerViewModel)
                        is CcModule.Key -> TileKey(module.tile, details)
                        CcModule.Brightness ->
                            BrightnessModule(containerViewModel.brightnessSliderViewModel)
                        is CcModule.Volume -> VolumeModule(module.viewModel)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Footer(
                toolbarViewModelFactory,
                Modifier.graphicsLayer { reveal(shown(), MaxRevealDelay) },
            )
        }
    }
}

private sealed class CcModule(val span: Span) {
    class Connectivity(val tiles: List<TileViewModel>) : CcModule(Span(2, 2))

    data object Media : CcModule(Span(2, 2))

    class Key(val tile: TileViewModel) : CcModule(Span(1, 1))

    data object Brightness : CcModule(Span(1, 2))

    class Volume(val viewModel: AudioStreamSliderViewModel) : CcModule(Span(1, 2))
}

internal class Span(val columns: Int, val rows: Int)

/**
 * Lays [content] out in [CC_COLUMNS] square cells, the n-th child sized and placed by [spans],
 * each in its own layer revealed by [shown].
 */
@Composable
private fun ModuleGrid(spans: List<Span>, shown: () -> Float, content: @Composable () -> Unit) {
    Layout(content) { measurables, constraints ->
        val gap = CcGap.roundToPx()
        val cell = (constraints.maxWidth - gap * (CC_COLUMNS - 1)) / CC_COLUMNS
        fun extent(cells: Int) = cells * cell + (cells - 1) * gap
        val origins = placeModules(spans)
        val placeables =
            measurables.mapIndexed { i, measurable ->
                measurable.measure(
                    Constraints.fixed(extent(spans[i].columns), extent(spans[i].rows))
                )
            }
        val rows = origins.indices.maxOfOrNull { origins[it].y + spans[it].rows } ?: 0
        layout(constraints.maxWidth, if (rows > 0) extent(rows) else 0) {
            placeables.forEachIndexed { i, placeable ->
                val origin = origins[i]
                placeable.placeWithLayer(origin.x * (cell + gap), origin.y * (cell + gap)) {
                    reveal(shown(), (origin.y * RowRevealDelay).coerceAtMost(MaxRevealDelay))
                }
            }
        }
    }
}

private const val RowRevealDelay = 0.06f
private const val MaxRevealDelay = 0.3f
private val RevealOffset = 24.dp

/**
 * A module fades in and settles from slightly above and smaller as [shown] goes past [delay]. The
 * alpha is applied to each draw, so no offscreen layer is needed.
 */
private fun GraphicsLayerScope.reveal(shown: Float, delay: Float) {
    val fraction = ((shown - delay) / (1f - MaxRevealDelay)).coerceIn(0f, 1f)
    alpha = fraction
    compositingStrategy = CompositingStrategy.ModulateAlpha
    translationY = -(1f - fraction) * RevealOffset.toPx()
    scaleX = 0.92f + 0.08f * fraction
    scaleY = scaleX
}

/** Puts every module at the first free cell in reading order where it fits (dense packing). */
internal fun placeModules(spans: List<Span>, columns: Int = CC_COLUMNS): List<IntOffset> {
    val taken = ArrayList<BooleanArray>()
    fun isFree(x: Int, y: Int, span: Span): Boolean {
        for (row in y until y + span.rows) {
            val cells = taken.getOrNull(row) ?: continue
            for (column in x until x + span.columns) if (cells[column]) return false
        }
        return true
    }
    return spans.map { span ->
        val (x, y) =
            generateSequence(0) { it + 1 }
                .firstNotNullOf { y ->
                    (0..columns - span.columns).firstOrNull { isFree(it, y, span) }?.let { it to y }
                }
        while (taken.size < y + span.rows) taken += BooleanArray(columns)
        for (row in y until y + span.rows) taken[row].fill(true, x, x + span.columns)
        IntOffset(x, y)
    }
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

/** Toggle for dual target tiles, otherwise the tile's main action (or its details). */
private fun onTileClick(tile: TileViewModel, ui: TileUiState, details: DetailsViewModel) {
    if (!ui.handlesMainClick) return
    if (ui.handlesToggleClick) {
        tile.toggleClick()
    } else if (!(QsDetailedView.isEnabled && details.onTileClicked(tile.spec))) {
        tile.mainClick(null)
    }
}

/** Details for dual target tiles (Wi-Fi list, Bluetooth devices), otherwise the settings. */
private fun onTileLongClick(tile: TileViewModel, ui: TileUiState, details: DetailsViewModel) {
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
private fun keyColors(ui: TileUiState, face: Color, shadow: Color): KeyColors {
    val cc = ccColors
    return when (ui.visualState) {
        Tile.STATE_ACTIVE -> KeyColors(Budik.Mint, Budik.MintInk, Budik.Ink)
        Tile.STATE_UNAVAILABLE -> KeyColors(face, shadow, cc.onKeyDim)
        else -> KeyColors(face, shadow, cc.onKey)
    }
}

private fun Context.tileIcon(icon: IconProvider): Icon =
    icon.icon?.let {
        if (it is QSTileImpl.ResourceIcon) Icon.Resource(it.resId, null)
        else Icon.Loaded(it.getDrawable(this), null)
    } ?: Icon.Resource(R.drawable.ic_error_outline, null)

private fun Modifier.tileSemantics(ui: TileUiState): Modifier =
    semantics(mergeDescendants = true) {
        contentDescription = ui.label
        stateDescription = ui.secondaryLabel
    }

/** A one cell tile: the icon, and its name under it when the name fits uncut. */
@Composable
private fun TileKey(tile: TileViewModel, details: DetailsViewModel, modifier: Modifier = Modifier) {
    val cc = ccColors
    val (ui, icon) = rememberTileState(tile)
    val colors = keyColors(ui, cc.module, cc.moduleShadow)
    BudikKey(
        face = colors.face,
        shadow = colors.shadow,
        radius = Budik.RadiusL,
        onClick = { onTileClick(tile, ui, details) },
        onLongClick = { onTileLongClick(tile, ui, details) },
        modifier = modifier.tileSemantics(ui),
    ) {
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SmallTileContent(iconProvider = { tileIcon(icon) }, color = colors.content, size = { 26.dp })
            TextIfFits(
                text = ui.label,
                style = Budik.body(11.sp, colors.content, FontWeight.Medium),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** Shows [text] only when it fits in one line without being cut; otherwise nothing. */
@Composable
internal fun TextIfFits(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    Layout(
        content = { Text(text = text, style = style, maxLines = 1, softWrap = false) },
        modifier = modifier,
    ) { measurables, constraints ->
        val line =
            measurables.first().measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
        if (line.width <= constraints.maxWidth) {
            layout(line.width, line.height) { line.place(0, 0) }
        } else {
            layout(0, 0) {}
        }
    }
}

/** A static module surface: [face] on a hard 0 x 3 dp shadow, the depth inside the element. */
private fun Modifier.moduleSurface(face: Color, shadow: Color): Modifier =
    drawBehind {
            val depth = Budik.KeyDepth.toPx()
            val corner = CornerRadius(Budik.RadiusL.toPx())
            val faceSize = Size(size.width, size.height - depth)
            drawRoundRect(shadow, Offset(0f, depth), faceSize, corner)
            drawRoundRect(face, Offset.Zero, faceSize, corner)
        }
        .padding(bottom = Budik.KeyDepth)

/** Wi-Fi, Bluetooth, mobile data, airplane mode (whichever are in the panel) as round keys. */
@Composable
private fun ConnectivityModule(tiles: List<TileViewModel>, details: DetailsViewModel) {
    val cc = ccColors
    Column(
        Modifier.moduleSurface(cc.module, cc.moduleShadow).padding(12.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        tiles.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { RoundKey(it, details) }
                if (row.size == 1) Spacer(Modifier.size(RoundKeySize))
            }
        }
    }
}

private val RoundKeySize = 54.dp

@Composable
private fun RoundKey(tile: TileViewModel, details: DetailsViewModel) {
    val cc = ccColors
    val (ui, icon) = rememberTileState(tile)
    val colors = keyColors(ui, cc.key, cc.keyShadow)
    BudikKey(
        face = colors.face,
        shadow = colors.shadow,
        radius = RoundKeySize / 2,
        onClick = { onTileClick(tile, ui, details) },
        onLongClick = { onTileLongClick(tile, ui, details) },
        modifier = Modifier.size(RoundKeySize, RoundKeySize + Budik.KeyDepth).tileSemantics(ui),
    ) {
        SmallTileContent(
            iconProvider = { tileIcon(icon) },
            color = colors.content,
            modifier = Modifier.align(Alignment.Center),
            size = { 24.dp },
        )
    }
}

/** Now playing: title, artist and previous / play-pause / next; "Nic nehraje" when idle. */
@Composable
private fun MediaModule(containerViewModel: QuickSettingsContainerViewModel) {
    val cc = ccColors
    val context = LocalContext.current
    val viewModel =
        rememberViewModel("BudikControlCenter.Media") {
            containerViewModel.mediaViewModelFactory.create(
                context,
                MediaCarouselVisibility.WhenNotEmpty,
            )
        }
    LaunchedEffect(viewModel) { viewModel.setVisibility { true } }
    val card = viewModel.cards.getOrNull(viewModel.currentIndex) ?: viewModel.cards.firstOrNull()

    Box(Modifier.drawBehind { drawModuleShadow(cc.moduleShadow) }.padding(bottom = Budik.KeyDepth)) {
        Expandable(
            color = cc.module,
            shape = RoundedCornerShape(Budik.RadiusL),
            onClick = card?.onClick,
            useModifierBasedImplementation = true,
            defaultMinSize = false,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (card == null) {
                Text(
                    text = "Nic nehraje",
                    style = Budik.body(14.sp, cc.textDim, FontWeight.Medium),
                    modifier = Modifier.fillMaxSize().padding(14.dp),
                )
            } else {
                NowPlaying(card)
            }
        }
    }
}

private fun DrawScope.drawModuleShadow(color: Color) {
    val depth = Budik.KeyDepth.toPx()
    drawRoundRect(
        color,
        Offset(0f, depth),
        Size(size.width, size.height - depth),
        CornerRadius(Budik.RadiusL.toPx()),
    )
}

@Composable
private fun NowPlaying(card: MediaCardViewModel) {
    val cc = ccColors
    Column(
        Modifier.fillMaxSize().padding(14.dp).semantics { contentDescription = card.contentDescription }
    ) {
        Text(
            text = card.title,
            style = Budik.body(15.sp, cc.text, FontWeight.SemiBold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = card.subtitle,
            style = Budik.body(13.sp, cc.textDim),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaAction(card.navigation.left)
            card.playPauseAction?.let { action ->
                action.icon?.let { MediaButton(it, action.onClick) }
            }
            MediaAction(card.navigation.right)
        }
    }
}

@Composable
private fun MediaAction(action: MediaSecondaryActionViewModel) {
    when (action) {
        is MediaSecondaryActionViewModel.Action -> MediaButton(action.icon, action.onClick)
        MediaSecondaryActionViewModel.ReserveSpace -> Spacer(Modifier.size(MediaButtonSize))
        MediaSecondaryActionViewModel.None -> Unit
    }
}

private val MediaButtonSize = 40.dp

@Composable
private fun MediaButton(icon: Icon, onClick: (() -> Unit)?) {
    Box(
        Modifier.size(MediaButtonSize)
            .clickable(
                enabled = onClick != null,
                interactionSource = null,
                indication = null,
                onClick = { onClick?.invoke() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        SmallTileContent(iconProvider = { icon }, color = ccColors.text, size = { 26.dp })
    }
}

@Composable
private fun BrightnessModule(viewModel: BrightnessSliderViewModel) {
    val min = viewModel.minBrightness.value
    val max = viewModel.maxBrightness.value
    val loaded by remember {
        derivedStateOf {
            viewModel.currentBrightness.value != BrightnessSliderViewModel.initialValue.value
        }
    }
    if (max <= min || !loaded) return
    val restriction by
        viewModel.policyRestriction.collectAsStateWithLifecycle(
            initialValue = PolicyRestriction.NoRestriction
        )
    val coroutineScope = rememberCoroutineScope()
    fun gamma(fraction: Float) = GammaBrightness((min + fraction * (max - min)).toInt())

    VerticalSlider(
        fraction = { (viewModel.currentBrightness.value - min).toFloat() / (max - min) },
        icon = BudikIcons.Brightness,
        description = "Jas",
        onDragStart = {
            (restriction as? PolicyRestriction.Restricted)?.let {
                viewModel.showPolicyRestrictionDialog(it)
                false
            } ?: true
        },
        onDrag = { coroutineScope.launch { viewModel.onDrag(Drag.Dragging(gamma(it))) } },
        onDragEnd = {
            coroutineScope.launch { viewModel.onDrag(Drag.Stopped(gamma(it))) }
            viewModel.emitBrightnessTouchForFalsing()
        },
    )
}

@Composable
private fun VolumeModule(viewModel: AudioStreamSliderViewModel) {
    val state by viewModel.slider.collectAsStateWithLifecycle()
    val range = state.valueRange
    if (range.endInclusive <= range.start) return
    fun value(fraction: Float) = range.start + fraction * (range.endInclusive - range.start)

    VerticalSlider(
        fraction = { (state.value - range.start) / (range.endInclusive - range.start) },
        icon = BudikSoundPowerIcons.Volume,
        description = state.label.ifEmpty { "Hlasitost" },
        enabled = state.isEnabled,
        onDragStart = { true },
        onDrag = { viewModel.onValueChanged(state, value(it)) },
        onDragEnd = {
            viewModel.onValueChanged(state, value(it))
            viewModel.onValueChangeFinished()
        },
    )
}

/**
 * A two cell high slider (iOS): mint fills it from the bottom. Dragging moves the value by the
 * distance moved, not to the finger, so grabbing it never jumps. The value is read only while
 * drawing, so a drag redraws the slider without recomposing anything.
 */
@Composable
private fun VerticalSlider(
    fraction: () -> Float,
    icon: ImageVector,
    description: String,
    onDragStart: () -> Boolean,
    onDrag: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
    enabled: Boolean = true,
) {
    val cc = ccColors
    val currentFraction by rememberUpdatedState(fraction)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    var dragged by remember { mutableStateOf<Float?>(null) }
    val shown = { (dragged ?: currentFraction()).coerceIn(0f, 1f) }
    val iconOnFill by remember { derivedStateOf { shown() > IconFillThreshold } }
    val path = remember { Path() }

    Box(
        Modifier.drawBehind {
                val depth = Budik.KeyDepth.toPx()
                val radius = Budik.RadiusL.toPx()
                val faceSize = Size(size.width, size.height - depth)
                drawRoundRect(cc.moduleShadow, Offset(0f, depth), faceSize, CornerRadius(radius))
                drawRoundRect(cc.module, Offset.Zero, faceSize, CornerRadius(radius))
                val fill = faceSize.height * shown()
                if (fill > 0f) {
                    val corner = CornerRadius(radius.coerceAtMost(fill))
                    path.reset()
                    path.addRoundRect(
                        RoundRect(
                            left = 0f,
                            top = faceSize.height - fill,
                            right = faceSize.width,
                            bottom = faceSize.height,
                            bottomLeftCornerRadius = corner,
                            bottomRightCornerRadius = corner,
                        )
                    )
                    drawPath(path, Budik.Mint)
                }
            }
            .padding(bottom = Budik.KeyDepth)
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(shown(), 0f..1f)
                if (enabled) {
                    setProgress { target ->
                        currentOnDragEnd(target.coerceIn(0f, 1f))
                        true
                    }
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var value = currentFraction().coerceIn(0f, 1f)
                    val slop =
                        awaitVerticalTouchSlopOrCancellation(down.id) { change, _ ->
                            change.consume()
                        } ?: return@awaitEachGesture
                    if (!currentOnDragStart()) return@awaitEachGesture
                    val height = (size.height - Budik.KeyDepth.toPx()).coerceAtLeast(1f)
                    fun moveBy(dy: Float) {
                        value = (value - dy / height).coerceIn(0f, 1f)
                        dragged = value
                        currentOnDrag(value)
                    }
                    moveBy(slop.position.y - down.position.y)
                    verticalDrag(slop.id) { change ->
                        moveBy(change.position.y - change.previousPosition.y)
                        change.consume()
                    }
                    currentOnDragEnd(value)
                    dragged = null
                }
            }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (iconOnFill) Budik.Ink else cc.onKey,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).size(26.dp),
        )
    }
}

/** Above this value the mint fill reaches the icon at the bottom of a slider. */
private const val IconFillThreshold = 0.18f

@Composable
private fun Footer(toolbarViewModelFactory: ToolbarViewModel.Factory, modifier: Modifier) {
    val cc = ccColors
    val toolbarViewModel =
        rememberViewModel("BudikControlCenter.Toolbar") { toolbarViewModelFactory.create() }
    val editViewModel =
        rememberViewModel("BudikControlCenter.Edit") {
            toolbarViewModel.editModeButtonViewModelFactory.create()
        }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (editViewModel.isEditButtonVisible) {
            BudikKey(
                face = cc.module,
                shadow = cc.moduleShadow,
                radius = Budik.RadiusM,
                onClick = editViewModel::onButtonClick,
                modifier =
                    Modifier.size(FooterKeyWidth, FooterKeyHeight).semantics {
                        contentDescription = "Upravit"
                    },
            ) {
                FooterIcon(BudikIcons.Edit, Modifier.align(Alignment.Center))
            }
        }
        Spacer(Modifier.weight(1f))
        toolbarViewModel.settingsButtonViewModel?.let {
            FooterExpandableKey(it, BudikIcons.Tune, "Nastavení")
            Spacer(Modifier.width(CcGap))
        }
        FooterExpandableKey(toolbarViewModel.powerButtonViewModel, BudikIcons.Power, "Vypnout")
    }
}

private val FooterKeyWidth = 56.dp
private val FooterKeyHeight = 51.dp

@Composable
private fun FooterIcon(icon: ImageVector, modifier: Modifier = Modifier) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = ccColors.onKey,
        modifier = modifier.size(22.dp),
    )
}

/** Footer key whose action launches from the key (settings, power menu). */
@Composable
private fun FooterExpandableKey(
    model: FooterActionsButtonViewModel,
    icon: ImageVector,
    description: String,
) {
    val cc = ccColors
    val faceHeight: Dp = FooterKeyHeight - Budik.KeyDepth
    Box(
        Modifier.size(FooterKeyWidth, FooterKeyHeight).drawBehind {
            val depth = Budik.KeyDepth.toPx()
            drawRoundRect(
                cc.moduleShadow,
                Offset(0f, depth),
                Size(size.width, size.height - depth),
                CornerRadius(Budik.RadiusM.toPx()),
            )
        }
    ) {
        Expandable(
            color = cc.module,
            shape = RoundedCornerShape(Budik.RadiusM),
            onClick = model.onClick,
            modifier =
                Modifier.size(FooterKeyWidth, faceHeight).semantics {
                    contentDescription = description
                },
            useModifierBasedImplementation = true,
            defaultMinSize = false,
        ) {
            Box(Modifier.size(FooterKeyWidth, faceHeight), contentAlignment = Alignment.Center) {
                FooterIcon(icon)
            }
        }
    }
}
