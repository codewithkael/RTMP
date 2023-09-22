package com.codewithkael.rtmp.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.codewithkael.rtmp.R
import com.codewithkael.rtmp.local.MySharedPreference
import com.codewithkael.rtmp.utils.CameraInfoModel
import com.codewithkael.rtmp.utils.RtmpClient
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainService : LifecycleService(), FrameLayoutProvider {

    companion object {
        @SuppressLint("StaticFieldLeak")
        var myFm: FrameLayout? = null
    }

    @Inject lateinit var mySharedPreference: MySharedPreference

    private var isServiceRunning = false
    private lateinit var notificationManager: NotificationManager

    private var rtmpClient: RtmpClient? = null
    private var key:String?=""


    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(
            NotificationManager::class.java
        )
//        myFm = FrameLayout(this)
//        val params = WindowManager.LayoutParams(
//            444,
//            444,
//            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, // This type is suitable for overlays
//            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
//            PixelFormat.TRANSLUCENT
//        )
//        params.gravity = Gravity.TOP or Gravity.START
//        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
//        windowManager.addView(myFm, params)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { incomingIntent ->
            when (incomingIntent.action) {
                MainServiceActions.START_SERVICE.name -> handleStartService(intent)
                MainServiceActions.STOP_SERVICE.name -> handleStopService()
                MainServiceActions.UPDATE_CAMERA.name -> handleUpdateCamera()
                else -> Unit
            }
        }

        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    private fun handleUpdateCamera() {
        val info = mySharedPreference.getCameraModel()
        myFm?.let { rtmpClient?.startStreaming(info,key, it) }

    }

    private fun handleStopService() {
        stopSelf()
        notificationManager.cancelAll()
        rtmpClient?.onDestroy()
    }

    private fun handleToggleVideo(incomingIntent: Intent) {
        val shouldBeMuted = incomingIntent.getBooleanExtra("shouldBeMuted", true)

    }

    private fun handleToggleAudio(incomingIntent: Intent) {
        val shouldBeMuted = incomingIntent.getBooleanExtra("shouldBeMuted", true)
    }

    private fun handleSwitchCamera() {
    }

    private fun handleEndCall() {

    }

    private fun endCallAndRestartRepository() {
    }

    private fun handleSetupViews(incomingIntent: Intent) {

    }

    private fun handleStartService(incomingIntent: Intent) {
        //start our foreground service
        if (!isServiceRunning) {
            isServiceRunning = true
            startServiceWithNotification()
            key = incomingIntent.getStringExtra("key")
            myFm?.let {
                Log.d("TAG", "handleStartService: 1")
                rtmpClient = RtmpClient(this@MainService, it)
                rtmpClient?.startStreaming(mySharedPreference.getCameraModel(),key,it)
//                rtmpClient?.startStreaming(key)
//                CoroutineScope(Dispatchers.Main).launch {
//                    rtmpClient?.startStreaming(
//                        CameraInfoModel(
//                            zoomLevel = 1, exposure = 0, width = 1080, height = 1920,fps = 30, bitrate = 2500000,
//                            normalizedX = 1f , normalizedY = 1f, size = 1f , frontCamera = false, flashLight = true
//                        ),key,it)
//                    delay(8000)
//                    rtmpClient?.startStreaming(
//                        CameraInfoModel(
//                            zoomLevel = 1, exposure = 0, width = 1080, height = 1920,fps = 30, bitrate = 2500000,
//                            normalizedX = 0f , normalizedY = 0f, size = 0f , frontCamera = false,flashLight = true
//                        ),key,it)
//                    delay(8000)
//                    rtmpClient?.startStreaming(
//                        CameraInfoModel(
//                            zoomLevel = 1, exposure = 0, width = 1080, height = 1920,fps = 30, bitrate = 2500000,
//                            normalizedX = 1f , normalizedY = 1f, size = 1f , frontCamera = true,flashLight = true
//                        ),key,it)
//                    delay(8000)
//                    rtmpClient?.startStreaming(
//                        CameraInfoModel(
//                            zoomLevel = 1, exposure = 0, width = 1080, height = 1920,fps = 30, bitrate = 2500000,
//                            normalizedX = 0f , normalizedY = 0f, size = 0f , frontCamera = false,flashLight = true
//                        ),key,it)
////                    delay(8000)
////                    rtmpClient?.startStreaming(
////                        CameraInfoModel(
////                        zoomLevel = 1f , exposure = 20, width = 620, height = 840,fps = 10, bitrate = 2500000
////                    ),key,it)
////                    delay(4000)
////                    rtmpClient?.startStreaming(
////                        CameraInfoModel(
////                            zoomLevel = 1f, exposure = -20, width = 320, height = 480,fps = 30, bitrate = 2500000
////                        ),key,it)
//                }
            } ?: kotlin.run {
                Log.d("TAG", "handleStartService: 2")
                handleStopService()
            }
        }
    }

    private fun startServiceWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                "channel1", "foreground", NotificationManager.IMPORTANCE_HIGH
            )

            val intent = Intent(this, MainServiceReceiver::class.java).apply {
                action = "ACTION_EXIT"
            }
            val pendingIntent: PendingIntent =
                PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

            notificationManager.createNotificationChannel(notificationChannel)
            val notification = NotificationCompat.Builder(
                this, "channel1"
            ).setSmallIcon(R.mipmap.ic_launcher)
                .addAction(R.drawable.ic_end_call, "Exit", pendingIntent)

            startForeground(1, notification.build())
        }
    }

    override fun getFrameLayout(): FrameLayout? {
        return myFm
    }
}