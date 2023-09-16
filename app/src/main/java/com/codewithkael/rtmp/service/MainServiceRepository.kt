package com.codewithkael.rtmp.service

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.widget.FrameLayout
import com.codewithkael.rtmp.remote.models.GetStreamKeyResponse
import javax.inject.Inject

class MainServiceRepository @Inject constructor(
    private val context: Context,
) {

    fun startService(key: GetStreamKeyResponse) {
        Thread {
            val intent = Intent(context, MainService::class.java)
            intent.action = MainServiceActions.START_SERVICE.name
            intent.putExtra("key",key.streamKey)
            startServiceIntent(intent)
        }.start()
    }



    fun stopService() {
        val intent = Intent(context, MainService::class.java)
        intent.action = MainServiceActions.STOP_SERVICE.name
        startServiceIntent(intent)
    }

    private fun startServiceIntent(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }


}