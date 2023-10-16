package com.codewithkael.rtmp.utils

import CameraController
import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest.Builder
import android.util.Log
import com.codewithkael.rtmp.remote.UserApi
import com.haishinkit.event.Event
import com.haishinkit.event.IEventListener
import com.haishinkit.media.Camera2Source
import com.haishinkit.rtmp.RtmpConnection
import com.haishinkit.rtmp.RtmpStream
import com.haishinkit.view.HkSurfaceView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.reflect.Field
import javax.inject.Singleton

@Singleton
class RtmpClient(
    context: Context,
    private val surfaceView: HkSurfaceView,
    private val userApi: UserApi
) : Camera2Source.Listener, IEventListener {

    private val TAG = "RtmpClient3"

    private var connection: RtmpConnection = RtmpConnection()
    private var stream: RtmpStream = RtmpStream(connection)
    private var videoSource: Camera2Source = Camera2Source(context).apply {
        open(CameraCharacteristics.LENS_FACING_BACK)
    }
    private lateinit var url: String

    private var session: CameraCaptureSession? = null
    private var cameraManager: CameraManager? = null
    private var requestBuilder: Builder? = null
    private var key: String? = null

    private var cameraController: CameraController? = null
    private fun getCameraController(): CameraController? {
        return if (cameraController != null) {
            cameraController
        } else {
            try {
                requestBuilder?.let { requestCameraControlBuild(it, true) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            null
        }
    }

    private var isCameraOpen = false
    private var isPublishing = false
    private var currentCameraInfo: CameraInfoModel? = null

    init {
        stream.attachVideo(videoSource)
        connection.addEventListener(Event.RTMP_STATUS, this@RtmpClient)
        surfaceView.attachStream(stream)
        videoSource.listener = this@RtmpClient
    }

    fun start(
        info: CameraInfoModel, key: String?,
        isCameraOpenResult: (Boolean) -> Unit
    ) {
        Log.d(TAG, "kael start called publishing:$isPublishing camera:$isCameraOpen : $info ")
        if (currentCameraInfo == null) currentCameraInfo = info
        this@RtmpClient.key = key
        url = "rtmp://164.92.142.251/live/$key"
//        val url = "rtmp://192.168.126.131/live/$key"
        handleStartOrUpdate(info, url)
        CoroutineScope(Dispatchers.IO).launch {
            delay(2000)
            isCameraOpenResult(isCameraOpen)
        }

    }


    private fun handleStartOrUpdate(info: CameraInfoModel, url: String) {
        if (currentCameraInfo?.fps != info.fps || currentCameraInfo?.bitrate != info.bitrate
            || currentCameraInfo?.width != info.width || currentCameraInfo?.height != info.height
        ) {
            Log.d(TAG, "handleStartOrUpdate: kael start called 1")
            CoroutineScope(Dispatchers.IO).launch {
                stopPublishing()
                delay(1000)
                startPublishing(info, url)
            }

        } else {
            Log.d(TAG, "handleStartOrUpdate: kael start called 2")

            if (!isPublishing) {
                Log.d(TAG, "handleStartOrUpdate: kael start called 3")

                CoroutineScope(Dispatchers.IO).launch {
                    stopPublishing()
                    delay(1000)
                    startPublishing(info, url)
                }
            } else {
                Log.d(TAG, "handleStartOrUpdate: kael start called 4")
                Log.d(TAG, "startPublishing: update publishing 2 call shod")

                updatePublishing(
                    info,
                    info.exposureCompensation != currentCameraInfo?.exposureCompensation
                )
            }
        }

        currentCameraInfo = info

    }

    private fun updatePublishing(info: CameraInfoModel, isExposureUpdated: Boolean) {
        Log.d(TAG, "updatePublishing: called $info")
        try {
//            rotateCameraPreview(info.orientation)
            CoroutineScope(Dispatchers.IO).launch {
                getCameraController()?.updateCameraInfo(info, isExposureUpdated)
            }
        } catch (e: Exception) {
            Log.d(TAG, "updatePublishing: error dad $info")

            e.printStackTrace()
        }
    }


    private fun startPublishing(info: CameraInfoModel, url: String) {
        try {
            stream.videoSetting.width = info.width // The width  of video output.
            stream.videoSetting.height = info.height // The height  of video output.
            stream.videoSetting.bitRate = info.bitrate // The bitRate of video output.
            stream.videoSetting.frameRate = if (info.fps < 15) 15 else info.fps
            stream.videoSetting.IFrameInterval = 2
//            stream.videoSetting.videoGravity = VideoGravity.RESIZE_ASPECT
            connection.connect(url)
            stream.publish(url.split("live/")[1])
            CoroutineScope(Dispatchers.IO).launch {
                delay(3000)
                if (requestBuilder == null || session == null || cameraManager == null) {
                    try {
                        CoroutineScope(Dispatchers.IO).launch {
                            val cameraManagerField: Field =
                                Camera2Source::class.java.getDeclaredField("manager")
                            cameraManagerField.isAccessible = true
                            cameraManager = cameraManagerField.get(videoSource) as CameraManager

                            val cameraSessionField: Field =
                                Camera2Source::class.java.getDeclaredField("session")
                            cameraSessionField.isAccessible = true
                            try {
                                delay(2000)
                                session = cameraSessionField.get(videoSource) as CameraCaptureSession
                                Log.d(TAG, "onCreate 11: $session")
                                getCameraController()
                                delay(2000)
                                Log.d(TAG, "startPublishing: update publishing 1 call shod")
                                delay(1000)
                                updatePublishing(
                                    info,
                                    info.exposureCompensation != currentCameraInfo?.exposureCompensation
                                )

                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                        }

                    } catch (e: NoSuchFieldException) {
                        Log.d(TAG, "onCreate: ${e.message}")
                        e.printStackTrace()
                    } catch (e: IllegalAccessException) {
                        Log.d(TAG, "onCreate: ${e.message}")
                        e.printStackTrace()
                    }
                }

            }

        } catch (e: Exception) {
            Log.d(TAG, "startPublishing: error  ${e.message}")
            e.printStackTrace()
        }

    }

//    private fun rotateCameraPreview(degrees: Int) {
//        Log.d(TAG, "rotateCameraPreview: called")
//        try {
//            requestBuilder.set(CaptureRequest.JPEG_ORIENTATION, 270)
//            session.setRepeatingRequest(requestBuilder.build(), null, null)
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
//    }


    private fun stopPublishing() {

        try {
            stream.close()
            connection.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("SuspiciousIndentation")
    override fun onCreateCaptureRequest(builder: Builder) {
        this.requestBuilder = builder
        requestCameraControlBuild(builder, false)
    }

    private fun requestCameraControlBuild(builder: Builder, refresh: Boolean) {

        try {
            if (!refresh) {
                cameraController = cameraManager?.let {
                    session?.let { it1 ->
                        CameraController(
                            0.toString(),
                            it, it1, builder, surfaceView
                        )
                    }
                }
                isCameraOpen = true
            } else {
                    try {
                        CoroutineScope(Dispatchers.IO).launch {
                            delay(2000)
                            val cameraManagerField: Field =
                                Camera2Source::class.java.getDeclaredField("manager")
                            cameraManagerField.isAccessible = true
                            cameraManager = cameraManagerField.get(videoSource) as CameraManager
                            delay(2000)
                            val cameraSessionField: Field =
                                Camera2Source::class.java.getDeclaredField("session")
                            cameraSessionField.isAccessible = true
                            session = cameraSessionField.get(videoSource) as CameraCaptureSession
                            Log.d(TAG, "onCreate: $session")
                            if (cameraManager != null && session != null && requestBuilder != null) {
                                cameraController = CameraController(
                                    0.toString(), cameraManager!!, session!!, requestBuilder!!,
                                    surfaceView
                                )
                                isCameraOpen = true
                                delay(1000)
                                currentCameraInfo?.let {
                                    updatePublishing(
                                        it,
                                        false
                                    )
                                }
                            }
                        }
                    } catch (e: NoSuchFieldException) {
                        Log.d(TAG, "onCreate: ${e.message}")
                        e.printStackTrace()
                    } catch (e: IllegalAccessException) {
                        Log.d(TAG, "onCreate: ${e.message}")
                        e.printStackTrace()
                    }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onError(camera: CameraDevice, error: Int) {
        isCameraOpen = false
    }

    override fun handleEvent(event: Event) {
        isPublishing = event.data.toString().contains("code=NetConnection.Connect.Success")
                && event.type == "rtmpStatus"

        if (isPublishing) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    userApi.resetStream(key ?: "")
                } catch (e: Exception) {
                    Log.d(TAG, "handleEvent: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
        Log.d(TAG, "handleStartOrUpdate: kael publisher setter 4 $isPublishing")
        Log.d(TAG, "handleStartOrUpdate: kael  ${event.data}")
        Log.d(TAG, "handleStartOrUpdate: kael  ${event.type}")

        if (event.data.toString().contains("code=NetConnection.Connect.Closed")
            && event.type == "rtmpStatus"
        ) {
            CoroutineScope(Dispatchers.IO).launch {

                delay(3000)
                if (!isPublishing) {
                    stopPublishing()
                    delay(1000)
                    currentCameraInfo?.let { startPublishing(it, url) }
                }
            }
        }
    }


}

