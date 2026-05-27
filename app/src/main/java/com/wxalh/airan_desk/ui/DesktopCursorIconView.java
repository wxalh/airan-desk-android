package com.wxalh.airan_desk.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

public final class DesktopCursorIconView extends View {
    private final boolean drawBackground;
    private final Paint fill;
    private final Paint stroke;
    private final Path cursor;
    private boolean expandedStyle;

    public DesktopCursorIconView(Context context, boolean drawBackground) {
        super(context);
        this.fill = new Paint(1);
        this.stroke = new Paint(1);
        this.cursor = new Path();
        this.expandedStyle = false;
        this.drawBackground = drawBackground;
        this.setClickable(true);
        this.setFocusable(false);
        this.fill.setStyle(Paint.Style.FILL);
        this.stroke.setStyle(Paint.Style.STROKE);
        this.stroke.setStrokeWidth(dp(1));
        this.stroke.setStrokeJoin(Paint.Join.ROUND);
        this.stroke.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setExpandedStyle(boolean expandedStyle) {
        if (this.expandedStyle == expandedStyle) {
            return;
        }
        this.expandedStyle = expandedStyle;
        this.invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = this.getWidth();
        int h = this.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        if (this.drawBackground) {
            this.fill.setColor(this.expandedStyle ? Color.argb(204, 190, 190, 190) : Color.argb(104, 8, 8, 8));
            this.stroke.setColor(this.expandedStyle ? Color.argb(180, 0, 0, 0) : Color.argb(220, 255, 255, 255));
            RectF oval = this.expandedStyle ? new RectF(0.0f, 0.0f, (float)w, (float)h) : new RectF(dp(2), dp(4), (float)(w - dp(2)), (float)(h - dp(4)));
            canvas.drawOval(oval, this.fill);
            canvas.drawOval(oval, this.stroke);
        }
        this.cursor.reset();
        this.cursor.moveTo(0.0f, 0.0f);
        this.cursor.lineTo(0.0f, 22.0f);
        this.cursor.lineTo(6.0f, 15.5f);
        this.cursor.lineTo(10.0f, 24.0f);
        this.cursor.lineTo(14.0f, 22.0f);
        this.cursor.lineTo(9.8f, 13.6f);
        this.cursor.lineTo(18.0f, 13.6f);
        this.cursor.close();
        canvas.save();
        float scale = this.drawBackground ? 0.48f : 0.9f;
        if (this.drawBackground) {
            canvas.translate((float)w * 0.5f - 9.0f * scale, (float)h * 0.5f - 12.0f * scale);
        } else {
            canvas.translate(0.0f, 0.0f);
        }
        canvas.scale(scale, scale);
        this.fill.setColor(this.expandedStyle ? Color.rgb(40, 40, 40) : Color.WHITE);
        this.stroke.setColor(Color.argb(220, 40, 40, 40));
        this.stroke.setStrokeWidth(this.drawBackground ? 2.2f : 1.8f);
        canvas.drawPath(this.cursor, this.fill);
        canvas.drawPath(this.cursor, this.stroke);
        canvas.restore();
    }

    private float dp(int value) {
        return value * this.getResources().getDisplayMetrics().density + 0.5f;
    }
}
