package com.limelight.companion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Windows virtual-key codes (what the host receives) with display labels. */
public final class KeyCatalog {

    public static final int VK_SHIFT = 0xA0;
    public static final int VK_CTRL = 0xA2;
    public static final int VK_ALT = 0xA4;
    public static final int VK_WIN = 0x5B;

    public static final int[] MODIFIERS = {VK_CTRL, VK_ALT, VK_SHIFT, VK_WIN};

    public static final class Key {
        public final String label;
        public final int vk;

        Key(String label, int vk) {
            this.label = label;
            this.vk = vk;
        }
    }

    private static final Map<String, List<Key>> CATEGORIES = new LinkedHashMap<>();
    private static final Map<Integer, String> LABELS = new LinkedHashMap<>();

    static {
        List<Key> letters = new ArrayList<>();
        for (char c = 'A'; c <= 'Z'; c++) letters.add(new Key(String.valueOf(c), c));

        List<Key> numbers = new ArrayList<>();
        for (char c = '1'; c <= '9'; c++) numbers.add(new Key(String.valueOf(c), c));
        numbers.addAll(Arrays.asList(new Key("0", '0'), new Key("-", 0xBD), new Key("=", 0xBB),
                new Key("Num +", 0x6B), new Key("Num -", 0x6D), new Key("Num *", 0x6A), new Key("Num /", 0x6F)));

        List<Key> fkeys = new ArrayList<>();
        for (int i = 1; i <= 12; i++) fkeys.add(new Key("F" + i, 0x70 + i - 1));

        List<Key> nav = Arrays.asList(new Key("Esc", 0x1B), new Key("Tab", 0x09), new Key("Enter", 0x0D),
                new Key("Space", 0x20), new Key("⌫", 0x08), new Key("Del", 0x2E), new Key("Ins", 0x2D),
                new Key("Home", 0x24), new Key("End", 0x23), new Key("PgUp", 0x21), new Key("PgDn", 0x22),
                new Key("↑", 0x26), new Key("↓", 0x28), new Key("←", 0x25), new Key("→", 0x27),
                new Key("PrtSc", 0x2C), new Key("`", 0xC0), new Key("[", 0xDB), new Key("]", 0xDD),
                new Key("\\", 0xDC), new Key(";", 0xBA), new Key("'", 0xDE), new Key(",", 0xBC),
                new Key(".", 0xBE), new Key("/", 0xBF), new Key("Caps", 0x14));

        List<Key> media = Arrays.asList(new Key("Vol +", 0xAF), new Key("Vol -", 0xAE), new Key("Mute", 0xAD),
                new Key("Play", 0xB3), new Key("Next", 0xB0), new Key("Prev", 0xB1));

        CATEGORIES.put("Letters", letters);
        CATEGORIES.put("Numbers", numbers);
        CATEGORIES.put("F-keys", fkeys);
        CATEGORIES.put("Navigation", nav);
        CATEGORIES.put("Media", media);

        LABELS.put(VK_CTRL, "Ctrl");
        LABELS.put(VK_ALT, "Alt");
        LABELS.put(VK_SHIFT, "Shift");
        LABELS.put(VK_WIN, "Win");
        for (List<Key> list : CATEGORIES.values()) {
            for (Key k : list) LABELS.put(k.vk, k.label);
        }
    }

    private KeyCatalog() {
    }

    public static List<String> categories() {
        return new ArrayList<>(CATEGORIES.keySet());
    }

    public static List<Key> keysIn(String category) {
        List<Key> keys = CATEGORIES.get(category);
        return keys == null ? Collections.emptyList() : keys;
    }

    public static String label(int vk) {
        String l = LABELS.get(vk);
        return l != null ? l : String.format("0x%02X", vk);
    }

    public static boolean isModifier(int vk) {
        for (int m : MODIFIERS) if (m == vk) return true;
        return false;
    }
}
