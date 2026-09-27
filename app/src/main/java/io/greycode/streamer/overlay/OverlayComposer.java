package io.greycode.streamer.overlay;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.os.Handler;
import android.os.HandlerThread;

import com.pedro.encoder.input.gl.render.filters.object.ImageObjectFilterRender;

import java.util.List;

public class OverlayComposer {

    private final List<OverlayItem> overlays;
    private ImageObjectFilterRender filterRender;

    // Double buffering to prevent GL thread read vs render thread write race conditions
    private Bitmap frontBitmap;
    private Bitmap backBitmap;
    private Canvas backCanvas;

    private final Paint drawPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Object bitmapLock = new Object();

    private int streamWidth = 1280;
    private int streamHeight = 720;
    private boolean isRendering = false;

    private HandlerThread renderThread;
    private Handler renderHandler;

    public OverlayComposer(List<OverlayItem> overlays) {
        this.overlays = overlays;
    }

    public void setStreamResolution(int width, int height) {
        this.streamWidth = width;
        this.streamHeight = height;
        recreateBitmaps();
    }

    public void setFilterRender(ImageObjectFilterRender filterRender) {
        this.filterRender = filterRender;
        if (this.filterRender != null && frontBitmap != null && !frontBitmap.isRecycled()) {
            this.filterRender.setImage(frontBitmap);
            this.filterRender.setScale(100f, 100f);
            this.filterRender.setPosition(0f, 0f);
        }
    }

    public void start() {
        if (isRendering) return;
        isRendering = true;

        recreateBitmaps();

        renderThread = new HandlerThread("OverlayComposerThread");
        renderThread.start();
        renderHandler = new Handler(renderThread.getLooper());
        renderHandler.post(renderTask);
    }

    public void stop() {
        isRendering = false;
        if (renderHandler != null) {
            renderHandler.removeCallbacks(renderTask);
        }
        if (renderThread != null) {
            renderThread.quitSafely();
            renderThread = null;
        }
    }

    private void recreateBitmaps() {
        if (streamWidth <= 0 || streamHeight <= 0) return;
        synchronized (bitmapLock) {
            if (frontBitmap != null && !frontBitmap.isRecycled()) {
                frontBitmap.recycle();
            }
            if (backBitmap != null && !backBitmap.isRecycled()) {
                backBitmap.recycle();
            }
            frontBitmap = Bitmap.createBitmap(streamWidth, streamHeight, Bitmap.Config.ARGB_8888);
            backBitmap = Bitmap.createBitmap(streamWidth, streamHeight, Bitmap.Config.ARGB_8888);
            backCanvas = new Canvas(backBitmap);

            if (filterRender != null) {
                filterRender.setImage(frontBitmap);
                filterRender.setScale(100f, 100f);
                filterRender.setPosition(0f, 0f);
            }
        }
    }

    private final Runnable renderTask = new Runnable() {
        @Override
        public void run() {
            if (!isRendering) return;

            renderFrame();

            if (isRendering && renderHandler != null) {
                renderHandler.postDelayed(this, 33); // Sync ~30 FPS frame push
            }
        }
    };

    public interface FpsListener {
        void onFpsCalculated(int fps);
    }

    private FpsListener fpsListener;
    private int frameCounter = 0;
    private long lastFpsCalcTime = System.currentTimeMillis();

    public void setFpsListener(FpsListener listener) {
        this.fpsListener = listener;
    }

    public Bitmap renderFrame() {
        synchronized (bitmapLock) {
            if (backCanvas == null || backBitmap == null || backBitmap.isRecycled() || frontBitmap == null || frontBitmap.isRecycled()) {
                return null;
            }

            // 1. Render new frame offscreen cleanly to backBitmap
            backCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

            OverlayItem[] activeOverlays = overlays.toArray(new OverlayItem[0]);
            java.util.Arrays.sort(activeOverlays, (o1, o2) -> Integer.compare(o1.getZIndex(), o2.getZIndex()));

            for (OverlayItem item : activeOverlays) {
                if (item != null && item.isVisible()) {
                    item.draw(backCanvas, streamWidth, streamHeight, drawPaint);
                }
            }

            // 2. Atomic Swap: backBitmap becomes frontBitmap for GL thread consumption
            Bitmap temp = frontBitmap;
            frontBitmap = backBitmap;
            backBitmap = temp;
            backCanvas.setBitmap(backBitmap);

            // 3. Push complete, uncorrupted frontBitmap to RootEncoder OpenGL filter
            if (filterRender != null && frontBitmap != null && !frontBitmap.isRecycled()) {
                filterRender.setImage(frontBitmap);
                filterRender.setScale(100f, 100f);
                filterRender.setPosition(0f, 0f);
            }

            frameCounter++;
            long now = System.currentTimeMillis();
            if (now - lastFpsCalcTime >= 1000) {
                int calculatedFps = frameCounter;
                frameCounter = 0;
                lastFpsCalcTime = now;
                if (fpsListener != null) {
                    fpsListener.onFpsCalculated(calculatedFps);
                }
            }

            return frontBitmap;
        }
    }
}
