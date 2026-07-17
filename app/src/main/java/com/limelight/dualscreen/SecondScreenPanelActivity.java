package com.limelight.dualscreen;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
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
import com.limelight.ui.ExternalControllerView;
import com.limelight.utils.KeyConfigHelper;

/**
 * Companion panel shown on a built-in secondary screen (e.g. AYN Thor) while a stream is active
 * on the main screen. Offers a soft-keyboard toggle (forwarding typed text into the live stream
 * via {@link ExternalControllerView}'s IME bridge) and a grid of tappable macro buttons.
 */
public class SecondScreenPanelActivity extends AppCompatActivity {

    private static final int DESIRED_MACRO_BUTTON_WIDTH_DP = 140;

    private ExternalControllerView rootLayout;
    private RecyclerView macroRecyclerView;
    private MacroGridAdapter macroGridAdapter;
    private TextView emptyStateText;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int failCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

    @SuppressLint("ClickableViewAccessibility")
    private void createProgrammaticUI() {
        rootLayout = new ExternalControllerView(this);
        rootLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rootLayout.setInputCallbacks(Game.instance);
        rootLayout.setCommitTextEnabled(true);
        setContentView(rootLayout);

        // Top-right: manage macros
        LinearLayout topBar = createButtonContainer(Gravity.TOP | Gravity.END);
        topBar.addView(createImageButton(R.drawable.ic_settings, v ->
                startActivity(new Intent(this, MacroListActivity.class))));
        rootLayout.addView(topBar);

        // Bottom-left: soft keyboard toggle
        LinearLayout bottomBar = createButtonContainer(Gravity.BOTTOM | Gravity.START);
        bottomBar.addView(createImageButton(R.drawable.ic_android_keyboard, v -> toggleKeyboard()));
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
