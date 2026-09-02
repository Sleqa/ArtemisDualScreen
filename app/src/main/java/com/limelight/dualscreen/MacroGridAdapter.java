package com.limelight.dualscreen;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.limelight.R;
import com.limelight.utils.KeyConfigHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Grid of compact circular macro keys shown on the second-screen companion panel, laid out like
 * the AYN Thor's own second-screen toggles: a filled circle carrying the macro's icon (or its
 * initials, when no icon was picked) with the macro's name underneath. Unnamed macros show the
 * circle alone.
 */
public class MacroGridAdapter extends RecyclerView.Adapter<MacroGridAdapter.MacroButtonViewHolder> {

    public interface OnMacroTappedListener {
        void onMacroTapped(KeyConfigHelper.Shortcut macro);
    }

    private final Context context;
    private final OnMacroTappedListener listener;
    private final List<KeyConfigHelper.Shortcut> macros = new ArrayList<>();

    public MacroGridAdapter(Context context, OnMacroTappedListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setMacros(List<KeyConfigHelper.Shortcut> newMacros) {
        macros.clear();
        if (newMacros != null) {
            macros.addAll(newMacros);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MacroButtonViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.row_macro_tile, parent, false);
        return new MacroButtonViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MacroButtonViewHolder holder, int position) {
        KeyConfigHelper.Shortcut macro = macros.get(position);

        int iconRes = MacroIconCatalog.drawableFor(macro.icon);
        if (iconRes != 0) {
            holder.icon.setImageResource(iconRes);
            holder.icon.setVisibility(View.VISIBLE);
            holder.initials.setVisibility(View.GONE);
        } else {
            holder.icon.setVisibility(View.GONE);
            holder.initials.setText(initialsOf(macro.name));
            holder.initials.setVisibility(View.VISIBLE);
        }

        boolean named = macro.name != null && !macro.name.trim().isEmpty();
        holder.label.setText(named ? macro.name : "");
        holder.label.setVisibility(named ? View.VISIBLE : View.GONE);
        holder.circle.setContentDescription(named ? macro.name : null);

        View.OnClickListener clickListener = v -> {
            if (listener != null) {
                listener.onMacroTapped(macro);
            }
        };
        holder.circle.setOnClickListener(clickListener);
        // The label is part of the same key, so tapping it fires the macro too
        holder.itemView.setOnClickListener(clickListener);
    }

    /**
     * Condenses a macro name into the one or two characters shown inside its circle:
     * the initials of the first two words ("Task Manager" to "TM", "Alt+Tab" to "AT"),
     * or the first two characters of a single-word name ("Escape" to "Es").
     */
    static String initialsOf(String name) {
        if (name == null) {
            return "";
        }
        String[] words = name.split("[^\\p{L}\\p{N}]+");
        StringBuilder initials = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            initials.append(Character.toUpperCase(word.charAt(0)));
            if (initials.length() == 2) {
                return initials.toString();
            }
        }
        if (initials.length() == 1) {
            // Single word: pad the initial with its second character for a fuller-looking key
            String word = words.length > 0 && !words[0].isEmpty() ? words[0] : name.trim();
            if (word.length() > 1) {
                initials.append(Character.toLowerCase(word.charAt(1)));
            }
            return initials.toString();
        }
        String trimmed = name.trim();
        return trimmed.isEmpty() ? "" : trimmed.substring(0, 1).toUpperCase(Locale.getDefault());
    }

    @Override
    public int getItemCount() {
        return macros.size();
    }

    static class MacroButtonViewHolder extends RecyclerView.ViewHolder {
        final FrameLayout circle;
        final TextView initials;
        final ImageView icon;
        final TextView label;

        MacroButtonViewHolder(@NonNull View itemView) {
            super(itemView);
            circle = itemView.findViewById(R.id.macroCircle);
            initials = itemView.findViewById(R.id.macroInitials);
            icon = itemView.findViewById(R.id.macroIcon);
            label = itemView.findViewById(R.id.macroLabel);
        }
    }
}
