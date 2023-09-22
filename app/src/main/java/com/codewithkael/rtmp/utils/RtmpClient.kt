package com.codewithkael.rtmp.utils

import android.content.Context
import android.util.Log
import android.util.Rational
import android.widget.FrameLayout
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.MeteringPoint
import androidx.camera.core.MeteringPointFactory
import cn.nodemedia.NodePublisher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Singleton

@Singleton
class RtmpClient constructor(
    private val context: Context, private val frameLayout: FrameLayout
) {

    private val TAG = "RtmpClient"
    private var nodePublisher: NodePublisher? = null
    private var currentCameraInfo: CameraInfoModel? = null
    private var isCameraOpen = false
    private var isPublishing = false

    init {
        nodePublisher = getNodePublisher()
        setPublisherListener()
    }

    private fun setPublisherListener(){
        nodePublisher?.setOnNodePublisherEventListener { _, event, msg ->
            Log.d(TAG, "event: $event   message: $msg")
            if (event == 2001) {
                nodePublisher?.isAttachedNow()
            }
            isPublishing = event == 2001
        }
    }

    private fun getNodePublisher(): NodePublisher {
        return NodePublisher(context, "").apply {
            attachView(frameLayout)
        }
    }

    fun startStreaming(info: CameraInfoModel, key: String?, frameLayout: FrameLayout) {
        Log.d(TAG, "startStreaming: start stream called $info")
        val url = "rtmp://141.11.184.69/live/$key"
        updateCameraStats(info, url, frameLayout)
//        if (!isPublishing){
//            nodePublisher?.openCamera(info.frontCamera)
//            isCameraOpen = true
//        }
//
//        Log.d("TAG", "startStreaming: key here $key")
//        nodePublisher?.start(url)

    }

    private fun updateCameraStats(info: CameraInfoModel, url: String, frameLayout: FrameLayout) {
        if (currentCameraInfo == null) currentCameraInfo = info
        if (currentCameraInfo?.fps != info.fps || currentCameraInfo?.bitrate != info.bitrate
            || currentCameraInfo?.width != info.width || currentCameraInfo?.height != info.height
            || currentCameraInfo?.orientation != info.orientation) {

            if (isPublishing) {
                CoroutineScope(Dispatchers.Main).launch {
                    nodePublisher?.stopNow()
                    nodePublisher?.detachView()
                    isCameraOpen = false

                    nodePublisher = null
                    nodePublisher = getNodePublisher()
                    setPublisherListener()

                    nodePublisher?.apply {

                        attachView(frameLayout)
                        setVideoOrientation(info.orientation)
                        setVideoCodecParam(
                            NodePublisher.NMC_CODEC_ID_H264,
                            NodePublisher.NMC_PROFILE_AUTO,
                            info.width,   // Width (pixels)
                            info.height,   // Height (pixels)
                            info.fps,     // Frame rate (fps)
                            info.bitrate // Bit rate (bps)
                        )
                        openCamera(info.frontCamera)
                        isCameraOpen = true
                        start(url)
                    }
                    delay(500)
                    nodePublisher?.camera?.cameraControl?.let { cameraControl ->
                        cameraControl.setZoomRatio(info.zoomLevel.toFloat())
                        cameraControl.setExposureCompensationIndex(info.exposure)
                    }
                }

            }


        } else {
            Log.d(TAG, "updateCameraStats: 3 $isPublishing")

            if (!isPublishing) {
                nodePublisher?.apply {
                    attachView(frameLayout)
                    setVideoOrientation(info.orientation)
                    setVideoCodecParam(
                        NodePublisher.NMC_CODEC_ID_H264,
                        NodePublisher.NMC_PROFILE_AUTO,
                        info.width,   // Width (pixels)
                        info.height,   // Height (pixels)
                        info.fps,     // Frame rate (fps)
                        info.bitrate // Bit rate (bps)
                    )
                    if (!isCameraOpen) {
                        openCamera(info.frontCamera)
                        isCameraOpen = true
                    }
                }
                nodePublisher?.start(url)

            }
            CoroutineScope(Dispatchers.Main).launch {

                delay(500)
                nodePublisher?.camera?.cameraControl?.let { cameraControl ->
                    val point = nodePublisher?.createPoint(info.normalizedX,info.normalizedY,info.size)
                    val action = point?.let { FocusMeteringAction.Builder(it).build() }
                    action?.let {
                        cameraControl.startFocusAndMetering(it)
                    }
                    Log.d(TAG, "updateCameraStats: updateCameraState 1 ${currentCameraInfo?.frontCamera}")
                    Log.d(TAG, "updateCameraStats: updateCameraState 2 ${info.frontCamera}")
                    Log.d(TAG, "updateCameraStats: updateCameraState 3 ${!info.frontCamera && info.flashLight}")

                    if (currentCameraInfo?.frontCamera!=info.frontCamera){
                        nodePublisher?.switchCamera()
                    }

                    cameraControl.setZoomRatio(info.zoomLevel.toFloat())
                    cameraControl.setExposureCompensationIndex(info.exposure)
                    currentCameraInfo = info
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(1000)
                        if (info.flashLight &&!info.frontCamera){
                            cameraControl.enableTorch(true)
                        }
                    }
                }
            }
        }


    }


    fun onDestroy() {
        nodePublisher?.stop()
    }

}