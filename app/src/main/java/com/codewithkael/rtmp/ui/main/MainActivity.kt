package com.codewithkael.rtmp.ui.main

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.FrameLayout
import com.codewithkael.rtmp.R
import com.codewithkael.rtmp.databinding.ActivityMainBinding
import com.codewithkael.rtmp.local.MySharedPreference
import com.codewithkael.rtmp.service.MainService
import com.codewithkael.rtmp.ui.login.LoginActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var sharedPreference: MySharedPreference
    private lateinit var views : ActivityMainBinding
    @Inject lateinit var viewModel:MainViewModel


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        views = ActivityMainBinding.inflate(layoutInflater)
        setContentView(views.root)
        init()

    }


    private fun init(){
            if (sharedPreference.getToken().isNullOrEmpty()){
                this@MainActivity.startActivity(Intent(this@MainActivity,LoginActivity::class.java))
            }else{
                viewModel.init {
                    if (!it){
                        finish()
                    }
                }
            }
    }
}