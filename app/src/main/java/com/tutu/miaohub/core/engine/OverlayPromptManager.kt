package com.tutu.miaohub.core.engine

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.tutu.miaohub.core.model.SkillStep
import com.tutu.miaohub.core.service.OverlayLifecycleOwner
import kotlinx.coroutines.*
import kotlin.coroutines.resume

class OverlayPromptManager(private val context: Context) : SkillEngine.PromptHandler {

    companion object {
        private const val DEFAULT_PROMPT_TIMEOUT_SEC = 120L
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val lifecycleOwner = OverlayLifecycleOwner()
    private var promptView: ComposeView? = null

    init {
        lifecycleOwner.onCreate()
    }

    override suspend fun promptUser(step: SkillStep): Map<String, String> =
        suspendCancellableCoroutine { cont ->
            val fields = step.fields.ifEmpty {
                listOf(
                    com.tutu.miaohub.core.model.StepField(
                        key = "input",
                        label = step.label.ifEmpty { "请输入" },
                        type = "text"
                    )
                )
            }

            val timeoutSec = step.timeout.takeIf { it > 0 } ?: DEFAULT_PROMPT_TIMEOUT_SEC
            var timeoutJob: Job? = null

            MainScope().launch {
                val view = ComposeView(context).apply {
                    setViewTreeLifecycleOwner(lifecycleOwner)
                    setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                    setContent {
                        CompositionLocalProvider(
                            LocalOnBackPressedDispatcherOwner provides lifecycleOwner
                        ) {
                            PromptOverlayContent(
                                title = step.title.ifEmpty { "Skill 需要您的输入" },
                                fields = fields,
                                timeoutSec = timeoutSec.toInt(),
                                onSubmit = { values ->
                                    dismissPrompt()
                                    timeoutJob?.cancel()
                                    if (cont.isActive) cont.resume(values)
                                },
                                onCancel = {
                                    dismissPrompt()
                                    timeoutJob?.cancel()
                                    val defaults = mutableMapOf<String, String>()
                                    fields.forEach { f ->
                                        defaults[f.key] = f.default.ifEmpty { "" }
                                    }
                                    if (cont.isActive) cont.resume(defaults)
                                }
                            )
                        }
                    }
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER
                    softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
                }

                promptView = view
                windowManager.addView(view, params)

                if (timeoutSec > 0) {
                    timeoutJob = launch {
                        delay(timeoutSec * 1000L)
                        if (cont.isActive) {
                            dismissPrompt()
                            val defaults = mutableMapOf<String, String>()
                            fields.forEach { f ->
                                defaults[f.key] = f.default.ifEmpty {
                                    step.defaultValues?.get(f.key)
                                        ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                                        ?: ""
                                }
                            }
                            if (step.timeoutAction == "stop") {
                                cont.cancel(CancellationException("用户输入超时"))
                            } else {
                                cont.resume(defaults)
                            }
                        }
                    }
                }
            }

            cont.invokeOnCancellation {
                MainScope().launch { dismissPrompt() }
                timeoutJob?.cancel()
            }
        }

    private fun dismissPrompt() {
        promptView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        promptView = null
    }

    fun destroy() {
        dismissPrompt()
        lifecycleOwner.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromptOverlayContent(
    title: String,
    fields: List<com.tutu.miaohub.core.model.StepField>,
    timeoutSec: Int,
    onSubmit: (Map<String, String>) -> Unit,
    onCancel: () -> Unit
) {
    val values = remember {
        mutableStateMapOf<String, String>().apply {
            fields.forEach { put(it.key, it.default) }
        }
    }
    var remainingSec by remember { mutableIntStateOf(timeoutSec) }

    if (timeoutSec > 0) {
        LaunchedEffect(Unit) {
            while (remainingSec > 0) {
                delay(1000)
                remainingSec--
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 48.dp)
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2A2518)
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFFF5EDD8)
                )

                if (timeoutSec > 0) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { remainingSec.toFloat() / timeoutSec },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = Color(0xFFF5B731),
                        trackColor = Color(0xFF362F24),
                    )
                    Text(
                        "${remainingSec}s",
                        fontSize = 11.sp,
                        color = Color(0xFFB8AD9A),
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 2.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                fields.forEach { field ->
                    Text(
                        field.label.ifEmpty { field.key },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFF5EDD8)
                    )
                    Spacer(Modifier.height(6.dp))

                    if (field.type == "select" && field.options.isNotEmpty()) {
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = it }
                        ) {
                            OutlinedTextField(
                                value = values[field.key] ?: "",
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                                colors = promptTextFieldColors()
                            )
                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                field.options.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            values[field.key] = option
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = values[field.key] ?: "",
                            onValueChange = { values[field.key] = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(
                                    field.placeholder.ifEmpty { field.label },
                                    color = Color(0xFF7A6F5F)
                                )
                            },
                            singleLine = field.type != "textarea",
                            minLines = if (field.type == "textarea") 3 else 1,
                            colors = promptTextFieldColors()
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFB8AD9A)
                        )
                    ) {
                        Text("跳过")
                    }

                    Button(
                        onClick = { onSubmit(values.toMap()) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF5B731),
                            contentColor = Color(0xFF2C2518)
                        )
                    ) {
                        Text("确认", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun promptTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color(0xFFF5EDD8),
    unfocusedTextColor = Color(0xFFF5EDD8),
    cursorColor = Color(0xFFF5B731),
    focusedBorderColor = Color(0xFFF5B731),
    unfocusedBorderColor = Color(0xFF7A6F5F),
    focusedContainerColor = Color(0xFF362F24),
    unfocusedContainerColor = Color(0xFF362F24),
)
