package com.codewithkael.rtmp.utils

import android.util.Log
import com.github.faucamp.simplertmp.RtmpHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.ossrs.yasea.SrsCameraView
import net.ossrs.yasea.SrsEncodeHandler
import net.ossrs.yasea.SrsPublisher
import net.ossrs.yasea.SrsRecordHandler
import java.io.IOException
import java.net.SocketException
import javax.inject.Singleton

@Singleton
class RtmpClient2 constructor(
    private val srsCameraView: SrsCameraView
) : SrsEncodeHandler.SrsEncodeListener, RtmpHandler.RtmpListener,
    SrsRecordHandler.SrsRecordListener {

    private val TAG = "RtmpClient2"

    private val mPublisher: SrsPublisher
    private var isCameraOpen = false
    private var isPublishing = false
    private var currentCameraInfo: CameraInfoModel? = null

    private fun getSrsPublisher(srsCameraView: SrsCameraView): SrsPublisher {
        return SrsPublisher(srsCameraView)
    }


    init {
        mPublisher = getSrsPublisher(srsCameraView)
    }

    fun start(
        info: CameraInfoModel, key: String?,
        isCameraOpenResult: (Boolean) -> Unit
    ) {
        if (currentCameraInfo == null) currentCameraInfo = info

        Log.d(TAG, "kael start called : $info")
        val url = "rtmp://141.11.184.69/live/$key"
        handleStartOrUpdate(info, url)
        CoroutineScope(Dispatchers.IO).launch {
            delay(3000)
            withContext(Dispatchers.Main) {
//                isCameraOpenResult(isPublishing)
//                isCameraOpenResult(isCameraOpen)
                Log.d(TAG, "kael start: $isPublishing resul $")

            }
        }

    }


    private fun handleStartOrUpdate(info: CameraInfoModel, url: String) {
        startPublishing(info, url)
//        if (currentCameraInfo?.fps != info.fps || currentCameraInfo?.bitrate != info.bitrate
//            || currentCameraInfo?.width != info.width || currentCameraInfo?.height != info.height
//            || currentCameraInfo?.orientation != info.orientation) {
//
//            startPublishing(info, url)
//
//        } else {
//            if (!isPublishing) {
//                startPublishing(info, url)
//            } else {
//                updatePublishing(info)
//            }
//        }
//
//        currentCameraInfo = info

    }

    private fun updatePublishing(info: CameraInfoModel) {

        try {
            val params = mPublisher.camera.parameters
            params.apply {
                zoom = info.zoomLevel
            }
            mPublisher.camera.parameters = params
            mPublisher.setPreviewResolution(info.width, info.height)
            mPublisher.setOutputResolution(info.height, info.width)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startPublishing(info: CameraInfoModel, url: String) {
        stopPublishing()
        try {
            mPublisher.setEncodeHandler(SrsEncodeHandler(this@RtmpClient2))
            mPublisher.setRtmpHandler(RtmpHandler(this@RtmpClient2))
            mPublisher.setRecordHandler(SrsRecordHandler(this@RtmpClient2))
            mPublisher.setVideoHDMode()

            mPublisher.setPreviewResolution(info.width, info.height)
            mPublisher.setOutputResolution(info.height, info.width)
            mPublisher.startCamera()
            val params = mPublisher.camera.parameters
            params.apply {
                zoom = info.zoomLevel
                exposureCompensation
            }
            mPublisher.camera.parameters = params
            mPublisher.startPublish(url)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        CoroutineScope(Dispatchers.Main).launch {
//            stopPublishing()
//            delay(2000)

        }

    }

    private fun stopPublishing() {
        try {
            mPublisher.stopCamera()
            mPublisher.stopPublish()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onNetworkWeak() {
        Log.d(TAG, "1onNetworkWeak: ")
    }

    override fun onNetworkResume() {
        Log.d(TAG, "2onNetworkResume: ")
    }

    override fun onEncodeIllegalArgumentException(e: IllegalArgumentException?) {
        Log.d(TAG, "3onEncodeIllegalArgumentException: ")
    }

    override fun onRtmpConnecting(msg: String?) {
        Log.d(TAG, "4onRtmpConnecting: ")
        isPublishing = false
    }

    override fun onRtmpConnected(msg: String?) {
        Log.d(TAG, "5onRtmpConnected: $msg")
        isPublishing = true
    }

    override fun onRtmpVideoStreaming() {
//        Log.d(TAG, "onRtmpVideoStreaming: ")
    }

    override fun onRtmpAudioStreaming() {
        Log.d(TAG, "onRtmpAudioStreaming: ")
    }

    override fun onRtmpStopped() {
        Log.d(TAG, "onRtmpStopped: ")
        isPublishing = false

    }

    override fun onRtmpDisconnected() {
        Log.d(TAG, "onRtmpDisconnected: ")
        isPublishing = false

    }

    override fun onRtmpVideoFpsChanged(fps: Double) {
    }

    override fun onRtmpVideoBitrateChanged(bitrate: Double) {
    }

    override fun onRtmpAudioBitrateChanged(bitrate: Double) {
    }

    override fun onRtmpSocketException(e: SocketException?) {
        Log.d(TAG, "onRtmpSocketException: ")
    }

    override fun onRtmpIOException(e: IOException?) {
        Log.d(TAG, "onRtmpIOException: ")
    }

    override fun onRtmpIllegalArgumentException(e: IllegalArgumentException?) {
        Log.d(TAG, "onRtmpIllegalArgumentException: ")
    }

    override fun onRtmpIllegalStateException(e: IllegalStateException?) {
    }

    override fun onRecordPause() {
    }

    override fun onRecordResume() {
    }

    override fun onRecordStarted(msg: String?) {
    }

    override fun onRecordFinished(msg: String?) {
    }

    override fun onRecordIllegalArgumentException(e: IllegalArgumentException?) {
    }

    override fun onRecordIOException(e: IOException?) {
    }

}

