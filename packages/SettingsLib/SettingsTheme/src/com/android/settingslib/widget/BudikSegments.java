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
import android.graphics.Paint;
import android.graphics.RectF;

import com.android.settingslib.widget.theme.R;

/**
 * Budík OS segmented bar, the look of the control centre brightness slider: equal mint segments
 * for the filled part, line-coloured ones for the rest. Shared by sliders and progress bars.
 */
public final class BudikSegments {
    public static final int COUNT = 20;
    private static final int DISABLED_ALPHA = 97; // 38 %

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final int mOnColor;
    private final int mOffColor;
    private final float mGap;
    private final float mRadius;

    public BudikSegments(Context context) {
        mOnColor = context.getColor(R.color.settingslib_budik_mint);
        mOffColor = context.getColor(R.color.settingslib_budik_line);
        final float density = context.getResources().getDisplayMetrics().density;
        mGap = 3 * density;
        mRadius = 2 * density;
    }

    /** Draws the bar into [left, top, left + width, top + height], {@code fraction} in 0..1. */
    public void draw(Canvas canvas, float left, float top, float width, float height,
            float fraction, boolean rtl, boolean enabled) {
        final float segment = (width - mGap * (COUNT - 1)) / COUNT;
        if (segment <= 0) {
            return;
        }
        final int filled = Math.round(Math.max(0f, Math.min(1f, fraction)) * COUNT);
        final int alpha = enabled ? 255 : DISABLED_ALPHA;
        for (int i = 0; i < COUNT; i++) {
            final float offset = i * (segment + mGap);
            final float x = rtl ? left + width - offset - segment : left + offset;
            mRect.set(x, top, x + segment, top + height);
            mPaint.setColor(i < filled ? mOnColor : mOffColor);
            mPaint.setAlpha(alpha);
            canvas.drawRoundRect(mRect, mRadius, mRadius, mPaint);
        }
    }
}
