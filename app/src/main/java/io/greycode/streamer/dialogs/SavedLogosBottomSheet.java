package io.greycode.streamer.dialogs;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.util.List;

import io.greycode.streamer.R;
import io.greycode.streamer.databinding.DialogSavedLogosBinding;
import io.greycode.streamer.utils.LogoManager;

public class SavedLogosBottomSheet extends BottomSheetDialogFragment {

    public interface OnLogoSelectedListener {
        void onLogoSelected(File logoFile, Bitmap bitmap);
        void onPickNewGalleryClick();
    }

    private DialogSavedLogosBinding binding;
    private OnLogoSelectedListener listener;

    public void setOnLogoSelectedListener(OnLogoSelectedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogSavedLogosBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.rvSavedLogos.setLayoutManager(new GridLayoutManager(getContext(), 2));
        refreshList();

        binding.btnPickNewFromGallery.setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onPickNewGalleryClick();
            }
        });
    }

    private void refreshList() {
        if (getContext() == null) return;
        List<File> files = LogoManager.getSavedLogos(getContext());
        if (files.isEmpty()) {
            binding.tvEmptyState.setVisibility(View.VISIBLE);
            binding.rvSavedLogos.setVisibility(View.GONE);
        } else {
            binding.tvEmptyState.setVisibility(View.GONE);
            binding.rvSavedLogos.setVisibility(View.VISIBLE);
            binding.rvSavedLogos.setAdapter(new LogosAdapter(files));
        }
    }

    private class LogosAdapter extends RecyclerView.Adapter<LogosAdapter.LogoViewHolder> {
        private final List<File> logos;

        public LogosAdapter(List<File> logos) {
            this.logos = logos;
        }

        @NonNull
        @Override
        public LogoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_saved_logo, parent, false);
            return new LogoViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull LogoViewHolder holder, int position) {
            File file = logos.get(position);
            holder.bind(file);
        }

        @Override
        public int getItemCount() {
            return logos.size();
        }

        class LogoViewHolder extends RecyclerView.ViewHolder {
            ImageView ivLogoPreview;
            TextView tvLogoName;
            MaterialButton btnSelectLogo;
            MaterialButton btnDeleteLogo;

            public LogoViewHolder(@NonNull View itemView) {
                super(itemView);
                ivLogoPreview = itemView.findViewById(R.id.ivLogoPreview);
                tvLogoName = itemView.findViewById(R.id.tvLogoName);
                btnSelectLogo = itemView.findViewById(R.id.btnSelectLogo);
                btnDeleteLogo = itemView.findViewById(R.id.btnDeleteLogo);
            }

            public void bind(File file) {
                tvLogoName.setText(file.getName());
                Bitmap bmp = LogoManager.loadBitmapFromFile(file.getAbsolutePath());
                if (bmp != null) {
                    ivLogoPreview.setImageBitmap(bmp);
                } else {
                    ivLogoPreview.setImageResource(R.drawable.ic_add);
                }

                btnSelectLogo.setOnClickListener(v -> {
                    dismiss();
                    if (listener != null && bmp != null) {
                        listener.onLogoSelected(file, bmp);
                    }
                });

                btnDeleteLogo.setOnClickListener(v -> {
                    boolean deleted = LogoManager.deleteLogo(file);
                    if (deleted) {
                        Toast.makeText(getContext(), "Logo deleted from app directory", Toast.LENGTH_SHORT).show();
                        refreshList();
                    }
                });
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
