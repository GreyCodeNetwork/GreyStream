package io.greycode.streamer.overlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;

import java.util.UUID;

public class OverlayItem {
    private String name;
    private String id;
    private OverlayType type;
    private float xPercent; // 0.0 to 1.0 (normalized relative to canvas width)
    private float yPercent; // 0.0 to 1.0 (normalized relative to canvas height)
    private float scale;    // Default 1.0f
    private float rotation; // In degrees
    private float alpha;    // 0.0f to 1.0f (Total Layer Opacity)
    private String text;
    private int textColor;
    private int bgColor;
    private float textSize; // In sp/dp
    private float scrollSpeed; // Pixels per frame
    private ScrollDirection scrollDirection = ScrollDirection.RIGHT_TO_LEFT;
    private OverlayAnimation animation = OverlayAnimation.NONE;

    private Uri imageUri;
    private Bitmap imageBitmap;
    private String imagePath;

    private BackgroundType bgType = BackgroundType.COLOR;
    private float bgAlpha = 0.6f; // 0.0f to 1.0f (Background Opacity)
    private Uri bgImageUri;
    private Bitmap bgImageBitmap;
    private String bgImagePath;

    private int zIndex;
    private boolean visible;
    private boolean locked;

    private String htmlUrlOrCode = "https://example.com";
    private boolean htmlFullPage = false;
    private float cropLeftPercent = 0f;
    private float cropRightPercent = 0f;
    private float cropTopPercent = 0f;
    private float cropBottomPercent = 0f;
    private transient HtmlOverlayRenderer htmlRenderer;

    // Animation phase counter
    private float animationPhase = 0f;
    private float marqueeXOffset = 0f;

    public OverlayItem(OverlayType type, String text) {
        this.id = UUID.randomUUID().toString();
        this.type = type;
        this.text = text != null ? text : "";
        this.name = getDefaultNameForType(type);
        this.xPercent = 0.5f;
        this.yPercent = 0.5f;
        this.scale = 1.0f;
        this.rotation = 0f;
        this.alpha = 1.0f;
        this.bgAlpha = 0.6f;
        this.bgType = BackgroundType.COLOR;
        this.textColor = Color.WHITE;
        this.bgColor = Color.parseColor("#99000000");
        this.textSize = 36f;
        this.scrollSpeed = 5f;
        this.scrollDirection = ScrollDirection.RIGHT_TO_LEFT;
        this.animation = OverlayAnimation.NONE;
        this.zIndex = 0;
        this.visible = true;
        this.locked = false;

        // Preset defaults
        if (type == OverlayType.SCROLLING_TEXT) {
            this.yPercent = 0.90f;
        } else if (type == OverlayType.TEXT) {
            this.xPercent = 0.5f;
            this.yPercent = 0.5f;
            this.textSize = 42f;
        } else if (type == OverlayType.IMAGE_LOGO) {
            this.xPercent = 0.85f;
            this.yPercent = 0.15f;
            this.bgType = BackgroundType.NONE;
        } else if (type == OverlayType.HTML_OVERLAY) {
            this.xPercent = 0.5f;
            this.yPercent = 0.5f;
            this.bgType = BackgroundType.NONE;
            this.htmlUrlOrCode = text.isEmpty() ? "https://example.com" : text;
        }
    }

    private String getDefaultNameForType(OverlayType type) {
        switch (type) {
            case SCROLLING_TEXT: return "Scrolling Marquee";
            case IMAGE_LOGO: return "Logo / Image";
            case TEXT: return "Text Overlay";
            case HTML_OVERLAY: return "HTML Overlay";
            default: return "Custom Overlay";
        }
    }

    public void initHtmlRenderer(Context context) {
        if (type == OverlayType.HTML_OVERLAY && htmlRenderer == null && context != null) {
            final Context appContext = context.getApplicationContext();
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (htmlRenderer == null) {
                    htmlRenderer = new HtmlOverlayRenderer(appContext);
                    htmlRenderer.loadUrlOrCode(htmlUrlOrCode.isEmpty() ? text : htmlUrlOrCode);
                }
            });
        }
    }

    public boolean checkAndResetHtmlNewFrame() {
        if (type == OverlayType.HTML_OVERLAY && htmlRenderer != null) {
            return htmlRenderer.consumeHasNewFrame();
        }
        return false;
    }

    public void draw(Canvas canvas, int width, int height, Paint paint) {
        if (!visible) return;

        long now = System.currentTimeMillis();
        float timeSec = (now % 10000000L) / 1000f;
        float animationPhase = timeSec * 3f;

        canvas.save();
        paint.reset();
        paint.setAntiAlias(true);

        float calculatedAlpha = alpha;
        float currentScale = scale;
        float currentRotation = rotation;
        float offsetY = 0f;

        // Apply animations
        if (animation == OverlayAnimation.PULSE) {
            float pulse = (float) (Math.sin(animationPhase) * 0.15);
            currentScale *= (1.0f + pulse);
        } else if (animation == OverlayAnimation.FADE) {
            float fade = (float) ((Math.sin(animationPhase) + 1.0) / 2.0 * 0.7 + 0.3);
            calculatedAlpha *= fade;
        } else if (animation == OverlayAnimation.SPIN) {
            currentRotation += (animationPhase * 20f) % 360f;
        } else if (animation == OverlayAnimation.BOUNCE) {
            offsetY = (float) (Math.sin(animationPhase * 2f) * 20f);
        }

        paint.setAlpha((int) (calculatedAlpha * 255));

        float centerX = xPercent * width;
        float centerY = (yPercent * height) + offsetY;

        canvas.translate(centerX, centerY);
        canvas.rotate(currentRotation);
        canvas.scale(currentScale, currentScale);

        // 1. Draw Custom Layer Background (Transparent / Solid Color / Custom Image)
        drawLayerBackground(canvas, paint, calculatedAlpha, width);

        // Apply universal layer clipping/cropping across ALL overlay types
        if (cropLeftPercent > 0 || cropRightPercent > 0 || cropTopPercent > 0 || cropBottomPercent > 0) {
            RectF bounds = getLocalBounds();
            float w = bounds.width();
            float h = bounds.height();
            float clipLeft = bounds.left + (w * Math.max(0f, Math.min(0.45f, cropLeftPercent)));
            float clipTop = bounds.top + (h * Math.max(0f, Math.min(0.45f, cropTopPercent)));
            float clipRight = bounds.right - (w * Math.max(0f, Math.min(0.45f, cropRightPercent)));
            float clipBottom = bounds.bottom - (h * Math.max(0f, Math.min(0.45f, cropBottomPercent)));
            canvas.clipRect(clipLeft, clipTop, Math.max(clipLeft + 1f, clipRight), Math.max(clipTop + 1f, clipBottom));
        }

        // 2. Draw Layer Content
        switch (type) {
            case SCROLLING_TEXT:
                drawScrollingMarquee(canvas, width, paint);
                break;
            case IMAGE_LOGO:
                drawImageLogoOverlay(canvas, paint);
                break;
            case TEXT:
                drawTextOverlay(canvas, paint);
                break;
            case HTML_OVERLAY:
                drawHtmlOverlay(canvas, paint, width, height);
                break;
        }

        canvas.restore();
    }

    private void drawLayerBackground(Canvas canvas, Paint paint, float currentAlpha, int width) {
        if (bgType == BackgroundType.NONE) return;

        RectF bounds;
        if (type == OverlayType.SCROLLING_TEXT) {
            // Background fills full screen width horizontally
            float centerX = xPercent * width;
            float h = textSize * 1.8f;
            float leftLocal = -centerX / scale;
            float rightLocal = (width - centerX) / scale;
            bounds = new RectF(leftLocal, -h / 2f, rightLocal, h / 2f);
        } else {
            bounds = getLocalBounds();
        }

        int bgAlphaInt = (int) (currentAlpha * bgAlpha * 255);

        if (bgType == BackgroundType.COLOR) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(bgColor);
            paint.setAlpha(bgAlphaInt);
            canvas.drawRect(bounds, paint);
        } else if (bgType == BackgroundType.IMAGE) {
            Bitmap bgBmp = getBgImageBitmap();
            if (bgBmp != null && !bgBmp.isRecycled()) {
                paint.setAlpha(bgAlphaInt);
                canvas.drawBitmap(bgBmp, null, bounds, paint);
            }
        }
    }

    private RectF getLocalBounds() {
        return getLocalBounds(1280, 720);
    }

    private RectF getLocalBounds(int canvasWidth, int canvasHeight) {
        float w = canvasWidth * 0.25f;
        float h = canvasHeight * 0.15f;
        if (type == OverlayType.SCROLLING_TEXT) {
            w = canvasWidth * 0.8f;
            float scaledTextSize = Math.max(16f, (textSize / 720f) * canvasHeight);
            h = scaledTextSize * 1.8f;
        } else if (type == OverlayType.TEXT) {
            float scaledTextSize = Math.max(16f, (textSize / 720f) * canvasHeight);
            Paint tempPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            tempPaint.setTextSize(scaledTextSize);
            tempPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            String str = text.isEmpty() ? "CUSTOM TEXT" : text;
            w = tempPaint.measureText(str) + (scaledTextSize * 0.8f);
            h = scaledTextSize * 1.8f;
        } else if (type == OverlayType.HTML_OVERLAY) {
            w = canvasWidth * 0.35f;
            h = w * (250f / 400f);
        } else {
            Bitmap bmp = getImageBitmap();
            if (bmp != null && !bmp.isRecycled()) {
                float rawW = bmp.getWidth();
                float rawH = bmp.getHeight();
                float maxDim = canvasWidth * 0.25f;
                if (rawW > 0 && rawH > 0) {
                    float aspect = rawW / rawH;
                    if (rawW >= rawH) {
                        w = maxDim;
                        h = maxDim / aspect;
                    } else {
                        h = maxDim;
                        w = maxDim * aspect;
                    }
                }
            }
        }
        return new RectF(-w / 2f, -h / 2f, w / 2f, h / 2f);
    }

    private void drawScrollingMarquee(Canvas canvas, int canvasWidth, Paint paint) {
        float scaledTextSize = Math.max(16f, (textSize / 720f) * canvas.getHeight());
        paint.setTextSize(scaledTextSize);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        String displayText = text.isEmpty() ? "★ GREYSTREAM LIVE BROADCAST ★" : text;
        float textWidth = paint.measureText(displayText);

        long now = System.currentTimeMillis();
        float timeSec = (now % 10000000L) / 1000f;
        float marqueeProgress = (timeSec * (scrollSpeed * 0.05f)) % 1.0f;

        float startX = (canvasWidth * 0.5f) + (textWidth * 0.5f);
        float totalDistance = canvasWidth + textWidth;
        float currentX;

        if (scrollDirection == ScrollDirection.RIGHT_TO_LEFT) {
            currentX = startX - (marqueeProgress * totalDistance);
        } else {
            currentX = -startX + (marqueeProgress * totalDistance);
        }

        paint.setColor(textColor);
        paint.setTextAlign(Paint.Align.LEFT);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float textY = -(fm.ascent + fm.descent) / 2f;

        canvas.drawText(displayText, currentX, textY, paint);
    }

    private void drawImageLogoOverlay(Canvas canvas, Paint paint) {
        Bitmap imgBmp = getImageBitmap();
        if (imgBmp != null && !imgBmp.isRecycled()) {
            RectF bounds = getLocalBounds(canvas.getWidth(), canvas.getHeight());
            canvas.drawBitmap(imgBmp, null, bounds, paint);
        } else {
            // Placeholder logo badge
            paint.setColor(textColor);
            paint.setTextSize(textSize);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("🖼️ IMAGE / LOGO", 0f, 12f, paint);
        }
    }

    private void drawTextOverlay(Canvas canvas, Paint paint) {
        String displayText = text.isEmpty() ? "GREYSTREAM TEXT" : text;
        float scaledTextSize = Math.max(16f, (textSize / 720f) * canvas.getHeight());
        paint.setTextSize(scaledTextSize);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setColor(textColor);
        paint.setTextAlign(Paint.Align.CENTER);

        Paint.FontMetrics fm = paint.getFontMetrics();
        float textY = -(fm.ascent + fm.descent) / 2f;

        canvas.drawText(displayText, 0f, textY, paint);
    }

    private void drawHtmlOverlay(Canvas canvas, Paint paint, int width, int height) {
        RectF bounds;
        if (htmlFullPage) {
            bounds = new RectF(-width / 2f, -height / 2f, width / 2f, height / 2f);
            if (htmlRenderer != null) {
                htmlRenderer.updateSize(width, height);
            }
        } else {
            bounds = getLocalBounds(width, height);
        }

        Bitmap htmlBmp = (htmlRenderer != null) ? htmlRenderer.getCurrentBitmap() : null;
        if (htmlBmp != null && !htmlBmp.isRecycled()) {
            if (cropLeftPercent > 0 || cropRightPercent > 0 || cropTopPercent > 0 || cropBottomPercent > 0) {
                int srcW = htmlBmp.getWidth();
                int srcH = htmlBmp.getHeight();
                int left = (int) (srcW * Math.max(0f, Math.min(0.45f, cropLeftPercent)));
                int top = (int) (srcH * Math.max(0f, Math.min(0.45f, cropTopPercent)));
                int right = (int) (srcW * (1.0f - Math.max(0f, Math.min(0.45f, cropRightPercent))));
                int bottom = (int) (srcH * (1.0f - Math.max(0f, Math.min(0.45f, cropBottomPercent))));
                android.graphics.Rect srcRect = new android.graphics.Rect(left, top, Math.max(left + 1, right), Math.max(top + 1, bottom));
                canvas.drawBitmap(htmlBmp, srcRect, bounds, paint);
            } else {
                canvas.drawBitmap(htmlBmp, null, bounds, paint);
            }
        } else {
            // HTML Placeholder badge
            paint.setColor(Color.parseColor("#3300E5FF"));
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(bounds, 16f, 16f, paint);

            paint.setColor(Color.parseColor("#00E5FF"));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            canvas.drawRoundRect(bounds, 16f, 16f, paint);

            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(24f);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("🌐 HTML WEB OVERLAY", 0f, 8f, paint);
        }
    }

    public RectF getBoundingRect(int canvasWidth, int canvasHeight) {
        float cx = xPercent * canvasWidth;
        float cy = yPercent * canvasHeight;
        RectF bounds = getLocalBounds(canvasWidth, canvasHeight);
        float w = bounds.width() * scale;
        float h = bounds.height() * scale;

        return new RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
    }

    // Getters and Setters
    public String getName() {
        return (name != null && !name.trim().isEmpty()) ? name : getDefaultNameForType(type);
    }
    public void setName(String name) { this.name = name; }

    public String getId() { return id; }
    public OverlayType getType() { return type; }
    public void setType(OverlayType type) { this.type = type; }

    public float getXPercent() { return xPercent; }
    public void setXPercent(float xPercent) { this.xPercent = Math.max(-0.2f, Math.min(1.2f, xPercent)); }

    public float getYPercent() { return yPercent; }
    public void setYPercent(float yPercent) { this.yPercent = Math.max(-0.2f, Math.min(1.2f, yPercent)); }

    public float getScale() { return scale; }
    public void setScale(float scale) { this.scale = Math.max(0.3f, Math.min(4.0f, scale)); }

    public float getRotation() { return rotation; }
    public void setRotation(float rotation) { this.rotation = rotation; }

    public float getAlpha() { return alpha; }
    public void setAlpha(float alpha) { this.alpha = Math.max(0.05f, Math.min(1.0f, alpha)); }

    public BackgroundType getBgType() { return bgType; }
    public void setBgType(BackgroundType bgType) { this.bgType = bgType; }

    public float getBgAlpha() { return bgAlpha; }
    public void setBgAlpha(float bgAlpha) { this.bgAlpha = Math.max(0.0f, Math.min(1.0f, bgAlpha)); }

    public Uri getBgImageUri() { return bgImageUri; }
    public void setBgImageUri(Uri bgImageUri) { this.bgImageUri = bgImageUri; }

    public String getBgImagePath() { return bgImagePath; }
    public void setBgImagePath(String bgImagePath) { this.bgImagePath = bgImagePath; }

    public Bitmap getBgImageBitmap() {
        if (bgImageBitmap == null && bgImagePath != null && !bgImagePath.isEmpty()) {
            bgImageBitmap = io.greycode.streamer.utils.LogoManager.loadBitmapFromFile(bgImagePath);
        }
        return bgImageBitmap;
    }
    public void setBgImageBitmap(Bitmap bgImageBitmap) { this.bgImageBitmap = bgImageBitmap; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public int getTextColor() { return textColor; }
    public void setTextColor(int textColor) { this.textColor = textColor; }

    public int getBgColor() { return bgColor; }
    public void setBgColor(int bgColor) { this.bgColor = bgColor; }

    public float getTextSize() { return textSize; }
    public void setTextSize(float textSize) { this.textSize = textSize; }

    public float getScrollSpeed() { return scrollSpeed; }
    public void setScrollSpeed(float scrollSpeed) { this.scrollSpeed = scrollSpeed; }

    public ScrollDirection getScrollDirection() { return scrollDirection; }
    public void setScrollDirection(ScrollDirection scrollDirection) { this.scrollDirection = scrollDirection; }

    public OverlayAnimation getAnimation() { return animation; }
    public void setAnimation(OverlayAnimation animation) { this.animation = animation; }

    public String getHtmlUrlOrCode() { return htmlUrlOrCode; }
    public void setHtmlUrlOrCode(String htmlUrlOrCode) {
        this.htmlUrlOrCode = htmlUrlOrCode;
        if (htmlRenderer != null) {
            htmlRenderer.loadUrlOrCode(htmlUrlOrCode);
        }
    }

    public Uri getImageUri() { return imageUri; }
    public void setImageUri(Uri imageUri) { this.imageUri = imageUri; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public Bitmap getImageBitmap() {
        if (imageBitmap == null && imagePath != null && !imagePath.isEmpty()) {
            imageBitmap = io.greycode.streamer.utils.LogoManager.loadBitmapFromFile(imagePath);
        }
        return imageBitmap;
    }
    public void setImageBitmap(Bitmap imageBitmap) { this.imageBitmap = imageBitmap; }

    public int getZIndex() { return zIndex; }
    public void setZIndex(int zIndex) { this.zIndex = zIndex; }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }

    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }

    public boolean isHtmlFullPage() { return htmlFullPage; }
    public void setHtmlFullPage(boolean htmlFullPage) { this.htmlFullPage = htmlFullPage; }

    public float getCropLeftPercent() { return cropLeftPercent; }
    public void setCropLeftPercent(float cropLeftPercent) { this.cropLeftPercent = cropLeftPercent; }

    public float getCropRightPercent() { return cropRightPercent; }
    public void setCropRightPercent(float cropRightPercent) { this.cropRightPercent = cropRightPercent; }

    public float getCropTopPercent() { return cropTopPercent; }
    public void setCropTopPercent(float cropTopPercent) { this.cropTopPercent = cropTopPercent; }

    public float getCropBottomPercent() { return cropBottomPercent; }
    public void setCropBottomPercent(float cropBottomPercent) { this.cropBottomPercent = cropBottomPercent; }
}
