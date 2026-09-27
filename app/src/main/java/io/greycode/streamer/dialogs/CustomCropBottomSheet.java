package io.greycode.streamer.dialogs;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import io.greycode.streamer.R;
import io.greycode.streamer.databinding.BottomSheetCustomCropBinding;
import io.greycode.streamer.utils.ImageProcessingUtils;
import io.greycode.streamer.utils.LogoManager;

public class CustomCropBottomSheet extends BottomSheetDialogFragment {

    public interface OnCropAppliedListener {
        void onCropApplied(Bitmap croppedBitmap, String savedFilePath);
    }

    private BottomSheetCustomCropBinding binding;
    private Bitmap sourceBitmap;
    private OnCropAppliedListener listener;

    public void setSourceBitmap(Bitmap bitmap) {
        this.sourceBitmap = bitmap;
    }

    public void setOnCropAppliedListener(OnCropAppliedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetCustomCropBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (sourceBitmap == null) {
            Toast.makeText(getContext(), "No image selected to crop", Toast.LENGTH_SHORT).show();
            dismiss();
            return;
        }

        binding.dragCropView.setBitmap(sourceBitmap);
        binding.layoutSlidersSection.setVisibility(View.GONE);

        // Aspect ratio selection listener
        binding.chipGroupAspectRatio.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chipRatioFree)) {
                binding.dragCropView.setAspectRatio(0f);
            } else if (checkedIds.contains(R.id.chipRatioSquare)) {
                binding.dragCropView.setAspectRatio(1.0f);
            } else if (checkedIds.contains(R.id.chipRatio169)) {
                binding.dragCropView.setAspectRatio(16f / 9f);
            } else if (checkedIds.contains(R.id.chipRatio43)) {
                binding.dragCropView.setAspectRatio(4f / 3f);
            } else if (checkedIds.contains(R.id.chipRatio916)) {
                binding.dragCropView.setAspectRatio(9f / 16f);
            }
        });

        // Make background transparent inside crop sheet
        binding.btnRemoveBgInCrop.setOnClickListener(v -> {
            Bitmap current = binding.dragCropView.getCroppedBitmap();
            if (current == null) current = sourceBitmap;
            Bitmap transparent = ImageProcessingUtils.makeBackgroundTransparent(current, 0, 65);
            if (transparent != null) {
                sourceBitmap = transparent;
                binding.dragCropView.setBitmap(transparent);
                Toast.makeText(getContext(), "Background made transparent!", Toast.LENGTH_SHORT).show();
            }
        });

        // Cancel
        binding.btnCancelCrop.setOnClickListener(v -> dismiss());

        // Apply & Save to App Dir
        binding.btnApplyCrop.setOnClickListener(v -> {
            Bitmap cropped = binding.dragCropView.getCroppedBitmap();
            if (cropped != null) {
                String savedPath = LogoManager.saveLogo(requireContext(), cropped, "logo_" + System.currentTimeMillis());
                if (listener != null) {
                    listener.onCropApplied(cropped, savedPath);
                }
                Toast.makeText(getContext(), "Logo processed & saved to app storage!", Toast.LENGTH_SHORT).show();
                dismiss();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
