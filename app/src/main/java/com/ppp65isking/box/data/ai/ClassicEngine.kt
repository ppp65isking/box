package com.ppp65isking.box.data.ai

import android.content.Context
import com.google.mlkit.nl.smartreply.SmartReply
import com.google.mlkit.nl.smartreply.SmartReplySuggestionResult
import com.google.mlkit.nl.smartreply.TextMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ML Kit classic — Smart Reply for short reply drafts. Works on
 * essentially every Android phone, no model download, no key, no
 * server, free, offline. It can't summarize, so Summarize returns
 * null and the router falls through to the plain list.
 */
class ClassicEngine(private val context: Context) : AiEngine {
    override val leg = AiLeg.CLASSIC
    override val label = "Smart Reply"

    override suspend fun available(): Boolean = true

    override suspend fun run(task: AiTask): String? = when (task) {
        is AiTask.ReplyDraft -> smartReply(task.messages)
        is AiTask.Summarize -> null
    }

    private suspend fun smartReply(messages: List<ChatMessage>): String? {
        val history = messages.takeLast(10).map { message ->
            if (message.isOutgoing) {
                TextMessage.createForLocalUser(
                    message.text,
                    System.currentTimeMillis(),
                )
            } else {
                TextMessage.createForRemoteUser(
                    message.text,
                    System.currentTimeMillis(),
                    "remote",
                )
            }
        }
        if (history.isEmpty()) return null
        return try {
            val result = withContext(Dispatchers.IO) {
                SmartReply.getClient().suggestReplies(history).awaitTask()
            }
            if (result.status != SmartReplySuggestionResult.STATUS_SUCCESS) {
                return null
            }
            result.suggestions.take(3)
                .joinToString("\n") { it.text }
                .takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }
}

/** Await a Play Services Task without the tasks-ktx artifact. */
internal suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener { error ->
            if (cont.isActive) cont.resumeWithException(error)
        }
    }
