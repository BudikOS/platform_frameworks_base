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

package com.android.systemui.notifications.ui.composable

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.systemui.budik.ui.compose.Budik
import java.util.Date
import java.util.Locale

/**
 * Budík notification cards on the lock screen (export/mockups 01_lock): the newest notifications as
 * cards in the language of the receipt (app icon, "App · time", title, text), as many as fit, then
 * "+ N další oznámení". A tap opens the notification, through the bouncer when the device is
 * locked. Sensitive notifications are redacted like in the receipt.
 */
@Composable
fun BudikLockNotifications(
    actions: BudikNotificationActions,
    darkText: Boolean,
    modifier: Modifier = Modifier,
) {
    val store by actions.activeNotifications.collectAsStateWithLifecycle()
    val items = remember(store) { actions.items(store, emptySet()) }
    if (items.isEmpty()) return
    val locale = LocalConfiguration.current.locales[0]

    SubcomposeLayout(modifier.fillMaxWidth()) { constraints ->
        val loose = constraints.copy(minHeight = 0)
        val gap = CardGap.roundToPx()
        val footerHeight = FooterHeight.roundToPx()
        val room = constraints.maxHeight - if (items.size > 1) footerHeight else 0
        val cards = mutableListOf<Placeable>()
        var height = 0
        for ((index, item) in items.withIndex()) {
            if (cards.size == MAX_CARDS) break
            val card =
                subcompose(item.key) {
                        LockCard(item, actions, locale) { actions.open(item, index, items.size) }
                    }
                    .single()
                    .measure(loose)
            val next = height + (if (cards.isEmpty()) 0 else gap) + card.height
            if (next > room) break
            cards += card
            height = next
        }
        val hidden = items.size - cards.size
        val footer =
            if (hidden > 0) {
                subcompose(FooterSlot) { MoreLine(hidden, darkText) }
                    .single()
                    .measure(Constraints.fixed(constraints.maxWidth, footerHeight))
            } else {
                null
            }
        layout(constraints.maxWidth, height + (footer?.height ?: 0)) {
            var y = 0
            cards.forEach { card ->
                card.place(0, y)
                y += card.height + gap
            }
            footer?.place(0, height)
        }
    }
}

private const val MAX_CARDS = 3
private const val FooterSlot = "more"
private val CardGap = 10.dp
private val FooterHeight = 40.dp

@Composable
private fun LockCard(
    item: BudikNotificationItem,
    actions: BudikNotificationActions,
    locale: Locale,
    onOpen: () -> Unit,
) {
    val c = Budik.colors
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    val time =
        remember(item.time, locale, is24) {
            dateFormat(locale, if (is24) "Hm" else "hm").format(Date(item.time))
        }
    val shape = RoundedCornerShape(Budik.RadiusL)
    Row(
        Modifier.fillMaxWidth()
            .background(c.surface, shape)
            .border(1.dp, c.line, shape)
            .clickable(
                enabled = item.canOpen,
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onOpen,
            )
            .padding(16.dp)
    ) {
        AppIcon(item, actions)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "${item.app} · $time",
                style = Budik.meta(13.sp, c.fg3),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.title,
                style = Budik.body(17.sp, c.fg, FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.text.isNotEmpty()) {
                Text(
                    text = item.text,
                    style = Budik.body(15.sp, c.fg2),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** "+ 2 další oznámení" under the cards, readable on the wallpaper. */
@Composable
private fun MoreLine(hidden: Int, darkText: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(FooterHeight)) {
        Text(
            text = "+ $hidden ${if (hidden < 5) "další" else "dalších"} oznámení",
            style =
                Budik.body(15.sp, if (darkText) Budik.Ink else Budik.White, FontWeight.SemiBold)
                    .copy(textAlign = TextAlign.Center),
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }
}
