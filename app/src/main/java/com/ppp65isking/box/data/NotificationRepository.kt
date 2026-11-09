package com.ppp65isking.box.data

import com.ppp65isking.box.model.BoxNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The single source of truth for captured notifications. The
 * NotificationListenerService writes here; the Inbox and Deals screens
 * read from here. In-memory only, newest first, capped so the list
 * cannot grow without bound. A restart re-seeds from the listener's
 * active notifications when it connects.
 */
object NotificationRepository {

    private const val MAX_KEPT = 200

    private val _notifications = MutableStateFlow<List<BoxNotification>>(emptyList())
    val notifications: StateFlow<List<BoxNotification>> = _notifications.asStateFlow()

    /** Replace the whole list (used when the listener connects). */
    fun setAll(items: List<BoxNotification>) {
        _notifications.value = items
            .sortedByDescending { it.postTime }
            .take(MAX_KEPT)
    }

    /** Add or replace one notification (same id = update, not duplicate). */
    fun upsert(item: BoxNotification) {
        val current = _notifications.value.filterNot { it.id == item.id }
        _notifications.value = (listOf(item) + current)
            .sortedByDescending { it.postTime }
            .take(MAX_KEPT)
    }

    /** Remove one notification by id. */
    fun remove(id: String) {
        _notifications.value = _notifications.value.filterNot { it.id == id }
    }
}
