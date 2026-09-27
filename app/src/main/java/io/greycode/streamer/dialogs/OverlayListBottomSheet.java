package io.greycode.streamer.dialogs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Collections;
import java.util.List;

import io.greycode.streamer.adapters.OverlayAdapter;
import io.greycode.streamer.databinding.BottomSheetOverlayListBinding;
import io.greycode.streamer.overlay.OverlayCanvasView;
import io.greycode.streamer.overlay.OverlayItem;

public class OverlayListBottomSheet extends BottomSheetDialogFragment implements OverlayAdapter.OnOverlayActionListener {

    public interface OnAddNewOverlayClickListener {
        void onAddNewOverlayClick();
    }

    public interface OnEditOverlayClickListener {
        void onEditOverlayClick(OverlayItem item);
    }

    private BottomSheetOverlayListBinding binding;
    private OverlayCanvasView overlayCanvasView;
    private OverlayAdapter adapter;
    private OnAddNewOverlayClickListener addNewListener;
    private OnEditOverlayClickListener editListener;

    public void setOverlayCanvasView(OverlayCanvasView overlayCanvasView) {
        this.overlayCanvasView = overlayCanvasView;
    }

    public void setOnAddNewOverlayClickListener(OnAddNewOverlayClickListener listener) {
        this.addNewListener = listener;
    }

    public void setOnEditOverlayClickListener(OnEditOverlayClickListener listener) {
        this.editListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetOverlayListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.rvOverlays.setLayoutManager(new LinearLayoutManager(getContext()));
        refreshList();

        binding.btnAddOverlayFromList.setOnClickListener(v -> {
            dismiss();
            if (addNewListener != null) {
                addNewListener.onAddNewOverlayClick();
            }
        });
    }

    private void refreshList() {
        if (overlayCanvasView == null) return;
        List<OverlayItem> list = overlayCanvasView.getOverlays();

        if (list.isEmpty()) {
            binding.tvEmptyState.setVisibility(View.VISIBLE);
            binding.rvOverlays.setVisibility(View.GONE);
        } else {
            binding.tvEmptyState.setVisibility(View.GONE);
            binding.rvOverlays.setVisibility(View.VISIBLE);

            // Sort list for UI display: Top of list (index 0) = Highest zIndex (Frontmost Layer)
            Collections.sort(list, (o1, o2) -> Integer.compare(o2.getZIndex(), o1.getZIndex()));

            adapter = new OverlayAdapter(list, this);
            binding.rvOverlays.setAdapter(adapter);

            setupDragAndDropReordering(list);
        }
    }

    private void updateZIndexesAndSync(List<OverlayItem> list) {
        // Assign zIndex: top of list (index 0) gets highest zIndex
        int total = list.size();
        for (int i = 0; i < total; i++) {
            list.get(i).setZIndex(total - 1 - i);
        }

        if (overlayCanvasView != null) {
            overlayCanvasView.sortOverlays();
            overlayCanvasView.invalidate();
        }
        if (getContext() != null) {
            io.greycode.streamer.utils.OverlayStorageManager.saveOverlays(getContext(), list);
        }
    }

    private void setupDragAndDropReordering(List<OverlayItem> list) {
        ItemTouchHelper.SimpleCallback simpleCallback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int fromPos = viewHolder.getBindingAdapterPosition();
                int toPos = target.getBindingAdapterPosition();

                if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) return false;

                Collections.swap(list, fromPos, toPos);
                if (adapter != null) {
                    adapter.notifyItemMoved(fromPos, toPos);
                    adapter.notifyItemChanged(fromPos);
                    adapter.notifyItemChanged(toPos);
                }

                updateZIndexesAndSync(list);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // Not used
            }
        };

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(simpleCallback);
        itemTouchHelper.attachToRecyclerView(binding.rvOverlays);
    }

    @Override
    public void onMoveUp(OverlayItem item, int position) {
        if (overlayCanvasView == null || position <= 0) return;
        List<OverlayItem> list = overlayCanvasView.getOverlays();
        Collections.swap(list, position, position - 1);
        updateZIndexesAndSync(list);
        refreshList();
    }

    @Override
    public void onMoveDown(OverlayItem item, int position) {
        if (overlayCanvasView == null) return;
        List<OverlayItem> list = overlayCanvasView.getOverlays();
        if (position >= list.size() - 1) return;
        Collections.swap(list, position, position + 1);
        updateZIndexesAndSync(list);
        refreshList();
    }

    @Override
    public void onEdit(OverlayItem item) {
        dismiss();
        if (editListener != null) {
            editListener.onEditOverlayClick(item);
        }
    }

    @Override
    public void onToggleVisibility(OverlayItem item) {
        item.setVisible(!item.isVisible());
        if (overlayCanvasView != null) overlayCanvasView.invalidate();
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    @Override
    public void onDelete(OverlayItem item) {
        if (overlayCanvasView != null) {
            overlayCanvasView.removeOverlay(item);
            refreshList();
            if (getContext() != null) {
                io.greycode.streamer.utils.OverlayStorageManager.saveOverlays(getContext(), overlayCanvasView.getOverlays());
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
