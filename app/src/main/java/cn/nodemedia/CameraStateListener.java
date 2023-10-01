package cn.nodemedia;

import androidx.camera.core.CameraState;

public interface CameraStateListener {
    void onCameraStateChanged(CameraState cameraState);
}
