package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.data.PeerNode
import com.example.data.RadioPacket
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SignalApp(viewModel: SignalViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // State bindings
    val currentChan by viewModel.currentChannel.collectAsState()
    val isBatterySaver by viewModel.isBatterySaver.collectAsState()
    val isSosActive by viewModel.isSosActive.collectAsState()
    val isPassphraseActive by viewModel.isPassphraseActive.collectAsState()
    val passphraseValue by viewModel.passphrase.collectAsState()
    val isRecordingState by viewModel.isRecording.collectAsState()
    val amplitudeVal by viewModel.amplitude.collectAsState()
    val pttLogs by viewModel.packets.collectAsState()
    val activeNodes by viewModel.activePeers.collectAsState()
    val consoleLogs by viewModel.terminalLogs.collectAsState()
    val isBleHardwareSupported by viewModel.isBleHardwareSupported.collectAsState()
    val isBleScanning by viewModel.isBleScanning.collectAsState()
    val activePlaybackId by viewModel.activePlaybackPacketId.collectAsState()
    val selectedChannelIndex by viewModel.selectedChannelIndex.collectAsState()

    // UI Dialogs
    var showPassphraseDialog by remember { mutableStateOf(false) }
    var selectedPeerDetail by remember { mutableStateOf<PeerNode?>(null) }
    var textMessageInput by remember { mutableStateOf("") }
    var micLockOn by remember { mutableStateOf(false) }

    // System Permissions launcher
    val requiredPermissions = mutableListOf(
        Manifest.permission.RECORD_AUDIO
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val micGranted = results[Manifest.permission.RECORD_AUDIO] ?: false
        if (micGranted) {
            viewModel.playClickTone()
        }
        // Start BLE scan if bluetooth permission is granted
        viewModel.startBleHardwareTransmissions(context)
    }

    // Trigger permission checks on start
    LaunchedEffect(Unit) {
        val missed = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missed.isNotEmpty()) {
            permissionLauncher.launch(requiredPermissions.toTypedArray())
        } else {
            viewModel.startBleHardwareTransmissions(context)
        }
    }

    // Automatic dimmer for Battery Saver Mode
    val screenAlpha by animateFloatAsState(
        targetValue = if (isBatterySaver) 0.55f else 1.0f,
        animationSpec = tween(500),
        label = "dim"
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .alpha(screenAlpha)
            .testTag("app_root"),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(TacticalDarkBg)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. TOP BAR CONTROL CENTER
            TopTacticalHeader(
                isBatterySaver = isBatterySaver,
                isSosActive = isSosActive,
                isPassphraseActive = isPassphraseActive,
                isScanning = isBleScanning,
                passphrase = passphraseValue,
                onBatteryToggle = { viewModel.toggleBatterySaver() },
                onPassphraseClick = { showPassphraseDialog = true },
                onWipeAll = { viewModel.wipeEmergencyLogs() },
                frequency = currentChan.frequency,
                peersCount = activeNodes.size
            )

            // Split Dashboard containing Radar and Radio info side by side or top-bottom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 2. TACTICAL PROXIMITY RADAR CONSOLE (Circular view)
                Box(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(32.dp))
                        .border(1.dp, TacticalBorder, RoundedCornerShape(32.dp))
                        .background(TacticalSurface)
                        .testTag("radar_container"),
                    contentAlignment = Alignment.Center
                ) {
                    TacticalRadar(
                        peers = activeNodes,
                        isBatterySaverActive = isBatterySaver,
                        onPeerClick = { peer ->
                            viewModel.playClickTone()
                            selectedPeerDetail = peer
                        }
                    )
                }

                // 3. SECTOR DIALS AND STATUS TELEMETRY (Clean Minimalism style)
                Column(
                    modifier = Modifier
                        .weight(0.9f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Channel Text Box
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, TacticalBorder, RoundedCornerShape(16.dp))
                                .background(TacticalSurface)
                                .padding(10.dp)
                        ) {
                            Column(
                                verticalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "CHANNEL",
                                        color = TacticalMutedText,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        if (currentChan.isEmergency) "WARN CH" else "SECURE MODE",
                                        color = if (currentChan.isEmergency) TacticalSOSPulse else TacticalTertiary,
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            viewModel.playClickTone()
                                            val prevIndex = if (selectedChannelIndex > 0) selectedChannelIndex - 1 else viewModel.channels.size - 1
                                            viewModel.selectChannel(prevIndex)
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Text("◁", color = Color.White, fontSize = 14.sp)
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            currentChan.frequency,
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.testTag("frequency_display")
                                        )
                                        Text(
                                            currentChan.label.uppercase(),
                                            color = TacticalPrimary,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Center
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            viewModel.playClickTone()
                                            val nextIndex = if (selectedChannelIndex < viewModel.channels.size - 1) selectedChannelIndex + 1 else 0
                                            viewModel.selectChannel(nextIndex)
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Text("▷", color = Color.White, fontSize = 14.sp)
                                    }
                                }

                                Text(
                                    text = "RSSI: -74dBm",
                                    color = TacticalMutedText,
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )
                            }
                        }

                        // Battery Box
                        Box(
                            modifier = Modifier
                                .width(56.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, TacticalBorder, RoundedCornerShape(16.dp))
                                .background(TacticalSurface)
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                verticalArrangement = Arrangement.SpaceBetween,
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    "BATT",
                                    color = TacticalMutedText,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Box(
                                    modifier = Modifier
                                        .size(16.dp, 36.dp)
                                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
                                        .padding(1.dp),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    val pct = if (isBatterySaver) 0.50f else 0.88f
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight(pct)
                                            .clip(RoundedCornerShape(1.5.dp))
                                            .background(TacticalPrimary)
                                    )
                                }

                                Text(
                                    text = if (isBatterySaver) "50%" else "88%",
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Emergency SOS Beacon Trigger button
                    Button(
                        onClick = { viewModel.toggleSosMode() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSosActive) Color.Red else Color(0xFF2D0A0A),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .border(
                                width = 1.dp,
                                color = if (isSosActive) Color.White else Color(0x3DF87171),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .testTag("sos_beacon_button"),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "SOS",
                                color = if (isSosActive) Color.White else Color(0xFFF87171),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Text(
                                if (isSosActive) "TRANSMITTING BEACON..." else "Beacon",
                                color = if (isSosActive) Color.White else Color(0x99F87171),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }

            // 4. VERTICAL FREQUENCY KNOB SLIDER DIAL
            FrequencyKnobScroll(
                channels = viewModel.channels,
                selectedIndex = selectedChannelIndex,
                onChannelSelect = { index ->
                    viewModel.selectChannel(index)
                }
            )

            // 5. COMM LOGS AND TELEMETRY PANELS (TABBED SELECTION)
            var currentTab by remember { mutableStateOf(0) } // 0 = Radio Feed, 1 = Terminal Logs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TabButton(
                    text = "RADIO INBOX (${pttLogs.size})",
                    isActive = currentTab == 0,
                    modifier = Modifier.weight(1f),
                    onClick = { currentTab = 0; viewModel.playClickTone() }
                )
                TabButton(
                    text = "HARDWARE TERMINAL",
                    isActive = currentTab == 1,
                    modifier = Modifier.weight(1f),
                    onClick = { currentTab = 1; viewModel.playClickTone() }
                )
            }

            // Subpanel Render
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, TacticalBorder, RoundedCornerShape(10.dp))
                    .background(Color(0xFF070B08))
                    .padding(6.dp)
            ) {
                if (currentTab == 0) {
                    if (pttLogs.isEmpty()) {
                        EmptyFeedState()
                    } else {
                        val scrollState = rememberLazyListState()
                        LazyColumn(
                            state = scrollState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            reverseLayout = false
                        ) {
                            items(pttLogs) { packet ->
                                PacketRowItem(
                                    packet = packet,
                                    isPlaying = activePlaybackId == packet.id,
                                    currentPassphrase = passphraseValue,
                                    isPassphraseActive = isPassphraseActive,
                                    onPlayClick = { viewModel.playVoiceMessage(packet) }
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(consoleLogs) { logEntry ->
                            Text(
                                text = ">> ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(logEntry.timestamp))} | ${logEntry.message}",
                                color = TacticalSecondary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }

            // 6. LOW POWER SILENT EMOJI TRANSMITTER BOARD
            SilentPingsDashboard(onPingSend = { emoji ->
                viewModel.sendTextPing("🚨 [SILENT TELEMETRY] Code: $emoji")
                coroutineScope.launch {
                    textMessageInput = ""
                }
            })

            // 7. MULTI-COMMAND PTT COCKPIT (BOTTOM ACTION BOARD)
            BottomControlCockpit(
                inputValue = textMessageInput,
                onValueChange = { textMessageInput = it },
                isRecording = isRecordingState,
                amplitude = amplitudeVal,
                micLockOn = micLockOn,
                onMicLockToggle = {
                    micLockOn = !micLockOn
                    viewModel.playClickTone()
                    if (micLockOn) {
                        viewModel.startRecording(context)
                    } else {
                        viewModel.stopRecording()
                    }
                },
                onTouchDownPTT = {
                    if (!micLockOn) {
                        viewModel.startRecording(context)
                    }
                },
                onTouchUpPTT = {
                    if (!micLockOn) {
                        viewModel.stopRecording()
                    }
                },
                onSendText = {
                    if (textMessageInput.trim().isNotEmpty()) {
                        viewModel.sendTextPing(textMessageInput)
                        textMessageInput = ""
                    }
                }
            )
        }
    }

    // 8. CRYPTOGRAPHIC SCRAMBLER PASSPHRASE DIALOG
    if (showPassphraseDialog) {
        var tempPassphrase by remember { mutableStateOf(passphraseValue) }

        Dialog(onDismissRequest = { showPassphraseDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(2.dp, TacticalPrimary, RoundedCornerShape(14.dp))
                    .background(TacticalSurface)
                    .padding(16.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock Secure",
                            tint = TacticalPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            "SCRAMBLER SYNC KEYS",
                            color = TacticalPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Text(
                        "Sets the offline private group encryption key. Only users nearby with the exact same key will automatically decrypt your voice and text pings. Cleartext is forced on Emergency channel (156.800 MHz).",
                        color = TacticalMutedText,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    OutlinedTextField(
                        value = tempPassphrase,
                        onValueChange = { tempPassphrase = it },
                        modifier = Modifier.fillMaxWidth().testTag("passphrase_input"),
                        label = { Text("PASS PHRASE / GROUP KEY") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TacticalPrimary,
                            unfocusedBorderColor = TacticalBorder,
                            focusedLabelColor = TacticalPrimary,
                            unfocusedLabelColor = TacticalMutedText,
                            focusedTextColor = TacticalPrimary,
                            unfocusedTextColor = TacticalSecondary
                        ),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { viewModel.togglePassphraseActive() }
                        ) {
                            Checkbox(
                                checked = isPassphraseActive,
                                onCheckedChange = { viewModel.togglePassphraseActive() },
                                colors = CheckboxDefaults.colors(checkedColor = TacticalPrimary)
                            )
                            Text(
                                "SCRAMBLER ACTIVE",
                                color = TacticalSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.setPassphrase(tempPassphrase)
                                showPassphraseDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TacticalPrimary)
                        ) {
                            Text("SYNC KEY", color = Color.Black, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // 9. RADAR BLIP DETAILED MODAL DIAGRAM
    if (selectedPeerDetail != null) {
        val peer = selectedPeerDetail!!
        Dialog(onDismissRequest = { selectedPeerDetail = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, if (peer.isEmergency) TacticalSOSPulse else TacticalPrimary, RoundedCornerShape(14.dp))
                    .background(TacticalSurface)
                    .padding(16.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (peer.isEmergency) TacticalSOSPulse else TacticalPrimary)
                            )
                            Text(
                                "PEER RADAR ACQUISITION",
                                color = if (peer.isEmergency) TacticalSOSPulse else TacticalPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        IconButton(
                            onClick = { selectedPeerDetail = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TacticalMutedText)
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0x22000000)),
                        border = BorderStroke(1.dp, TacticalBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("IDENTIFIER: ${peer.displayName}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("HW REF ID: ${peer.macAddress}", color = TacticalMutedText, fontSize = 11.sp)
                            Text("SECTOR VECTOR: X: ${peer.relativeX.toInt()}m, Y: ${peer.relativeY.toInt()}m", color = TacticalSecondary, fontSize = 12.sp)
                            Text("CALCULATED DISTANCE: ~${Math.hypot(peer.relativeX.toDouble(), peer.relativeY.toDouble()).toInt()} meters", color = TacticalSecondary, fontSize = 12.sp)
                            Text("SIGNAL RSSI: ${peer.rssi} dBm (Good Link)", color = TacticalPrimary, fontSize = 12.sp)
                            Text("POWER PACK: ${peer.batteryLevel}% remaining", color = if (peer.batteryLevel < 20) TacticalSOSPulse else TacticalSecondary, fontSize = 12.sp)
                            Text("STATUS: ${if (peer.isEmergency) "🔴 BEACON DISTRESS ACTIVATED" else "🟢 SECURE PASSIVE LISTENING"}", color = if (peer.isEmergency) TacticalSOSPulse else TacticalPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Mesh Map Visualization in Dialog
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("ROUTING PATHWAY (MESH RELAY):", color = TacticalMutedText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text("ME", color = TacticalPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Icon(imageVector = Icons.Default.KeyboardArrowRight, contentDescription = "relay", tint = TacticalBorder, modifier = Modifier.size(16.dp))
                            if (peer.isEmergency) {
                                Text("Relay #3", color = TacticalSecondary, fontSize = 11.sp)
                                Icon(imageVector = Icons.Default.KeyboardArrowRight, contentDescription = "relay", tint = TacticalBorder, modifier = Modifier.size(16.dp))
                            }
                            Text(peer.displayName.split(" ")[0], color = if (peer.isEmergency) TacticalSOSPulse else TacticalPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = { selectedPeerDetail = null },
                        colors = ButtonDefaults.buttonColors(containerColor = if (peer.isEmergency) TacticalSOSPulse else TacticalPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("CLOSE DISCOVERY MODULE", color = Color.Black, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// 1. TOP HEADER LAYOUT
@Composable
fun TopTacticalHeader(
    isBatterySaver: Boolean,
    isSosActive: Boolean,
    isPassphraseActive: Boolean,
    isScanning: Boolean,
    passphrase: String,
    onBatteryToggle: () -> Unit,
    onPassphraseClick: () -> Unit,
    onWipeAll: () -> Unit,
    frequency: String,
    peersCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("terminal_header")
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "MESH ACTIVE • $frequency MHz",
                color = TacticalPrimary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "SIGNAL",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Peer status pill from design HTML
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(TacticalSurface)
                    .border(1.dp, TacticalBorder, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                    modifier = Modifier.height(10.dp)
                ) {
                    Box(modifier = Modifier.size(2.dp, 3.dp).clip(CircleShape).background(TacticalPrimary))
                    Box(modifier = Modifier.size(2.dp, 6.dp).clip(CircleShape).background(if (peersCount > 0) TacticalPrimary else TacticalMutedText))
                    Box(modifier = Modifier.size(2.dp, 10.dp).clip(CircleShape).background(if (peersCount > 1) TacticalPrimary else TacticalMutedText))
                }
                Text(
                    "$peersCount PEERS",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            // Minimalist Action Pill 1: Battery saver mode
            IconButton(
                onClick = onBatteryToggle,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (isBatterySaver) TacticalTertiary else TacticalSurface)
                    .border(1.dp, TacticalBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Battery Mode",
                    tint = if (isBatterySaver) Color.Black else Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Minimalist Action Pill 2: Secure Passphrase Toggle
            IconButton(
                onClick = onPassphraseClick,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (isPassphraseActive) TacticalPrimary else TacticalSurface)
                    .border(1.dp, TacticalBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Scrambler secure Keys",
                    tint = if (isPassphraseActive) Color.Black else Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Minimalist Action Pill 3: Delete / Wipe all logs
            IconButton(
                onClick = onWipeAll,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(TacticalSurface)
                    .border(1.dp, TacticalBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Wipe Logs",
                    tint = TacticalSOSPulse,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

// 2. RADAR CANVAS CUSTOM VIEW
@Composable
fun TacticalRadar(
    peers: List<PeerNode>,
    isBatterySaverActive: Boolean,
    onPeerClick: (PeerNode) -> Unit
) {
    var sweepAngle by remember { mutableStateOf(0f) }

    // If battery saver is on, disable heavy radar sweeps to conserve GPU draw
    if (!isBatterySaverActive) {
        val infiniteTransition = rememberInfiniteTransition(label = "radar")
        val animatedAngle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(4000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "sweep"
        )
        LaunchedEffect(animatedAngle) {
            sweepAngle = animatedAngle
        }
    } else {
        // Freeze sweep line completely in Low Power
        sweepAngle = 45f
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(peers) {
                detectTapGestures { offset ->
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val activeRadius = size.width.coerceAtMost(size.height) / 2f - 16.dp.toPx()

                    // Match clicking offset coordinates to find the selected blip
                    for (peer in peers) {
                        val visualX = centerX + (peer.relativeX / 120f) * activeRadius
                        val visualY = centerY + (peer.relativeY / 120f) * activeRadius
                        val distToClick = Math.hypot((offset.x - visualX).toDouble(), (offset.y - visualY).toDouble())
                        if (distToClick < 24.dp.toPx()) {
                            onPeerClick(peer)
                            break
                        }
                    }
                }
            }
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val center = Offset(width / 2f, height / 2f)
        val radarRadius = width.coerceAtMost(height) / 2f - 16.dp.value

        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw concentric background scope grids
            drawCircle(color = TacticalBorder, radius = radarRadius, style = androidx.compose.ui.graphics.drawscope.Stroke(0.8f.dp.toPx()))
            drawCircle(color = TacticalBorder, radius = radarRadius * 0.66f, style = androidx.compose.ui.graphics.drawscope.Stroke(0.6f.dp.toPx()))
            drawCircle(color = TacticalBorder, radius = radarRadius * 0.33f, style = androidx.compose.ui.graphics.drawscope.Stroke(0.6f.dp.toPx()))

            // Crosshair lines
            drawLine(color = TacticalBorder, start = Offset(center.x - radarRadius, center.y), end = Offset(center.x + radarRadius, center.y), strokeWidth = 1f)
            drawLine(color = TacticalBorder, start = Offset(center.x, center.y - radarRadius), end = Offset(center.x, center.y + radarRadius), strokeWidth = 1f)

            // Draw sweeps (Exclude in battery saver to conserve power)
            if (!isBatterySaverActive) {
                val radians = Math.toRadians(sweepAngle.toDouble())
                val sweepX = center.x + radarRadius * cos(radians).toFloat()
                val sweepY = center.y + radarRadius * sin(radians).toFloat()

                drawLine(
                    color = TacticalPrimary.copy(alpha = 0.5f),
                    start = center,
                    end = Offset(sweepX, sweepY),
                    strokeWidth = 2.dp.toPx()
                )
            }

            // Draw Center Self Node (white circle with black contour stroke)
            drawCircle(
                color = Color.White,
                radius = 6.dp.toPx(),
                center = center
            )
            drawCircle(
                color = Color.Black,
                radius = 6.dp.toPx(),
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f.dp.toPx())
            )
        }

        // Place peer blips as floating components over canvas for easier click actions
        val centerX = maxWidth / 2
        val centerY = maxHeight / 2
        val activeRadarRadiusVal = (maxWidth.value.coerceAtMost(maxHeight.value) / 2f - 16f)

        for (peer in peers) {
            val visualX = centerX + ((peer.relativeX / 120f).coerceIn(-1f, 1f) * activeRadarRadiusVal).dp
            val visualY = centerY + ((peer.relativeY / 120f).coerceIn(-1f, 1f) * activeRadarRadiusVal).dp

            // Pulse animation for active emergency blips
            val distressPulse by rememberInfiniteTransition("emergency").animateFloat(
                initialValue = 0.3f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "distress"
            )

            Box(
                modifier = Modifier
                    .offset(x = visualX - 8.dp, y = visualY - 8.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(
                        color = if (peer.isEmergency) {
                            TacticalSOSPulse.copy(alpha = distressPulse)
                        } else {
                            TacticalPrimary
                        }
                    )
                    .border(
                        1.dp,
                        if (peer.isEmergency) Color.White else TacticalSecondary,
                        CircleShape
                    )
            )

            // Display shorthand labels next to blip
            val labelText = if (peer.displayName.contains("Ranger")) "RGR" else if (peer.displayName.contains("Relay")) "RLY" else "HKR"
            Text(
                text = "$labelText (${Math.hypot(peer.relativeX.toDouble(), peer.relativeY.toDouble()).toInt()}m)",
                color = if (peer.isEmergency) TacticalSOSPulse else TacticalSecondary,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .offset(x = visualX + 10.dp, y = visualY - 6.dp)
                    .background(Color(0xE6000000), RoundedCornerShape(3.dp))
                    .padding(horizontal = 2.dp, vertical = 1.dp)
            )
        }

        // Safe compass references
        Text("N", color = TacticalMutedText, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopCenter).offset(y = 2.dp))
        Text("S", color = TacticalMutedText, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomCenter).offset(y = (-2).dp))
        Text("W", color = TacticalMutedText, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterStart).offset(x = 2.dp))
        Text("E", color = TacticalMutedText, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterEnd).offset(x = (-2).dp))
    }
}

// 4. VERTICAL SLIDER TUNING DIAL
@Composable
fun FrequencyKnobScroll(
    channels: List<RadioChannel>,
    selectedIndex: Int,
    onChannelSelect: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TacticalBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = TacticalSurface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "BAND PRESET SELECTOR",
                color = TacticalMutedText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("ch_selector_list"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(channels.size) { index ->
                    val chan = channels[index]
                    val isSelected = index == selectedIndex
                    Box(
                        modifier = Modifier
                            .width(84.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) {
                                    if (chan.isEmergency) TacticalSOSPulse else TacticalPrimary
                                } else {
                                    Color(0x0CFFFFFF)
                                }
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color.White else TacticalBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onChannelSelect(index) }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "CH ${index + 1}",
                                color = if (isSelected) Color.Black else TacticalMutedText,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                chan.frequency,
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                if (chan.isEmergency) "WARN" else "OFFLINE",
                                color = if (isSelected) Color.Black else (if (chan.isEmergency) TacticalSOSPulse else TacticalSecondary),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

// 5. BUTTON SUB-TABS
@Composable
fun TabButton(
    text: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) TacticalSurface else Color.Transparent
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .border(
                1.dp,
                if (isActive) TacticalPrimary else TacticalBorder,
                RoundedCornerShape(12.dp)
            ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text,
            fontSize = 11.sp,
            color = if (isActive) TacticalPrimary else TacticalMutedText,
            fontWeight = FontWeight.Bold
        )
    }
}

// Empty State message inbox
@Composable
fun EmptyFeedState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "quiet",
            tint = TacticalBorder,
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "COMM FEED SILENT",
            color = TacticalMutedText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            "Tune dials or push-to-talk to begin relay",
            color = TacticalMutedText,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

// Message/Voice log item
@Composable
fun PacketRowItem(
    packet: RadioPacket,
    isPlaying: Boolean,
    currentPassphrase: String,
    isPassphraseActive: Boolean,
    onPlayClick: () -> Unit
) {
    val isFromMe = packet.senderName == "Me"
    val isEmergency = packet.messageType == "SOS"

    // Check if scrambling applies
    val isScrambled = packet.passphrase.isNotEmpty() &&
            packet.passphrase != currentPassphrase &&
            isPassphraseActive &&
            packet.messageType == "TEXT"

    val displayContent = if (isScrambled) {
        // Obfuscate standard chars
        "░█▒ ▓█░ CAUTION: MISMAPPED CRYPT UNIT"
    } else {
        packet.content
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isEmergency) TacticalSOSPulse else (if (isFromMe) TacticalBorder else TacticalPrimary.copy(alpha = 0.3f)),
                RoundedCornerShape(8.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isEmergency) Color(0x333A0A0A) else Color(0x1A000000)
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Meta info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isEmergency) TacticalSOSPulse else TacticalPrimary)
                    )
                    Text(
                        packet.senderName.uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = if (isEmergency) TacticalSOSPulse else (if (isFromMe) Color.White else TacticalPrimary),
                        fontSize = 11.sp
                    )
                    Text(
                        "${packet.channel} MHz",
                        fontSize = 9.sp,
                        color = TacticalMutedText,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = "${packet.rssi} dBm",
                    fontSize = 9.sp,
                    color = TacticalMutedText,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Body
            if (packet.messageType == "VOICE") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onPlayClick() }
                        .background(Color(0x3300FF66), RoundedCornerShape(6.dp))
                        .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isPlaying) "⏸ playing" else "▶ play",
                        color = TacticalPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (isPlaying) "DEMODULATING VOICE..." else "VOICE ENVELOPE RECEIVED",
                            color = TacticalPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.KeyboardArrowRight, contentDescription = "relay", tint = TacticalSecondary, modifier = Modifier.size(10.dp))
                            Text(
                                " COMPRESSION LEVEL: 12KBPS | OFFLINE LINK",
                                color = TacticalSecondary,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Rotating tiny wave visualization for voice clips
                    if (isPlaying) {
                        repeat(4) { idx ->
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(14.dp)
                                    .background(TacticalPrimary)
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = displayContent,
                    color = if (isEmergency) Color.White else (if (isFromMe) Color.White else TacticalSecondary),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Mesh path indicators
            if (packet.isRelayed) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = "tower",
                        tint = TacticalSecondary,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        "HOPS: ${packet.relayChain}",
                        fontSize = 8.sp,
                        color = TacticalSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

// 6. LOW POWER SILENT DIRECT CODES TRANSMITTER
@Composable
fun SilentPingsDashboard(onPingSend: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TacticalBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = TacticalSurface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "SILENT DIRECT TELEMETRY PINGS",
                fontSize = 10.sp,
                color = TacticalMutedText,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val pingOptions = listOf(
                    "🚨" to "RED",
                    "🆘" to "HELP",
                    "💧" to "WTR",
                    "🩹" to "AID",
                    "🚶" to "TRK",
                    "🏕️" to "CAMP",
                    "📡" to "ANT"
                )

                for ((emoji, name) in pingOptions) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x0CFFFFFF))
                            .border(1.dp, TacticalBorder, RoundedCornerShape(8.dp))
                            .clickable { onPingSend(emoji) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(emoji, fontSize = 16.sp)
                            Text(name, fontSize = 7.sp, color = TacticalMutedText, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// 7. BOTTOM PTT COCKPIT WITH TEXT MESSAGE AND PTT BUTTONS
@Composable
fun BottomControlCockpit(
    inputValue: String,
    onValueChange: (String) -> Unit,
    isRecording: Boolean,
    amplitude: Float,
    micLockOn: Boolean,
    onMicLockToggle: () -> Unit,
    onTouchDownPTT: () -> Unit,
    onTouchUpPTT: () -> Unit,
    onSendText: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TacticalBorder, RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = TacticalSurface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Text messaging and Send
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("text_ping_input"),
                    placeholder = { Text("Write text or distress ping...", fontSize = 11.sp, color = TacticalMutedText) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TacticalPrimary,
                        unfocusedBorderColor = TacticalBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.SansSerif, fontSize = 12.sp),
                    singleLine = true
                )

                IconButton(
                    onClick = onSendText,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(TacticalPrimary)
                        .size(46.dp)
                        .testTag("send_ping_button")
                ) {
                    Icon(imageVector = Icons.Default.Send, contentDescription = "Send", tint = Color.Black)
                }
            }

            // Realtime Amplitude Waveform graph
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF070707))
                    .border(1.dp, TacticalBorder, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (isRecording) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "[TX VOICE ACTIVE]",
                            color = TacticalSOSPulse,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 6.dp)
                        )

                        Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            val count = 20
                            val widthPart = size.width / count
                            for (i in 0 until count) {
                                val ranAmp = (amplitude * (1.0f - (cos(i * 0.3) * 0.2f))).toFloat()
                                val barHeight = size.height * ranAmp * ((0..100).random() / 100f)
                                drawLine(
                                    color = TacticalSOSPulse,
                                    start = Offset(i * widthPart, (size.height - barHeight) / 2f),
                                    end = Offset(i * widthPart, (size.height + barHeight) / 2f),
                                    strokeWidth = 3.dp.toPx()
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        "GRID ENVELOPE IDLE // TOUCH PTT KEY TO TRANSMIT VOICE",
                        color = TacticalMutedText,
                        fontSize = 8.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // PTT Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // TRANSMIT SQUELCH LOCK SWITCH (No hold needed!)
                Button(
                    onClick = onMicLockToggle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (micLockOn) TacticalSOSPulse else Color(0x1AFFFFFF)
                    ),
                    modifier = Modifier
                        .height(72.dp)
                        .weight(0.35f)
                        .border(
                            1.dp,
                            if (micLockOn) Color.White else TacticalBorder,
                            RoundedCornerShape(16.dp)
                        ),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "mic lock method",
                            tint = if (micLockOn) Color.White else TacticalMutedText,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (micLockOn) "TX LOCKED" else "TX MUTED",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // PUSH-TO-TALK PHYSICAL COMMAND (Design mockup matching: white background, black items, beautiful dynamic states)
                val isPttPressed = isRecording && !micLockOn
                val pttBgColor by animateColorAsState(
                    targetValue = if (isPttPressed) TacticalSOSPulse else Color.White,
                    label = "pttColor"
                )
                val pttContentColor = if (isPttPressed) Color.White else Color.Black

                Box(
                    modifier = Modifier
                        .weight(0.65f)
                        .height(72.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(pttBgColor)
                        .border(
                            width = 1.dp,
                            color = if (isPttPressed) Color.White else Color.White.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    onTouchDownPTT()
                                    tryAwaitRelease()
                                    onTouchUpPTT()
                                }
                            )
                        }
                        .testTag("push_to_talk_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(if (isPttPressed) Color.White else Color.Black)
                            ) {
                                if (!isPttPressed) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                    )
                                }
                            }
                            Text(
                                "PUSH TO TALK",
                                color = pttContentColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            if (isPttPressed) "TRANSMITTING TO ACTIVE NODES..." else "BLUETOOTH / WIFI DIRECT LINK",
                            color = pttContentColor.copy(alpha = 0.6f),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
