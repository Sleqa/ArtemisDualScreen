package com.limelight.dualscreen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A curated, human-friendly subset of the KeyMapper VK_* constants for building macros in the
 * UI, grouped for display in the macro editor's key picker.
 */
public class MacroKeyCatalog {

    public static class KeyOption {
        public final String vkName;
        public final String label;

        public KeyOption(String vkName, String label) {
            this.vkName = vkName;
            this.label = label;
        }
    }

    public static class Group {
        public final String title;
        public final List<KeyOption> keys;

        public Group(String title, List<KeyOption> keys) {
            this.title = title;
            this.keys = keys;
        }
    }

    private static final Map<String, String> LABELS = new LinkedHashMap<>();
    private static final List<Group> GROUPS = new ArrayList<>();

    static {
        GROUPS.add(new Group("Modifiers", listOf(
                opt("VK_LCONTROL", "Ctrl"),
                opt("VK_LMENU", "Alt"),
                opt("VK_LSHIFT", "Shift"),
                opt("VK_LWIN", "Win")
        )));

        GROUPS.add(new Group("Navigation & Editing", listOf(
                opt("VK_TAB", "Tab"),
                opt("VK_RETURN", "Enter"),
                opt("VK_ESCAPE", "Esc"),
                opt("VK_SPACE", "Space"),
                opt("VK_BACK", "Backspace"),
                opt("VK_DELETE", "Delete"),
                opt("VK_INSERT", "Insert"),
                opt("VK_HOME", "Home"),
                opt("VK_END", "End"),
                opt("VK_PRIOR", "Page Up"),
                opt("VK_NEXT", "Page Down"),
                opt("VK_UP", "↑"),
                opt("VK_DOWN", "↓"),
                opt("VK_LEFT", "←"),
                opt("VK_RIGHT", "→")
        )));

        List<KeyOption> functionKeys = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            functionKeys.add(opt("VK_F" + i, "F" + i));
        }
        GROUPS.add(new Group("Function Keys", functionKeys));

        List<KeyOption> letters = new ArrayList<>();
        for (char c = 'A'; c <= 'Z'; c++) {
            letters.add(opt("VK_" + c, String.valueOf(c)));
        }
        GROUPS.add(new Group("Letters", letters));

        List<KeyOption> numbers = new ArrayList<>();
        for (int i = 0; i <= 9; i++) {
            numbers.add(opt("VK_" + i, String.valueOf(i)));
        }
        GROUPS.add(new Group("Numbers", numbers));
    }

    public static List<Group> getGroups() {
        return GROUPS;
    }

    /**
     * Best-effort human-friendly label for a stored VK_* key name, used to render a macro's
     * key-combo summary (e.g. "Ctrl + Alt + Del"). Falls back to a stripped/title-cased name for
     * keys outside the curated catalog above.
     */
    public static String displayLabel(String vkName) {
        String cached = LABELS.get(vkName);
        if (cached != null) {
            return cached;
        }
        if (vkName == null) {
            return "";
        }
        String stripped = vkName.startsWith("VK_") ? vkName.substring(3) : vkName;
        if (stripped.length() <= 1) {
            return stripped;
        }
        return stripped.charAt(0) + stripped.substring(1).toLowerCase();
    }

    private static KeyOption opt(String vkName, String label) {
        LABELS.put(vkName, label);
        return new KeyOption(vkName, label);
    }

    private static List<KeyOption> listOf(KeyOption... options) {
        List<KeyOption> list = new ArrayList<>();
        for (KeyOption o : options) {
            list.add(o);
        }
        return list;
    }
}
