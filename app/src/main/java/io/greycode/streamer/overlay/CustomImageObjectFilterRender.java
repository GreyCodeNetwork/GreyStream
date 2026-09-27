package io.greycode.streamer.overlay;

import android.graphics.Bitmap;
import android.opengl.EGL14;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import com.pedro.encoder.input.gl.TextureLoader;
import com.pedro.encoder.input.gl.render.filters.object.ImageObjectFilterRender;

import java.lang.reflect.Method;

public class CustomImageObjectFilterRender extends ImageObjectFilterRender {

    private boolean recordWithoutOverlays = false;
    private volatile boolean isRecording = false;

    private Object mainRenderRef;
    private Object cameraRenderRef;
    private Method setTexIdMethod;
    private Method getCameraTexIdMethod;
    private EGLSurface recordEglSurface = EGL14.EGL_NO_SURFACE;

    public void configureCleanRecordHook(Object mainRender, Object cameraRender, EGLSurface recordSurface, boolean active) {
        this.mainRenderRef = mainRender;
        this.cameraRenderRef = cameraRender;
        this.recordEglSurface = recordSurface;
        this.recordWithoutOverlays = active;

        if (mainRender != null && cameraRender != null) {
            try {
                this.setTexIdMethod = mainRender.getClass().getMethod("setTexId", int.class);
                this.getCameraTexIdMethod = cameraRender.getClass().getMethod("getTexId");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void setRecordWithoutOverlays(boolean withoutOverlays) {
        this.recordWithoutOverlays = withoutOverlays;
    }

    public void setRecording(boolean recording) {
        this.isRecording = recording;
        if (!recording) {
            recordEglSurface = EGL14.EGL_NO_SURFACE;
        }
    }

    @Override
    public int getTexId() {
        int filteredTexId = super.getTexId();

        if (recordWithoutOverlays && isRecording && recordEglSurface != EGL14.EGL_NO_SURFACE && mainRenderRef != null && setTexIdMethod != null && getCameraTexIdMethod != null) {
            EGLSurface currentSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);
            if (currentSurface != null && currentSurface.equals(recordEglSurface)) {
                try {
                    int cleanTexId = (Integer) getCameraTexIdMethod.invoke(cameraRenderRef);
                    setTexIdMethod.invoke(mainRenderRef, cleanTexId);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    setTexIdMethod.invoke(mainRenderRef, filteredTexId);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return filteredTexId;
    }

    public CustomImageObjectFilterRender() {
        super();
        this.textureLoader = new TextureLoader() {
            private int[] textureIds;

            @Override
            public int[] load(Bitmap[] bitmaps) {
                if (bitmaps == null || bitmaps.length == 0 || bitmaps[0] == null || bitmaps[0].isRecycled()) {
                    return textureIds != null ? textureIds : new int[] { -1 };
                }

                if (textureIds == null || textureIds[0] <= 0) {
                    textureIds = new int[bitmaps.length];
                    GLES20.glGenTextures(bitmaps.length, textureIds, 0);
                }

                for (int i = 0; i < bitmaps.length; i++) {
                    if (bitmaps[i] != null && !bitmaps[i].isRecycled()) {
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[i]);
                        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
                        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
                        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
                        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
                        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmaps[i], 0);
                    }
                }
                return textureIds;
            }
        };
    }
}
