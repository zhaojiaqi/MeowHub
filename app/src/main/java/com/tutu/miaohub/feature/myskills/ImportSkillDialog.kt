package com.tutu.miaohub.feature.myskills

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutu.miaohub.core.database.LocalSkillEntity
import com.tutu.miaohub.core.database.ValidationResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSkillDialog(
    importState: ImportState,
    onValidateJson: (String) -> Unit,
    onImportFile: (android.net.Uri) -> Unit,
    onConfirmImport: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var pasteText by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { onImportFile(it) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "导入 Skill",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("导入文件") },
                    icon = { Icon(Icons.Outlined.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("粘贴 JSON") },
                    icon = { Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            Spacer(Modifier.height(16.dp))

            when (selectedTab) {
                0 -> FileImportTab(
                    importState = importState,
                    onPickFile = { filePicker.launch(arrayOf("application/json", "*/*")) },
                    onConfirmImport = onConfirmImport
                )
                1 -> PasteImportTab(
                    pasteText = pasteText,
                    onPasteTextChange = { pasteText = it },
                    importState = importState,
                    onPasteFromClipboard = {
                        clipboardManager.getText()?.text?.let { pasteText = it }
                    },
                    onValidate = { onValidateJson(pasteText) },
                    onConfirmImport = onConfirmImport
                )
            }
        }
    }
}

@Composable
private fun FileImportTab(
    importState: ImportState,
    onPickFile: () -> Unit,
    onConfirmImport: (String) -> Unit
) {
    Column {
        OutlinedButton(
            onClick = onPickFile,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("选择 JSON 文件")
        }

        Spacer(Modifier.height(12.dp))
        ValidationResultView(importState, onConfirmImport)
    }
}

@Composable
private fun PasteImportTab(
    pasteText: String,
    onPasteTextChange: (String) -> Unit,
    importState: ImportState,
    onPasteFromClipboard: () -> Unit,
    onValidate: () -> Unit,
    onConfirmImport: (String) -> Unit
) {
    Column {
        OutlinedTextField(
            value = pasteText,
            onValueChange = onPasteTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 200.dp),
            placeholder = { Text("在此粘贴 Skill JSON 内容…", fontSize = 14.sp) },
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)
        )

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onPasteFromClipboard,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("从剪贴板粘贴", fontSize = 13.sp)
            }

            Button(
                onClick = onValidate,
                enabled = pasteText.isNotBlank() && !importState.isValidating,
                modifier = Modifier.weight(1f)
            ) {
                if (importState.isValidating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(4.dp))
                Text("验证", fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(12.dp))
        ValidationResultView(importState, onConfirmImport)
    }
}

@Composable
private fun ValidationResultView(
    importState: ImportState,
    onConfirmImport: (String) -> Unit
) {
    val result = importState.validationResult ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        when (result) {
            is ValidationResult.Valid -> {
                val skill = result.skill
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "验证通过",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(skill.displayName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (skill.description.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                skill.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            InfoChip("v${skill.version}")
                            InfoChip("${skill.steps.size} 步骤")
                            if (skill.authorName.isNotEmpty()) InfoChip(skill.authorName)
                        }

                        if (skill.tags.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                skill.tags.take(5).forEach { tag ->
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text(tag, fontSize = 11.sp) },
                                        modifier = Modifier.height(28.dp)
                                    )
                                }
                            }
                        }

                        if (importState.slugExists) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "该 Skill 已存在，导入将覆盖现有数据",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        onConfirmImport(LocalSkillEntity.SOURCE_LOCAL_IMPORT)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !importState.isImporting
                ) {
                    if (importState.isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("导入中…")
                    } else {
                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (importState.slugExists) "覆盖导入" else "确认导入")
                    }
                }
            }

            is ValidationResult.Invalid -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "验证失败",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        result.errors.forEach { error ->
                            Text(
                                "• $error",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoChip(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
    )
}
