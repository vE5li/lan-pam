package com.example.phonepam

import android.util.Base64

data class LanPamResponse(
    val device: String,
    val secret: String,
    val accepted: Boolean
) {
    companion object {
        fun create(device: String, secret: ByteArray, accepted: Boolean): LanPamResponse {
            val base64Secret = Base64.encodeToString(secret, Base64.NO_WRAP)
            return LanPamResponse(device, base64Secret, accepted)
        }
    }
}
