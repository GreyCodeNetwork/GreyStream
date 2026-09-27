package io.greycode.streamer.dialogs;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.io.File;
import java.io.InputStream;

import io.greycode.streamer.R;
import io.greycode.streamer.databinding.BottomSheetAddOverlayBinding;
import io.greycode.streamer.overlay.BackgroundType;
import io.greycode.streamer.overlay.OverlayAnimation;
import io.greycode.streamer.overlay.OverlayItem;
import io.greycode.streamer.overlay.OverlayType;
import io.greycode.streamer.overlay.ScrollDirection;

public class AddOverlayBottomSheet extends BottomSheetDialogFragment {

    public interface OnOverlayCreatedListener {
        void onOverlayCreated(OverlayItem item);
    }

    private BottomSheetAddOverlayBinding binding;
    private OnOverlayCreatedListener listener;
    private OverlayItem itemToEdit = null;

    private OverlayType selectedType = OverlayType.SCROLLING_TEXT;
    private ScrollDirection selectedScrollDirection = ScrollDirection.RIGHT_TO_LEFT;
    private OverlayAnimation selectedAnimation = OverlayAnimation.NONE;
    private BackgroundType selectedBgType = BackgroundType.COLOR;
    private int selectedColor = Color.WHITE;
    private Bitmap selectedBitmap = null;
    private String selectedLogoPath = null;
    private Bitmap selectedBgBitmap = null;
    private boolean isPickingBgImage = false;

    private ActivityResultLauncher<Intent> imagePickerLauncher;

    public void setOnOverlayCreatedListener(OnOverlayCreatedListener listener) {
        this.listener = listener;
    }

    public void setOverlayItemToEdit(OverlayItem item) {
        this.itemToEdit = item;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            try {
                                InputStream is = requireContext().getContentResolver().openInputStream(uri);
                                Bitmap loaded = BitmapFactory.decodeStream(is);
                                if (isPickingBgImage) {
                                    selectedBgBitmap = loaded;
                                    Toast.makeText(getContext(), "Background Image loaded!", Toast.LENGTH_SHORT).show();
                                } else {
                                    updateSelectedLogoPreview(loaded);
                                    // Automatically prompt user for cropping & transparent background removal
                                    CustomCropBottomSheet cropSheet = new CustomCropBottomSheet();
                                    cropSheet.setSourceBitmap(loaded);
                                    cropSheet.setOnCropAppliedListener((croppedBitmap, savedFilePath) -> {
                                        updateSelectedLogoPreview(croppedBitmap);
                                        selectedLogoPath = savedFilePath;
                                    });
                                    cropSheet.show(getParentFragmentManager(), "CustomCropBottomSheet");
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                Toast.makeText(getContext(), "Failed to load image", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetAddOverlayBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (itemToEdit != null) {
            binding.tvSheetTitle.setText("Edit Custom Overlay");
            binding.btnSaveOverlay.setText("Update Overlay");

            selectedType = itemToEdit.getType();
            selectedScrollDirection = itemToEdit.getScrollDirection();
            selectedAnimation = itemToEdit.getAnimation();
            selectedBgType = itemToEdit.getBgType();
            selectedColor = itemToEdit.getTextColor();
            selectedBitmap = itemToEdit.getImageBitmap();
            selectedBgBitmap = itemToEdit.getBgImageBitmap();

            binding.etLayerName.setText(itemToEdit.getName());
            binding.etOverlayText.setText(itemToEdit.getText());
            binding.etHtmlUrlOrCode.setText(itemToEdit.getHtmlUrlOrCode());

            float safeScale = snapToStep(itemToEdit.getScale(), 0.5f, 3.0f, 0.1f);
            binding.sliderScale.setValue(safeScale);

            float safeSpeed = snapToStep(itemToEdit.getScrollSpeed(), 1.0f, 20.0f, 1.0f);
            binding.sliderSpeed.setValue(safeSpeed);

            float safeLayerAlpha = snapToStep(itemToEdit.getAlpha(), 0.05f, 1.0f, 0.05f);
            binding.sliderLayerAlpha.setValue(safeLayerAlpha);

            float safeBgAlpha = snapToStep(itemToEdit.getBgAlpha(), 0.0f, 1.0f, 0.05f);
            binding.sliderBgAlpha.setValue(safeBgAlpha);

            selectedLogoPath = itemToEdit.getImagePath();
            binding.switchHtmlFullPage.setChecked(itemToEdit.isHtmlFullPage());
            binding.sliderCropLeft.setValue(snapToStep(itemToEdit.getCropLeftPercent(), 0f, 0.45f, 0.01f));
            binding.sliderCropRight.setValue(snapToStep(itemToEdit.getCropRightPercent(), 0f, 0.45f, 0.01f));
            binding.sliderCropTop.setValue(snapToStep(itemToEdit.getCropTopPercent(), 0f, 0.45f, 0.01f));
            binding.sliderCropBottom.setValue(snapToStep(itemToEdit.getCropBottomPercent(), 0f, 0.45f, 0.01f));

            switch (selectedType) {
                case SCROLLING_TEXT: binding.chipMarquee.setChecked(true); break;
                case IMAGE_LOGO: binding.chipLogo.setChecked(true); break;
                case TEXT: binding.chipText.setChecked(true); break;
                case HTML_OVERLAY: binding.chipHtml.setChecked(true); break;
            }

            switch (selectedScrollDirection) {
                case RIGHT_TO_LEFT: binding.chipScrollRightToLeft.setChecked(true); break;
                case LEFT_TO_RIGHT: binding.chipScrollLeftToRight.setChecked(true); break;
            }

            switch (selectedAnimation) {
                case NONE: binding.chipAnimNone.setChecked(true); break;
                case PULSE: binding.chipAnimPulse.setChecked(true); break;
                case FADE: binding.chipAnimFade.setChecked(true); break;
                case SPIN: binding.chipAnimSpin.setChecked(true); break;
                case BOUNCE: binding.chipAnimBounce.setChecked(true); break;
            }

            switch (selectedBgType) {
                case NONE: binding.chipBgNone.setChecked(true); break;
                case COLOR: binding.chipBgColor.setChecked(true); break;
                case IMAGE: binding.chipBgImage.setChecked(true); break;
            }
        } else {
            selectedBgType = BackgroundType.NONE;
            binding.chipBgNone.setChecked(true);
        }

        updateTypeVisibility(selectedType);

        // Chip selection logic for overlay type
        binding.chipGroupType.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chipMarquee)) {
                selectedType = OverlayType.SCROLLING_TEXT;
            } else if (checkedIds.contains(R.id.chipLogo)) {
                selectedType = OverlayType.IMAGE_LOGO;
            } else if (checkedIds.contains(R.id.chipText)) {
                selectedType = OverlayType.TEXT;
            } else if (checkedIds.contains(R.id.chipHtml)) {
                selectedType = OverlayType.HTML_OVERLAY;
            }
            updateTypeVisibility(selectedType);
        });

        // Scroll direction chip group
        binding.chipGroupDirection.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chipScrollRightToLeft)) {
                selectedScrollDirection = ScrollDirection.RIGHT_TO_LEFT;
            } else if (checkedIds.contains(R.id.chipScrollLeftToRight)) {
                selectedScrollDirection = ScrollDirection.LEFT_TO_RIGHT;
            }
        });

        // Animation chip group
        binding.chipGroupAnimation.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chipAnimNone)) {
                selectedAnimation = OverlayAnimation.NONE;
            } else if (checkedIds.contains(R.id.chipAnimPulse)) {
                selectedAnimation = OverlayAnimation.PULSE;
            } else if (checkedIds.contains(R.id.chipAnimFade)) {
                selectedAnimation = OverlayAnimation.FADE;
            } else if (checkedIds.contains(R.id.chipAnimSpin)) {
                selectedAnimation = OverlayAnimation.SPIN;
            } else if (checkedIds.contains(R.id.chipAnimBounce)) {
                selectedAnimation = OverlayAnimation.BOUNCE;
            }
        });

        // Background type chip selection logic
        binding.chipGroupBgType.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chipBgNone)) {
                selectedBgType = BackgroundType.NONE;
                binding.btnPickBgImage.setVisibility(View.GONE);
            } else if (checkedIds.contains(R.id.chipBgColor)) {
                selectedBgType = BackgroundType.COLOR;
                binding.btnPickBgImage.setVisibility(View.GONE);
            } else if (checkedIds.contains(R.id.chipBgImage)) {
                selectedBgType = BackgroundType.IMAGE;
                binding.btnPickBgImage.setVisibility(View.VISIBLE);
            }
        });

        // Open Saved Logos Library
        binding.btnOpenLogosLibrary.setOnClickListener(v -> {
            SavedLogosBottomSheet sheet = new SavedLogosBottomSheet();
            sheet.setOnLogoSelectedListener(new SavedLogosBottomSheet.OnLogoSelectedListener() {
                @Override
                public void onLogoSelected(File logoFile, Bitmap bitmap) {
                    updateSelectedLogoPreview(bitmap);
                }

                @Override
                public void onPickNewGalleryClick() {
                    isPickingBgImage = false;
                    Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    imagePickerLauncher.launch(intent);
                }
            });
            sheet.show(getParentFragmentManager(), "SavedLogosBottomSheet");
        });

        // Logo Image picker click
        binding.btnPickImage.setOnClickListener(v -> {
            isPickingBgImage = false;
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            imagePickerLauncher.launch(intent);
        });

        // Background Image picker click
        binding.btnPickBgImage.setOnClickListener(v -> {
            isPickingBgImage = true;
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            imagePickerLauncher.launch(intent);
        });

        // Custom Crop Button
        binding.btnCropImage.setOnClickListener(v -> {
            if (selectedBitmap != null) {
                CustomCropBottomSheet cropSheet = new CustomCropBottomSheet();
                cropSheet.setSourceBitmap(selectedBitmap);
                cropSheet.setOnCropAppliedListener((croppedBitmap, savedFilePath) -> {
                    updateSelectedLogoPreview(croppedBitmap);
                    selectedLogoPath = savedFilePath;
                });
                cropSheet.show(getParentFragmentManager(), "CustomCropBottomSheet");
            } else {
                Toast.makeText(getContext(), "Please select an image first to crop", Toast.LENGTH_SHORT).show();
            }
        });

        // Background Transparency Removal Button
        binding.btnRemoveBackground.setOnClickListener(v -> {
            if (selectedBitmap != null) {
                Bitmap transparent = io.greycode.streamer.utils.ImageProcessingUtils.makeBackgroundTransparent(selectedBitmap, 0, 65);
                if (transparent != null) {
                    updateSelectedLogoPreview(transparent);
                    selectedBgType = BackgroundType.NONE;
                    binding.chipBgNone.setChecked(true);
                    String savedPath = io.greycode.streamer.utils.LogoManager.saveOrOverwriteLogo(requireContext(), transparent, "transparent_" + System.currentTimeMillis());
                    if (savedPath != null) {
                        selectedLogoPath = savedPath;
                    }
                    Toast.makeText(getContext(), "Background made transparent & saved!", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(getContext(), "Please select an image first", Toast.LENGTH_SHORT).show();
            }
        });

        // Color selection
        binding.btnColorWhite.setOnClickListener(v -> selectedColor = Color.WHITE);
        binding.btnColorRed.setOnClickListener(v -> selectedColor = Color.parseColor("#FF3B30"));
        binding.btnColorYellow.setOnClickListener(v -> selectedColor = Color.parseColor("#FFCC00"));
        binding.btnColorCyan.setOnClickListener(v -> selectedColor = Color.parseColor("#00E5FF"));
        binding.btnColorPurple.setOnClickListener(v -> selectedColor = Color.parseColor("#7C4DFF"));

        if (selectedBitmap != null) {
            updateSelectedLogoPreview(selectedBitmap);
        }

        // Save click
        binding.btnSaveOverlay.setOnClickListener(v -> {
            String layerName = binding.etLayerName.getText() != null ? binding.etLayerName.getText().toString().trim() : "";
            String text = binding.etOverlayText.getText() != null ? binding.etOverlayText.getText().toString() : "";
            String htmlText = binding.etHtmlUrlOrCode.getText() != null ? binding.etHtmlUrlOrCode.getText().toString() : "";

            OverlayItem item = (itemToEdit != null) ? itemToEdit : new OverlayItem(selectedType, text);
            item.setName(layerName);
            item.setType(selectedType);
            item.setText(text);
            item.setScrollDirection(selectedScrollDirection);
            item.setAnimation(selectedAnimation);
            item.setTextColor(selectedColor);
            item.setBgType(selectedBgType);
            item.setAlpha(binding.sliderLayerAlpha.getValue());
            item.setBgAlpha(binding.sliderBgAlpha.getValue());
            item.setScale(binding.sliderScale.getValue());
            item.setScrollSpeed(binding.sliderSpeed.getValue());

            item.setCropLeftPercent(binding.sliderCropLeft.getValue());
            item.setCropRightPercent(binding.sliderCropRight.getValue());
            item.setCropTopPercent(binding.sliderCropTop.getValue());
            item.setCropBottomPercent(binding.sliderCropBottom.getValue());

            if (selectedType == OverlayType.HTML_OVERLAY) {
                item.setHtmlUrlOrCode(htmlText);
                item.setHtmlFullPage(binding.switchHtmlFullPage.isChecked());
                item.initHtmlRenderer(requireContext());
            }
            if (selectedType == OverlayType.IMAGE_LOGO && selectedBitmap != null) {
                item.setImageBitmap(selectedBitmap);
                String targetPathOrName = (selectedLogoPath != null && !selectedLogoPath.isEmpty()) ? selectedLogoPath : layerName;
                String savedPath = io.greycode.streamer.utils.LogoManager.saveOrOverwriteLogo(requireContext(), selectedBitmap, targetPathOrName);
                if (savedPath != null) {
                    item.setImagePath(savedPath);
                }
            }
            if (selectedBgType == BackgroundType.IMAGE && selectedBgBitmap != null) {
                item.setBgImageBitmap(selectedBgBitmap);
                String savedBgPath = io.greycode.streamer.utils.LogoManager.saveLogo(requireContext(), selectedBgBitmap, "bg_" + layerName);
                if (savedBgPath != null) {
                    item.setBgImagePath(savedBgPath);
                }
            }

            if (listener != null) {
                listener.onOverlayCreated(item);
            }
            dismiss();
        });
    }

    private void updateSelectedLogoPreview(Bitmap bitmap) {
        this.selectedBitmap = bitmap;
        if (binding != null) {
            binding.ivSelectedLogoPreview.setImageBitmap(bitmap);
            binding.cardImagePreview.setVisibility(bitmap != null ? View.VISIBLE : View.GONE);
        }
    }

    private void updateTypeVisibility(OverlayType type) {
        if (binding == null) return;
        switch (type) {
            case SCROLLING_TEXT:
                binding.layoutScrollOptions.setVisibility(View.VISIBLE);
                binding.layoutAnimationOptions.setVisibility(View.VISIBLE);
                binding.tilText.setVisibility(View.VISIBLE);
                binding.tilText.setHint("Scrolling Text Message");
                binding.layoutHtmlSection.setVisibility(View.GONE);
                binding.layoutLogoOptions.setVisibility(View.GONE);
                binding.layoutTextColorSection.setVisibility(View.VISIBLE);
                break;

            case IMAGE_LOGO:
                binding.layoutScrollOptions.setVisibility(View.GONE);
                binding.layoutAnimationOptions.setVisibility(View.VISIBLE);
                binding.tilText.setVisibility(View.GONE);
                binding.layoutHtmlSection.setVisibility(View.GONE);
                binding.layoutLogoOptions.setVisibility(View.VISIBLE);
                binding.layoutTextColorSection.setVisibility(View.GONE);
                break;

            case TEXT:
                binding.layoutScrollOptions.setVisibility(View.GONE);
                binding.layoutAnimationOptions.setVisibility(View.VISIBLE);
                binding.tilText.setVisibility(View.VISIBLE);
                binding.tilText.setHint("Static Text Overlay Message");
                binding.layoutHtmlSection.setVisibility(View.GONE);
                binding.layoutLogoOptions.setVisibility(View.GONE);
                binding.layoutTextColorSection.setVisibility(View.VISIBLE);
                break;

            case HTML_OVERLAY:
                binding.layoutScrollOptions.setVisibility(View.GONE);
                binding.layoutAnimationOptions.setVisibility(View.VISIBLE);
                binding.tilText.setVisibility(View.GONE);
                binding.layoutHtmlSection.setVisibility(View.VISIBLE);
                binding.layoutLogoOptions.setVisibility(View.GONE);
                binding.layoutTextColorSection.setVisibility(View.GONE);
                break;
        }
    }

    private float snapToStep(float value, float valueFrom, float valueTo, float stepSize) {
        float clamped = Math.max(valueFrom, Math.min(valueTo, value));
        if (stepSize <= 0f) return clamped;
        float steps = Math.round((clamped - valueFrom) / stepSize);
        float snapped = valueFrom + (steps * stepSize);
        return Math.max(valueFrom, Math.min(valueTo, snapped));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
