package com.wxalh.airan_desk.ui;

import android.content.Context;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import com.wxalh.airan_desk.R;

public final class DesktopSettingsMenus {
    public interface ValueSelection {
        void onSelected(String value);
    }

    public interface ResolutionSelection {
        void onSelected(int width, int height);
    }

    public interface AudioModeSelection {
        void onSelected(String mode);
    }

    private DesktopSettingsMenus() {
    }

    public static void showValueMenu(Context context, View anchor, String[] labels, final String[] values, String current, final ValueSelection selection) {
        PopupMenu menu = new PopupMenu(context, anchor);
        for (int i = 0; i < values.length; ++i) {
            MenuItem item = menu.getMenu().add(0, i, i, (CharSequence)labels[i]);
            item.setCheckable(true);
            item.setChecked(values[i].equals(current));
        }
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener(){

            public boolean onMenuItemClick(MenuItem item) {
                int index = item.getItemId();
                if (index >= 0 && index < values.length && selection != null) {
                    selection.onSelected(values[index]);
                }
                return true;
            }
        });
        menu.show();
    }

    public static void showResolutionMenu(Context context, View anchor, int currentWidth, int currentHeight, final ResolutionSelection selection) {
        PopupMenu menu = new PopupMenu(context, anchor);
        addResolutionItem(menu, 0, context.getString(R.string.resolution_original), 0, 0, currentWidth, currentHeight);
        addResolutionItem(menu, 1, "1280x720", 1280, 720, currentWidth, currentHeight);
        addResolutionItem(menu, 2, "1920x1080", 1920, 1080, currentWidth, currentHeight);
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener(){

            public boolean onMenuItemClick(MenuItem item) {
                int id = item.getItemId();
                if (selection == null) {
                    return true;
                }
                if (id == 1) {
                    selection.onSelected(1280, 720);
                } else if (id == 2) {
                    selection.onSelected(1920, 1080);
                } else {
                    selection.onSelected(0, 0);
                }
                return true;
            }
        });
        menu.show();
    }

    public static void showAudioModeMenu(Context context, View anchor, String currentMode, final AudioModeSelection selection) {
        showValueMenu(context, anchor, new String[]{context.getString(R.string.audio_mode_off), context.getString(R.string.audio_mode_listen), context.getString(R.string.audio_mode_call)}, new String[]{"off", "listen", "call"}, currentMode, new ValueSelection(){

            @Override
            public void onSelected(String value) {
                if (selection != null) {
                    selection.onSelected(value);
                }
            }
        });
    }

    private static void addResolutionItem(PopupMenu menu, int id, String label, int width, int height, int currentWidth, int currentHeight) {
        MenuItem item = menu.getMenu().add(0, id, id, (CharSequence)label);
        item.setCheckable(true);
        item.setChecked(currentWidth == width && currentHeight == height);
    }
}
