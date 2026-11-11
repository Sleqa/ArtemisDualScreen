package com.limelight.companion;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;
import com.limelight.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Grid of keybind tiles plus a trailing "New keybind" tile. */
public class KeybindAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface EditorOpener {
        void open(String keybindId);
    }

    public interface DragStarter {
        void startDrag(RecyclerView.ViewHolder holder);
    }

    private static final int TYPE_BIND = 0;
    private static final int TYPE_ADD = 1;

    private final Context context;
    private final KeybindStore store;
    private final EditorOpener opener;
    private DragStarter dragStarter;
    private List<Keybind> binds = new ArrayList<>();
    private boolean editing;
    private boolean animateIn = true;

    public KeybindAdapter(Context context, KeybindStore store, EditorOpener opener) {
        this.context = context;
        this.store = store;
        this.opener = opener;
        reload();
    }

    public void setDragStarter(DragStarter s) {
        dragStarter = s;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void reload() {
        binds = store.load();
        notifyDataSetChanged();
    }

    public boolean isEditing() {
        return editing;
    }

    public void setEditing(boolean e) {
        editing = e;
        notifyItemRangeChanged(0, binds.size(), "edit");
    }

    @Override
    public int getItemCount() {
        return binds.size() + 1;
    }

    @Override
    public int getItemViewType(int position) {
        return position < binds.size() ? TYPE_BIND : TYPE_ADD;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_ADD) {
            View v = inf.inflate(R.layout.companion_keybind_add, parent, false);
            v.setOnClickListener(x -> opener.open(null));
            return new RecyclerView.ViewHolder(v) {
            };
        }
        return new BindHolder(inf.inflate(R.layout.companion_keybind_tile, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && holder instanceof BindHolder) {
            ((BindHolder) holder).applyEditing(true);
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof BindHolder) {
            ((BindHolder) holder).bind(binds.get(position));
        }
        if (animateIn) {
            View v = holder.itemView;
            v.setAlpha(0f);
            v.setTranslationY(10 * context.getResources().getDisplayMetrics().density);
            v.animate().alpha(1f).translationY(0).setStartDelay(position * 30L).setDuration(360)
                    .setInterpolator(new DecelerateInterpolator()).start();
            if (position >= getItemCount() - 1) animateIn = false;
        }
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof BindHolder) ((BindHolder) holder).stopWiggle();
    }

    private void remove(int position) {
        if (position < 0 || position >= binds.size()) return;
        binds.remove(position);
        store.save(binds);
        notifyItemRemoved(position);
    }

    public ItemTouchHelper.Callback dragCallback() {
        return new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.START | ItemTouchHelper.END, 0) {
            @Override
            public boolean isLongPressDragEnabled() {
                return editing;
            }

            @Override
            public int getMovementFlags(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                if (!(vh instanceof BindHolder) || !editing) return 0;
                return super.getMovementFlags(rv, vh);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder from,
                                  @NonNull RecyclerView.ViewHolder to) {
                int a = from.getBindingAdapterPosition(), b = to.getBindingAdapterPosition();
                if (a < 0 || b < 0 || a >= binds.size() || b >= binds.size()) return false;
                Collections.swap(binds, a, b);
                notifyItemMoved(a, b);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder vh, int actionState) {
                super.onSelectedChanged(vh, actionState);
                if (vh != null && actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    vh.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    vh.itemView.animate().scaleX(1.06f).scaleY(1.06f).setDuration(150).start();
                    vh.itemView.setActivated(true);
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                super.clearView(rv, vh);
                vh.itemView.setActivated(false);
                vh.itemView.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
                store.save(binds);
            }
        };
    }

    final class BindHolder extends RecyclerView.ViewHolder {
        final TextView name, custom;
        final ChipGroup keys;
        final View overlay, drag, delete;
        ObjectAnimator wiggle;
        Keybind bind;
        boolean holding;

        @SuppressLint("ClickableViewAccessibility")
        BindHolder(View v) {
            super(v);
            name = v.findViewById(R.id.bind_name);
            custom = v.findViewById(R.id.bind_custom);
            keys = v.findViewById(R.id.bind_keys);
            overlay = v.findViewById(R.id.bind_edit_overlay);
            drag = v.findViewById(R.id.bind_drag);
            delete = v.findViewById(R.id.bind_delete);

            v.setOnTouchListener((view, e) -> onTileTouch(e));
            delete.setOnClickListener(x -> {
                int pos = getBindingAdapterPosition();
                itemView.animate().scaleX(0.6f).scaleY(0.6f).alpha(0f).setDuration(180)
                        .withEndAction(() -> {
                            itemView.setScaleX(1f);
                            itemView.setScaleY(1f);
                            itemView.setAlpha(1f);
                            remove(pos);
                        }).start();
            });
            drag.setOnTouchListener((view, e) -> {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN && dragStarter != null) {
                    dragStarter.startDrag(this);
                }
                return false;
            });
        }

        void bind(Keybind b) {
            bind = b;
            name.setText(b.name);
            custom.setVisibility(b.custom ? View.VISIBLE : View.GONE);
            keys.removeAllViews();
            float d = context.getResources().getDisplayMetrics().density;
            for (String label : b.keyLabels()) {
                TextView cap = new TextView(context);
                cap.setText(label);
                cap.setTextSize(11);
                cap.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
                cap.setTextColor(context.getResources().getColor(R.color.companion_text_soft));
                cap.setBackgroundResource(R.drawable.companion_bg_keycap);
                cap.setPadding((int) (6 * d), (int) (3 * d), (int) (6 * d), (int) (3 * d));
                keys.addView(cap);
            }
            itemView.setContentDescription(b.name + ", " + String.join(" + ", b.keyLabels()));
            delete.setContentDescription(context.getString(R.string.companion_remove, b.name));
            applyEditing(false);
        }

        void applyEditing(boolean animate) {
            if (editing) {
                overlay.setVisibility(View.VISIBLE);
                if (animate) overlay.animate().alpha(1f).setDuration(200).start();
                else overlay.setAlpha(1f);
                startWiggle();
            } else {
                stopWiggle();
                if (animate) {
                    overlay.animate().alpha(0f).setDuration(160)
                            .withEndAction(() -> overlay.setVisibility(View.GONE)).start();
                } else {
                    overlay.setAlpha(0f);
                    overlay.setVisibility(View.GONE);
                }
            }
        }

        void startWiggle() {
            if (wiggle != null) return;
            wiggle = ObjectAnimator.ofFloat(itemView, View.ROTATION, -0.8f, 0.8f);
            wiggle.setDuration(240 + (getBindingAdapterPosition() % 3) * 30L);
            wiggle.setRepeatMode(ValueAnimator.REVERSE);
            wiggle.setRepeatCount(ValueAnimator.INFINITE);
            wiggle.setStartDelay((getBindingAdapterPosition() % 4) * 40L);
            wiggle.start();
        }

        void stopWiggle() {
            if (wiggle != null) {
                wiggle.cancel();
                wiggle = null;
            }
            itemView.animate().rotation(0f).setDuration(120).start();
        }

        private boolean onTileTouch(MotionEvent e) {
            if (bind == null) return false;
            if (editing) {
                if (e.getActionMasked() == MotionEvent.ACTION_UP && e.getEventTime() - e.getDownTime() < 300) {
                    opener.open(bind.id);
                }
                return e.getActionMasked() == MotionEvent.ACTION_DOWN || e.getActionMasked() == MotionEvent.ACTION_UP;
            }
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    itemView.setPressed(true);
                    itemView.animate().scaleX(0.95f).scaleY(0.95f).setDuration(90).start();
                    itemView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    if (bind.hold) {
                        holding = true;
                        InputSender.pressAll(bind.keys);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    itemView.setPressed(false);
                    itemView.animate().scaleX(1f).scaleY(1f).setDuration(260)
                            .setInterpolator(new OvershootInterpolator(2.2f)).start();
                    boolean inside = e.getX() >= 0 && e.getY() >= 0 && e.getX() <= itemView.getWidth() && e.getY() <= itemView.getHeight();
                    if (holding) {
                        holding = false;
                        InputSender.releaseAll(bind.keys);
                    } else if (e.getActionMasked() == MotionEvent.ACTION_UP && inside) {
                        InputSender.tapCombo(bind.keys);
                    }
                    return true;
                }
            }
            return true;
        }
    }
}
