package com.example.phonepam

import android.util.Base64
import com.google.gson.annotations.SerializedName

data class LanPamRequest(
    @SerializedName("encrypted_key")
    val encryptedKey: String,
    @SerializedName("encrypted_body")
    val encryptedBody: String
) {
    fun getEncryptedKeyBytes(): ByteArray {
        return Base64.decode(encryptedKey, Base64.DEFAULT)
    }

    fun getEncryptedBodyBytes(): ByteArray {
        return Base64.decode(encryptedBody, Base64.DEFAULT)
    }
}

data class LanPamRequestBody(
    val source: String,
    val user: String?,
    val service: String?,
    val type: String?,
    val secret: String
) {
    fun getSecretBytes(): ByteArray {
        return Base64.decode(secret, Base64.DEFAULT)
    }
}
