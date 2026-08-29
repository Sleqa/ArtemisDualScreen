package com.limelight.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.limelight.GameMenu;

import java.lang.reflect.Field;
import java.util.List;
import java.util.ArrayList;

public class KeyConfigHelper {
    public static class ShortcutFile {
        public List<Shortcut> data;

        // Default constructor for Gson
        public ShortcutFile() {
            this.data = new ArrayList<>();
        }
        public ShortcutFile(List<Shortcut> data) {
            this.data = data;
        }
    }

    public static class Shortcut {
        public String id;
        public String name;
        public boolean sticky = false;  // Default to false
        public List<String> keys;
        // Optional icon shown on the second-screen panel's circular key. Holds a
        // MacroIconCatalog id; null or empty means the macro has no icon.
        public String icon;

        // Default constructor for Gson
        public Shortcut() {
            this.keys = new ArrayList<>();
        }

        public Shortcut(String id, String name, boolean sticky, List<String> keys) {
            this.id = id;
            this.name = name;
            this.sticky = sticky;
            this.keys = keys;
        }
    }

    public static ShortcutFile parseShortcutFile(String json) {
        Gson gson = new Gson();
        return gson.fromJson(json, ShortcutFile.class);
    }

    /**
     * Loads the user's custom shortcuts/macros from the shared "special keys" store
     * (the same SharedPreferences entry GameMenu's "Special Keys" in-stream menu reads from).
     */
    public static ShortcutFile loadShortcutFile(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(GameMenu.PREF_NAME, Context.MODE_PRIVATE);
        String json = preferences.getString(GameMenu.KEY_NAME, "");

        if (TextUtils.isEmpty(json)) {
            return new ShortcutFile(new ArrayList<>());
        }

        try {
            ShortcutFile file = parseShortcutFile(json);
            return file != null ? file : new ShortcutFile(new ArrayList<>());
        } catch (Exception e) {
            return new ShortcutFile(new ArrayList<>());
        }
    }

    /**
     * Saves the user's custom shortcuts/macros to the shared "special keys" store so both the
     * in-stream "Special Keys" menu and the second-screen macro pad stay in sync.
     */
    public static void saveShortcutFile(Context context, ShortcutFile file) {
        SharedPreferences preferences = context.getSharedPreferences(GameMenu.PREF_NAME, Context.MODE_PRIVATE);
        Gson gson = new Gson();
        preferences.edit().putString(GameMenu.KEY_NAME, gson.toJson(file)).apply();
    }

    /**
     * Resolves a shortcut's symbolic key list (e.g. "VK_LMENU", "VK_TAB", or literal "0x5B")
     * into the short[] VK code array expected by Game.sendKeys().
     */
    public static short[] resolveKeyCodes(List<String> keys) throws NoSuchFieldException, IllegalAccessException {
        short[] keyCodes = new short[keys.size()];

        for (int i = 0; i < keys.size(); i++) {
            String code = keys.get(i);
            int keycode;

            if (code.startsWith("0x")) {               // literal hex value
                keycode = Integer.parseInt(code.substring(2), 16);
            } else if (code.startsWith("VK_")) {       // symbolic constant in KeyMapper
                Field field = KeyMapper.class.getDeclaredField(code);
                keycode = field.getInt(null);
            } else {                                   // unsupported
                throw new IllegalArgumentException("Unknown key code: " + code);
            }
            keyCodes[i] = (short) keycode;
        }

        return keyCodes;
    }
}
