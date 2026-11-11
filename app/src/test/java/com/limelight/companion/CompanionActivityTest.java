package com.limelight.companion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import com.limelight.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class})
@RunWith(RobolectricTestRunner.class)
public class CompanionActivityTest {

    @Test
    public void startsOnWaitingScreenWithoutAStream() {
        ActivityController<CompanionActivity> c = Robolectric.buildActivity(CompanionActivity.class).setup();
        CompanionActivity a = c.get();
        assertEquals(View.VISIBLE, a.findViewById(R.id.companion_waiting).getVisibility());
        c.pause().stop().destroy();
    }

    @Test
    public void streamStartRevealsPanelsAndStatsUpdate() {
        ActivityController<CompanionActivity> c = Robolectric.buildActivity(CompanionActivity.class).setup();
        CompanionActivity a = c.get();

        CompanionBridge.notifyStreamStarted();
        CompanionBridge.publishStats(new StreamStats(1920, 1080, 60, 59.8f, 60f, 59.8f, 0.1f,
                14, 2, 4.1f, 2.3f, "HEVC", "c2.test.decoder", 4800f));
        ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS);

        assertEquals(View.GONE, a.findViewById(R.id.companion_waiting).getVisibility());
        android.widget.TextView chip = a.findViewById(R.id.status_stats);
        assertTrue(chip.getText().toString().contains("1080p"));

        RecyclerView grid = a.findViewById(R.id.keybinds_grid);
        assertNotNull(grid.getAdapter());
        assertEquals(KeybindStore.presets().size() + 1, grid.getAdapter().getItemCount());

        // Every tab can be shown without crashing.
        int[] tabs = {R.id.tab_keyboard, R.id.tab_keybinds, R.id.tab_stats, R.id.tab_touchpad};
        for (int id : tabs) {
            a.findViewById(id).performClick();
            ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertEquals(View.VISIBLE, a.findViewById(R.id.page_touchpad).getVisibility());

        CompanionBridge.notifyStreamStopped();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(a.isFinishing());
        c.pause().stop().destroy();
    }

    @Test
    public void keybindEditorOpens() {
        ActivityController<KeybindEditorActivity> c = Robolectric.buildActivity(KeybindEditorActivity.class).setup();
        assertNotNull(c.get().findViewById(R.id.editor_keys));
        c.pause().stop().destroy();
    }
}
