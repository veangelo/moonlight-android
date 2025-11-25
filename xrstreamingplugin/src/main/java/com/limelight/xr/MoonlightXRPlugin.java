package com.limelight.xr;

import android.app.Activity;
import android.content.Context;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.view.Surface;
import android.graphics.SurfaceTexture;

import com.limelight.binding.PlatformBinding;
import com.limelight.binding.audio.AndroidAudioRenderer;
import com.limelight.binding.video.CrashListener;
import com.limelight.binding.video.MediaCodecDecoderRenderer;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.NvConnectionListener;
import com.limelight.nvstream.StreamConfiguration;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.LimelightCryptoProvider;
import com.limelight.nvstream.http.NvApp;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.nvstream.jni.MoonBridge;

import java.security.cert.X509Certificate;

/**
 * Lightweight wrapper for XR/Unity integrations that need to drive Moonlight's
 * decoder and renderer without the full Android UI stack.
 */
public class MoonlightXRPlugin implements NvConnectionListener, CrashListener {
    private final Activity activity;
    private final Context appContext;
    private final LimelightCryptoProvider cryptoProvider;
    private final NvApp appDescriptor;

    private PreferenceConfiguration preferences;
    private StreamConfiguration streamConfiguration;
    private NvConnection connection;
    private MediaCodecDecoderRenderer videoRenderer;
    private AndroidAudioRenderer audioRenderer;
    private MoonlightXRPluginListener listener;

    private SurfaceTexture surfaceTexture;
    private Surface renderSurface;
    private int textureId = -1;

    public MoonlightXRPlugin(Activity activity) {
        this.activity = activity;
        this.appContext = activity.getApplicationContext();
        this.cryptoProvider = PlatformBinding.getCryptoProvider(appContext);
        this.appDescriptor = new NvApp("Steam");
    }

    public void setListener(MoonlightXRPluginListener listener) {
        this.listener = listener;
    }

    public boolean connect(String host, int port, int httpsPort, String uniqueId,
                           int width, int height, int fps, int bitrate) {
        return connect(host, port, httpsPort, uniqueId, width, height, fps, bitrate, null);
    }

    public boolean connect(String host, int port, int httpsPort, String uniqueId,
                           int width, int height, int fps, int bitrate, X509Certificate serverCert) {
        stopStream();

        preferences = buildPreferences(width, height, fps, bitrate);
        if (!ensureSurface(width, height)) {
            return false;
        }

        videoRenderer = new MediaCodecDecoderRenderer(activity, preferences, this,
                0, false, false, getGlRendererName(), null);
        videoRenderer.setRenderSurface(renderSurface);

        int supportedVideoFormats = MoonBridge.VIDEO_FORMAT_H264;
        if (videoRenderer.isHevcSupported()) {
            supportedVideoFormats |= MoonBridge.VIDEO_FORMAT_H265;
        }
        if (videoRenderer.isAv1Supported()) {
            supportedVideoFormats |= MoonBridge.VIDEO_FORMAT_AV1_MAIN8;
        }

        streamConfiguration = new StreamConfiguration.Builder()
                .setApp(appDescriptor)
                .setResolution(width, height)
                .setLaunchRefreshRate(fps)
                .setRefreshRate(fps)
                .setBitrate(bitrate)
                .setEnableSops(true)
                .enableLocalAudioPlayback(false)
                .setMaxPacketSize(1392)
                .setRemoteConfiguration(StreamConfiguration.STREAM_CFG_AUTO)
                .setSupportedVideoFormats(supportedVideoFormats)
                .setAttachedGamepadMask(0)
                .setClientRefreshRateX100(fps * 100)
                .setAudioConfiguration(MoonBridge.AUDIO_CONFIGURATION_STEREO)
                .setColorSpace(videoRenderer.getPreferredColorSpace())
                .setColorRange(videoRenderer.getPreferredColorRange())
                .setPersistGamepadsAfterDisconnect(true)
                .build();

        connection = new NvConnection(appContext,
                new ComputerDetails.AddressTuple(host, port),
                httpsPort, uniqueId, streamConfiguration,
                cryptoProvider, serverCert);
        audioRenderer = new AndroidAudioRenderer(appContext, false);
        return true;
    }

    public void startStream() {
        if (connection != null && videoRenderer != null && audioRenderer != null) {
            connection.start(audioRenderer, videoRenderer, this);
        }
    }

    public void stopStream() {
        if (connection != null) {
            connection.stop();
            connection = null;
        }
        streamConfiguration = null;
        preferences = null;
        videoRenderer = null;
        audioRenderer = null;
        if (surfaceTexture != null) {
            surfaceTexture.release();
            surfaceTexture = null;
        }
        if (renderSurface != null) {
            renderSurface.release();
            renderSurface = null;
        }
        if (textureId != -1) {
            int[] textures = new int[]{textureId};
            GLES20.glDeleteTextures(1, textures, 0);
            textureId = -1;
        }
    }

    public int getVideoTextureId() {
        return textureId;
    }

    public void updateVideoTexture() {
        if (surfaceTexture != null) {
            surfaceTexture.updateTexImage();
        }
    }

    @Override
    public void stageStarting(String stage) {
        if (listener != null) {
            listener.onStageStarting(stage);
        }
    }

    @Override
    public void stageComplete(String stage) {
        if (listener != null) {
            listener.onStageComplete(stage);
        }
    }

    @Override
    public void stageFailed(String stage, int portFlags, int errorCode) {
        if (listener != null) {
            listener.onStageFailed(stage, portFlags, errorCode);
        }
    }

    @Override
    public void connectionStarted() {
        if (listener != null) {
            listener.onConnectionStarted();
        }
    }

    @Override
    public void connectionTerminated(int errorCode) {
        if (listener != null) {
            listener.onConnectionTerminated(errorCode);
        }
    }

    @Override
    public void connectionStatusUpdate(int connectionStatus) {
        if (listener != null) {
            listener.onConnectionStatusUpdate(connectionStatus);
        }
    }

    @Override
    public void displayMessage(String message) {
        if (listener != null) {
            listener.onMessage(message);
        }
    }

    @Override
    public void displayTransientMessage(String message) {
        if (listener != null) {
            listener.onMessage(message);
        }
    }

    @Override
    public void rumble(short controllerNumber, short lowFreqMotor, short highFreqMotor) {
        // No-op by default for XR integrations.
    }

    @Override
    public void rumbleTriggers(short controllerNumber, short leftTrigger, short rightTrigger) {
        // No-op by default for XR integrations.
    }

    @Override
    public void setHdrMode(boolean enabled, byte[] hdrMetadata) {
        if (listener != null) {
            listener.onHdrModeChanged(enabled, hdrMetadata);
        }
    }

    @Override
    public void setMotionEventState(short controllerNumber, byte motionType, short reportRateHz) {
        // Motion input is not handled by the XR plugin wrapper yet.
    }

    @Override
    public void setControllerLED(short controllerNumber, byte r, byte g, byte b) {
        // Controller LEDs are not managed by the XR plugin wrapper.
    }

    @Override
    public void onCrash() {
        if (listener != null) {
            listener.onDecoderCrashed();
        }
    }

    private PreferenceConfiguration buildPreferences(int width, int height, int fps, int bitrate) {
        PreferenceConfiguration config = new PreferenceConfiguration();
        config.width = width;
        config.height = height;
        config.fps = fps;
        config.bitrate = bitrate;
        config.enableSops = true;
        config.videoFormat = PreferenceConfiguration.FormatOption.AUTO;
        config.multiController = true;
        config.playHostAudio = true;
        config.audioConfiguration = MoonBridge.AUDIO_CONFIGURATION_STEREO;
        return config;
    }

    private boolean ensureSurface(int width, int height) {
        if (renderSurface != null && surfaceTexture != null) {
            return true;
        }

        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        textureId = textures[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);

        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        surfaceTexture = new SurfaceTexture(textureId);
        surfaceTexture.setDefaultBufferSize(width, height);
        renderSurface = new Surface(surfaceTexture);
        return true;
    }

    private String getGlRendererName() {
        String renderer = GLES20.glGetString(GLES20.GL_RENDERER);
        return renderer != null ? renderer : "unity";
    }
}
