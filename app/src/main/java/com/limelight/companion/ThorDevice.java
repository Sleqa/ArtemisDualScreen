package com.limelight.companion;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.preference.PreferenceManager;

import com.limelight.LimeLog;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.Locale;

/**
 * AYN Thor detection and one-time tuned defaults.
 * <p>
 * Thor (Base/Pro/Max): Snapdragon 8 Gen 2, 6" 1920x1080 120 Hz AMOLED top screen, 3.92"
 * 1080x1240 60 Hz AMOLED bottom screen, Wi-Fi 7, Android 13. Thor Lite: Snapdragon 865.
 */
public final class ThorDevice {

    private static final String APPLIED_KEY = "ds_thor_defaults_applied_v1";

    private ThorDevice() {
    }

    public static boolean isAyn() {
        return contains(Build.MANUFACTURER, "ayn") || contains(Build.BRAND, "ayn");
    }

    public static boolean isThor() {
        return isAyn() && (contains(Build.MODEL, "thor") || contains(Build.DEVICE, "thor")
                || contains(Build.PRODUCT, "thor"));
    }

    /** Lite uses the older Snapdragon 865; everything else is Snapdragon 8 Gen 2 (SM8550). */
    public static boolean isThorLite() {
        return isThor() && (contains(Build.MODEL, "lite") || contains(Build.PRODUCT, "lite")
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && contains(Build.SOC_MODEL, "8250")));
    }

    private static boolean contains(String s, String needle) {
        return s != null && s.toLowerCase(Locale.ROOT).contains(needle);
    }

    /**
     * On first launch on a Thor, fill in settings the user has not chosen yet:
     * native 1080p at 120 fps (the top panel's refresh, halving frame time vs 60), the
     * Snapdragon ultra-low-latency decoder path, a matching bitrate, and the companion screen.
     * Anything the user already set is left alone, and this runs only once.
     */
    public static void applyRecommendedDefaultsOnce(Context context) {
        if (!isThor()) return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.getBoolean(APPLIED_KEY, false)) return;

        boolean lite = isThorLite();
        String res = "1920x1080";
        String fps = lite ? "60" : "120";
        SharedPreferences.Editor e = prefs.edit().putBoolean(APPLIED_KEY, true);
        if (!prefs.contains("list_resolution")) e.putString("list_resolution", res);
        if (!prefs.contains("list_fps")) e.putString("list_fps", fps);
        if (!prefs.contains("seekbar_bitrate_kbps")) {
            e.putInt("seekbar_bitrate_kbps", PreferenceConfiguration.getDefaultBitrate(res, fps));
        }
        if (!lite && !prefs.contains("checkbox_ultra_low_latency")) e.putBoolean("checkbox_ultra_low_latency", true);
        if (!prefs.contains("checkbox_enable_companion_screen")) e.putBoolean("checkbox_enable_companion_screen", true);
        e.apply();
        LimeLog.info("Applied AYN Thor recommended defaults (" + res + "@" + fps + ")");
    }
}
