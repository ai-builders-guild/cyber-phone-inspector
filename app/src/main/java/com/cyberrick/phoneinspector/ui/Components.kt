package com.cyberrick.phoneinspector.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cyberrick.phoneinspector.rules.Severity
import com.cyberrick.phoneinspector.ui.model.AppCard
import com.cyberrick.phoneinspector.ui.theme.Cpi

fun severityColor(severity: Severity): Color =
    when (severity) {
        Severity.HIGH -> Cpi.High
        Severity.MEDIUM -> Cpi.Medium
        Severity.LOW -> Cpi.Note
    }

fun scoreColor(score: Int): Color =
    when {
        score >= 90 -> Cpi.Accent
        score >= 60 -> Cpi.Medium
        else -> Cpi.High
    }

/**
 * The safety score ring. [fraction] fills the arc; [capped] draws a grey segment for a check that could not run;
 * [dotted] draws a thin dotted segment for the optional watch not run. Null [score] is the empty, not scanned state.
 */
@Composable
fun ScoreRing(
    score: Int?,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    capped: Boolean = false,
    dotted: Boolean = false,
    caption: String = "SAFETY",
    centre: String? = null,
) {
    val color = score?.let { scoreColor(it) } ?: Cpi.Outline
    val description =
        when {
            score == null -> "Safety score: not scanned yet"
            capped -> "Safety score $score of 100, capped because a check could not run"
            else -> "Safety score $score of 100"
        }
    Box(modifier = modifier.size(size).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = size.toPx() * 0.095f
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(Cpi.Outline, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (score != null) {
                val sweep = 360f * (score / 100f)
                drawArc(color, -90f, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                if (capped) drawArc(Cpi.Note, -90f + 324f, 36f, false, topLeft, arcSize, style = Stroke(stroke))
                if (dotted) {
                    drawArc(
                        Cpi.Muted,
                        -90f + sweep,
                        (360f - sweep).coerceAtLeast(0f),
                        false,
                        topLeft,
                        arcSize,
                        style = Stroke(stroke / 3, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 10f))),
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centre ?: score?.toString() ?: "?",
                style = if (size >= 120.dp) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
                color = if (score == null) Cpi.Muted else color,
            )
            Text(caption, style = MaterialTheme.typography.labelSmall, color = Cpi.Muted)
        }
    }
}

@Composable
fun LabelPill(
    text: String,
    severity: Severity,
    modifier: Modifier = Modifier,
) {
    val (back, fore) =
        when (severity) {
            Severity.HIGH -> Cpi.HighBack to Cpi.HighText
            Severity.MEDIUM -> Cpi.MediumBack to Cpi.MediumText
            Severity.LOW -> Cpi.SurfaceHigh to Cpi.Muted
        }
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = fore,
        modifier = modifier.clip(RoundedCornerShape(999.dp)).background(back).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** A threat card: colour bar, label, app name, one sentence, chevron. The whole card is one tap target. */
@Composable
fun ThreatCard(
    card: AppCard,
    onClick: () -> Unit,
) {
    val sev = card.severity.name.lowercase()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Cpi.Surface)
                .clickable(onClickLabel = "Open details for ${card.label}", onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = "${card.cardLabel}, $sev: ${card.label}. ${card.cardText}" }
                .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .width(6.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(severityColor(card.severity)),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            LabelPill(card.cardLabel, card.severity)
            Text(card.label, style = MaterialTheme.typography.titleMedium)
            Text(card.cardText, style = MaterialTheme.typography.bodyMedium, color = Cpi.Muted)
        }
        Text("›", style = MaterialTheme.typography.headlineMedium, color = Cpi.Muted)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors =
            if (danger) {
                ButtonDefaults.buttonColors(containerColor = Cpi.High, contentColor = Color.White)
            } else {
                ButtonDefaults.buttonColors(containerColor = Cpi.Accent, contentColor = Cpi.OnAccent)
            },
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Cpi.Text),
    ) { Text(text, style = MaterialTheme.typography.titleMedium) }
}

@Composable
fun SectionHeading(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
}

@Composable
fun MutedText(
    text: String,
    modifier: Modifier = Modifier,
    centered: Boolean = false,
) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = Cpi.Muted,
        modifier = modifier,
        textAlign = if (centered) TextAlign.Center else null,
    )
}

/** A key and value row for the detail screen's facts. */
@Composable
fun FactRow(
    key: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(key, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(120.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = Cpi.Muted,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
        )
    }
}
