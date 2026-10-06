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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.systemui.budik.ui.compose.Budik
import com.android.systemui.budik.ui.compose.BudikIcons
import com.android.systemui.budik.ui.compose.BudikKey
import com.android.systemui.budik.ui.compose.BudikSoundPowerIcons
import com.android.systemui.budik.ui.compose.budikKeycap
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.qs.panels.ui.compose.TileListener
import com.android.systemui.qs.panels.ui.compose.infinitegrid.SmallTileContent
import com.android.systemui.qs.panels.ui.viewmodel.AvailableEditActions
import com.android.systemui.qs.ui.viewmodel.QuickSettingsContainerViewModel
import com.android.systemui.volume.panel.component.volume.slider.ui.viewmodel.AudioStreamSliderViewModel
import kotlin.math.roundToInt

/** Drag of one item of the grid, picked up by a long press. */
private class ItemDrag(val item: CcItem, val grab: Offset, val itemSize: IntSize) {
    var pointer by mutableStateOf(Offset.Zero)
    /** Index in the grid the item would land at, [REMOVE], or null (back where it was). */
    var target by mutableStateOf<Int?>(null)

    /** [items] with the item moved to [target]. */
    fun preview(items: List<CcItem>): List<CcItem> {
        val index = target?.takeIf { it >= 0 } ?: return items
        return items.toMutableList().apply {
            remove(item)
            add(index.coerceIn(0, size), item)
        }
    }

    companion object {
        const val REMOVE = -1
    }
}

/**
 * Budík editor of the control centre, in place like the iOS one: the same grid with the same
 * modules and tiles as the panel, each with "−" to remove it and, where it has more sizes, a key to
 * resize it. A long press picks an item up to move it; dropped below the grid it is removed. Below
 * the grid are the modules and tiles that are not in the panel; a tap adds one at the end. Tiles
 * are stored as before (sysui_qs_tiles), the modules' places and sizes in [CcLayout.SETTING].
 */
@Composable
fun BudikControlCenterEditor(
    containerViewModel: QuickSettingsContainerViewModel,
    volumeViewModel: AudioStreamSliderViewModel?,
    modifier: Modifier = Modifier,
) {
    val cc = ccColors
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val viewModel = containerViewModel.editModeViewModel
    val editTiles by viewModel.tiles.collectAsStateWithLifecycle(emptyList())
    val tiles = containerViewModel.tileGridViewModel.tileViewModels
    val layout = rememberCcLayout()
    TileListener(tiles) { true }
    val grid = rememberCcGrid(tiles, layout, containerViewModel, volumeViewModel)

    BackHandler { viewModel.stopEditing() }
    DisposableEffect(Unit) { onDispose { viewModel.stopEditing() } }

    val editable = remember(editTiles) { editTiles.associateBy { it.tileSpec } }
    val offer =
        remember(editTiles) {
            editTiles.filter {
                !it.isCurrent && AvailableEditActions.ADD in it.availableEditActions
            }
        }
    val hiddenBlocks = CcBlock.entries.filter { it !in layout.blocks }

    fun canRemove(item: CcItem) =
        when (item) {
            is CcItem.Block -> true
            is CcItem.Tile -> editable[item.spec]?.isRemovable == true
        }

    fun canResize(item: CcItem) = item !is CcItem.Block || item.block.sizes.size > 1

    fun name(item: CcItem) =
        when (item) {
            is CcItem.Block -> item.block.title
            is CcItem.Tile -> editable[item.spec]?.label?.text.orEmpty()
        }

    fun remove(item: CcItem) {
        if (!canRemove(item)) return
        val index = grid.items.indexOf(item)
        val next =
            when (item) {
                is CcItem.Block -> layout.hide(item.block, index)
                is CcItem.Tile -> {
                    viewModel.removeTile(item.spec)
                    layout.withoutItemAt(index).copy(wideTiles = layout.wideTiles - item.spec.spec)
                }
            }
        CcLayout.write(context, next)
    }

    fun move(items: List<CcItem>) {
        val specs = tiles.map { it.spec }
        val order =
            layout.connectivityTiles(specs) + items.filterIsInstance<CcItem.Tile>().map { it.spec }
        if (order != specs) viewModel.setTiles(order)
        val next = layout.withOrder(items)
        if (next != layout) CcLayout.write(context, next)
    }

    var picked by remember { mutableStateOf<ItemDrag?>(null) }
    val items = picked?.preview(grid.items) ?: grid.items
    val removing = picked?.target == ItemDrag.REMOVE

    // Where every item is, to find the one under the finger.
    val itemBounds = remember { HashMap<CcItem, LayoutCoordinates>() }
    var area by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var gridBounds by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val currentItems by rememberUpdatedState(items)
    val currentGridItems by rememberUpdatedState(grid.items)
    val currentMove by rememberUpdatedState(::move)
    val currentRemove by rememberUpdatedState(::remove)

    fun itemAt(position: Offset): CcItem? {
        val root = area ?: return null
        return currentItems.firstOrNull { item ->
            itemBounds[item]?.let {
                it.isAttached && root.localBoundingBoxOf(it).contains(position)
            } ?: false
        }
    }

    Column(modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "UPRAVIT ŘÍDICÍ CENTRUM",
                style = Budik.meta(11.sp, cc.textDim, caps = true),
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            BudikKey(
                face = Budik.Navy,
                shadow = Budik.NavyInk,
                onClick = viewModel::stopEditing,
                modifier = Modifier.height(43.dp).width(96.dp),
            ) {
                Text(
                    text = "Hotovo",
                    style = Budik.body(15.sp, Color.White, FontWeight.Bold),
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text =
                when {
                    removing -> "Pusťte pro odebrání z panelu"
                    picked != null -> name(picked!!.item)
                    else -> "Podržením přesunete, šipkami změníte velikost."
                },
            style = Budik.meta(12.sp, cc.textDim),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))

        Column(
            Modifier.fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState(), enabled = picked == null)
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .onGloballyPositioned { area = it }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val item = itemAt(down.position) ?: return@awaitEachGesture
                            val long =
                                awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                            val root = area ?: return@awaitEachGesture
                            val bounds =
                                itemBounds[item]?.let { root.localBoundingBoxOf(it) }
                                    ?: return@awaitEachGesture
                            val d =
                                ItemDrag(
                                    item = item,
                                    grab = long.position - bounds.topLeft,
                                    itemSize =
                                        IntSize(
                                            bounds.width.roundToInt(),
                                            bounds.height.roundToInt(),
                                        ),
                                )
                            d.pointer = long.position
                            picked = d
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            long.consume()
                            val completed =
                                drag(long.id) { change ->
                                    d.pointer = change.position
                                    change.consume()
                                    val over = itemAt(change.position)
                                    val gridBottom =
                                        gridBounds?.let { root.localBoundingBoxOf(it).bottom }
                                            ?: Float.MAX_VALUE
                                    d.target =
                                        when {
                                            over != null -> currentItems.indexOf(over)
                                            change.position.y > gridBottom -> ItemDrag.REMOVE
                                            else -> d.target
                                        }
                                }
                            if (completed) {
                                when (d.target) {
                                    null -> Unit
                                    ItemDrag.REMOVE -> currentRemove(d.item)
                                    else -> currentMove(d.preview(currentGridItems))
                                }
                            }
                            picked = null
                        }
                    }
            ) {
                Box(Modifier.onGloballyPositioned { gridBounds = it }) {
                    ModuleGrid(items.map { it.span }) {
                        items.forEach { item ->
                            key(item) {
                                DisposableEffect(Unit) { onDispose { itemBounds.remove(item) } }
                                EditableItem(
                                    item = item,
                                    grid = grid,
                                    ghost = picked?.item == item,
                                    onRemove = { remove(item) }.takeIf { canRemove(item) },
                                    onResize =
                                        { CcLayout.write(context, layout.resized(item)) }.takeIf {
                                            canResize(item)
                                        },
                                    modifier =
                                        Modifier.onGloballyPositioned { itemBounds[item] = it },
                                )
                            }
                        }
                    }
                }

                // The picked up item follows the finger above everything else.
                picked?.let { d ->
                    val density = LocalDensity.current
                    Box(
                        Modifier.offset {
                                (d.pointer - d.grab).let {
                                    IntOffset(it.x.roundToInt(), it.y.roundToInt())
                                }
                            }
                            .size(
                                with(density) { d.itemSize.width.toDp() },
                                with(density) { d.itemSize.height.toDp() },
                            ),
                        propagateMinConstraints = true,
                    ) {
                        CcItemContent(d.item, grid)
                    }
                }
            }

            if (hiddenBlocks.isNotEmpty() || offer.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Caption("PŘIDAT")
                val addable =
                    hiddenBlocks.map { block ->
                        OfferItem(block.title, OfferIcon.Vector(block.icon)) {
                            CcLayout.write(context, layout.show(block, grid.items.size))
                        }
                    } +
                        offer.map { tile ->
                            OfferItem(tile.label.text, OfferIcon.Tile(tile.icon)) {
                                viewModel.addTile(tile.tileSpec)
                            }
                        }
                addable.chunked(CC_COLUMNS).forEachIndexed { i, row ->
                    if (i > 0) Spacer(Modifier.height(CcGap))
                    Row(horizontalArrangement = Arrangement.spacedBy(CcGap)) {
                        row.forEach { OfferKey(it, Modifier.weight(1f)) }
                        repeat(CC_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private val CcBlock.icon: ImageVector
    get() =
        when (this) {
            CcBlock.CONNECTIVITY -> BudikIcons.Wifi
            CcBlock.MEDIA -> BudikIcons.Music
            CcBlock.BRIGHTNESS -> BudikIcons.Brightness
            CcBlock.VOLUME -> BudikSoundPowerIcons.Volume
        }

/**
 * An item of the grid exactly as in the panel, but inert: the editor handles the gestures. "−"
 * removes it, the arrows switch its size. [ghost] is the empty slot a picked up item leaves behind.
 */
@Composable
private fun EditableItem(
    item: CcItem,
    grid: CcGrid,
    ghost: Boolean,
    onRemove: (() -> Unit)?,
    onResize: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val cc = ccColors
    Box(modifier) {
        if (ghost) {
            Box(
                Modifier.matchParentSize().drawBehind {
                    val w = 1.5.dp.toPx()
                    drawRoundRect(
                        cc.textDim,
                        topLeft = Offset(w / 2f, w / 2f),
                        size = Size(size.width - w, size.height - Budik.KeyDepth.toPx() - w),
                        cornerRadius = CornerRadius(Budik.RadiusL.toPx()),
                        style = Stroke(w),
                    )
                }
            )
            return@Box
        }
        Box(Modifier.matchParentSize(), propagateMinConstraints = true) {
            CcItemContent(item, grid)
        }
        // Covers the item's own controls (keys, sliders) so that they do nothing while editing.
        Box(
            Modifier.matchParentSize().pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent() }
            }
        )
        onRemove?.let { EditBadge(BudikIcons.Minus, "Odebrat", Alignment.TopStart, it) }
        onResize?.let { EditBadge(BudikIcons.Resize, "Změnit velikost", Alignment.BottomEnd, it) }
    }
}

/**
 * A small round key in a corner of an item ("−" or resize): 22 dp drawn, 44 dp to touch, both
 * inside the item (touches outside of it would not reach the badge).
 */
@Composable
private fun BoxScope.EditBadge(
    icon: ImageVector,
    description: String,
    corner: Alignment,
    onClick: () -> Unit,
) {
    Box(
        Modifier.align(corner)
            .padding(bottom = if (corner == Alignment.BottomEnd) Budik.KeyDepth else 0.dp)
            .size(EditBadgeTouch)
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = corner,
    ) {
        Box(
            Modifier.padding(4.dp).size(22.dp).background(ccColors.text, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = Budik.colors.bg,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

private val EditBadgeTouch = 44.dp

private sealed interface OfferIcon {
    class Vector(val image: ImageVector) : OfferIcon

    class Tile(val icon: Icon) : OfferIcon
}

private class OfferItem(val name: String, val icon: OfferIcon, val onAdd: () -> Unit)

/** A module or tile that can be added: its icon and name on a key; a tap adds it. */
@Composable
private fun OfferKey(item: OfferItem, modifier: Modifier = Modifier) {
    val cc = ccColors
    Box(
        modifier
            .aspectRatio(1f)
            .budikKeycap(cc.module, cc.moduleShadow, Budik.RadiusL)
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClickLabel = "Přidat",
                onClick = item.onAdd,
            )
            .semantics { contentDescription = item.name }
    ) {
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val icon = item.icon) {
                is OfferIcon.Vector ->
                    Icon(icon.image, null, tint = cc.onKey, modifier = Modifier.size(26.dp))
                is OfferIcon.Tile ->
                    SmallTileContent(
                        iconProvider = { icon.icon },
                        color = cc.onKey,
                        size = { 26.dp },
                    )
            }
            TextIfFits(
                text = item.name,
                style = Budik.body(11.sp, cc.onKey, FontWeight.Medium),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Box(
            Modifier.align(Alignment.TopStart)
                .padding(4.dp)
                .size(22.dp)
                .background(Budik.Mint, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(BudikIcons.Plus, null, tint = Budik.Ink, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = Budik.meta(11.sp, ccColors.textDim, FontWeight.Bold, caps = true),
        maxLines = 1,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}
