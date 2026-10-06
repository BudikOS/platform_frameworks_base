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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import com.android.systemui.qs.pipeline.shared.TileSpec

/** Columns of the control centre grid; every module is a multiple of one square cell. */
internal const val CC_COLUMNS = 4

/** A size in cells of the control centre grid. */
@Immutable
data class Span(val columns: Int, val rows: Int) {
    override fun toString() = "${columns}x$rows"

    companion object {
        val Cell = Span(1, 1)
        val Wide = Span(2, 1)

        fun parse(value: String): Span? {
            val (columns, rows) =
                value.split('x').mapNotNull { it.toIntOrNull() }.takeIf { it.size == 2 }
                    ?: return null
            return Span(columns, rows).takeIf { columns in 1..CC_COLUMNS && rows in 1..2 }
        }
    }
}

/** Tiles that the connectivity module shows as round keys, in the user's order. */
private val CONNECTIVITY_SPECS = setOf("internet", "wifi", "bt", "cell", "airplane", "hotspot")
private const val CONNECTIVITY_KEYS = 4

/**
 * Modules of the control centre besides the tiles, with the sizes each can take (first = default).
 */
enum class CcBlock(val title: String, val sizes: List<Span>, val defaultIndex: Int) {
    CONNECTIVITY("Připojení", listOf(Span(2, 2), Span(4, 1)), 0),
    MEDIA("Média", listOf(Span(2, 2), Span(4, 2)), 1),
    BRIGHTNESS("Jas", listOf(Span(1, 2)), 6),
    VOLUME("Hlasitost", listOf(Span(1, 2)), 7),
}

/** One place of the control centre grid: a module or a tile. */
sealed interface CcItem {
    val span: Span

    data class Block(val block: CcBlock, override val span: Span) : CcItem

    data class Tile(val spec: TileSpec, override val span: Span) : CcItem
}

/** Where a shown module sits: its index among all items of the grid, and its size. */
@Immutable data class CcPlacement(val index: Int, val span: Span)

/**
 * The user's arrangement of the control centre: the shown modules with their places and sizes, and
 * the tiles shown wide (icon and name) instead of as one cell. The tiles themselves and their order
 * stay in sysui_qs_tiles; a module's index counts all items before it, tiles and modules.
 */
@Immutable
data class CcLayout(val blocks: Map<CcBlock, CcPlacement>, val wideTiles: Set<String>) {

    /** The tiles of [tiles] that the connectivity module takes, if it is shown. */
    fun connectivityTiles(tiles: List<TileSpec>): List<TileSpec> =
        if (CcBlock.CONNECTIVITY in blocks) {
            tiles.filter { it.spec in CONNECTIVITY_SPECS }.take(CONNECTIVITY_KEYS)
        } else {
            emptyList()
        }

    /**
     * The items of the grid in reading order for the panel's [tiles]; modules that cannot be shown
     * right now (not in [available], or connectivity without its tiles) leave no gap.
     */
    fun arrange(tiles: List<TileSpec>, available: Set<CcBlock>): List<CcItem> {
        val inModule = connectivityTiles(tiles).toSet()
        val items: MutableList<CcItem> =
            tiles.filterNot { it in inModule }.mapTo(ArrayList()) { CcItem.Tile(it, tileSpan(it)) }
        blocks.entries
            .filter { (block, _) ->
                block in available && (block != CcBlock.CONNECTIVITY || inModule.isNotEmpty())
            }
            .sortedBy { it.value.index }
            .forEach { (block, placement) ->
                items.add(
                    placement.index.coerceAtMost(items.size),
                    CcItem.Block(block, placement.span),
                )
            }
        return items
    }

    fun tileSpan(spec: TileSpec): Span = if (spec.spec in wideTiles) Span.Wide else Span.Cell

    /** The modules' places after the grid was reordered to [items]. */
    fun withOrder(items: List<CcItem>): CcLayout =
        copy(
            blocks =
                blocks.mapValues { (block, placement) ->
                    val index = items.indexOfFirst { it is CcItem.Block && it.block == block }
                    if (index < 0) placement else placement.copy(index = index)
                }
        )

    /** Keeps the modules after [index] where they are when the item at [index] goes away. */
    fun withoutItemAt(index: Int): CcLayout =
        copy(
            blocks =
                blocks.mapValues { (_, placement) ->
                    if (placement.index > index) placement.copy(index = placement.index - 1)
                    else placement
                }
        )

    fun hide(block: CcBlock, index: Int): CcLayout =
        copy(blocks = blocks - block).withoutItemAt(index)

    fun show(block: CcBlock, index: Int): CcLayout =
        copy(blocks = blocks + (block to CcPlacement(index, block.sizes.first())))

    /** The next size of [item] (one cell / wide for tiles, the module's sizes for modules). */
    fun resized(item: CcItem): CcLayout =
        when (item) {
            is CcItem.Tile -> {
                val spec = item.spec.spec
                copy(wideTiles = if (spec in wideTiles) wideTiles - spec else wideTiles + spec)
            }
            is CcItem.Block -> {
                val placement = blocks[item.block] ?: return this
                val sizes = item.block.sizes
                val next = sizes[(sizes.indexOf(placement.span) + 1) % sizes.size]
                copy(blocks = blocks + (item.block to placement.copy(span = next)))
            }
        }

    fun serialize(): String =
        (CcBlock.entries.map { block ->
                blocks[block]?.let { "${block.name}@${it.index}=${it.span}" } ?: "!${block.name}"
            } + wideTiles.sorted().map { "$WIDE$it" })
            .joinToString(",")

    companion object {
        /**
         * Settings.Secure key, e.g. "CONNECTIVITY@0=2x2,MEDIA@1=4x2,!BRIGHTNESS,wide:flashlight".
         */
        const val SETTING = "budik_cc_blocks"
        private const val WIDE = "wide:"

        val Default =
            CcLayout(
                CcBlock.entries.associateWith { CcPlacement(it.defaultIndex, it.sizes.first()) },
                emptySet(),
            )

        /**
         * Reads [value]; the names of the first editor ("MEDIA,!BRIGHTNESS", no place) keep their
         * default places, unknown names are ignored.
         */
        fun parse(value: String?): CcLayout {
            val tokens = value.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val blocks = Default.blocks.toMutableMap()
            val wide = mutableSetOf<String>()
            for (token in tokens) {
                if (token.startsWith(WIDE)) {
                    wide += token.removePrefix(WIDE)
                    continue
                }
                val hidden = token.startsWith("!")
                val name = token.removePrefix("!").substringBefore('@')
                val block = CcBlock.entries.firstOrNull { it.name == name } ?: continue
                if (hidden) {
                    blocks -= block
                    continue
                }
                val index = token.substringAfter('@', "").substringBefore('=').toIntOrNull()
                val span = Span.parse(token.substringAfter('=', ""))?.takeIf { it in block.sizes }
                blocks[block] =
                    CcPlacement(index ?: block.defaultIndex, span ?: block.sizes.first())
            }
            return CcLayout(blocks, wide)
        }

        fun read(context: Context): CcLayout =
            parse(
                Settings.Secure.getStringForUser(
                    context.contentResolver,
                    SETTING,
                    ActivityManager.getCurrentUser(),
                )
            )

        fun write(context: Context, layout: CcLayout) {
            Settings.Secure.putStringForUser(
                context.contentResolver,
                SETTING,
                layout.serialize(),
                ActivityManager.getCurrentUser(),
            )
        }
    }
}

/** The current [CcLayout], updated when the setting changes. */
@Composable
fun rememberCcLayout(): CcLayout {
    val context = LocalContext.current
    val initial = remember(context) { CcLayout.read(context) }
    val layout by
        produceState(initial, context) {
            val observer =
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        value = CcLayout.read(context)
                    }
                }
            context.contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(CcLayout.SETTING),
                false,
                observer,
                UserHandle.USER_ALL,
            )
            awaitDispose { context.contentResolver.unregisterContentObserver(observer) }
        }
    return layout
}

/** Puts every item at the first free cell in reading order where it fits (dense packing). */
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
