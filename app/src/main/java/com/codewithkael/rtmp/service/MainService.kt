package com.codewithkael.rtmp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.codewithkael.rtmp.R
import com.codewithkael.rtmp.utils.RtmpClient
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainService : LifecycleService(), FrameLayoutProvider {
    private var myFm: FrameLayout? = null

    private var isServiceRunning = false
    private lateinit var notificationManager: NotificationManager

    private var rtmpClient: RtmpClient? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(
            NotificationManager::class.java
        )
        myFm = FrameLayout(this)
        val params = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, // This type is suitable for overlays
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.addView(myFm, params)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { incomingIntent ->
            when (incomingIntent.action) {
                MainServiceActions.START_SERVICE.name -> handleStartService(intent)
                MainServiceActions.STOP_SERVICE.name -> handleStopService()
                else -> Unit
            }
        }

        super.onStartCommand(intent, flags, startId)
        return START_STICKY
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
            val key = incomingIntent.getStringExtra("key")
            myFm?.let {
                Log.d("TAG", "handleStartService: 1")
                rtmpClient = RtmpClient(this@MainService, it)
                rtmpClient?.startStreaming(key)
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