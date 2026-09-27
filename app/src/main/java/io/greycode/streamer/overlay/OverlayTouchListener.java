package io.greycode.streamer.overlay;

import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;

public class OverlayTouchListener implements View.OnTouchListener {

    public interface OnOverlaySelectedListener {
        void onOverlaySelected(OverlayItem item);
        void onOverlayMoved(OverlayItem item);
        void onScreenTouched();
    }

    private List<OverlayItem> overlays;
    private final OnOverlaySelectedListener listener;
    private OverlayItem selectedItem = null;
    private boolean locked = false;

    // Gesture tracking variables
    private float lastTouchX;
    private float lastTouchY;
    private float initialPinchDist = 0f;
    private float initialScale = 1.0f;

    public OverlayTouchListener(List<OverlayItem> overlays, OnOverlaySelectedListener listener) {
        this.overlays = overlays;
        this.listener = listener;
    }

    public void setOverlays(List<OverlayItem> overlays) {
        this.overlays = overlays;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
        if (locked) {
            selectedItem = null;
        }
    }

    public boolean isLocked() {
        return locked;
    }

    public OverlayItem getSelectedItem() {
        return selectedItem;
    }

    public void setSelectedItem(OverlayItem selectedItem) {
        this.selectedItem = selectedItem;
    }

    private boolean isDragging = false;
    private float touchDownX, touchDownY;

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        int width = v.getWidth();
        int height = v.getHeight();
        if (width == 0 || height == 0) return false;

        int action = event.getActionMasked();
        int pointerCount = event.getPointerCount();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (v.getParent() != null) {
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                }
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                touchDownX = event.getX();
                touchDownY = event.getY();
                isDragging = false;

                if (!locked) {
                    selectedItem = findOverlayAt(lastTouchX, lastTouchY, width, height);
                    if (listener != null && selectedItem != null) {
                        listener.onOverlaySelected(selectedItem);
                    }
                }
                v.invalidate();
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                if (pointerCount == 2 && selectedItem != null && !locked) {
                    initialPinchDist = getSpacing(event);
                    initialScale = selectedItem.getScale();
                    isDragging = true;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (v.getParent() != null) {
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                }
                float distMoved = (float) Math.hypot(event.getX() - touchDownX, event.getY() - touchDownY);
                if (distMoved > 6f) {
                    isDragging = true;
                }

                if (selectedItem == null || selectedItem.isLocked() || locked) return true;

                if (pointerCount >= 2) {
                    float newDist = getSpacing(event);
                    if (newDist > 10f && initialPinchDist > 10f) {
                        float scaleFactor = newDist / initialPinchDist;
                        selectedItem.setScale(initialScale * scaleFactor);
                        v.invalidate();
                    }
                } else if (pointerCount == 1) {
                    float dx = event.getX() - lastTouchX;
                    float dy = event.getY() - lastTouchY;

                    float newXPercent = selectedItem.getXPercent() + (dx / width);
                    float newYPercent = selectedItem.getYPercent() + (dy / height);

                    selectedItem.setXPercent(newXPercent);
                    selectedItem.setYPercent(newYPercent);

                    lastTouchX = event.getX();
                    lastTouchY = event.getY();

                    if (listener != null) {
                        listener.onOverlayMoved(selectedItem);
                    }
                    v.invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (v.getParent() != null) {
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                }
                if (!isDragging && listener != null && action == MotionEvent.ACTION_UP) {
                    listener.onScreenTouched();
                }
                v.invalidate();
                return true;
        }

        return false;
    }

    private OverlayItem findOverlayAt(float touchX, float touchY, int width, int height) {
        // Iterate backwards from top z-index to bottom
        for (int i = overlays.size() - 1; i >= 0; i--) {
            OverlayItem item = overlays.get(i);
            if (!item.isVisible() || item.isLocked()) continue;

            RectF rect = item.getBoundingRect(width, height);
            // Add generous touch padding for easy selection
            rect.inset(-40f, -40f);
            if (rect.contains(touchX, touchY)) {
                return item;
            }
        }
        return null;
    }

    private float getSpacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }
}
