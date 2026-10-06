/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.settingslib.spa.framework.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Budík OS: fixed palette (export/res/values*), not wallpaper colours. */
@Composable
internal fun materialColorScheme(isDarkTheme: Boolean): ColorScheme =
    if (isDarkTheme) BudikDarkColors else BudikLightColors

private val BudikLightColors = lightColorScheme(
    primary = Color(0xFF0B1E52),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF21D4BE),
    onPrimaryContainer = Color(0xFF0E0F11),
    inversePrimary = Color(0xFF21D4BE),
    secondary = Color(0xFF3A3E43),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3E5E8),
    onSecondaryContainer = Color(0xFF0E0F11),
    tertiary = Color(0xFF05332E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF21D4BE),
    onTertiaryContainer = Color(0xFF0E0F11),
    background = Color(0xFFF5F6F7),
    onBackground = Color(0xFF0E0F11),
    surface = Color(0xFFF5F6F7),
    onSurface = Color(0xFF0E0F11),
    surfaceVariant = Color(0xFFEEF0F2),
    onSurfaceVariant = Color(0xFF5A6370),
    surfaceTint = Color(0xFF0B1E52),
    inverseSurface = Color(0xFF0E0F11),
    inverseOnSurface = Color(0xFFFFFFFF),
    outline = Color(0xFFCFD3D8),
    outlineVariant = Color(0xFFE3E5E8),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE3E5E8),
    surfaceContainer = Color(0xFFF5F6F7),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFE3E5E8),
)

private val BudikDarkColors = darkColorScheme(
    primary = Color(0xFF21D4BE),
    onPrimary = Color(0xFF0E0F11),
    primaryContainer = Color(0xFF21D4BE),
    onPrimaryContainer = Color(0xFF0E0F11),
    inversePrimary = Color(0xFF0B1E52),
    secondary = Color(0xFFC4C8CE),
    onSecondary = Color(0xFF0E0F11),
    secondaryContainer = Color(0xFF262A2F),
    onSecondaryContainer = Color(0xFFF5F6F7),
    tertiary = Color(0xFF21D4BE),
    onTertiary = Color(0xFF0E0F11),
    tertiaryContainer = Color(0xFF05332E),
    onTertiaryContainer = Color(0xFF21D4BE),
    background = Color(0xFF0E0F11),
    onBackground = Color(0xFFF5F6F7),
    surface = Color(0xFF0E0F11),
    onSurface = Color(0xFFF5F6F7),
    surfaceVariant = Color(0xFF1A1C20),
    onSurfaceVariant = Color(0xFF8D949E),
    surfaceTint = Color(0xFF21D4BE),
    inverseSurface = Color(0xFFF5F6F7),
    inverseOnSurface = Color(0xFF0E0F11),
    outline = Color(0xFF3A3F46),
    outlineVariant = Color(0xFF262A2F),
    surfaceBright = Color(0xFF17191C),
    surfaceDim = Color(0xFF0E0F11),
    surfaceContainer = Color(0xFF0E0F11),
    surfaceContainerLow = Color(0xFF17191C),
    surfaceContainerLowest = Color(0xFF0A0B0D),
    surfaceContainerHigh = Color(0xFF17191C),
    surfaceContainerHighest = Color(0xFF262A2F),
)

val ColorScheme.divider: Color
    get() = onSurface.copy(SettingsOpacity.Divider)

val ColorScheme.surfaceTone: Color
    get() = primary.copy(SettingsOpacity.SurfaceTone)

/** The overall background color in Settings. */
val ColorScheme.settingsBackground: Color
    get() = surfaceContainer
