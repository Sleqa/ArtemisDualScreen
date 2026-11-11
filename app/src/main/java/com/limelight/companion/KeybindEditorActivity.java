package com.limelight.companion;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.limelight.R;

import java.util.ArrayList;
import java.util.List;

/** Create or edit a companion keybind: name, tap-or-hold, and a modifier + key combo. */
public class KeybindEditorActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "keybind_id";

    private KeybindStore store;
    private Keybind editing;
    private EditText nameField;
    private TextView modeTap, modeHold, comboEmpty;
    private LinearLayout comboRow, modsRow, categoryRow;
    private final List<TextView> modButtons = new ArrayList<>();
    private final List<TextView> categoryButtons = new ArrayList<>();
    private final List<Integer> selectedMods = new ArrayList<>();
    private Integer selectedKey;
    private boolean hold;
    private String category = "Letters";
    private KeyGridAdapter keyAdapter;
    private float density;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.companion_keybind_editor);
        density = getResources().getDisplayMetrics().density;
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.hide(WindowInsetsCompat.Type.systemBars());
        c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        store = new KeybindStore(this);
        nameField = findViewById(R.id.editor_name);
        modeTap = findViewById(R.id.editor_mode_tap);
        modeHold = findViewById(R.id.editor_mode_hold);
        comboRow = findViewById(R.id.editor_combo);
        comboEmpty = findViewById(R.id.editor_combo_empty);
        modsRow = findViewById(R.id.editor_mods);
        categoryRow = findViewById(R.id.editor_categories);

        String id = getIntent().getStringExtra(EXTRA_ID);
        editing = id != null ? store.find(id) : null;
        if (editing != null) {
            ((TextView) findViewById(R.id.editor_title)).setText(R.string.companion_edit_keybind);
            nameField.setText(editing.name);
            hold = editing.hold;
            for (int k : editing.keys) {
                if (KeyCatalog.isModifier(k)) selectedMods.add(k);
                else selectedKey = k;
            }
            if (selectedKey != null) category = categoryOf(selectedKey);
            View delete = findViewById(R.id.editor_delete);
            delete.setVisibility(View.VISIBLE);
            delete.setOnClickListener(v -> {
                store.delete(editing.id);
                finish();
            });
        }

        modeTap.setOnClickListener(v -> setHold(false));
        modeHold.setOnClickListener(v -> setHold(true));
        setHold(hold);

        buildModifiers();
        buildCategories();

        RecyclerView grid = findViewById(R.id.editor_keys);
        grid.setLayoutManager(new GridLayoutManager(this, 8));
        keyAdapter = new KeyGridAdapter();
        grid.setAdapter(keyAdapter);

        findViewById(R.id.editor_cancel).setOnClickListener(v -> finish());
        findViewById(R.id.editor_save).setOnClickListener(v -> save());
        renderCombo(false);
    }

    private static String categoryOf(int vk) {
        for (String c : KeyCatalog.categories()) {
            for (KeyCatalog.Key k : KeyCatalog.keysIn(c)) if (k.vk == vk) return c;
        }
        return "Letters";
    }

    private void setHold(boolean h) {
        hold = h;
        modeTap.setSelected(!h);
        modeHold.setSelected(h);
        modeTap.setTypeface(null, h ? Typeface.NORMAL : Typeface.BOLD);
        modeHold.setTypeface(null, h ? Typeface.BOLD : Typeface.NORMAL);
    }

    private TextView makeToggle(String label, int heightDp) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(13);
        t.setTextColor(getResources().getColorStateList(R.color.companion_toggle_text));
        t.setBackgroundResource(R.drawable.companion_bg_toggle);
        t.setMinHeight((int) (heightDp * density));
        return t;
    }

    private void buildModifiers() {
        for (int i = 0; i < KeyCatalog.MODIFIERS.length; i++) {
            final int vk = KeyCatalog.MODIFIERS[i];
            TextView t = makeToggle(KeyCatalog.label(vk), 44);
            t.setSelected(selectedMods.contains(vk));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            if (i > 0) lp.setMarginStart((int) (8 * density));
            t.setOnClickListener(v -> {
                if (selectedMods.contains(vk)) selectedMods.remove(Integer.valueOf(vk));
                else selectedMods.add(vk);
                t.setSelected(selectedMods.contains(vk));
                bounce(t);
                renderCombo(true);
            });
            modButtons.add(t);
            modsRow.addView(t, lp);
        }
    }

    private void buildCategories() {
        for (String c : KeyCatalog.categories()) {
            TextView t = new TextView(this);
            t.setText(c);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(12);
            t.setPadding((int) (12 * density), 0, (int) (12 * density), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.setMarginEnd((int) (6 * density));
            t.setOnClickListener(v -> {
                category = c;
                styleCategories();
                keyAdapter.refresh();
            });
            categoryButtons.add(t);
            categoryRow.addView(t, lp);
        }
        styleCategories();
    }

    private void styleCategories() {
        for (TextView t : categoryButtons) {
            boolean on = t.getText().toString().equals(category);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(999);
            if (on) {
                bg.setColor(getResources().getColor(R.color.companion_text));
            } else {
                bg.setColor(0);
                bg.setStroke((int) density, getResources().getColor(R.color.companion_stroke_strong));
            }
            t.setBackground(bg);
            t.setTextColor(getResources().getColor(on ? R.color.companion_bg : R.color.companion_text_secondary));
            t.setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private void bounce(View v) {
        v.setScaleX(0.92f);
        v.setScaleY(0.92f);
        v.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new OvershootInterpolator(2.5f)).start();
        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private int[] combo() {
        List<Integer> out = new ArrayList<>();
        for (int m : KeyCatalog.MODIFIERS) if (selectedMods.contains(m)) out.add(m);
        if (selectedKey != null) out.add(selectedKey);
        int[] arr = new int[out.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = out.get(i);
        return arr;
    }

    private void renderCombo(boolean animate) {
        comboRow.removeAllViews();
        int[] keys = combo();
        comboEmpty.setVisibility(keys.length == 0 ? View.VISIBLE : View.GONE);
        for (int i = 0; i < keys.length; i++) {
            TextView cap = new TextView(this);
            cap.setText(KeyCatalog.label(keys[i]));
            cap.setTextSize(15);
            cap.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            cap.setTextColor(getResources().getColor(R.color.companion_text));
            cap.setBackgroundResource(R.drawable.companion_bg_keycap);
            cap.setPadding((int) (12 * density), (int) (6 * density), (int) (12 * density), (int) (6 * density));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.setMarginStart((int) (8 * density));
            comboRow.addView(cap, lp);
            if (animate) {
                cap.setScaleX(0.6f);
                cap.setScaleY(0.6f);
                cap.setAlpha(0f);
                cap.animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(i * 25L).setDuration(260)
                        .setInterpolator(new OvershootInterpolator(2f)).start();
            }
        }
    }

    private void save() {
        int[] keys = combo();
        if (keys.length == 0) {
            Toast.makeText(this, R.string.companion_need_key, Toast.LENGTH_SHORT).show();
            comboRow.getRootView().findViewById(R.id.editor_combo_empty).animate()
                    .translationX(8 * density).setDuration(60)
                    .withEndAction(() -> comboEmpty.animate().translationX(0).setDuration(160)
                            .setInterpolator(new OvershootInterpolator(4f)).start()).start();
            return;
        }
        String name = nameField.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            List<String> labels = new ArrayList<>();
            for (int k : keys) labels.add(KeyCatalog.label(k));
            name = TextUtils.join(" + ", labels);
        }
        Keybind b = editing != null ? editing : new Keybind(name, true, hold, keys);
        b.name = name;
        b.keys = keys;
        b.hold = hold;
        if (editing == null) b.custom = true;
        store.upsert(b);
        finish();
    }

    private final class KeyGridAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private List<KeyCatalog.Key> keys = KeyCatalog.keysIn(category);

        @android.annotation.SuppressLint("NotifyDataSetChanged")
        void refresh() {
            keys = KeyCatalog.keysIn(category);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView t = makeToggle("", 44);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (44 * density));
            int m = (int) (3 * density);
            lp.setMargins(m, m, m, m);
            t.setLayoutParams(lp);
            return new RecyclerView.ViewHolder(t) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            KeyCatalog.Key k = keys.get(position);
            TextView t = (TextView) holder.itemView;
            t.setText(k.label);
            t.setSelected(selectedKey != null && selectedKey == k.vk);
            t.setOnClickListener(v -> {
                Integer before = selectedKey;
                selectedKey = (selectedKey != null && selectedKey == k.vk) ? null : k.vk;
                bounce(t);
                if (before != null) {
                    for (int i = 0; i < keys.size(); i++) if (keys.get(i).vk == before) notifyItemChanged(i);
                }
                t.setSelected(selectedKey != null && selectedKey == k.vk);
                renderCombo(true);
            });
        }

        @Override
        public int getItemCount() {
            return keys.size();
        }
    }
}
