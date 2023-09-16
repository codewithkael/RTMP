package com.codewithkael.rtmp.remote

import com.codewithkael.rtmp.remote.models.GetStreamKeyResponse
import com.codewithkael.rtmp.remote.models.LoginBody
import com.codewithkael.rtmp.remote.models.LoginResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface UserApi {

    @POST("driver/login")
    suspend fun login(
        @Body loginBody: LoginBody
    ): LoginResponse

    @GET("stream-key")
    suspend fun getStreamKey(): GetStreamKeyResponse

}