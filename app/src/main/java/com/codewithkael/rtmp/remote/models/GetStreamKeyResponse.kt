package com.codewithkael.rtmp.remote.models

data class GetStreamKeyResponse(
    val isStreaming: Boolean,
    val streamKey: String,
    val streamerUserId: String
)