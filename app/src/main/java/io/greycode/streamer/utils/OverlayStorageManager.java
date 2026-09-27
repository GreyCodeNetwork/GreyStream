package io.greycode.streamer.utils;

import android.content.Context;
import android.graphics.Bitmap;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.greycode.streamer.overlay.BackgroundType;
import io.greycode.streamer.overlay.OverlayAnimation;
import io.greycode.streamer.overlay.OverlayItem;
import io.greycode.streamer.overlay.OverlayType;
import io.greycode.streamer.overlay.ScrollDirection;

public class OverlayStorageManager {

    private static final String OVERLAYS_FILE_NAME = "overlays.json";

    public static void saveOverlays(Context context, List<OverlayItem> overlays) {
        if (context == null) return;
        try {
            JSONArray array = new JSONArray();

            if (overlays != null) {
                for (OverlayItem item : overlays) {
                    JSONObject obj = new JSONObject();
                    obj.put("id", item.getId());
                    obj.put("name", item.getName());
                    obj.put("type", item.getType().name());
                    obj.put("xPercent", item.getXPercent());
                    obj.put("yPercent", item.getYPercent());
                    obj.put("scale", item.getScale());
                    obj.put("rotation", item.getRotation());
                    obj.put("alpha", item.getAlpha());
                    obj.put("text", item.getText());
                    obj.put("textColor", item.getTextColor());
                    obj.put("bgColor", item.getBgColor());
                    obj.put("textSize", item.getTextSize());
                    obj.put("scrollSpeed", item.getScrollSpeed());
                    obj.put("scrollDirection", item.getScrollDirection().name());
                    obj.put("animation", item.getAnimation().name());
                    obj.put("bgType", item.getBgType().name());
                    obj.put("bgAlpha", item.getBgAlpha());
                    obj.put("zIndex", item.getZIndex());
                    obj.put("visible", item.isVisible());
                    obj.put("locked", item.isLocked());
                    obj.put("htmlUrlOrCode", item.getHtmlUrlOrCode());
                    obj.put("htmlFullPage", item.isHtmlFullPage());
                    obj.put("cropLeftPercent", item.getCropLeftPercent());
                    obj.put("cropRightPercent", item.getCropRightPercent());
                    obj.put("cropTopPercent", item.getCropTopPercent());
                    obj.put("cropBottomPercent", item.getCropBottomPercent());

                    // Always save / overwrite transparent PNG bitmap to app data storage
                    String imagePath = item.getImagePath();
                    Bitmap imageBmp = item.getImageBitmap();
                    if (imageBmp != null) {
                        String targetPathOrName = (imagePath != null && !imagePath.isEmpty()) ? imagePath : "logo_" + item.getId();
                        String savedPath = LogoManager.saveOrOverwriteLogo(context, imageBmp, targetPathOrName);
                        if (savedPath != null) {
                            imagePath = savedPath;
                            item.setImagePath(savedPath);
                        }
                    }
                    obj.put("imagePath", imagePath != null ? imagePath : "");

                    // Always save / overwrite bg image bitmap
                    String bgImagePath = item.getBgImagePath();
                    Bitmap bgBmp = item.getBgImageBitmap();
                    if (bgBmp != null) {
                        String targetBgPathOrName = (bgImagePath != null && !bgImagePath.isEmpty()) ? bgImagePath : "bg_" + item.getId();
                        String savedBgPath = LogoManager.saveOrOverwriteLogo(context, bgBmp, targetBgPathOrName);
                        if (savedBgPath != null) {
                            bgImagePath = savedBgPath;
                            item.setBgImagePath(savedBgPath);
                        }
                    }
                    obj.put("bgImagePath", bgImagePath != null ? bgImagePath : "");

                    array.put(obj);
                }
            }

            File file = new File(context.getFilesDir(), OVERLAYS_FILE_NAME);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(array.toString(2).getBytes("UTF-8"));
            fos.flush();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<OverlayItem> loadOverlays(Context context) {
        List<OverlayItem> list = new ArrayList<>();
        if (context == null) return list;

        try {
            File file = new File(context.getFilesDir(), OVERLAYS_FILE_NAME);
            if (!file.exists()) {
                return list;
            }

            FileInputStream fis = new FileInputStream(file);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            fis.close();

            JSONArray array = new JSONArray(sb.toString());
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                OverlayType type = OverlayType.valueOf(obj.optString("type", OverlayType.TEXT.name()));
                String text = obj.optString("text", "");

                OverlayItem item = new OverlayItem(type, text);
                item.setName(obj.optString("name", item.getName()));
                item.setXPercent((float) obj.optDouble("xPercent", 0.5));
                item.setYPercent((float) obj.optDouble("yPercent", 0.5));
                item.setScale((float) obj.optDouble("scale", 1.0));
                item.setRotation((float) obj.optDouble("rotation", 0.0));
                item.setAlpha((float) obj.optDouble("alpha", 1.0));
                item.setTextColor(obj.optInt("textColor", -1));
                item.setBgColor(obj.optInt("bgColor", 0));
                item.setTextSize((float) obj.optDouble("textSize", 36.0));
                item.setScrollSpeed((float) obj.optDouble("scrollSpeed", 5.0));

                String dirStr = obj.optString("scrollDirection", ScrollDirection.RIGHT_TO_LEFT.name());
                item.setScrollDirection(ScrollDirection.valueOf(dirStr));

                String animStr = obj.optString("animation", OverlayAnimation.NONE.name());
                item.setAnimation(OverlayAnimation.valueOf(animStr));

                String bgTypeStr = obj.optString("bgType", BackgroundType.COLOR.name());
                item.setBgType(BackgroundType.valueOf(bgTypeStr));

                item.setBgAlpha((float) obj.optDouble("bgAlpha", 0.6));
                item.setZIndex(obj.optInt("zIndex", i));
                item.setVisible(obj.optBoolean("visible", true));
                item.setLocked(obj.optBoolean("locked", false));

                item.setHtmlFullPage(obj.optBoolean("htmlFullPage", false));
                item.setCropLeftPercent((float) obj.optDouble("cropLeftPercent", 0.0));
                item.setCropRightPercent((float) obj.optDouble("cropRightPercent", 0.0));
                item.setCropTopPercent((float) obj.optDouble("cropTopPercent", 0.0));
                item.setCropBottomPercent((float) obj.optDouble("cropBottomPercent", 0.0));

                String htmlUrlOrCode = obj.optString("htmlUrlOrCode", "");
                if (!htmlUrlOrCode.isEmpty()) {
                    item.setHtmlUrlOrCode(htmlUrlOrCode);
                    if (type == OverlayType.HTML_OVERLAY) {
                        item.initHtmlRenderer(context);
                    }
                }

                String imagePath = obj.optString("imagePath", "");
                if (!imagePath.isEmpty()) {
                    item.setImagePath(imagePath);
                }

                String bgImagePath = obj.optString("bgImagePath", "");
                if (!bgImagePath.isEmpty()) {
                    item.setBgImagePath(bgImagePath);
                }

                list.add(item);
            }

            // Sort by zIndex ascending so canvas renders lower zIndex back and higher zIndex front
            Collections.sort(list, (o1, o2) -> Integer.compare(o1.getZIndex(), o2.getZIndex()));

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }
}
