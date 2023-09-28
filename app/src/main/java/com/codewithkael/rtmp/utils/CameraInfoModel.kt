package com.codewithkael.rtmp.utils

data class CameraInfoModel(

    //switch camera, is front or back
    val frontCamera:Boolean = false,

    //only if front camera is off
    val flashLight:Boolean = false,

    //VIDEO_ORIENTATION_PORTRAIT = 0
    //VIDEO_ORIENTATION_LANDSCAPE_RIGHT = 1
    //VIDEO_ORIENTATION_LANDSCAPE_LEFT = 3
    val orientation:Int = 0,

    //1 to 8
    val zoomLevel:Int=1,

    //-20 to 20
    val iso:Int=0,

    val width:Int=720,
    val height:Int=1080,
    val fps:Int=30,
    val bitrate:Int=2500000,

    //normalizedX – center X of the region in current normalized coordinate system. (ranging from 0 to 1).
    // normalizedY – center Y of the region in current normalized coordinate system. (ranging from 0 to 1).
    // size – size of the MeteringPoint width and height (ranging from 0 to 1). It is the percentage of the
    // sensor width/height (or crop region width/height if crop region is set).
    val normalizedX:Float = 0f,
    val normalizedY:Float = 0f,
    val size:Float = 0f,

    )
