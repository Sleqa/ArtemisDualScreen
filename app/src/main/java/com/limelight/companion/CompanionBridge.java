package com.limelight.companion;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;

import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.utils.ServerHelper;

/**
 * Static glue between the stream (Game / video renderer) and the second-screen companion.
 * Everything delivered to the listener arrives on the main thread.
 */
public final class CompanionBridge {

    public interface Listener {
        void onStreamStarted();

        void onStreamStopped();

        void onStats(StreamStats stats);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile Listener listener;
    private static volatile boolean streaming;
    private static volatile StreamStats lastStats;

    private CompanionBridge() {
    }

    public static void setListener(Listener l) {
        listener = l;
    }

    public static void clearListener(Listener l) {
        if (listener == l) {
            listener = null;
        }
    }

    public static boolean hasListener() {
        return listener != null;
    }

    public static boolean isStreaming() {
        return streaming;
    }

    public static StreamStats getLastStats() {
        return lastStats;
    }

    public static void notifyStreamStarted() {
        streaming = true;
        MAIN.post(() -> {
            Listener l = listener;
            if (l != null) l.onStreamStarted();
        });
    }

    public static void notifyStreamStopped() {
        streaming = false;
        lastStats = null;
        MAIN.post(() -> {
            Listener l = listener;
            if (l != null) l.onStreamStopped();
        });
    }

    /** Called from the renderer thread roughly once per second. */
    public static void publishStats(StreamStats stats) {
        lastStats = stats;
        MAIN.post(() -> {
            Listener l = listener;
            if (l != null) l.onStats(stats);
        });
    }

    /**
     * Opens the companion on the device's built-in second screen (e.g. AYN Thor) when the setting
     * is on. Skipped when "full external display" mode already owns the secondary display.
     */
    public static void launchIfEnabled(Activity parent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        PreferenceConfiguration prefs = PreferenceConfiguration.readPreferences(parent);
        if (!prefs.enableCompanionScreen || prefs.enableFullExDisplay) {
            return;
        }
        Display display = ServerHelper.getSecondaryDisplay(parent);
        if (display == null) {
            return;
        }
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(display.getDisplayId());
        Intent intent = new Intent(parent, CompanionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            parent.startActivity(intent, options.toBundle());
        } catch (RuntimeException e) {
            // A display that refuses activities (e.g. a presentation-only panel) must never
            // break the stream itself.
            e.printStackTrace();
        }
    }
}
