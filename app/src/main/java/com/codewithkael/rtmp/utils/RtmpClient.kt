package com.codewithkael.rtmp.utils

import android.content.Context
import android.util.Log
import android.widget.FrameLayout
import cn.nodemedia.NodePublisher
import javax.inject.Singleton

@Singleton
class RtmpClient constructor(
    private val context: Context,
    private val frameLayout: FrameLayout
) {

    private var nodePublisher : NodePublisher?=null

    init {
        nodePublisher = getNodePublisher()
    }

    private fun getNodePublisher(): NodePublisher {
        return NodePublisher(context, "").apply {
            attachView(frameLayout)
            setVideoOrientation(NodePublisher.VIDEO_ORIENTATION_PORTRAIT)
            setVideoCodecParam(
                NodePublisher.NMC_CODEC_ID_H264,
                NodePublisher.NMC_PROFILE_AUTO,
                720,   // Width (pixels)
                1080,   // Height (pixels)
                30,     // Frame rate (fps)
                2_500_000 // Bit rate (bps)
            )
//            setKeyFrameInterval(1000)
//            setVideoRateControl(1)
            openCamera(true)
        }
    }

    fun startStreaming(key: String?) {
        Log.d("TAG", "startStreaming: key here $key")
        nodePublisher?.start("rtmp://141.11.184.69/live/$key")
    }

    fun onDestroy(){
        nodePublisher?.stop()
    }

}