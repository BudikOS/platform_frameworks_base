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

package com.android.systemui.keyguard.ui.composable.elements

import android.app.AlarmManager
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.android.systemui.budik.ui.compose.Budik
import com.android.systemui.budik.ui.compose.BudikSoundPowerIcons
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Budík lock screen header (export/mockups 01_lock): the date, a large clock in Schibsted Grotesk
 * that shrinks only to fit the width, and the next alarm. The text is dark on wallpapers that
 * support dark text and white on the others.
 */
@Composable
fun BudikLockClock(
    now: Long,
    nextAlarm: AlarmManager.AlarmClockInfo?,
    darkText: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val is24 = DateFormat.is24HourFormat(context)
    val color = if (darkText) Budik.Ink else Budik.White
    val date = remember(now, locale) { format(locale, "EEEEdMMMM", now) }
    val time = remember(now, locale, is24) { format(locale, if (is24) "Hm" else "hm", now) }

    Column(modifier) {
        BasicText(text = date, style = Budik.body(22.sp, color, FontWeight.SemiBold), maxLines = 1)
        BasicText(
            text = time,
            style =
                Budik.headline(ClockMaxSize, color, FontWeight.ExtraBold)
                    .copy(
                        fontFeatureSettings = "tnum",
                        lineHeight = 1.em,
                        lineHeightStyle =
                            LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.Both,
                            ),
                    ),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(ClockMinSize, ClockMaxSize, 4.sp),
        )
        if (nextAlarm != null) {
            val alarm =
                remember(nextAlarm.triggerTime, locale, is24) {
                    format(locale, if (is24) "EEEHm" else "EEEhm", nextAlarm.triggerTime)
                }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = BudikSoundPowerIcons.Alarm,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                BasicText(
                    text = alarm,
                    style = Budik.body(19.sp, color, FontWeight.SemiBold),
                    maxLines = 1,
                )
            }
        }
    }
}

private val ClockMaxSize = 128.sp
private val ClockMinSize = 64.sp

private fun format(locale: Locale, skeleton: String, time: Long): String =
    SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
        .format(Date(time))
        .replaceFirstChar { it.titlecase(locale) }
