package io.greycode.streamer.overlay;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import com.pedro.encoder.input.gl.TextureLoader;
import com.pedro.encoder.input.gl.render.filters.object.ImageObjectFilterRender;

public class CustomImageObjectFilterRender extends ImageObjectFilterRender {

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
