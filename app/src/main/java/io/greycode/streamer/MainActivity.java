package io.greycode.streamer;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import android.os.SystemClock;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import io.greycode.streamer.databinding.ActivityMainBinding;
import io.greycode.streamer.dialogs.AddOverlayBottomSheet;
import io.greycode.streamer.dialogs.OverlayListBottomSheet;
import io.greycode.streamer.dialogs.StreamSettingsBottomSheet;
import io.greycode.streamer.overlay.OverlayCanvasView;
import io.greycode.streamer.overlay.OverlayComposer;
import io.greycode.streamer.overlay.OverlayItem;
import io.greycode.streamer.overlay.OverlayTouchListener;
import io.greycode.streamer.overlay.OverlayType;
import io.greycode.streamer.streaming.StreamManager;
import io.greycode.streamer.utils.PermissionUtils;
import io.greycode.streamer.utils.ProfileManager;

import android.view.SurfaceHolder;
import android.view.View;

public class MainActivity extends AppCompatActivity implements StreamManager.StreamListener, OverlayTouchListener.OnOverlaySelectedListener, SurfaceHolder.Callback {

    private ActivityMainBinding binding;
    private StreamManager streamManager;
    private OverlayComposer overlayComposer;
    private OverlayTouchListener overlayTouchListener;
    private ProfileManager profileManager;

    private boolean isStreaming = false;
    private boolean isOrientationLocked = false;
    private int retryCount = 0;
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private boolean isRetrying = false;
    private final Handler retryHandler = new Handler(Looper.getMainLooper());
    private final Runnable retryRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isStreaming) {
                startStreamProcess();
            }
        }
    };
    private long streamStartTime = 0L;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isStreaming) {
                long millis = SystemClock.elapsedRealtime() - streamStartTime;
                int seconds = (int) (millis / 1000) % 60;
                int minutes = (int) ((millis / (1000 * 60)) % 60);
                int hours = (int) ((millis / (1000 * 60 * 60)) % 24);

                binding.tvStreamTimer.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
                timerHandler.postDelayed(this, 1000);
            }
        }
    };

    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.mainContainer, (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        profileManager = new ProfileManager(this);
        binding.openGlView.getHolder().addCallback(this);

        // Permission launcher setup
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean allGranted = true;
                    for (Boolean granted : result.values()) {
                        if (!granted) {
                            allGranted = false;
                            break;
                        }
                    }
                    if (allGranted) {
                        checkAndApplyStartupOrientation();
                    } else {
                        Toast.makeText(this, "Camera & Audio permissions are required for live streaming.", Toast.LENGTH_LONG).show();
                    }
                });

        if (PermissionUtils.hasAllPermissions(this)) {
            checkAndApplyStartupOrientation();
        } else {
            permissionLauncher.launch(PermissionUtils.getRequiredPermissions());
        }

        setupUIInteractions();
    }

    private boolean isEngineInitialized = false;

    private void checkAndApplyStartupOrientation() {
        applySavedOrientationMode(profileManager.getPreferredOrientation());
        startStreamingEngineIfReady();
    }

    private void startStreamingEngineIfReady() {
        if (!isEngineInitialized) {
            isEngineInitialized = true;
            initStreamingEngine();
        } else {
            int resW = profileManager.getResolutionWidth();
            int resH = profileManager.getResolutionHeight();
            if (streamManager != null) {
                streamManager.startPreview(resW, resH);
            }
        }
    }

    private void initStreamingEngine() {
        // Initialize Stream Manager
        streamManager = new StreamManager(this, binding.openGlView, this);

        // Load saved overlays from app data directory (or start empty if none saved)
        java.util.List<OverlayItem> savedOverlays = io.greycode.streamer.utils.OverlayStorageManager.loadOverlays(this);
        binding.overlayCanvasView.setOverlays(savedOverlays);

        // Initialize Overlay Touch Listener & Canvas View
        overlayTouchListener = new OverlayTouchListener(binding.overlayCanvasView.getOverlays(), this);
        binding.overlayCanvasView.setTouchListener(overlayTouchListener);

        // Initialize Real-time Overlay Composer for OpenGL Stream Output
        overlayComposer = new OverlayComposer(binding.overlayCanvasView.getOverlays());
        streamManager.bindOverlayComposer(overlayComposer);
        streamManager.bindOverlayView(binding.overlayCanvasView);

        // Start camera preview in full resolution (1080p/720p)
        int resW = profileManager.getResolutionWidth();
        int resH = profileManager.getResolutionHeight();
        streamManager.startPreview(resW, resH);
    }

    private void applySavedOrientationMode(String mode) {
        boolean isPortrait = false;
        if ("portrait".equalsIgnoreCase(mode)) {
            isOrientationLocked = true;
            isPortrait = true;
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            binding.btnLockOrientation.setImageResource(R.drawable.ic_screen_lock);
            Toast.makeText(this, "Orientation: Portrait Mode (9:16)", Toast.LENGTH_SHORT).show();
        } else if ("landscape".equalsIgnoreCase(mode)) {
            isOrientationLocked = true;
            isPortrait = false;
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            binding.btnLockOrientation.setImageResource(R.drawable.ic_screen_lock);
            Toast.makeText(this, "Orientation: Landscape Mode (16:9)", Toast.LENGTH_SHORT).show();
        } else {
            isOrientationLocked = false;
            isPortrait = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT;
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
            binding.btnLockOrientation.setImageResource(R.drawable.ic_screen_rotation);
            Toast.makeText(this, "Orientation: Auto-Rotate (Sensor)", Toast.LENGTH_SHORT).show();
        }
        updatePreviewAspectRatio(isPortrait);
    }

    private void updatePreviewAspectRatio(boolean isPortrait) {
        if (binding == null || binding.previewContainer == null || binding.mainContainer == null) return;
        try {
            androidx.constraintlayout.widget.ConstraintSet constraintSet = new androidx.constraintlayout.widget.ConstraintSet();
            constraintSet.clone(binding.mainContainer);
            String ratio;
            int resW = profileManager.getResolutionWidth();
            int resH = profileManager.getResolutionHeight();
            if (resW == 0 || resH == 0) {
                // Auto Resolution: Show aspect ratio adjustment button and match screen native ratio
                binding.btnAspectRatio.setVisibility(View.VISIBLE);
                android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
                int w = dm.widthPixels;
                int h = dm.heightPixels;
                ratio = isPortrait ? (Math.min(w, h) + ":" + Math.max(w, h)) : (Math.max(w, h) + ":" + Math.min(w, h));
            } else {
                // Fixed / Custom Resolution: Hide aspect ratio button and set preview container to match set resolution
                binding.btnAspectRatio.setVisibility(View.GONE);
                int reqW = Math.max(resW, resH);
                int reqH = Math.min(resW, resH);
                ratio = isPortrait ? (reqH + ":" + reqW) : (reqW + ":" + reqH);
            }
            constraintSet.setDimensionRatio(binding.previewContainer.getId(), ratio);
            constraintSet.applyTo(binding.mainContainer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addInitialDefaultOverlays() {
        // 1. Scrolling Marquee Ticker
        OverlayItem marquee = new OverlayItem(OverlayType.SCROLLING_TEXT, "★ GREYSTREAM LIVE - Broadcasting HD Stream with Drag-and-Drop Overlays ★");
        marquee.setTextColor(Color.WHITE);
        marquee.setBgColor(Color.parseColor("#B3121316"));
        marquee.setYPercent(0.88f);

        // 2. Text Overlay
        OverlayItem textOverlay = new OverlayItem(OverlayType.TEXT, "LIVE STREAM HD");
        textOverlay.setTextColor(Color.parseColor("#FFCC00"));
        textOverlay.setXPercent(0.20f);
        textOverlay.setYPercent(0.08f);

        // 3. Image/Logo Overlay
        OverlayItem logoOverlay = new OverlayItem(OverlayType.IMAGE_LOGO, "GREYCODE");
        logoOverlay.setXPercent(0.82f);
        logoOverlay.setYPercent(0.12f);

        binding.overlayCanvasView.addOverlay(marquee);
        binding.overlayCanvasView.addOverlay(textOverlay);
        binding.overlayCanvasView.addOverlay(logoOverlay);
    }

    private void setupUIInteractions() {
        // GO LIVE / STOP STREAM Button
        binding.btnGoLive.setOnClickListener(v -> {
            if (isRetrying) {
                cancelAutoRetry();
                resetGoLiveButtonState();
                Toast.makeText(this, "Auto-retry cancelled.", Toast.LENGTH_SHORT).show();
            } else if (isStreaming) {
                cancelAutoRetry();
                stopStreamProcess();
            } else {
                cancelAutoRetry();
                startStreamProcess();
            }
        });

        // Lock / Unlock Drag Layout
        binding.btnLockLayout.setOnClickListener(v -> {
            boolean currentLock = overlayTouchListener.isLocked();
            overlayTouchListener.setLocked(!currentLock);

            if (!currentLock) {
                binding.btnLockLayout.setIconResource(R.drawable.ic_lock_closed);
                Toast.makeText(this, "Overlay Drag Layout Locked", Toast.LENGTH_SHORT).show();
            } else {
                binding.btnLockLayout.setIconResource(R.drawable.ic_lock_open);
                Toast.makeText(this, "Overlay Drag Layout Unlocked - Touch to Move/Resize", Toast.LENGTH_SHORT).show();
            }
        });

        // Side Quick Controls
        binding.btnSwitchCamera.setOnClickListener(v -> {
            if (streamManager != null) streamManager.switchCamera();
        });

        binding.btnToggleFlash.setOnClickListener(v -> {
            if (streamManager != null) {
                streamManager.toggleFlash();
                binding.btnToggleFlash.setImageResource(
                        streamManager.isFlashEnabled() ? R.drawable.ic_flash_on : R.drawable.ic_flash_off
                );
            }
        });

        binding.btnToggleMic.setOnClickListener(v -> {
            if (streamManager != null) {
                streamManager.toggleAudioMute();
                binding.btnToggleMic.setImageResource(
                        streamManager.isAudioMuted() ? R.drawable.ic_mic_off : R.drawable.ic_mic_on
                );
            }
        });

        // Aspect Ratio Mode Cycle (Adjust / Match Stream Output -> Fill -> None -> Adjust)
        binding.btnAspectRatio.setOnClickListener(v -> {
            String current = profileManager.getAspectRatioMode();
            String nextMode;
            String toastLabel;
            if ("adjust".equalsIgnoreCase(current)) {
                nextMode = "fill";
                toastLabel = "Aspect Ratio: Fill Screen (Crop)";
            } else if ("fill".equalsIgnoreCase(current)) {
                nextMode = "none";
                toastLabel = "Aspect Ratio: Unscaled Stretch";
            } else {
                nextMode = "adjust";
                toastLabel = "Aspect Ratio: Match Stream Output (16:9 / 9:16)";
            }
            profileManager.setAspectRatioMode(nextMode);
            applyAspectRatioMode();
            Toast.makeText(this, toastLabel, Toast.LENGTH_SHORT).show();
        });

        // Lock / Unlock Screen Orientation Toggle Button
        binding.btnLockOrientation.setOnClickListener(v -> {
            if (isOrientationLocked) {
                isOrientationLocked = false;
                setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
                binding.btnLockOrientation.setImageResource(R.drawable.ic_screen_rotation);
                Toast.makeText(this, "Orientation: Unlocked (Auto-Rotate)", Toast.LENGTH_SHORT).show();
            } else {
                isOrientationLocked = true;
                int currentOrientation = getResources().getConfiguration().orientation;
                if (currentOrientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
                    setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    Toast.makeText(this, "Orientation: Locked in Portrait Mode (9:16)", Toast.LENGTH_SHORT).show();
                } else {
                    setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                    Toast.makeText(this, "Orientation: Locked in Landscape Mode (16:9)", Toast.LENGTH_SHORT).show();
                }
                binding.btnLockOrientation.setImageResource(R.drawable.ic_screen_lock);
            }
        });

        // Bottom Bar Dialog Actions
        binding.btnSettings.setOnClickListener(v -> openStreamSettingsBottomSheet());
        binding.btnAddOverlay.setOnClickListener(v -> openAddOverlayBottomSheet());
        binding.btnOverlayLayers.setOnClickListener(v -> openOverlayListBottomSheet());
    }

    private void startStreamProcess() {
        if (streamManager == null) return;

        // Cleanly reset any lingering RTMP socket connection state from previous failed attempt
        streamManager.stopStream();

        String fullUrl = profileManager.getFullStreamUrl();
        int resW = profileManager.getResolutionWidth();
        int resH = profileManager.getResolutionHeight();
        int bitrateKbps = profileManager.getBitrateKbps();
        int fps = profileManager.getFps();
        int gop = profileManager.getKeyframeInterval();
        String audioSource = profileManager.getAudioSource();
        boolean isAdaptiveBitrate = profileManager.isAdaptiveBitrateEnabled();

        if (streamManager.prepareStream(resW, resH, fps, bitrateKbps, gop, audioSource, isAdaptiveBitrate)) {
            streamManager.setRecordWithoutOverlays(profileManager.isRecordWithoutOverlays());
            if (profileManager.isRecordLocal()) {
                currentRecordFilePath = getRecordFilePath();
                streamManager.startRecord(currentRecordFilePath);
            }
            streamManager.startStream(fullUrl);
        } else {
            onConnectionFailed("Encoder preparation failed (" + resW + "x" + resH + " @ " + bitrateKbps + " Kbps).");
        }
    }

    private String currentRecordFilePath = null;

    private String getRecordFilePath() {
        java.io.File recordDir = getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES);
        if (recordDir == null) {
            recordDir = getFilesDir();
        }
        if (!recordDir.exists()) {
            recordDir.mkdirs();
        }
        String fileName = "GreyStream_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(new java.util.Date()) + ".mp4";
        java.io.File recordFile = new java.io.File(recordDir, fileName);
        return recordFile.getAbsolutePath();
    }

    private void finishRecordingAndSave() {
        if (streamManager != null && streamManager.isRecording()) {
            streamManager.stopRecord();
        }
        if (currentRecordFilePath != null) {
            String tempPath = currentRecordFilePath;
            currentRecordFilePath = null;
            saveRecordedVideoToMediaStore(tempPath);
        }
    }

    private void saveRecordedVideoToMediaStore(String filePath) {
        if (filePath == null) return;
        java.io.File file = new java.io.File(filePath);
        if (!file.exists() || file.length() == 0) return;

        new Thread(() -> {
            try {
                String fileName = file.getName();
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, fileName);
                values.put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4");

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    values.put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, "Movies/GreyStream");
                    values.put(android.provider.MediaStore.Video.Media.IS_PENDING, 1);
                } else {
                    java.io.File moviesDir = new java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES), "GreyStream");
                    if (!moviesDir.exists()) moviesDir.mkdirs();
                    java.io.File destFile = new java.io.File(moviesDir, fileName);
                    values.put(android.provider.MediaStore.Video.Media.DATA, destFile.getAbsolutePath());
                }

                android.net.Uri uri = getContentResolver().insert(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (java.io.OutputStream out = getContentResolver().openOutputStream(uri);
                         java.io.FileInputStream in = new java.io.FileInputStream(file)) {
                        byte[] buffer = new byte[16384];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        values.clear();
                        values.put(android.provider.MediaStore.Video.Media.IS_PENDING, 0);
                        getContentResolver().update(uri, values, null, null);
                    } else {
                        android.media.MediaScannerConnection.scanFile(MainActivity.this, new String[]{file.getAbsolutePath()}, null, null);
                    }

                    // Delete temp file after successful export to MediaStore
                    file.delete();

                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Recording saved to Movies/GreyStream", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to export recording: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void stopStreamProcess() {
        finishRecordingAndSave();
        if (streamManager != null) {
            streamManager.stopStream();
        }
    }

    private StreamSettingsBottomSheet activeSettingsSheet;

    private void openStreamSettingsBottomSheet() {
        resetAutoHideTimer();
        activeSettingsSheet = new StreamSettingsBottomSheet();
        activeSettingsSheet.setStreamingActive(isStreaming);
        activeSettingsSheet.setOnSettingsSavedListener(() -> {
            int resW = profileManager.getResolutionWidth();
            int resH = profileManager.getResolutionHeight();
            if (streamManager != null) {
                streamManager.startPreview(resW, resH);
            }
            applyAspectRatioMode();
            Toast.makeText(this, "Stream Settings & " + resH + "p Quality Applied!", Toast.LENGTH_SHORT).show();
        });
        activeSettingsSheet.show(getSupportFragmentManager(), "StreamSettingsBottomSheet");
    }

    private void openAddOverlayBottomSheet() {
        resetAutoHideTimer();
        AddOverlayBottomSheet sheet = new AddOverlayBottomSheet();
        sheet.setOnOverlayCreatedListener(item -> {
            binding.overlayCanvasView.addOverlay(item);
            io.greycode.streamer.utils.OverlayStorageManager.saveOverlays(this, binding.overlayCanvasView.getOverlays());
            Toast.makeText(this, "Overlay Added & Saved!", Toast.LENGTH_SHORT).show();
        });
        sheet.show(getSupportFragmentManager(), "AddOverlayBottomSheet");
    }

    private void openEditOverlayBottomSheet(OverlayItem item) {
        resetAutoHideTimer();
        AddOverlayBottomSheet sheet = new AddOverlayBottomSheet();
        sheet.setOverlayItemToEdit(item);
        sheet.setOnOverlayCreatedListener(updatedItem -> {
            binding.overlayCanvasView.invalidate();
            io.greycode.streamer.utils.OverlayStorageManager.saveOverlays(this, binding.overlayCanvasView.getOverlays());
            Toast.makeText(this, "Overlay Updated & Saved!", Toast.LENGTH_SHORT).show();
        });
        sheet.show(getSupportFragmentManager(), "EditOverlayBottomSheet");
    }

    private void openOverlayListBottomSheet() {
        resetAutoHideTimer();
        OverlayListBottomSheet sheet = new OverlayListBottomSheet();
        sheet.setOverlayCanvasView(binding.overlayCanvasView);
        sheet.setOnAddNewOverlayClickListener(this::openAddOverlayBottomSheet);
        sheet.setOnEditOverlayClickListener(this::openEditOverlayBottomSheet);
        sheet.show(getSupportFragmentManager(), "OverlayListBottomSheet");
    }

    private void applyAspectRatioMode() {
        if (binding == null || binding.openGlView == null || binding.previewContainer == null || binding.mainContainer == null) return;
        String mode = profileManager.getAspectRatioMode();
        boolean isPortrait = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT;

        androidx.constraintlayout.widget.ConstraintSet constraintSet = new androidx.constraintlayout.widget.ConstraintSet();
        constraintSet.clone(binding.mainContainer);

        if ("adjust".equalsIgnoreCase(mode)) {
            // Match Stream Output (16:9 in landscape, 9:16 in portrait)
            binding.openGlView.setAspectRatioMode(com.pedro.encoder.utils.gl.AspectRatioMode.Fill);
            String ratio = isPortrait ? "9:16" : "16:9";
            constraintSet.setDimensionRatio(binding.previewContainer.getId(), ratio);
        } else if ("fill".equalsIgnoreCase(mode)) {
            // Fill Screen (Crop to full display bounds)
            binding.openGlView.setAspectRatioMode(com.pedro.encoder.utils.gl.AspectRatioMode.Fill);
            constraintSet.setDimensionRatio(binding.previewContainer.getId(), null);
        } else {
            // Unscaled Stretch
            binding.openGlView.setAspectRatioMode(com.pedro.encoder.utils.gl.AspectRatioMode.NONE);
            constraintSet.setDimensionRatio(binding.previewContainer.getId(), null);
        }

        constraintSet.applyTo(binding.mainContainer);
    }

    private void enableImmersiveFullScreen() {
        androidx.core.view.WindowInsetsControllerCompat controller =
                androidx.core.view.ViewCompat.getWindowInsetsController(getWindow().getDecorView());
        if (controller != null) {
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(
                    androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
        }
    }

    private final Handler autoHideHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoHideRunnable = this::hideBottomControlCard;
    private boolean isBottomCardVisible = true;

    private void resetAutoHideTimer() {
        showBottomControlCard();
        autoHideHandler.removeCallbacks(autoHideRunnable);
        autoHideHandler.postDelayed(autoHideRunnable, 4000);
    }

    private void showBottomControlCard() {
        if (!isBottomCardVisible && binding != null) {
            isBottomCardVisible = true;
            binding.bottomControlCard.animate()
                    .translationY(0f)
                    .alpha(1.0f)
                    .setDuration(250)
                    .start();
        }
    }

    private void hideBottomControlCard() {
        if (isBottomCardVisible && binding != null) {
            isBottomCardVisible = false;
            float targetY = binding.bottomControlCard.getHeight() + 80f;
            binding.bottomControlCard.animate()
                    .translationY(targetY)
                    .alpha(0.0f)
                    .setDuration(350)
                    .start();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enableImmersiveFullScreen();
        }
    }

    // StreamManager Callbacks
    @Override
    public void onConnectionStarted(String url) {
        binding.tvStatusBadge.setText(isRetrying ? "RETRYING (" + retryCount + "/" + MAX_RETRY_ATTEMPTS + ")" : getString(R.string.status_connecting));
        binding.tvStatusBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF9500")));
        binding.btnGoLive.setEnabled(true);
    }

    @Override
    public void onConnectionSuccess() {
        isStreaming = true;
        isRetrying = false;
        retryCount = 0;
        retryHandler.removeCallbacks(retryRunnable);

        // Auto-lock screen orientation when live streaming starts
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        binding.btnLockOrientation.setImageResource(R.drawable.ic_screen_lock);

        streamStartTime = SystemClock.elapsedRealtime();
        timerHandler.post(timerRunnable);

        binding.tvStatusBadge.setText(R.string.status_live);
        binding.tvStatusBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF3B30")));

        binding.btnGoLive.setText(R.string.stop_stream);
        binding.btnGoLive.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#555555")));
        binding.btnGoLive.setEnabled(true);

        if (profileManager != null && profileManager.isRecordLocal()) {
            if (profileManager.isRecordWithoutOverlays()) {
                Toast.makeText(this, "Broadcasting Live & Recording Clean Video (No Overlays)!", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Broadcasting Live & Recording locally to Movies/GreyStream!", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "Broadcasting Live to Server!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onConnectionFailed(String reason) {
        isStreaming = false;
        timerHandler.removeCallbacks(timerRunnable);

        if (streamManager != null) {
            streamManager.stopStream();
        }

        if (retryCount < MAX_RETRY_ATTEMPTS) {
            retryCount++;
            isRetrying = true;

            binding.tvStatusBadge.setText("RETRYING (" + retryCount + "/" + MAX_RETRY_ATTEMPTS + ")");
            binding.tvStatusBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF9500")));

            binding.btnGoLive.setText("CANCEL RETRY (" + retryCount + "/" + MAX_RETRY_ATTEMPTS + ")");
            binding.btnGoLive.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF9500")));
            binding.btnGoLive.setEnabled(true);

            Toast.makeText(this, "Connection failed: " + reason + "\nRetrying in 3s... (" + retryCount + "/" + MAX_RETRY_ATTEMPTS + ")", Toast.LENGTH_SHORT).show();

            retryHandler.postDelayed(retryRunnable, 3000);
        } else {
            finishRecordingAndSave();
            cancelAutoRetry();
            resetGoLiveButtonState();
            binding.tvStatusBadge.setText(R.string.status_offline);
            binding.tvStatusBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#8E8E93")));
            Toast.makeText(this, "Connection Failed: " + reason, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onDisconnected() {
        if (isRetrying) {
            // Ignore disconnect callback emitted by Pedro library during auto-retry loop
            return;
        }

        boolean wasStreaming = isStreaming;
        isStreaming = false;
        timerHandler.removeCallbacks(timerRunnable);

        if (streamManager != null) {
            streamManager.stopStream();
        }

        if (wasStreaming) {
            finishRecordingAndSave();
            cancelAutoRetry();
            resetGoLiveButtonState();

            binding.tvStatusBadge.setText(R.string.status_offline);
            binding.tvStatusBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#8E8E93")));
            binding.tvStreamTimer.setText("00:00:00");
            binding.tvBitrate.setText("0 Kbps");

            Toast.makeText(this, "Stream Disconnected.", Toast.LENGTH_SHORT).show();
        } else {
            if (!isRetrying) {
                resetGoLiveButtonState();
                binding.tvStatusBadge.setText(R.string.status_offline);
                binding.tvStatusBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#8E8E93")));
            }
        }
    }

    private void cancelAutoRetry() {
        isRetrying = false;
        retryCount = 0;
        retryHandler.removeCallbacks(retryRunnable);
    }

    private void resetGoLiveButtonState() {
        if (binding == null) return;
        binding.btnGoLive.setText(R.string.go_live);
        binding.btnGoLive.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF3B30")));
        binding.btnGoLive.setEnabled(true);

        if (!isOrientationLocked) {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
            binding.btnLockOrientation.setImageResource(R.drawable.ic_screen_rotation);
        }
    }

    @Override
    public void onBitrateUpdate(long bitrate) {
        long kbps = bitrate / 1024;
        binding.tvBitrate.setText(kbps + " Kbps");
    }

    @Override
    public void onFpsUpdate(int fps) {
        if (binding != null && binding.tvFps != null) {
            binding.tvFps.setText(fps + " FPS");
        }
    }

    @Override
    public void onAudioLevelUpdate(int levelL, int levelR) {
        if (binding != null) {
            if (binding.audioLevelBarL != null) binding.audioLevelBarL.setProgress(levelL);
            if (binding.audioLevelBarR != null) binding.audioLevelBarR.setProgress(levelR);
        }
        if (activeSettingsSheet != null && activeSettingsSheet.isResumed()) {
            activeSettingsSheet.updateAudioLevels(levelL, levelR);
        }
    }

    // Touch Selection Listener
    @Override
    public void onOverlaySelected(OverlayItem item) {
        resetAutoHideTimer();
        if (item != null) {
            Toast.makeText(this, "Selected: " + item.getType().name() + " (Drag/Pinch to modify)", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isUiVisible = true;

    private void toggleAllUiVisibility() {
        if (binding == null) return;
        isUiVisible = !isUiVisible;

        float targetAlpha = isUiVisible ? 1.0f : 0.0f;

        if (isUiVisible) {
            binding.topBar.setVisibility(View.VISIBLE);
            binding.sideControls.setVisibility(View.VISIBLE);
            binding.bottomControlCard.setVisibility(View.VISIBLE);
        }

        binding.topBar.animate().alpha(targetAlpha).setDuration(250).withEndAction(() -> {
            if (!isUiVisible && binding != null) binding.topBar.setVisibility(View.GONE);
        }).start();

        binding.sideControls.animate().alpha(targetAlpha).setDuration(250).withEndAction(() -> {
            if (!isUiVisible && binding != null) binding.sideControls.setVisibility(View.GONE);
        }).start();

        binding.bottomControlCard.animate().alpha(targetAlpha).setDuration(250).withEndAction(() -> {
            if (!isUiVisible && binding != null) binding.bottomControlCard.setVisibility(View.GONE);
        }).start();

        if (isUiVisible) {
            resetAutoHideTimer();
        } else {
            autoHideHandler.removeCallbacks(autoHideRunnable);
        }
    }

    @Override
    public void onOverlayMoved(OverlayItem item) {
        if (isUiVisible) {
            resetAutoHideTimer();
        }
        io.greycode.streamer.utils.OverlayStorageManager.saveOverlays(this, binding.overlayCanvasView.getOverlays());
    }

    @Override
    public void onScreenTouched() {
        toggleAllUiVisibility();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        boolean isPortrait = newConfig.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT;
        int displayRotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (displayRotation) {
            case android.view.Surface.ROTATION_0: degrees = 0; break;
            case android.view.Surface.ROTATION_90: degrees = 90; break;
            case android.view.Surface.ROTATION_180: degrees = 180; break;
            case android.view.Surface.ROTATION_270: degrees = 270; break;
        }

        updatePreviewAspectRatio(isPortrait);
        if (streamManager != null) {
            streamManager.setOrientation(degrees, isPortrait);
        }

        if (binding != null && binding.overlayCanvasView != null) {
            binding.overlayCanvasView.invalidate();
        }
        applyAspectRatioMode();
    }

    // SurfaceHolder Callbacks
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        applyAspectRatioMode();
        if (streamManager != null && PermissionUtils.hasAllPermissions(this)) {
            int resW = profileManager.getResolutionWidth();
            int resH = profileManager.getResolutionHeight();
            streamManager.startPreview(resW, resH);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (streamManager != null && PermissionUtils.hasAllPermissions(this)) {
            int resW = profileManager.getResolutionWidth();
            int resH = profileManager.getResolutionHeight();
            streamManager.startPreview(resW, resH);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (streamManager != null) {
            if (isStreaming) {
                stopStreamProcess();
            }
            streamManager.stopPreview();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        enableImmersiveFullScreen();
        applyAspectRatioMode();
        resetAutoHideTimer();
        if (streamManager != null && PermissionUtils.hasAllPermissions(this)) {
            int resW = profileManager.getResolutionWidth();
            int resH = profileManager.getResolutionHeight();
            streamManager.startPreview(resW, resH);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        autoHideHandler.removeCallbacks(autoHideRunnable);
        io.greycode.streamer.utils.OverlayStorageManager.saveOverlays(this, binding.overlayCanvasView.getOverlays());
        if (streamManager != null) {
            if (isStreaming) {
                stopStreamProcess();
            }
            streamManager.stopPreview();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        autoHideHandler.removeCallbacks(autoHideRunnable);
        if (streamManager != null) {
            streamManager.stopPreview();
        }
    }
}
