package com.ppp65isking.box.data

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import com.ppp65isking.box.model.SmsConversation
import com.ppp65isking.box.model.SmsMessage

/**
 * SMS, read-only in v1. Needs READ_SMS (conversations) and, for contact
 * names, READ_CONTACTS. All queries run on the calling thread — callers
 * must use Dispatchers.IO. Every query is best-effort: a missing column
 * or a denied permission yields an empty list, never a crash.
 */
class SmsRepository(private val context: Context) {

    /** Latest message per thread, newest first. */
    fun conversations(): List<SmsConversation> {
        val uri = Uri.parse("content://sms/")
        val projection = arrayOf("_id", "thread_id", "address", "body", "date", "read", "type")
        val byThread = linkedMapOf<Long, SmsConversation>()
        runCatching {
            context.contentResolver.query(
                uri, projection, null, null, "date DESC",
            )?.use { cursor ->
                val threadIdx = cursor.getColumnIndex("thread_id")
                val addressIdx = cursor.getColumnIndex("address")
                val bodyIdx = cursor.getColumnIndex("body")
                val dateIdx = cursor.getColumnIndex("date")
                val readIdx = cursor.getColumnIndex("read")
                val typeIdx = cursor.getColumnIndex("type")
                while (cursor.moveToNext()) {
                    val threadId = if (threadIdx >= 0) cursor.getLong(threadIdx) else continue
                    if (byThread.containsKey(threadId)) continue
                    val address = if (addressIdx >= 0) {
                        cursor.getString(addressIdx).orEmpty()
                    } else {
                        ""
                    }
                    val snippet = if (bodyIdx >= 0) cursor.getString(bodyIdx).orEmpty() else ""
                    val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                    val read = if (readIdx >= 0) cursor.getInt(readIdx) == 1 else true
                    val type = if (typeIdx >= 0) cursor.getInt(typeIdx) else Telephony.Sms.MESSAGE_TYPE_INBOX
                    byThread[threadId] = SmsConversation(
                        threadId = threadId,
                        address = address,
                        displayName = lookupName(address),
                        snippet = snippet,
                        date = date,
                        unread = !read && type == Telephony.Sms.MESSAGE_TYPE_INBOX,
                    )
                }
            }
        }
        return byThread.values.toList()
    }

    /** All messages in one thread, oldest first. */
    fun messages(threadId: Long): List<SmsMessage> {
        val uri = Uri.parse("content://sms/")
        val projection = arrayOf("_id", "body", "date", "type")
        val out = mutableListOf<SmsMessage>()
        runCatching {
            context.contentResolver.query(
                uri, projection, "thread_id=?",
                arrayOf(threadId.toString()), "date ASC",
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex("_id")
                val bodyIdx = cursor.getColumnIndex("body")
                val dateIdx = cursor.getColumnIndex("date")
                val typeIdx = cursor.getColumnIndex("type")
                while (cursor.moveToNext()) {
                    out += SmsMessage(
                        id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L,
                        body = if (bodyIdx >= 0) cursor.getString(bodyIdx).orEmpty() else "",
                        date = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L,
                        isOutgoing = if (typeIdx >= 0) {
                            cursor.getInt(typeIdx) == Telephony.Sms.MESSAGE_TYPE_SENT
                        } else {
                            false
                        },
                    )
                }
            }
        }
        return out
    }

    /** Contact display name for a phone number, or the number itself. */
    private fun lookupName(number: String): String {
        if (number.isBlank()) return "Unknown"
        return runCatching {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number),
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            }
        }.getOrNull() ?: number
    }
}
