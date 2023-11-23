package com.codewithkael.rtmp.ui.login

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.codewithkael.rtmp.databinding.ActivityLoginBinding
import com.codewithkael.rtmp.local.MySharedPreference
import com.codewithkael.rtmp.remote.UserApi
import com.codewithkael.rtmp.remote.models.LoginBody
import com.codewithkael.rtmp.ui.main.MainActivity
import com.codewithkael.rtmp.utils.Constants
import com.permissionx.guolindev.PermissionX
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private lateinit var views: ActivityLoginBinding

    private var userApi = Constants.getRetrofitObject("").create(UserApi::class.java)

    @Inject
    lateinit var sharedPreference: MySharedPreference


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        views = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(views.root)

        init()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finishAffinity()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @SuppressLint("BatteryLife")
    private fun init() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intent.data = Uri.parse("package:$packageName")
        startActivity(intent)

        views.apply {
            loginBtn.setOnClickListener {
                if (usernameET.text.isNotEmpty() && passwordET.text.isNotEmpty()) {

                    PermissionX.init(this@LoginActivity).permissions(
                        android.Manifest.permission.CAMERA,
                        android.Manifest.permission.POST_NOTIFICATIONS,
                    ).request { allGranted, _, _ ->
                        if (allGranted) {
                            if (!Settings.canDrawOverlays(this@LoginActivity)) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                                startActivity(intent)
                            } else {
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        val result = userApi.login(
                                            LoginBody(
                                                username = usernameET.text.toString(),
                                                password = passwordET.text.toString()
                                            )
                                        )
                                        sharedPreference.setToken(result.token)
                                        Log.d("TAG", "init: ${result.token}")
                                        withContext(Dispatchers.Main) {
                                            this@LoginActivity.startActivity(
                                                Intent(this@LoginActivity, MainActivity::class.java)
                                            )
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                this@LoginActivity,
                                                "${e.message}", Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            }

                        } else {
                            Toast.makeText(
                                this@LoginActivity,
                                "Camera and audio permission is required",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    Toast.makeText(
                        this@LoginActivity, "fill the username and password", Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}