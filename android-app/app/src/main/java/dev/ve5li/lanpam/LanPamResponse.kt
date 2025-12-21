package dev.ve5li.lanpam

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
