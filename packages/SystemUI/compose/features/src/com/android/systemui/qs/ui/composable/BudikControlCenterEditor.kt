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

import android.app.ActivityManager
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.android.systemui.budik.ui.compose.BudikSheetHandle
import com.android.systemui.budik.ui.compose.budikKeycap
import com.android.systemui.qs.panels.ui.compose.infinitegrid.SmallTileContent
import com.android.systemui.qs.panels.ui.viewmodel.AvailableEditActions
import com.android.systemui.qs.panels.ui.viewmodel.EditModeViewModel
import com.android.systemui.qs.panels.ui.viewmodel.EditTileViewModel
import com.android.systemui.qs.pipeline.shared.TileSpec
import kotlin.math.roundToInt

/** Blocks of the control centre that the user can order and switch on or off. */
enum class CcBlock(val title: String, val canHide: Boolean) {
    MEDIA("Média", true),
    TILES("Dlaždice", false),
    BRIGHTNESS("Jas", true),
}

/** Order and visibility of the control centre blocks, kept in Settings.Secure. */
@Stable
data class CcBlocks(val order: List<CcBlock>, val hidden: Set<CcBlock>) {
    val visible: List<CcBlock>
        get() = order.filter { it !in hidden }

    fun serialize(): String = order.joinToString(",") { (if (it in hidden) "!" else "") + it.name }

    fun move(block: CcBlock, by: Int): CcBlocks {
        val from = order.indexOf(block)
        val to = (from + by).coerceIn(0, order.lastIndex)
        if (from == to) return this
        return copy(order = order.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun toggle(block: CcBlock): CcBlocks =
        copy(hidden = if (block in hidden) hidden - block else hidden + block)

    companion object {
        /** Settings.Secure key, e.g. "MEDIA,TILES,!BRIGHTNESS" (! = hidden). */
        const val SETTING = "budik_cc_blocks"
        val Default = CcBlocks(listOf(CcBlock.MEDIA, CcBlock.TILES, CcBlock.BRIGHTNESS), emptySet())

        fun parse(value: String?): CcBlocks {
            if (value.isNullOrBlank()) return Default
            val order = mutableListOf<CcBlock>()
            val hidden = mutableSetOf<CcBlock>()
            value.split(',').forEach { raw ->
                val name = raw.trim().removePrefix("!")
                val block = CcBlock.entries.firstOrNull { it.name == name } ?: return@forEach
                if (block in order) return@forEach
                order += block
                if (raw.trim().startsWith("!") && block.canHide) hidden += block
            }
            // Blocks added in a later version go to their default place at the end.
            CcBlock.entries.filter { it !in order }.forEach { order += it }
            return CcBlocks(order, hidden)
        }

        fun read(context: Context): CcBlocks =
            parse(
                Settings.Secure.getStringForUser(
                    context.contentResolver,
                    SETTING,
                    ActivityManager.getCurrentUser(),
                )
            )

        fun write(context: Context, blocks: CcBlocks) {
            Settings.Secure.putStringForUser(
                context.contentResolver,
                SETTING,
                blocks.serialize(),
                ActivityManager.getCurrentUser(),
            )
        }
    }
}

/** The current [CcBlocks], updated when the setting changes. */
@Composable
fun rememberCcBlocks(): CcBlocks {
    val context = LocalContext.current
    val initial = remember(context) { CcBlocks.read(context) }
    val blocks by
        produceState(initial, context) {
            val observer =
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        value = CcBlocks.read(context)
                    }
                }
            context.contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(CcBlocks.SETTING),
                false,
                observer,
                UserHandle.USER_ALL,
            )
            awaitDispose { context.contentResolver.unregisterContentObserver(observer) }
        }
    return blocks
}

/** Drag of one tile: from the panel (move or remove) or from the offer (add). */
private class TileDrag(
    val spec: TileSpec,
    val fromCurrent: Boolean,
    val grab: Offset,
    val size: IntSize,
    val large: Boolean,
) {
    var pointer by mutableStateOf(Offset.Zero)
    /** Index in the panel the tile would land at, [REMOVE], or null (back where it was). */
    var target by mutableStateOf<Int?>(null)

    companion object {
        const val REMOVE = -1
    }
}

/**
 * Budík editor of the control centre (replaces the Pixel tile editor): a dark sheet with the
 * panel's tiles laid out exactly like the control centre (six icon keys, then large keys), the
 * offer of other tiles below and the panel blocks (media, tiles, brightness) to order and switch
 * on or off. Tap removes or adds a tile, a long press picks it up to move it, to drop it into
 * the panel or out of it. Tiles are stored as before (sysui_qs_tiles), blocks in
 * [CcBlocks.SETTING].
 */
@Composable
fun BudikControlCenterEditor(viewModel: EditModeViewModel, modifier: Modifier = Modifier) {
    val c = Budik.colors
    val context = LocalContext.current
    val tiles by viewModel.tiles.collectAsStateWithLifecycle(emptyList())
    val blocks = rememberCcBlocks()

    BackHandler { viewModel.stopEditing() }
    DisposableEffect(Unit) { onDispose { viewModel.stopEditing() } }

    val byspec = remember(tiles) { tiles.associateBy { it.tileSpec } }
    val current = remember(tiles) { tiles.filter { it.isCurrent }.map { it.tileSpec } }
    val offer =
        remember(tiles) {
            tiles.filter { !it.isCurrent && AvailableEditActions.ADD in it.availableEditActions }
        }

    var picked by remember { mutableStateOf<TileDrag?>(null) }
    var pressed by remember { mutableStateOf<EditTileViewModel?>(null) }
    val preview =
        picked.let { d ->
            val t = d?.target
            when {
                d == null || t == null || t < 0 -> current
                else ->
                    current.toMutableList().apply {
                        remove(d.spec)
                        add(t.coerceIn(0, size), d.spec)
                    }
            }
        }
    val removing = picked?.let { it.fromCurrent && it.target == TileDrag.REMOVE } == true

    // Where every key is, to find the one under the finger (keys: true = panel, false = offer).
    val keyBounds = remember { HashMap<Pair<Boolean, TileSpec>, LayoutCoordinates>() }
    var area by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var panelGrid by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val latestPreview by rememberUpdatedState(preview)
    val latestCurrent by rememberUpdatedState(current)
    val latestTiles by rememberUpdatedState(byspec)

    fun keyAt(position: Offset): Pair<Boolean, TileSpec>? {
        val root = area ?: return null
        return keyBounds.entries
            .firstOrNull { (_, coords) ->
                coords.isAttached && root.localBoundingBoxOf(coords).contains(position)
            }
            ?.key
    }

    fun tap(key: Pair<Boolean, TileSpec>) {
        val tile = latestTiles[key.second] ?: return
        if (key.first) {
            if (tile.isRemovable) viewModel.removeTile(key.second)
        } else {
            viewModel.addTile(key.second)
        }
    }

    fun drop(d: TileDrag) {
        val t = d.target ?: return
        when {
            d.fromCurrent && t == TileDrag.REMOVE ->
                if (latestTiles[d.spec]?.isRemovable == true) viewModel.removeTile(d.spec)
            d.fromCurrent -> if (latestPreview != latestCurrent) viewModel.setTiles(latestPreview)
            t >= 0 -> viewModel.addTile(d.spec, t)
        }
    }

    val sheetShape = RoundedCornerShape(topStart = Budik.RadiusL, topEnd = Budik.RadiusL)
    Column(
        modifier
            .fillMaxWidth()
            .background(c.ccPanel, sheetShape)
            .then(if (c.isDark) Modifier.border(1.dp, c.ccBorder, sheetShape) else Modifier)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "UPRAVIT ŘÍDICÍ CENTRUM",
                style = Budik.meta(11.sp, c.ccText, caps = true),
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
        // One line of context: the name of the key under the finger, or how editing works.
        val hint =
            when {
                picked != null && removing -> "Pusťte pro odebrání z panelu"
                picked != null -> byspec[picked!!.spec]?.label?.text.orEmpty()
                pressed != null ->
                    (if (pressed!!.isCurrent) "Odebrat: " else "Přidat: ") + pressed!!.label.text
                else -> "Klepnutím odeberete nebo přidáte, podržením přesunete."
            }
        Text(
            text = hint,
            style = Budik.meta(12.sp, c.ccText),
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
                            val key = keyAt(down.position) ?: return@awaitEachGesture
                            pressed = latestTiles[key.second]
                            val long = awaitLongPressOrCancellation(down.id)
                            pressed = null
                            if (long == null) {
                                val up = currentEvent.changes.firstOrNull { it.id == down.id }
                                if (
                                    up != null &&
                                        !up.pressed &&
                                        !up.isConsumed &&
                                        (up.position - down.position).getDistance() <
                                            viewConfiguration.touchSlop
                                ) {
                                    tap(key)
                                }
                                return@awaitEachGesture
                            }
                            val coords = keyBounds[key] ?: return@awaitEachGesture
                            val bounds = area!!.localBoundingBoxOf(coords)
                            val d =
                                TileDrag(
                                    spec = key.second,
                                    fromCurrent = key.first,
                                    grab = long.position - bounds.topLeft,
                                    size =
                                        IntSize(
                                            bounds.width.roundToInt(),
                                            bounds.height.roundToInt(),
                                        ),
                                    large =
                                        key.first &&
                                            latestCurrent.indexOf(key.second) >= SMALL_KEYS,
                                )
                            d.pointer = long.position
                            picked = d
                            long.consume()
                            val completed =
                                drag(long.id) { change ->
                                    d.pointer = change.position
                                    change.consume()
                                    val over = keyAt(change.position)
                                    val gridBottom =
                                        panelGrid?.let { area!!.localBoundingBoxOf(it).bottom }
                                            ?: Float.MAX_VALUE
                                    d.target =
                                        when {
                                            over != null && over.first ->
                                                latestPreview.indexOf(over.second)
                                            change.position.y > gridBottom ->
                                                if (d.fromCurrent) TileDrag.REMOVE else null
                                            else -> d.target
                                        }
                                }
                            if (completed) drop(d)
                            picked = null
                        }
                    }
            ) {
                Column {
                    Caption("V PANELU")
                    Column(Modifier.onGloballyPositioned { panelGrid = it }) {
                        PanelGrid(preview) { spec, large, keyModifier ->
                            val tile = byspec[spec]
                            val ghost = picked?.spec == spec
                            EditKey(
                                tile = tile,
                                large = large,
                                badge = if (tile?.isRemovable == true) Badge.Remove else Badge.None,
                                ghost = ghost,
                                modifier =
                                    keyModifier.onGloballyPositioned {
                                        keyBounds[true to spec] = it
                                    },
                            )
                        }
                    }
                    if (offer.isNotEmpty()) {
                        Spacer(Modifier.height(18.dp))
                        Caption("PŘIDAT")
                        offer.chunked(SMALL_KEYS).forEachIndexed { i, row ->
                            if (i > 0) Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { tile ->
                                    EditKey(
                                        tile = tile,
                                        large = false,
                                        badge = Badge.Add,
                                        ghost = picked?.spec == tile.tileSpec,
                                        modifier =
                                            Modifier.weight(1f).onGloballyPositioned {
                                                keyBounds[false to tile.tileSpec] = it
                                            },
                                    )
                                }
                                repeat(SMALL_KEYS - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }

                // The picked up key follows the finger above everything else.
                picked?.let { d ->
                    val density = LocalDensity.current
                    EditKey(
                        tile = byspec[d.spec],
                        large = d.large,
                        badge = Badge.None,
                        ghost = false,
                        modifier =
                            Modifier.offset {
                                    (d.pointer - d.grab).let {
                                        IntOffset(it.x.roundToInt(), it.y.roundToInt())
                                    }
                                }
                                .size(
                                    with(density) { d.size.width.toDp() },
                                    with(density) { d.size.height.toDp() },
                                ),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Caption("BLOKY PANELU")
            blocks.order.forEachIndexed { i, block ->
                if (i > 0) Spacer(Modifier.height(8.dp))
                BlockRow(
                    block = block,
                    shown = block !in blocks.hidden,
                    canMoveUp = i > 0,
                    canMoveDown = i < blocks.order.lastIndex,
                    onToggle = { CcBlocks.write(context, blocks.toggle(block)) },
                    onMove = { by -> CcBlocks.write(context, blocks.move(block, by)) },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
        BudikSheetHandle(Modifier.align(Alignment.CenterHorizontally), color = c.ccText)
    }
}

/** The panel's tiles laid out like the control centre: six icon keys, then four large per row. */
@Composable
private fun PanelGrid(
    specs: List<TileSpec>,
    content: @Composable (spec: TileSpec, large: Boolean, modifier: Modifier) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        specs.take(SMALL_KEYS).forEach { spec ->
            key(spec) { content(spec, false, Modifier.weight(1f)) }
        }
        repeat(SMALL_KEYS - specs.size.coerceAtMost(SMALL_KEYS)) { Spacer(Modifier.weight(1f)) }
    }
    specs.drop(SMALL_KEYS).chunked(LARGE_KEYS_PER_ROW).forEach { row ->
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { spec ->
                key(spec) { content(spec, true, Modifier.weight(1f)) }
            }
            repeat(LARGE_KEYS_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

private enum class Badge {
    None,
    Add,
    Remove,
}

/**
 * A key of the editor: the tile's icon (and its name on a large key when it fits). [ghost] is
 * the empty slot a picked up key leaves behind.
 */
@Composable
private fun EditKey(
    tile: EditTileViewModel?,
    large: Boolean,
    badge: Badge,
    ghost: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = Budik.colors
    val label = tile?.label?.text.orEmpty()
    Box(
        modifier
            .height(if (large) 63.dp else 55.dp)
            .semantics { contentDescription = label }
            .then(
                if (ghost) {
                    Modifier.drawBehind {
                        val w = 1.5.dp.toPx()
                        drawRoundRect(
                            c.ccText,
                            topLeft = Offset(w / 2f, w / 2f),
                            size =
                                Size(size.width - w, size.height - Budik.KeyDepth.toPx() - w),
                            cornerRadius = CornerRadius(Budik.RadiusS.toPx()),
                            style = Stroke(w),
                        )
                    }
                } else {
                    Modifier.budikKeycap(c.ccKeyOff, c.ccKeyOffShadow)
                }
            )
    ) {
        if (!ghost && tile != null) {
            if (large) {
                SmallTileContent(
                    iconProvider = { tile.icon },
                    color = c.ccOnKeyOff,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 9.dp, end = 9.dp),
                    size = { 20.dp },
                )
                TextIfFits(
                    text = label,
                    style = Budik.body(13.sp, c.ccOnKeyOff, FontWeight.SemiBold),
                    modifier =
                        Modifier.align(Alignment.BottomStart)
                            .padding(start = 9.dp, end = 6.dp, bottom = 8.dp),
                )
            } else {
                SmallTileContent(
                    iconProvider = { tile.icon },
                    color = c.ccOnKeyOff,
                    modifier = Modifier.align(Alignment.Center),
                    size = { 24.dp },
                )
            }
            if (badge != Badge.None) EditBadge(badge)
        }
    }
}

/** "−" (remove) or "+" (add) in a small disc at the top start corner of a key. */
@Composable
private fun BoxScope.EditBadge(badge: Badge) {
    val add = badge == Badge.Add
    Box(
        Modifier.align(Alignment.TopStart)
            .offset(x = (-5).dp, y = (-5).dp)
            .size(20.dp)
            .background(if (add) Budik.Mint else Budik.colors.ccText, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (add) BudikIcons.Plus else BudikIcons.Minus,
            contentDescription = null,
            tint = Budik.Ink,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = Budik.meta(11.sp, Budik.colors.ccText, FontWeight.Bold, caps = true),
        maxLines = 1,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

/** One panel block: its name, on / off (if it can be hidden) and up / down. */
@Composable
private fun BlockRow(
    block: CcBlock,
    shown: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: () -> Unit,
    onMove: (Int) -> Unit,
) {
    val c = Budik.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = block.title,
            style = Budik.body(16.sp, c.ccText, FontWeight.SemiBold),
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (block.canHide) {
            BudikKey(
                face = if (shown) Budik.Mint else c.ccKeyOff,
                shadow = if (shown) Budik.MintInk else c.ccKeyOffShadow,
                onClick = onToggle,
                modifier =
                    Modifier.height(51.dp).width(104.dp).semantics {
                        contentDescription = block.title + if (shown) ", zapnuto" else ", vypnuto"
                    },
            ) {
                Text(
                    text = if (shown) "Zapnuto" else "Vypnuto",
                    style =
                        Budik.body(14.sp, if (shown) Budik.Ink else c.ccOnKeyOff, FontWeight.Bold),
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        MoveKey(BudikIcons.ChevronUp, "Posunout výš", enabled = canMoveUp) { onMove(-1) }
        Spacer(Modifier.width(8.dp))
        MoveKey(BudikIcons.ChevronDown, "Posunout níž", enabled = canMoveDown) { onMove(1) }
    }
}

@Composable
private fun MoveKey(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val c = Budik.colors
    BudikKey(
        face = if (enabled) c.ccKeyOff else c.ccUnavailable,
        shadow = c.ccKeyOffShadow,
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp, 51.dp).semantics { contentDescription = description },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) c.ccOnKeyOff else c.ccOnUnavailable,
            modifier = Modifier.align(Alignment.Center).size(20.dp),
        )
    }
}
