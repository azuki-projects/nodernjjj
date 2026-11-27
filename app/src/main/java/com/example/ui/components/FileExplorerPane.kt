package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Javascript
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.FileNode
import com.example.ui.theme.AmberGlow
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.NodeEmerald

@Composable
fun FileExplorerPane(
    projectName: String,
    entryFile: String,
    fileTree: List<FileNode>,
    activeFilePath: String?,
    onToggleFolder: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onCreateFile: (String, String) -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameNode: (String, String) -> Unit,
    onDeleteNode: (String) -> Unit,
    onSetEntryPoint: (String) -> Unit,
    onImportFile: (String, String) -> Unit,
    actionMessage: String? = null,
    onDismissActionMessage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newTargetParent by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<FileNode?>(null) }
    var deleteTarget by remember { mutableStateOf<FileNode?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val name = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "imported.js" } ?: "imported.js"
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText().orEmpty()
                onImportFile(name, content)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Explorer Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "EXPLORER",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = projectName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(
                    onClick = {
                        newTargetParent = ""
                        showNewFileDialog = true
                    },
                    modifier = Modifier.testTag("new_file_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = "New File", tint = NodeEmerald)
                }
                IconButton(
                    onClick = {
                        newTargetParent = ""
                        showNewFolderDialog = true
                    },
                    modifier = Modifier.testTag("new_folder_button")
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder", tint = CyanPulse)
                }
                IconButton(
                    onClick = { importLauncher.launch(arrayOf("text/*", "application/json", "application/javascript", "*/*")) },
                    modifier = Modifier.testTag("import_file_button")
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = "Import File", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (!actionMessage.isNullOrBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .testTag("explorer_action_message"),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = actionMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismissActionMessage,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss message",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (fileTree.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Workspace is empty", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = { showNewFileDialog = true }) {
                        Text("Create index.js")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                items(fileTree, key = { it.relativePath }) { node ->
                    FileTreeRow(
                        node = node,
                        isSelected = node.relativePath == activeFilePath,
                        isEntryPoint = !node.isDirectory && node.relativePath == entryFile,
                        onClick = {
                            if (node.isDirectory) onToggleFolder(node.relativePath)
                            else onOpenFile(node.relativePath)
                        },
                        onCreateChildFile = {
                            newTargetParent = node.relativePath
                            showNewFileDialog = true
                        },
                        onCreateChildFolder = {
                            newTargetParent = node.relativePath
                            showNewFolderDialog = true
                        },
                        onRename = { renameTarget = node },
                        onDelete = { deleteTarget = node },
                        onSetEntry = { onSetEntryPoint(node.relativePath) }
                    )
                }
            }
        }
    }

    if (showNewFileDialog) {
        InputNameDialog(
            title = if (newTargetParent.isEmpty()) "New File" else "New File in $newTargetParent/",
            placeholder = "e.g. app.js",
            initialValue = "",
            confirmLabel = "Create",
            onDismiss = { showNewFileDialog = false },
            onConfirm = { name ->
                val clean = name.trim().trimStart('/')
                if (clean.isNotEmpty()) {
                    val full = if (newTargetParent.isEmpty()) clean else "$newTargetParent/$clean"
                    val starter = if (clean.endsWith(".js")) "// $clean\n\n" else if (clean.endsWith(".json")) "{\n  \n}\n" else ""
                    onCreateFile(full, starter)
                }
                showNewFileDialog = false
            }
        )
    }

    if (showNewFolderDialog) {
        InputNameDialog(
            title = if (newTargetParent.isEmpty()) "New Folder" else "New Folder in $newTargetParent/",
            placeholder = "e.g. src",
            initialValue = "",
            confirmLabel = "Create",
            onDismiss = { showNewFolderDialog = false },
            onConfirm = { name ->
                val clean = name.trim().trim('/')
                if (clean.isNotEmpty()) {
                    val full = if (newTargetParent.isEmpty()) clean else "$newTargetParent/$clean"
                    onCreateFolder(full)
                }
                showNewFolderDialog = false
            }
        )
    }

    renameTarget?.let { target ->
        InputNameDialog(
            title = "Rename ${target.name}",
            placeholder = "New name",
            initialValue = target.name,
            confirmLabel = "Rename",
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                onRenameNode(target.relativePath, newName)
                renameTarget = null
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${target.name}?") },
            text = {
                Text(
                    if (target.isDirectory) "This will permanently delete the folder and everything inside it."
                    else "This will permanently remove ${target.relativePath} from disk."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteNode(target.relativePath)
                    deleteTarget = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileTreeRow(
    node: FileNode,
    isSelected: Boolean,
    isEntryPoint: Boolean,
    onClick: () -> Unit,
    onCreateChildFile: () -> Unit,
    onCreateChildFolder: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onSetEntry: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.surface
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuExpanded = true }
            )
            .padding(
                start = (12 + node.depth * 18).dp,
                end = 2.dp,
                top = 4.dp,
                bottom = 4.dp
            )
            .testTag("file_node_${node.relativePath}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (node.isDirectory) {
            Icon(
                imageVector = if (node.isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = if (node.isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                contentDescription = null,
                tint = if (node.name == "node_modules") CyanPulse else AmberGlow,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Spacer(Modifier.width(22.dp))
            val (icon, tint) = when (node.extension) {
                "js", "mjs", "cjs" -> Icons.Default.Javascript to AmberGlow
                "json" -> Icons.Default.DataObject to NodeEmerald
                "md" -> Icons.Default.Description to CyanPulse
                else -> Icons.AutoMirrored.Filled.InsertDriveFile to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.width(8.dp))

        Text(
            text = node.name,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isSelected || isEntryPoint) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (isEntryPoint) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "ENTRY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
        } else if (!node.isDirectory) {
            Text(
                text = formatBytes(node.sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("file_more_${node.relativePath}")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "File options for ${node.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                if (node.isDirectory) {
                    DropdownMenuItem(
                        text = { Text("New File Inside") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, null) },
                        onClick = { menuExpanded = false; onCreateChildFile() }
                    )
                    DropdownMenuItem(
                        text = { Text("New Folder Inside") },
                        leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                        onClick = { menuExpanded = false; onCreateChildFolder() }
                    )
                } else if (node.extension in setOf("js", "mjs", "cjs")) {
                    DropdownMenuItem(
                        text = { Text("Set as Entry Script") },
                        leadingIcon = { Icon(Icons.Default.PlayCircleOutline, null) },
                        onClick = { menuExpanded = false; onSetEntry() }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                    onClick = {
                        menuExpanded = false
                        onRename()
                    },
                    modifier = Modifier.testTag("file_menu_rename_${node.relativePath}")
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { menuExpanded = false; onDelete() },
                    modifier = Modifier.testTag("file_menu_delete_${node.relativePath}")
                )
            }
        }
    }
}

@Composable
fun InputNameDialog(
    title: String,
    placeholder: String,
    initialValue: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(title, initialValue) { mutableStateOf(initialValue) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(title, initialValue) {
        runCatching { focusRequester.requestFocus() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text(placeholder) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        val trimmed = text.trim()
                        if (trimmed.isNotEmpty()) onConfirm(trimmed)
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag("dialog_name_input")
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) },
                enabled = text.isNotBlank(),
                modifier = Modifier.testTag("dialog_confirm_button")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dialog_cancel_button")
            ) { Text("Cancel") }
        }
    )
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "${bytes / 1024}KB"
    else -> "${bytes / (1024 * 1024)}MB"
}
