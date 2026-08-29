package com.limelight.dualscreen;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.limelight.Game;
import com.limelight.R;
import com.limelight.binding.video.PerfOverlayListener;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.ExternalControllerView;
import com.limelight.utils.KeyConfigHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Companion panel shown on a built-in secondary screen (e.g. AYN Thor) while a stream is active
 * on the main screen. Offers a soft-keyboard toggle (forwarding typed text into the live stream
 * via {@link ExternalControllerView}'s IME bridge), a grid of tappable macro keys, a trackpad
 * mode, and a permanent column of ring gauges down the right edge showing stream FPS, end-to-end
 * latency, and the host PC's CPU/GPU/RAM load.
 */
public class SecondScreenPanelActivity extends AppCompatActivity {

    private static final int DESIRED_MACRO_BUTTON_WIDTH_DP = 84;

    // Gauge column geometry (see createGaugeColumn)
    private static final int GAUGE_SIZE_DP = 56;
    private static final int GAUGE_COLUMN_PADDING_DP = 6;
    private static final int GAUGE_SPACING_DP = 4;
    private static final int GAUGE_COLUMN_WIDTH_DP = GAUGE_SIZE_DP + GAUGE_COLUMN_PADDING_DP * 2;
    private static final int MACRO_GRID_TOP_MARGIN_DP = 72;

    private static final int COLOR_FPS = 0xFFB14AE8;
    private static final int COLOR_CPU = 0xFFFF2D6F;
    private static final int COLOR_GPU = 0xFF38B6FF;
    private static final int COLOR_RAM = 0xFF35D07F;

    // Latency gauge banding: green while the round trip is comfortable, amber as it slips,
    // then red darkening toward the 100 ms point where the ring is full.
    private static final float LATENCY_GOOD_MS = 10f;
    private static final float LATENCY_FAIR_MS = 18f;
    private static final float LATENCY_FULL_MS = 100f;
    private static final int COLOR_LATENCY_GOOD = 0xFF35D07F;
    private static final int COLOR_LATENCY_FAIR = 0xFFFFB02E;
    private static final int COLOR_LATENCY_BAD = 0xFFFF3B30;
    private static final int COLOR_LATENCY_WORST = 0xFF6E0A0A;

    /**
     * Panel root view that always acts as an IME target. When the commit-text pref is off,
     * ExternalControllerView exposes no InputConnection at all, which makes
     * InputMethodManager.showSoftInput() refuse to serve the view - forcing the flaky
     * toggleSoftInput() API whose show/hide state desyncs whenever the user dismisses the
     * keyboard themselves. Exposing a TYPE_NULL editor keeps the IME in raw key-event mode
     * (the exact input path the panel already forwards) while making explicit
     * show/hide calls reliable.
     */
    public static class PanelRootView extends ExternalControllerView {
        public PanelRootView(Context context) {
            super(context);
        }

        @Override
        public boolean onCheckIsTextEditor() {
            return true;
        }

        @Override
        public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
            InputConnection connection = super.onCreateInputConnection(outAttrs);
            if (connection == null) {
                outAttrs.inputType = InputType.TYPE_NULL;
                outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN;
                connection = new BaseInputConnection(this, false);
            }
            return connection;
        }
    }

    private PreferenceConfiguration prefConfig;
    private ExternalControllerView rootLayout;
    private RecyclerView macroRecyclerView;
    private MacroGridAdapter macroGridAdapter;
    private TextView emptyStateText;
    private TextView trackpadHint;
    private ImageButton trackpadButton;
    private ImageButton mouseModeButton;
    private TextView statsOverlayText;
    private LinearLayout gaugeColumn;
    private GaugeView fpsGauge;
    private GaugeView latencyGauge;
    private GaugeView cpuGauge;
    private GaugeView gpuGauge;
    private GaugeView ramGauge;
    private HostStatsPoller hostStatsPoller;
    private boolean trackpadEnabled = false;
    private String latencyText;
    private String hostStatsMessage;
    private final PerfOverlayListener panelPerfListener = this::onPerfTextUpdate;
    private boolean mouseModeOverridden = false;
    private int previousMouseMode = 0;

    // Index of "Trackpad (natural)" in the mouse_mode_names array (see Game.applyMouseMode)
    private static final int MOUSE_MODE_TRACKPAD_NATURAL = 2;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int failCount = 0;
    private boolean imeVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefConfig = PreferenceConfiguration.readPreferences(this);
        initViews();
    }

    private void initViews() {
        if (Game.instance == null) {
            if (failCount > 10) {
                finish();
                return;
            }
            handler.postDelayed(this::initViews, 500);
            failCount++;
            return;
        }

        WindowInsetsControllerCompat windowInsetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        windowInsetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());

        // Track the keyboard's real visibility so the toggle button can issue explicit
        // show/hide calls instead of relying on toggleSoftInput's desync-prone state.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
            ViewCompat.setOnApplyWindowInsetsListener(getWindow().getDecorView(), (v, insets) -> {
                imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
                return ViewCompat.onApplyWindowInsets(v, insets);
            });
        }

        createProgrammaticUI();
        refreshMacros();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // macroGridAdapter is only non-null once initViews() has finished waiting for
        // Game.instance, so this won't fire a premature finish() during that startup window.
        if (macroGridAdapter != null) {
            if (Game.instance == null) {
                finish();
                return;
            }
            refreshMacros();
            if (hostStatsPoller == null) {
                startHostStatsPolling();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Nothing on this screen is visible while it's backgrounded, so stop polling the host
        stopHostStatsPolling();
        if (macroGridAdapter != null && Game.instance == null) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopHostStatsPolling();
        if (Game.instance != null) {
            Game.instance.setSecondScreenPerfListener(null);
        }
    }

    // --- Input forwarding to the live stream ---
    // The soft keyboard delivers its input as key events to this (focused) activity,
    // not to the Game activity on the other display, so everything must be forwarded.

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (Game.instance != null) {
            Game.instance.handleFocusChange(hasFocus);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (Game.instance != null && Game.instance.handleKeyDown(event)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (Game.instance != null && Game.instance.handleKeyUp(event)) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
        if (Game.instance != null && Game.instance.handleKeyMultiple(event)) {
            return true;
        }
        return super.onKeyMultiple(keyCode, repeatCount, event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (Game.instance != null && Game.instance.onGenericMotionEvent(event)) {
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void createProgrammaticUI() {
        rootLayout = new PanelRootView(this);
        rootLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rootLayout.setFocusable(true);
        rootLayout.setFocusableInTouchMode(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            rootLayout.setFocusedByDefault(true);
        }
        rootLayout.setInputCallbacks(Game.instance);
        // With this pref off (the default), no InputConnection is exposed and the IME
        // falls back to raw key events, which reach Game via the onKey* overrides below -
        // the same dual path ExternalDisplayControlActivity relies on.
        rootLayout.setCommitTextEnabled(prefConfig.enableCommitText);
        // Every ancestor clips children to their bounds by default; without this the
        // quit handle disappears behind its corner container the moment it's dragged up.
        rootLayout.setClipChildren(false);
        rootLayout.setClipToPadding(false);
        setContentView(rootLayout);

        // Top-right: manage macros - the same style of plus FAB used by the macro list
        // and profiles screens (ic_settings has a broken 256dp intrinsic size that
        // renders as a cropped mess in an unscaled ImageButton)
        LinearLayout topBar = createButtonContainer(Gravity.TOP | Gravity.END);
        topBar.setFocusable(false);
        FloatingActionButton manageMacrosFab = new FloatingActionButton(this);
        manageMacrosFab.setImageResource(R.drawable.ic_add_base);
        manageMacrosFab.setSize(FloatingActionButton.SIZE_MINI);
        manageMacrosFab.setContentDescription(getString(R.string.title_manage_macros));
        manageMacrosFab.setFocusable(false);
        manageMacrosFab.setOnClickListener(v ->
                startActivity(new Intent(this, MacroListActivity.class)));
        LinearLayout.LayoutParams fabParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int fabMargin = dpToPx(8);
        fabParams.setMargins(fabMargin, fabMargin, fabMargin, fabMargin);
        manageMacrosFab.setLayoutParams(fabParams);
        topBar.addView(manageMacrosFab);
        rootLayout.addView(topBar);

        // Bottom-left: soft keyboard toggle + trackpad toggle
        LinearLayout bottomBar = createButtonContainer(Gravity.BOTTOM | Gravity.START);
        bottomBar.setFocusable(false);
        bottomBar.addView(createImageButton(R.drawable.ic_android_keyboard, v -> toggleKeyboard()));
        trackpadButton = createImageButton(R.drawable.ic_trackpad, v -> toggleTrackpad());
        trackpadButton.setAlpha(0.5f);
        bottomBar.addView(trackpadButton);
        mouseModeButton = createImageButton(R.drawable.ic_mouse, v -> toggleMouseModeOverride());
        mouseModeButton.setAlpha(0.5f);
        mouseModeButton.setVisibility(View.GONE);
        bottomBar.addView(mouseModeButton);
        rootLayout.addView(bottomBar);

        // Bottom-right: swipe-up-to-quit handle. Deliberately not a tap target -
        // ending the stream requires dragging it fully up and releasing there, so it
        // can't fire by accident (sliding back down before release cancels).
        LinearLayout quitBar = createButtonContainer(Gravity.BOTTOM | Gravity.END);
        quitBar.setFocusable(false);
        // Let the handle render outside the container's bounds while dragged upward
        quitBar.setClipChildren(false);
        quitBar.setClipToPadding(false);
        FloatingActionButton quitButton = new FloatingActionButton(this);
        quitButton.setImageResource(R.drawable.ic_close);
        quitButton.setSize(FloatingActionButton.SIZE_MINI);
        quitButton.setBackgroundTintList(ColorStateList.valueOf(0xFFD32F2F));
        quitButton.setAlpha(0.5f);
        quitButton.setContentDescription(getString(R.string.second_screen_quit_hint));
        quitButton.setFocusable(false);
        LinearLayout.LayoutParams quitParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int quitMargin = dpToPx(8);
        quitParams.setMargins(quitMargin, quitMargin, quitMargin, quitMargin);
        quitButton.setLayoutParams(quitParams);
        attachSwipeUpToQuit(quitButton);
        quitBar.addView(quitButton);
        rootLayout.addView(quitBar);

        // Macro grid
        macroRecyclerView = new RecyclerView(this);
        FrameLayout.LayoutParams gridParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        gridParams.topMargin = dpToPx(MACRO_GRID_TOP_MARGIN_DP);
        gridParams.bottomMargin = dpToPx(72);
        gridParams.leftMargin = dpToPx(8);
        // Leave the right-hand strip to the gauge column
        gridParams.rightMargin = dpToPx(GAUGE_COLUMN_WIDTH_DP + 8);
        macroRecyclerView.setLayoutParams(gridParams);

        final GridLayoutManager layoutManager = new GridLayoutManager(this, 2);
        macroRecyclerView.setLayoutManager(layoutManager);
        macroRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (macroRecyclerView.getWidth() > 0) {
                    int spanCount = Math.max(2, macroRecyclerView.getWidth() / dpToPx(DESIRED_MACRO_BUTTON_WIDTH_DP));
                    layoutManager.setSpanCount(spanCount);
                    macroRecyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            }
        });

        macroGridAdapter = new MacroGridAdapter(this, this::onMacroTapped);
        macroRecyclerView.setAdapter(macroGridAdapter);
        rootLayout.addView(macroRecyclerView);

        emptyStateText = new TextView(this);
        emptyStateText.setText(R.string.macro_list_tap_create);
        emptyStateText.setTextColor(0xFFCCCCCC);
        emptyStateText.setTextSize(14);
        emptyStateText.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams emptyParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        emptyStateText.setLayoutParams(emptyParams);
        emptyStateText.setVisibility(View.GONE);
        rootLayout.addView(emptyStateText);

        trackpadHint = new TextView(this);
        trackpadHint.setText(R.string.second_screen_trackpad_hint);
        trackpadHint.setTextColor(0x66FFFFFF);
        trackpadHint.setTextSize(14);
        trackpadHint.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        trackpadHint.setLayoutParams(hintParams);
        trackpadHint.setVisibility(View.GONE);
        rootLayout.addView(trackpadHint);

        createGaugeColumn();

        statsOverlayText = new TextView(this);
        statsOverlayText.setTextColor(0xFFCCCCCC);
        statsOverlayText.setTextSize(10);
        statsOverlayText.setGravity(Gravity.CENTER_HORIZONTAL);
        statsOverlayText.setPadding(dpToPx(8), dpToPx(2), dpToPx(8), dpToPx(2));
        FrameLayout.LayoutParams statsParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        statsParams.topMargin = dpToPx(8);
        statsParams.leftMargin = dpToPx(8);
        statsOverlayText.setLayoutParams(statsParams);
        rootLayout.addView(statsOverlayText);

        // The readout is permanent, so the stats feeds start with the panel and only stop
        // when it goes away.
        Game.instance.setSecondScreenPerfListener(panelPerfListener);
        startHostStatsPolling();
    }

    /**
     * Builds the column of ring gauges pinned down the right edge: stream FPS and total latency
     * measured on this device, then the host PC's CPU, GPU and RAM load from Vibepollo's web API.
     */
    private void createGaugeColumn() {
        gaugeColumn = new LinearLayout(this);
        gaugeColumn.setOrientation(LinearLayout.VERTICAL);
        gaugeColumn.setGravity(Gravity.CENTER_HORIZONTAL);
        gaugeColumn.setFocusable(false);
        gaugeColumn.setPadding(dpToPx(GAUGE_COLUMN_PADDING_DP), dpToPx(GAUGE_COLUMN_PADDING_DP),
                dpToPx(GAUGE_COLUMN_PADDING_DP), dpToPx(GAUGE_COLUMN_PADDING_DP));
        // Dark rounded card behind the gauges, as on the Thor's own dashboard
        GradientDrawable columnBackground = new GradientDrawable();
        columnBackground.setShape(GradientDrawable.RECTANGLE);
        columnBackground.setCornerRadius(dpToPx(18));
        columnBackground.setColor(0x99000000);
        gaugeColumn.setBackground(columnBackground);
        FrameLayout.LayoutParams columnParams = new FrameLayout.LayoutParams(
                dpToPx(GAUGE_COLUMN_WIDTH_DP), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL);
        columnParams.rightMargin = dpToPx(4);
        gaugeColumn.setLayoutParams(columnParams);

        fpsGauge = addGauge(getString(R.string.second_screen_gauge_fps), COLOR_FPS);
        latencyGauge = addGauge(getString(R.string.second_screen_gauge_latency), COLOR_LATENCY_GOOD);
        cpuGauge = addGauge(getString(R.string.second_screen_gauge_cpu), COLOR_CPU);
        gpuGauge = addGauge(getString(R.string.second_screen_gauge_gpu), COLOR_GPU);
        ramGauge = addGauge(getString(R.string.second_screen_gauge_ram), COLOR_RAM);

        rootLayout.addView(gaugeColumn);
    }

    private GaugeView addGauge(String label, int accentColor) {
        GaugeView gauge = new GaugeView(this);
        gauge.setLabel(label);
        gauge.setAccentColor(accentColor);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dpToPx(GAUGE_SIZE_DP), dpToPx(GAUGE_SIZE_DP));
        if (gaugeColumn.getChildCount() > 0) {
            params.topMargin = dpToPx(GAUGE_SPACING_DP);
        }
        gauge.setLayoutParams(params);
        gaugeColumn.addView(gauge);
        return gauge;
    }

    /**
     * Starts polling the host's Vibepollo web API for its CPU/GPU/RAM counters. Requires the
     * host credentials to be configured in settings; without them the host gauges stay blank
     * and only the client-measured FPS is shown.
     */
    private void startHostStatsPolling() {
        stopHostStatsPolling();
        if (Game.instance == null) {
            return;
        }
        String host = Game.instance.getHostAddress();
        if (host == null) {
            return;
        }
        // Re-read the settings so credentials edited since the stream started take effect
        PreferenceConfiguration prefs = PreferenceConfiguration.readPreferences(this);
        if (!HostStatsPoller.hasCredentials(prefs.hostStatsUsername, prefs.hostStatsToken)) {
            showHostStatsUnavailable(getString(R.string.second_screen_host_stats_no_credentials));
            return;
        }
        // The web interface listens one port above the stream's HTTP port unless overridden
        int webPort = prefs.hostStatsPort > 0
                ? prefs.hostStatsPort : Game.instance.getHostPort() + 1;
        hostStatsPoller = new HostStatsPoller(host, webPort, Game.instance.getServerCert(),
                prefs.hostStatsUsername, prefs.hostStatsPassword, prefs.hostStatsToken,
                new HostStatsPoller.Listener() {
                    @Override
                    public void onHostStats(HostStats stats) {
                        applyHostStats(stats);
                    }

                    @Override
                    public void onHostStatsUnavailable(String reason) {
                        if (reason != null) {
                            showHostStatsUnavailable(reason);
                        }
                    }
                });
        hostStatsPoller.start();
    }

    private void stopHostStatsPolling() {
        if (hostStatsPoller != null) {
            hostStatsPoller.stop();
            hostStatsPoller = null;
        }
    }

    private void applyHostStats(HostStats stats) {
        hostStatsMessage = null;
        if (stats.hasCpu()) {
            cpuGauge.setReading(String.format(Locale.getDefault(), "%.0f", stats.cpuPercent),
                    getString(R.string.second_screen_gauge_unit_percent), stats.cpuPercent / 100f);
        } else {
            cpuGauge.clearReading();
        }
        if (stats.hasGpu()) {
            gpuGauge.setReading(String.format(Locale.getDefault(), "%.0f", stats.gpuPercent),
                    getString(R.string.second_screen_gauge_unit_percent), stats.gpuPercent / 100f);
        } else {
            gpuGauge.clearReading();
        }
        if (stats.hasRam()) {
            ramGauge.setReading(String.format(Locale.getDefault(), "%.1f", stats.ramUsedGb()),
                    getString(R.string.second_screen_gauge_unit_gb), stats.ramFraction());
        } else {
            ramGauge.clearReading();
        }
        refreshStatsText();
    }

    private void clearHostGauges() {
        cpuGauge.clearReading();
        gpuGauge.clearReading();
        ramGauge.clearReading();
    }

    private void showHostStatsUnavailable(String reason) {
        clearHostGauges();
        hostStatsMessage = getString(R.string.second_screen_host_stats_unavailable, reason);
        refreshStatsText();
    }

    // Called on the UI thread (Game.onPerfUpdate dispatches via runOnUiThread)
    private void onPerfTextUpdate(String text) {
        latencyText = simplifyPerfText(text);

        float fps = parseFloatOrDefault(parseFps(text), -1f);
        if (fps >= 0f) {
            float target = prefConfig.fps > 0 ? prefConfig.fps : 60f;
            fpsGauge.setReading(String.format(Locale.getDefault(), "%.0f", fps), null, fps / target);
        } else {
            fpsGauge.clearReading();
        }

        updateLatencyGauge(text);
        refreshStatsText();
    }

    /**
     * Sums the three legs of the frame's journey - network round trip, host processing and
     * client decode - into one end-to-end figure. The ring fills up to the 100 ms mark and
     * stays full beyond it, while the number keeps counting, and the color runs green to amber
     * to a deepening red as the total climbs.
     */
    private void updateLatencyGauge(String text) {
        String[] lines = text.split("\n");
        float net = parseFloatOrDefault(matchGroup(
                findLine(lines, prefixOf(R.string.perf_overlay_netlatency)), "(\\d+)"), -1f);
        float host = parseFloatOrDefault(matchGroup(
                findLine(lines, prefixOf(R.string.perf_overlay_hostprocessinglatency)),
                "[\\d.,]+/[\\d.,]+/([\\d.,]+)"), -1f);
        float decode = parseFloatOrDefault(matchGroup(
                findLine(lines, prefixOf(R.string.perf_overlay_dectime)), "([\\d.,]+)"), -1f);
        if (net < 0f) {
            // Lite HUD reports the same two figures in its "5ms / 1.20ms" pair
            net = parseFloatOrDefault(matchGroup(text, "([\\d.,]+)ms\\s*/"), -1f);
            decode = parseFloatOrDefault(matchGroup(text, "/\\s*([\\d.,]+)ms"), decode);
        }

        float total = 0f;
        boolean any = false;
        for (float leg : new float[] {net, host, decode}) {
            if (leg >= 0f) {
                total += leg;
                any = true;
            }
        }
        if (!any) {
            latencyGauge.clearReading();
            return;
        }
        latencyGauge.setAccentColor(latencyColor(total));
        latencyGauge.setReading(String.format(Locale.getDefault(), "%.0f", total),
                getString(R.string.second_screen_gauge_unit_ms), total / LATENCY_FULL_MS);
    }

    private static int latencyColor(float totalMs) {
        if (totalMs < LATENCY_GOOD_MS) {
            return COLOR_LATENCY_GOOD;
        }
        if (totalMs < LATENCY_FAIR_MS) {
            return COLOR_LATENCY_FAIR;
        }
        float t = Math.min((totalMs - LATENCY_FAIR_MS) / (LATENCY_FULL_MS - LATENCY_FAIR_MS), 1f);
        return blendColor(COLOR_LATENCY_BAD, COLOR_LATENCY_WORST, t);
    }

    private static int blendColor(int from, int to, float t) {
        int r = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * t);
        int g = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * t);
        int b = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t);
        return Color.argb(255, r, g, b);
    }

    private void refreshStatsText() {
        if (statsOverlayText == null) {
            return;
        }
        if (hostStatsMessage != null && !hostStatsMessage.isEmpty()) {
            statsOverlayText.setText(latencyText == null || latencyText.isEmpty()
                    ? hostStatsMessage : latencyText + "\n" + hostStatsMessage);
        } else {
            statsOverlayText.setText(latencyText != null ? latencyText : "");
        }
    }

    /**
     * Pulls the frame rate out of the HUD text, which formats it either as part of the full
     * overlay's stream line ("Video stream: 1920x1080 119.94 FPS") or at the end of the lite
     * overlay's single line ("FPS：119.94").
     */
    private String parseFps(String text) {
        String streamLine = findLine(text.split("\n"), prefixOf(R.string.perf_overlay_streamdetails));
        String fps = matchGroup(streamLine, "x\\d+\\s+([\\d.,]+)");
        if (fps == null) {
            // Lite HUD: the frame rate is the trailing "FPS：119.94" field. This has to be tried
            // before the loose pattern below, which would otherwise latch onto the packet-loss
            // percentage that immediately precedes the "FPS" label.
            fps = matchGroup(text, "FPS[:：]\\s*([\\d.,]+)");
        }
        if (fps == null) {
            fps = matchGroup(text, "([\\d.,]+)\\s*FPS");
        }
        return fps;
    }

    private float parseFloatOrDefault(String value, float fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            // The HUD formats its numbers for the device locale, so a decimal comma is possible
            return Float.parseFloat(value.replace(',', '.'));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Condenses the multi-line HUD text into a single compact line:
     *   1920x1080 · Net 5 ms · Host 0.8 ms · Decode 1.2 ms
     * (the frame rate is left out here because it has its own gauge)
     * where Host is the host-side processing latency (only reported by Sunshine/Apollo
     * hosts) and Decode is the client-side decode time. Values are pulled from the HUD
     * lines located via their string-resource prefixes; if parsing fails the raw text is
     * shown unmodified. The lite-HUD format is a single compact line already and passes
     * through as-is.
     */
    private String simplifyPerfText(String text) {
        if (!text.contains("\n")) {
            return text;
        }
        String[] lines = text.split("\n");
        String streamLine = findLine(lines, prefixOf(R.string.perf_overlay_streamdetails));
        String netLine = findLine(lines, prefixOf(R.string.perf_overlay_netlatency));
        String hostLine = findLine(lines, prefixOf(R.string.perf_overlay_hostprocessinglatency));
        String decodeLine = findLine(lines, prefixOf(R.string.perf_overlay_dectime));

        String resolution = matchGroup(streamLine, "(\\d+x\\d+)");
        String netMs = matchGroup(netLine, "(\\d+)");
        String hostMs = matchGroup(hostLine, "[\\d.,]+/[\\d.,]+/([\\d.,]+)");
        String decodeMs = matchGroup(decodeLine, "([\\d.,]+)");

        // The frame rate has its own gauge above this line, so only the resolution is repeated here
        StringBuilder sb = new StringBuilder();
        if (resolution != null) {
            sb.append(resolution);
        }
        List<String> latencyParts = new ArrayList<>();
        if (netMs != null) {
            latencyParts.add(getString(R.string.second_screen_stats_net, netMs));
        }
        if (hostMs != null) {
            latencyParts.add(getString(R.string.second_screen_stats_host, hostMs));
        }
        if (decodeMs != null) {
            latencyParts.add(getString(R.string.second_screen_stats_decode, decodeMs));
        }
        if (!latencyParts.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(TextUtils.join(" · ", latencyParts));
        }
        return sb.length() > 0 ? sb.toString() : text;
    }

    private String findLine(String[] lines, String prefix) {
        if (prefix.isEmpty()) {
            return null;
        }
        for (String line : lines) {
            if (line.startsWith(prefix)) {
                return line;
            }
        }
        return null;
    }

    private String matchGroup(String input, String regex) {
        if (input == null) {
            return null;
        }
        Matcher matcher = Pattern.compile(regex).matcher(input);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String prefixOf(int stringRes) {
        String template = getString(stringRes);
        int firstSpecifier = template.indexOf('%');
        return firstSpecifier > 0 ? template.substring(0, firstSpecifier) : template;
    }

    /**
     * Turns the whole panel surface into a touchpad for the host PC's mouse, forwarding
     * touches into the stream the same way ExternalDisplayControlActivity's controller
     * surface does. Pointer behavior (natural/gaming trackpad, absolute) follows the
     * in-stream mouse mode selected from the game menu.
     */
    @SuppressLint("ClickableViewAccessibility")
    private void toggleTrackpad() {
        trackpadEnabled = !trackpadEnabled;
        trackpadButton.setAlpha(trackpadEnabled ? 1.0f : 0.5f);
        if (trackpadEnabled) {
            mouseModeButton.setVisibility(View.VISIBLE);
            rootLayout.setOnTouchListener((v, event) -> {
                if (Game.instance != null) {
                    Game.instance.handleMotionEvent(v, event);
                }
                return true;
            });
        } else {
            rootLayout.setOnTouchListener(null);
            mouseModeButton.setVisibility(View.GONE);
            // Leaving trackpad mode restores whatever mouse mode the stream had
            if (mouseModeOverridden) {
                if (Game.instance != null) {
                    Game.instance.setMouseMode(previousMouseMode);
                }
                mouseModeOverridden = false;
                mouseModeButton.setAlpha(0.5f);
            }
        }
        updateCenterVisibility();
    }

    /**
     * Temporarily switches the stream's mouse mode to Trackpad (natural) - the classic
     * drag-to-move-the-cursor behavior - while the panel trackpad is in use, since the
     * user's regular mode (e.g. direct touch) makes trackpad dragging act like touch
     * gestures instead. Toggling off (or exiting trackpad mode) restores the prior mode.
     */
    private void toggleMouseModeOverride() {
        if (Game.instance == null) {
            return;
        }
        if (!mouseModeOverridden) {
            previousMouseMode = Game.instance.getMouseMode();
            Game.instance.setMouseMode(MOUSE_MODE_TRACKPAD_NATURAL);
            mouseModeOverridden = true;
            mouseModeButton.setAlpha(1.0f);
        } else {
            Game.instance.setMouseMode(previousMouseMode);
            mouseModeOverridden = false;
            mouseModeButton.setAlpha(0.5f);
        }
    }

    private void onMacroTapped(KeyConfigHelper.Shortcut macro) {
        if (Game.instance == null) {
            return;
        }
        try {
            short[] keyCodes = KeyConfigHelper.resolveKeyCodes(macro.keys);
            Game.instance.sendKeys(keyCodes);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, getString(R.string.wrong_import_format), Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshMacros() {
        KeyConfigHelper.ShortcutFile file = KeyConfigHelper.loadShortcutFile(this);
        macroGridAdapter.setMacros(file != null ? file.data : null);
        updateCenterVisibility();
    }

    private void updateCenterVisibility() {
        if (trackpadEnabled) {
            macroRecyclerView.setVisibility(View.GONE);
            emptyStateText.setVisibility(View.GONE);
            trackpadHint.setVisibility(View.VISIBLE);
            return;
        }
        trackpadHint.setVisibility(View.GONE);
        boolean empty = macroGridAdapter.getItemCount() == 0;
        emptyStateText.setVisibility(empty ? View.VISIBLE : View.GONE);
        macroRecyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void toggleKeyboard() {
        InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        rootLayout.requestFocus();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Explicit show/hide keyed off the keyboard's actual (insets-reported)
            // visibility. toggleSoftInput's internal state goes stale whenever the user
            // dismisses the keyboard via back/swipe, leaving the button dead until the
            // window regains focus.
            if (imeVisible) {
                inputManager.hideSoftInputFromWindow(rootLayout.getWindowToken(), 0);
            } else {
                inputManager.showSoftInput(rootLayout, InputMethodManager.SHOW_IMPLICIT);
            }
        } else {
            inputManager.toggleSoftInput(0, 0);
        }
    }

    /**
     * Arms the quit handle: drag it up the full travel distance AND release it there to
     * end the streaming session (Game.disconnect() - the host app keeps running). The
     * handle follows the finger and brightens toward the trigger point; releasing it
     * anywhere short of the limit (including sliding back down) just springs it back.
     */
    @SuppressLint("ClickableViewAccessibility")
    private void attachSwipeUpToQuit(View handle) {
        final int triggerDistance = dpToPx(120);
        handle.setOnTouchListener(new View.OnTouchListener() {
            private float downRawY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float dy = event.getRawY() - downRawY; // negative when swiping up
                        float drag = Math.max(Math.min(dy, 0), -triggerDistance);
                        v.setTranslationY(drag);
                        v.setAlpha(0.5f + 0.5f * (-drag / triggerDistance));
                        // Pop the handle larger once releasing would trigger the quit
                        boolean armed = dy <= -triggerDistance;
                        float scale = armed ? 1.2f : 1.0f;
                        v.setScaleX(scale);
                        v.setScaleY(scale);
                        return true;
                    }
                    case MotionEvent.ACTION_UP: {
                        float dy = event.getRawY() - downRawY;
                        if (dy <= -triggerDistance) {
                            if (Game.instance != null) {
                                Game.instance.disconnect();
                            }
                            finish();
                        } else {
                            v.animate().translationY(0).alpha(0.5f).scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_CANCEL:
                        v.animate().translationY(0).alpha(0.5f).scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private LinearLayout createButtonContainer(int gravity) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(gravity);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity);
        layout.setLayoutParams(params);
        return layout;
    }

    private ImageButton createImageButton(int imageResourceId, View.OnClickListener listener) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(imageResourceId);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setOnClickListener(listener);
        button.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(56), dpToPx(56)));
        return button;
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
