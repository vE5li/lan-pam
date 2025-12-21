package dev.ve5li.lanpam

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.ServerSocket
import java.net.Socket

class TcpListener(
    private val context: Context,
    private val rsaCrypto: RsaCrypto,
    private val port: Int = 4200
) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val gson = Gson()
    private val notificationManager = PamNotificationManager(context)

    suspend fun start() = withContext(Dispatchers.IO) {
        try {
            serverSocket = ServerSocket(port)
            isRunning = true
            Log.d("TcpListener", "Listening on port $port")

            while (isRunning) {
                try {
                    val clientSocket = serverSocket?.accept() ?: break
                    Log.d("TcpListener", "Accepted connection from ${clientSocket.inetAddress.hostAddress}")

                    handleClient(clientSocket)
                } catch (e: Exception) {
                    if (isRunning) {
                        Log.e("TcpListener", "Error accepting connection: ${e.message}", e)
                    }
                }
            }
        } catch (e: Exception) {
            if (isRunning) {
                Log.e("TcpListener", "Error: ${e.message}", e)
            }
        }
    }

    private suspend fun handleClient(clientSocket: Socket) = withContext(Dispatchers.IO) {
        try {
            val inputStream = BufferedInputStream(clientSocket.getInputStream())
            val outputStream = BufferedOutputStream(clientSocket.getOutputStream())

            // Read the message
            val buffer = ByteArray(1200)
            val bytesRead = inputStream.read(buffer)

            if (bytesRead <= 0) {
                Log.w("TcpListener", "No data received from client")
                return@withContext
            }

            val message = String(buffer, 0, bytesRead)
            Log.d("TcpListener", "Received raw: $message from ${clientSocket.inetAddress.hostAddress}")

            try {
                // Parse envelope
                val envelope = gson.fromJson(message, LanPamRequest::class.java)
                Log.d("TcpListener", "Parsed LanPamRequest envelope")

                // Decrypt cipher key with RSA
                val encryptedKey = envelope.getEncryptedKeyBytes()
                val cipherKey = rsaCrypto.rsaDecrypt(encryptedKey)
                Log.d("TcpListener", "Decrypted cipher key: ${cipherKey.size} bytes")

                Log.d("TcpListener", "Decrypted cipher key: ${cipherKey.map { it.toInt() and 0xFF } }")

                // Decrypt body with AES
                val encryptedBody = envelope.getEncryptedBodyBytes()
                val decryptedBody = rsaCrypto.aesDecrypt(cipherKey, encryptedBody)
                val bodyJson = String(decryptedBody)
                Log.d("TcpListener", "Decrypted body JSON: ${bodyJson.take(100)}...")

                // Parse body
                val requestBody = gson.fromJson(bodyJson, LanPamRequestBody::class.java)
                Log.d("TcpListener", "Parsed request body:")
                Log.d("TcpListener", "  Source: ${requestBody.source}")
                Log.d("TcpListener", "  User: ${requestBody.user}")
                Log.d("TcpListener", "  Service: ${requestBody.service}")
                Log.d("TcpListener", "  Type: ${requestBody.type}")

                // Show notification and wait for user response
                val requestId = PamResponseHandler.createRequest()
                val notificationId = notificationManager.showPamRequest(requestId, requestBody)
                Log.d("TcpListener", "Notification shown, waiting for user response...")

                // Track if request was cancelled
                var wasCancelled = false

                // Monitor socket connection and cancel notification if disconnected
                val monitorJob = launch {
                    try {
                        // Try to read from socket to detect disconnection
                        val monitorBuffer = ByteArray(1)
                        val bytesRead = inputStream.read(monitorBuffer)

                        if (bytesRead == -1) {
                            // Socket disconnected
                            Log.d("TcpListener", "Socket disconnected, canceling notification")
                            wasCancelled = true
                            PamResponseHandler.cancelRequest(requestId)
                            notificationManager.cancelNotification(notificationId)
                        }
                    } catch (e: CancellationException) {
                        // Job was cancelled normally (user responded), this is expected
                        throw e
                    } catch (e: Exception) {
                        // Any other exception (timeout, reset, etc.) means disconnection
                        Log.d("TcpListener", "Socket error detected, canceling notification: ${e.message}")
                        wasCancelled = true
                        PamResponseHandler.cancelRequest(requestId)
                        notificationManager.cancelNotification(notificationId)
                    }
                }

                val accepted = try {
                    PamResponseHandler.awaitResponse(requestId)
                } finally {
                    monitorJob.cancel()
                }
                Log.d("TcpListener", "User response: ${if (accepted) "ACCEPTED" else "REJECTED"}")

                // Record to history based on what happened
                val status = if (wasCancelled) {
                    RequestStatus.CANCELLED
                } else if (accepted) {
                    RequestStatus.ACCEPTED
                } else {
                    RequestStatus.REJECTED
                }
                RequestHistoryManager.addRequest(requestId, requestBody, status)

                // Create response
                val response = LanPamResponse.create(
                    "Android Device",
                    accepted
                )
                val responseJson = gson.toJson(response)
                Log.d("TcpListener", "Response JSON: ${responseJson.take(100)}...")

                // Encrypt response with AES
                val encryptedResponse = rsaCrypto.aesEncrypt(cipherKey, responseJson.toByteArray())
                Log.d("TcpListener", "Encrypted response: ${encryptedResponse.size} bytes")

                // Send encrypted response back to client
                outputStream.write(encryptedResponse)
                outputStream.flush()
                Log.d("TcpListener", "Response sent successfully (${encryptedResponse.size} bytes)")

            } catch (e: Exception) {
                Log.e("TcpListener", "Error processing request: ${e.message}", e)
            }
        } catch (e: Exception) {
            Log.e("TcpListener", "Error handling client: ${e.message}", e)
        } finally {
            try {
                clientSocket.close()
            } catch (e: Exception) {
                Log.e("TcpListener", "Error closing client socket: ${e.message}")
            }
        }
    }

    fun stop() {
        isRunning = false
        serverSocket?.close()
        Log.d("TcpListener", "Stopped")
    }
}
