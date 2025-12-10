package dev.ve5li.lanpam

import kotlinx.coroutines.CompletableDeferred
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object PamResponseHandler {
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    fun createRequest(): String {
        val requestId = UUID.randomUUID().toString()
        pendingRequests[requestId] = CompletableDeferred()
        return requestId
    }

    suspend fun awaitResponse(requestId: String): Boolean {
        return pendingRequests[requestId]?.await() ?: false
    }

    fun respondToRequest(requestId: String, accepted: Boolean) {
        pendingRequests[requestId]?.complete(accepted)
        pendingRequests.remove(requestId)
    }

    fun cancelRequest(requestId: String) {
        pendingRequests[requestId]?.complete(false)
        pendingRequests.remove(requestId)
    }
}
