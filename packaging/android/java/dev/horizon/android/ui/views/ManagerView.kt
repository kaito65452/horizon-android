package dev.horizon.android.ui.views

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.horizon.android.ui.components.HorizonButton
import dev.horizon.android.ui.components.HorizonGlass
import dev.horizon.android.ui.components.HorizonPill
import dev.horizon.android.ui.theme.LocalBlazeColors
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class LocalManagedItem(
    val file: File,
    val name: String,
    val category: String,
    val sizeLabel: String,
    val isEnabled: Boolean
)

@Composable
fun ManagerView(
    modifier: Modifier = Modifier
) {
    val colors = LocalBlazeColors.current
    val context = LocalContext.current

    var subTab by remember { mutableStateOf("library") }
    var filterCategory by remember { mutableStateOf("all") } // all, mods, packs, shaders, saves
    var actionStatus by remember { mutableStateOf("") }
    var managedItems by remember { mutableStateOf<List<LocalManagedItem>>(emptyList()) }

    fun refreshFiles() {
        val list = mutableListOf<LocalManagedItem>()
        val base = context.filesDir

        // 1. Mods
        val modsDir = File(base, "horizon-mods")
        if (modsDir.exists()) {
            modsDir.listFiles()?.filter { it.isFile }?.forEach { f ->
                val isEnabled = !f.name.endsWith(".disabled")
                val cleanName = f.name.removeSuffix(".disabled")
                list.add(LocalManagedItem(f, cleanName, "Mod", formatBytes(f.length()), isEnabled))
            }
        }

        // 2. Resource Packs
        val packsDir = File(base, "resourcepacks")
        if (packsDir.exists()) {
            packsDir.listFiles()?.forEach { f ->
                val isEnabled = !f.name.endsWith(".disabled")
                val cleanName = f.name.removeSuffix(".disabled")
                list.add(LocalManagedItem(f, cleanName, "Resource Pack", formatBytes(f.length()), isEnabled))
            }
        }

        // 3. Shader Packs
        val shadersDir = File(base, "shaderpacks")
        if (shadersDir.exists()) {
            shadersDir.listFiles()?.forEach { f ->
                val isEnabled = !f.name.endsWith(".disabled")
                val cleanName = f.name.removeSuffix(".disabled")
                list.add(LocalManagedItem(f, cleanName, "Shader Pack", formatBytes(f.length()), isEnabled))
            }
        }

        // 4. Worlds (Saves)
        val savesDir = File(base, "saves")
        if (savesDir.exists()) {
            savesDir.listFiles()?.filter { it.isDirectory }?.forEach { d ->
                list.add(LocalManagedItem(d, d.name, "World Save", "Directory", true))
            }
        }

        // Also add built-in Complex Voice Chat
        list.add(
            LocalManagedItem(
                File(base, "complex_voice_chat"),
                "Complex Voice Chat (ported by Asano Saryu)",
                "Built-in Subsystem",
                "UDP 3D Audio",
                true
            )
        )

        managedItems = list
    }

    LaunchedEffect(Unit) {
        refreshFiles()
    }

    // REAL SAF: Import Mod / Pack / Shader / World
    val importFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val fileName = getUriDisplayName(context, uri)
                val targetDir = when {
                    fileName.endsWith(".hshader") || fileName.contains("shader", ignoreCase = true) ->
                        File(context.filesDir, "shaderpacks")
                    fileName.contains("resource", ignoreCase = true) || fileName.contains("pack", ignoreCase = true) ->
                        File(context.filesDir, "resourcepacks")
                    else ->
                        File(context.filesDir, "horizon-mods")
                }
                targetDir.mkdirs()
                val targetFile = File(targetDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                actionStatus = "Successfully imported $fileName"
                refreshFiles()
            } catch (e: Exception) {
                actionStatus = "Import error: ${e.message}"
            }
        }
    }

    // REAL SAF: Export Worlds to ZIP
    val exportWorldsPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            try {
                val savesDir = File(context.filesDir, "saves")
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    ZipOutputStream(outputStream).use { zipOut ->
                        if (savesDir.exists() && savesDir.isDirectory) {
                            savesDir.walkTopDown().forEach { file ->
                                val relPath = file.relativeTo(savesDir).path
                                if (file.isFile) {
                                    zipOut.putNextEntry(ZipEntry(relPath))
                                    file.inputStream().use { it.copyTo(zipOut) }
                                    zipOut.closeEntry()
                                }
                            }
                        }
                    }
                }
                actionStatus = "Exported worlds to ZIP successfully via SAF."
            } catch (e: Exception) {
                actionStatus = "Export failed: ${e.message}"
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Sub-tabs & Real SAF Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("library" to "All Items", "mods" to "Mods", "packs" to "Packs", "saves" to "Worlds").forEach { (key, label) ->
                    val isSel = filterCategory == key
                    HorizonPill(
                        backgroundColor = if (isSel) colors.activePalette.primary else Color(0x1AFFFFFF),
                        borderColor = if (isSel) colors.activePalette.primary else Color(0x26FFFFFF),
                        onClick = { filterCategory = key }
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            text = label,
                            style = androidx.compose.ui.text.TextStyle(
                                color = if (isSel) Color(0xFF020617) else Color.White,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                }
            }

            // Real SAF Action Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HorizonButton(
                    text = "Export Worlds (SAF)",
                    onClick = {
                        exportWorldsPicker.launch("horizon_worlds_backup.zip")
                    }
                )
                HorizonButton(
                    text = "Import .hmod / .zip (SAF)",
                    accent = true,
                    onClick = {
                        importFilePicker.launch(arrayOf("*/*", "application/zip", "application/java-archive"))
                    }
                )
            }
        }

        // Installed Content Glass Panel
        HorizonGlass(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            cornerRadius = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                if (actionStatus.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x3310B981))
                            .border(1.dp, Color(0x4D10B981), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            text = actionStatus,
                            style = androidx.compose.ui.text.TextStyle(
                                color = Color(0xFF6EE7B7),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                val filtered = managedItems.filter { item ->
                    when (filterCategory) {
                        "mods" -> item.category == "Mod" || item.category.contains("Subsystem")
                        "packs" -> item.category.contains("Pack")
                        "saves" -> item.category.contains("World")
                        else -> true
                    }
                }

                // Scrollable File List
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    filtered.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x14FFFFFF))
                                .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                androidx.compose.foundation.text.BasicText(
                                    text = item.name,
                                    style = androidx.compose.ui.text.TextStyle(
                                        color = if (item.isEnabled) Color.White else Color(0xFF94A3B8),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(Modifier.height(2.dp))
                                androidx.compose.foundation.text.BasicText(
                                    text = "${item.category} • ${item.sizeLabel}",
                                    style = androidx.compose.ui.text.TextStyle(
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.sp
                                    )
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (item.file.exists() && item.file.name != "complex_voice_chat") {
                                    // Toggle Enable / Disable
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (item.isEnabled) colors.activePalette.soft else Color(0x26FFFFFF))
                                            .clickable {
                                                if (item.isEnabled) {
                                                    val disabled = File(item.file.parentFile, "${item.file.name}.disabled")
                                                    item.file.renameTo(disabled)
                                                } else {
                                                    val enabledName = item.file.name.removeSuffix(".disabled")
                                                    val enabled = File(item.file.parentFile, enabledName)
                                                    item.file.renameTo(enabled)
                                                }
                                                refreshFiles()
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        androidx.compose.foundation.text.BasicText(
                                            text = if (item.isEnabled) "ACTIVE" else "DISABLED",
                                            style = androidx.compose.ui.text.TextStyle(
                                                color = if (item.isEnabled) colors.activePalette.primary else Color(0xFF94A3B8),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }

                                    // Delete File
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0x26EF4444))
                                            .clickable {
                                                item.file.deleteRecursively()
                                                actionStatus = "Deleted ${item.name}"
                                                refreshFiles()
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        androidx.compose.foundation.text.BasicText(
                                            text = "DELETE",
                                            style = androidx.compose.ui.text.TextStyle(
                                                color = Color(0xFFF87171),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0x2610B981))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        androidx.compose.foundation.text.BasicText(
                                            text = "BUILT-IN",
                                            style = androidx.compose.ui.text.TextStyle(
                                                color = Color(0xFF34D399),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getUriDisplayName(context: Context, uri: Uri): String {
    var name = "imported_file.zip"
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex != -1 && cursor.moveToFirst()) {
            name = cursor.getString(nameIndex)
        }
    }
    return name
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val k = 1024.0
    val m = k * 1024.0
    return when {
        bytes >= m -> "%.1f MiB".format(bytes / m)
        bytes >= k -> "%.1f KiB".format(bytes / k)
        else -> "$bytes B"
    }
}
