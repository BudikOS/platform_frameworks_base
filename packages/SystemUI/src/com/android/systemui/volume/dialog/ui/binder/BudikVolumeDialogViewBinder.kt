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

package com.android.systemui.volume.dialog.ui.binder

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.media.AudioManager
import android.media.AudioManager.RINGER_MODE_NORMAL
import android.media.AudioManager.RINGER_MODE_SILENT
import android.media.AudioManager.RINGER_MODE_VIBRATE
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.ViewTreeObserver.InternalInsetsInfo
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.app.tracing.coroutines.launchInTraced
import com.android.app.tracing.coroutines.launchTraced
import com.android.settingslib.volume.shared.model.RingerMode
import com.android.systemui.budik.ui.compose.Budik
import com.android.systemui.budik.ui.compose.BudikIcons
import com.android.systemui.budik.ui.compose.BudikKey
import com.android.systemui.budik.ui.compose.BudikSoundPowerIcons
import com.android.systemui.budik.ui.compose.budikKeycap
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.VolumeDialogController
import com.android.systemui.util.view.listenToComputeInternalInsets
import com.android.systemui.volume.Events
import com.android.systemui.volume.dialog.dagger.scope.VolumeDialogScope
import com.android.systemui.volume.dialog.domain.interactor.VolumeDialogStateInteractor
import com.android.systemui.volume.dialog.domain.interactor.VolumeDialogVisibilityInteractor
import com.android.systemui.volume.dialog.ringer.domain.VolumeDialogRingerInteractor
import com.android.systemui.volume.dialog.ringer.shared.model.VolumeDialogRingerModel
import com.android.systemui.volume.dialog.settings.domain.VolumeDialogSettingsButtonInteractor
import com.android.systemui.volume.dialog.shared.model.VolumeDialogStateModel
import com.android.systemui.volume.dialog.shared.model.VolumeDialogStreamModel
import com.android.systemui.volume.dialog.shared.model.VolumeDialogVisibilityModel
import com.android.systemui.volume.dialog.shared.model.streamLabel
import com.android.systemui.volume.dialog.ui.viewmodel.VolumeDialogViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach

/**
 * Budík OS volume dialog (export/mockups 08_volume). Like on iOS and Pixel a volume key first shows
 * one slider of the active stream at the edge, with a key that expands the card to the ringer mode
 * segment, three vertical sliders (media, ringer, alarm) and a "Zvuk" key opening the sound
 * settings. It replaces the AOSP view binders but keeps their state, visibility and dismiss logic.
 */
@VolumeDialogScope
class BudikVolumeDialogViewBinder
@Inject
constructor(
    private val viewModel: VolumeDialogViewModel,
    private val visibilityInteractor: VolumeDialogVisibilityInteractor,
    private val stateInteractor: VolumeDialogStateInteractor,
    private val ringerInteractor: VolumeDialogRingerInteractor,
    private val settingsButtonInteractor: VolumeDialogSettingsButtonInteractor,
    private val controller: VolumeDialogController,
    private val activityStarter: ActivityStarter,
) {

    fun CoroutineScope.bind(dialog: Dialog, root: ComposeView) {
        val cardBounds = Rect()
        launchTraced("BVDVB#insets") {
            root.viewTreeObserver.listenToComputeInternalInsets {
                setTouchableInsets(InternalInsetsInfo.TOUCHABLE_INSETS_REGION)
                touchableRegion.set(cardBounds)
            }
        }
        viewModel.dialogTitle
            .filter { it.isNotEmpty() }
            .onEach { dialog.window?.setTitle(it) }
            .launchInTraced("BVDVB#dialogTitle", this)

        root.setContent {
            val progress = remember { Animatable(0f) }
            var expanded by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                viewModel.dialogVisibilityModel.conflate().collect {
                    when (it) {
                        is VolumeDialogVisibilityModel.Visible ->
                            progress.animateTo(
                                1f,
                                spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow),
                            )
                        is VolumeDialogVisibilityModel.Dismissed -> {
                            progress.animateTo(0f, tween(DISMISS_MS))
                            dialog.dismiss()
                            expanded = false
                        }
                        is VolumeDialogVisibilityModel.Invisible -> {}
                    }
                }
            }
            val state by stateInteractor.volumeDialogState.collectAsState(null)
            val ringer by ringerInteractor.ringerModel.collectAsState(null)
            val showSettings by settingsButtonInteractor.isVisible.collectAsState()
            BudikVolumeDialog(
                state = state,
                ringer = ringer,
                showSettings = showSettings,
                expanded = expanded,
                onExpand = {
                    expanded = true
                    viewModel.resetDialogTimeout()
                },
                onLevel = { stream, level -> controller.setStreamVolume(stream, level, true) },
                onRinger = { mode ->
                    ringerInteractor.setRingerMode(RingerMode(mode))
                    viewModel.resetDialogTimeout()
                },
                onSettings = {
                    activityStarter.postStartActivityDismissingKeyguard(
                        Intent(Settings.ACTION_SOUND_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        0,
                    )
                    visibilityInteractor.dismissDialog(Events.DISMISS_REASON_SETTINGS_CLICKED)
                },
                onInteract = viewModel::resetDialogTimeout,
                modifier =
                    Modifier.graphicsLayer {
                            alpha = progress.value.coerceIn(0f, 1f)
                            translationX = (1f - progress.value) * size.width
                        }
                        .onGloballyPositioned { coordinates ->
                            val r = coordinates.boundsInWindow()
                            cardBounds.set(
                                r.left.toInt(),
                                r.top.toInt(),
                                r.right.toInt(),
                                r.bottom.toInt(),
                            )
                        },
            )
        }
    }

    private companion object {
        const val DISMISS_MS = 150
    }
}

private val SliderWidth = 50.dp
private val SliderGap = 14.dp
private val SliderHeight = 218.dp

@Composable
private fun BudikVolumeDialog(
    state: VolumeDialogStateModel?,
    ringer: VolumeDialogRingerModel?,
    showSettings: Boolean,
    expanded: Boolean,
    onExpand: () -> Unit,
    onLevel: (stream: Int, level: Int) -> Unit,
    onRinger: (mode: Int) -> Unit,
    onSettings: () -> Unit,
    onInteract: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Budik.colors
    Box(
        modifier =
            Modifier.fillMaxHeight()
                .windowInsetsPadding(
                    WindowInsets.navigationBars
                        .union(WindowInsets.displayCutout)
                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Vertical)
                )
                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                modifier
                    .budikKeycap(
                        face = colors.surface,
                        shadow = colors.border,
                        radius = Budik.RadiusL,
                        border = colors.line,
                    )
                    .animateContentSize(
                        spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)
                    )
                    .padding(12.dp)
                    .width(if (expanded) SliderWidth * 3 + SliderGap * 2 else SliderWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val sliders = state?.let { budikSliders(it) }.orEmpty()
            if (!expanded) {
                val active =
                    sliders.firstOrNull { it.first.stream == state?.activeStream }
                        ?: sliders.firstOrNull()
                active?.let { (model, label, icon) ->
                    Box(Modifier.height(SliderHeight)) {
                        VolumeSlider(model, label, icon, onLevel, onInteract, showCaption = false)
                    }
                }
                Spacer(Modifier.height(10.dp))
                ExpandKey(onExpand)
                return@Column
            }
            if (ringer != null) {
                RingerSegment(ringer, onRinger)
                Spacer(Modifier.height(12.dp))
            }
            Row(
                modifier = Modifier.weight(1f, fill = false).heightIn(max = SliderHeight + 56.dp),
                horizontalArrangement = Arrangement.spacedBy(SliderGap),
            ) {
                sliders.forEach { (model, label, icon) ->
                    VolumeSlider(model, label, icon, onLevel, onInteract)
                }
            }
            if (showSettings) {
                Spacer(Modifier.height(14.dp))
                SettingsKey(onSettings)
            }
        }
    }
}

/** Media (or the active call / remote stream), ringer and alarm, as in 08_volume. */
@Composable
private fun budikSliders(
    state: VolumeDialogStateModel
): List<Triple<VolumeDialogStreamModel, String, ImageVector>> {
    val context = LocalView.current.context
    val first =
        state.streamModels[state.activeStream]?.takeIf { it.stream !in FixedStreams }
            ?: state.streamModels[AudioManager.STREAM_MUSIC]
    return listOfNotNull(
            first?.let { it to firstLabel(context, it) },
            state.streamModels[AudioManager.STREAM_RING]?.let { it to "Vyzvánění" },
            state.streamModels[AudioManager.STREAM_ALARM]?.let { it to "Budík" },
        )
        .map { (model, label) -> Triple(model, label, iconFor(model.stream)) }
}

private val FixedStreams =
    setOf(
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_RING,
        AudioManager.STREAM_ALARM,
        AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_SYSTEM,
    )

private fun firstLabel(context: Context, model: VolumeDialogStreamModel): String =
    if (model.stream == AudioManager.STREAM_MUSIC && model.remoteLabel == null) "Média"
    else runCatching { model.streamLabel(context) }.getOrDefault("Média")

private fun iconFor(stream: Int): ImageVector =
    when (stream) {
        AudioManager.STREAM_RING,
        AudioManager.STREAM_NOTIFICATION -> BudikSoundPowerIcons.Ring
        AudioManager.STREAM_ALARM -> BudikSoundPowerIcons.Alarm
        AudioManager.STREAM_VOICE_CALL,
        AudioManager.STREAM_BLUETOOTH_SCO -> BudikSoundPowerIcons.Call
        AudioManager.STREAM_MUSIC -> BudikSoundPowerIcons.Media
        else -> BudikSoundPowerIcons.Volume
    }

@Composable
private fun RingerSegment(ringer: VolumeDialogRingerModel, onRinger: (Int) -> Unit) {
    val colors = Budik.colors
    val view = LocalView.current
    val available = ringer.availableModes.map { it.value }
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .background(colors.bg, RoundedCornerShape(Budik.RadiusM))
                .padding(4.dp)
    ) {
        RingerModes.filter { (mode, _, _) -> mode in available }
            .forEach { (mode, icon, label) ->
                val active = ringer.currentRingerMode.value == mode
                Box(
                    modifier =
                        Modifier.weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(Budik.RadiusS))
                            .background(if (active) Budik.Mint else Color.Transparent)
                            .semantics {
                                contentDescription = label
                                selected = active
                                role = Role.Tab
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                if (!active) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    onRinger(mode)
                                }
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    BudikIcon(icon, if (active) Budik.Ink else colors.fg)
                }
            }
    }
}

private val RingerModes =
    listOf(
        Triple(RINGER_MODE_NORMAL, BudikSoundPowerIcons.Ring, "Zvonění"),
        Triple(RINGER_MODE_VIBRATE, BudikSoundPowerIcons.Vibrate, "Vibrace"),
        Triple(RINGER_MODE_SILENT, BudikSoundPowerIcons.Mute, "Ticho"),
    )

@Composable
private fun VolumeSlider(
    model: VolumeDialogStreamModel,
    label: String,
    icon: ImageVector,
    onLevel: (stream: Int, level: Int) -> Unit,
    onInteract: () -> Unit,
    showCaption: Boolean = true,
) {
    val colors = Budik.colors
    val view = LocalView.current
    val max = model.levelMax.coerceAtLeast(1)
    val min = model.levelMin.coerceIn(0, max)
    val level = if (model.muted) 0 else model.level.coerceIn(0, max)
    // While the finger is down the fill follows it; afterwards it follows the stream state.
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = dragFraction ?: (level.toFloat() / max)
    val shownLevel = dragFraction?.let { (it * max).roundToInt().coerceAtLeast(min) } ?: level
    val percent = shownLevel * 100 / max
    val currentOnLevel by rememberUpdatedState(onLevel)
    val currentOnInteract by rememberUpdatedState(onInteract)

    Column(
        modifier = Modifier.width(SliderWidth).fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier.weight(1f)
                    .width(SliderWidth)
                    .semantics {
                        contentDescription = label
                        stateDescription = "$percent %"
                        setProgress { target ->
                            val l = (target * max).roundToInt().coerceIn(min, max)
                            currentOnLevel(model.stream, l)
                            true
                        }
                    }
                    .pointerInput(model.stream, min, max) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            var sent = -1
                            fun follow(y: Float) {
                                val f = (1f - y / size.height).coerceIn(0f, 1f)
                                dragFraction = f
                                val l = (f * max).roundToInt().coerceIn(min, max)
                                if (l != sent) {
                                    if (sent != -1) {
                                        view.performHapticFeedback(
                                            HapticFeedbackConstants.CLOCK_TICK
                                        )
                                    }
                                    sent = l
                                    currentOnLevel(model.stream, l)
                                }
                                currentOnInteract()
                            }
                            follow(down.position.y)
                            down.consume()
                            drag(down.id) { change ->
                                follow(change.position.y)
                                change.consume()
                            }
                            dragFraction = null
                        }
                    }
                    .drawWithCache {
                        val corner = CornerRadius(Budik.RadiusM.toPx())
                        val track =
                            Path().apply {
                                addRoundRect(RoundRect(0f, 0f, size.width, size.height, corner))
                            }
                        val edge = 1.5.dp.toPx()
                        val w = 1.dp.toPx()
                        onDrawBehind {
                            drawPath(track, colors.bg)
                            val top = size.height * (1f - fraction)
                            clipPath(track) {
                                drawRect(
                                    Budik.Mint,
                                    Offset(0f, top),
                                    Size(size.width, size.height - top),
                                )
                                if (fraction > 0f && fraction < 1f) {
                                    drawRect(
                                        Budik.Ink,
                                        Offset(0f, top - edge / 2f),
                                        Size(size.width, edge),
                                    )
                                }
                            }
                            drawRoundRect(
                                colors.line,
                                Offset(w / 2f, w / 2f),
                                Size(size.width - w, size.height - w),
                                CornerRadius(corner.x - w / 2f),
                                style = Stroke(w),
                            )
                        }
                    },
            contentAlignment = Alignment.BottomCenter,
        ) {
            // The icon sits in the fill (ink on mint) unless the level is too low to cover it.
            val iconOnFill = fraction * SliderHeight.value >= IconBottom.value + 24f
            BudikIcon(
                icon,
                if (iconOnFill) Budik.Ink else colors.fg,
                Modifier.padding(bottom = IconBottom),
            )
        }
        if (!showCaption) return@Column
        Spacer(Modifier.height(8.dp))
        BasicText(
            text = label,
            style =
                Budik.body(15.sp, colors.fg, FontWeight.Bold).copy(textAlign = TextAlign.Center),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            modifier = Modifier.overflowCentered(),
        )
        Spacer(Modifier.height(4.dp))
        BasicText(text = "$percent %", style = Budik.meta(13.sp, colors.fg3), maxLines = 1)
    }
}

private val IconBottom = 14.dp

/** Lets a label wider than its column overflow symmetrically ("Vyzvánění" under a 50 dp bar). */
private fun Modifier.overflowCentered(): Modifier = layout { measurable, constraints ->
    val placeable =
        measurable.measure(constraints.copy(minWidth = 0, maxWidth = constraints.maxWidth * 2))
    val width = constraints.constrainWidth(placeable.width)
    layout(width, placeable.height) { placeable.place((width - placeable.width) / 2, 0) }
}

/** Expands the single slider to the full card: a round key with three dots. */
@Composable
private fun ExpandKey(onExpand: () -> Unit) {
    val colors = Budik.colors
    BudikKey(
        face = colors.bg,
        shadow = colors.border,
        radius = SliderWidth / 2,
        onClick = onExpand,
        modifier =
            Modifier.size(SliderWidth, SliderWidth + Budik.KeyDepth).semantics {
                contentDescription = "Další ovládání zvuku"
            },
    ) {
        Canvas(Modifier.align(Alignment.Center).size(24.dp, 6.dp)) {
            val radius = size.height / 2f
            for (i in 0..2) {
                drawCircle(
                    colors.fg,
                    radius,
                    Offset(radius + i * (size.width - 2 * radius) / 2f, radius),
                )
            }
        }
    }
}

@Composable
private fun SettingsKey(onSettings: () -> Unit) {
    val colors = Budik.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(Budik.RadiusM))
                .background(if (pressed) colors.bg else colors.surface)
                .border(1.dp, colors.line, RoundedCornerShape(Budik.RadiusM))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onSettings,
                ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BudikIcon(BudikIcons.Tune, colors.fg)
        Spacer(Modifier.width(10.dp))
        BasicText("Zvuk", style = Budik.body(16.sp, colors.fg, FontWeight.Bold))
    }
}

@Composable
private fun BudikIcon(icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Image(
        imageVector = icon,
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(24.dp),
    )
}
