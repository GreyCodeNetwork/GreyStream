package io.greycode.streamer.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class ProfileManager {

    private static final String PREF_NAME = "greystream_prefs";

    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_STREAM_KEY = "stream_key";
    private static final String KEY_PRESET = "preset";
    private static final String KEY_RESOLUTION_WIDTH = "res_width";
    private static final String KEY_RESOLUTION_HEIGHT = "res_height";
    private static final String KEY_BITRATE_KBPS = "bitrate_kbps";
    private static final String KEY_FPS = "fps";
    private static final String KEY_RECORD_LOCAL = "record_local";

    public static final String PRESET_YOUTUBE = "YouTube Live";
    public static final String PRESET_TWITCH = "Twitch";
    public static final String PRESET_FACEBOOK = "Facebook Live";
    public static final String PRESET_CUSTOM = "Custom RTMP/S";

    private final SharedPreferences prefs;

    public ProfileManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public String getServerUrl() {
        return prefs.getString(KEY_SERVER_URL, "rtmp://a.rtmp.youtube.com/live2");
    }

    public void setServerUrl(String url) {
        prefs.edit().putString(KEY_SERVER_URL, url).apply();
    }

    public String getStreamKey() {
        return prefs.getString(KEY_STREAM_KEY, "live_stream_key_demo");
    }

    public void setStreamKey(String key) {
        prefs.edit().putString(KEY_STREAM_KEY, key).apply();
    }

    public String getPreset() {
        return prefs.getString(KEY_PRESET, PRESET_YOUTUBE);
    }

    public void setPreset(String preset) {
        prefs.edit().putString(KEY_PRESET, preset).apply();
    }

    public int getResolutionWidth() {
        return prefs.getInt(KEY_RESOLUTION_WIDTH, 1280);
    }

    public int getResolutionHeight() {
        return prefs.getInt(KEY_RESOLUTION_HEIGHT, 720);
    }

    public void setResolution(int width, int height) {
        prefs.edit()
                .putInt(KEY_RESOLUTION_WIDTH, width)
                .putInt(KEY_RESOLUTION_HEIGHT, height)
                .apply();
    }

    public int getBitrateKbps() {
        return prefs.getInt(KEY_BITRATE_KBPS, 2500);
    }

    public void setBitrateKbps(int kbps) {
        prefs.edit().putInt(KEY_BITRATE_KBPS, kbps).apply();
    }

    public int getFps() {
        return prefs.getInt(KEY_FPS, 30);
    }

    public void setFps(int fps) {
        prefs.edit().putInt(KEY_FPS, fps).apply();
    }

    public boolean isRecordLocal() {
        return prefs.getBoolean(KEY_RECORD_LOCAL, false);
    }

    public void setRecordLocal(boolean record) {
        prefs.edit().putBoolean(KEY_RECORD_LOCAL, record).apply();
    }

    public boolean isAdaptiveBitrateEnabled() {
        return prefs.getBoolean("adaptive_bitrate", true);
    }

    public void setAdaptiveBitrateEnabled(boolean enabled) {
        prefs.edit().putBoolean("adaptive_bitrate", enabled).apply();
    }

    public String getAspectRatioMode() {
        return prefs.getString("aspect_ratio_mode", "adjust");
    }

    public void setAspectRatioMode(String mode) {
        prefs.edit().putString("aspect_ratio_mode", mode).apply();
    }

    public String getAudioSource() {
        return prefs.getString("audio_source", "internal");
    }

    public void setAudioSource(String source) {
        prefs.edit().putString("audio_source", source).apply();
    }

    public int getKeyframeInterval() {
        return prefs.getInt("keyframe_interval", 2);
    }

    public void setKeyframeInterval(int seconds) {
        prefs.edit().putInt("keyframe_interval", seconds).apply();
    }

    public String getPreferredOrientation() {
        return prefs.getString("preferred_orientation", "prompt");
    }

    public void setPreferredOrientation(String mode) {
        prefs.edit().putString("preferred_orientation", mode).apply();
    }

    public boolean isRememberOrientation() {
        return prefs.getBoolean("remember_orientation", false);
    }

    public void setRememberOrientation(boolean remember) {
        prefs.edit().putBoolean("remember_orientation", remember).apply();
    }

    public float getAudioGain() {
        return prefs.getFloat("audio_gain", 1.0f);
    }

    public void setAudioGain(float gain) {
        prefs.edit().putFloat("audio_gain", gain).apply();
    }

    public String getFullStreamUrl() {
        String url = getServerUrl().trim();
        String key = getStreamKey().trim();

        if (url.endsWith("/")) {
            return url + key;
        } else {
            return url + "/" + key;
        }
    }
}
