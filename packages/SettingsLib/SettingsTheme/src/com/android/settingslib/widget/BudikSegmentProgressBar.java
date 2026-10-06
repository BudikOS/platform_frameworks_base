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
import android.widget.ProgressBar;

/**
 * Budík OS determinate progress bar (storage, battery) drawn as the segmented control centre
 * bar. Stays a {@link ProgressBar}, so callers keep using setProgress()/setMax(). The bar height
 * is the view height, set it with android:layout_height.
 */
public class BudikSegmentProgressBar extends ProgressBar {
    private final BudikSegments mSegments;

    public BudikSegmentProgressBar(Context context, AttributeSet attrs) {
        super(context, attrs, android.R.attr.progressBarStyleHorizontal);
        mSegments = new BudikSegments(context);
        setProgressDrawable(null);
        setIndeterminateDrawable(null);
        setIndeterminate(false);
    }

    @Override
    protected synchronized void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec), height);
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        final int range = getMax() - getMin();
        final float fraction = isIndeterminate() || range <= 0
                ? 0f : (float) (getProgress() - getMin()) / range;
        mSegments.draw(canvas, getPaddingStart(), getPaddingTop(),
                getWidth() - getPaddingStart() - getPaddingEnd(),
                getHeight() - getPaddingTop() - getPaddingBottom(), fraction,
                getLayoutDirection() == LAYOUT_DIRECTION_RTL, isEnabled());
    }
}
