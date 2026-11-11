package com.limelight.binding.video;

import android.content.Context;
import android.os.Build;
import android.os.PerformanceHintManager;

import com.limelight.LimeLog;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Android Dynamic Performance Framework (ADPF) hint session for the video pipeline threads.
 * <p>
 * Instead of keeping the CPU pinned high (wasting battery) or letting the governor ramp too
 * slowly after idle (adding frame-time spikes), we tell the system each frame's deadline and
 * how long our threads actually worked. On Snapdragon 8 Gen 2 handhelds like the AYN Thor this
 * lets the scheduler place the threads on the right cores and clock them just high enough.
 * <p>
 * Everything here is best-effort: on devices without ADPF support every call is a no-op.
 */
final class StreamPerfHint {

    private final Context context;
    private final Set<Integer> tids = new LinkedHashSet<>();
    private final long targetNanos;
    private Object session; // PerformanceHintManager.Session, kept as Object for API < 31
    private boolean failed;
    private long pendingWorkNanos;

    StreamPerfHint(Context context, int fps) {
        this.context = context.getApplicationContext();
        long period = 1_000_000_000L / Math.max(1, fps);
        // Aim to finish CPU-side work in half a frame so decode/present stay ahead of vsync.
        this.targetNanos = Math.max(1_000_000L, period / 2);
    }

    static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    /** Registers a pipeline thread. The session is (re)built lazily on the next report. */
    synchronized void addThread(int tid) {
        if (!isSupported() || failed || tid <= 0) return;
        if (tids.add(tid)) {
            closeSession();
        }
    }

    /** Adds CPU work done for the current frame by any registered thread. */
    synchronized void addWork(long nanos) {
        if (nanos > 0) pendingWorkNanos += nanos;
    }

    /** Called once per frame: reports the accumulated work for the frame that just finished. */
    synchronized void frameDone() {
        long work = pendingWorkNanos;
        pendingWorkNanos = 0;
        if (!isSupported() || failed || work <= 0 || tids.isEmpty()) return;
        try {
            if (session == null) createSession();
            if (session != null) {
                ((PerformanceHintManager.Session) session).reportActualWorkDuration(work);
            }
        } catch (RuntimeException e) {
            LimeLog.warning("ADPF report failed, disabling: " + e);
            failed = true;
            closeSession();
        }
    }

    private void createSession() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        PerformanceHintManager mgr = (PerformanceHintManager) context.getSystemService(Context.PERFORMANCE_HINT_SERVICE);
        if (mgr == null || mgr.getPreferredUpdateRateNanos() < 0) {
            failed = true;
            LimeLog.info("ADPF not supported on this device");
            return;
        }
        int[] arr = new int[tids.size()];
        int i = 0;
        for (int t : tids) arr[i++] = t;
        session = mgr.createHintSession(arr, targetNanos);
        if (session == null) {
            failed = true;
            LimeLog.info("ADPF hint session unavailable");
        } else {
            LimeLog.info("ADPF hint session created for " + arr.length + " threads, target " + targetNanos / 1000 + " us");
        }
    }

    private void closeSession() {
        if (session != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                ((PerformanceHintManager.Session) session).close();
            } catch (RuntimeException ignored) {
            }
        }
        session = null;
    }

    synchronized void release() {
        closeSession();
        tids.clear();
        pendingWorkNanos = 0;
    }
}
