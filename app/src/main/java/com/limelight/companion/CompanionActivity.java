package com.limelight.companion;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.limelight.Game;
import com.limelight.R;
import com.limelight.companion.view.BatteryView;
import com.limelight.companion.view.HoldButton;
import com.limelight.companion.view.KeyboardView;
import com.limelight.companion.view.SparklineView;
import com.limelight.companion.view.TouchpadView;

import java.util.Locale;

/**
 * Second-screen companion for built-in dual-screen handhelds (AYN Thor). Runs on the secondary
 * display while the stream plays on the main one.
 */
public class CompanionActivity extends AppCompatActivity implements CompanionBridge.Listener {

    private static final String PREF_TAB = "last_tab";

    private SharedPreferences prefs;

    // Status bar
    private TextView statsText, batteryText, hostText;
    private View statusDot;
    private BatteryView batteryIcon;

    // Tabs
    private final View[] pages = new View[4];
    private final View[] tabs = new View[4];
    private View tabIndicator;
    private int currentTab = -1;

    // Pages
    private TouchpadView touchpad;
    private View touchHint;
    private TextView modsLine;
    private KeybindAdapter keybindAdapter;
    private TextView keybindsEdit, keybindsSubline;
    private StatTile tileFps, tileNet, tileHost, tileDecode;
    private final TextView[] detailValues = new TextView[8];
    private MaterialSwitch overlaySwitch;

    // Waiting overlay
    private View waiting;
    private TextView waitingBattery, waitingBatteryDetail;
    private BatteryView waitingBatteryIcon;

    private long sessionStart;
    private Boolean lastHealthy;

    // Idle dimming: an OLED-black scrim over the second screen after a period without touches.
    // Black pixels on AMOLED draw almost no power, and a static scrim means no redraws.
    private static final long IDLE_DIM_MS = 45_000;
    private final android.os.Handler idleHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private View idleScrim;
    private boolean idleDimEnabled;
    private boolean dimmed;
    private final Runnable dimRunnable = this::dim;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateBattery(intent);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(KeybindStore.PREFS, MODE_PRIVATE);
        setContentView(R.layout.companion_activity);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemBars();

        setupStatusBar();
        setupTabs();
        setupTouchpad();
        setupKeyboard();
        setupKeybinds();
        setupStats();
        setupWaiting();
        setupIdleDim();

        CompanionBridge.setListener(this);
        if (CompanionBridge.isStreaming()) {
            sessionStart = SystemClock.elapsedRealtime();
            waiting.setVisibility(View.GONE);
            StreamStats last = CompanionBridge.getLastStats();
            if (last != null) onStats(last);
        } else {
            waiting.setVisibility(View.VISIBLE);
        }
        refreshHostName();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (keybindAdapter != null) keybindAdapter.reload();
        if (overlaySwitch != null && Game.instance != null) {
            overlaySwitch.setChecked(Game.instance.isHudVisible());
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    private void hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.hide(WindowInsetsCompat.Type.systemBars());
        c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    @Override
    protected void onDestroy() {
        CompanionBridge.clearListener(this);
        try {
            unregisterReceiver(batteryReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        idleHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ------------------------------------------------------------------ status bar

    private void setupStatusBar() {
        statsText = findViewById(R.id.status_stats);
        statusDot = findViewById(R.id.status_dot);
        hostText = findViewById(R.id.status_host);
        batteryText = findViewById(R.id.status_battery);
        batteryIcon = findViewById(R.id.status_battery_icon);
        statsText.setText("—");


        HoldButton disconnect = findViewById(R.id.status_disconnect);
        disconnect.setOnConfirmed(() -> {
            Game g = Game.instance;
            if (g != null) g.disconnect();
            finishWithFade();
        });

        Intent sticky = registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (sticky != null) updateBattery(sticky);
    }

    private void refreshHostName() {
        Game g = Game.instance;
        String name = g != null ? g.getIntent().getStringExtra(Game.EXTRA_PC_NAME) : null;
        hostText.setText(name != null ? name : "");
    }

    private void updateBattery(Intent intent) {
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
        if (level < 0 || scale <= 0) return;
        float pct = level / (float) scale;
        String text = Math.round(pct * 100) + "%";
        batteryText.setText(text);
        batteryIcon.setState(pct, charging);
        if (waitingBattery != null) {
            waitingBattery.setText(text);
            waitingBatteryIcon.setState(pct, charging);
            String detail = getString(charging ? R.string.companion_battery_charging : R.string.companion_battery_on_battery);
            if (charging && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
                long ms = bm != null ? bm.computeChargeTimeRemaining() : -1;
                if (ms > 0) detail = getString(R.string.companion_battery_full_in, formatDuration(ms / 1000, false));
            }
            waitingBatteryDetail.setText(detail);
        }
    }

    // ------------------------------------------------------------------ tabs

    private void setupTabs() {
        pages[0] = findViewById(R.id.page_touchpad);
        pages[1] = findViewById(R.id.page_keyboard);
        pages[2] = findViewById(R.id.page_keybinds);
        pages[3] = findViewById(R.id.page_stats);
        tabs[0] = findViewById(R.id.tab_touchpad);
        tabs[1] = findViewById(R.id.tab_keyboard);
        tabs[2] = findViewById(R.id.tab_keybinds);
        tabs[3] = findViewById(R.id.tab_stats);
        tabIndicator = findViewById(R.id.tab_indicator);

        int[] icons = {R.drawable.companion_ic_touchpad, R.drawable.companion_ic_keyboard,
                R.drawable.companion_ic_keybinds, R.drawable.companion_ic_stats};
        int[] labels = {R.string.companion_tab_touchpad, R.string.companion_tab_keyboard,
                R.string.companion_tab_keybinds, R.string.companion_tab_stats};
        for (int i = 0; i < 4; i++) {
            final int index = i;
            ((ImageView) tabs[i].findViewById(R.id.tab_icon)).setImageResource(icons[i]);
            ((TextView) tabs[i].findViewById(R.id.tab_label)).setText(labels[i]);
            tabs[i].setContentDescription(getString(labels[i]));
            tabs[i].setOnClickListener(v -> selectTab(index, true));
        }
        int initial = Math.max(0, Math.min(3, prefs.getInt(PREF_TAB, 0)));
        tabs[0].post(() -> selectTab(initial, false));
    }

    private void selectTab(int index, boolean animate) {
        if (index == currentTab) return;
        int previous = currentTab;
        currentTab = index;
        prefs.edit().putInt(PREF_TAB, index).apply();

        for (int i = 0; i < 4; i++) {
            tabs[i].setSelected(i == index);
            ((TextView) tabs[i].findViewById(R.id.tab_label)).setTypeface(null,
                    i == index ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }

        View tab = tabs[index];
        ViewGroup.LayoutParams lp = tabIndicator.getLayoutParams();
        lp.width = tab.getWidth();
        tabIndicator.setLayoutParams(lp);
        if (animate) {
            tabIndicator.animate().translationX(tab.getLeft()).setDuration(340)
                    .setInterpolator(new OvershootInterpolator(0.9f)).start();
            tab.findViewById(R.id.tab_icon).animate().scaleX(1.15f).scaleY(1.15f).setDuration(120)
                    .withEndAction(() -> tab.findViewById(R.id.tab_icon).animate().scaleX(1f).scaleY(1f).setDuration(220).start())
                    .start();
        } else {
            tabIndicator.setTranslationX(tab.getLeft());
        }

        View in = pages[index];
        if (previous >= 0 && animate) {
            View out = pages[previous];
            float dir = index > previous ? 1 : -1;
            float shift = 18 * getResources().getDisplayMetrics().density;
            out.animate().alpha(0f).translationX(-dir * shift).setDuration(140).withEndAction(() -> {
                out.setVisibility(View.GONE);
                out.setTranslationX(0);
            }).start();
            in.setAlpha(0f);
            in.setTranslationX(dir * shift);
            in.setVisibility(View.VISIBLE);
            in.animate().alpha(1f).translationX(0).setStartDelay(60).setDuration(260)
                    .setInterpolator(new DecelerateInterpolator()).start();
        } else {
            for (int i = 0; i < 4; i++) pages[i].setVisibility(i == index ? View.VISIBLE : View.GONE);
        }
    }

    // ------------------------------------------------------------------ touchpad

    private void setupTouchpad() {
        touchpad = findViewById(R.id.touchpad);
        touchHint = findViewById(R.id.touch_hint);
        touchpad.setListener(active -> touchHint.animate().alpha(active ? 0.12f : 1f)
                .setDuration(active ? 150 : 400).start());
    }

    // ------------------------------------------------------------------ keyboard

    private void setupKeyboard() {
        modsLine = findViewById(R.id.mods_line);
        KeyboardView keyboard = findViewById(R.id.keyboard);
        keyboard.setListener(held -> {
            modsLine.setText(held.isEmpty() ? getString(R.string.companion_mods_idle)
                    : getString(R.string.companion_mods_held, TextUtils.join(" + ", held)));
            modsLine.setTextColor(getResources().getColor(held.isEmpty()
                    ? R.color.companion_text_faint : R.color.companion_accent));
        });
        findViewById(R.id.type_text).setOnClickListener(v -> showTypeTextDialog());
    }

    private void showTypeTextDialog() {
        EditText input = new EditText(this);
        input.setHint(R.string.companion_type_text_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(pad, pad / 2, pad, 0);
        wrap.addView(input);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.companion_type_text)
                .setView(wrap)
                .setPositiveButton(R.string.companion_send, (d, w) -> InputSender.sendText(input.getText().toString()))
                .setNegativeButton(R.string.companion_cancel, null)
                .create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        dialog.show();
        input.requestFocus();
    }

    // ------------------------------------------------------------------ keybinds

    private void setupKeybinds() {
        RecyclerView grid = findViewById(R.id.keybinds_grid);
        keybindsEdit = findViewById(R.id.keybinds_edit);
        keybindsSubline = findViewById(R.id.keybinds_subline);

        grid.setLayoutManager(new GridLayoutManager(this, 4));
        keybindAdapter = new KeybindAdapter(this, new KeybindStore(this), this::openEditor);
        grid.setAdapter(keybindAdapter);
        grid.setItemAnimator(new androidx.recyclerview.widget.DefaultItemAnimator());

        ItemTouchHelper helper = new ItemTouchHelper(keybindAdapter.dragCallback());
        helper.attachToRecyclerView(grid);
        keybindAdapter.setDragStarter(helper::startDrag);

        keybindsEdit.setOnClickListener(v -> {
            boolean editing = !keybindAdapter.isEditing();
            keybindAdapter.setEditing(editing);
            keybindsEdit.setSelected(editing);
            keybindsEdit.setText(editing ? R.string.companion_done : R.string.companion_edit);
            keybindsSubline.setText(editing ? R.string.companion_keybinds_editing : R.string.companion_keybinds_idle);
        });
    }

    private void openEditor(String keybindId) {
        Intent i = new Intent(this, KeybindEditorActivity.class);
        if (keybindId != null) i.putExtra(KeybindEditorActivity.EXTRA_ID, keybindId);
        startActivity(i);
    }

    // ------------------------------------------------------------------ stats

    private static final class StatTile {
        final TextView value, sub;
        final SparklineView spark;
        float shown = Float.NaN;
        ValueAnimator anim;
        final int decimals;

        StatTile(View root, String label, String unit, int decimals) {
            ((TextView) root.findViewById(R.id.stat_label)).setText(label);
            ((TextView) root.findViewById(R.id.stat_unit)).setText(unit);
            value = root.findViewById(R.id.stat_value);
            sub = root.findViewById(R.id.stat_sub);
            spark = root.findViewById(R.id.stat_spark);
            this.decimals = decimals;
        }

        void set(float v, String subText) {
            sub.setText(subText);
            if (v < 0) {
                value.setText("—");
                return;
            }
            spark.push(v);
            float from = Float.isNaN(shown) ? v : shown;
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat(from, v).setDuration(420);
            anim.setInterpolator(new DecelerateInterpolator());
            anim.addUpdateListener(a -> {
                shown = (float) a.getAnimatedValue();
                value.setText(String.format(Locale.US, decimals == 0 ? "%.0f" : "%.1f", shown));
            });
            anim.start();
        }

        void reset() {
            shown = Float.NaN;
            value.setText("—");
            sub.setText("");
            spark.clear();
        }
    }

    private void setupStats() {
        tileFps = new StatTile(findViewById(R.id.tile_fps), getString(R.string.companion_frame_rate), getString(R.string.companion_fps), 0);
        tileNet = new StatTile(findViewById(R.id.tile_net), getString(R.string.companion_network), getString(R.string.companion_ms), 0);
        tileHost = new StatTile(findViewById(R.id.tile_host), getString(R.string.companion_host), getString(R.string.companion_ms), 1);
        tileDecode = new StatTile(findViewById(R.id.tile_decode), getString(R.string.companion_decode), getString(R.string.companion_ms), 1);

        GridLayout details = findViewById(R.id.stats_details);
        int[] keys = {R.string.companion_resolution, R.string.companion_codec, R.string.companion_bandwidth,
                R.string.companion_network_drops, R.string.companion_decoder, R.string.companion_session_time,
                R.string.companion_received_fps, R.string.companion_rendered_fps};
        float d = getResources().getDisplayMetrics().density;
        for (int i = 0; i < keys.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, (int) (8 * d), 0, (int) (8 * d));

            TextView k = new TextView(this);
            k.setText(keys[i]);
            k.setTextColor(getResources().getColor(R.color.companion_text_secondary));
            k.setTextSize(13);
            k.setMaxLines(1);
            row.addView(k, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView v = new TextView(this);
            v.setText("—");
            v.setTextColor(getResources().getColor(R.color.companion_text_soft));
            v.setTextSize(12);
            v.setTypeface(android.graphics.Typeface.MONOSPACE);
            v.setGravity(Gravity.END);
            v.setMaxLines(1);
            v.setEllipsize(TextUtils.TruncateAt.START);
            LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            vlp.setMarginStart((int) (8 * d));
            row.addView(v, vlp);
            detailValues[i] = v;

            GridLayout.LayoutParams glp = new GridLayout.LayoutParams(
                    GridLayout.spec(i / 2, 1f), GridLayout.spec(i % 2, 1f));
            glp.width = 0;
            glp.setMarginEnd(i % 2 == 0 ? (int) (24 * d) : 0);
            details.addView(row, glp);
        }

        overlaySwitch = findViewById(R.id.stats_overlay_switch);
        overlaySwitch.setOnCheckedChangeListener((b, checked) -> {
            Game g = Game.instance;
            if (g != null && g.isHudVisible() != checked) g.toggleHUD();
        });
    }

    @Override
    public void onStats(StreamStats s) {
        String res = s.shortResolution();
        statsText.setText(String.format(Locale.US, "%.0f fps · %d ms · %s", s.totalFps, s.rttMs, res));
        boolean healthy = s.rttMs < 40 && s.networkDropPercent < 2f
                && (s.targetFps <= 0 || s.totalFps >= s.targetFps * 0.9f);
        if (lastHealthy == null || lastHealthy != healthy) {
            lastHealthy = healthy;
            statusDot.getBackground().mutate().setTint(getResources().getColor(
                    healthy ? R.color.companion_accent : R.color.companion_warning));
            statusDot.setScaleX(1.8f);
            statusDot.setScaleY(1.8f);
            statusDot.animate().scaleX(1f).scaleY(1f).setDuration(320)
                    .setInterpolator(new OvershootInterpolator(3f)).start();
        }

        tileFps.set(s.totalFps, getString(R.string.companion_sub_fps, s.receivedFps, s.renderedFps));
        tileNet.set(s.rttMs, getString(R.string.companion_sub_rtt, s.rttVarianceMs));
        tileHost.set(s.hostLatencyMs, getString(R.string.companion_sub_host));
        tileDecode.set(s.decodeMs, getString(R.string.companion_sub_decode));

        detailValues[0].setText(String.format(Locale.US, "%d×%d @ %d", s.width, s.height, s.targetFps));
        detailValues[1].setText(s.codec);
        detailValues[2].setText(s.bandwidthKBps < 0 ? "—" : s.bandwidthKBps >= 1000
                ? String.format(Locale.US, "%.1f Mbps", s.bandwidthKBps * 8 / 1024f)
                : String.format(Locale.US, "%.0f kbps", s.bandwidthKBps * 8));
        detailValues[3].setText(String.format(Locale.US, "%.1f%%", s.networkDropPercent));
        detailValues[4].setText(s.decoderName);
        detailValues[5].setText(sessionStart > 0
                ? formatDuration((SystemClock.elapsedRealtime() - sessionStart) / 1000, true) : "—");
        detailValues[6].setText(String.format(Locale.US, "%.1f", s.receivedFps));
        detailValues[7].setText(String.format(Locale.US, "%.1f", s.renderedFps));
    }

    private static String formatDuration(long seconds, boolean clock) {
        long h = seconds / 3600, m = (seconds / 60) % 60, sec = seconds % 60;
        if (clock) {
            return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
                    : String.format(Locale.US, "%d:%02d", m, sec);
        }
        return h > 0 ? String.format(Locale.US, "%d h %d min", h, m) : String.format(Locale.US, "%d min", Math.max(1, m));
    }

    // ------------------------------------------------------------------ waiting / lifecycle

    private void setupWaiting() {
        waiting = findViewById(R.id.companion_waiting);
        waitingBattery = findViewById(R.id.waiting_battery);
        waitingBatteryDetail = findViewById(R.id.waiting_battery_detail);
        waitingBatteryIcon = findViewById(R.id.waiting_battery_icon);
        findViewById(R.id.waiting_edit_keybinds).setOnClickListener(v -> openEditor(null));
        Intent sticky = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (sticky != null) updateBattery(sticky);
    }

    @Override
    public void onStreamStarted() {
        sessionStart = SystemClock.elapsedRealtime();
        scheduleDim();
        refreshHostName();
        if (overlaySwitch != null && Game.instance != null) overlaySwitch.setChecked(Game.instance.isHudVisible());
        if (waiting.getVisibility() == View.VISIBLE) {
            View body = findViewById(R.id.waiting_body);
            body.animate().scaleX(0.94f).scaleY(0.94f).setDuration(380).start();
            waiting.animate().alpha(0f).setDuration(380).withEndAction(() -> {
                waiting.setVisibility(View.GONE);
                waiting.setAlpha(1f);
                body.setScaleX(1f);
                body.setScaleY(1f);
            }).start();
            View main = findViewById(R.id.companion_main);
            main.setScaleX(1.03f);
            main.setScaleY(1.03f);
            main.animate().scaleX(1f).scaleY(1f).setDuration(420).setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    @Override
    public void onStreamStopped() {
        tileFps.reset();
        tileNet.reset();
        tileHost.reset();
        tileDecode.reset();
        finishWithFade();
    }

    private void finishWithFade() {
        View root = findViewById(R.id.companion_root);
        root.animate().alpha(0f).setDuration(220).withEndAction(this::finish).start();
    }

    // ------------------------------------------------------------------ idle dimming

    private void setupIdleDim() {
        idleDimEnabled = com.limelight.preferences.PreferenceConfiguration.readPreferences(this).companionIdleDim;
        FrameLayout root = findViewById(R.id.companion_root);
        FrameLayout scrim = new FrameLayout(this);
        scrim.setBackgroundColor(0xF2000000);
        scrim.setVisibility(View.GONE);
        scrim.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        android.widget.TextClock clock = new android.widget.TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextSize(44);
        clock.setTypeface(android.graphics.Typeface.MONOSPACE);
        clock.setTextColor(0xFF3A3A42);
        scrim.addView(clock, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        idleScrim = scrim;
        if (CompanionBridge.isStreaming()) scheduleDim();
    }

    private void scheduleDim() {
        idleHandler.removeCallbacks(dimRunnable);
        if (idleDimEnabled) idleHandler.postDelayed(dimRunnable, IDLE_DIM_MS);
    }

    private void dim() {
        if (!CompanionBridge.isStreaming() || dimmed) return;
        dimmed = true;
        idleScrim.setAlpha(0f);
        idleScrim.setVisibility(View.VISIBLE);
        idleScrim.animate().alpha(1f).setDuration(600).start();
    }

    /** Returns true when the touch only woke the screen and must not reach the controls. */
    private boolean wake() {
        scheduleDim();
        if (!dimmed) return false;
        dimmed = false;
        idleScrim.animate().alpha(0f).setDuration(220)
                .withEndAction(() -> idleScrim.setVisibility(View.GONE)).start();
        return true;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (wake()) {
                swallowGesture = true;
                return true;
            }
            swallowGesture = false;
        } else if (swallowGesture) {
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    private boolean swallowGesture;

    // ------------------------------------------------------------------ input forwarding

    /**
     * Only one display has input focus at a time. After the second screen is touched it holds
     * focus, so controller buttons and hardware keys would land here instead of the stream:
     * hand them to the game.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        Game g = Game.instance;
        if (g != null && !isTextInputFocused()) {
            boolean handled = false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) handled = g.handleKeyDown(event);
            else if (event.getAction() == KeyEvent.ACTION_UP) handled = g.handleKeyUp(event);
            if (handled) return true;
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        Game g = Game.instance;
        if (g != null && (ev.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) != 0) {
            if (g.onGenericMotionEvent(ev)) return true;
        }
        return super.dispatchGenericMotionEvent(ev);
    }

    private boolean isTextInputFocused() {
        View f = getCurrentFocus();
        return f instanceof EditText;
    }
}
