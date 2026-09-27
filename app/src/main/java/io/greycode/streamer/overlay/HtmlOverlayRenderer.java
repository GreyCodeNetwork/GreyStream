package io.greycode.streamer.overlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class HtmlOverlayRenderer {

    private final Context context;
    private WebView webView;
    private Bitmap currentBitmap;
    private int width = 800;
    private int height = 450;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isCapturing = false;
    private boolean hasNewFrame = false;

    public boolean consumeHasNewFrame() {
        if (hasNewFrame) {
            hasNewFrame = false;
            return true;
        }
        return false;
    }

    private final Object bitmapLock = new Object();

    private final Runnable captureRunnable = new Runnable() {
        @Override
        public void run() {
            if (webView != null && width > 0 && height > 0) {
                try {
                    synchronized (bitmapLock) {
                        if (currentBitmap == null || currentBitmap.getWidth() != width || currentBitmap.getHeight() != height) {
                            currentBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        }
                        Canvas canvas = new Canvas(currentBitmap);
                        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                        webView.draw(canvas);
                        hasNewFrame = true;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (isCapturing) {
                mainHandler.postDelayed(this, 150); // ~7 FPS capture for HTML widgets without blocking UI thread
            }
        }
    };

    public HtmlOverlayRenderer(Context context) {
        this.context = context.getApplicationContext();
        mainHandler.post(this::initWebView);
    }

    private void initWebView() {
        try {
            webView = new WebView(context);
            webView.setBackgroundColor(Color.TRANSPARENT);
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    startCapturing();
                }
            });
            webView.layout(0, 0, width, height);
            startCapturing();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void startCapturing() {
        if (!isCapturing) {
            isCapturing = true;
            mainHandler.removeCallbacks(captureRunnable);
            mainHandler.post(captureRunnable);
        }
    }

    public void stopCapturing() {
        isCapturing = false;
        mainHandler.removeCallbacks(captureRunnable);
    }

    public void loadUrlOrCode(String content) {
        if (content == null || content.trim().isEmpty()) return;
        mainHandler.post(() -> {
            if (webView != null) {
                String str = content.trim();
                if (str.startsWith("http://") || str.startsWith("https://")) {
                    webView.loadUrl(str);
                } else {
                    webView.loadDataWithBaseURL(null, str, "text/html", "UTF-8", null);
                }
                startCapturing();
            }
        });
    }

    public void updateSize(int w, int h) {
        this.width = Math.max(100, w);
        this.height = Math.max(100, h);
        mainHandler.post(() -> {
            if (webView != null) {
                webView.layout(0, 0, width, height);
            }
        });
    }

    public Bitmap getCurrentBitmap() {
        synchronized (bitmapLock) {
            return currentBitmap;
        }
    }

    public void destroy() {
        stopCapturing();
        mainHandler.post(() -> {
            if (webView != null) {
                webView.destroy();
                webView = null;
            }
        });
    }
}
