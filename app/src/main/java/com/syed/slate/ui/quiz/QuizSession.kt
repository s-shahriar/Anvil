package com.syed.slate.ui.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.syed.slate.ModuleServices
import com.syed.slate.backend.ModuleId
import com.syed.slate.content.Item
import com.syed.slate.content.correctAnswer
import com.syed.slate.content.explanation
import com.syed.slate.content.optionList
import com.syed.slate.progress.Flag
import com.syed.slate.ui.component.HandMirror
import com.syed.slate.ui.component.LocalLeftHand
import com.syed.slate.ui.component.LocalToggleHand
import com.syed.slate.ui.theme.LocalPalette
import kotlinx.coroutines.delay

/**
 * One question at a time with instant feedback, used by both Quiz and Exam. The list is fixed when the session
 * starts, so marking or un-marking questions part-way through never reshuffles or shortens it.
 */
@Composable
fun QuizSession(
    services: ModuleServices,
    questions: List<Item>,
    topicName: (Item) -> String?,
    pill: String,
    resultTitle: String,
    resultLabel: String? = null,
    showTopicTag: Boolean = false,
    stoppable: Boolean = false,
    emptyMessage: String = "No questions here yet.",
    onBack: () -> Unit,
    onHome: () -> Unit,
) {
    val p = LocalPalette.current
    val module = services.id
    val flags by services.progress.flags.collectAsState()
    var idx by rememberSaveable { mutableIntStateOf(0) }
    var score by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var done by rememberSaveable { mutableStateOf(false) }
    var attempt by rememberSaveable { mutableIntStateOf(0) } // bumps on retry so the list is reshuffled
    val deck = remember(questions, attempt) { if (attempt == 0) questions else questions.shuffled() }
    val revealed = selected != null

    Column(Modifier.fillMaxSize()) {
        HandMirror { Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(pill, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (stoppable && !done && deck.isNotEmpty()) StopButton { done = true }
            LocalToggleHand.current?.let { toggle ->
                IconButton(onClick = toggle) {
                    Icon(
                        Icons.Filled.PanTool,
                        if (LocalLeftHand.current) "Left-hand layout: switch to right" else "Right-hand layout: switch to left",
                        tint = if (LocalLeftHand.current) MaterialTheme.colorScheme.primary else p.text3,
                    )
                }
            }
        } }

        if (deck.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(emptyMessage, style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) { Text("ফিরে যাও") }
            }
            return@Column
        }
        if (done || idx >= deck.size) {
            val answered = if (done && idx < deck.size) idx + if (revealed) 1 else 0 else deck.size
            ScoreRingScreen(score, if (stoppable) answered.coerceAtLeast(0) else deck.size, resultTitle, resultLabel,
                onRetry = { idx = 0; score = 0; selected = null; done = false; attempt++ }, onHome = onHome)
            return@Column
        }

        val q = deck[idx]
        val uid = q.uid
        val progress = (idx + if (revealed) 1 else 0) / deck.size.toFloat()
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text("Question ${idx + 1} of ${deck.size}", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text("${Math.round(progress * 100)}%", style = MaterialTheme.typography.labelLarge, color = p.text3)
            }
            LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth(), trackColor = p.outline)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (showTopicTag) topicName(q)?.let {
                Text(it, Modifier.clip(MaterialTheme.shapes.small).background(p.primaryContainer).padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium, color = p.onPrimaryContainer)
            }
            QuestionBody(module, q)
            val correct = q.correctAnswer
            q.optionList().forEach { (letter, text) ->
                val state = when {
                    !revealed -> OptState.IDLE
                    letter == correct -> OptState.CORRECT
                    letter == selected -> OptState.WRONG
                    else -> OptState.DIM
                }
                OptionRow(module, letter, text, state, enabled = !revealed) {
                    selected = letter
                    if (letter == correct) score++
                }
            }
            if (revealed) {
                if (uid != null) FlagBar(flags[uid] ?: Flag(), uid, services.progress, labels = true, itemId = q.id, onDeleted = { if (idx + 1 >= deck.size) done = true else { idx++; selected = null } })
                q.explanation?.let { ExplanationBox(module, it, selected == correct, uid = uid) }
                Button(
                    onClick = { if (idx + 1 >= deck.size) done = true else { idx++; selected = null } },
                    Modifier.fillMaxWidth(),
                ) {
                    Text(if (idx + 1 >= deck.size) "ফলাফল দেখুন" else "পরবর্তী প্রশ্ন")
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/** Two taps to stop, so a stray touch cannot end an exam: the first arms it for three seconds. */
@Composable
private fun StopButton(onStop: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) { if (armed) { delay(3000); armed = false } }
    OutlinedButton(onClick = { if (armed) onStop() else armed = true }, Modifier.padding(end = 8.dp)) {
        Text(if (armed) "Sure?" else "Stop Exam")
    }
}
