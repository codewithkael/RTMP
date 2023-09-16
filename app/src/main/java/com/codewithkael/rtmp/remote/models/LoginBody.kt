package com.codewithkael.rtmp.remote.models

data class LoginBody(
    val username:String,
    val password:String
)

data class LoginResponse(
    val token:String
)