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

package com.android.settingslib.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;

import androidx.annotation.NonNull;

import com.google.android.material.slider.Slider;

/**
 * Budík OS slider: the Material {@link Slider} keeps input, steps and accessibility, but is drawn
 * as the segmented bar of the control centre brightness slider (no thumb).
 * The Material track and thumb are transparent (SettingslibSliderStyle.Expressive).
 */
public class BudikSegmentSlider extends Slider {
    private final BudikSegments mSegments;
    private final float mBarHeight;

    public BudikSegmentSlider(@NonNull Context context, AttributeSet attrs) {
        super(context, attrs);
        mSegments = new BudikSegments(context);
        mBarHeight = 24 * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        final float range = getValueTo() - getValueFrom();
        if (range <= 0) {
            return;
        }
        mSegments.draw(canvas, getTrackSidePadding(), (getHeight() - mBarHeight) / 2f,
                getTrackWidth(), mBarHeight, (getValue() - getValueFrom()) / range,
                getLayoutDirection() == LAYOUT_DIRECTION_RTL, isEnabled());
    }
}
