package dev.ve5li.lanpam

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class RequestStatus {
    ACCEPTED,
    REJECTED,
    CANCELLED
}

data class RequestHistoryEntry(
    val id: String,
    val timestamp: Instant,
    val source: String,
    val user: String,
    val service: String,
    val type: String,
    val status: RequestStatus
) {
    fun getFormattedTime(): String {
        val localDateTime = LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault())
        val formatter = DateTimeFormatter.ofPattern("MMM dd, HH:mm:ss")
        return localDateTime.format(formatter)
    }
}

// Serializable version for storage
private data class SerializableEntry(
    val id: String,
    val timestampEpochSeconds: Long,
    val source: String,
    val user: String,
    val service: String,
    val type: String,
    val status: String
)

object RequestHistoryManager {
    private val _history = MutableStateFlow<List<RequestHistoryEntry>>(emptyList())
    val history: StateFlow<List<RequestHistoryEntry>> = _history.asStateFlow()

    private var prefs: SharedPreferences? = null
    private val gson = Gson()
    private const val PREFS_NAME = "lan_pam_history"
    private const val HISTORY_KEY = "request_history"

    fun initialize(context: Context) {
        // Only initialize once
        if (prefs != null) return

        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadHistory()
    }

    private fun loadHistory() {
        val json = prefs?.getString(HISTORY_KEY, null) ?: return

        try {
            val type = object : TypeToken<List<SerializableEntry>>() {}.type
            val serializedEntries: List<SerializableEntry> = gson.fromJson(json, type)

            val entries = serializedEntries.map { serialized ->
                RequestHistoryEntry(
                    id = serialized.id,
                    timestamp = Instant.ofEpochSecond(serialized.timestampEpochSeconds),
                    source = serialized.source,
                    user = serialized.user,
                    service = serialized.service,
                    type = serialized.type,
                    status = RequestStatus.valueOf(serialized.status)
                )
            }

            _history.value = entries
        } catch (e: Exception) {
            // If loading fails, start with empty history
            _history.value = emptyList()
        }
    }

    private fun saveHistory() {
        val serializedEntries = _history.value.map { entry ->
            SerializableEntry(
                id = entry.id,
                timestampEpochSeconds = entry.timestamp.epochSecond,
                source = entry.source,
                user = entry.user,
                service = entry.service,
                type = entry.type,
                status = entry.status.name
            )
        }

        val json = gson.toJson(serializedEntries)
        prefs?.edit()?.putString(HISTORY_KEY, json)?.apply()
    }

    fun addRequest(
        id: String,
        requestBody: LanPamRequestBody,
        status: RequestStatus
    ) {
        val entry = RequestHistoryEntry(
            id = id,
            timestamp = Instant.now(),
            source = requestBody.source,
            user = requestBody.user,
            service = requestBody.service,
            type = requestBody.type,
            status = status
        )

        // Add to the beginning of the list (most recent first)
        _history.value = listOf(entry) + _history.value
        saveHistory()
    }
}
