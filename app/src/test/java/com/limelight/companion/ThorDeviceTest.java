package com.limelight.companion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowBuild;

@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class})
@RunWith(RobolectricTestRunner.class)
public class ThorDeviceTest {

    private Context context;
    private SharedPreferences prefs;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().clear().commit();
    }

    @Test
    public void nonThorDevicesAreUntouched() {
        ShadowBuild.setManufacturer("Google");
        ShadowBuild.setModel("Pixel 8");
        assertFalse(ThorDevice.isThor());
        ThorDevice.applyRecommendedDefaultsOnce(context);
        assertTrue(prefs.getAll().isEmpty());
    }

    @Test
    public void thorGetsNative1080p120AndCompanion() {
        ShadowBuild.setManufacturer("AYN");
        ShadowBuild.setModel("AYN Thor");
        assertTrue(ThorDevice.isThor());
        ThorDevice.applyRecommendedDefaultsOnce(context);
        assertEquals("1920x1080", prefs.getString("list_resolution", null));
        assertEquals("120", prefs.getString("list_fps", null));
        assertTrue(prefs.getBoolean("checkbox_ultra_low_latency", false));
        assertTrue(prefs.getBoolean("checkbox_enable_companion_screen", false));
        assertTrue(prefs.getInt("seekbar_bitrate_kbps", 0) > 0);
    }

    @Test
    public void userChoicesWinAndItOnlyRunsOnce() {
        ShadowBuild.setManufacturer("AYN");
        ShadowBuild.setModel("Thor");
        prefs.edit().putString("list_fps", "60").commit();
        ThorDevice.applyRecommendedDefaultsOnce(context);
        assertEquals("60", prefs.getString("list_fps", null));

        prefs.edit().remove("list_resolution").commit();
        ThorDevice.applyRecommendedDefaultsOnce(context);
        assertFalse(prefs.contains("list_resolution"));
    }
}
