package com.limelight.dualscreen;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.limelight.R;
import com.limelight.utils.KeyConfigHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Grid of large, tappable macro buttons shown on the second-screen companion panel.
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
        View view = LayoutInflater.from(context).inflate(R.layout.row_macro_button, parent, false);
        return new MacroButtonViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MacroButtonViewHolder holder, int position) {
        KeyConfigHelper.Shortcut macro = macros.get(position);
        holder.button.setText(macro.name);
        holder.button.setOnClickListener(v -> {
            if (listener != null) {
                listener.onMacroTapped(macro);
            }
        });
    }

    @Override
    public int getItemCount() {
        return macros.size();
    }

    static class MacroButtonViewHolder extends RecyclerView.ViewHolder {
        MaterialButton button;

        MacroButtonViewHolder(@NonNull View itemView) {
            super(itemView);
            button = (MaterialButton) itemView;
        }
    }
}
