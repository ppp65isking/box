package com.ppp65isking.box.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ppp65isking.box.data.ai.DetectedEntity
import com.ppp65isking.box.data.ai.EntityKind

/** Tappable things found in a text: phone numbers dial, links open,
 *  emails compose. Dates and addresses are detected too, but Box has
 *  nothing to do with them yet — so they are not shown as chips.
 *  No dead buttons: only kinds with a real action appear here. */
@Composable
fun EntityChips(
    entities: List<DetectedEntity>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val actionable = entities.filter {
        it.kind == EntityKind.PHONE ||
            it.kind == EntityKind.URL ||
            it.kind == EntityKind.EMAIL
    }
    if (actionable.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        actionable.forEach { entity ->
            AssistChip(
                onClick = {
                    when (entity.kind) {
                        EntityKind.PHONE -> {
                            val digits = entity.text.filter {
                                it.isDigit() || it == '+'
                            }
                            context.startActivity(
                                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits")),
                            )
                        }
                        EntityKind.URL -> {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(entity.text)),
                            )
                        }
                        EntityKind.EMAIL -> {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_SENDTO,
                                    Uri.parse("mailto:${entity.text}"),
                                ),
                            )
                        }
                        else -> Unit
                    }
                },
                label = { Text(entity.text) },
            )
        }
    }
}
