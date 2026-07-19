package com.limelight.dualscreen;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.limelight.R;

public class MacroListActivity extends AppCompatActivity implements MacroAdapter.OnMacrosChangedListener {
    private MacroAdapter adapter;
    private RecyclerView recyclerView;
    private View emptyState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_macro_list);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle(R.string.title_manage_macros);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        recyclerView = findViewById(R.id.macrosRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new MacroAdapter(this, this);
        recyclerView.setAdapter(adapter);

        emptyState = findViewById(R.id.emptyState);

        FloatingActionButton fab = findViewById(R.id.addMacroFab);
        fab.setOnClickListener(v -> startActivity(new Intent(this, EditMacroActivity.class)));

        updateEmptyState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        adapter.reload();
        updateEmptyState();
    }

    @Override
    public void onMacrosChanged() {
        updateEmptyState();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void updateEmptyState() {
        boolean empty = adapter.getItemCount() == 0;
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
    }
}
