package com.limelight.companion;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class})
@RunWith(RobolectricTestRunner.class)
public class KeybindStoreTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(KeybindStore.PREFS, Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void firstLoadSeedsPresets() {
        List<Keybind> binds = new KeybindStore(context).load();
        assertEquals(KeybindStore.presets().size(), binds.size());
        assertEquals("Switch window", binds.get(0).name);
        assertArrayEquals(new int[]{KeyCatalog.VK_ALT, 0x09}, binds.get(0).keys);
        for (Keybind b : binds) assertFalse(b.custom);
        // Seeding is persisted so ids are stable across loads.
        assertEquals(binds.get(3).id, new KeybindStore(context).load().get(3).id);
    }

    @Test
    public void upsertAddsThenReplaces() {
        KeybindStore store = new KeybindStore(context);
        int before = store.load().size();
        Keybind quickSave = new Keybind("Quick save", true, false, 0x74);
        store.upsert(quickSave);

        List<Keybind> after = new KeybindStore(context).load();
        assertEquals(before + 1, after.size());
        Keybind last = after.get(after.size() - 1);
        assertEquals("Quick save", last.name);
        assertTrue(last.custom);
        assertArrayEquals(new int[]{0x74}, last.keys);

        last.name = "Save";
        last.hold = true;
        store.upsert(last);
        Keybind reloaded = store.find(quickSave.id);
        assertNotNull(reloaded);
        assertEquals("Save", reloaded.name);
        assertTrue(reloaded.hold);
        assertEquals(before + 1, store.load().size());
    }

    @Test
    public void deleteRemovesOnlyThatBind() {
        KeybindStore store = new KeybindStore(context);
        List<Keybind> binds = store.load();
        String id = binds.get(1).id;
        store.delete(id);
        assertNull(store.find(id));
        assertEquals(binds.size() - 1, store.load().size());
    }

    @Test
    public void emptyListStaysEmpty() {
        KeybindStore store = new KeybindStore(context);
        store.save(new java.util.ArrayList<>());
        assertEquals(0, store.load().size());
    }

    @Test
    public void corruptJsonFallsBackToPresets() {
        context.getSharedPreferences(KeybindStore.PREFS, Context.MODE_PRIVATE).edit()
                .putString(KeybindStore.KEY, "{not json").commit();
        assertEquals(KeybindStore.presets().size(), new KeybindStore(context).load().size());
    }

    @Test
    public void labelsCoverModifiersAndKeys() {
        Keybind b = new Keybind("x", true, false, KeyCatalog.VK_CTRL, KeyCatalog.VK_SHIFT, 0x1B);
        assertArrayEquals(new String[]{"Ctrl", "Shift", "Esc"}, b.keyLabels());
        assertEquals("F5", KeyCatalog.label(0x74));
    }
}
