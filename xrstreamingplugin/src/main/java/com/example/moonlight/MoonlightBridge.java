package com.example.moonlight;

import android.app.Activity;

import com.limelight.xr.MoonlightXRPlugin;
import com.limelight.xr.MoonlightXRPluginListener;

/**
 * Thin compatibility wrapper that exposes the XR streaming plugin under the
 * package name expected by prebuilt moonlight_jni libraries. This avoids JNI
 * name mismatches when the native code looks for com.example.moonlight.
 */
public class MoonlightBridge {
    private final MoonlightXRPlugin plugin;

    public MoonlightBridge(Activity activity) {
        this.plugin = new MoonlightXRPlugin(activity);
    }

    /**
     * Convenience overload that uses default ports and stream settings for
     * compatibility with existing Unity bindings expecting only host/appId
     * parameters.
     */
    public boolean connect(String host, String appId) {
        return connect(host, 47984, 47989, appId, 1920, 1080, 60, 20000);
    }

    public boolean connect(String host, int port, int httpsPort, String uniqueId,
                           int width, int height, int fps, int bitrate) {
        return plugin.connect(host, port, httpsPort, uniqueId, width, height, fps, bitrate);
    }

    public void setListener(MoonlightXRPluginListener listener) {
        plugin.setListener(listener);
    }

    public void startStream() {
        plugin.startStream();
    }

    public void stopStream() {
        plugin.stopStream();
    }

    public int getVideoTextureId() {
        return plugin.getVideoTextureId();
    }

    public void updateVideoTexture() {
        plugin.updateVideoTexture();
    }
}
