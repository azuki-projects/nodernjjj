package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.EditorTab
import com.example.ui.theme.AmberGlow
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalSyntaxColors
import com.example.ui.theme.NodeEmerald
import org.json.JSONObject

@Composable
fun CodeEditorView(
    tabs: List<EditorTab>,
    activeTabPath: String?,
    fontSizeSp: Int,
    wordWrap: Boolean,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onOpenFilesPane: () -> Unit,
    modifier: Modifier = Modifier
) {
    val syntaxColors = LocalSyntaxColors.current
    val activeTab = tabs.find { it.relativePath == activeTabPath }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(syntaxColors.editorBackground)
    ) {
        // Open File Tabs Bar
        if (tabs.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(tabs, key = { it.relativePath }) { tab ->
                    val isSelected = tab.relativePath == activeTabPath
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest
                        else MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .testTag("editor_tab_${tab.name}")
                            .clickable { onSelectTab(tab.relativePath) }
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(extensionAccentColor(tab.extension))
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = tab.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (tab.isDirty) {
                                Spacer(Modifier.width(5.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(AmberGlow)
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = { onCloseTab(tab.relativePath) },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close ${tab.name}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        if (activeTab == null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "No File Open",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Select a script or JSON file from the workspace explorer to start editing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        onClick = onOpenFilesPane,
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Browse Workspace Files",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }
            return@Column
        }

        var textFieldValue by remember(activeTab.relativePath) {
            mutableStateOf(TextFieldValue(activeTab.content))
        }

        LaunchedEffect(activeTab.relativePath, activeTab.content) {
            if (textFieldValue.text != activeTab.content) {
                val safeCursor = textFieldValue.selection.start.coerceIn(0, activeTab.content.length)
                textFieldValue = TextFieldValue(activeTab.content, TextRange(safeCursor))
            }
        }

        val vScroll = rememberScrollState()
        val hScroll = rememberScrollState()
        val lineHeightSp = (fontSizeSp * 1.45f).sp
        val lineStartOffsets = remember(textFieldValue.text) {
            computeLineStartOffsets(textFieldValue.text)
        }
        val cursorOffset = textFieldValue.selection.start.coerceIn(0, textFieldValue.text.length)
        val beforeCursor = textFieldValue.text.substring(0, cursorOffset)
        val cursorLine = beforeCursor.count { it == '\n' } + 1
        val cursorCol = cursorOffset - beforeCursor.lastIndexOf('\n')

        val visualTransformation = remember(activeTab.extension, syntaxColors) {
            CodeVisualTransformation(activeTab.extension, syntaxColors)
        }

        val codeTextStyle = TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = fontSizeSp.sp,
            lineHeight = lineHeightSp,
            color = syntaxColors.plainText
        )

        val primaryColor = MaterialTheme.colorScheme.primary
        val outlineColor = MaterialTheme.colorScheme.outlineVariant
        val activeLineHighlightColor = primaryColor.copy(alpha = 0.08f)
        val gutterHighlightColor = primaryColor.copy(alpha = 0.14f)
        val normalGutterStyle = remember(fontSizeSp, syntaxColors.gutterText) {
            TextStyle(
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = (fontSizeSp - 2).coerceAtLeast(10).sp,
                fontWeight = FontWeight.Normal,
                color = syntaxColors.gutterText
            )
        }
        val activeGutterStyle = remember(fontSizeSp, primaryColor) {
            TextStyle(
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = (fontSizeSp - 2).coerceAtLeast(10).sp,
                fontWeight = FontWeight.Bold,
                color = primaryColor
            )
        }

        // Single-Scroll Editor Canvas with pixel-locked Line Number Gutter
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val density = LocalDensity.current
            val viewportHeight = maxHeight
            val viewportWidth = maxWidth
            val viewportHeightPx = with(density) { viewportHeight.toPx() }
            val topPaddingDp = 12.dp
            val bottomPaddingDp = 96.dp
            val topPaddingPx = with(density) { topPaddingDp.toPx() }
            val bottomPaddingPx = with(density) { bottomPaddingDp.toPx() }
            val lineHeightPx = with(density) { lineHeightSp.toPx() }
            val gutterRightPadPx = with(density) { 8.dp.toPx() }

            val digitCount = lineStartOffsets.size.toString().length.coerceAtLeast(2)
            val gutterWidth = (digitCount * 10 + 22).dp.coerceAtLeast(44.dp)
            val codeViewportMinWidth = (viewportWidth - gutterWidth - 1.dp - 24.dp).coerceAtLeast(120.dp)

            var textLayoutResult by remember(activeTab.relativePath) {
                mutableStateOf<TextLayoutResult?>(null)
            }
            var editorHeightPx by remember { mutableIntStateOf(0) }
            val textMeasurer = rememberTextMeasurer()
            val focusRequester = remember { FocusRequester() }
            val tapInteraction = remember { MutableInteractionSource() }

            val estimatedHeightPx = (lineStartOffsets.size * lineHeightPx) + topPaddingPx + bottomPaddingPx
            val layoutHeightPx = textLayoutResult?.let { it.size.height.toFloat() + topPaddingPx + bottomPaddingPx } ?: estimatedHeightPx
            val totalContentHeightPx = maxOf(viewportHeightPx, editorHeightPx.toFloat(), layoutHeightPx)
            val totalContentHeightDp = with(density) { totalContentHeightPx.toDp() }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(vScroll)
            ) {
                // Pixel-aligned Line Number Gutter (locked to single vertical scroll)
                Box(
                    modifier = Modifier
                        .width(gutterWidth)
                        .height(totalContentHeightDp)
                        .background(syntaxColors.gutterBackground)
                        .testTag("code_editor_gutter")
                        .drawBehind {
                            val layout = textLayoutResult
                            val layoutLen = layout?.layoutInput?.text?.length ?: 0
                            val visibleTop = (vScroll.value - 240f).coerceAtLeast(0f)
                            val visibleBottom = vScroll.value + viewportHeightPx + 240f

                            for (i in lineStartOffsets.indices) {
                                val startOffset = lineStartOffsets[i]
                                val (lineTop, lineBottom) = if (layout != null && startOffset <= layoutLen) {
                                    val visualLine = layout.getLineForOffset(startOffset)
                                    (layout.getLineTop(visualLine) + topPaddingPx) to
                                        (layout.getLineBottom(visualLine) + topPaddingPx)
                                } else {
                                    val t = topPaddingPx + i * lineHeightPx
                                    t to (t + lineHeightPx)
                                }

                                if (lineBottom < visibleTop) continue
                                if (lineTop > visibleBottom) break

                                val isCurrentLine = (i + 1) == cursorLine
                                if (isCurrentLine) {
                                    drawRect(
                                        color = gutterHighlightColor,
                                        topLeft = Offset(0f, lineTop),
                                        size = Size(size.width, (lineBottom - lineTop).coerceAtLeast(1f))
                                    )
                                }

                                val measured = textMeasurer.measure(
                                    text = (i + 1).toString(),
                                    style = if (isCurrentLine) activeGutterStyle else normalGutterStyle,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                val x = (size.width - gutterRightPadPx - measured.size.width).coerceAtLeast(2f)
                                val y = lineTop + ((lineBottom - lineTop) - measured.size.height) / 2f
                                drawText(
                                    textLayoutResult = measured,
                                    topLeft = Offset(x, y)
                                )
                            }
                        }
                )

                // Gutter Separator
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(totalContentHeightDp)
                        .background(outlineColor)
                )

                // Editable Syntax-Colored TextField
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = viewportHeight)
                        .onSizeChanged { editorHeightPx = it.height }
                        .clickable(
                            interactionSource = tapInteraction,
                            indication = null
                        ) { focusRequester.requestFocus() }
                        .drawBehind {
                            val layout = textLayoutResult ?: return@drawBehind
                            val layoutLen = layout.layoutInput.text.length
                            val safeCursor = cursorOffset.coerceIn(0, layoutLen)
                            val visualCursorLine = layout.getLineForOffset(safeCursor)
                            val top = layout.getLineTop(visualCursorLine) + topPaddingPx
                            val bottom = layout.getLineBottom(visualCursorLine) + topPaddingPx
                            drawRect(
                                color = activeLineHighlightColor,
                                topLeft = Offset(0f, top),
                                size = Size(size.width, (bottom - top).coerceAtLeast(1f))
                            )
                        }
                        .then(if (!wordWrap) Modifier.horizontalScroll(hScroll) else Modifier)
                        .padding(start = 12.dp, end = 12.dp, top = topPaddingDp, bottom = bottomPaddingDp)
                ) {
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { updated ->
                            textFieldValue = updated
                            if (updated.text != activeTab.content) {
                                onContentChange(updated.text)
                            }
                        },
                        onTextLayout = { textLayoutResult = it },
                        textStyle = codeTextStyle,
                        cursorBrush = SolidColor(primaryColor),
                        visualTransformation = visualTransformation,
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .then(
                                if (wordWrap) Modifier.fillMaxWidth()
                                else Modifier.widthIn(min = codeViewportMinWidth)
                            )
                            .testTag("code_editor_input")
                    )
                }
            }
        }

        // Quick-Insert Coding Bar + Save & Format Actions
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val snippets = listOf(
                "Tab" to "  ",
                "{}" to "{}",
                "()" to "()",
                "[]" to "[]",
                "=>" to " => ",
                "'" to "'",
                "\"" to "\"",
                "`" to "`",
                ";" to ";",
                "log" to "console.log();"
            )
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(snippets) { (label, insertText) ->
                    Surface(
                        onClick = {
                            val selStart = textFieldValue.selection.min
                            val selEnd = textFieldValue.selection.max
                            val nextText = buildString {
                                append(textFieldValue.text.substring(0, selStart))
                                append(insertText)
                                append(textFieldValue.text.substring(selEnd))
                            }
                            val cursorShift = when (insertText) {
                                "{}", "()", "[]" -> selStart + 1
                                "console.log();" -> selStart + 12
                                else -> selStart + insertText.length
                            }
                            textFieldValue = TextFieldValue(nextText, TextRange(cursorShift))
                            onContentChange(nextText)
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            if (activeTab.extension == "json") {
                IconButton(
                    onClick = {
                        runCatching {
                            val formatted = JSONObject(textFieldValue.text).toString(2) + "\n"
                            textFieldValue = TextFieldValue(formatted, TextRange(0))
                            onContentChange(formatted)
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = "Format JSON",
                        tint = CyanPulse,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            IconButton(
                onClick = onSave,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("save_file_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = "Save file",
                    tint = if (activeTab.isDirty) AmberGlow else NodeEmerald,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Status Footer (Path • Ln/Col • Language)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(syntaxColors.gutterBackground)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = activeTab.relativePath,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Ln $cursorLine, Col $cursorCol  •  ${activeTab.extension.uppercase().ifBlank { "TXT" }}  •  UTF-8",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal fun computeLineStartOffsets(text: String): IntArray {
    val lineCount = text.count { it == '\n' } + 1
    val offsets = IntArray(lineCount)
    offsets[0] = 0
    var lineIndex = 1
    for (i in text.indices) {
        if (text[i] == '\n' && lineIndex < lineCount) {
            offsets[lineIndex++] = i + 1
        }
    }
    return offsets
}

private fun extensionAccentColor(ext: String) = when (ext.lowercase()) {
    "js", "mjs", "cjs" -> AmberGlow
    "json" -> NodeEmerald
    "md", "ts" -> CyanPulse
    else -> NodeEmerald
}
