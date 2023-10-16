package com.codewithkael.rtmp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.codewithkael.rtmp.R
import com.codewithkael.rtmp.local.MySharedPreference
import com.codewithkael.rtmp.remote.UserApi
import com.codewithkael.rtmp.remote.socket.SocketClient
import com.codewithkael.rtmp.remote.socket.SocketState
import com.codewithkael.rtmp.ui.main.MainActivity
import com.codewithkael.rtmp.utils.CameraInfoModel
import com.codewithkael.rtmp.utils.Constants
import com.codewithkael.rtmp.utils.RtmpClient
import com.haishinkit.view.HkSurfaceView
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
        var isUiActive = true
        var listener: Listener? = null
    }

    private lateinit var notificationBuilder: NotificationCompat.Builder


    private var surface: HkSurfaceView? = null


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


    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(
            NotificationManager::class.java
        )
         val notificationChannel = NotificationChannel(
            "channel1", "foreground", NotificationManager.IMPORTANCE_HIGH
        )

         val intent = Intent(this, MainServiceReceiver::class.java).apply {
            action = "ACTION_EXIT"
        }
         val pendingIntent: PendingIntent =
            PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        notificationManager.createNotificationChannel(notificationChannel)
        notificationBuilder = NotificationCompat.Builder(
            this, "channel1"
        ).setSmallIcon(R.mipmap.ic_launcher)
            .addAction(R.drawable.ic_end_call, "Exit", pendingIntent)

        surface = HkSurfaceView(this)
        val params = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, // This type is suitable for overlays
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.addView(surface, params)
        surface?.keepScreenOn = true
//        surface?.setRotation(100f)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        intent?.let { incomingIntent ->
            when (incomingIntent.action) {
                MainServiceActions.START_SERVICE.name -> handleStartService(intent)
                MainServiceActions.STOP_SERVICE.name -> handleStopService()
//                MainServiceActions.UPDATE_CAMERA.name -> handleUpdateCamera()
                else -> Unit
            }
        }

        return START_STICKY
    }

    private fun handleUpdateCamera() {
        startServiceWithNotification()
        val info = mySharedPreference.getCameraModel()
        rtmpClient?.start(info, key) {
            if (!isUiActive && !it) {
                openAppReopenCamera(info, key)
                Log.d(tag, "1onNewMessageReceived: camera is opened $it")
            }
        }

    }

    private fun openAppReopenCamera(info: CameraInfoModel, key: String?) {
        startActivity(Intent(this@MainService, MainActivity::class.java).apply {
            addFlags(FLAG_ACTIVITY_NEW_TASK)
        })
        rtmpClient?.start(info, key) {
            if (it) {
                listener?.cameraOpenedSuccessfully()
            }
        }
    }

    private fun handleStopService() {
        startServiceWithNotification()
        isServiceRunning = false
        socketClient.unregisterClients()
        socketClient.closeSocket()
        rtmpClient?.stop()
        stopSelf()
        stopForeground(true)
        notificationManager.cancelAll()
//        rtmpClient?.onDestroy()
    }

    private fun handleStartService(incomingIntent: Intent) {
        //start our foreground service
        if (!isServiceRunning) {
            isServiceRunning = true
            startServiceWithNotification()
            key = incomingIntent.getStringExtra("key")
            surface?.let { srf ->
                rtmpClient = RtmpClient(this@MainService, srf,userApi)
//                val baseModel = CameraInfoModel()
//                CoroutineScope(Dispatchers.Main).launch {
//                    val totalDelay = 10000L
//
//                    rtmpClient?.start(baseModel.copy(orientation = 90),key){}
//                    delay(totalDelay)
//
//                    rtmpClient?.start(baseModel.copy(shutterSpeed = ExposureMode.EXPOSURE_1_2000,
//                        orientation = 180),key){}
//                    delay(totalDelay)
//
//                    rtmpClient?.start(baseModel.copy(shutterSpeed = ExposureMode.EXPOSURE_1),key){}
//                    delay(totalDelay)
//
//                    rtmpClient?.start(baseModel.copy(shutterSpeed = ExposureMode.EXPOSURE_1_4000),key){}
//                    delay(totalDelay)
//
//                    rtmpClient?.start(baseModel.copy(shutterSpeed = ExposureMode.EXPOSURE_15),key){}
//                    delay(totalDelay)
//
//                    rtmpClient?.start(baseModel.copy(shutterSpeed = ExposureMode.EXPOSURE_1_4000),key){}
//                    delay(totalDelay)
//
//                    rtmpClient?.start(baseModel.copy(shutterSpeed = ExposureMode.EXPOSURE_30),key){}
//                    delay(totalDelay)
//
//
//                    rtmpClient?.start(baseModel.copy(flashLight = false,
//                        focusPercent = 0.1f),key){}
//                    delay(totalDelay)
//
//                    rtmpClient?.start(baseModel.copy(flashLight = false,
//                        focusPercent = 0.9f),key){}
//                    delay(totalDelay)
//
//                    rtmpClient?.start(baseModel.copy(flashLight = false,
//                        red = 0.9f, green = 0.9f, blue = 0.91f),key){}
//                    delay(totalDelay)
//
//
//
//                    rtmpClient?.start(baseModel.copy(flashLight = false,
//                        exposureCompensation = 20),key){}
//                    delay(totalDelay)
//
//                    rtmpClient?.start(baseModel.copy(exposureCompensation = -20,flashLight = false),key){}
//                    delay(totalDelay)
//
//                    rtmpClient?.start(baseModel.copy(exposureCompensation = -20,flashLight = false),key){}
//                    delay(totalDelay)
//
//                    rtmpClient?.start(baseModel.copy(exposureCompensation = 0, zoomLevel = 8,flashLight = false),key){}
//                    delay(totalDelay)
//
//                    rtmpClient?.start(baseModel.copy(exposureCompensation = 0, zoomLevel = 4,flashLight = false),key){}
//                    delay(totalDelay)
//
//                    rtmpClient?.start(baseModel.copy(exposureCompensation = 0, zoomLevel = 1),key){}
//                    delay(totalDelay)
//
//                }

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
                                result?.let { cameraInfoModel ->
                                    Log.d(tag, "onConnectionStateChanged: 1")
                                    mySharedPreference.setCameraModel(cameraInfoModel)
                                    withContext(Dispatchers.Main) {
                                        rtmpClient?.start(
                                            cameraInfoModel, key
                                        ) {
                                            if (!isUiActive && !it) {
                                                openAppReopenCamera(cameraInfoModel, key)
                                            }
                                            Log.d(tag, "2onNewMessageReceived: camera is opened $it")
                                        }
                                    }

                                } ?: kotlin.run {
                                    Log.d(tag, "onConnectionStateChanged: 2")
                                    delay(1000)
                                    withContext(Dispatchers.Main) {
                                        rtmpClient?.start(
                                            mySharedPreference.getCameraModel(), key
                                        ) {
                                            if (!isUiActive && !it) {
                                                openAppReopenCamera(
                                                    mySharedPreference.getCameraModel(),
                                                    key
                                                )
                                            }
                                            Log.d(tag, "3onNewMessageReceived: camera is opened $it")
                                        }
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

                            result?.let { cameraInfo ->
                                withContext(Dispatchers.Main) {
                                    rtmpClient?.start(
                                        cameraInfo, key
                                    ) {
                                        if (!isUiActive && !it) {
                                            openAppReopenCamera(cameraInfo, key)
                                        }
                                        Log.d(tag, "4onNewMessageReceived: camera is opened $it")
                                    }
                                }
                            }
                        }

                        Log.d(
                            tag,
                            "5onNewMessageReceived: isUiActive = $isUiActive , message: $message"
                        )
                    }

                })

            } ?: kotlin.run {
                handleStopService()
            }
        }
    }

    private fun startServiceWithNotification() {

        startForeground(1, notificationBuilder.build())
    }

    interface Listener {
        fun cameraOpenedSuccessfully()
    }

}