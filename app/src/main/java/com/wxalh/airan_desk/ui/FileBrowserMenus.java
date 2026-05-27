package com.wxalh.airan_desk.ui;

import android.content.Context;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import com.wxalh.airan_desk.R;
import java.util.List;

public final class FileBrowserMenus {
    public interface FileActionListener {
        void onUploadFile();

        void onUploadDirectory();

        void onRunSelected();

        void onShowMountRoots(View anchor);
    }

    public interface MountRootListener {
        void onSelected(String root);
    }

    private FileBrowserMenus() {
    }

    public static void showFileActionMenu(Context context, final View anchor, final FileActionListener listener) {
        PopupMenu menu = new PopupMenu(context, anchor);
        menu.getMenu().add(0, 4, 4, (CharSequence)context.getString(R.string.file_menu_upload_file));
        menu.getMenu().add(0, 5, 5, (CharSequence)context.getString(R.string.file_menu_upload_folder));
        menu.getMenu().add(0, 6, 6, (CharSequence)context.getString(R.string.file_action_run));
        menu.getMenu().add(0, 7, 7, (CharSequence)context.getString(R.string.file_menu_mount_points));
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener(){

            public boolean onMenuItemClick(MenuItem item) {
                if (listener == null) {
                    return true;
                }
                int id = item.getItemId();
                if (id == 4) {
                    listener.onUploadFile();
                } else if (id == 5) {
                    listener.onUploadDirectory();
                } else if (id == 6) {
                    listener.onRunSelected();
                } else if (id == 7) {
                    listener.onShowMountRoots(anchor);
                }
                return true;
            }
        });
        menu.show();
    }

    public static void showMountRootMenu(Context context, View anchor, final List<String> roots, final MountRootListener listener) {
        if (anchor == null || roots == null || roots.isEmpty()) {
            return;
        }
        PopupMenu menu = new PopupMenu(context, anchor);
        for (int i = 0; i < roots.size(); ++i) {
            menu.getMenu().add(0, i, i, (CharSequence)roots.get(i));
        }
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener(){

            public boolean onMenuItemClick(MenuItem item) {
                int index = item.getItemId();
                if (listener != null && index >= 0 && index < roots.size()) {
                    listener.onSelected(roots.get(index));
                }
                return true;
            }
        });
        menu.show();
    }
}
