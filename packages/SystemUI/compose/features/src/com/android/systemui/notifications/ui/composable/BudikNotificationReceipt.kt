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

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.text.format.DateFormat
import android.util.Log
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.animation.scene.ContentScope
import com.android.internal.statusbar.NotificationVisibility
import com.android.systemui.budik.ui.compose.Budik
import com.android.systemui.budik.ui.compose.BudikKey
import com.android.systemui.budik.ui.compose.budikKeycap
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.statusbar.NotificationClickNotifier
import com.android.systemui.statusbar.notification.collection.NotifPipeline
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.data.repository.ActiveNotificationListRepository
import com.android.systemui.statusbar.notification.data.repository.ActiveNotificationsStore
import com.android.systemui.statusbar.notification.row.OnUserInteractionCallback
import com.android.systemui.statusbar.notification.shared.ActiveBundleModel
import com.android.systemui.statusbar.notification.shared.ActiveNotificationGroupModel
import com.android.systemui.statusbar.notification.shared.ActiveNotificationModel
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout
import com.android.systemui.statusbar.notification.stack.ui.view.NotificationScrollView
import dagger.Lazy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "BudikNotifications"

/** One line of the receipt. */
@Immutable
data class BudikNotificationItem(
    val key: String,
    /** Package and user of the posting app, the key of its icon. */
    val iconKey: String,
    val time: Long,
    val title: String,
    val text: String,
    val app: String,
    val isNew: Boolean,
    val canDismiss: Boolean,
    val canMarkRead: Boolean,
    val canReply: Boolean,
    val canOpen: Boolean,
)

/**
 * Data and actions of the Budík notification receipt. Reads the same list the notification stack
 * shows (after all filtering, incl. the lock screen) and acts on the notifications like the stack
 * rows do: open = content intent (dismissing the keyguard) + click report (auto cancel), dismiss =
 * user dismissal, clear all = the stack's clear all.
 */
class BudikNotificationActions(
    private val repository: ActiveNotificationListRepository,
    private val notifPipeline: Lazy<NotifPipeline>,
    private val onUserInteractionCallback: Lazy<OnUserInteractionCallback>,
    private val activityStarter: Lazy<ActivityStarter>,
    private val clickNotifier: Lazy<NotificationClickNotifier>,
    private val stackScrollView: Lazy<NotificationScrollView>,
    private val bgDispatcher: CoroutineDispatcher,
) {
    val activeNotifications = repository.activeNotifications

    private val icons = LruCache<String, BudikAppIcon>(ICON_CACHE_SIZE)

    fun cachedIcon(item: BudikNotificationItem): BudikAppIcon? = icons[item.iconKey]

    /**
     * The posting app's launcher icon (with the Budík icon overlays), or the notification's small
     * icon for the system and for apps without one. Decoded off the main thread, [sizePx] square.
     */
    suspend fun appIcon(item: BudikNotificationItem, context: Context, sizePx: Int): BudikAppIcon? {
        icons[item.iconKey]?.let {
            return it
        }
        val sbn = notifPipeline.get().getEntry(item.key)?.sbn ?: return null
        val smallIcon = sbn.notification.smallIcon
        val icon =
            withContext(bgDispatcher) {
                val pm = context.packageManager
                val app =
                    sbn.packageName
                        .takeIf { it !in SYSTEM_PACKAGES }
                        ?.let {
                            try {
                                pm.getApplicationInfoAsUser(it, 0, sbn.userId)
                            } catch (e: PackageManager.NameNotFoundException) {
                                null
                            }
                        }
                        ?.takeIf { it.icon != 0 }
                app?.loadIcon(pm)?.let {
                    BudikAppIcon(it.toBitmap(sizePx, sizePx).asImageBitmap(), false)
                }
                    ?: smallIcon?.loadDrawable(context)?.let {
                        BudikAppIcon(it.toBitmap(sizePx, sizePx).asImageBitmap(), true)
                    }
            }
        return icon?.also { icons.put(item.iconKey, it) }
    }

    fun items(store: ActiveNotificationsStore, seen: Set<String>): List<BudikNotificationItem> {
        val pipeline = notifPipeline.get()
        return store
            .flatten()
            .mapNotNull { model -> pipeline.getEntry(model.key)?.toItem(model, seen) }
            .sortedByDescending { it.time }
    }

    fun open(item: BudikNotificationItem, rank: Int, count: Int) {
        val entry = notifPipeline.get().getEntry(item.key) ?: return
        val intent = entry.sbn.notification.contentIntent ?: return
        val key = entry.key
        activityStarter.get().startPendingIntentDismissingKeyguard(intent) {
            clickNotifier
                .get()
                .onNotificationClick(key, NotificationVisibility.obtain(key, rank, count, true))
        }
    }

    /** "Přečteno": the app's mark-as-read action if it has one, then dismiss if possible. */
    fun markRead(item: BudikNotificationItem) {
        val entry = notifPipeline.get().getEntry(item.key) ?: return
        entry.sbn.notification.markAsReadAction()?.actionIntent?.let { send(it) }
        dismiss(item)
    }

    /** User dismissal (swipe), like swiping a row of the notification stack. */
    fun dismiss(item: BudikNotificationItem) {
        val entry = notifPipeline.get().getEntry(item.key) ?: return
        if (entry.isClearable) {
            onUserInteractionCallback
                .get()
                .registerFutureDismissal(entry, NotificationListenerService.REASON_CANCEL)
                .run()
        }
    }

    fun clearAll() {
        (stackScrollView.get().asView() as? NotificationStackScrollLayout)?.clearAllNotifications(
            /* hideSilentSection= */ false
        )
    }

    private fun send(intent: PendingIntent) {
        try {
            intent.send()
        } catch (e: PendingIntent.CanceledException) {
            Log.w(TAG, "action intent canceled", e)
        }
    }

    private fun ActiveNotificationsStore.flatten(): List<ActiveNotificationModel> =
        renderList.flatMap { key -> this[key].models() }

    private fun Any?.models(): List<ActiveNotificationModel> =
        when (this) {
            is ActiveNotificationModel -> listOf(this)
            // Show the messages of a group, not its "3 new messages" summary.
            is ActiveNotificationGroupModel -> children.ifEmpty { listOf(summary) }
            is ActiveBundleModel -> children.flatMap { it.models() }
            else -> emptyList()
        }

    private fun NotificationEntry.toItem(
        model: ActiveNotificationModel,
        seen: Set<String>,
    ): BudikNotificationItem {
        val n = sbn.notification
        val extras = n.extras
        val sensitive = isSensitive().value
        var title: CharSequence? =
            extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
                ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)
                ?: extras.getCharSequence(Notification.EXTRA_TITLE)
        var text: CharSequence? =
            extras.getCharSequence(Notification.EXTRA_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
        lastMessage(n)?.let { (sender, message) ->
            if (title == null) title = sender
            text = if (sender != null && sender != title) "$sender: $message" else message
        }
        if (sensitive) {
            title = n.publicVersion?.extras?.getCharSequence(Notification.EXTRA_TITLE)
            text = "Obsah skrytý"
        }
        val isNew = model.key !in seen && !model.isSilent
        return BudikNotificationItem(
            key = model.key,
            iconKey = "${sbn.packageName}:${sbn.userId}",
            time = model.whenTime.takeIf { it > 0 } ?: sbn.postTime,
            title = (title ?: model.appName).toString(),
            text = text?.toString().orEmpty(),
            app = model.appName,
            isNew = isNew,
            canDismiss = isClearable,
            canMarkRead = isClearable || n.markAsReadAction() != null,
            canReply = !sensitive && n.actions.orEmpty().any { it.hasFreeFormInput() },
            canOpen = n.contentIntent != null,
        )
    }

    private fun lastMessage(n: Notification): Pair<CharSequence?, CharSequence>? {
        val messages =
            n.extras.getParcelableArray(
                Notification.EXTRA_MESSAGES,
                android.os.Parcelable::class.java,
            ) ?: return null
        val last =
            Notification.MessagingStyle.Message.getMessagesFromBundleArray(messages).lastOrNull()
                ?: return null
        val text = last.text ?: return null
        return last.senderPerson?.name to text
    }

    private fun Notification.markAsReadAction(): Notification.Action? =
        actions.orEmpty().firstOrNull {
            it.semanticAction == Notification.Action.SEMANTIC_ACTION_MARK_AS_READ
        }

    private fun Notification.Action.hasFreeFormInput(): Boolean =
        remoteInputs.orEmpty().any { it.allowFreeFormInput }
}

/** An app icon for a receipt row; a [monochrome] small icon is tinted like text. */
@Immutable class BudikAppIcon(val bitmap: ImageBitmap, val monochrome: Boolean)

private const val ICON_CACHE_SIZE = 48
private val SYSTEM_PACKAGES = setOf("android", "com.android.systemui")

/** Keys already shown in the shade (no longer "nové"); lives as long as SystemUI. */
private object SeenNotifications {
    val keys = mutableSetOf<String>()
}

private fun dateFormat(locale: Locale, skeleton: String) =
    SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale)

private fun String.capitalized(locale: Locale) = replaceFirstChar { it.titlecase(locale) }

/** Large clock and date above the receipt (04_shade). */
@Composable
fun BudikShadeClock(modifier: Modifier = Modifier) {
    val c = Budik.colors
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val now by
        produceState(System.currentTimeMillis()) {
            while (true) {
                value = System.currentTimeMillis()
                delay(60_000L - value % 60_000L)
            }
        }
    val is24 = DateFormat.is24HourFormat(context)
    val time =
        remember(now, locale, is24) {
            dateFormat(locale, if (is24) "Hm" else "hm").format(Date(now))
        }
    val date =
        remember(now, locale) {
            dateFormat(locale, "EEEdMMMM").format(Date(now)).capitalized(locale)
        }
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(text = time, style = Budik.headline(46.sp, c.fg, FontWeight.ExtraBold), maxLines = 1)
        Spacer(Modifier.width(10.dp))
        Text(
            text = date,
            style = Budik.body(17.sp, c.fg3, FontWeight.SemiBold),
            maxLines = 1,
            modifier = Modifier.padding(bottom = 7.dp),
        )
    }
}

/** The "Oznámení." card laid out like a receipt (export/mockups/{light,dark}/04_shade.png). */
@Composable
fun ContentScope.BudikNotificationReceipt(
    actions: BudikNotificationActions,
    modifier: Modifier = Modifier,
) {
    val c = Budik.colors
    val locale = LocalConfiguration.current.locales[0]
    val store by actions.activeNotifications.collectAsStateWithLifecycle()
    var seenVersion by remember { mutableIntStateOf(0) }
    // Rows swiped away disappear at once, before the notification is really gone from the list.
    val swiped = remember { mutableStateListOf<String>() }
    val items =
        remember(store, seenVersion, swiped.size) {
            actions.items(store, SeenNotifications.keys).filterNot { it.key in swiped }
        }
    val currentItems by rememberUpdatedState(items)

    // Everything shown while the shade was open is no longer new once it closes.
    DisposableEffect(Unit) {
        onDispose {
            SeenNotifications.keys.clear()
            SeenNotifications.keys += currentItems.map { it.key }
        }
    }

    val shape = RoundedCornerShape(Budik.RadiusL)
    Column(
        modifier
            .fillMaxWidth()
            .background(c.surface, shape)
            .border(1.dp, c.line, shape)
            .padding(top = 16.dp, bottom = 14.dp)
    ) {
        // The date is shown once, next to the large clock above the card.
        Text(
            text = "Oznámení.",
            style = Budik.headline(30.sp, c.fg, FontWeight.ExtraBold),
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(12.dp))
        Rule(thick = true)

        if (items.isEmpty()) {
            Text(
                text = "Žádná oznámení",
                style = Budik.body(15.sp, c.fg3),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            )
        } else {
            LazyColumn(
                modifier =
                    Modifier.fillMaxWidth().weight(1f, fill = false).disableSwipesWhenScrolling()
            ) {
                items(items, key = { it.key }) { item ->
                    val index = items.indexOf(item)
                    SwipeToDismiss(
                        canDismiss = item.canDismiss,
                        onDismissed = {
                            swiped += item.key
                            actions.dismiss(item)
                        },
                        modifier = Modifier.animateItem(),
                    ) {
                        ReceiptRow(
                            item = item,
                            actions = actions,
                            locale = locale,
                            showDivider = index > 0 && !item.isNew && !items[index - 1].isNew,
                            onOpen = { actions.open(item, index, items.size) },
                            onMarkRead = {
                                SeenNotifications.keys += item.key
                                seenVersion++
                                actions.markRead(item)
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Rule(thick = true)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
        ) {
            val newCount = items.count { it.isNew }
            Text(
                text = "Celkem ${items.size} · z toho $newCount ${newWord(newCount)}",
                style = Budik.body(16.sp, c.fg, FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (items.any { it.canDismiss }) {
                Text(
                    text = "Vymazat vše",
                    style = Budik.body(16.sp, c.fg, FontWeight.Bold),
                    maxLines = 1,
                    modifier =
                        Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                                onClick = actions::clearAll,
                            )
                            .drawBehind {
                                val h = 3.dp.toPx()
                                drawRect(
                                    Budik.Mint,
                                    topLeft = Offset(0f, size.height + 1.dp.toPx()),
                                    size = Size(size.width, h),
                                )
                            }
                            .padding(vertical = 2.dp),
                )
            }
        }
    }
}

private val AppIconFrame = 36.dp
private val AppIconSize = 26.dp

/**
 * A row that can be swiped away sideways, like a row of the notification stack: past 40 % of the
 * width (or flung) it slides out and is dismissed, otherwise it springs back. Rows that cannot be
 * dismissed (ongoing) only follow the finger a little and spring back. Only the row's layer moves,
 * nothing is re-measured while dragging.
 */
@Composable
private fun SwipeToDismiss(
    canDismiss: Boolean,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val currentOnDismissed by rememberUpdatedState(onDismissed)
    Box(
        modifier
            .fillMaxWidth()
            .pointerInput(canDismiss) {
                val velocity = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = { velocity.resetTracking() },
                    onDragCancel = { scope.launch { offset.animateTo(0f, spring()) } },
                    onDragEnd = {
                        val width = size.width.toFloat()
                        val x = offset.value
                        val vx = velocity.calculateVelocity().x
                        val flung = abs(vx) > 1500f && sign(vx) == sign(x) && abs(x) > width * 0.1f
                        scope.launch {
                            if (canDismiss && (abs(x) > width * 0.4f || flung)) {
                                offset.animateTo(sign(x) * width, tween(150))
                                currentOnDismissed()
                            } else {
                                offset.animateTo(0f, spring())
                            }
                        }
                    },
                ) { change, dx ->
                    velocity.addPointerInputChange(change)
                    change.consume()
                    val target = offset.value + if (canDismiss) dx else dx * 0.25f
                    scope.launch { offset.snapTo(target) }
                }
            }
            .graphicsLayer { translationX = offset.value }
    ) {
        content()
    }
}

private fun newWord(count: Int) =
    when (count) {
        1 -> "nové"
        2,
        3,
        4 -> "nová"
        else -> "nových"
    }

@Composable
private fun Rule(thick: Boolean) {
    val c = Budik.colors
    Box(
        Modifier.padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(if (thick) 2.dp else 1.dp)
            .background(if (thick) c.fg else c.line)
    )
}

@Composable
private fun ReceiptRow(
    item: BudikNotificationItem,
    actions: BudikNotificationActions,
    locale: Locale,
    showDivider: Boolean,
    onOpen: () -> Unit,
    onMarkRead: () -> Unit,
) {
    val c = Budik.colors
    val context = LocalContext.current
    val is24 = DateFormat.is24HourFormat(context)
    val time =
        remember(item.time, locale, is24) {
            dateFormat(locale, if (is24) "Hm" else "hm").format(Date(item.time))
        }
    if (showDivider) Rule(thick = false)
    val outer =
        if (item.isNew) {
            Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                .border(2.dp, Budik.Mint, RoundedCornerShape(Budik.RadiusM))
                .padding(horizontal = 9.dp)
        } else {
            Modifier.padding(horizontal = 16.dp)
        }
    Row(
        Modifier.fillMaxWidth()
            .then(outer)
            .clickable(
                enabled = item.canOpen,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onOpen,
            )
            .padding(vertical = 12.dp)
    ) {
        AppIcon(item, actions)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = item.title,
                    style = Budik.body(15.sp, c.fg, FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = time,
                    style = Budik.meta(13.sp, c.fg3, FontWeight.Medium),
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (item.text.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.text,
                    style = Budik.body(14.sp, c.fg2),
                    maxLines = if (item.isNew) 3 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.isNew && (item.canOpen || item.canMarkRead)) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item.canOpen) {
                        ActionKey(
                            label = if (item.canReply) "Odpovědět" else "Otevřít",
                            primary = true,
                            onClick = onOpen,
                        )
                    }
                    if (item.canMarkRead) {
                        ActionKey(label = "Přečteno", primary = false, onClick = onMarkRead)
                    }
                }
            }
        }
    }
}

/** The app's icon in a keycap frame, named for TalkBack; loads off the main thread, once. */
@Composable
private fun AppIcon(item: BudikNotificationItem, actions: BudikNotificationActions) {
    val c = Budik.colors
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { AppIconSize.roundToPx() }
    val icon by
        produceState(actions.cachedIcon(item), item.iconKey, sizePx) {
            if (value == null) value = actions.appIcon(item, context, sizePx)
        }
    Box(
        Modifier.size(AppIconFrame, AppIconFrame + Budik.KeyDepth)
            .budikKeycap(c.surface, c.border, border = c.line)
            .semantics { contentDescription = item.app },
        contentAlignment = Alignment.Center,
    ) {
        icon?.let {
            Image(
                bitmap = it.bitmap,
                contentDescription = null,
                colorFilter = if (it.monochrome) ColorFilter.tint(c.fg) else null,
                modifier = Modifier.size(AppIconSize),
            )
        }
    }
}

@Composable
private fun ActionKey(label: String, primary: Boolean, onClick: () -> Unit) {
    val c = Budik.colors
    BudikKey(
        face = if (primary) c.primary else c.key,
        shadow = if (primary) c.primaryShadow else c.keyShadow,
        border = if (primary) Color.Unspecified else c.keyBorder,
        onClick = onClick,
        modifier = Modifier.height(37.dp),
    ) {
        Text(
            text = label,
            style = Budik.body(15.sp, if (primary) Color.White else c.fg, FontWeight.Bold),
            maxLines = 1,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp),
        )
    }
}
