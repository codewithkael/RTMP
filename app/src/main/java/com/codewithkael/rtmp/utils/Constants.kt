package com.codewithkael.rtmp.utils

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit


object Constants {
    fun getSocketUrl(token: String) :String{
        return "ws://141.11.184.69:3002?token=$token"
    }
    private fun getAuthHeader(token:String): Interceptor {
        return Interceptor { chain ->
            val request = chain.request().newBuilder().addHeader(
                "Authorization",
                "Bearer $token"
            ).build()
            chain.proceed(request)
        }
    }

    private fun getOkHttpClient(interceptor: Interceptor): OkHttpClient {
        return OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(interceptor).readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS).build()

    }
    fun getRetrofitObject(token:String) : Retrofit {
        return Retrofit.Builder()
            .baseUrl("http://141.11.184.69:3000/api/v1/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(getOkHttpClient(getAuthHeader(token)))
            .build()
    }
}