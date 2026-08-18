package com.jarona.helper

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class BuffType { Strength, Agility }
private data class Buff(val type: BuffType, val amount: Int, val rounds: Int? = null)
private data class OperationRecord(
    val round: Int,
    val key: String,
    val text: String,
    val unit: String? = null,
    val count: Int = 1
) {
    fun displayText(): String = unit?.let { "$text $count $it" } ?: text
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BoardGameHelperApp() }
    }
}

@Composable
private fun BoardGameHelperApp() {
    val context = LocalContext.current
    val colorScheme: ColorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicLightColorScheme(context)
    } else {
        androidx.compose.material3.lightColorScheme()
    }

    MaterialTheme(colorScheme = colorScheme) {
        BoardGameContent()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoardGameContent() {
    var hp by remember { mutableIntStateOf(50) }
    // 0 表示未开启限伤。
    var hpLossLimit by remember { mutableIntStateOf(0) }
    var hpLostThisRound by remember { mutableIntStateOf(0) }
    var block by remember { mutableIntStateOf(0) }
    var retainedBlock by remember { mutableIntStateOf(0) }
    var retainAllBlockThisRound by remember { mutableStateOf(false) }
    var round by remember { mutableIntStateOf(1) }
    var hasFirstPlayer by remember { mutableStateOf(true) }
    var intimidation by remember { mutableStateOf(false) }
    val infiniteBuffs = remember { mutableStateListOf<Buff>() }
    val timedBuffs = remember { mutableStateListOf<Buff>() }
    val operationRecords = remember { mutableStateListOf<OperationRecord>() }
    var dialogType by remember { mutableStateOf<BuffType?>(null) }
    var removeMode by remember { mutableStateOf(false) }
    var retainDialog by remember { mutableStateOf(false) }
    var hpLossLimitDialog by remember { mutableStateOf(false) }
    var overflowMenuExpanded by remember { mutableStateOf(false) }
    var showRecords by remember { mutableStateOf(false) }

    fun recordOperation(key: String, text: String, unit: String? = null, count: Int = 1) {
        val previous = operationRecords.lastOrNull()
        if (previous != null && previous.round == round && previous.key == key && previous.text == text && previous.unit == unit) {
            operationRecords[operationRecords.lastIndex] = previous.copy(count = previous.count + count)
        } else {
            operationRecords.add(OperationRecord(round, key, text, unit, count))
        }
    }

    fun loseHp(amount: Int = 1) {
        // 限伤仅用于提示：即使达到上限，仍允许手动继续调整生命值。
        if (amount > 0) {
            hp -= amount
            hpLostThisRound += amount
        }
    }

    // 记录页拦截系统返回手势/按键，避免 Activity 被直接结束而看起来像闪退。
    BackHandler(enabled = showRecords) { showRecords = false }

    if (showRecords) {
        RecordScreen(records = operationRecords, onBack = { showRecords = false })
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Jarona", fontWeight = FontWeight.Bold) },
                    actions = {
                        Box {
                            IconButton(onClick = { overflowMenuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "更多选项")
                            }
                            DropdownMenu(
                                expanded = overflowMenuExpanded,
                                onDismissRequest = { overflowMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("记录") },
                                    onClick = {
                                        overflowMenuExpanded = false
                                        showRecords = true
                                    }
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                Surface(shadowElevation = 8.dp) {
                    Button(
                        onClick = {
                            recordOperation("roundEnd", "结束本轮")
                            if (retainAllBlockThisRound) {
                                retainAllBlockThisRound = false
                            } else {
                                // 保留格挡只会在回合结束时减少格挡，绝不会补足格挡。
                                block = minOf(block, retainedBlock)
                            }
                            hpLostThisRound = 0
                            val nextHasFirstPlayer = !hasFirstPlayer
                            val shifted = timedBuffs.mapNotNull { buff ->
                                val remainingRounds = (buff.rounds ?: 1) - 1
                                if (remainingRounds > 0) buff.copy(rounds = remainingRounds) else null
                            }
                            timedBuffs.clear()
                            timedBuffs.addAll(shifted)
                            hasFirstPlayer = nextHasFirstPlayer
                            round++
                            if (intimidation && hasFirstPlayer) {
                                timedBuffs.add(Buff(BuffType.Strength, 1, 1))
                                timedBuffs.add(Buff(BuffType.Agility, 1, 1))
                                recordOperation("intimidationStrength", "威慑获得", "点力量")
                                recordOperation("intimidationAgility", "威慑获得", "点敏捷")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("每轮结束") }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("第 $round 轮", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                FirstPlayerMarker(
                    hasFirstPlayer = hasFirstPlayer,
                    intimidation = intimidation,
                    onIntimidationChange = { enabled ->
                        if (intimidation != enabled) {
                            intimidation = enabled
                            recordOperation(
                                key = if (enabled) "intimidationOn" else "intimidationOff",
                                text = if (enabled) "开启威慑" else "关闭威慑"
                            )
                        }
                    },
                    onToggle = {
                        hasFirstPlayer = !hasFirstPlayer
                        recordOperation(
                            key = if (hasFirstPlayer) "firstPlayerOn" else "firstPlayerOff",
                            text = if (hasFirstPlayer) "获得先手标记" else "移除先手标记"
                        )
                    }
                )
                val hpLossLimitReached = hpLossLimit > 0 && hpLostThisRound >= hpLossLimit
                StatusCard(
                    title = if (hpLossLimitReached) "生命值（已达到限伤${hpLossLimit}点）" else "生命值",
                    value = hp,
                    color = MaterialTheme.colorScheme.error
                ) {
                    ActionButton("生命值+1") {
                        hp++
                        recordOperation("hpGain", "获得", "点生命值")
                    }
                    ActionButton("生命值-1") {
                        loseHp()
                        recordOperation("hpLoss", "失去", "点生命值")
                    }
                    ActionButton("受到伤害") {
                        if (block > 0) block-- else loseHp()
                        recordOperation("damage", "受到", "点伤害")
                    }
                    ActionButton("限伤：$hpLossLimit") { hpLossLimitDialog = true }
                }
                StatusCard("格挡", block, MaterialTheme.colorScheme.secondaryContainer) {
                    ActionButton("格挡+1") {
                        block++
                        recordOperation("blockGain", "获得", "点格挡")
                    }
                    ActionButton("格挡-1") {
                        if (block > 0) {
                            block--
                            recordOperation("blockLoss", "失去", "点格挡")
                        }
                    }
                    ActionButton("清空") {
                        if (block > 0) {
                            block = 0
                            recordOperation("blockClear", "清空格挡")
                        }
                    }
                    ActionButton("保留：$retainedBlock") { retainDialog = true }
                }
                BuffCard(
                    infiniteBuffs = infiniteBuffs,
                    timedBuffs = timedBuffs,
                    removeMode = removeMode,
                    onRemoveModeChange = { removeMode = it },
                    onAdd = { dialogType = it },
                    onRemove = { buff, timed ->
                        val target = if (timed) timedBuffs else infiniteBuffs
                        val index = target.indexOfFirst {
                            it.type == buff.type && it.rounds == buff.rounds
                        }
                        if (index >= 0) {
                            val current = target[index]
                            val step = if (current.amount < 0) 1 else -1
                            val remaining = current.amount + step
                            if (remaining == 0) target.removeAt(index)
                            else target[index] = current.copy(amount = remaining)
                            recordOperation(
                                key = "removeBuff:${buff.type}",
                                text = "移除",
                                unit = "点${if (buff.type == BuffType.Strength) "力量" else "敏捷"}"
                            )
                        }
                    }
                )
            }
        }

        dialogType?.let { type ->
            BuffDialog(type = type, onDismiss = { dialogType = null }) { amount, rounds, infinite ->
                if (amount != 0) {
                    val normalizedRounds = rounds.coerceAtLeast(1)
                    val newBuff = Buff(type, amount, if (infinite) null else normalizedRounds)
                    if (infinite) infiniteBuffs.add(newBuff) else timedBuffs.add(newBuff)
                    val buffName = if (type == BuffType.Strength) "力量" else "敏捷"
                    val duration = if (infinite) "永久Buff" else "${normalizedRounds}轮"
                    recordOperation(
                        key = "addBuff:$type:$duration",
                        text = "获得",
                        unit = "点$buffName（$duration）",
                        count = amount
                    )
                }
                dialogType = null
            }
        }
        if (retainDialog) {
            RetainedBlockDialog(
                value = retainedBlock,
                retainAllBlockThisRound = retainAllBlockThisRound,
                onRetainAllBlockChange = { enabled ->
                    if (retainAllBlockThisRound != enabled) {
                        retainAllBlockThisRound = enabled
                        recordOperation(
                            key = if (enabled) "retainAllOn" else "retainAllOff",
                            text = if (enabled) "本轮保留全部格挡" else "取消本轮保留全部格挡"
                        )
                    }
                },
                onDismiss = { retainDialog = false },
                onConfirm = { value ->
                    if (retainedBlock != value) {
                        retainedBlock = value
                        recordOperation("retainBlock", "设置每轮保留格挡为$value 点")
                    }
                }
            )
        }
        if (hpLossLimitDialog) {
            LimitHpLossDialog(
                value = hpLossLimit,
                onDismiss = { hpLossLimitDialog = false },
                onConfirm = { value ->
                    if (hpLossLimit != value) {
                        hpLossLimit = value
                        recordOperation("hpLossLimit", "设置限伤为$value 点")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordScreen(records: List<OperationRecord>, onBack: () -> Unit) {
    val timelineColor = Color(0xFFD1D5DB)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("记录", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            if (records.isEmpty()) {
                Text("暂无记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val groupedRecords = records.groupBy { it.round }.toList()
                groupedRecords.forEachIndexed { index, (recordRound, roundRecords) ->
                    Row(Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .width(88.dp)
                                .fillMaxHeight()
                        ) {
                            Text(
                                "第 $recordRound 轮",
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(top = 2.dp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (index < groupedRecords.lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 38.dp, end = 8.dp)
                                        .width(4.dp)
                                        .fillMaxHeight()
                                        .background(timelineColor, RoundedCornerShape(2.dp))
                                )
                            }
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 14.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            roundRecords.forEach { record ->
                                Text(record.displayText())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FirstPlayerMarker(
    hasFirstPlayer: Boolean,
    intimidation: Boolean,
    onIntimidationChange: (Boolean) -> Unit,
    onToggle: () -> Unit
) {
    val green = Color(0xFF1B5E20)
    Box(Modifier.fillMaxWidth().height(106.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size(92.dp).graphicsLayer { rotationZ = 45f }.clickable(onClick = onToggle),
                shape = RoundedCornerShape(14.dp),
                color = if (hasFirstPlayer) green else green.copy(alpha = 0.38f)
            ) {
                Box(Modifier.graphicsLayer { rotationZ = -45f }, contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("+1", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("先手标记", fontSize = 11.sp, color = Color.White)
                    }
                }
            }
        }
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = intimidation, onCheckedChange = onIntimidationChange)
            Text("威慑", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    value: Int,
    color: Color,
    actions: @Composable RowScope.() -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = color), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val isHealthStatus = title.startsWith("生命值")
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isHealthStatus) Color.White else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.weight(1f))
                Text(
                    value.toString(),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isHealthStatus) Color.White else MaterialTheme.colorScheme.onSurface
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
        }
    }
}

@Composable
private fun RowScope.ActionButton(label: String, onClick: () -> Unit) {
    val isHealthAction = label.startsWith("生命值") || label == "受到伤害" || label.startsWith("限伤：")
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        colors = if (isHealthAction) {
            androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        } else {
            androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
        },
        border = if (isHealthAction) {
            BorderStroke(1.dp, Color.White.copy(alpha = 0.8f))
        } else {
            androidx.compose.material3.ButtonDefaults.outlinedButtonBorder
        }
    ) {
        Text(label, fontSize = 12.sp)
    }
}

@Composable
private fun BuffCard(
    infiniteBuffs: List<Buff>,
    timedBuffs: List<Buff>,
    removeMode: Boolean,
    onRemoveModeChange: (Boolean) -> Unit,
    onAdd: (BuffType) -> Unit,
    onRemove: (Buff, Boolean) -> Unit
) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Buff Token", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                FilterChip(
                    selected = removeMode,
                    onClick = { onRemoveModeChange(!removeMode) },
                    label = { Text(if (removeMode) "完成移除" else "移除Buff") }
                )
            }
            Text("永久Buff", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            BuffRow(infiniteBuffs, timed = false, removeMode, onRemove)
            HorizontalDivider()
            Text("临时Buff", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

            val groupedTimed = timedBuffs.groupBy { it.rounds ?: 1 }
            val maxSlot = maxOf(3, groupedTimed.keys.maxOrNull() ?: 3)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(maxSlot) { index ->
                    val slot = index + 1
                    val merged = mergeBuffs(groupedTimed[slot].orEmpty())
                    val contentWidth = (merged.sumOf { buffTokenWidth(it).value.toInt() } + (merged.size - 1).coerceAtLeast(0) * 6).coerceAtLeast(72)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width((contentWidth + 16).dp)) {
                        Text(slot.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            modifier = Modifier
                                .width((contentWidth + 8).dp)
                                .height(58.dp)
                                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(12.dp))
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            merged.forEach { buff ->
                                BuffToken(buff, removeMode) { onRemove(buff, true) }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onAdd(BuffType.Strength) }, modifier = Modifier.weight(1f)) {
                    Icon(painterResource(R.drawable.swords), null)
                    Spacer(Modifier.width(4.dp))
                    Text("获得力量")
                }
                Button(onClick = { onAdd(BuffType.Agility) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Shield, null)
                    Spacer(Modifier.width(4.dp))
                    Text("获得敏捷")
                }
            }
        }
    }
}

private fun mergeBuffs(buffs: List<Buff>): List<Buff> = buffs
    .groupBy { it.type }
    .mapNotNull { (type, items) ->
        val amount = items.sumOf { it.amount }
        if (amount == 0) null else Buff(type, amount, items.first().rounds)
    }

@Composable
private fun BuffRow(buffs: List<Buff>, timed: Boolean, removeMode: Boolean, onRemove: (Buff, Boolean) -> Unit) {
    val merged = mergeBuffs(buffs)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        merged.forEach { buff -> BuffToken(buff, removeMode) { onRemove(buff, timed) } }
        if (merged.isEmpty()) Text("暂无", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun buffTokenWidth(buff: Buff) = (kotlin.math.abs(buff.amount).coerceAtLeast(1) * 25 + (kotlin.math.abs(buff.amount).coerceAtLeast(1) - 1) * 5 + 16).dp

@Composable
private fun BuffToken(buff: Buff, removeMode: Boolean, onClick: () -> Unit) {
    val tint = if (buff.type == BuffType.Strength) Color(0xFFC62828) else Color(0xFF2E7D32)
    val iconCount = kotlin.math.abs(buff.amount).coerceAtLeast(1)
    Surface(
        modifier = Modifier.width(buffTokenWidth(buff)).height(54.dp).clickable(enabled = removeMode, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = tint.copy(alpha = 0.16f),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(iconCount) {
                if (buff.type == BuffType.Strength) {
                    Icon(painterResource(R.drawable.swords), null, tint = tint, modifier = Modifier.size(25.dp))
                } else {
                    Icon(Icons.Default.Shield, null, tint = tint, modifier = Modifier.size(25.dp))
                }
            }
        }
    }
}

@Composable
private fun BuffDialog(type: BuffType, onDismiss: () -> Unit, onConfirm: (Int, Int, Boolean) -> Unit) {
    var amount by remember { mutableIntStateOf(1) }
    var rounds by remember { mutableIntStateOf(1) }
    var infinite by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("获得${if (type == BuffType.Strength) "力量" else "敏捷"}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Stepper("点数", amount, { amount-- }, { amount++ }, allowZero = false)
                Stepper("轮数", rounds, { rounds-- }, { rounds++ }, enabled = !infinite, allowZero = false)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = infinite, onCheckedChange = { infinite = it })
                    Text("永久Buff")
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(amount, rounds, infinite) }) { Text("添加") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun Stepper(
    label: String,
    value: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    enabled: Boolean = true,
    allowZero: Boolean = true
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(48.dp))
        IconButton(enabled = enabled && (allowZero || value > 1), onClick = onMinus) { Icon(Icons.Default.Remove, null) }
        Text(value.toString(), modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
        IconButton(enabled = enabled, onClick = onPlus) { Icon(Icons.Default.Add, null) }
    }
}

@Composable
private fun RetainedBlockDialog(
    value: Int,
    retainAllBlockThisRound: Boolean,
    onRetainAllBlockChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var current by remember { mutableIntStateOf(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("每轮保留格挡") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Stepper("数值", current, { if (current > 0) current-- }, { current++ })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = retainAllBlockThisRound,
                        onCheckedChange = onRetainAllBlockChange
                    )
                    Text("本轮保留全部格挡")
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(current); onDismiss() }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}











@Composable
private fun LimitHpLossDialog(
    value: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var current by remember { mutableIntStateOf(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("每轮限制生命值损失") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Stepper("限伤", current, { if (current > 0) current-- }, { current++ })
            }
        },
        confirmButton = { Button(onClick = { onConfirm(current); onDismiss() }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}









