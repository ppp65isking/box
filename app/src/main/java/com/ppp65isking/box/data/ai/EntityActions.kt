package com.ppp65isking.box.data.ai

import android.content.Context
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class EntityKind { PHONE, URL, EMAIL, DATE, ADDRESS }

data class DetectedEntity(val text: String, val kind: EntityKind)

/**
 * Find tappable things (phone numbers, links, emails, dates,
 * addresses) in a piece of text. ML Kit Entity Extraction first, plain
 * regex as the fallback — so this works even before the ML Kit model
 * has downloaded. Dates and addresses are detected but have no action
 * in Box yet, so the UI only turns phone / link / email into chips.
 */
suspend fun extractEntities(
    context: Context,
    text: String,
): List<DetectedEntity> {
    val found = linkedMapOf<String, EntityKind>()
    try {
        val extractor = EntityExtraction.getClient(
            EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build(),
        )
        withTimeoutOrNull(8_000) {
            withContext(Dispatchers.IO) {
                extractor.downloadModelIfNeeded().awaitTask()
            }
        }
        val annotations = withTimeoutOrNull(8_000) {
            withContext(Dispatchers.IO) {
                extractor.annotate(text).awaitTask()
            }
        }
        annotations?.forEach { annotation ->
            annotation.entities.forEach { entity ->
                val kind = when (entity.type) {
                    Entity.TYPE_PHONE -> EntityKind.PHONE
                    Entity.TYPE_URL -> EntityKind.URL
                    Entity.TYPE_EMAIL -> EntityKind.EMAIL
                    Entity.TYPE_DATE_TIME -> EntityKind.DATE
                    Entity.TYPE_ADDRESS -> EntityKind.ADDRESS
                    else -> null
                }
                if (kind != null) {
                    found[annotation.annotatedText.trim()] = kind
                }
            }
        }
    } catch (e: Exception) {
        // ML Kit unavailable — the regex below still finds the basics.
    }
    URL_REGEX.findAll(text).forEach { found[it.value] = EntityKind.URL }
    EMAIL_REGEX.findAll(text).forEach { found[it.value] = EntityKind.EMAIL }
    PHONE_REGEX.findAll(text).forEach { found[it.value.trim()] = EntityKind.PHONE }
    return found.entries.take(6).map { (t, k) -> DetectedEntity(t, k) }
}

private val URL_REGEX = Regex("https?://[^\\s]+")
private val EMAIL_REGEX = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
private val PHONE_REGEX = Regex("\\+?\\d[\\d\\s().-]{6,}\\d")
