package io.greycode.streamer.dialogs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import io.greycode.streamer.R;
import io.greycode.streamer.databinding.DialogOrientationSelectionBinding;

public class OrientationSelectionDialog extends BottomSheetDialogFragment {

    public interface OnOrientationSelectedListener {
        void onOrientationSelected(String mode, boolean rememberChoice);
    }

    private DialogOrientationSelectionBinding binding;
    private OnOrientationSelectedListener listener;

    public void setOnOrientationSelectedListener(OnOrientationSelectedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogOrientationSelectionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.cardPortrait.setOnClickListener(v -> select("portrait"));
        binding.cardLandscape.setOnClickListener(v -> select("landscape"));
        binding.cardAuto.setOnClickListener(v -> select("auto"));
    }

    private void select(String mode) {
        boolean remember = binding.cbRememberChoice.isChecked();
        if (listener != null) {
            listener.onOrientationSelected(mode, remember);
        }
        dismiss();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
