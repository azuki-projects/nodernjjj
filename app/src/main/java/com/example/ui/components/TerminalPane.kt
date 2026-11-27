package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TerminalLine
import com.example.data.model.TerminalLineType
import com.example.ui.theme.AmberGlow
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalSyntaxColors
import com.example.ui.theme.NodeEmerald

internal data class TerminalChordOutcome(
    val field: TextFieldValue,
    val stopProcess: Boolean = false,
    val clearTerminal: Boolean = false,
    val historyStep: Int = 0,
    val executeCommand: String? = null
)

private val SHELL_COMMANDS = listOf(
    "node", "npm", "git", "curl", "clear", "cls", "help",
    "ls", "pwd", "cd", "cat", "touch", "mkdir", "rm", "echo"
)

internal fun applyCtrlChord(
    ch: Char,
    current: TextFieldValue
): TerminalChordOutcome {
    val text = current.text
    val cursor = current.selection.start.coerceIn(0, text.length)
    return when (ch.lowercaseChar()) {
        'c', 'd' -> TerminalChordOutcome(
            field = TextFieldValue(""),
            stopProcess = true
        )
        'l' -> TerminalChordOutcome(
            field = current,
            clearTerminal = true
        )
        'u' -> TerminalChordOutcome(
            field = TextFieldValue(text.substring(cursor), TextRange(0))
        )
        'k' -> TerminalChordOutcome(
            field = TextFieldValue(text.substring(0, cursor), TextRange(cursor))
        )
        'w' -> {
            var idx = cursor
            while (idx > 0 && text[idx - 1].isWhitespace()) idx--
            while (idx > 0 && !text[idx - 1].isWhitespace()) idx--
            val updated = text.substring(0, idx) + text.substring(cursor)
            TerminalChordOutcome(field = TextFieldValue(updated, TextRange(idx)))
        }
        'a' -> TerminalChordOutcome(
            field = current.copy(selection = TextRange(0))
        )
        'e' -> TerminalChordOutcome(
            field = current.copy(selection = TextRange(text.length))
        )
        'p' -> TerminalChordOutcome(
            field = current,
            historyStep = -1
        )
        'n' -> TerminalChordOutcome(
            field = current,
            historyStep = 1
        )
        else -> TerminalChordOutcome(field = current)
    }
}

internal fun applyAltChord(
    ch: Char,
    current: TextFieldValue,
    history: List<String>
): TerminalChordOutcome {
    val text = current.text
    val cursor = current.selection.start.coerceIn(0, text.length)
    return when (ch.lowercaseChar()) {
        'b' -> {
            var idx = cursor
            while (idx > 0 && text[idx - 1].isWhitespace()) idx--
            while (idx > 0 && !text[idx - 1].isWhitespace()) idx--
            TerminalChordOutcome(field = current.copy(selection = TextRange(idx)))
        }
        'f' -> {
            var idx = cursor
            while (idx < text.length && text[idx].isWhitespace()) idx++
            while (idx < text.length && !text[idx].isWhitespace()) idx++
            TerminalChordOutcome(field = current.copy(selection = TextRange(idx)))
        }
        'd' -> {
            var idx = cursor
            while (idx < text.length && text[idx].isWhitespace()) idx++
            while (idx < text.length && !text[idx].isWhitespace()) idx++
            val updated = text.substring(0, cursor) + text.substring(idx)
            TerminalChordOutcome(field = TextFieldValue(updated, TextRange(cursor)))
        }
        '.' -> {
            val lastArg = history.lastOrNull()
                ?.trim()
                ?.split(Regex("\\s+"))
                ?.lastOrNull()
                .orEmpty()
            if (lastArg.isEmpty()) {
                TerminalChordOutcome(field = current)
            } else {
                val updated = text.substring(0, cursor) + lastArg + text.substring(cursor)
                val nextCursor = cursor + lastArg.length
                TerminalChordOutcome(field = TextFieldValue(updated, TextRange(nextCursor)))
            }
        }
        'c', 'x' -> TerminalChordOutcome(field = TextFieldValue(""))
        'l' -> TerminalChordOutcome(field = current, executeCommand = "ls")
        'h' -> TerminalChordOutcome(field = current, executeCommand = "help")
        else -> TerminalChordOutcome(field = current)
    }
}

internal fun autocompleteTerminalInput(
    current: TextFieldValue,
    workspacePaths: List<String>
): TextFieldValue {
    val text = current.text
    val cursor = current.selection.start.coerceIn(0, text.length)
    val beforeCursor = text.substring(0, cursor)
    val tokenStart = beforeCursor.lastIndexOf(' ').let { if (it < 0) 0 else it + 1 }
    val prefix = beforeCursor.substring(tokenStart)
    val isFirstToken = !beforeCursor.substring(0, tokenStart).contains(Regex("\\S"))
    val candidates = if (isFirstToken) SHELL_COMMANDS else workspacePaths
    val matches = candidates.filter { it.startsWith(prefix, ignoreCase = true) }
    if (matches.isEmpty()) return current

    val replacement = if (matches.size == 1) {
        matches.first() + if (isFirstToken) " " else ""
    } else {
        var common = matches.first()
        for (item in matches.drop(1)) {
            val maxLen = minOf(common.length, item.length)
            var idx = 0
            while (idx < maxLen && common[idx] == item[idx]) idx++
            common = common.substring(0, idx)
        }
        if (common.length > prefix.length) common else matches.first()
    }

    val updated = text.substring(0, tokenStart) + replacement + text.substring(cursor)
    val nextCursor = tokenStart + replacement.length
    return TextFieldValue(updated, TextRange(nextCursor))
}

@Composable
fun TerminalPane(
    entryFile: String,
    currentDirLabel: String,
    lines: List<TerminalLine>,
    commandHistory: List<String>,
    isRunning: Boolean,
    activeServerPort: Int?,
    lanAddresses: List<String>,
    localHostingEnabled: Boolean,
    wakeLockEnabled: Boolean,
    runningNotificationEnabled: Boolean,
    onToggleLocalHosting: (Boolean) -> Unit,
    onToggleWakeLock: (Boolean) -> Unit,
    onToggleRunningNotification: (Boolean) -> Unit,
    onExecuteCommand: (String) -> Unit,
    onStopProcess: () -> Unit,
    onTestServer: () -> Unit,
    onClear: () -> Unit,
    workspacePaths: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    val syntaxColors = LocalSyntaxColors.current
    val listState = rememberLazyListState()
    var commandField by remember { mutableStateOf(TextFieldValue("")) }
    var historyCursor by remember { mutableIntStateOf(-1) }
    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.lastIndex)
        }
    }

    fun stepHistory(direction: Int) {
        if (commandHistory.isEmpty()) return
        if (direction < 0) {
            val nextIdx = if (historyCursor < 0) commandHistory.lastIndex else (historyCursor - 1).coerceAtLeast(0)
            historyCursor = nextIdx
            val cmd = commandHistory[nextIdx]
            commandField = TextFieldValue(cmd, TextRange(cmd.length))
        } else if (historyCursor >= 0) {
            if (historyCursor >= commandHistory.lastIndex) {
                historyCursor = -1
                commandField = TextFieldValue("")
            } else {
                val nextIdx = historyCursor + 1
                historyCursor = nextIdx
                val cmd = commandHistory[nextIdx]
                commandField = TextFieldValue(cmd, TextRange(cmd.length))
            }
        }
    }

    fun handleChordOutcome(outcome: TerminalChordOutcome) {
        commandField = outcome.field
        if (outcome.stopProcess) {
            historyCursor = -1
            onStopProcess()
        }
        if (outcome.clearTerminal) {
            onClear()
        }
        if (outcome.historyStep != 0) {
            stepHistory(outcome.historyStep)
        }
        if (!outcome.executeCommand.isNullOrBlank()) {
            historyCursor = -1
            onExecuteCommand(outcome.executeCommand)
        }
    }

    fun submitCommand() {
        val text = commandField.text
        if (text.isNotBlank()) {
            onExecuteCommand(text)
            commandField = TextFieldValue("")
            historyCursor = -1
            ctrlActive = false
            altActive = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(syntaxColors.terminalBg)
    ) {
        // Terminal Status Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isRunning) NodeEmerald else MaterialTheme.colorScheme.onSurfaceVariant)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "~/$currentDirLabel",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (isRunning) {
                    Surface(
                        onClick = onStopProcess,
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.testTag("terminal_stop_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Stop", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                        }
                    }
                } else {
                    Surface(
                        onClick = { onExecuteCommand("npm start") },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.testTag("terminal_start_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Start", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("npm start", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }

                IconButton(
                    onClick = onClear,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("terminal_clear_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear terminal",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Hosting, WakeLock & Notification Control Strip
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 10.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                FilterChip(
                    selected = localHostingEnabled,
                    onClick = { onToggleLocalHosting(!localHostingEnabled) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Public,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (localHostingEnabled) CyanPulse else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    label = {
                        Text(
                            if (localHostingEnabled) "LAN Host: 0.0.0.0" else "Loopback Only",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.testTag("toggle_local_hosting_chip")
                )
            }
            item {
                FilterChip(
                    selected = wakeLockEnabled,
                    onClick = { onToggleWakeLock(!wakeLockEnabled) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.BatteryChargingFull,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (wakeLockEnabled) AmberGlow else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    label = {
                        Text(
                            if (wakeLockEnabled) "WakeLock ON" else "WakeLock OFF",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.testTag("toggle_wakelock_chip")
                )
            }
            item {
                FilterChip(
                    selected = runningNotificationEnabled,
                    onClick = { onToggleRunningNotification(!runningNotificationEnabled) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.NotificationsActive,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (runningNotificationEnabled) NodeEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    label = {
                        Text(
                            if (runningNotificationEnabled) "Notify Running" else "Notify OFF",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.testTag("toggle_notification_chip")
                )
            }
        }

        // Active HTTP Server Banner (Loopback + LAN IP)
        AnimatedVisibility(visible = activeServerPort != null) {
            val port = activeServerPort ?: 3000
            val lanIp = if (localHostingEnabled) lanAddresses.firstOrNull() else null
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Http, contentDescription = null, tint = CyanPulse, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Local: http://127.0.0.1:$port",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (lanIp != null) {
                                Text(
                                    text = "LAN: http://$lanIp:$port",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NodeEmerald,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    FilledTonalButton(
                        onClick = onTestServer,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("test_http_server_button")
                    ) {
                        Text("Test GET /", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // Scrollable & Selectable Output Stream
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            SelectionContainer(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    items(lines, key = { it.id }) { line ->
                        val color = when (line.type) {
                            TerminalLineType.COMMAND -> syntaxColors.terminalCommand
                            TerminalLineType.STDOUT -> syntaxColors.terminalStdout
                            TerminalLineType.STDERR -> syntaxColors.terminalStderr
                            TerminalLineType.INFO -> syntaxColors.terminalInfo
                            TerminalLineType.SUCCESS -> syntaxColors.terminalSuccess
                            TerminalLineType.SYSTEM -> CyanPulse
                        }
                        Text(
                            text = line.text,
                            softWrap = true,
                            style = TextStyle(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp,
                                fontWeight = if (line.type == TerminalLineType.COMMAND) FontWeight.SemiBold else FontWeight.Normal,
                                color = color
                            )
                        )
                    }
                }
            }
        }

        // Quick Command Chips
        val quickCommands = listOf(
            "npm start",
            "node $entryFile",
            "npm list",
            "npm update",
            "ls",
            "node -v",
            "help"
        )
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(quickCommands) { cmd ->
                AssistChip(
                    onClick = { onExecuteCommand(cmd) },
                    label = {
                        Text(
                            text = cmd,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Terminal Modifier & Control Key Bar (Ctrl, Alt, Tab, Esc, ^C, Arrows)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                TerminalKeyCap(
                    label = "Ctrl",
                    active = ctrlActive,
                    activeColor = NodeEmerald,
                    onClick = {
                        ctrlActive = !ctrlActive
                        if (ctrlActive) altActive = false
                    },
                    modifier = Modifier.testTag("terminal_ctrl_key")
                )
            }
            item {
                TerminalKeyCap(
                    label = "Alt",
                    active = altActive,
                    activeColor = AmberGlow,
                    onClick = {
                        altActive = !altActive
                        if (altActive) ctrlActive = false
                    },
                    modifier = Modifier.testTag("terminal_alt_key")
                )
            }
            item {
                TerminalKeyCap(
                    label = "^C",
                    active = false,
                    onClick = {
                        ctrlActive = false
                        altActive = false
                        commandField = TextFieldValue("")
                        historyCursor = -1
                        onStopProcess()
                    },
                    modifier = Modifier.testTag("terminal_ctrl_c_key")
                )
            }
            item {
                TerminalKeyCap(
                    label = "Tab",
                    active = false,
                    onClick = {
                        commandField = autocompleteTerminalInput(commandField, workspacePaths)
                    },
                    modifier = Modifier.testTag("terminal_tab_key")
                )
            }
            item {
                TerminalKeyCap(
                    label = "Esc",
                    active = false,
                    onClick = {
                        ctrlActive = false
                        altActive = false
                        historyCursor = -1
                    },
                    modifier = Modifier.testTag("terminal_esc_key")
                )
            }
            item {
                TerminalKeyCap(
                    label = "↑",
                    active = false,
                    onClick = { stepHistory(-1) },
                    modifier = Modifier.testTag("terminal_up_key")
                )
            }
            item {
                TerminalKeyCap(
                    label = "↓",
                    active = false,
                    onClick = { stepHistory(1) },
                    modifier = Modifier.testTag("terminal_down_key")
                )
            }
            item {
                TerminalKeyCap(
                    label = "/",
                    active = false,
                    onClick = {
                        val cursor = commandField.selection.start.coerceIn(0, commandField.text.length)
                        val next = commandField.text.substring(0, cursor) + "/" + commandField.text.substring(cursor)
                        commandField = TextFieldValue(next, TextRange(cursor + 1))
                    }
                )
            }
            item {
                TerminalKeyCap(
                    label = "-",
                    active = false,
                    onClick = {
                        val cursor = commandField.selection.start.coerceIn(0, commandField.text.length)
                        val next = commandField.text.substring(0, cursor) + "-" + commandField.text.substring(cursor)
                        commandField = TextFieldValue(next, TextRange(cursor + 1))
                    }
                )
            }
        }

        // Interactive Command Prompt
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (ctrlActive || altActive) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (ctrlActive) NodeEmerald.copy(alpha = 0.2f) else AmberGlow.copy(alpha = 0.2f),
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .testTag("terminal_modifier_badge")
                ) {
                    Text(
                        text = if (ctrlActive) "CTRL+" else "ALT+",
                        style = TextStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (ctrlActive) NodeEmerald else AmberGlow
                        ),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = "$",
                style = TextStyle(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = NodeEmerald
                )
            )
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (commandField.text.isEmpty()) {
                    Text(
                        text = when {
                            ctrlActive -> "Press C, L, U, K, W, A, E, P, N..."
                            altActive -> "Press B, F, D, ., C, L, H..."
                            else -> "Enter command (node, npm, git, curl, ls...)"
                        },
                        style = TextStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = commandField,
                    onValueChange = { newValue ->
                        val oldText = commandField.text
                        if ((ctrlActive || altActive) && newValue.text.length == oldText.length + 1) {
                            val insertIdx = (newValue.selection.start - 1).coerceIn(0, newValue.text.lastIndex)
                            val ch = newValue.text[insertIdx]
                            val outcome = if (ctrlActive) {
                                applyCtrlChord(ch, commandField)
                            } else {
                                applyAltChord(ch, commandField, commandHistory)
                            }
                            ctrlActive = false
                            altActive = false
                            handleChordOutcome(outcome)
                        } else {
                            ctrlActive = false
                            altActive = false
                            commandField = newValue
                            historyCursor = -1
                        }
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(NodeEmerald),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = { submitCommand() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when {
                                keyEvent.isCtrlPressed -> {
                                    val ch = keyToChar(keyEvent.key)
                                    if (ch != null) {
                                        ctrlActive = false
                                        altActive = false
                                        handleChordOutcome(applyCtrlChord(ch, commandField))
                                        true
                                    } else false
                                }
                                keyEvent.isAltPressed -> {
                                    val ch = keyToChar(keyEvent.key)
                                    if (ch != null) {
                                        ctrlActive = false
                                        altActive = false
                                        handleChordOutcome(applyAltChord(ch, commandField, commandHistory))
                                        true
                                    } else false
                                }
                                keyEvent.key == Key.DirectionUp -> {
                                    stepHistory(-1)
                                    true
                                }
                                keyEvent.key == Key.DirectionDown -> {
                                    stepHistory(1)
                                    true
                                }
                                keyEvent.key == Key.Tab -> {
                                    commandField = autocompleteTerminalInput(commandField, workspacePaths)
                                    true
                                }
                                keyEvent.key == Key.Escape -> {
                                    ctrlActive = false
                                    altActive = false
                                    true
                                }
                                else -> false
                            }
                        }
                        .testTag("terminal_command_input")
                )
            }

            if (commandHistory.isNotEmpty()) {
                IconButton(
                    onClick = { stepHistory(-1) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Previous command",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            IconButton(
                onClick = { submitCommand() },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("terminal_send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Run command",
                    tint = NodeEmerald,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun TerminalKeyCap(
    label: String,
    active: Boolean,
    activeColor: androidx.compose.ui.graphics.Color = NodeEmerald,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (active) activeColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            width = 1.dp,
            color = if (active) activeColor else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier.defaultMinSize(minWidth = 44.dp, minHeight = 36.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (active) activeColor else MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

private fun keyToChar(key: Key): Char? = when (key) {
    Key.A -> 'a'
    Key.B -> 'b'
    Key.C -> 'c'
    Key.D -> 'd'
    Key.E -> 'e'
    Key.F -> 'f'
    Key.H -> 'h'
    Key.K -> 'k'
    Key.L -> 'l'
    Key.N -> 'n'
    Key.P -> 'p'
    Key.U -> 'u'
    Key.W -> 'w'
    Key.X -> 'x'
    Key.Period -> '.'
    else -> null
}
