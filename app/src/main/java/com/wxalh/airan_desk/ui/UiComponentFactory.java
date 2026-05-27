package com.wxalh.airan_desk.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class UiComponentFactory {
    private final Context context;

    public UiComponentFactory(Context context) {
        this.context = context;
    }

    public Button primaryButton(String text) {
        Button button = baseButton(text);
        button.setTextColor(AppTheme.C_ON_PRIMARY);
        button.setBackground((Drawable)border(AppTheme.C_PRIMARY, AppTheme.C_PRIMARY, dp(8), 0));
        return button;
    }

    public Button secondaryButton(String text) {
        Button button = baseButton(text);
        button.setTextColor(AppTheme.C_SECONDARY);
        button.setBackground((Drawable)border(AppTheme.C_SECONDARY_CONTAINER, AppTheme.C_SECONDARY, dp(8), 1));
        return button;
    }

    public Button outlineButton(String text) {
        Button button = baseButton(text);
        button.setTextColor(AppTheme.C_TEXT);
        button.setBackground((Drawable)border(AppTheme.C_CONTAINER, AppTheme.C_OUTLINE, dp(8), 1));
        return button;
    }

    public Button ghostButton(String text) {
        Button button = baseButton(text);
        button.setTextColor(AppTheme.C_TEXT_MUTED);
        button.setBackground((Drawable)border(0, 0, dp(8), 0));
        return button;
    }

    public Button fileActionButton(String text, int iconRes) {
        return fileActionButton(text, this.context.getResources().getDrawable(iconRes));
    }

    public Button fileActionButton(String text, Drawable icon) {
        Button button = baseButton(text);
        button.setTextColor(AppTheme.C_TEXT);
        button.setTextSize(11.0f);
        button.setMinHeight(dp(36));
        button.setPadding(dp(6), 0, dp(6), 0);
        button.setBackground((Drawable)border(0xff2d3031, AppTheme.C_OUTLINE, dp(8), 1));
        if (icon != null) {
            icon.setBounds(0, 0, dp(16), dp(16));
        }
        button.setCompoundDrawables(icon, null, null, null);
        button.setCompoundDrawablePadding(dp(4));
        tintCompoundDrawables((TextView)button, AppTheme.C_TEXT);
        button.setGravity(17);
        return button;
    }

    public Button fileTextActionButton(String text) {
        Button button = baseButton(text);
        button.setTextColor(AppTheme.C_TEXT_MUTED);
        button.setTextSize(11.0f);
        button.setMinHeight(dp(36));
        button.setPadding(dp(6), 0, dp(6), 0);
        button.setBackground((Drawable)border(0xff262829, AppTheme.C_OUTLINE, dp(8), 1));
        button.setGravity(17);
        return button;
    }

    public Button iconButton(int iconRes) {
        Button button = ghostButton("");
        Drawable icon = this.context.getResources().getDrawable(iconRes);
        icon.setBounds(0, 0, dp(20), dp(20));
        button.setCompoundDrawables(icon, null, null, null);
        button.setGravity(17);
        tintCompoundDrawables((TextView)button, AppTheme.C_TEXT_MUTED);
        return button;
    }

    public Button floatingIconButton(int iconRes) {
        Button button = iconButton(iconRes);
        button.setMinHeight(dp(44));
        button.setPadding(0, 0, 0, 0);
        button.setBackground((Drawable)oval(0xae080808, 0x8cffffff, 1));
        tintCompoundDrawables((TextView)button, -1);
        return button;
    }

    public Button baseButton(String text) {
        Button button = new Button(this.context);
        button.setText((CharSequence)text);
        button.setAllCaps(false);
        button.setTextSize(13.0f);
        button.setTypeface(Typeface.MONOSPACE);
        button.setMinHeight(dp(44));
        button.setPadding(dp(8), 0, dp(8), 0);
        return button;
    }

    public TextView text(String text, int sp, int color, boolean bold, boolean mono) {
        TextView view = new TextView(this.context);
        view.setText((CharSequence)text);
        view.setTextColor(color);
        view.setTextSize((float)sp);
        view.setTypeface(mono ? Typeface.MONOSPACE : Typeface.DEFAULT, bold ? 1 : 0);
        return view;
    }

    public void fitSingleLineText(final TextView view, final String value, final float maxSp, final float minSp) {
        view.post(new Runnable(){

            @Override
            public void run() {
                float sp;
                int width = view.getWidth() - view.getPaddingLeft() - view.getPaddingRight();
                if (width <= 0) {
                    return;
                }
                for (sp = maxSp; sp > minSp; sp -= 0.5f) {
                    view.setTextSize(2, sp);
                    if (view.getPaint().measureText(value) <= (float)width) break;
                }
                view.setTextSize(2, Math.max(minSp, sp));
            }
        });
    }

    public GradientDrawable border(int fill, int stroke, int radius, int strokeWidth) {
        return DrawableFactory.border(fill, stroke, radius, strokeWidth);
    }

    public GradientDrawable oval(int fill, int stroke, int strokeWidth) {
        return DrawableFactory.oval(fill, stroke, strokeWidth);
    }

    public LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    public LinearLayout.LayoutParams fullWidthInput() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(44));
        lp.topMargin = dp(4);
        return lp;
    }

    public LinearLayout.LayoutParams fullWidthButton() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
        lp.topMargin = dp(8);
        lp.bottomMargin = dp(4);
        return lp;
    }

    public LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, -1, 1.0f);
    }

    public LinearLayout.LayoutParams weightWithLeftMargin() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(50), 1.0f);
        lp.leftMargin = dp(8);
        return lp;
    }

    private int dp(int value) {
        return (int)((float)value * this.context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static void tintCompoundDrawables(TextView view, int color) {
        Drawable[] drawables = view.getCompoundDrawables();
        for (Drawable drawable : drawables) {
            if (drawable == null) continue;
            drawable.mutate().setTint(color);
        }
    }
}
