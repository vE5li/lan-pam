package com.example.phonepam

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket

class UdpBroadcastListener(
    private val context: Context,
    private val rsaCrypto: RsaCrypto,
    private val port: Int = 4200
) {
    private var socket: DatagramSocket? = null
    private var isRunning = false
    private var multicastLock: WifiManager.MulticastLock? = null
    private val gson = Gson()
    private val notificationManager = PamNotificationManager(context)

    suspend fun start() = withContext(Dispatchers.IO) {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("UdpBroadcastLock").apply {
                setReferenceCounted(false)
                acquire()
            }
            Log.d("UdpListener", "Multicast lock acquired, held: ${multicastLock?.isHeld}")

            socket = DatagramSocket(port)
            isRunning = true
            Log.d("UdpListener", "Listening on port $port")

            val buffer = ByteArray(1200)
            while (isRunning) {
                val packet = DatagramPacket(buffer, buffer.size)
                socket?.receive(packet)

                val message = String(packet.data, 0, packet.length)
                Log.d("UdpListener", "Received raw: $message from ${packet.address.hostAddress}")

                try {
                    // Parse envelope
                    val envelope = gson.fromJson(message, LanPamRequest::class.java)
                    Log.d("UdpListener", "Parsed LanPamRequest envelope")

                    // Decrypt cipher key with RSA
                    val encryptedKey = envelope.getEncryptedKeyBytes()
                    val cipherKey = rsaCrypto.rsaDecrypt(encryptedKey)
                    Log.d("UdpListener", "Decrypted cipher key: ${cipherKey.size} bytes")

                    Log.d("UdpListener", "Decrypted cipher key: ${cipherKey.map { it.toInt() and 0xFF } }")

                    // Decrypt body with AES
                    val encryptedBody = envelope.getEncryptedBodyBytes()
                    val decryptedBody = rsaCrypto.aesDecrypt(cipherKey, encryptedBody)
                    val bodyJson = String(decryptedBody)
                    Log.d("UdpListener", "Decrypted body JSON: ${bodyJson.take(100)}...")

                    // Parse body
                    val requestBody = gson.fromJson(bodyJson, LanPamRequestBody::class.java)
                    Log.d("UdpListener", "Parsed request body:")
                    Log.d("UdpListener", "  Source: ${requestBody.source}")
                    Log.d("UdpListener", "  User: ${requestBody.user}")
                    Log.d("UdpListener", "  Service: ${requestBody.service}")
                    Log.d("UdpListener", "  Type: ${requestBody.type}")

                    // Show notification and wait for user response
                    val requestId = PamResponseHandler.createRequest()
                    notificationManager.showPamRequest(requestId, requestBody)
                    Log.d("UdpListener", "Notification shown, waiting for user response...")

                    val accepted = PamResponseHandler.awaitResponse(requestId)
                    Log.d("UdpListener", "User response: ${if (accepted) "ACCEPTED" else "REJECTED"}")

                    // Create response with the secret from the request
                    val response = LanPamResponse.create(
                        "Android Device",
                        requestBody.getSecretBytes(),
                        accepted
                    )
                    val responseJson = gson.toJson(response)
                    Log.d("UdpListener", "Response JSON: ${responseJson.take(100)}...")

                    // Encrypt response with AES
                    val encryptedResponse = rsaCrypto.aesEncrypt(cipherKey, responseJson.toByteArray())
                    Log.d("UdpListener", "Encrypted response: ${encryptedResponse.size} bytes")

                    // Send encrypted response back to source
                    val sourceAddress = packet.address
                    val sourcePort = packet.port
                    Log.d("UdpListener", "Preparing to send to: ${sourceAddress.hostAddress}:${sourcePort}")

                    val responsePacket = DatagramPacket(
                        encryptedResponse,
                        encryptedResponse.size,
                        sourceAddress,
                        sourcePort
                    )

                    socket?.send(responsePacket)
                    Log.d("UdpListener", "Response sent successfully (${encryptedResponse.size} bytes)")

                } catch (e: Exception) {
                    Log.e("UdpListener", "Error processing request: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            if (isRunning) {
                Log.e("UdpListener", "Error: ${e.message}")
            }
        } finally {
            multicastLock?.release()
            Log.d("UdpListener", "Multicast lock released")
        }
    }

    fun stop() {
        isRunning = false
        socket?.close()
        multicastLock?.release()
        Log.d("UdpListener", "Stopped")
    }
}
