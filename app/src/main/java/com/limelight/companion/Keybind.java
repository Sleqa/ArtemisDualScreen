package com.limelight.companion;

import java.util.UUID;

/** A named key combo the companion can send to the host. */
public final class Keybind {
    public String id;
    public String name;
    /** Windows VK codes in press order; modifiers come first. */
    public int[] keys;
    /** true: keys stay down while the tile is held. false: a quick tap. */
    public boolean hold;
    public boolean custom;

    public Keybind() {
    }

    public Keybind(String name, boolean custom, boolean hold, int... keys) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.custom = custom;
        this.hold = hold;
        this.keys = keys;
    }

    public String[] keyLabels() {
        String[] out = new String[keys == null ? 0 : keys.length];
        for (int i = 0; i < out.length; i++) out[i] = KeyCatalog.label(keys[i]);
        return out;
    }
}
