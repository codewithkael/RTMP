//package com.codewithkael.rtmp.utils
//
//import android.content.Context
//import android.util.Log
//import android.widget.FrameLayout
//import androidx.camera.core.CameraState
//import androidx.camera.core.FocusMeteringAction
//import cn.nodemedia.CameraStateListener
//import cn.nodemedia.NodePublisher
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//import javax.inject.Singleton
//
//@Singleton
//class RtmpClient constructor(
//    private val context: Context
//) : CameraStateListener {
//
//    private val TAG = "MainService-rtmpClient"
//    private var nodePublisher: NodePublisher? = null
//    private var currentCameraInfo: CameraInfoModel? = null
//    private var isCameraOpen = false
//    private var isPublishing = false
//
//    init {
//        nodePublisher = getNodePublisher()
//        setPublisherListener()
//    }
//
//    private fun removePublisherListeners() {
//        nodePublisher?.setOnNodePublisherEventListener(null)
//        nodePublisher?.clearCameraListeners()
//    }
//
//    private fun setPublisherListener() {
//        nodePublisher?.setOnNodePublisherEventListener { _, event, msg ->
//            Log.d(TAG, "event: $event   message: $msg")
//            if (event == 2001) {
//                nodePublisher?.isAttachedNow()
//            }
//            isPublishing = event == 2001
//        }
//        nodePublisher?.setCameraStateListener(this@RtmpClient)
//
//    }
//
//    private fun getNodePublisher(): NodePublisher {
//        return NodePublisher(context, "")
//    }
//
//    fun startStreaming(info: CameraInfoModel, key: String?, frameLayout: FrameLayout,
//                       isCameraOpenResult:(Boolean)->Unit) {
//        Log.d(TAG, "startStreaming: start stream called $info")
//        val url = "rtmp://141.11.184.69/live/$key"
//        updateCameraStats(info, url, frameLayout)
//        CoroutineScope(Dispatchers.IO).launch {
//            delay(2000)
//            withContext(Dispatchers.Main){
//                isCameraOpenResult(isCameraOpen)
//            }
//        }
//    }
////
////    fun startStreaming(info: CameraInfoModel, key: String?, frameLayout: FrameLayout) {
////        Log.d(TAG, "startStreaming: start stream called $info")
////        val url = "rtmp://141.11.184.69/live/$key"
////        updateCameraStats(info, url, frameLayout)
////    }
//
//    private fun updateCameraStats(info: CameraInfoModel, url: String, frameLayout: FrameLayout) {
//        if (currentCameraInfo == null) currentCameraInfo = info
//        if (currentCameraInfo?.fps != info.fps || currentCameraInfo?.bitrate != info.bitrate
//            || currentCameraInfo?.width != info.width || currentCameraInfo?.height != info.height
//            || currentCameraInfo?.orientation != info.orientation) {
//
//            if (isPublishing) {
//                CoroutineScope(Dispatchers.Main).launch {
//                    nodePublisher?.stopNow()
//                    nodePublisher?.detachView()
//                    isCameraOpen = false
//
//                    removePublisherListeners()
//                    nodePublisher = null
//                    nodePublisher = getNodePublisher()
//                    setPublisherListener()
//                    updateCamera(frameLayout, info, url)
//                    updateCameraControl(info)
//                }
//
//            }
//        } else {
//            Log.d(TAG, "updateCameraStats: 3 $isPublishing  camera:$isCameraOpen")
//            if (!isPublishing) {
//                updateCamera(frameLayout, info, url)
//            } else {
//                if (!isCameraOpen) {
//                    CoroutineScope(Dispatchers.Main).launch {
//                        nodePublisher?.stopNow()
//                        nodePublisher?.detachView()
//
//                        removePublisherListeners()
//                        nodePublisher = null
//                        nodePublisher = getNodePublisher()
//                        setPublisherListener()
//                        updateCamera(frameLayout, info, url)
//                        updateCameraControl(info)
//                    }
//                }
//            }
//            updateCameraControl(info)
//        }
//    }
//
//    private fun updateCamera(frameLayout: FrameLayout, info: CameraInfoModel, url: String) {
//        try {
//            nodePublisher?.apply {
//                attachView(frameLayout)
//                setVideoOrientation(info.orientation)
//                setVideoCodecParam(
//                    NodePublisher.NMC_CODEC_ID_H264,
//                    NodePublisher.NMC_PROFILE_AUTO,
//                    info.width,   // Width (pixels)
//                    info.height,   // Height (pixels)
//                    if (info.fps < 15) 15 else info.fps,
//                    info.bitrate // Bit rate (bps)
//                )
//                if (!isCameraOpen) {
//                    openCamera(info.frontCamera)
//                    isCameraOpen = true
//                }
//                start(url)
//            }
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
//    }
//
//    private fun updateCameraControl(info: CameraInfoModel) {
//        CoroutineScope(Dispatchers.Main).launch {
//
//            delay(500)
//            try {
//                nodePublisher?.camera?.cameraControl?.let { cameraControl ->
//                    val point =
//                        nodePublisher?.createPoint(info.normalizedX, info.normalizedY, info.size)
//                    val action = point?.let { FocusMeteringAction.Builder(it).build() }
//                    action?.let {
//                        cameraControl.startFocusAndMetering(it)
//                    }
//
//                    if (currentCameraInfo?.frontCamera != info.frontCamera) {
//                        nodePublisher?.switchCamera()
//                    }
//
//                    cameraControl.setZoomRatio(info.zoomLevel.toFloat())
//                    cameraControl.setExposureCompensationIndex(info.iso)
//                    currentCameraInfo = info
//                    //                    CoroutineScope(Dispatchers.Main).launch {
//                    //                        delay(1000)
//                    //                        if (info.flashLight &&!info.frontCamera){
//                    //                            cameraControl.enableTorch(true)
//                    //                        }
//                    //                    }
//                }
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//        }
//    }
//
//
//    fun onDestroy() {
//        nodePublisher?.stop()
//    }
//
//    override fun onCameraStateChanged(cameraState: CameraState?) {
//        isCameraOpen = cameraState?.type == CameraState.Type.OPEN
//    }
//
//}