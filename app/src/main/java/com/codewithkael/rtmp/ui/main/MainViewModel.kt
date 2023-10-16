package com.codewithkael.rtmp.ui.main

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
    private val serviceRepository: MainServiceRepository,
    private val sharedPreference: MySharedPreference
) : ViewModel() {
    private val userApi: UserApi =
        Constants.getRetrofitObject(sharedPreference.getToken() ?: "")
            .create(UserApi::class.java)

    fun init(done: (Boolean, GetStreamKeyResponse?) -> Unit, signOut: () -> Unit) {
        if (MainService.isServiceRunning){
            done(true,null)
        } else {
            viewModelScope.launch {
                val streamKey = try {
                    userApi.getStreamKey()
                } catch (e: Exception) {
                    null
                }

                streamKey?.let { keyResponse ->
                    if (!keyResponse.isSuccessful) {
                        if (keyResponse.code() == 401) {
                            sharedPreference.setToken(null)
                            signOut()
                        }
                        done(false,null)
                    } else {
//                        serviceRepository.startService(keyResponse.body()!!)
                        done(true,keyResponse.body()!!)
                    }
                } ?: kotlin.run {
                    done(false,null)
                }
            }
        }

    }

}