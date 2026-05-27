package com.wxalh.airan_desk.ui;

import android.content.Context;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import com.wxalh.airan_desk.R;

public final class TerminalActionMenu {
    public interface Listener {
        void onSelectAll();

        void onCopy();

        void onPaste();

        void onToggleFreeSelect();
    }

    private TerminalActionMenu() {
    }

    public static void show(Context context, View anchor, boolean freeSelectEnabled, boolean hasSelection, final Listener listener) {
        PopupMenu menu = new PopupMenu(context, anchor);
        MenuItem freeSelect = menu.getMenu().add(0, 4, 4, (CharSequence)(freeSelectEnabled ? context.getString(R.string.terminal_menu_close_free_select) : context.getString(R.string.terminal_menu_free_select)));
        freeSelect.setCheckable(true);
        freeSelect.setChecked(freeSelectEnabled);
        menu.getMenu().add(0, 1, 1, (CharSequence)context.getString(R.string.terminal_menu_select_all));
        MenuItem copy = menu.getMenu().add(0, 2, 2, (CharSequence)context.getString(R.string.terminal_menu_copy));
        copy.setEnabled(hasSelection);
        menu.getMenu().add(0, 3, 3, (CharSequence)context.getString(R.string.terminal_menu_paste));
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener(){

            public boolean onMenuItemClick(MenuItem item) {
                if (listener == null) {
                    return true;
                }
                int id = item.getItemId();
                if (id == 1) {
                    listener.onSelectAll();
                } else if (id == 2) {
                    listener.onCopy();
                } else if (id == 3) {
                    listener.onPaste();
                } else if (id == 4) {
                    listener.onToggleFreeSelect();
                }
                return true;
            }
        });
        menu.show();
    }
}
