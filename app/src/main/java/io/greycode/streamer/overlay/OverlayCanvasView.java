package io.greycode.streamer.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class OverlayCanvasView extends View {

    private final List<OverlayItem> overlays = new CopyOnWriteArrayList<>();
    private final Paint drawPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private OverlayTouchListener touchListener;
    private final Handler animationHandler = new Handler(Looper.getMainLooper());
    private final Runnable renderRunnable = new Runnable() {
        @Override
        public void run() {
            invalidate();
            animationHandler.postDelayed(this, 33); // ~30 FPS UI animation refresh for marquee/clock
        }
    };

    public OverlayCanvasView(Context context) {
        super(context);
        init();
    }

    public OverlayCanvasView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public OverlayCanvasView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        selectionPaint.setColor(Color.parseColor("#00E5FF"));
        selectionPaint.setStyle(Paint.Style.STROKE);
        selectionPaint.setStrokeWidth(3f);
        selectionPaint.setPathEffect(new DashPathEffect(new float[]{12f, 8f}, 0f));

        handlePaint.setColor(Color.parseColor("#00E5FF"));
        handlePaint.setStyle(Paint.Style.FILL);

        animationHandler.post(renderRunnable);
    }

    public void setTouchListener(OverlayTouchListener listener) {
        this.touchListener = listener;
        setOnTouchListener(listener);
    }

    public void addOverlay(OverlayItem item) {
        item.setZIndex(overlays.size());
        overlays.add(item);
        sortOverlays();
        invalidate();
    }

    public void removeOverlay(OverlayItem item) {
        overlays.remove(item);
        if (touchListener != null && touchListener.getSelectedItem() == item) {
            touchListener.setSelectedItem(null);
        }
        sortOverlays();
        invalidate();
    }

    public List<OverlayItem> getOverlays() {
        return overlays;
    }

    public void setOverlays(List<OverlayItem> items) {
        this.overlays.clear();
        if (items != null) {
            this.overlays.addAll(items);
        }
        if (touchListener != null) {
            touchListener.setOverlays(this.overlays);
        }
        sortOverlays();
        invalidate();
    }

    public void sortOverlays() {
        List<OverlayItem> sorted = new ArrayList<>(overlays);
        Collections.sort(sorted, new Comparator<OverlayItem>() {
            @Override
            public int compare(OverlayItem o1, OverlayItem o2) {
                return Integer.compare(o1.getZIndex(), o2.getZIndex());
            }
        });
        overlays.clear();
        overlays.addAll(sorted);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) return;

        // Initialize HTML renderers for active overlays
        for (OverlayItem item : overlays) {
            if (item != null) {
                item.initHtmlRenderer(getContext());
            }
        }

        // Draw selection box & drag handles if unlocked and an item is selected
        if (touchListener != null && !touchListener.isLocked()) {
            OverlayItem selected = touchListener.getSelectedItem();
            if (selected != null && selected.isVisible()) {
                RectF box = selected.getBoundingRect(width, height);

                // Selection bounding border
                canvas.drawRoundRect(box, 8f, 8f, selectionPaint);

                // Corner selection handles
                canvas.drawCircle(box.left, box.top, 12f, handlePaint);
                canvas.drawCircle(box.right, box.top, 12f, handlePaint);
                canvas.drawCircle(box.left, box.bottom, 12f, handlePaint);
                canvas.drawCircle(box.right, box.bottom, 12f, handlePaint);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        animationHandler.removeCallbacks(renderRunnable);
    }
}
