package com.codewithkael.rtmp.ui.main

import android.content.Intent
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

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var sharedPreference: MySharedPreference
    private lateinit var views: ActivityMainBinding

    @Inject
    lateinit var viewModel: MainViewModel

    @Inject lateinit var mainServiceRepository: MainServiceRepository

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
            frontCameraCb.isChecked = model.frontCamera

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

            exposureSeekBar.progress = model.exposure

            streamBitrateEt.setText(model.bitrate.toString())

        }
    }

    private fun saveSettings() {
        views.apply {
            val model = sharedPreference.getCameraModel().copy(
                frontCamera = frontCameraCb.isChecked,
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
                exposure = exposureSeekBar.progress,
                bitrate = streamBitrateEt.text.toString().toInt()

            )
            sharedPreference.setCameraModel(model)
            mainServiceRepository.updateCamera()
        }


    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        views = ActivityMainBinding.inflate(layoutInflater)
        setContentView(views.root)
        init()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finishAffinity()
    }


    private fun init() {
        MainService.myFm = views.frameLayout

        views.saveBtn.setOnClickListener {
            saveSettings()
        }


        if (sharedPreference.getToken().isNullOrEmpty()) {
            this@MainActivity.startActivity(Intent(this@MainActivity, LoginActivity::class.java))
        } else {
            viewModel.init({
                if (it) {
//                    finishAffinity()
                    renderUi()
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


}