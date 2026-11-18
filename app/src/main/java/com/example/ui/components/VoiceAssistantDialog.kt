package com.example.ui.components

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.obd.i18n.l10nContext
import com.example.obd.i18n.l10nStr
import com.example.obd.model.VoiceAssistantState
import com.example.ui.theme.CarbonObsidian
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.CockpitSurface
import com.example.ui.theme.CockpitSurfaceElevated
import com.example.ui.theme.MotorsportAmber
import com.example.ui.theme.NominalEmerald
import com.example.ui.theme.RedlineCrimson
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceAssistantDialog(
    state: VoiceAssistantState,
    isEnglish: Boolean,
    onAskQuestion: (question: String, speakReply: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val l10nCtx = l10nContext()
    var questionText by remember { mutableStateOf("") }
    var speakReply by remember { mutableStateOf(true) }
    var speechError by remember { mutableStateOf<String?>(null) }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!spoken.isNullOrBlank()) {
                questionText = spoken
                speechError = null
                onAskQuestion(spoken, speakReply)
            }
        }
    }

    val quickPrompts = if (isEnglish) {
        listOf(
            "Is engine temperature & voltage normal?",
            "What do my trouble codes mean?",
            "What service is due soon?",
            "Why is fuel consumption elevated?"
        )
    } else {
        listOf(
            "В норме ли температура ДВС и напряжение?",
            "Что означают текущие ошибки Check Engine?",
            "Какое ТО скоро подойдёт по пробегу?",
            "Почему может расти расход топлива?"
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .testTag("voice_ai_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = CockpitSurface,
            border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = TelemetryCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = l10nStr(R.string.voice_ai_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = l10nStr(R.string.action_close), tint = TextSecondary)
                    }
                }

                Text(
                    text = l10nStr(R.string.voice_ai_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    quickPrompts.forEachIndexed { idx, prompt ->
                        SuggestionChip(
                            onClick = {
                                questionText = prompt
                                onAskQuestion(prompt, speakReply)
                            },
                            label = { Text(prompt) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = CockpitSurfaceElevated,
                                labelColor = TelemetryCyan
                            ),
                            modifier = Modifier.testTag("voice_quick_prompt_$idx")
                        )
                    }
                }

                OutlinedTextField(
                    value = questionText,
                    onValueChange = { questionText = it },
                    placeholder = { Text(l10nStr(R.string.voice_ai_input_hint)) },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("voice_question_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = l10nStr(R.string.voice_ai_speak_toggle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Switch(
                        checked = speakReply,
                        onCheckedChange = { speakReply = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = TelemetryCyan)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                )
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE,
                                    if (isEnglish) "en-US" else "ru-RU"
                                )
                                putExtra(
                                    RecognizerIntent.EXTRA_PROMPT,
                                    l10nCtx.getString(R.string.voice_ai_title)
                                )
                            }
                            runCatching {
                                speechLauncher.launch(intent)
                                speechError = null
                            }.onFailure {
                                speechError = l10nCtx.getString(R.string.voice_ai_no_mic)
                            }
                        },
                        border = BorderStroke(1.dp, MotorsportAmber.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("voice_mic_button")
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = null, tint = MotorsportAmber, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(l10nStr(R.string.voice_ai_mic_btn), color = MotorsportAmber)
                    }

                    Button(
                        onClick = {
                            if (questionText.isNotBlank()) {
                                onAskQuestion(questionText.trim(), speakReply)
                            }
                        },
                        enabled = questionText.isNotBlank() && state !is VoiceAssistantState.Thinking,
                        colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("voice_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            tint = CarbonObsidian,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(l10nStr(R.string.voice_ai_send_btn), color = CarbonObsidian)
                    }
                }

                speechError?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.labelMedium,
                        color = MotorsportAmber
                    )
                }

                when (state) {
                    is VoiceAssistantState.Thinking -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = CockpitSurfaceElevated,
                            border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = TelemetryCyan
                                )
                                Text(
                                    text = l10nStr(R.string.voice_ai_thinking),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                    is VoiceAssistantState.Answered -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = CarbonObsidian,
                            border = BorderStroke(1.dp, NominalEmerald.copy(alpha = 0.55f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("voice_answer_box")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = state.question,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TelemetryCyan
                                )
                                Text(
                                    text = state.answer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    is VoiceAssistantState.Error -> {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = RedlineCrimson
                        )
                    }
                    VoiceAssistantState.Idle -> Unit
                }
            }
        }
    }
}
