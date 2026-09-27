package io.greycode.streamer.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import io.greycode.streamer.R;
import io.greycode.streamer.overlay.OverlayItem;
import io.greycode.streamer.overlay.OverlayType;

public class OverlayAdapter extends RecyclerView.Adapter<OverlayAdapter.OverlayViewHolder> {

    public interface OnOverlayActionListener {
        void onEdit(OverlayItem item);
        void onToggleVisibility(OverlayItem item);
        void onDelete(OverlayItem item);
        void onMoveUp(OverlayItem item, int position);
        void onMoveDown(OverlayItem item, int position);
    }

    private final List<OverlayItem> overlays;
    private final OnOverlayActionListener listener;

    public OverlayAdapter(List<OverlayItem> overlays, OnOverlayActionListener listener) {
        this.overlays = overlays;
        this.listener = listener;
    }

    @NonNull
    @Override
    public OverlayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_overlay, parent, false);
        return new OverlayViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OverlayViewHolder holder, int position) {
        OverlayItem item = overlays.get(position);
        holder.bind(item, position, getItemCount(), listener);
    }

    @Override
    public int getItemCount() {
        return overlays.size();
    }

    static class OverlayViewHolder extends RecyclerView.ViewHolder {

        private final ImageView ivDragHandle;
        private final ImageView ivTypeIcon;
        private final TextView tvOverlayTitle;
        private final TextView tvOverlaySubtitle;
        private final ImageButton btnMoveUp;
        private final ImageButton btnMoveDown;
        private final ImageButton btnEdit;
        private final ImageButton btnToggleVisibility;
        private final ImageButton btnDelete;

        public OverlayViewHolder(@NonNull View itemView) {
            super(itemView);
            ivDragHandle = itemView.findViewById(R.id.ivDragHandle);
            ivTypeIcon = itemView.findViewById(R.id.ivTypeIcon);
            tvOverlayTitle = itemView.findViewById(R.id.tvOverlayTitle);
            tvOverlaySubtitle = itemView.findViewById(R.id.tvOverlaySubtitle);
            btnMoveUp = itemView.findViewById(R.id.btnMoveUp);
            btnMoveDown = itemView.findViewById(R.id.btnMoveDown);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnToggleVisibility = itemView.findViewById(R.id.btnToggleVisibility);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }

        public void bind(OverlayItem item, int position, int totalCount, OnOverlayActionListener listener) {
            tvOverlayTitle.setText(item.getName());
            tvOverlaySubtitle.setText(getTypeName(item.getType()) + " • " + (item.getText().isEmpty() ? "Graphic Layer" : item.getText()));

            btnToggleVisibility.setImageResource(item.isVisible() ? R.drawable.ic_visible : R.drawable.ic_hidden);

            // Enable / disable Move buttons depending on stack position
            btnMoveUp.setEnabled(position > 0);
            btnMoveUp.setAlpha(position > 0 ? 1.0f : 0.3f);

            btnMoveDown.setEnabled(position < totalCount - 1);
            btnMoveDown.setAlpha(position < totalCount - 1 ? 1.0f : 0.3f);

            btnMoveUp.setOnClickListener(v -> {
                if (listener != null) listener.onMoveUp(item, position);
            });

            btnMoveDown.setOnClickListener(v -> {
                if (listener != null) listener.onMoveDown(item, position);
            });

            btnEdit.setOnClickListener(v -> {
                if (listener != null) listener.onEdit(item);
            });

            btnToggleVisibility.setOnClickListener(v -> {
                if (listener != null) listener.onToggleVisibility(item);
            });

            btnDelete.setOnClickListener(v -> {
                if (listener != null) listener.onDelete(item);
            });
        }

        private String getTypeName(OverlayType type) {
            switch (type) {
                case IMAGE_LOGO: return "Logo / Image";
                case SCROLLING_TEXT: return "Scrolling Marquee";
                case TEXT: return "Text Overlay";
                case HTML_OVERLAY: return "HTML Overlay";
                default: return "Overlay Item";
            }
        }
    }
}
