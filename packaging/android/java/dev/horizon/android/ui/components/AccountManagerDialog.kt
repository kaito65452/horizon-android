package dev.horizon.android.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.horizon.android.bridge.BlazeAccount
import dev.horizon.android.bridge.BlazeNativeBridge
import dev.horizon.android.ui.animation.EaseOutFluent
import dev.horizon.android.ui.animation.pressBounce
import dev.horizon.android.ui.theme.LocalBlazeColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun AccountManagerDialog(
    onDismissRequest: () -> Unit,
    onAccountChanged: (String) -> Unit
) {
    val colors = LocalBlazeColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var accounts by remember { mutableStateOf<List<BlazeAccount>>(emptyList()) }
    var offlineAccounts by remember { mutableStateOf<List<String>>(emptyList()) }
    var activeName by remember { mutableStateOf(BlazeNativeBridge.getActiveAccountName()) }
    var activeType by remember { mutableStateOf(BlazeNativeBridge.getActiveAccountType()) }

    var showAddDialog by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }

    fun refreshAccounts() {
        accounts = BlazeNativeBridge.getAccounts()
        offlineAccounts = BlazeNativeBridge.getOfflineAccounts()
        activeName = BlazeNativeBridge.getActiveAccountName()
        activeType = BlazeNativeBridge.getActiveAccountType()
    }

    LaunchedEffect(Unit) {
        refreshAccounts()
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xB3020617))
                .clickable(onClick = onDismissRequest),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .fillMaxHeight(0.85f)
                    .clickable(enabled = false) {}
            ) {
                HorizonGlass(
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 20.dp,
                    backgroundColor = Color(0xF2081324),
                    borderColor = Color(0x3338BDF8)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp)
                    ) {
                        // Dialog Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                androidx.compose.foundation.text.BasicText(
                                    text = "ACCOUNTS & PROFILES",
                                    style = androidx.compose.ui.text.TextStyle(
                                        color = colors.activePalette.primary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 1.sp
                                    )
                                )
                                androidx.compose.foundation.text.BasicText(
                                    text = "Manage Player Identity",
                                    style = androidx.compose.ui.text.TextStyle(
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                HorizonButton(
                                    text = "+ ADD ACCOUNT",
                                    accent = true,
                                    onClick = { showAddDialog = true }
                                )
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x26FFFFFF))
                                        .clickable(onClick = onDismissRequest),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.foundation.text.BasicText(
                                        text = "✕",
                                        style = androidx.compose.ui.text.TextStyle(
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Active Account Banner
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x2638BDF8))
                                .border(1.dp, Color(0x4038BDF8), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Avatar Circle
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(colors.activePalette.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.foundation.text.BasicText(
                                        text = activeName.firstOrNull()?.uppercase() ?: "P",
                                        style = androidx.compose.ui.text.TextStyle(
                                            color = Color(0xFF020617),
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    )
                                }

                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        androidx.compose.foundation.text.BasicText(
                                            text = activeName,
                                            style = androidx.compose.ui.text.TextStyle(
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        ProviderBadge(type = activeType)
                                    }
                                    androidx.compose.foundation.text.BasicText(
                                        text = "Currently selected for launching Minecraft 26.3",
                                        style = androidx.compose.ui.text.TextStyle(
                                            color = Color(0xFF94A3B8),
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x3310B981))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                androidx.compose.foundation.text.BasicText(
                                    text = "ACTIVE",
                                    style = androidx.compose.ui.text.TextStyle(
                                        color = Color(0xFF34D399),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Scrollable List of All Accounts
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (accounts.isEmpty() && offlineAccounts.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 30.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.foundation.text.BasicText(
                                        text = "No saved accounts. Tap + ADD ACCOUNT to sign in or add offline name.",
                                        style = androidx.compose.ui.text.TextStyle(
                                            color = Color(0xFF64748B),
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }

                            // 1. Online Accounts (Microsoft / Ely.by)
                            accounts.forEach { acc ->
                                val isCurrent = acc.name == activeName
                                AccountRowItem(
                                    name = acc.name,
                                    provider = acc.provider,
                                    subtitle = "UUID: ${acc.uuid.take(8)}...",
                                    isActive = isCurrent,
                                    onSelect = {
                                        BlazeNativeBridge.nativeSelectAccount(acc.provider, acc.key)
                                        refreshAccounts()
                                        onAccountChanged(acc.name)
                                    },
                                    onDelete = {
                                        BlazeNativeBridge.nativeDeleteAccount(acc.provider, acc.key)
                                        refreshAccounts()
                                        onAccountChanged(BlazeNativeBridge.getActiveAccountName())
                                    }
                                )
                            }

                            // 2. Offline Names
                            offlineAccounts.forEach { offName ->
                                val isCurrent = offName == activeName
                                AccountRowItem(
                                    name = offName,
                                    provider = "offline",
                                    subtitle = "Local offline identity",
                                    isActive = isCurrent,
                                    onSelect = {
                                        BlazeNativeBridge.nativeSelectAccount("offline", offName)
                                        refreshAccounts()
                                        onAccountChanged(offName)
                                    },
                                    onDelete = {
                                        BlazeNativeBridge.nativeDeleteAccount("offline", offName)
                                        refreshAccounts()
                                        onAccountChanged(BlazeNativeBridge.getActiveAccountName())
                                    }
                                )
                            }
                        }

                        if (statusMessage.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            androidx.compose.foundation.text.BasicText(
                                text = statusMessage,
                                style = androidx.compose.ui.text.TextStyle(
                                    color = Color(0xFF34D399),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddAccountModal(
            onDismissRequest = { showAddDialog = false },
            onAccountAdded = { newName ->
                refreshAccounts()
                onAccountChanged(newName)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AccountRowItem(
    name: String,
    provider: String,
    subtitle: String,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalBlazeColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) Color(0x1A38BDF8) else Color(0x0DFFFFFF))
            .border(1.dp, if (isActive) Color(0x3338BDF8) else Color(0x14FFFFFF), RoundedCornerShape(10.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isActive) colors.activePalette.primary else Color(0x33FFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.text.BasicText(
                    text = name.firstOrNull()?.uppercase() ?: "P",
                    style = androidx.compose.ui.text.TextStyle(
                        color = if (isActive) Color(0xFF020617) else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    androidx.compose.foundation.text.BasicText(
                        text = name,
                        style = androidx.compose.ui.text.TextStyle(
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    ProviderBadge(type = provider)
                }
                androidx.compose.foundation.text.BasicText(
                    text = subtitle,
                    style = androidx.compose.ui.text.TextStyle(
                        color = Color(0xFF64748B),
                        fontSize = 9.sp
                    )
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!isActive) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x1AFFFFFF))
                        .clickable(onClick = onSelect)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    androidx.compose.foundation.text.BasicText(
                        text = "SWITCH",
                        style = androidx.compose.ui.text.TextStyle(
                            color = Color(0xFFCBD5E1),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x1AEF4444))
                    .clickable(onClick = onDelete)
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
        }
    }
}

@Composable
private fun ProviderBadge(type: String) {
    val (label, bg, fg) = when (type.lowercase()) {
        "microsoft" -> Triple("MICROSOFT", Color(0x3310B981), Color(0xFF34D399))
        "ely" -> Triple("ELY.BY", Color(0x33A855F7), Color(0xFFC084FC))
        else -> Triple("OFFLINE", Color(0x33F59E0B), Color(0xFFFBBF24))
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        androidx.compose.foundation.text.BasicText(
            text = label,
            style = androidx.compose.ui.text.TextStyle(
                color = fg,
                fontSize = 7.5.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )
        )
    }
}

@Composable
private fun AddAccountModal(
    onDismissRequest: () -> Unit,
    onAccountAdded: (String) -> Unit
) {
    val colors = LocalBlazeColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf("microsoft") } // microsoft, ely, offline
    var offlineNameInput by remember { mutableStateOf("") }

    var elyUsernameInput by remember { mutableStateOf("") }
    var elyPasswordInput by remember { mutableStateOf("") }
    var elyOtpInput by remember { mutableStateOf("") }

    var msUserCode by remember { mutableStateOf("") }
    var msVerificationUri by remember { mutableStateOf("") }
    var msIsPolling by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }

    // Microsoft polling loop
    LaunchedEffect(msIsPolling) {
        if (msIsPolling) {
            while (msIsPolling) {
                delay(3000L)
                try {
                    val pollResStr = BlazeNativeBridge.nativePollMicrosoftDeviceLogin()
                    val pollJson = JSONObject(pollResStr)
                    val status = pollJson.optString("status")
                    if (status == "success") {
                        val name = pollJson.optString("name", "Player")
                        msIsPolling = false
                        onAccountAdded(name)
                        break
                    } else if (status == "error") {
                        statusText = "Error: " + pollJson.optString("error")
                        msIsPolling = false
                        break
                    }
                } catch (e: Throwable) {
                    statusText = "Polling error: ${e.message}"
                    msIsPolling = false
                    break
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xB3020617))
                .clickable(onClick = onDismissRequest),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .fillMaxHeight(0.80f)
                    .clickable(enabled = false) {}
            ) {
                HorizonGlass(
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 20.dp,
                    backgroundColor = Color(0xF2081324),
                    borderColor = Color(0x3338BDF8)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp)
                    ) {
                        // Title
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.foundation.text.BasicText(
                                text = "ADD NEW ACCOUNT",
                                style = androidx.compose.ui.text.TextStyle(
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x26FFFFFF))
                                    .clickable(onClick = onDismissRequest),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.foundation.text.BasicText(
                                    text = "✕",
                                    style = androidx.compose.ui.text.TextStyle(
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Type selector tab
                        HorizonSegmented(
                            options = listOf("Microsoft", "Ely.by", "Offline"),
                            selectedIndex = when (activeTab) {
                                "microsoft" -> 0
                                "ely" -> 1
                                else -> 2
                            },
                            onSelect = {
                                activeTab = when (it) {
                                    0 -> "microsoft"
                                    1 -> "ely"
                                    else -> "offline"
                                }
                                statusText = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(12.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            when (activeTab) {
                                "microsoft" -> {
                                    androidx.compose.foundation.text.BasicText(
                                        text = "Official Microsoft OAuth device-flow. Authenticate in your web browser securely without entering credentials into the app.",
                                        style = androidx.compose.ui.text.TextStyle(
                                            color = Color(0xFF94A3B8),
                                            fontSize = 10.5.sp
                                        )
                                    )

                                    if (msUserCode.isEmpty()) {
                                        HorizonButton(
                                            text = "REQUEST SIGN-IN CODE",
                                            accent = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            onClick = {
                                                coroutineScope.launch {
                                                    try {
                                                        statusText = "Requesting device code from Microsoft..."
                                                        val resStr = BlazeNativeBridge.nativeStartMicrosoftDeviceLogin()
                                                        val json = JSONObject(resStr)
                                                        if (json.optBoolean("success")) {
                                                            msUserCode = json.optString("user_code")
                                                            msVerificationUri = json.optString("verification_uri", "https://microsoft.com/link")
                                                            msIsPolling = true
                                                            statusText = "Code active. Approve sign-in in browser."
                                                        } else {
                                                            statusText = "Failed: " + json.optString("error")
                                                        }
                                                    } catch (e: Exception) {
                                                        statusText = "Error: ${e.message}"
                                                    }
                                                }
                                            }
                                        )
                                    } else {
                                        // Display code card
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0x2610B981))
                                                .border(1.dp, Color(0x4010B981), RoundedCornerShape(12.dp))
                                                .padding(14.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            androidx.compose.foundation.text.BasicText(
                                                text = "ENTER THIS CODE AT MICROSOFT:",
                                                style = androidx.compose.ui.text.TextStyle(
                                                    color = Color(0xFF34D399),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            )
                                            androidx.compose.foundation.text.BasicText(
                                                text = msUserCode,
                                                style = androidx.compose.ui.text.TextStyle(
                                                    color = Color.White,
                                                    fontSize = 24.sp,
                                                    fontWeight = FontWeight.Black,
                                                    fontFamily = FontFamily.Monospace,
                                                    letterSpacing = 2.sp
                                                )
                                            )
                                        }

                                        HorizonButton(
                                            text = "COPY CODE & OPEN BROWSER",
                                            accent = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            onClick = {
                                                val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clip.setPrimaryClip(ClipData.newPlainText("Microsoft Code", msUserCode))
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(msVerificationUri)).apply {
                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                }
                                                context.startActivity(intent)
                                            }
                                        )

                                        if (msIsPolling) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                androidx.compose.foundation.text.BasicText(
                                                    text = "⏳ Waiting for authorization in browser...",
                                                    style = androidx.compose.ui.text.TextStyle(
                                                        color = colors.activePalette.primary,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }

                                "ely" -> {
                                    androidx.compose.foundation.text.BasicText(
                                        text = "Sign in with your Ely.by Yggdrasil account. Your skin and cape will load automatically into the 26.3 client.",
                                        style = androidx.compose.ui.text.TextStyle(
                                            color = Color(0xFF94A3B8),
                                            fontSize = 10.5.sp
                                        )
                                    )

                                    ModernInputField(
                                        label = "Ely.by Username or Email",
                                        value = elyUsernameInput,
                                        onValueChange = { elyUsernameInput = it }
                                    )

                                    ModernInputField(
                                        label = "Password",
                                        value = elyPasswordInput,
                                        onValueChange = { elyPasswordInput = it },
                                        isPassword = true
                                    )

                                    ModernInputField(
                                        label = "2FA Code (Optional, 6 digits)",
                                        value = elyOtpInput,
                                        onValueChange = { elyOtpInput = it }
                                    )

                                    HorizonButton(
                                        text = "SIGN IN WITH ELY.BY",
                                        accent = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            coroutineScope.launch {
                                                try {
                                                    statusText = "Authenticating with Ely.by..."
                                                    val resStr = BlazeNativeBridge.nativeAddElyByAccount(
                                                        elyUsernameInput.trim(),
                                                        elyPasswordInput,
                                                        elyOtpInput.trim()
                                                    )
                                                    val json = JSONObject(resStr)
                                                    if (json.optBoolean("success")) {
                                                        onAccountAdded(json.optString("name", elyUsernameInput))
                                                    } else {
                                                        statusText = "Ely.by error: " + json.optString("error")
                                                    }
                                                } catch (e: Exception) {
                                                    statusText = "Error: ${e.message}"
                                                }
                                            }
                                        }
                                    )
                                }

                                "offline" -> {
                                    androidx.compose.foundation.text.BasicText(
                                        text = "Play without an online account in singleplayer or offline LAN servers. Specify your in-game name (3–16 letters, numbers, or _).",
                                        style = androidx.compose.ui.text.TextStyle(
                                            color = Color(0xFF94A3B8),
                                            fontSize = 10.5.sp
                                        )
                                    )

                                    ModernInputField(
                                        label = "Player Name",
                                        value = offlineNameInput,
                                        onValueChange = { offlineNameInput = it }
                                    )

                                    HorizonButton(
                                        text = "USE OFFLINE NAME",
                                        accent = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            val name = offlineNameInput.trim()
                                            if (name.length in 3..16 && name.all { it.isLetterOrDigit() || it == '_' }) {
                                                val success = BlazeNativeBridge.nativeAddOfflineAccount(name)
                                                if (success) {
                                                    onAccountAdded(name)
                                                } else {
                                                    statusText = "Could not add offline account"
                                                }
                                            } else {
                                                statusText = "Name must be 3-16 alphanumeric characters"
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        if (statusText.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            androidx.compose.foundation.text.BasicText(
                                text = statusText,
                                style = androidx.compose.ui.text.TextStyle(
                                    color = if (statusText.startsWith("Error") || statusText.startsWith("Failed")) Color(0xFFF87171) else Color(0xFF34D399),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModernInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false
) {
    val colors = LocalBlazeColors.current
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        androidx.compose.foundation.text.BasicText(
            text = label,
            style = androidx.compose.ui.text.TextStyle(
                color = Color(0xFF94A3B8),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x1AFFFFFF))
                .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                cursorBrush = SolidColor(colors.activePalette.primary),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.fillMaxWidth()
            )
            if (value.isEmpty()) {
                androidx.compose.foundation.text.BasicText(
                    text = "Enter $label",
                    style = androidx.compose.ui.text.TextStyle(
                        color = Color(0xFF64748B),
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}
