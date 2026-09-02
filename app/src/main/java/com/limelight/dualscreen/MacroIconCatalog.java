package com.limelight.dualscreen;

import com.limelight.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The icons a macro can wear on its key. Ids are stable strings written into the macro file, so a
 * macro keeps its icon across releases even when the artwork behind an id changes; the drawables
 * themselves are Fluent UI System Icons (Microsoft, MIT License).
 */
public final class MacroIconCatalog {

    private static final List<String> IDS = new ArrayList<>();
    private static final Map<String, Integer> DRAWABLES = new HashMap<>();

    static {
        add("keyboard", R.drawable.ic_macro_keyboard);
        add("keyboard-esc", R.drawable.ic_macro_keyboard_esc);
        add("keyboard-tab", R.drawable.ic_macro_keyboard_tab);
        add("keyboard-return", R.drawable.ic_macro_keyboard_return);
        add("keyboard-space", R.drawable.ic_macro_keyboard_space);
        add("keyboard-backspace", R.drawable.ic_macro_keyboard_backspace);
        add("keyboard-caps", R.drawable.ic_macro_keyboard_caps);
        add("apple-keyboard-command", R.drawable.ic_macro_apple_keyboard_command);
        add("apple-keyboard-option", R.drawable.ic_macro_apple_keyboard_option);
        add("apple-keyboard-shift", R.drawable.ic_macro_apple_keyboard_shift);
        add("apple-keyboard-control", R.drawable.ic_macro_apple_keyboard_control);
        add("arrow-up-bold", R.drawable.ic_macro_arrow_up_bold);
        add("arrow-down-bold", R.drawable.ic_macro_arrow_down_bold);
        add("arrow-left-bold", R.drawable.ic_macro_arrow_left_bold);
        add("arrow-right-bold", R.drawable.ic_macro_arrow_right_bold);
        add("content-copy", R.drawable.ic_macro_content_copy);
        add("content-paste", R.drawable.ic_macro_content_paste);
        add("content-cut", R.drawable.ic_macro_content_cut);
        add("undo", R.drawable.ic_macro_undo);
        add("redo", R.drawable.ic_macro_redo);
        add("magnify", R.drawable.ic_macro_magnify);
        add("fullscreen", R.drawable.ic_macro_fullscreen);
        add("monitor", R.drawable.ic_macro_monitor);
        add("monitor-multiple", R.drawable.ic_macro_monitor_multiple);
        add("window-restore", R.drawable.ic_macro_window_restore);
        add("view-grid", R.drawable.ic_macro_view_grid);
        add("brightness-6", R.drawable.ic_macro_brightness_6);
        add("cog", R.drawable.ic_macro_cog);
        add("memory", R.drawable.ic_macro_memory);
        add("console", R.drawable.ic_macro_console);
        add("folder", R.drawable.ic_macro_folder);
        add("power", R.drawable.ic_macro_power);
        add("restart", R.drawable.ic_macro_restart);
        add("refresh", R.drawable.ic_macro_refresh);
        add("close", R.drawable.ic_macro_close);
        add("check", R.drawable.ic_macro_check);
        add("exit-run", R.drawable.ic_macro_exit_run);
        add("lock", R.drawable.ic_macro_lock);
        add("printer", R.drawable.ic_macro_printer);
        add("wifi", R.drawable.ic_macro_wifi);
        add("bluetooth", R.drawable.ic_macro_bluetooth);
        add("home", R.drawable.ic_macro_home);
        add("play", R.drawable.ic_macro_play);
        add("pause", R.drawable.ic_macro_pause);
        add("skip-next", R.drawable.ic_macro_skip_next);
        add("skip-previous", R.drawable.ic_macro_skip_previous);
        add("volume-high", R.drawable.ic_macro_volume_high);
        add("volume-off", R.drawable.ic_macro_volume_off);
        add("microphone", R.drawable.ic_macro_microphone);
        add("microphone-off", R.drawable.ic_macro_microphone_off);
        add("music", R.drawable.ic_macro_music);
        add("headphones", R.drawable.ic_macro_headphones);
        add("video", R.drawable.ic_macro_video);
        add("camera", R.drawable.ic_macro_camera);
        add("record-rec", R.drawable.ic_macro_record_rec);
        add("image", R.drawable.ic_macro_image);
        add("controller", R.drawable.ic_macro_controller);
        add("controller-classic", R.drawable.ic_macro_controller_classic);
        add("sword", R.drawable.ic_macro_sword);
        add("shield", R.drawable.ic_macro_shield);
        add("crosshairs", R.drawable.ic_macro_crosshairs);
        add("bullseye", R.drawable.ic_macro_bullseye);
        add("map", R.drawable.ic_macro_map);
        add("star", R.drawable.ic_macro_star);
        add("heart", R.drawable.ic_macro_heart);
        add("flash", R.drawable.ic_macro_flash);
        add("fire", R.drawable.ic_macro_fire);
        add("rocket-launch", R.drawable.ic_macro_rocket_launch);
        add("steam", R.drawable.ic_macro_steam);
        add("account-group", R.drawable.ic_macro_account_group);
        add("message-text", R.drawable.ic_macro_message_text);
        add("chat", R.drawable.ic_macro_chat);
        add("send", R.drawable.ic_macro_send);
        add("mouse", R.drawable.ic_macro_mouse);
        add("cursor-default-click", R.drawable.ic_macro_cursor_default_click);
    }

    private MacroIconCatalog() {
    }

    private static void add(String id, int drawableRes) {
        IDS.add(id);
        DRAWABLES.put(id, drawableRes);
    }

    /** All selectable icon ids, in picker order. */
    public static List<String> ids() {
        return Collections.unmodifiableList(IDS);
    }

    /** Drawable for an icon id, or 0 when the macro has no icon (or an id we no longer ship). */
    public static int drawableFor(String id) {
        if (id == null || id.isEmpty()) {
            return 0;
        }
        Integer res = DRAWABLES.get(id);
        return res != null ? res : 0;
    }
}
