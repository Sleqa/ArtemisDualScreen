package com.limelight.dualscreen;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.limelight.Game;
import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.ExternalControllerView;
import com.limelight.utils.KeyConfigHelper;

/**
 * Companion panel shown on a built-in secondary screen (e.g. AYN Thor) while a stream is active
 * on the main screen. Offers a soft-keyboard toggle (forwarding typed text into the live stream
 * via {@link ExternalControllerView}'s IME bridge) and a grid of tappable macro buttons.
 */
public class SecondScreenPanelActivity extends AppCompatActivity {

    private static final int DESIRED_MACRO_BUTTON_WIDTH_DP = 140;

    private PreferenceConfiguration prefConfig;
    private ExternalControllerView rootLayout;
    private RecyclerView macroRecyclerView;
    private MacroGridAdapter macroGridAdapter;
    private TextView emptyStateText;
    private TextView trackpadHint;
    private ImageButton trackpadButton;
    private ImageButton mouseModeButton;
    private boolean trackpadEnabled = false;
    private boolean mouseModeOverridden = false;
    private int previousMouseMode = 0;

    // Index of "Trackpad (natural)" in the mouse_mode_names array (see Game.applyMouseMode)
    private static final int MOUSE_MODE_TRACKPAD_NATURAL = 2;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int failCount = 0;

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
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (macroGridAdapter != null && Game.instance == null) {
            finish();
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
        rootLayout = new ExternalControllerView(this);
        rootLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rootLayout.setFocusable(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            rootLayout.setFocusedByDefault(true);
        }
        rootLayout.setInputCallbacks(Game.instance);
        // With this pref off (the default), no InputConnection is exposed and the IME
        // falls back to raw key events, which reach Game via the onKey* overrides below -
        // the same dual path ExternalDisplayControlActivity relies on.
        rootLayout.setCommitTextEnabled(prefConfig.enableCommitText);
        setContentView(rootLayout);

        // Top-right: manage macros
        LinearLayout topBar = createButtonContainer(Gravity.TOP | Gravity.END);
        topBar.setFocusable(false);
        topBar.addView(createImageButton(R.drawable.ic_settings, v ->
                startActivity(new Intent(this, MacroListActivity.class))));
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

        // Macro grid
        macroRecyclerView = new RecyclerView(this);
        FrameLayout.LayoutParams gridParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        gridParams.topMargin = dpToPx(72);
        gridParams.bottomMargin = dpToPx(72);
        gridParams.leftMargin = dpToPx(8);
        gridParams.rightMargin = dpToPx(8);
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
        inputManager.toggleSoftInput(0, 0);
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
