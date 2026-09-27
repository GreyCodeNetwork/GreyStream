package io.greycode.streamer.dialogs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import io.greycode.streamer.R;
import io.greycode.streamer.databinding.BottomSheetStreamSettingsBinding;
import io.greycode.streamer.utils.ProfileManager;

public class StreamSettingsBottomSheet extends BottomSheetDialogFragment {

    public interface OnSettingsSavedListener {
        void onSettingsSaved();
    }

    private BottomSheetStreamSettingsBinding binding;
    private ProfileManager profileManager;
    private OnSettingsSavedListener listener;
    private boolean isStreamingActive = false;

    public void setOnSettingsSavedListener(OnSettingsSavedListener listener) {
        this.listener = listener;
    }

    public void setStreamingActive(boolean active) {
        this.isStreamingActive = active;
    }

    public void updateAudioLevels(int levelL, int levelR) {
        if (binding != null) {
            if (binding.settingsAudioLevelBarL != null) binding.settingsAudioLevelBarL.setProgress(levelL);
            if (binding.settingsAudioLevelBarR != null) binding.settingsAudioLevelBarR.setProgress(levelR);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetStreamSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        profileManager = new ProfileManager(requireContext());

        // Populate existing settings
        binding.etServerUrl.setText(profileManager.getServerUrl());
        binding.etStreamKey.setText(profileManager.getStreamKey());
        binding.sliderBitrate.setValue((float) profileManager.getBitrateKbps());
        binding.sliderGop.setValue((float) profileManager.getKeyframeInterval());
        binding.switchRecord.setChecked(profileManager.isRecordLocal());
        binding.switchAdaptiveBitrate.setChecked(profileManager.isAdaptiveBitrateEnabled());

        updateBitrateLabel(profileManager.getBitrateKbps());
        updateGopLabel(profileManager.getKeyframeInterval());

        // 1. Preset Chip Selection initialization
        String preset = profileManager.getPreset();
        if (ProfileManager.PRESET_TWITCH.equalsIgnoreCase(preset)) {
            binding.chipTwitch.setChecked(true);
        } else if (ProfileManager.PRESET_FACEBOOK.equalsIgnoreCase(preset)) {
            binding.chipFacebook.setChecked(true);
        } else if (ProfileManager.PRESET_CUSTOM.equalsIgnoreCase(preset)) {
            binding.chipCustom.setChecked(true);
        } else {
            binding.chipYoutube.setChecked(true);
        }

        // Audio gain slider initialization
        float currentGain = profileManager.getAudioGain();
        int currentGainPercent = Math.round(currentGain * 100f);
        binding.sliderAudioGain.setValue((float) Math.max(0, Math.min(300, currentGainPercent)));
        updateAudioGainLabel(currentGainPercent);

        binding.sliderAudioGain.addOnChangeListener((slider, value, fromUser) -> {
            updateAudioGainLabel((int) value);
            if (profileManager != null) {
                profileManager.setAudioGain(value / 100f);
            }
        });

        // 2. Resolution Chip Selection initialization
        int resW = profileManager.getResolutionWidth();
        int resH = profileManager.getResolutionHeight();
        if (resW == 0 || resH == 0) {
            binding.chipResAuto.setChecked(true);
            binding.layoutCustomResolution.setVisibility(View.GONE);
        } else if (resH == 1080 && resW == 1920) {
            binding.chip1080p.setChecked(true);
            binding.layoutCustomResolution.setVisibility(View.GONE);
        } else if (resH == 480 && resW == 854) {
            binding.chip480p.setChecked(true);
            binding.layoutCustomResolution.setVisibility(View.GONE);
        } else if (resH == 720 && resW == 1280) {
            binding.chip720p.setChecked(true);
            binding.layoutCustomResolution.setVisibility(View.GONE);
        } else {
            binding.chipResCustom.setChecked(true);
            binding.layoutCustomResolution.setVisibility(View.VISIBLE);
            binding.etCustomWidth.setText(String.valueOf(resW));
            binding.etCustomHeight.setText(String.valueOf(resH));
        }

        binding.chipGroupResolution.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chipResCustom)) {
                binding.layoutCustomResolution.setVisibility(View.VISIBLE);
            } else {
                binding.layoutCustomResolution.setVisibility(View.GONE);
            }
        });

        // 3. FPS Chip Selection initialization
        int fps = profileManager.getFps();
        if (fps == 0) {
            binding.chipFpsAuto.setChecked(true);
        } else if (fps == 60) {
            binding.chipFps60.setChecked(true);
        } else {
            binding.chipFps30.setChecked(true);
        }

        // 4. Audio Source Selection initialization
        String audioSource = profileManager.getAudioSource();
        if ("external".equalsIgnoreCase(audioSource)) {
            binding.chipAudioExternal.setChecked(true);
        } else if ("bluetooth".equalsIgnoreCase(audioSource)) {
            binding.chipAudioBluetooth.setChecked(true);
        } else if ("unprocessed".equalsIgnoreCase(audioSource)) {
            binding.chipAudioUnprocessed.setChecked(true);
        } else {
            binding.chipAudioInternal.setChecked(true);
        }

        // 5. Aspect Ratio mode selection initialization
        String currentAspectMode = profileManager.getAspectRatioMode();
        if ("fill".equalsIgnoreCase(currentAspectMode)) {
            binding.chipAspectFill.setChecked(true);
        } else if ("none".equalsIgnoreCase(currentAspectMode)) {
            binding.chipAspectNone.setChecked(true);
        } else {
            binding.chipAspectAdjust.setChecked(true);
        }

        // Preset chips selection listener
        binding.chipGroupProfile.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chipYoutube)) {
                binding.etServerUrl.setText("rtmp://a.rtmp.youtube.com/live2");
            } else if (checkedIds.contains(R.id.chipTwitch)) {
                binding.etServerUrl.setText("rtmp://live.twitch.tv/app");
            } else if (checkedIds.contains(R.id.chipFacebook)) {
                binding.etServerUrl.setText("rtmps://live-api-s.facebook.com:443/rtmp/");
            }
        });

        // Bitrate slider changes
        binding.sliderBitrate.addOnChangeListener((slider, value, fromUser) -> {
            updateBitrateLabel((int) value);
        });

        // GOP slider changes
        binding.sliderGop.addOnChangeListener((slider, value, fromUser) -> {
            updateGopLabel((int) value);
        });

        // Check if streaming is active; if so, lock stream parameters
        if (isStreamingActive) {
            binding.layoutLiveLockBanner.setVisibility(View.VISIBLE);
            setSettingsLockedDuringLive(true);
        }

        // Save settings click
        binding.btnSaveSettings.setOnClickListener(v -> {
            if (!isStreamingActive) {
                String url = binding.etServerUrl.getText() != null ? binding.etServerUrl.getText().toString().trim() : "";
                String key = binding.etStreamKey.getText() != null ? binding.etStreamKey.getText().toString().trim() : "";

                if (url.isEmpty()) {
                    Toast.makeText(getContext(), "Please enter a valid RTMP/RTMPS Server URL", Toast.LENGTH_SHORT).show();
                    return;
                }

                profileManager.setServerUrl(url);
                profileManager.setStreamKey(key);
                profileManager.setBitrateKbps((int) binding.sliderBitrate.getValue());
                profileManager.setKeyframeInterval((int) binding.sliderGop.getValue());
                profileManager.setAdaptiveBitrateEnabled(binding.switchAdaptiveBitrate.isChecked());

                // Preset Selection
                if (binding.chipTwitch.isChecked()) {
                    profileManager.setPreset(ProfileManager.PRESET_TWITCH);
                } else if (binding.chipFacebook.isChecked()) {
                    profileManager.setPreset(ProfileManager.PRESET_FACEBOOK);
                } else if (binding.chipCustom.isChecked()) {
                    profileManager.setPreset(ProfileManager.PRESET_CUSTOM);
                } else {
                    profileManager.setPreset(ProfileManager.PRESET_YOUTUBE);
                }

                // Resolution Selection
                if (binding.chipResAuto.isChecked()) {
                    profileManager.setResolution(0, 0);
                } else if (binding.chipResCustom.isChecked()) {
                    String strW = binding.etCustomWidth.getText() != null ? binding.etCustomWidth.getText().toString().trim() : "1920";
                    String strH = binding.etCustomHeight.getText() != null ? binding.etCustomHeight.getText().toString().trim() : "1080";
                    int cW = 1920;
                    int cH = 1080;
                    try {
                        cW = Integer.parseInt(strW);
                        cH = Integer.parseInt(strH);
                    } catch (Exception ignored) {}
                    cW = Math.max(144, Math.min(4096, cW));
                    cH = Math.max(144, Math.min(4096, cH));
                    profileManager.setResolution(cW, cH);
                } else if (binding.chip1080p.isChecked()) {
                    profileManager.setResolution(1920, 1080);
                } else if (binding.chip480p.isChecked()) {
                    profileManager.setResolution(854, 480);
                } else {
                    profileManager.setResolution(1280, 720);
                }

                // FPS Selection
                if (binding.chipFpsAuto.isChecked()) {
                    profileManager.setFps(0);
                } else if (binding.chipFps60.isChecked()) {
                    profileManager.setFps(60);
                } else {
                    profileManager.setFps(30);
                }

                // Audio Source Selection
                if (binding.chipAudioExternal.isChecked()) {
                    profileManager.setAudioSource("external");
                } else if (binding.chipAudioBluetooth.isChecked()) {
                    profileManager.setAudioSource("bluetooth");
                } else if (binding.chipAudioUnprocessed.isChecked()) {
                    profileManager.setAudioSource("unprocessed");
                } else {
                    profileManager.setAudioSource("internal");
                }

                // Aspect Ratio mode selection
                if (binding.chipAspectFill.isChecked()) {
                    profileManager.setAspectRatioMode("fill");
                } else if (binding.chipAspectNone.isChecked()) {
                    profileManager.setAspectRatioMode("none");
                } else {
                    profileManager.setAspectRatioMode("adjust");
                }
            }

            // Always allow updating Local Record and Audio Gain!
            profileManager.setRecordLocal(binding.switchRecord.isChecked());
            profileManager.setAudioGain(binding.sliderAudioGain.getValue() / 100f);

            Toast.makeText(getContext(), "Stream configuration saved successfully!", Toast.LENGTH_SHORT).show();

            if (listener != null) {
                listener.onSettingsSaved();
            }
            dismiss();
        });
    }

    private void setSettingsLockedDuringLive(boolean locked) {
        boolean enabled = !locked;
        binding.chipGroupProfile.setEnabled(enabled);
        binding.tilServerUrl.setEnabled(enabled);
        binding.etServerUrl.setEnabled(enabled);
        binding.tilStreamKey.setEnabled(enabled);
        binding.etStreamKey.setEnabled(enabled);
        binding.chipGroupAudioSource.setEnabled(enabled);
        binding.chipGroupResolution.setEnabled(enabled);
        binding.layoutCustomResolution.setEnabled(enabled);
        binding.etCustomWidth.setEnabled(enabled);
        binding.etCustomHeight.setEnabled(enabled);
        binding.chipGroupFps.setEnabled(enabled);
        binding.sliderBitrate.setEnabled(enabled);
        binding.switchAdaptiveBitrate.setEnabled(enabled);
        binding.sliderGop.setEnabled(enabled);
        binding.chipGroupAspectRatio.setEnabled(enabled);

        // Visually dim locked components
        float alpha = enabled ? 1.0f : 0.5f;
        binding.chipGroupProfile.setAlpha(alpha);
        binding.tilServerUrl.setAlpha(alpha);
        binding.tilStreamKey.setAlpha(alpha);
        binding.chipGroupAudioSource.setAlpha(alpha);
        binding.chipGroupResolution.setAlpha(alpha);
        binding.layoutCustomResolution.setAlpha(alpha);
        binding.chipGroupFps.setAlpha(alpha);
        binding.sliderBitrate.setAlpha(alpha);
        binding.switchAdaptiveBitrate.setAlpha(alpha);
        binding.sliderGop.setAlpha(alpha);
        binding.chipGroupAspectRatio.setAlpha(alpha);
    }

    private void updateAudioGainLabel(int percent) {
        if (binding != null && binding.tvAudioGainLabel != null) {
            binding.tvAudioGainLabel.setText("Input Microphone Gain: " + percent + "%");
        }
    }

    private void updateBitrateLabel(int kbps) {
        binding.tvBitrateLabel.setText("Video Bitrate: " + kbps + " Kbps");
    }

    private void updateGopLabel(int seconds) {
        binding.tvGopLabel.setText("Keyframe Interval (GOP): " + seconds + " seconds");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
