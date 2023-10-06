package com.codewithkael.rtmp.utils

import CameraController
import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureRequest.Builder
import android.util.Log
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

@SuppressLint("SuspiciousIndentation")
@Singleton
class RtmpClient3 constructor(
    private val context: Context,
    private val surfaceView: HkSurfaceView
) : Camera2Source.Listener, IEventListener {

    private val TAG = "RtmpClient3"

    private val connection: RtmpConnection
    private val stream: RtmpStream

    private lateinit var videoSource: Camera2Source


    private lateinit var session: CameraCaptureSession
    private lateinit var cameraManager: CameraManager
    private lateinit var requestBuilder: Builder

    private var cameraController: CameraController? = null
    private fun getCameraController():CameraController? {
        return if (cameraController!=null) {
            cameraController
        } else {
            try {
                requestCameraControlBuild(requestBuilder,true)
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
        connection = RtmpConnection()
        stream = RtmpStream(connection)
        videoSource = Camera2Source(context).apply {
            open(CameraCharacteristics.LENS_FACING_BACK)
        }
        videoSource.listener = this@RtmpClient3
        stream.attachVideo(videoSource)
        surfaceView.attachStream(stream)

        connection.addEventListener(Event.RTMP_STATUS, this@RtmpClient3)
    }

    fun start(
        info: CameraInfoModel, key: String?,
        isCameraOpenResult: (Boolean) -> Unit
    ) {
        Log.d(TAG, "kael start called publishing:$isPublishing camera:$isCameraOpen : $info ")
        if (currentCameraInfo == null) currentCameraInfo = info
        val url = "rtmp://141.11.184.69/live/$key"
//        val url = "rtmp://192.168.126.131/live/$key"
        handleStartOrUpdate(info, url)
        CoroutineScope(Dispatchers.Main).launch {
            delay(2000)
            isCameraOpenResult(isCameraOpen)
        }

    }


    private fun handleStartOrUpdate(info: CameraInfoModel, url: String) {
        if (currentCameraInfo?.fps != info.fps || currentCameraInfo?.bitrate != info.bitrate
            || currentCameraInfo?.width != info.width || currentCameraInfo?.height != info.height
            || currentCameraInfo?.orientation != info.orientation) {

            stopPublishing()
            CoroutineScope(Dispatchers.Main).launch {
                delay(1000)
                startPublishing(info, url)
            }

        } else {
            if (!isPublishing){
                stopPublishing()
                CoroutineScope(Dispatchers.Main).launch {
                   delay(1000)
                    startPublishing(info,url)
                    isPublishing = true
                }
            }else{
                updatePublishing(info)
            }
        }

        currentCameraInfo = info

    }

    private fun updatePublishing(info: CameraInfoModel) {
        try {

//            rotateCameraPreview(session,surfaceView,info.orientation)
            getCameraController()?.updateCameraInfo(info)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }



    private fun startPublishing(info: CameraInfoModel, url: String) {
        try {
            stream.videoSetting.width = info.width // The width resoulution of video output.
            stream.videoSetting.height = info.height // The height resoulution of video output.
            stream.videoSetting.bitRate = info.bitrate // The bitRate of video output.
            stream.videoSetting.frameRate = if (info.fps<15) 15 else info.fps
            stream.videoSetting.IFrameInterval = 2

            surfaceView.attachStream(stream)
            connection.connect(url)
            stream.publish(url.split("live/")[1])
            try {
                CoroutineScope(Dispatchers.Main).launch {
                    delay(2000)
                    val cameraManagerField: Field =
                        Camera2Source::class.java.getDeclaredField("manager")
                    cameraManagerField.isAccessible = true
                    cameraManager = cameraManagerField.get(videoSource) as CameraManager

                    val cameraSessionField: Field =
                        Camera2Source::class.java.getDeclaredField("session")
                    cameraSessionField.isAccessible = true
                    delay(1000)
                    session = cameraSessionField.get(videoSource) as CameraCaptureSession
                    Log.d(TAG, "onCreate: $session")
                    updatePublishing(info)
                    isPublishing = true
                }

            } catch (e: NoSuchFieldException) {
                Log.d(TAG, "onCreate: ${e.message}")
                e.printStackTrace()
            } catch (e: IllegalAccessException) {
                Log.d(TAG, "onCreate: ${e.message}")
                e.printStackTrace()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    fun rotateCameraPreview(session: CameraCaptureSession, surfaceView: HkSurfaceView, degrees: Int) {
        try {
//            requestBuilder.addTarget(surfaceView.holder.surface)
//            requestBuilder.set(CaptureRequest.JPEG_ORIENTATION, degrees)
//            session.setRepeatingRequest(requestBuilder.build(), null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopPublishing() {
        try {
            isPublishing = false
            connection.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCreateCaptureRequest(builder: Builder) {
        this.requestBuilder = builder
           requestCameraControlBuild(builder,false)
    }

    private fun requestCameraControlBuild(builder: Builder,refresh:Boolean) {

        try {
            if (!refresh){
                cameraController = CameraController(0.toString(),cameraManager,session,builder,surfaceView)
                isCameraOpen = true
            }else{
                CoroutineScope(Dispatchers.Main).launch {
                        try {
                            CoroutineScope(Dispatchers.Main).launch {
                                delay(2000)
                                val cameraManagerField: Field =
                                    Camera2Source::class.java.getDeclaredField("manager")
                                cameraManagerField.isAccessible = true
                                cameraManager = cameraManagerField.get(videoSource) as CameraManager

                                val cameraSessionField: Field =
                                    Camera2Source::class.java.getDeclaredField("session")
                                cameraSessionField.isAccessible = true
                                session = cameraSessionField.get(videoSource) as CameraCaptureSession
                                Log.d(TAG, "onCreate: $session")
                                cameraController = CameraController(0.toString(),cameraManager,session,requestBuilder,
                                    surfaceView)
                                isCameraOpen = true
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
            e.printStackTrace()
        }
    }

    override fun onError(camera: CameraDevice, error: Int) {
        isCameraOpen = false
    }

    override fun handleEvent(event: Event) {
        Log.d(TAG, "handleEvent: $event")

    }
}

