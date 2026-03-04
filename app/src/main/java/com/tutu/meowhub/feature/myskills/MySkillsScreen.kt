package com.tutu.meowhub.feature.myskills

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tutu.meowhub.MeowApp
import com.tutu.meowhub.R
import com.tutu.meowhub.core.database.LocalSkillEntity
import com.tutu.meowhub.core.engine.SkillEngine
import com.tutu.meowhub.core.model.ConnectionState
import com.tutu.meowhub.feature.engine.SkillEngineViewModel
import com.tutu.meowhub.feature.market.PermissionCheckDialog
import com.tutu.meowhub.feature.market.allPermissionsGranted

private val cardShape = RoundedCornerShape(16.dp)
private val iconBgShape = RoundedCornerShape(10.dp)
private val tagShape = RoundedCornerShape(4.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MySkillsScreen(
    viewModel: MySkillsViewModel = viewModel(),
    engineViewModel: SkillEngineViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val importState by viewModel.importState.collectAsState()
    val context = LocalContext.current

    var showImportDialog by remember { mutableStateOf(false) }
    var showDetailSheet by remember { mutableStateOf<LocalSkillEntity?>(null) }
    var showEditDialog by remember { mutableStateOf<LocalSkillEntity?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<LocalSkillEntity?>(null) }

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeToast()
        }
    }

    LaunchedEffect(importState.importSuccess) {
        if (importState.importSuccess) {
            showImportDialog = false
            viewModel.resetImportState()
        }
    }

    if (showImportDialog) {
        ImportSkillDialog(
            importState = importState,
            onValidateJson = viewModel::validateJson,
            onImportFile = { uri -> viewModel.importFromFile(context, uri) },
            onConfirmImport = { source -> viewModel.confirmImport(source) },
            onDismiss = {
                showImportDialog = false
                viewModel.resetImportState()
            }
        )
    }

    showDetailSheet?.let { entity ->
        SkillDetailBottomSheet(
            entity = entity,
            engineViewModel = engineViewModel,
            onEdit = {
                showDetailSheet = null
                showEditDialog = entity
            },
            onDuplicate = {
                viewModel.duplicateSkill(entity.id)
                showDetailSheet = null
            },
            onShare = {
                viewModel.shareSkill(context, entity)
            },
            onDelete = {
                showDetailSheet = null
                showDeleteConfirm = entity
            },
            onDismiss = { showDetailSheet = null }
        )
    }

    showEditDialog?.let { entity ->
        EditSkillDialog(
            entity = entity,
            onConfirm = { name, desc, tags ->
                viewModel.updateSkillMetadata(entity.id, name, desc, tags)
                showEditDialog = null
            },
            onDismiss = { showEditDialog = null }
        )
    }

    showDeleteConfirm?.let { entity ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            title = { Text("删除 Skill") },
            text = { Text("确定删除「${entity.displayName}」？此操作不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSkill(entity.id)
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("取消") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    stringResource(R.string.my_skills_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            actions = {
                IconButton(onClick = { showImportDialog = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = "导入 Skill")
                }
            }
        )

        if (uiState.allTags.isNotEmpty()) {
            TagFilterChips(
                tags = uiState.allTags,
                selectedTag = uiState.selectedTag,
                onSelectTag = viewModel::selectTag
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                uiState.isLoading -> LoadingState()
                uiState.skills.isEmpty() -> EmptyState(onImport = { showImportDialog = true })
                else -> SkillList(
                    skills = uiState.skills,
                    onSkillClick = { showDetailSheet = it }
                )
            }
        }
    }
}

@Composable
private fun TagFilterChips(
    tags: List<String>,
    selectedTag: String?,
    onSelectTag: (String?) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        item {
            FilterChip(
                selected = selectedTag == null,
                onClick = { onSelectTag(null) },
                label = { Text("全部", fontSize = 13.sp) },
                leadingIcon = if (selectedTag == null) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null
            )
        }
        items(tags) { tag ->
            FilterChip(
                selected = tag == selectedTag,
                onClick = { onSelectTag(if (tag == selectedTag) null else tag) },
                label = { Text(tag, fontSize = 13.sp) },
                leadingIcon = if (tag == selectedTag) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null
            )
        }
    }
}

@Composable
private fun SkillList(
    skills: List<LocalSkillEntity>,
    onSkillClick: (LocalSkillEntity) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            count = skills.size,
            key = { skills[it].id }
        ) { index ->
            LocalSkillCard(
                skill = skills[index],
                onClick = { onSkillClick(skills[index]) }
            )
        }
    }
}

@Composable
private fun LocalSkillCard(
    skill: LocalSkillEntity,
    onClick: () -> Unit
) {
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant

    val sourceLabel = remember(skill.source) {
        when (skill.source) {
            LocalSkillEntity.SOURCE_MARKET -> "市场"
            LocalSkillEntity.SOURCE_CLIPBOARD -> "粘贴"
            else -> "导入"
        }
    }

    val sourceColor = when (skill.source) {
        LocalSkillEntity.SOURCE_MARKET -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(iconBgShape)
                        .background(primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Extension,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            skill.displayName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            sourceLabel,
                            fontSize = 10.sp,
                            color = sourceColor,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(tagShape)
                                .background(sourceColor.copy(alpha = 0.12f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    Text(
                        "v${skill.version} · ${skill.authorName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }

            if (skill.description.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    skill.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = outlineVariant.copy(alpha = 0.3f))
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Category, contentDescription = null, modifier = Modifier.size(13.dp), tint = onSurfaceVariant)
                    Spacer(Modifier.width(2.dp))
                    Text(skill.category.ifEmpty { "-" }, fontSize = 11.sp, color = onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(13.dp), tint = onSurfaceVariant)
                    Spacer(Modifier.width(2.dp))
                    Text(skill.estimatedTime.ifEmpty { "-" }, fontSize = 11.sp, color = onSurfaceVariant)
                }
                Text(
                    skill.slug,
                    fontSize = 10.sp,
                    color = onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkillDetailBottomSheet(
    entity: LocalSkillEntity,
    engineViewModel: SkillEngineViewModel,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val client = MeowApp.instance.tutuClient
    val connectionState by client.connectionState.collectAsState()
    val engineState by engineViewModel.engineState.collectAsState()
    val repo = MeowApp.instance.skillRepository

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Extension,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(entity.displayName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "v${entity.version} · ${entity.authorName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (entity.description.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    entity.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(Modifier.height(12.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ActionButton(Icons.Outlined.Edit, "编辑", onClick = onEdit)
                ActionButton(Icons.Outlined.ContentCopy, "复制", onClick = onDuplicate)
                ActionButton(Icons.Outlined.Share, "分享", onClick = onShare)
                ActionButton(Icons.Outlined.Delete, "删除", onClick = onDelete,
                    tint = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(16.dp))

            // Run button
            var showPermissionDialog by remember { mutableStateOf(false) }

            Button(
                onClick = {
                    if (allPermissionsGranted(context)) {
                        val detail = repo.toMeowSkillDetail(entity)
                        val activity = context as? android.app.Activity
                        engineViewModel.runLocalSkill(detail, activity)
                    } else {
                        showPermissionDialog = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = connectionState == ConnectionState.CONNECTED &&
                        engineState in listOf(
                            SkillEngine.EngineState.IDLE,
                            SkillEngine.EngineState.FINISHED,
                            SkillEngine.EngineState.STOPPED,
                            SkillEngine.EngineState.ERROR
                        )
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (connectionState != ConnectionState.CONNECTED) "请先连接设备" else "运行 Skill")
            }

            if (showPermissionDialog) {
                PermissionCheckDialog(
                    onDismiss = { showPermissionDialog = false },
                    onAllGranted = {
                        showPermissionDialog = false
                        val detail = repo.toMeowSkillDetail(entity)
                        val activity = context as? android.app.Activity
                        engineViewModel.runLocalSkill(detail, activity)
                    }
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        }
        Text(label, fontSize = 11.sp, color = tint)
    }
}

@Composable
private fun EditSkillDialog(
    entity: LocalSkillEntity,
    onConfirm: (name: String, description: String, tags: List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(entity.displayName) }
    var description by remember { mutableStateOf(entity.description) }
    var tagsText by remember { mutableStateOf("") }

    LaunchedEffect(entity.id) {
        val dao = MeowApp.instance.database.skillDao()
        val tags = dao.getTagsForSkill(entity.id)
        tagsText = tags.joinToString(", ")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑 Skill") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text("标签（逗号分隔）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val tags = tagsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    onConfirm(name, description, tags)
                },
                enabled = name.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
    }
}

@Composable
private fun EmptyState(onImport: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.Inventory2,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "还没有 Skill",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "导入 JSON 文件或从市场收藏",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            FilledTonalButton(onClick = onImport) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("导入 Skill")
            }
        }
    }
}
