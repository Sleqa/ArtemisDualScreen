package com.limelight.dualscreen;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.limelight.R;
import com.limelight.utils.KeyConfigHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EditMacroActivity extends AppCompatActivity {
    public static final String EXTRA_MACRO_ID = "macroId";

    private static final int ICON_PICKER_COLUMNS = 5;

    private String macroId;
    private TextInputEditText nameInput;
    private ChipGroup selectedKeysChipGroup;
    private TextView noKeysSelectedText;
    private ImageView iconPreview;
    private TextView iconEmptyGlyph;
    private String selectedIcon;
    private final List<String> selectedKeys = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_macro);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        nameInput = findViewById(R.id.macroNameInput);
        selectedKeysChipGroup = findViewById(R.id.selectedKeysChipGroup);
        noKeysSelectedText = findViewById(R.id.noKeysSelectedText);
        iconPreview = findViewById(R.id.macroIconPreview);
        iconEmptyGlyph = findViewById(R.id.macroIconEmpty);
        findViewById(R.id.macroIconPicker).setOnClickListener(v -> showIconPicker());

        macroId = getIntent().getStringExtra(EXTRA_MACRO_ID);
        if (macroId != null) {
            KeyConfigHelper.Shortcut existing = findMacro(macroId);
            if (existing != null) {
                nameInput.setText(existing.name);
                selectedIcon = existing.icon;
                if (existing.keys != null) {
                    selectedKeys.addAll(existing.keys);
                }
            }
            setTitle(R.string.macro_edit_macro);
        } else {
            setTitle(R.string.macro_new_macro);
        }

        renderSelectedIcon();
        buildKeyPicker();
        renderSelectedKeys();
    }

    private KeyConfigHelper.Shortcut findMacro(String id) {
        KeyConfigHelper.ShortcutFile file = KeyConfigHelper.loadShortcutFile(this);
        if (file.data != null) {
            for (KeyConfigHelper.Shortcut sc : file.data) {
                if (TextUtils.equals(sc.id, id)) {
                    return sc;
                }
            }
        }
        return null;
    }

    private void renderSelectedIcon() {
        int iconRes = MacroIconCatalog.drawableFor(selectedIcon);
        if (iconRes != 0) {
            iconPreview.setImageResource(iconRes);
            iconPreview.setVisibility(View.VISIBLE);
            iconEmptyGlyph.setVisibility(View.GONE);
        } else {
            iconPreview.setVisibility(View.GONE);
            iconEmptyGlyph.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Grid of every bundled icon, with a "no icon" cell first. Picking one updates the preview
     * immediately; the choice is only written to the macro file when the macro is saved.
     */
    private void showIconPicker() {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(ICON_PICKER_COLUMNS);
        int gridPadding = dpToPx(12);
        grid.setPadding(gridPadding, gridPadding, gridPadding, gridPadding);

        final AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_Win_Dialog)
                .setTitle(R.string.macro_icon_pick_title)
                .setView(wrapInScrollView(grid))
                .setNegativeButton(R.string.cancel, null)
                .create();

        grid.addView(buildIconCell(null, dialog));
        for (String id : MacroIconCatalog.ids()) {
            grid.addView(buildIconCell(id, dialog));
        }

        dialog.show();
    }

    private ScrollView wrapInScrollView(View content) {
        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scrollView;
    }

    private View buildIconCell(final String iconId, final AlertDialog dialog) {
        int cell = dpToPx(52);
        int iconRes = MacroIconCatalog.drawableFor(iconId);

        View view;
        if (iconRes != 0) {
            ImageView image = new ImageView(this);
            image.setImageResource(iconRes);
            image.setColorFilter(ContextCompat.getColor(this,
                    TextUtils.equals(iconId, selectedIcon) ? R.color.win_text_on_accent
                                                           : R.color.win_text_primary));
            image.setPadding(dpToPx(13), dpToPx(13), dpToPx(13), dpToPx(13));
            view = image;
        } else {
            // "No icon" cell - the macro falls back to its initials
            TextView none = new TextView(this);
            none.setText(R.string.macro_icon_none_glyph);
            none.setTextColor(ContextCompat.getColor(this, R.color.win_text_tertiary));
            none.setTextSize(20);
            none.setGravity(Gravity.CENTER);
            view = none;
        }

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = cell;
        params.height = cell;
        params.setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2));
        view.setLayoutParams(params);
        view.setBackgroundResource(TextUtils.equals(iconId, selectedIcon)
                ? R.drawable.bg_win_accent_control : R.drawable.bg_win_control);
        view.setOnClickListener(v -> {
            selectedIcon = iconId;
            renderSelectedIcon();
            dialog.dismiss();
        });
        return view;
    }

    private void buildKeyPicker() {
        LinearLayout container = findViewById(R.id.keyPickerContainer);

        for (MacroKeyCatalog.Group group : MacroKeyCatalog.getGroups()) {
            TextView header = new TextView(this);
            header.setText(group.title);
            header.setTextColor(0xFFCCCCCC);
            header.setTextSize(12);
            LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            headerParams.topMargin = dpToPx(16);
            header.setLayoutParams(headerParams);
            container.addView(header);

            ChipGroup chipGroup = new ChipGroup(this);
            LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            groupParams.topMargin = dpToPx(4);
            chipGroup.setLayoutParams(groupParams);

            for (MacroKeyCatalog.KeyOption option : group.keys) {
                Chip chip = new Chip(this);
                chip.setText(option.label);
                chip.setClickable(true);
                chip.setCheckable(false);
                chip.setOnClickListener(v -> addKey(option.vkName));
                chipGroup.addView(chip);
            }

            container.addView(chipGroup);
        }
    }

    private void addKey(String vkName) {
        selectedKeys.add(vkName);
        renderSelectedKeys();
    }

    private void removeKeyAt(int index) {
        if (index >= 0 && index < selectedKeys.size()) {
            selectedKeys.remove(index);
            renderSelectedKeys();
        }
    }

    private void renderSelectedKeys() {
        selectedKeysChipGroup.removeAllViews();
        if (selectedKeys.isEmpty()) {
            noKeysSelectedText.setVisibility(View.VISIBLE);
            selectedKeysChipGroup.setVisibility(View.GONE);
            return;
        }
        noKeysSelectedText.setVisibility(View.GONE);
        selectedKeysChipGroup.setVisibility(View.VISIBLE);

        for (int i = 0; i < selectedKeys.size(); i++) {
            final int index = i;
            Chip chip = new Chip(this);
            chip.setText(MacroKeyCatalog.displayLabel(selectedKeys.get(i)));
            chip.setCloseIconVisible(true);
            chip.setCloseIconContentDescription(getString(R.string.macro_remove_key_content_description));
            chip.setOnCloseIconClickListener(v -> removeKeyAt(index));
            selectedKeysChipGroup.addView(chip);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.edit_macro_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_save) {
            saveMacro();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void saveMacro() {
        // The name is optional: a macro with an icon and no name shows just its key on the panel
        String name = nameInput.getText() != null ? nameInput.getText().toString().trim() : "";
        if (selectedKeys.isEmpty()) {
            Toast.makeText(this, R.string.macro_needs_at_least_one_key, Toast.LENGTH_SHORT).show();
            return;
        }

        KeyConfigHelper.ShortcutFile file = KeyConfigHelper.loadShortcutFile(this);
        if (file.data == null) {
            file.data = new ArrayList<>();
        }

        String id = macroId != null ? macroId : UUID.randomUUID().toString();
        KeyConfigHelper.Shortcut shortcut = new KeyConfigHelper.Shortcut(id, name, false, new ArrayList<>(selectedKeys));
        shortcut.icon = selectedIcon;

        boolean replaced = false;
        for (int i = 0; i < file.data.size(); i++) {
            if (TextUtils.equals(file.data.get(i).id, id)) {
                file.data.set(i, shortcut);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            file.data.add(shortcut);
        }

        KeyConfigHelper.saveShortcutFile(this, file);
        Toast.makeText(this, R.string.macro_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
