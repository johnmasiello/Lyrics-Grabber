package com.example.john.lyricsbuddy;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.style.LineBackgroundSpan;

/**
 * Created by john on 3/27/18.
 * Adds padding to backgroundColorSpan
 * https://medium.com/@tokudu/android-adding-padding-to-backgroundcolorspan-179ab4fae187
 */

class PaddedBackgroundColorSpan implements LineBackgroundSpan {
    private int mPadding = 0;
    private int mBackgroundColor = Color.WHITE;
    private Rect mBgRect;

    public static final float LINE_SPACING_MULTIPLIER = 1.2f;
    private static final int JUSTIFY_CENTER = 0;
    private static final int JUSTIFY_FULL = 1;
    private final int alignment = JUSTIFY_FULL;

    PaddedBackgroundColorSpan(int padding, int backgroundColor) {
        this.mPadding = padding;
        this.mBackgroundColor = backgroundColor;

        mBgRect = new Rect();
    }

    @Override
    public void drawBackground(Canvas c, Paint p, int left, int right, int top, int baseline, int bottom, CharSequence text, int start, int end, int lnum) {
        final int paintColor = p.getColor();

        Paint.FontMetricsInt fmi = p.getFontMetricsInt();
        int inflatePadding = (int) (mPadding * 1.5f);
        int lesserPadding = (int)((fmi.bottom - fmi.top) * (LINE_SPACING_MULTIPLIER - 1.0f)) >> 1;
        int paddingTop, paddingBottom;
        paddingTop = paddingBottom = lesserPadding + 2;

        switch (alignment) {
            case JUSTIFY_CENTER:
                final int textWidth = Math.round(p.measureText(text, start, end));
                final int midH = (left + right) >> 1;

                // Draw the background...
                // Assuming center alignment of text
                mBgRect.set(midH - (textWidth >> 1) - inflatePadding,
                        baseline + fmi.top - paddingTop,
                        midH + (textWidth >> 1) + inflatePadding,
                        baseline + fmi.bottom + paddingBottom);
                break;

            case JUSTIFY_FULL:
            default:
                mBgRect.set(left - inflatePadding,
                        baseline + fmi.top - paddingTop,
                        right + inflatePadding,
                        baseline + fmi.bottom + paddingBottom);
        }
        p.setColor(mBackgroundColor);
        c.drawRect(mBgRect, p);
        p.setColor(paintColor);
    }
}
