package io.greycode.streamer.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LogoManager {

    private static final String LOGOS_DIR_NAME = "saved_logos";

    public static File getLogosDirectory(Context context) {
        File dir = new File(context.getFilesDir(), LOGOS_DIR_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static String saveLogo(Context context, Bitmap bitmap, String preferredName) {
        return saveOrOverwriteLogo(context, bitmap, preferredName);
    }

    public static String saveOrOverwriteLogo(Context context, Bitmap bitmap, String existingPathOrName) {
        if (bitmap == null || context == null) return null;
        try {
            File destFile;
            if (existingPathOrName != null && existingPathOrName.contains("/")) {
                destFile = new File(existingPathOrName);
            } else {
                File dir = getLogosDirectory(context);
                String fileName = (existingPathOrName != null && !existingPathOrName.trim().isEmpty())
                        ? existingPathOrName.replaceAll("[^a-zA-Z0-9._-]", "_")
                        : "logo_" + System.currentTimeMillis();
                if (!fileName.toLowerCase().endsWith(".png")) {
                    fileName += ".png";
                }
                destFile = new File(dir, fileName);
            }

            File parent = destFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            FileOutputStream fos = new FileOutputStream(destFile, false);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();

            return destFile.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static List<File> getSavedLogos(Context context) {
        File dir = getLogosDirectory(context);
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".png") || name.toLowerCase().endsWith(".jpg") || name.toLowerCase().endsWith(".webp"));
        if (files == null || files.length == 0) {
            return new ArrayList<>();
        }

        List<File> list = new ArrayList<>(Arrays.asList(files));
        // Sort newest first
        Collections.sort(list, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        return list;
    }

    public static Bitmap loadBitmapFromFile(String path) {
        if (path == null || path.isEmpty()) return null;
        try {
            File file = new File(path);
            if (file.exists()) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                options.inMutable = true;
                return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean deleteLogo(File file) {
        if (file != null && file.exists()) {
            return file.delete();
        }
        return false;
    }
}
