package com.codewithkael.rtmp.ui.main

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.codewithkael.rtmp.databinding.ActivityMainBinding
import com.codewithkael.rtmp.local.MySharedPreference
import com.codewithkael.rtmp.service.MainService
import com.codewithkael.rtmp.service.MainServiceRepository
import com.codewithkael.rtmp.ui.login.LoginActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import android.provider.Settings
import android.util.Log
import com.codewithkael.rtmp.remote.UserApi
import com.codewithkael.rtmp.remote.socket.SocketClient
import com.codewithkael.rtmp.remote.socket.SocketState
import com.codewithkael.rtmp.utils.Constants
import com.codewithkael.rtmp.utils.RtmpClient
import com.haishinkit.view.HkSurfaceView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), MainService.Listener {
    @Inject
    lateinit var sharedPreference: MySharedPreference
    private lateinit var views: ActivityMainBinding

    @Inject
    lateinit var viewModel: MainViewModel

    @Inject
    lateinit var mainServiceRepository: MainServiceRepository

    private val orientationList = listOf(
        "PORTRAIT", "LANDSCAPE_RTL", "LANDSCAPE_LTR"
    )
    private val orientationAdapter: ArrayAdapter<String> by lazy {
        ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, orientationList)
    }

    private val resolutionList = listOf(
        "320x480", "480x640", "720x1080", "1080x1920",
    )
    private val resolutionAdapter: ArrayAdapter<String> by lazy {
        ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, resolutionList)
    }

    private fun renderUi() {
        val model = sharedPreference.getCameraModel()
        views.apply {
//            frontCameraCb.isChecked = model.frontCamera

            orientationSpinner.adapter = orientationAdapter
            when (model.orientation) {
                0 -> {
                    orientationSpinner.setSelection(0)
                }

                1 -> {
                    orientationSpinner.setSelection(1)
                }

                3 -> {
                    orientationSpinner.setSelection(2)
                }
            }

            zoomSeekbar.progress = model.zoomLevel

            resolutionSpinner.adapter = resolutionAdapter
            when (model.width) {
                320 -> {
                    resolutionSpinner.setSelection(0)
                }

                480 -> {
                    resolutionSpinner.setSelection(1)
                }

                720 -> {
                    resolutionSpinner.setSelection(2)
                }

                1080 -> {
                    resolutionSpinner.setSelection(3)
                }
            }

            fpsSeekBar.progress = model.fps

//            isoSeekBar.progress = model.iso

            streamBitrateEt.setText(model.bitrate.toString())

//            normalizedXSeekbar.progress = (model.normalizedX*100).toInt()
//            normalizedYSeekbar.progress = (model.normalizedY*100).toInt()
//            focusSizeSeekbar.progress = (model.size*100).toInt()

        }
    }

    private fun saveSettings() {
        views.apply {
            val model = sharedPreference.getCameraModel().copy(
//                frontCamera = frontCameraCb.isChecked,
                orientation = when (orientationSpinner.selectedItemPosition) {
                    0 -> 0
                    1 -> 1
                    else -> 3
                },
                zoomLevel = zoomSeekbar.progress,
                width = when (resolutionSpinner.selectedItemPosition) {
                    0 -> 320
                    1 -> 480
                    2 -> 720
                    else -> 1080
                },
                height = when (resolutionSpinner.selectedItemPosition) {
                    0 -> 480
                    1 -> 640
                    2 -> 1080
                    else -> 1920
                },
                fps = fpsSeekBar.progress,
//                iso = isoSeekBar.progress,
                bitrate = streamBitrateEt.text.toString().toInt(),
//                normalizedX = normalizedXSeekbar.progress.toFloat()/100,
//                normalizedY = normalizedYSeekbar.progress.toFloat()/100,
//                size = focusSizeSeekbar.progress.toFloat()/100

            )
            sharedPreference.setCameraModel(model)
            mainServiceRepository.updateCamera()
        }


    }


    @Inject
    lateinit var mySharedPreference: MySharedPreference

    @Inject
    lateinit var socketClient: SocketClient

    private val userApi: UserApi by lazy {
        Constants.getRetrofitObject(mySharedPreference.getToken() ?: "").create(UserApi::class.java)
    }
    private var rtmpClient: RtmpClient? = null

    private val tag = "MainActivity"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        views = ActivityMainBinding.inflate(layoutInflater)
        setContentView(views.root)
        init()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        finishAffinity()
    }

    override fun onDestroy() {
        MainService.isUiActive = false
        MainService.listener = null
        socketClient.unregisterClients()
        socketClient.closeSocket()
        rtmpClient?.stop()
        super.onDestroy()
    }

    private fun init() {
        MainService.isUiActive = true
        MainService.listener = this

        views.saveBtn.setOnClickListener {
            saveSettings()
        }


        if (sharedPreference.getToken().isNullOrEmpty()) {
            this@MainActivity.startActivity(Intent(this@MainActivity, LoginActivity::class.java))
        } else {
            viewModel.init({ isDone, response ->
                if (isDone && response!=null) {
//                    finishAffinity()
//                    renderUi()
                    CoroutineScope(Dispatchers.IO).launch {
                        rtmpClient = RtmpClient(this@MainActivity, views.surface, userApi)
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
                                                    cameraInfoModel, response.streamKey
                                                ) {
//                                                if (!MainService.isUiActive && !it) {
//                                                    openAppReopenCamera(cameraInfoModel, key)
//                                                }
                                                    Log.d(tag, "2onNewMessageReceived: camera is opened $it")
                                                }
                                            }

                                        } ?: kotlin.run {
                                            Log.d(tag, "onConnectionStateChanged: 2")
                                            delay(1000)
                                            withContext(Dispatchers.Main) {
                                                rtmpClient?.start(
                                                    mySharedPreference.getCameraModel(), response.streamKey
                                                ) {
//                                                if (!MainService.isUiActive && !it) {
//                                                    openAppReopenCamera(
//                                                        mySharedPreference.getCameraModel(),
//                                                        key
//                                                    )
//                                                }
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
                                                cameraInfo, response.streamKey
                                            ) {
//                                            if (!MainService.isUiActive && !it) {
//                                                openAppReopenCamera(cameraInfo, key)
//                                            }
                                                Log.d(tag, "4onNewMessageReceived: camera is opened $it")
                                            }
                                        }
                                    }
                                }

                                Log.d(
                                    tag,
                                    "5onNewMessageReceived: isUiActive = ${MainService.isUiActive} , message: $message"
                                )
                            }

                        })
                    }
                }
            }, {
                this@MainActivity.startActivity(
                    Intent(
                        this@MainActivity,
                        LoginActivity::class.java
                    )
                )
            })
        }
    }


    override fun cameraOpenedSuccessfully() {
        runOnUiThread {
            finishAffinity()
        }
    }

}