package com.codewithkael.rtmp.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codewithkael.rtmp.local.MySharedPreference
import com.codewithkael.rtmp.remote.UserApi
import com.codewithkael.rtmp.service.MainServiceRepository
import com.codewithkael.rtmp.utils.Constants
import kotlinx.coroutines.launch
import javax.inject.Inject

class MainViewModel @Inject constructor(
    context: Context,
    private val serviceRepository: MainServiceRepository,
    private val sharedPreference: MySharedPreference
) : ViewModel() {
    private val userApi: UserApi =
        Constants.getRetrofitObject(sharedPreference.getToken() ?: "")
            .create(UserApi::class.java)

    fun init(done: (Boolean) -> Unit, signOut: () -> Unit) {
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
                    done(false)
                } else {
                    serviceRepository.startService(keyResponse.body()!!)
                    done(true)
                }
            } ?: kotlin.run {
                done(false)
            }
        }


    }

}