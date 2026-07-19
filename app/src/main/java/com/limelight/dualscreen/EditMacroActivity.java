package com.limelight.dualscreen;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

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

    private String macroId;
    private TextInputEditText nameInput;
    private ChipGroup selectedKeysChipGroup;
    private TextView noKeysSelectedText;
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

        macroId = getIntent().getStringExtra(EXTRA_MACRO_ID);
        if (macroId != null) {
            KeyConfigHelper.Shortcut existing = findMacro(macroId);
            if (existing != null) {
                nameInput.setText(existing.name);
                if (existing.keys != null) {
                    selectedKeys.addAll(existing.keys);
                }
            }
            setTitle(R.string.macro_edit_macro);
        } else {
            setTitle(R.string.macro_new_macro);
        }

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
        String name = nameInput.getText() != null ? nameInput.getText().toString().trim() : "";
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.macro_name_cannot_be_blank, Toast.LENGTH_SHORT).show();
            return;
        }
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
