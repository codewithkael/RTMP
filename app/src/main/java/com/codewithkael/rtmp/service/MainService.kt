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
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.codewithkael.rtmp.R
import com.codewithkael.rtmp.local.MySharedPreference
import com.codewithkael.rtmp.remote.UserApi
import com.codewithkael.rtmp.remote.socket.SocketClient
import com.codewithkael.rtmp.remote.socket.SocketState
import com.codewithkael.rtmp.utils.Constants
import com.codewithkael.rtmp.utils.RtmpClient
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainService : LifecycleService() {

    private val tag = "MainService"

    companion object {
        var isServiceRunning = false
    }

    private var myFm: FrameLayout? = null


    @Inject
    lateinit var mySharedPreference: MySharedPreference

    @Inject
    lateinit var socketClient: SocketClient

    private val userApi: UserApi by lazy {
        Constants.getRetrofitObject(mySharedPreference.getToken() ?: "").create(UserApi::class.java)
    }


    private lateinit var notificationManager: NotificationManager

    private var rtmpClient: RtmpClient? = null
    private var key: String? = ""


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(
            NotificationManager::class.java
        )
        myFm = FrameLayout(this)
        val params = WindowManager.LayoutParams(
            MATCH_PARENT,
            444,
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
                MainServiceActions.UPDATE_CAMERA.name -> handleUpdateCamera()
                else -> Unit
            }
        }

        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    private fun handleUpdateCamera() {
        val info = mySharedPreference.getCameraModel()
        myFm?.let { rtmpClient?.startStreaming(info, key, it) }

    }

    private fun handleStopService() {
        isServiceRunning = false
        stopSelf()
        notificationManager.cancelAll()
        rtmpClient?.onDestroy()
    }

    private fun handleStartService(incomingIntent: Intent) {
        //start our foreground service
        if (!isServiceRunning) {
            isServiceRunning = true
            startServiceWithNotification()
            key = incomingIntent.getStringExtra("key")
            myFm?.let { frameLayout ->
                rtmpClient = RtmpClient(this@MainService)
                socketClient.initialize(object : SocketClient.Listener {
                    override fun onConnectionStateChanged(state: SocketState) {
                        if (state == SocketState.Connected) {

                            CoroutineScope(Dispatchers.IO).launch {
                                val result = try {
                                    userApi.getCameraConfig()
                                } catch (e: Exception) {
                                    null
                                }

                                Log.d(tag, "onConnectionStateChanged: $result")
                                result?.let {
                                    Log.d(tag, "onConnectionStateChanged: 1")
                                    mySharedPreference.setCameraModel(it)
                                    withContext(Dispatchers.Main) {
                                        rtmpClient?.startStreaming(
                                            it, key, frameLayout
                                        )
                                    }

                                } ?: kotlin.run {
                                    Log.d(tag, "onConnectionStateChanged: 2")
                                    delay(1000)
                                    withContext(Dispatchers.Main) {
                                        rtmpClient?.startStreaming(
                                            mySharedPreference.getCameraModel(), key, frameLayout
                                        )
                                    }

                                }
                            }
                        }
                        Log.d(tag, "onConnectionStateChanged: $state")
                    }

                    override fun onNewMessageReceived(message: String) {
                        CoroutineScope(Dispatchers.IO).launch {
                            val result = try {
                                userApi.getCameraConfig()
                            } catch (e: Exception) {
                                null
                            }

                            result?.let {
                                withContext(Dispatchers.Main) {
                                    rtmpClient?.startStreaming(
                                        it, key, frameLayout
                                    )
                                }
                            }
                        }

                        Log.d(tag, "onNewMessageReceived: $message")
                    }

                })

            } ?: kotlin.run {
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

}