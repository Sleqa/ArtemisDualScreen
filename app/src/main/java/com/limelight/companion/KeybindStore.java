package com.limelight.companion;

import static com.limelight.companion.KeyCatalog.VK_ALT;
import static com.limelight.companion.KeyCatalog.VK_CTRL;
import static com.limelight.companion.KeyCatalog.VK_SHIFT;
import static com.limelight.companion.KeyCatalog.VK_WIN;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Persists the companion keybinds as JSON in SharedPreferences, seeding presets on first use. */
public final class KeybindStore {

    static final String PREFS = "companion";
    static final String KEY = "keybinds_v1";

    private static final Gson GSON = new Gson();

    private final SharedPreferences prefs;

    public KeybindStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static List<Keybind> presets() {
        return new ArrayList<>(Arrays.asList(
                new Keybind("Switch window", false, false, VK_ALT, 0x09),
                new Keybind("Start menu", false, false, VK_WIN),
                new Keybind("Escape", false, false, 0x1B),
                new Keybind("Close app", false, false, VK_ALT, 0x73),
                new Keybind("Task Manager", false, false, VK_CTRL, VK_SHIFT, 0x1B),
                new Keybind("Game Bar", false, false, VK_WIN, 'G'),
                new Keybind("Show desktop", false, false, VK_WIN, 'D'),
                new Keybind("Screenshot", false, false, VK_WIN, VK_SHIFT, 'S'),
                new Keybind("Fullscreen", false, false, VK_ALT, 0x0D),
                new Keybind("Steam overlay", false, false, VK_SHIFT, 0x09)
        ));
    }

    public List<Keybind> load() {
        String json = prefs.getString(KEY, null);
        if (json == null) {
            List<Keybind> seeded = presets();
            save(seeded);
            return seeded;
        }
        try {
            Keybind[] arr = GSON.fromJson(json, Keybind[].class);
            List<Keybind> out = new ArrayList<>();
            if (arr != null) {
                for (Keybind k : arr) {
                    if (k != null && k.keys != null && k.keys.length > 0) out.add(k);
                }
            }
            return out;
        } catch (JsonParseException e) {
            return presets();
        }
    }

    public void save(List<Keybind> binds) {
        prefs.edit().putString(KEY, GSON.toJson(binds.toArray(new Keybind[0]))).apply();
    }

    public Keybind find(String id) {
        for (Keybind k : load()) if (k.id.equals(id)) return k;
        return null;
    }

    /** Inserts or replaces (by id). */
    public void upsert(Keybind bind) {
        List<Keybind> all = load();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(bind.id)) {
                all.set(i, bind);
                save(all);
                return;
            }
        }
        all.add(bind);
        save(all);
    }

    public void delete(String id) {
        List<Keybind> all = load();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(id)) {
                all.remove(i);
                break;
            }
        }
        save(all);
    }
}
