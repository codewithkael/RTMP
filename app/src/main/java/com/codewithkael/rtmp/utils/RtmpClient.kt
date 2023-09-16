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
            setAudioCodecParam(
                NodePublisher.NMC_CODEC_ID_AAC,
                NodePublisher.NMC_PROFILE_AUTO,
                48000,  // Sample rate (Hz)
                2,      // Number of channels (Stereo)
                8_000 // Bit rate (bps)
            )

            setVideoOrientation(NodePublisher.VIDEO_ORIENTATION_PORTRAIT)

            setVideoCodecParam(
                NodePublisher.NMC_CODEC_ID_H264,
                NodePublisher.NMC_PROFILE_AUTO,
                320,   // Width (pixels)
                480,   // Height (pixels)
                30,     // Frame rate (fps)
                1_000_000 // Bit rate (bps)
            )
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