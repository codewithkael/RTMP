package com.codewithkael.rtmp.ui.main

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.widget.FrameLayout
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codewithkael.rtmp.local.MySharedPreference
import com.codewithkael.rtmp.remote.UserApi
import com.codewithkael.rtmp.remote.models.GetStreamKeyResponse
import com.codewithkael.rtmp.service.MainService
import com.codewithkael.rtmp.service.MainServiceRepository
import com.codewithkael.rtmp.utils.Constants
import kotlinx.coroutines.launch
import javax.inject.Inject

class MainViewModel @Inject constructor(
    context: Context,
    private val serviceRepository: MainServiceRepository,
    sharedPreference: MySharedPreference
) : ViewModel() {
    private val userApi : UserApi =
        Constants.getRetrofitObject(sharedPreference.getToken() ?: "")
            .create(UserApi::class.java)

    fun init(done: (Boolean) -> Unit) {
        viewModelScope.launch {
            val streamKey : GetStreamKeyResponse? = try {
                userApi.getStreamKey()
            } catch (e:Exception){
                null
            }

            streamKey?.let { key->
                serviceRepository.startService(key)
                done(true)
            }?: kotlin.run {
                done(false)
            }
        }



    }

}