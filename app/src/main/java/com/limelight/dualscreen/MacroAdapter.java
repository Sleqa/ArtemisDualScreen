package com.limelight.dualscreen;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.limelight.R;
import com.limelight.utils.KeyConfigHelper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MacroAdapter extends RecyclerView.Adapter<MacroAdapter.MacroViewHolder> {

    public interface OnMacrosChangedListener {
        void onMacrosChanged();
    }

    private final Context context;
    private final OnMacrosChangedListener listener;
    private final List<KeyConfigHelper.Shortcut> macros = new ArrayList<>();

    public MacroAdapter(Context context, OnMacrosChangedListener listener) {
        this.context = context;
        this.listener = listener;
        reload();
    }

    public void reload() {
        macros.clear();
        KeyConfigHelper.ShortcutFile file = KeyConfigHelper.loadShortcutFile(context);
        if (file != null && file.data != null) {
            macros.addAll(file.data);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MacroViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.row_macro, parent, false);
        return new MacroViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MacroViewHolder holder, int position) {
        KeyConfigHelper.Shortcut macro = macros.get(position);

        boolean named = macro.name != null && !macro.name.trim().isEmpty();
        holder.macroName.setText(named ? macro.name : context.getString(R.string.macro_unnamed));
        holder.macroKeysSummary.setText(formatKeysSummary(macro.keys));

        int iconRes = MacroIconCatalog.drawableFor(macro.icon);
        holder.macroIcon.setVisibility(iconRes != 0 ? View.VISIBLE : View.GONE);
        if (iconRes != 0) {
            holder.macroIcon.setImageResource(iconRes);
        }

        holder.editMacro.setOnClickListener(v -> {
            Intent intent = new Intent(context, EditMacroActivity.class);
            intent.putExtra(EditMacroActivity.EXTRA_MACRO_ID, macro.id);
            context.startActivity(intent);
        });

        holder.deleteMacro.setOnClickListener(v -> new AlertDialog.Builder(context)
                .setTitle(R.string.macro_delete_macro)
                .setMessage(context.getString(R.string.macro_confirm_delete,
                        named ? macro.name : context.getString(R.string.macro_unnamed)))
                .setPositiveButton(R.string.macro_delete, (dialog, which) -> {
                    deleteMacro(macro.id);
                    Toast.makeText(context, R.string.macro_deleted, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(context.getString(R.string.cancel), null)
                .show());

        holder.itemView.setOnClickListener(v -> holder.editMacro.performClick());
    }

    private void deleteMacro(String macroId) {
        KeyConfigHelper.ShortcutFile file = KeyConfigHelper.loadShortcutFile(context);
        if (file.data != null) {
            Iterator<KeyConfigHelper.Shortcut> it = file.data.iterator();
            while (it.hasNext()) {
                if (TextUtils.equals(it.next().id, macroId)) {
                    it.remove();
                    break;
                }
            }
        }
        KeyConfigHelper.saveShortcutFile(context, file);
        reload();
        if (listener != null) {
            listener.onMacrosChanged();
        }
    }

    private static String formatKeysSummary(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
                sb.append(" + ");
            }
            sb.append(MacroKeyCatalog.displayLabel(keys.get(i)));
        }
        return sb.toString();
    }

    @Override
    public int getItemCount() {
        return macros.size();
    }

    static class MacroViewHolder extends RecyclerView.ViewHolder {
        ImageView macroIcon;
        TextView macroName;
        TextView macroKeysSummary;
        ImageButton editMacro;
        ImageButton deleteMacro;

        MacroViewHolder(@NonNull View itemView) {
            super(itemView);
            macroIcon = itemView.findViewById(R.id.macroIcon);
            macroName = itemView.findViewById(R.id.macroName);
            macroKeysSummary = itemView.findViewById(R.id.macroKeysSummary);
            editMacro = itemView.findViewById(R.id.editMacro);
            deleteMacro = itemView.findViewById(R.id.deleteMacro);
        }
    }
}
