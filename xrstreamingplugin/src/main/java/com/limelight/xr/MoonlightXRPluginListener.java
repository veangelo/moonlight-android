package com.limelight.xr;

public interface MoonlightXRPluginListener {
    void onStageStarting(String stage);
    void onStageComplete(String stage);
    void onStageFailed(String stage, int portFlags, int errorCode);
    void onConnectionStarted();
    void onConnectionTerminated(int errorCode);
    void onConnectionStatusUpdate(int status);
    void onMessage(String message);
    void onHdrModeChanged(boolean enabled, byte[] metadata);
    void onDecoderCrashed();
}
