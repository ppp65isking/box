package com.ppp65isking.box.data.ai

/**
 * The last leg — no model at all. A digest here is just the plain
 * notification list, and reply drafts are short canned replies.
 * This leg always answers, so the router never returns a dead button
 * or an error state: the worst Box AI ever does is show the list.
 */
class PlainEngine : AiEngine {
    override val leg = AiLeg.PLAIN
    override val label = "List"

    override suspend fun available(): Boolean = true

    override suspend fun run(task: AiTask): String? = when (task) {
        is AiTask.Summarize ->
            task.lines.take(12)
                .joinToString("\n") { "• $it" }
                .takeIf { it.isNotBlank() }

        is AiTask.ReplyDraft ->
            listOf("Sounds good", "Thanks!", "I'll check and let you know")
                .joinToString("\n")
    }
}
