package io.greycode.streamer.utils;

import android.graphics.Bitmap;
import android.graphics.Color;

public class ImageProcessingUtils {

    /**
     * Makes matching background pixels transparent based on target color and tolerance.
     * If targetColor is 0, auto-samples top-left and top-right corner pixel colors.
     */
    public static Bitmap makeBackgroundTransparent(Bitmap source, int targetColor, int tolerance) {
        if (source == null) return null;

        Bitmap copy = source.copy(Bitmap.Config.ARGB_8888, true);
        copy.setHasAlpha(true);
        int width = copy.getWidth();
        int height = copy.getHeight();

        int[] pixels = new int[width * height];
        copy.getPixels(pixels, 0, width, 0, 0, width, height);

        // Auto-sample corner colors if targetColor is 0
        int cornerColor = (targetColor != 0) ? targetColor : pixels[0];
        int targetR = Color.red(cornerColor);
        int targetG = Color.green(cornerColor);
        int targetB = Color.blue(cornerColor);

        int topRight = pixels[width - 1];
        int trR = Color.red(topRight);
        int trG = Color.green(topRight);
        int trB = Color.blue(topRight);

        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int r = Color.red(pixel);
            int g = Color.green(pixel);
            int b = Color.blue(pixel);

            int diff1 = Math.abs(r - targetR) + Math.abs(g - targetG) + Math.abs(b - targetB);
            int diff2 = Math.abs(r - trR) + Math.abs(g - trG) + Math.abs(b - trB);

            if (diff1 <= tolerance || diff2 <= tolerance) {
                pixels[i] = Color.TRANSPARENT;
            }
        }

        copy.setPixels(pixels, 0, width, 0, 0, width, height);
        return copy;
    }

    /**
     * Crops the center square region of a bitmap.
     */
    public static Bitmap cropCenterSquare(Bitmap source) {
        if (source == null) return null;
        int width = source.getWidth();
        int height = source.getHeight();
        int newDim = Math.min(width, height);

        int cropX = (width - newDim) / 2;
        int cropY = (height - newDim) / 2;

        return Bitmap.createBitmap(source, cropX, cropY, newDim, newDim);
    }

    /**
     * Custom crop using percentage boundaries (left, top, right, bottom margins).
     */
    public static Bitmap cropCustom(Bitmap source, float leftPercent, float topPercent, float rightPercent, float bottomPercent) {
        if (source == null) return null;
        int w = source.getWidth();
        int h = source.getHeight();

        int left = (int) (w * Math.max(0f, Math.min(0.45f, leftPercent)));
        int top = (int) (h * Math.max(0f, Math.min(0.45f, topPercent)));
        int right = (int) (w * (1.0f - Math.max(0f, Math.min(0.45f, rightPercent))));
        int bottom = (int) (h * (1.0f - Math.max(0f, Math.min(0.45f, bottomPercent))));

        int cropWidth = Math.max(10, right - left);
        int cropHeight = Math.max(10, bottom - top);

        return Bitmap.createBitmap(source, left, top, cropWidth, cropHeight);
    }

    /**
     * Crops bitmap to fit target aspect ratio (width / height).
     */
    public static Bitmap cropAspectRatio(Bitmap source, float targetRatio) {
        if (source == null || targetRatio <= 0) return source;
        int w = source.getWidth();
        int h = source.getHeight();
        float currentRatio = (float) w / (float) h;

        int newW = w;
        int newH = h;

        if (currentRatio > targetRatio) {
            newW = (int) (h * targetRatio);
        } else {
            newH = (int) (w / targetRatio);
        }

        newW = Math.max(10, Math.min(w, newW));
        newH = Math.max(10, Math.min(h, newH));

        int startX = (w - newW) / 2;
        int startY = (h - newH) / 2;

        return Bitmap.createBitmap(source, startX, startY, newW, newH);
    }
}
