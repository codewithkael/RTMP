package com.codewithkael.rtmp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.annotation.RequiresApi
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
import com.codewithkael.rtmp.utils.RtmpClient2
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.ossrs.yasea.SrsCameraView
import javax.inject.Inject

@AndroidEntryPoint
class MainService : LifecycleService() {

    private val tag = "MainService"

    companion object {
        var isServiceRunning = false
        var isUiActive = true
        var listener : Listener?=null
    }

    private var mySrsView: SrsCameraView? = null


    @Inject
    lateinit var mySharedPreference: MySharedPreference

    @Inject
    lateinit var socketClient: SocketClient

    private val userApi: UserApi by lazy {
        Constants.getRetrofitObject(mySharedPreference.getToken() ?: "").create(UserApi::class.java)
    }


    private lateinit var notificationManager: NotificationManager

    private var rtmpClient: RtmpClient2? = null
    private var key: String? = ""


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(
            NotificationManager::class.java
        )
        mySrsView = SrsCameraView(this)
        val params = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, // This type is suitable for overlays
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.addView(mySrsView, params)
        mySrsView?.keepScreenOn = true
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
         rtmpClient?.start(info, key){
            if (!isUiActive&&!it){
                openAppReopenCamera(info,key)
                Log.d(tag, "onNewMessageReceived: camera is opened $it")
            }
        }

    }

    private fun openAppReopenCamera(info: CameraInfoModel, key: String?) {
            startActivity(Intent(this@MainService,MainActivity::class.java).apply {
                addFlags(FLAG_ACTIVITY_NEW_TASK)
            })
            rtmpClient?.start(info, key){
                if (it){
                    listener?.cameraOpenedSuccessfully()
                }
            }
    }

    private fun handleStopService() {
        isServiceRunning = false
        socketClient.unregisterClients()
        socketClient.closeSocket()
        stopSelf()
        notificationManager.cancelAll()
//        rtmpClient?.onDestroy()
    }

    private fun handleStartService(incomingIntent: Intent) {
        //start our foreground service
        if (!isServiceRunning) {
            isServiceRunning = true
            startServiceWithNotification()
            key = incomingIntent.getStringExtra("key")
            mySrsView?.let { srsCameraView ->
                rtmpClient = RtmpClient2(srsCameraView)
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
                                result?.let {cameraInfoModel->
                                    Log.d(tag, "onConnectionStateChanged: 1")
                                    mySharedPreference.setCameraModel(cameraInfoModel)
                                    withContext(Dispatchers.Main) {
                                        rtmpClient?.start(
                                            cameraInfoModel, key
                                        ){
                                            if (!isUiActive&&!it){
                                                openAppReopenCamera(cameraInfoModel, key)
                                            }
                                            Log.d(tag, "onNewMessageReceived: camera is opened $it")
                                        }
                                    }

                                } ?: kotlin.run {
                                    Log.d(tag, "onConnectionStateChanged: 2")
                                    delay(1000)
                                    withContext(Dispatchers.Main) {
                                        rtmpClient?.start(
                                            mySharedPreference.getCameraModel(), key
                                        ){
                                            if (!isUiActive&&!it){
                                                openAppReopenCamera(mySharedPreference.getCameraModel(), key)
                                            }
                                            Log.d(tag, "onNewMessageReceived: camera is opened $it")
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

                            result?.let {cameraInfo ->
                                withContext(Dispatchers.Main) {
                                    rtmpClient?.start(
                                        cameraInfo, key
                                    ){
                                        if (!isUiActive&&!it){
                                           openAppReopenCamera(cameraInfo, key)
                                        }
                                        Log.d(tag, "onNewMessageReceived: camera is opened $it")
                                    }
                                }
                            }
                        }

                        Log.d(tag, "onNewMessageReceived: isUiActive = $isUiActive , message: $message")
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

    interface Listener{
        fun cameraOpenedSuccessfully()
    }

}