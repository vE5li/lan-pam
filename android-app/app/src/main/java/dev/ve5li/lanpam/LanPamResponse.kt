package dev.ve5li.lanpam

import android.util.Base64

data class LanPamResponse(
    val device: String,
    val accepted: Boolean
) {
    companion object {
        fun create(device: String, accepted: Boolean): LanPamResponse {
            return LanPamResponse(device, accepted)
        }
    }
}
