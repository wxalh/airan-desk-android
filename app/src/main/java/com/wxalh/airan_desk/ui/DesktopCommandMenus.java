package com.wxalh.airan_desk.ui;

import android.content.Context;
import android.content.Intent;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import com.wxalh.airan_desk.R;

public final class DesktopCommandMenus {
    public interface Listener {
        void onKeyTap(int winKey);

        void onShortcut(int[] winKeys);

        void onRemoteOperation(String action);
    }

    private DesktopCommandMenus() {
    }

    public static void showShortcutMenu(Context context, View anchor, final Listener listener) {
        PopupMenu menu = new PopupMenu(context, anchor);
        menu.getMenu().add(0, 1, 1, (CharSequence)"Esc");
        menu.getMenu().add(0, 2, 2, (CharSequence)"Tab");
        menu.getMenu().add(0, 3, 3, (CharSequence)"Win");
        menu.getMenu().add(0, 4, 4, (CharSequence)"Alt+Tab");
        menu.getMenu().add(0, 5, 5, (CharSequence)"Ctrl+Alt+Del");
        menu.getMenu().add(0, 6, 6, (CharSequence)"Ctrl+C");
        menu.getMenu().add(0, 7, 7, (CharSequence)"Ctrl+V");
        menu.getMenu().add(0, 8, 8, (CharSequence)"F5");
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener(){

            public boolean onMenuItemClick(MenuItem item) {
                if (listener == null) {
                    return true;
                }
                int id = item.getItemId();
                if (id == 1) {
                    listener.onKeyTap(27);
                } else if (id == 2) {
                    listener.onKeyTap(9);
                } else if (id == 3) {
                    listener.onKeyTap(91);
                } else if (id == 4) {
                    listener.onShortcut(new int[]{18, 9});
                } else if (id == 5) {
                    listener.onShortcut(new int[]{17, 18, 46});
                } else if (id == 6) {
                    listener.onShortcut(new int[]{17, 67});
                } else if (id == 7) {
                    listener.onShortcut(new int[]{17, 86});
                } else if (id == 8) {
                    listener.onKeyTap(116);
                }
                return true;
            }
        });
        menu.show();
    }

    public static void showFunctionKeyMenu(Context context, View anchor, final Listener listener) {
        PopupMenu menu = new PopupMenu(context, anchor);
        for (int i = 1; i <= 12; ++i) {
            menu.getMenu().add(0, i, i, (CharSequence)("F" + i));
        }
        menu.getMenu().add(0, 21, 21, (CharSequence)"Backspace");
        menu.getMenu().add(0, 22, 22, (CharSequence)"Home");
        menu.getMenu().add(0, 23, 23, (CharSequence)"End");
        menu.getMenu().add(0, 24, 24, (CharSequence)"Page Up");
        menu.getMenu().add(0, 25, 25, (CharSequence)"Page Down");
        menu.getMenu().add(0, 26, 26, (CharSequence)"Ctrl+A");
        menu.getMenu().add(0, 27, 27, (CharSequence)"Ctrl+X");
        menu.getMenu().add(0, 28, 28, (CharSequence)"Ctrl+C");
        menu.getMenu().add(0, 29, 29, (CharSequence)"Ctrl+V");
        menu.getMenu().add(0, 30, 30, (CharSequence)"Alt+F4");
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener(){

            public boolean onMenuItemClick(MenuItem item) {
                if (listener == null) {
                    return true;
                }
                int id = item.getItemId();
                if (id >= 1 && id <= 12) {
                    listener.onKeyTap(112 + id - 1);
                } else if (id == 21) {
                    listener.onKeyTap(8);
                } else if (id == 22) {
                    listener.onKeyTap(36);
                } else if (id == 23) {
                    listener.onKeyTap(35);
                } else if (id == 24) {
                    listener.onKeyTap(33);
                } else if (id == 25) {
                    listener.onKeyTap(34);
                } else if (id == 26) {
                    listener.onShortcut(new int[]{17, 65});
                } else if (id == 27) {
                    listener.onShortcut(new int[]{17, 88});
                } else if (id == 28) {
                    listener.onShortcut(new int[]{17, 67});
                } else if (id == 29) {
                    listener.onShortcut(new int[]{17, 86});
                } else if (id == 30) {
                    listener.onShortcut(new int[]{18, 115});
                }
                return true;
            }
        });
        menu.show();
    }

    public static void showRemoteOperationMenu(Context context, View anchor, final Listener listener) {
        PopupMenu menu = new PopupMenu(context, anchor);
        addRemoteOperationItem(menu, 1, context.getString(R.string.remote_operation_lock), "lock");
        addRemoteOperationItem(menu, 2, context.getString(R.string.remote_operation_logoff), "logoff");
        addRemoteOperationItem(menu, 3, context.getString(R.string.remote_operation_restart), "restart");
        addRemoteOperationItem(menu, 4, context.getString(R.string.remote_operation_shutdown), "shutdown");
        addRemoteOperationItem(menu, 5, context.getString(R.string.remote_operation_resource_manager), "resource_manager");
        addRemoteOperationItem(menu, 6, context.getString(R.string.remote_operation_task_manager), "task_manager");
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener(){

            public boolean onMenuItemClick(MenuItem item) {
                Intent intent = item.getIntent();
                if (listener != null) {
                    listener.onRemoteOperation(intent == null ? "" : intent.getAction());
                }
                return true;
            }
        });
        menu.show();
    }

    private static void addRemoteOperationItem(PopupMenu menu, int id, String label, String action) {
        MenuItem item = menu.getMenu().add(0, id, id, (CharSequence)label);
        item.setIntent(new Intent(action));
    }
}
