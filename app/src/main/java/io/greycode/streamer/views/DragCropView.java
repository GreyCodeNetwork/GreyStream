package io.greycode.streamer.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import io.greycode.streamer.utils.ImageProcessingUtils;

public class DragCropView extends View {

    private Bitmap sourceBitmap;
    private final RectF imageBounds = new RectF();
    private final RectF cropRect = new RectF();

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float selectedAspectRatio = 0f; // 0f = free ratio

    // Touch handle modes
    private static final int TOUCH_NONE = 0;
    private static final int TOUCH_TOP_LEFT = 1;
    private static final int TOUCH_TOP_RIGHT = 2;
    private static final int TOUCH_BOTTOM_LEFT = 3;
    private static final int TOUCH_BOTTOM_RIGHT = 4;
    private static final int TOUCH_LEFT = 5;
    private static final int TOUCH_TOP = 6;
    private static final int TOUCH_RIGHT = 7;
    private static final int TOUCH_BOTTOM = 8;
    private static final int TOUCH_CENTER = 9;

    private int activeTouchMode = TOUCH_NONE;
    private float lastX, lastY;
    private static final float HANDLE_RADIUS = 28f;
    private static final float TOUCH_TOLERANCE = 48f;

    public DragCropView(Context context) {
        super(context);
        init();
    }

    public DragCropView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DragCropView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        overlayPaint.setColor(Color.parseColor("#99000000"));
        overlayPaint.setStyle(Paint.Style.FILL);

        borderPaint.setColor(Color.parseColor("#00E5FF"));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(4f);

        handlePaint.setColor(Color.parseColor("#00E5FF"));
        handlePaint.setStyle(Paint.Style.FILL);
    }

    public void setBitmap(Bitmap bitmap) {
        this.sourceBitmap = bitmap;
        post(this::resetCropRect);
    }

    public void setAspectRatio(float ratio) {
        this.selectedAspectRatio = ratio;
        resetCropRect();
    }

    public void resetCropRect() {
        if (sourceBitmap == null || getWidth() == 0 || getHeight() == 0) return;

        int viewW = getWidth();
        int viewH = getHeight();
        int bmpW = sourceBitmap.getWidth();
        int bmpH = sourceBitmap.getHeight();

        float scale = Math.min((float) viewW / bmpW, (float) viewH / bmpH) * 0.9f;
        float drawnW = bmpW * scale;
        float drawnH = bmpH * scale;

        float left = (viewW - drawnW) / 2f;
        float top = (viewH - drawnH) / 2f;
        imageBounds.set(left, top, left + drawnW, top + drawnH);

        if (selectedAspectRatio > 0f) {
            float cropW = drawnW;
            float cropH = drawnW / selectedAspectRatio;
            if (cropH > drawnH) {
                cropH = drawnH;
                cropW = drawnH * selectedAspectRatio;
            }
            float cLeft = imageBounds.centerX() - cropW / 2f;
            float cTop = imageBounds.centerY() - cropH / 2f;
            cropRect.set(cLeft, cTop, cLeft + cropW, cTop + cropH);
        } else {
            cropRect.set(imageBounds);
        }
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        resetCropRect();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (sourceBitmap == null || sourceBitmap.isRecycled()) return;

        // 1. Draw source bitmap scaled inside imageBounds
        canvas.drawBitmap(sourceBitmap, null, imageBounds, bitmapPaint);

        // 2. Draw dark overlay outside cropRect
        // Top rectangle
        canvas.drawRect(imageBounds.left, imageBounds.top, imageBounds.right, cropRect.top, overlayPaint);
        // Bottom rectangle
        canvas.drawRect(imageBounds.left, cropRect.bottom, imageBounds.right, imageBounds.bottom, overlayPaint);
        // Left rectangle
        canvas.drawRect(imageBounds.left, cropRect.top, cropRect.left, cropRect.bottom, overlayPaint);
        // Right rectangle
        canvas.drawRect(cropRect.right, cropRect.top, imageBounds.right, cropRect.bottom, overlayPaint);

        // 3. Draw crop border frame
        canvas.drawRect(cropRect, borderPaint);

        // 4. Draw grid lines inside cropRect
        borderPaint.setStrokeWidth(1.5f);
        borderPaint.setAlpha(120);
        float wThird = cropRect.width() / 3f;
        float hThird = cropRect.height() / 3f;
        canvas.drawLine(cropRect.left + wThird, cropRect.top, cropRect.left + wThird, cropRect.bottom, borderPaint);
        canvas.drawLine(cropRect.left + 2 * wThird, cropRect.top, cropRect.left + 2 * wThird, cropRect.bottom, borderPaint);
        canvas.drawLine(cropRect.left, cropRect.top + hThird, cropRect.right, cropRect.top + hThird, borderPaint);
        canvas.drawLine(cropRect.left, cropRect.top + 2 * hThird, cropRect.right, cropRect.top + 2 * hThird, borderPaint);
        borderPaint.setStrokeWidth(4f);
        borderPaint.setAlpha(255);

        // 5. Draw Corner & Edge Handles
        // Corners
        canvas.drawCircle(cropRect.left, cropRect.top, HANDLE_RADIUS, handlePaint);
        canvas.drawCircle(cropRect.right, cropRect.top, HANDLE_RADIUS, handlePaint);
        canvas.drawCircle(cropRect.left, cropRect.bottom, HANDLE_RADIUS, handlePaint);
        canvas.drawCircle(cropRect.right, cropRect.bottom, HANDLE_RADIUS, handlePaint);

        // Edges
        canvas.drawCircle(cropRect.centerX(), cropRect.top, HANDLE_RADIUS * 0.8f, handlePaint);
        canvas.drawCircle(cropRect.centerX(), cropRect.bottom, HANDLE_RADIUS * 0.8f, handlePaint);
        canvas.drawCircle(cropRect.left, cropRect.centerY(), HANDLE_RADIUS * 0.8f, handlePaint);
        canvas.drawCircle(cropRect.right, cropRect.centerY(), HANDLE_RADIUS * 0.8f, handlePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                activeTouchMode = getTouchHandle(x, y);
                lastX = x;
                lastY = y;
                return activeTouchMode != TOUCH_NONE;

            case MotionEvent.ACTION_MOVE:
                if (activeTouchMode != TOUCH_NONE) {
                    float dx = x - lastX;
                    float dy = y - lastY;
                    moveCropRect(activeTouchMode, dx, dy);
                    lastX = x;
                    lastY = y;
                    invalidate();
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                activeTouchMode = TOUCH_NONE;
                break;
        }
        return super.onTouchEvent(event);
    }

    private int getTouchHandle(float x, float y) {
        if (dist(x, y, cropRect.left, cropRect.top) < TOUCH_TOLERANCE) return TOUCH_TOP_LEFT;
        if (dist(x, y, cropRect.right, cropRect.top) < TOUCH_TOLERANCE) return TOUCH_TOP_RIGHT;
        if (dist(x, y, cropRect.left, cropRect.bottom) < TOUCH_TOLERANCE) return TOUCH_BOTTOM_LEFT;
        if (dist(x, y, cropRect.right, cropRect.bottom) < TOUCH_TOLERANCE) return TOUCH_BOTTOM_RIGHT;

        if (dist(x, y, cropRect.centerX(), cropRect.top) < TOUCH_TOLERANCE) return TOUCH_TOP;
        if (dist(x, y, cropRect.centerX(), cropRect.bottom) < TOUCH_TOLERANCE) return TOUCH_BOTTOM;
        if (dist(x, y, cropRect.left, cropRect.centerY()) < TOUCH_TOLERANCE) return TOUCH_LEFT;
        if (dist(x, y, cropRect.right, cropRect.centerY()) < TOUCH_TOLERANCE) return TOUCH_RIGHT;

        if (cropRect.contains(x, y)) return TOUCH_CENTER;
        return TOUCH_NONE;
    }

    private float dist(float x1, float y1, float x2, float y2) {
        return (float) Math.hypot(x1 - x2, y1 - y2);
    }

    private void moveCropRect(int handle, float dx, float dy) {
        float minSize = 60f;

        switch (handle) {
            case TOUCH_CENTER:
                float w = cropRect.width();
                float h = cropRect.height();
                cropRect.left = Math.max(imageBounds.left, Math.min(imageBounds.right - w, cropRect.left + dx));
                cropRect.top = Math.max(imageBounds.top, Math.min(imageBounds.bottom - h, cropRect.top + dy));
                cropRect.right = cropRect.left + w;
                cropRect.bottom = cropRect.top + h;
                break;

            case TOUCH_TOP_LEFT:
                cropRect.left = Math.max(imageBounds.left, Math.min(cropRect.right - minSize, cropRect.left + dx));
                cropRect.top = Math.max(imageBounds.top, Math.min(cropRect.bottom - minSize, cropRect.top + dy));
                break;

            case TOUCH_TOP_RIGHT:
                cropRect.right = Math.min(imageBounds.right, Math.max(cropRect.left + minSize, cropRect.right + dx));
                cropRect.top = Math.max(imageBounds.top, Math.min(cropRect.bottom - minSize, cropRect.top + dy));
                break;

            case TOUCH_BOTTOM_LEFT:
                cropRect.left = Math.max(imageBounds.left, Math.min(cropRect.right - minSize, cropRect.left + dx));
                cropRect.bottom = Math.min(imageBounds.bottom, Math.max(cropRect.top + minSize, cropRect.bottom + dy));
                break;

            case TOUCH_BOTTOM_RIGHT:
                cropRect.right = Math.min(imageBounds.right, Math.max(cropRect.left + minSize, cropRect.right + dx));
                cropRect.bottom = Math.min(imageBounds.bottom, Math.max(cropRect.top + minSize, cropRect.bottom + dy));
                break;

            case TOUCH_LEFT:
                cropRect.left = Math.max(imageBounds.left, Math.min(cropRect.right - minSize, cropRect.left + dx));
                break;

            case TOUCH_RIGHT:
                cropRect.right = Math.min(imageBounds.right, Math.max(cropRect.left + minSize, cropRect.right + dx));
                break;

            case TOUCH_TOP:
                cropRect.top = Math.max(imageBounds.top, Math.min(cropRect.bottom - minSize, cropRect.top + dy));
                break;

            case TOUCH_BOTTOM:
                cropRect.bottom = Math.min(imageBounds.bottom, Math.max(cropRect.top + minSize, cropRect.bottom + dy));
                break;
        }

        // Enforce aspect ratio if set
        if (selectedAspectRatio > 0f && handle != TOUCH_CENTER) {
            float currentW = cropRect.width();
            float currentH = currentW / selectedAspectRatio;
            if (cropRect.top + currentH <= imageBounds.bottom) {
                cropRect.bottom = cropRect.top + currentH;
            } else {
                currentH = cropRect.height();
                currentW = currentH * selectedAspectRatio;
                cropRect.right = Math.min(imageBounds.right, cropRect.left + currentW);
            }
        }
    }

    public Bitmap getCroppedBitmap() {
        if (sourceBitmap == null || imageBounds.width() == 0 || imageBounds.height() == 0) return sourceBitmap;

        float scaleX = sourceBitmap.getWidth() / imageBounds.width();
        float scaleY = sourceBitmap.getHeight() / imageBounds.height();

        int cropX = (int) Math.max(0, (cropRect.left - imageBounds.left) * scaleX);
        int cropY = (int) Math.max(0, (cropRect.top - imageBounds.top) * scaleY);
        int cropWidth = (int) Math.min(sourceBitmap.getWidth() - cropX, cropRect.width() * scaleX);
        int cropHeight = (int) Math.min(sourceBitmap.getHeight() - cropY, cropRect.height() * scaleY);

        cropWidth = Math.max(10, cropWidth);
        cropHeight = Math.max(10, cropHeight);

        return Bitmap.createBitmap(sourceBitmap, cropX, cropY, cropWidth, cropHeight);
    }
}
