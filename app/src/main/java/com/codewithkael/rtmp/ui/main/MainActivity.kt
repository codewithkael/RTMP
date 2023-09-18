package com.codewithkael.rtmp.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.codewithkael.rtmp.databinding.ActivityMainBinding
import com.codewithkael.rtmp.local.MySharedPreference
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
        if (sharedPreference.getToken().isNullOrEmpty()) {
            this@MainActivity.startActivity(Intent(this@MainActivity, LoginActivity::class.java))
        } else {
            viewModel.init({
                if (it) {
                    finishAffinity()
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