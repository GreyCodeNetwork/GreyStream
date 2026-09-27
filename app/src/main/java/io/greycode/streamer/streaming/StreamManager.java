package io.greycode.streamer.streaming;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.pedro.common.ConnectChecker;
import com.pedro.encoder.input.gl.render.filters.object.ImageObjectFilterRender;
import com.pedro.encoder.input.gl.render.filters.AndroidViewFilterRender;
import com.pedro.encoder.input.video.CameraHelper;

import com.pedro.library.view.OpenGlView;
import com.pedro.library.view.GlStreamInterface;
import com.pedro.library.rtmp.RtmpCamera2;

import java.util.List;
import android.util.Size;
import android.view.View;

import io.greycode.streamer.overlay.OverlayComposer;

public class StreamManager implements ConnectChecker {

    public interface StreamListener {
        void onConnectionStarted(String url);
        void onConnectionSuccess();
        void onConnectionFailed(String reason);
        void onDisconnected();
        void onBitrateUpdate(long bitrate);
        void onFpsUpdate(int fps);
        void onAudioLevelUpdate(int levelL, int levelR);
    }

    private final Context context;
    private final OpenGlView openGlView;
    private final StreamListener listener;
    private RtmpCamera2 rtmpCamera2;
    private io.greycode.streamer.utils.ProfileManager profileManager;

    private ImageObjectFilterRender imageObjectFilterRender;
    private AndroidViewFilterRender androidViewFilterRender;
    private View boundOverlayView;
    private OverlayComposer overlayComposer;

    private boolean isStreaming = false;
    private boolean isRecording = false;

    // Stream settings
    private int width = 1280;
    private int height = 720;
    private int fps = 30;
    private int bitrate = 2500 * 1024; // 2.5 Mbps
    private int iFrameInterval = 2; // Keyframe interval in seconds
    private String audioSourceMode = "internal";
    private int sampleRate = 44100;
    private boolean isStereo = true;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public StreamManager(Context context, OpenGlView openGlView, StreamListener listener) {
        this.context = context.getApplicationContext();
        this.openGlView = openGlView;
        this.listener = listener;
        this.profileManager = new io.greycode.streamer.utils.ProfileManager(this.context);

        initCamera();
    }

    private void initCamera() {
        if (openGlView != null) {
            openGlView.setAspectRatioMode(com.pedro.encoder.utils.gl.AspectRatioMode.NONE);
        }
        rtmpCamera2 = new RtmpCamera2(openGlView, this);

        // Setup OpenGL Real-time Overlay Filter
        imageObjectFilterRender = new io.greycode.streamer.overlay.CustomImageObjectFilterRender();
        rtmpCamera2.getGlInterface().setFilter(imageObjectFilterRender);

        // Apply real-time microphone gain multiplier to RTMP stream audio encoder
        rtmpCamera2.setCustomAudioEffect(new com.pedro.encoder.input.audio.CustomAudioEffect() {
            @Override
            public byte[] process(byte[] pcmBuffer) {
                float gain = profileManager != null ? profileManager.getAudioGain() : 1.0f;
                if (gain == 1.0f || pcmBuffer == null || pcmBuffer.length == 0) {
                    return pcmBuffer;
                }
                int len = pcmBuffer.length / 2;
                for (int i = 0; i < len; i++) {
                    int sample = (short) ((pcmBuffer[i * 2 + 1] << 8) | (pcmBuffer[i * 2] & 0xFF));
                    int boosted = Math.round(sample * gain);
                    short clamped = (short) Math.max(-32768, Math.min(32767, boosted));
                    pcmBuffer[i * 2] = (byte) (clamped & 0xFF);
                    pcmBuffer[i * 2 + 1] = (byte) ((clamped >> 8) & 0xFF);
                }
                return pcmBuffer;
            }
        });

        startAudioMonitoring();
    }

    private android.media.AudioRecord audioRecord;
    private Thread audioThread;
    private boolean isAudioMonitoring = false;

    public void startAudioMonitoring() {
        if (isAudioMonitoring) return;
        isAudioMonitoring = true;
        audioThread = new Thread(() -> {
            try {
                int sampleRate = 44100;
                int channelConfig = android.media.AudioFormat.CHANNEL_IN_STEREO;
                int audioFormat = android.media.AudioFormat.ENCODING_PCM_16BIT;
                int minBufSize = android.media.AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
                if (minBufSize <= 0) minBufSize = 4096;

                audioRecord = new android.media.AudioRecord(
                        android.media.MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        channelConfig,
                        audioFormat,
                        minBufSize
                );

                if (audioRecord.getState() != android.media.AudioRecord.STATE_INITIALIZED) {
                    // Fallback to MONO if STEREO recording initialization fails on specific device
                    channelConfig = android.media.AudioFormat.CHANNEL_IN_MONO;
                    minBufSize = android.media.AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
                    audioRecord = new android.media.AudioRecord(
                            android.media.MediaRecorder.AudioSource.MIC,
                            sampleRate,
                            channelConfig,
                            audioFormat,
                            Math.max(2048, minBufSize)
                    );
                }

                if (audioRecord.getState() == android.media.AudioRecord.STATE_INITIALIZED) {
                    audioRecord.startRecording();
                    short[] buffer = new short[1024];
                    while (isAudioMonitoring && audioRecord != null && audioRecord.getRecordingState() == android.media.AudioRecord.RECORDSTATE_RECORDING) {
                        if (rtmpCamera2 != null && rtmpCamera2.isAudioMuted()) {
                            mainHandler.post(() -> {
                                if (listener != null) listener.onAudioLevelUpdate(0, 0);
                            });
                            Thread.sleep(100);
                            continue;
                        }
                        float audioGain = profileManager != null ? profileManager.getAudioGain() : 1.0f;
                        int read = audioRecord.read(buffer, 0, buffer.length);
                        if (read > 0) {
                            long sumL = 0;
                            long sumR = 0;
                            int countL = 0;
                            int countR = 0;

                            for (int i = 0; i < read; i++) {
                                int boosted = (int) (buffer[i] * audioGain);
                                short clamped = (short) Math.max(-32768, Math.min(32767, boosted));
                                if (i % 2 == 0) {
                                    sumL += (long) clamped * clamped;
                                    countL++;
                                } else {
                                    sumR += (long) clamped * clamped;
                                    countR++;
                                }
                            }

                            double rmsL = Math.sqrt((double) sumL / Math.max(1, countL));
                            double rmsR = Math.sqrt((double) sumR / Math.max(1, countR > 0 ? countR : countL));

                            int levelL = (int) Math.min(100, (rmsL / 28000.0) * 100.0);
                            int levelR = (int) Math.min(100, (rmsR / 28000.0) * 100.0);

                            mainHandler.post(() -> {
                                if (listener != null) listener.onAudioLevelUpdate(levelL, levelR);
                            });
                        }
                        Thread.sleep(40);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                stopAudioMonitoringInternal();
            }
        }, "AudioLevelMonitorThread");
        audioThread.start();
    }

    public void stopAudioMonitoring() {
        isAudioMonitoring = false;
        stopAudioMonitoringInternal();
    }

    private void stopAudioMonitoringInternal() {
        try {
            if (audioRecord != null) {
                if (audioRecord.getState() == android.media.AudioRecord.STATE_INITIALIZED &&
                    audioRecord.getRecordingState() == android.media.AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                }
                audioRecord.release();
                audioRecord = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Size calculateAutoResolution(boolean isPortrait) {
        android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int screenW = metrics.widthPixels;
        int screenH = metrics.heightPixels;
        if (screenW <= 0 || screenH <= 0) {
            screenW = 1080;
            screenH = 1920;
        }
        double aspect = (double) Math.max(screenW, screenH) / Math.min(screenW, screenH);

        int baseShort = 720;
        int calcLong = (int) Math.round(baseShort * aspect);
        calcLong = ((calcLong + 15) / 16) * 16;

        if (isPortrait) {
            return new Size(baseShort, calcLong);
        } else {
            return new Size(calcLong, baseShort);
        }
    }

    public void bindOverlayView(View overlayView) {
        this.boundOverlayView = overlayView;
        if (rtmpCamera2 != null && rtmpCamera2.getGlInterface() != null && imageObjectFilterRender != null) {
            rtmpCamera2.getGlInterface().setFilter(imageObjectFilterRender);
        }
    }

    public void bindOverlayComposer(OverlayComposer composer) {
        this.overlayComposer = composer;
        if (this.overlayComposer != null && imageObjectFilterRender != null) {
            this.overlayComposer.setFilterRender(imageObjectFilterRender);
            this.overlayComposer.setStreamResolution(width, height);
            this.overlayComposer.setFpsListener(fps -> mainHandler.post(() -> {
                if (listener != null) listener.onFpsUpdate(fps);
            }));
            this.overlayComposer.start();
        }
    }

    public Size getOptimalResolution(int targetW, int targetH) {
        if (rtmpCamera2 == null) return null;
        try {
            List<Size> sizes = rtmpCamera2.getResolutionsBack();
            if (sizes == null || sizes.isEmpty()) return null;

            Size best = sizes.get(0);
            int targetArea = targetW * targetH;
            int minDiff = Integer.MAX_VALUE;

            for (Size s : sizes) {
                int area = s.getWidth() * s.getHeight();
                int diff = Math.abs(area - targetArea);
                if (diff < minDiff) {
                    minDiff = diff;
                    best = s;
                }
            }
            return best;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void startPreview() {
        startPreview(width, height);
    }

    public void startPreview(int targetWidth, int targetHeight) {
        if (rtmpCamera2 == null) return;
        try {
            boolean isPortrait = CameraHelper.isPortrait(context);
            if (targetWidth <= 0 || targetHeight <= 0) {
                Size autoSize = calculateAutoResolution(isPortrait);
                targetWidth = autoSize.getWidth();
                targetHeight = autoSize.getHeight();
            }

            int reqW = Math.max(targetWidth, targetHeight);
            int reqH = Math.min(targetWidth, targetHeight);

            Size best = getOptimalResolution(reqW, reqH);
            int rawW = (best != null) ? best.getWidth() : reqW;
            int rawH = (best != null) ? best.getHeight() : reqH;

            int encW = isPortrait ? Math.min(rawW, rawH) : Math.max(rawW, rawH);
            int encH = isPortrait ? Math.max(rawW, rawH) : Math.min(rawW, rawH);

            if (overlayComposer != null) {
                overlayComposer.setStreamResolution(encW, encH);
                overlayComposer.start();
            }

            if (rtmpCamera2.getGlInterface() instanceof GlStreamInterface) {
                GlStreamInterface glStream = (GlStreamInterface) rtmpCamera2.getGlInterface();
                glStream.setAutoHandleOrientation(false);
                glStream.setIsPortrait(isPortrait);
            }

            if (openGlView != null) {
                openGlView.setAspectRatioMode(com.pedro.encoder.utils.gl.AspectRatioMode.NONE);
            }
            if (rtmpCamera2.isOnPreview()) {
                rtmpCamera2.stopPreview();
            }
            rtmpCamera2.getGlInterface().setFilter(imageObjectFilterRender);
            rtmpCamera2.startPreview(rawW, rawH);
        } catch (Exception e) {
            try {
                rtmpCamera2.startPreview();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public void stopPreview() {
        if (rtmpCamera2 != null) {
            try {
                if (rtmpCamera2.isOnPreview()) {
                    rtmpCamera2.stopPreview();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void setOrientation(int degrees, boolean isPortrait) {
        if (rtmpCamera2 == null) return;
        try {
            int reqW = Math.max(width, height);
            int reqH = Math.min(width, height);

            Size best = getOptimalResolution(reqW, reqH);
            int rawW = (best != null) ? best.getWidth() : reqW;
            int rawH = (best != null) ? best.getHeight() : reqH;

            int encW = isPortrait ? Math.min(rawW, rawH) : Math.max(rawW, rawH);
            int encH = isPortrait ? Math.max(rawW, rawH) : Math.min(rawW, rawH);

            if (rtmpCamera2.getGlInterface() instanceof GlStreamInterface) {
                GlStreamInterface glStream = (GlStreamInterface) rtmpCamera2.getGlInterface();
                glStream.setAutoHandleOrientation(false);
                glStream.setIsPortrait(isPortrait);
            }

            if (!rtmpCamera2.isStreaming() && rtmpCamera2.isOnPreview()) {
                rtmpCamera2.stopPreview();
                rtmpCamera2.getGlInterface().setFilter(imageObjectFilterRender);
                rtmpCamera2.startPreview(rawW, rawH);
            }

            if (overlayComposer != null) {
                overlayComposer.setStreamResolution(encW, encH);
                overlayComposer.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void configureAudioSource(String audioSource) {
        this.audioSourceMode = audioSource;
        try {
            android.media.AudioManager audioManager = (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                if ("bluetooth".equalsIgnoreCase(audioSource)) {
                    audioManager.startBluetoothSco();
                    audioManager.setBluetoothScoOn(true);
                } else {
                    if (audioManager.isBluetoothScoOn()) {
                        audioManager.stopBluetoothSco();
                        audioManager.setBluetoothScoOn(false);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int getDeviceRotationDegrees() {
        try {
            android.view.WindowManager wm = (android.view.WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                int rotation = wm.getDefaultDisplay().getRotation();
                switch (rotation) {
                    case android.view.Surface.ROTATION_0: return 0;
                    case android.view.Surface.ROTATION_90: return 90;
                    case android.view.Surface.ROTATION_180: return 180;
                    case android.view.Surface.ROTATION_270: return 270;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    private boolean isAdaptiveBitrate = true;

    public boolean prepareStream(int targetW, int targetH, int fps, int bitrateKbps, int iFrameInterval, String audioSource, boolean isAdaptiveBitrate) {
        this.isAdaptiveBitrate = isAdaptiveBitrate;
        return prepareStream(targetW, targetH, fps, bitrateKbps, iFrameInterval, audioSource);
    }

    public boolean prepareStream(int targetW, int targetH, int fps, int bitrateKbps, int iFrameInterval, String audioSource) {
        boolean isPortrait = CameraHelper.isPortrait(context);
        if (targetW <= 0 || targetH <= 0) {
            Size autoSize = calculateAutoResolution(isPortrait);
            targetW = autoSize.getWidth();
            targetH = autoSize.getHeight();
        }

        this.width = targetW;
        this.height = targetH;
        this.fps = fps;
        this.bitrate = bitrateKbps * 1024;
        this.iFrameInterval = iFrameInterval;

        configureAudioSource(audioSource);
        int rotation = CameraHelper.getCameraOrientation(context);

        int reqW = Math.max(targetW, targetH);
        int reqH = Math.min(targetW, targetH);

        Size best = getOptimalResolution(reqW, reqH);
        int rawW = (best != null) ? best.getWidth() : reqW;
        int rawH = (best != null) ? best.getHeight() : reqH;

        int encW = isPortrait ? Math.min(rawW, rawH) : Math.max(rawW, rawH);
        int encH = isPortrait ? Math.max(rawW, rawH) : Math.min(rawW, rawH);

        if (overlayComposer != null) {
            overlayComposer.setStreamResolution(encW, encH);
            overlayComposer.start();
        }

        if (rtmpCamera2.getGlInterface() instanceof GlStreamInterface) {
            GlStreamInterface glStream = (GlStreamInterface) rtmpCamera2.getGlInterface();
            glStream.setAutoHandleOrientation(false);
            glStream.setIsPortrait(isPortrait);
        }

        rtmpCamera2.getGlInterface().setFilter(imageObjectFilterRender);

        int targetFps = (fps <= 0) ? 30 : fps;
        boolean videoPrepared = rtmpCamera2.prepareVideo(encW, encH, targetFps, bitrate, iFrameInterval, rotation);
        boolean audioPrepared = rtmpCamera2.prepareAudio(128 * 1024, sampleRate, isStereo, false, false);

        if (!rtmpCamera2.isOnPreview()) {
            try {
                rtmpCamera2.startPreview(rawW, rawH);
            } catch (Exception e) {
                rtmpCamera2.startPreview();
            }
        }

        return videoPrepared && audioPrepared;
    }

    public void startStream(String streamUrl) {
        if (rtmpCamera2 == null) return;

        if (rtmpCamera2.isStreaming()) {
            stopStream();
        }

        try {
            rtmpCamera2.startStream(streamUrl);
            if (overlayComposer != null) {
                overlayComposer.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (listener != null) {
                listener.onConnectionFailed("Failed to start stream: " + e.getMessage());
            }
        }
    }

    public void stopStream() {
        if (rtmpCamera2 != null) {
            try {
                rtmpCamera2.stopStream();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (overlayComposer != null) {
            try {
                overlayComposer.stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        isStreaming = false;
    }

    public void switchCamera() {
        if (rtmpCamera2 != null) {
            try {
                rtmpCamera2.switchCamera();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void toggleFlash() {
        if (rtmpCamera2 != null) {
            try {
                if (rtmpCamera2.isLanternEnabled()) {
                    rtmpCamera2.disableLantern();
                } else {
                    rtmpCamera2.enableLantern();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public boolean isFlashEnabled() {
        return rtmpCamera2 != null && rtmpCamera2.isLanternEnabled();
    }

    public void toggleAudioMute() {
        if (rtmpCamera2 != null) {
            if (rtmpCamera2.isAudioMuted()) {
                rtmpCamera2.enableAudio();
            } else {
                rtmpCamera2.disableAudio();
            }
        }
    }

    public boolean isAudioMuted() {
        return rtmpCamera2 != null && rtmpCamera2.isAudioMuted();
    }

    public boolean isStreaming() {
        return rtmpCamera2 != null && rtmpCamera2.isStreaming();
    }

    // ConnectChecker Implementation Callbacks
    @Override
    public void onConnectionStarted(String url) {
        mainHandler.post(() -> {
            if (listener != null) listener.onConnectionStarted(url);
        });
    }

    @Override
    public void onConnectionSuccess() {
        isStreaming = true;
        mainHandler.post(() -> {
            if (listener != null) listener.onConnectionSuccess();
        });
    }

    @Override
    public void onConnectionFailed(String reason) {
        isStreaming = false;
        mainHandler.post(() -> {
            if (listener != null) listener.onConnectionFailed(reason);
        });
    }

    @Override
    public void onDisconnect() {
        isStreaming = false;
        mainHandler.post(() -> {
            if (listener != null) listener.onDisconnected();
        });
    }

    @Override
    public void onAuthError() {
        mainHandler.post(() -> {
            if (listener != null) listener.onConnectionFailed("Authentication failed.");
        });
    }

    @Override
    public void onAuthSuccess() {
        // Auth succeeded
    }

    @Override
    public void onNewBitrate(long bitrate) {
        if (isAdaptiveBitrate && rtmpCamera2 != null && rtmpCamera2.isStreaming() && bitrate > 0) {
            int dynamicBitrate = (int) Math.min(bitrate, this.bitrate);
            dynamicBitrate = Math.max(500 * 1024, dynamicBitrate);
            try {
                rtmpCamera2.setVideoBitrateOnFly(dynamicBitrate);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        mainHandler.post(() -> {
            if (listener != null) listener.onBitrateUpdate(bitrate);
        });
    }
}
