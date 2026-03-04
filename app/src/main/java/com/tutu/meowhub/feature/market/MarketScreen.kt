package com.tutu.meowhub.feature.market

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tutu.meowhub.MeowApp
import com.tutu.meowhub.R
import com.tutu.meowhub.core.engine.SkillEngine
import com.tutu.meowhub.core.model.ConnectionState
import com.tutu.meowhub.core.model.MeowSkillItem
import com.tutu.meowhub.core.model.SkillCategory
import com.tutu.meowhub.core.model.SkillSortType
import com.tutu.meowhub.feature.engine.SkillEngineViewModel
import kotlinx.coroutines.flow.distinctUntilChanged

private val skillIconMap: HashMap<String, ImageVector> by lazy {
    hashMapOf(
        "message-circle" to Icons.AutoMirrored.Outlined.Message,
        "message-square" to Icons.AutoMirrored.Outlined.Message,
        "send" to Icons.AutoMirrored.Outlined.Send,
        "phone" to Icons.Outlined.Phone,
        "phone-call" to Icons.Outlined.PhoneInTalk,
        "mail" to Icons.Outlined.Email,
        "camera" to Icons.Outlined.CameraAlt,
        "image" to Icons.Outlined.Image,
        "video" to Icons.Outlined.Videocam,
        "music" to Icons.Outlined.MusicNote,
        "play-circle" to Icons.Outlined.PlayCircle,
        "settings" to Icons.Outlined.Settings,
        "wifi" to Icons.Outlined.Wifi,
        "bluetooth" to Icons.Outlined.Bluetooth,
        "battery" to Icons.Outlined.BatteryFull,
        "map-pin" to Icons.Outlined.LocationOn,
        "map" to Icons.Outlined.Map,
        "navigation" to Icons.Outlined.Navigation,
        "shopping-cart" to Icons.Outlined.ShoppingCart,
        "shopping-bag" to Icons.Outlined.ShoppingBag,
        "credit-card" to Icons.Outlined.CreditCard,
        "clock" to Icons.Outlined.Schedule,
        "calendar" to Icons.Outlined.CalendarToday,
        "alarm-clock" to Icons.Outlined.Alarm,
        "search" to Icons.Outlined.Search,
        "download" to Icons.Outlined.Download,
        "upload" to Icons.Outlined.Upload,
        "file" to Icons.AutoMirrored.Outlined.InsertDriveFile,
        "file-text" to Icons.Outlined.Description,
        "folder" to Icons.Outlined.Folder,
        "trash" to Icons.Outlined.Delete,
        "trash-2" to Icons.Outlined.Delete,
        "edit" to Icons.Outlined.Edit,
        "edit-2" to Icons.Outlined.Edit,
        "edit-3" to Icons.Outlined.Edit,
        "copy" to Icons.Outlined.ContentCopy,
        "clipboard" to Icons.Outlined.ContentPaste,
        "lock" to Icons.Outlined.Lock,
        "unlock" to Icons.Outlined.LockOpen,
        "shield" to Icons.Outlined.Shield,
        "user" to Icons.Outlined.Person,
        "user-plus" to Icons.Outlined.PersonAdd,
        "users" to Icons.Outlined.Group,
        "heart" to Icons.Outlined.FavoriteBorder,
        "star" to Icons.Outlined.StarBorder,
        "bookmark" to Icons.Outlined.BookmarkBorder,
        "bell" to Icons.Outlined.Notifications,
        "globe" to Icons.Outlined.Language,
        "zap" to Icons.Outlined.FlashOn,
        "sun" to Icons.Outlined.LightMode,
        "moon" to Icons.Outlined.DarkMode,
        "eye" to Icons.Outlined.Visibility,
        "eye-off" to Icons.Outlined.VisibilityOff,
        "check-circle" to Icons.Outlined.CheckCircle,
        "x-circle" to Icons.Outlined.Cancel,
        "alert-triangle" to Icons.Outlined.Warning,
        "info" to Icons.Outlined.Info,
        "help-circle" to Icons.AutoMirrored.Outlined.HelpOutline,
        "refresh-cw" to Icons.Outlined.Refresh,
        "power" to Icons.Outlined.PowerSettingsNew,
        "home" to Icons.Outlined.Home,
        "layers" to Icons.Outlined.Layers,
        "terminal" to Icons.Outlined.Terminal,
        "code" to Icons.Outlined.Code,
        "cpu" to Icons.Outlined.Memory,
        "smartphone" to Icons.Outlined.Smartphone,
        "monitor" to Icons.Outlined.Monitor,
        "volume-2" to Icons.AutoMirrored.Outlined.VolumeUp,
        "mic" to Icons.Outlined.Mic,
        "rotate-cw" to Icons.Outlined.Refresh,
        "compass" to Icons.Outlined.Explore,
        "package" to Icons.Outlined.Inventory2,
        "tool" to Icons.Outlined.Build,
        "wrench" to Icons.Outlined.Build,
        "tag" to Icons.AutoMirrored.Outlined.Label,
        "link" to Icons.Outlined.Link,
        "share" to Icons.Outlined.Share,
        "external-link" to Icons.AutoMirrored.Outlined.OpenInNew
    )
}

private fun getSkillIcon(iconName: String): ImageVector =
    skillIconMap[iconName] ?: Icons.Outlined.Extension

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketScreen(
    viewModel: MarketViewModel = viewModel(),
    engineViewModel: SkillEngineViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val saveMessage by viewModel.saveMessage.collectAsState()
    val client = MeowApp.instance.tutuClient
    val connectionState by client.connectionState.collectAsState()
    val engineState by engineViewModel.engineState.collectAsState()

    var selectedSkill by remember { mutableStateOf<MeowSkillItem?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(saveMessage) {
        saveMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.consumeSaveMessage()
        }
    }

    if (selectedSkill != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedSkill = null },
            sheetState = sheetState
        ) {
            SkillDetailSheet(
                skill = selectedSkill!!,
                engineState = engineState,
                connectionState = connectionState,
                engineViewModel = engineViewModel,
                onSaveToLocal = { viewModel.saveToLocal(selectedSkill!!.slug) },
                onDismiss = { selectedSkill = null }
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.ic_meow_logo_golden),
                        contentDescription = "MeowHub",
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("MeowHub", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(
                            stringResource(R.string.app_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            actions = {
                ConnectionStatusAction(
                    connectionState = connectionState,
                    onConnect = { MeowApp.instance.connectWithAuth() },
                    onDisconnect = { client.disconnect() }
                )
                Spacer(Modifier.width(8.dp))
            }
        )

        CategoryChips(
            selected = uiState.currentCategory,
            onSelect = viewModel::setCategory
        )

        SortChips(
            selected = uiState.currentSort,
            onSelect = viewModel::setSort
        )

        Spacer(Modifier.height(4.dp))

        Box(modifier = Modifier.weight(1f)) {
            when {
                uiState.isLoading && uiState.skills.isEmpty() -> LoadingState()
                uiState.error != null && uiState.skills.isEmpty() -> ErrorState(
                    message = uiState.error!!,
                    onRetry = viewModel::loadSkills
                )
                uiState.skills.isEmpty() -> EmptyState()
                else -> SkillList(
                    skills = uiState.skills,
                    isLoadingMore = uiState.isLoadingMore,
                    hasMore = uiState.hasMore,
                    onLoadMore = viewModel::loadMore,
                    onSkillClick = { selectedSkill = it }
                )
            }
        }
    }
}

@Composable
private fun SkillList(
    skills: List<MeowSkillItem>,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onSkillClick: (MeowSkillItem) -> Unit = {}
) {
    val listState = rememberLazyListState()

    InfiniteListHandler(listState = listState, onLoadMore = onLoadMore)

    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            count = skills.size,
            key = { skills[it].id }
        ) { index ->
            SkillCard(
                skill = skills[index],
                primaryContainer = primaryContainer,
                onSurfaceVariant = onSurfaceVariant,
                surfaceVariant = surfaceVariant,
                tertiaryColor = tertiaryColor,
                outlineVariant = outlineVariant,
                primaryColor = primaryColor,
                errorColor = errorColor,
                onClick = { onSkillClick(skills[index]) }
            )
        }

        if (isLoadingMore) {
            item(key = "loading_more") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }

        if (!hasMore && skills.isNotEmpty()) {
            item(key = "end_marker") {
                Text(
                    "- 已加载全部 -",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurfaceVariant.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun InfiniteListHandler(
    listState: LazyListState,
    buffer: Int = 3,
    onLoadMore: () -> Unit
) {
    LaunchedEffect(listState) {
        snapshotFlow {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= totalItems - buffer && totalItems > 0
        }
            .distinctUntilChanged()
            .collect { shouldLoad ->
                if (shouldLoad) onLoadMore()
            }
    }
}

@Composable
private fun CategoryChips(
    selected: SkillCategory,
    onSelect: (SkillCategory) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        items(SkillCategory.entries.toList()) { category ->
            FilterChip(
                selected = category == selected,
                onClick = { onSelect(category) },
                label = { Text(category.labelZh, fontSize = 13.sp) },
                leadingIcon = if (category == selected) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null
            )
        }
    }
}

@Composable
private fun SortChips(
    selected: SkillSortType,
    onSelect: (SkillSortType) -> Unit
) {
    val sortLabels = remember {
        listOf(
            SkillSortType.FEATURED to Icons.Outlined.StarBorder,
            SkillSortType.DOWNLOADS to Icons.AutoMirrored.Outlined.TrendingUp,
            SkillSortType.NEWEST to Icons.Outlined.NewReleases,
            SkillSortType.STARS to Icons.Outlined.FavoriteBorder
        )
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        items(sortLabels) { (sort, icon) ->
            AssistChip(
                onClick = { onSelect(sort) },
                label = { Text(sort.labelZh, fontSize = 12.sp) },
                leadingIcon = {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                border = if (sort == selected) {
                    AssistChipDefaults.assistChipBorder(
                        enabled = true,
                        borderColor = MaterialTheme.colorScheme.primary
                    )
                } else {
                    AssistChipDefaults.assistChipBorder(enabled = true)
                },
                colors = if (sort == selected) {
                    AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    AssistChipDefaults.assistChipColors()
                }
            )
        }
    }
}

private val cardShape = RoundedCornerShape(16.dp)
private val iconShape = RoundedCornerShape(10.dp)
private val tagShape = RoundedCornerShape(4.dp)

@Composable
private fun SkillCard(
    skill: MeowSkillItem,
    primaryContainer: Color,
    onSurfaceVariant: Color,
    surfaceVariant: Color,
    tertiaryColor: Color,
    outlineVariant: Color,
    primaryColor: Color,
    errorColor: Color,
    onClick: () -> Unit = {}
) {
    val icon = remember(skill.icon) { getSkillIcon(skill.icon) }

    val subtitle = remember(skill.version, skill.authorName) {
        "v${skill.version} · ${skill.authorName}"
    }
    val statsDownloads = remember(skill.downloads) { "${skill.downloads}" }
    val statsStars = remember(skill.stars) { "${skill.stars}" }
    val statsTime = remember(skill.estimatedTime) { skill.estimatedTime.ifEmpty { "-" } }
    val rateText = remember(skill.successRate) {
        if (skill.successRate > 0) "%.0f%%".format(skill.successRate) else "-"
    }
    val rateColor = remember(skill.successRate, primaryColor, tertiaryColor, errorColor) {
        when {
            skill.successRate >= 90 -> primaryColor
            skill.successRate >= 70 -> tertiaryColor
            else -> errorColor
        }
    }
    val rateBgColor = remember(rateColor) { rateColor.copy(alpha = 0.12f) }
    val featuredBg = remember(tertiaryColor) { tertiaryColor.copy(alpha = 0.15f) }
    val dividerColor = remember(outlineVariant) { outlineVariant.copy(alpha = 0.3f) }

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
                        .clip(iconShape)
                        .background(primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
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
                        if (skill.featured > 0) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "精选",
                                fontSize = 10.sp,
                                color = tertiaryColor,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clip(tagShape)
                                    .background(featuredBg)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        subtitle,
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

            if (skill.tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    skill.tags.take(4).forEach { tag ->
                        Text(
                            tag,
                            fontSize = 10.sp,
                            color = onSurfaceVariant,
                            modifier = Modifier
                                .clip(tagShape)
                                .background(surfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = dividerColor)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(13.dp), tint = onSurfaceVariant)
                    Spacer(Modifier.width(2.dp))
                    Text(statsDownloads, fontSize = 11.sp, color = onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, modifier = Modifier.size(13.dp), tint = onSurfaceVariant)
                    Spacer(Modifier.width(2.dp))
                    Text(statsStars, fontSize = 11.sp, color = onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(13.dp), tint = onSurfaceVariant)
                    Spacer(Modifier.width(2.dp))
                    Text(statsTime, fontSize = 11.sp, color = onSurfaceVariant)
                }
                Text(
                    rateText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = rateColor,
                    modifier = Modifier
                        .clip(tagShape)
                        .background(rateBgColor)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                "加载中…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("\u26A0\uFE0F", fontSize = 40.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onRetry) {
                Text("重试")
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("\uD83D\uDCE6", fontSize = 40.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "暂无 Skill",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConnectionStatusAction(
    connectionState: ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val (statusColor, statusText) = when (connectionState) {
            ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary to stringResource(R.string.label_connected)
            ConnectionState.CONNECTING -> MaterialTheme.colorScheme.tertiary to stringResource(R.string.status_connecting)
            ConnectionState.AUTHENTICATING -> MaterialTheme.colorScheme.tertiary to stringResource(R.string.status_authenticating)
            ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.error to stringResource(R.string.status_disconnected)
        }
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            statusText,
            style = MaterialTheme.typography.labelSmall,
            color = statusColor
        )
        Spacer(Modifier.width(8.dp))
        when (connectionState) {
            ConnectionState.DISCONNECTED -> {
                FilledTonalButton(
                    onClick = onConnect,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(stringResource(R.string.btn_connect), fontSize = 12.sp)
                }
            }
            ConnectionState.CONNECTING, ConnectionState.AUTHENTICATING -> {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            ConnectionState.CONNECTED -> {
                OutlinedButton(
                    onClick = onDisconnect,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(stringResource(R.string.btn_disconnect), fontSize = 12.sp)
                }
            }
        }
    }
}

// ── Skill 详情 BottomSheet ──

@Composable
private fun SkillDetailSheet(
    skill: MeowSkillItem,
    engineState: SkillEngine.EngineState,
    connectionState: ConnectionState,
    engineViewModel: SkillEngineViewModel,
    onSaveToLocal: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentSkill by engineViewModel.currentSkill.collectAsState()
    val stepStatuses by engineViewModel.stepStatuses.collectAsState()
    val currentStepIndex by engineViewModel.currentStepIndex.collectAsState()
    val runResult by engineViewModel.runResult.collectAsState()
    val isThisSkillRunning = currentSkill?.slug == skill.slug &&
            engineState in listOf(SkillEngine.EngineState.RUNNING, SkillEngine.EngineState.PAUSED, SkillEngine.EngineState.LOADING)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 32.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val icon = remember(skill.icon) { getSkillIcon(skill.icon) }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(skill.displayName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("v${skill.version} · ${skill.authorName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (skill.description.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(skill.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp)
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            DetailStat(Icons.Outlined.Schedule, skill.estimatedTime.ifEmpty { "-" })
            DetailStat(Icons.Outlined.Download, "${skill.downloads}")
            DetailStat(Icons.Outlined.FavoriteBorder, "${skill.stars}")
            DetailStat(Icons.AutoMirrored.Outlined.TrendingUp,
                if (skill.successRate > 0) "%.0f%%".format(skill.successRate) else "-")
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(Modifier.height(16.dp))

        if (isThisSkillRunning) {
            val stepsCount = currentSkill?.steps?.size ?: 0
            Text("执行中 (${currentStepIndex + 1}/$stepsCount)",
                fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            if (stepsCount > 0) {
                LinearProgressIndicator(
                    progress = { (currentStepIndex + 1).toFloat() / stepsCount },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (engineState == SkillEngine.EngineState.RUNNING) {
                    OutlinedButton(onClick = { engineViewModel.pause() }) {
                        Text("暂停")
                    }
                } else if (engineState == SkillEngine.EngineState.PAUSED) {
                    FilledTonalButton(onClick = { engineViewModel.resume() }) {
                        Text("继续")
                    }
                }
                OutlinedButton(
                    onClick = { engineViewModel.stop() },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("停止")
                }
            }
        } else {
            val result = runResult
            if (result != null && currentSkill?.slug == skill.slug) {
                val (statusIcon, statusText, statusColor) = when (result.status) {
                    "success" -> Triple(Icons.Outlined.CheckCircle, "执行完成", MaterialTheme.colorScheme.primary)
                    "aborted" -> Triple(Icons.Outlined.Cancel, "已停止", MaterialTheme.colorScheme.error)
                    else -> Triple(Icons.Outlined.Warning, "执行失败", MaterialTheme.colorScheme.error)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(statusText, color = statusColor, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("${result.completedSteps}/${result.totalSteps} 步",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!result.errorMessage.isNullOrEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(result.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(12.dp))
            }

            var showPermissionDialog by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSaveToLocal,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.BookmarkBorder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("收藏")
                }

                Button(
                    onClick = {
                        if (allPermissionsGranted(context)) {
                            val activity = context as? android.app.Activity
                            engineViewModel.runSkill(skill.slug, activity)
                        } else {
                            showPermissionDialog = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = connectionState == ConnectionState.CONNECTED &&
                            engineState in listOf(SkillEngine.EngineState.IDLE, SkillEngine.EngineState.FINISHED,
                                SkillEngine.EngineState.STOPPED, SkillEngine.EngineState.ERROR)
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (connectionState != ConnectionState.CONNECTED) "连接设备" else "运行")
                }
            }

            if (showPermissionDialog) {
                PermissionCheckDialog(
                    onDismiss = { showPermissionDialog = false },
                    onAllGranted = {
                        showPermissionDialog = false
                        val activity = context as? android.app.Activity
                        engineViewModel.runSkill(skill.slug, activity)
                    }
                )
            }
        }
    }
}

@Composable
private fun DetailStat(icon: ImageVector, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
