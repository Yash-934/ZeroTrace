package com.zerotrace.ui.pairing

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.zerotrace.bridge.ParsedQrInvite
import com.zerotrace.bridge.PublicIdentitySummary
import com.zerotrace.security.SecureWindowManager
import com.zerotrace.ui.AppLanguage
import com.zerotrace.ui.IdentityReplacementAlert
import com.zerotrace.ui.theme.CrimsonDanger
import com.zerotrace.ui.theme.CyanAccent
import com.zerotrace.ui.theme.EmeraldPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Saves a high-contrast QR Bitmap into the user's Pictures/ZeroTrace gallery collection
 * using MediaStore without requiring any storage permissions on modern Android.
 */
fun saveQrBitmapToGallery(context: Context, qrPayload: String): Boolean {
    return try {
        val bitmap = QrCodeEngine.encodeQrAndroidBitmap(qrPayload, 760)
        val filename = "ZeroTrace_QR_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ZeroTrace")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        true
    } catch (_: Exception) {
        false
    }
}

/**
 * Screen 6: Add Contact Screen (Section 7.6)
 * Options:
 * - Scan QR (CameraX live scanner with runtime CAMERA permission)
 * - Upload QR from Gallery / Screenshot (Zero-permission Android Photo Picker for older cameras)
 * - Display my QR (with Save QR to Gallery, Copy & Share Signed ZT2|v1 invite)
 * - Paste/Verify signed ZT2|v1 string + Loopback Verification Peer QR scan
 * - Cancel pairing
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddContactScreen(
    language: AppLanguage,
    myPairingQrString: String?,
    myPairingQrExpirySec: Long,
    errorBanner: String?,
    onQrScanned: (String) -> Unit,
    onScanLoopbackPeerQr: () -> Unit,
    onRefreshMyQr: () -> Unit,
    onCancelPairing: () -> Unit
) {
    BackHandler(onBack = onCancelPairing)
    val isHi = language == AppLanguage.HINDI
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showMyQrTab by remember { mutableStateOf(false) }
    var manualQrInput by remember { mutableStateOf("") }
    var localGalleryMessage by remember { mutableStateOf<String?>(null) }
    var localGalleryError by remember { mutableStateOf<String?>(null) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    // Zero-permission Android Photo Picker for uploading a QR image or screenshot from Gallery
    val galleryQrPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            localGalleryError = null
            localGalleryMessage = if (isHi) "गैलरी छवि से QR डिकोड किया जा रहा है…" else "Decoding QR from gallery image…"
            scope.launch {
                val decoded = withContext(Dispatchers.IO) {
                    QrCodeEngine.decodeQrFromUri(context, uri)
                }
                localGalleryMessage = null
                if (!decoded.isNullOrBlank()) {
                    onQrScanned(decoded)
                } else {
                    localGalleryError = if (isHi) {
                        "चुनी गई छवि में कोई वैध ZT2|v1 QR कोड नहीं मिला। कृपया स्पष्ट QR छवि चुनें या नीचे ZT2|v1 टेक्स्ट पेस्ट करें।"
                    } else {
                        "No valid ZT2|v1 QR code detected in the selected image. Please select a clearer QR image or paste the ZT2|v1 invite string below."
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isHi) "संपर्क जोड़ें (QR पेयरिंग)" else "AUTHENTICATED QR PAIRING",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancelPairing, modifier = Modifier.testTag("add_contact_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("add_contact_screen"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val activeError = errorBanner ?: localGalleryError
            if (activeError != null) {
                Surface(
                    color = CrimsonDanger.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CrimsonDanger, RoundedCornerShape(10.dp))
                ) {
                    Text(
                        text = activeError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CrimsonDanger,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (localGalleryMessage != null) {
                Surface(
                    color = EmeraldPrimary.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, EmeraldPrimary, RoundedCornerShape(10.dp))
                ) {
                    Text(
                        text = localGalleryMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = EmeraldPrimary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Tab selector: Scan Peer QR vs Display My QR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = !showMyQrTab,
                    onClick = { showMyQrTab = false },
                    label = { Text(if (isHi) "पीयर QR स्कैन / अपलोड" else "Scan / Upload QR") },
                    leadingIcon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_scan_qr")
                )
                FilterChip(
                    selected = showMyQrTab,
                    onClick = {
                        showMyQrTab = true
                        onRefreshMyQr()
                    },
                    label = { Text(if (isHi) "मेरा QR दिखाएं" else "Display My QR") },
                    leadingIcon = { Icon(Icons.Default.QrCode2, contentDescription = null) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_display_my_qr")
                )
            }

            if (showMyQrTab) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, EmeraldPrimary.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (isHi) "अस्थायी हस्ताक्षरित पेयरिंग QR" else "TEMPORARY SIGNED PAIRING QR (ZT2|v1)",
                            style = MaterialTheme.typography.titleMedium,
                            color = EmeraldPrimary
                        )

                        if (myPairingQrString != null) {
                            val qrBitmap = remember(myPairingQrString) {
                                QrCodeEngine.encodeQrBitmap(myPairingQrString, 680)
                            }
                            Box(
                                modifier = Modifier
                                    .size(250.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White)
                                    .border(3.dp, EmeraldPrimary, RoundedCornerShape(12.dp))
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = qrBitmap,
                                    contentDescription = "My Signed Pairing QR",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Text(
                                text = if (isHi) {
                                    "समाप्ति (Unix): $myPairingQrExpirySec • Ed25519 हस्ताक्षरित"
                                } else {
                                    "Expiry (Unix): $myPairingQrExpirySec • Ed25519 Signed + PQ Fingerprint"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val ok = withContext(Dispatchers.IO) {
                                                saveQrBitmapToGallery(context, myPairingQrString)
                                            }
                                            localGalleryError = null
                                            localGalleryMessage = if (ok) {
                                                if (isHi) "QR छवि गैलरी (Pictures/ZeroTrace) में सहेजी गई!" else "QR image saved to Gallery (Pictures/ZeroTrace)!"
                                            } else {
                                                if (isHi) "गैलरी में QR सहेजने में विफल।" else "Could not save QR image to Gallery."
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("save_my_qr_to_gallery_button")
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isHi) "गैलरी में सेव करें" else "Save QR Image")
                                }

                                OutlinedButton(
                                    onClick = {
                                        SecureWindowManager.copyWithAutoScrub(
                                            context = context,
                                            label = "ZeroTrace Signed QR Invite",
                                            value = myPairingQrString,
                                            scope = scope
                                        )
                                        localGalleryError = null
                                        localGalleryMessage = if (isHi) {
                                            "हस्ताक्षरित ZT2|v1 पेलोड क्लिपबोर्ड पर कॉपी किया गया!"
                                        } else {
                                            "Signed ZT2|v1 invite copied to clipboard!"
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("copy_my_qr_payload_button")
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isHi) "ZT2|v1 कॉपी" else "Copy Invite")
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    try {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, myPairingQrString)
                                        }
                                        context.startActivity(
                                            Intent.createChooser(
                                                shareIntent,
                                                if (isHi) "हस्ताक्षरित ZT2|v1 पेयरिंग कोड साझा करें" else "Share Signed ZT2|v1 Invite"
                                            )
                                        )
                                    } catch (_: Exception) {
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("share_my_qr_payload_button")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isHi) "दूसरे डिवाइस पर ZT2|v1 इनवाइट भेजें" else "Share Signed ZT2|v1 Invite to Another Device")
                            }
                        }

                        OutlinedButton(
                            onClick = onRefreshMyQr,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isHi) "नया अस्थायी नॉनस जनरेट करें" else "Rotate Pairing Nonce & Expiry")
                        }
                    }
                }
            } else {
                // 1. Upload QR from Gallery / Screenshot (Prominent option for devices with older cameras)
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, CyanAccent.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = CyanAccent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isHi) {
                                    "गैलरी / स्क्रीनशॉट से QR अपलोड करें"
                                } else {
                                    "UPLOAD QR FROM GALLERY / SCREENSHOT"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = CyanAccent
                            )
                        }
                        Text(
                            text = if (isHi) {
                                "यदि पुराने डिवाइस में कैमरा फ़ोकस नहीं कर पा रहा है, तो दूसरे फ़ोन के QR का स्क्रीनशॉट या फोटो गैलरी से चुनें।"
                            } else {
                                "Ideal for devices with older cameras: select a saved QR image or screenshot of your peer's QR code directly from your Gallery."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                galleryQrPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("upload_qr_gallery_button")
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isHi) "गैलरी से QR फोटो चुनें" else "SELECT QR IMAGE FROM GALLERY",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.Black
                            )
                        }
                    }
                }

                // 2. CameraX Live QR Scanner Box
                if (hasCameraPermission) {
                    CameraXQrScannerPreview(
                        onQrDetected = onQrScanned,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                    )
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                            .padding(18.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = if (isHi) "कैमरा QR स्कैनर अनुमति" else "Camera Permission Required for Live QR Scan",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (isHi) {
                                    "केवल पीयर के ZT2|v1 QR कोड को स्कैन करने के लिए कैमरा अनुमति का उपयोग किया जाता है।"
                                } else {
                                    "ZeroTrace requests CAMERA permission strictly for local ZXing YUV frame analysis."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                modifier = Modifier.testTag("grant_camera_permission_button")
                            ) {
                                Text(if (isHi) "कैमरा स्कैनर सक्षम करें" else "Enable Camera QR Scanner")
                            }
                        }
                    }
                }

                // 3. Loopback Verification Peer QR Scan (for single-device/emulator E2EE testing)
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, EmeraldPrimary.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isHi) {
                                "लोकल लूपबैक अनियन पीयर नोड (127.0.0.1 सत्यापन)"
                            } else {
                                "LOCAL LOOPBACK ONION PEER (127.0.0.1 E2EE VERIFICATION)"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = EmeraldPrimary
                        )
                        Text(
                            text = if (isHi) {
                                "एकल डिवाइस पर पूर्ण QR पेयरिंग, फ़िंगरप्रिंट सत्यापन, ZT-PQXDH-P2P-v1 हैंडशेक और ML-KEM-1024 रैचेट मैसेजिंग का परीक्षण करने के लिए स्थानीय पीयर नोड का हस्ताक्षरित QR स्कैन करें।"
                            } else {
                                "Scan a genuine ZT2|v1 signed QR artifact generated by the independent 127.0.0.1 loopback Onion responder node to verify full PQ handshake and ratcheted messaging."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onScanLoopbackPeerQr,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("scan_loopback_peer_qr_button")
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isHi) "लूपबैक पीयर QR स्कैन और सत्यापित करें" else "SCAN LOOPBACK PEER QR (ZT2|v1)",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                // 4. Manual Signed ZT2|v1 Payload Verification Input
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (isHi) "हस्ताक्षरित ZT2|v1 पेलोड सत्यापित करें" else "VERIFY SIGNED ZT2|v1 PAYLOAD STRING",
                            style = MaterialTheme.typography.labelLarge,
                            color = CyanAccent
                        )
                        OutlinedTextField(
                            value = manualQrInput,
                            onValueChange = { manualQrInput = it },
                            placeholder = { Text("ZT2|v1|<onion>|<classical_fp>|<pq_fp>|...") },
                            singleLine = false,
                            maxLines = 3,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("manual_qr_input")
                        )
                        OutlinedButton(
                            onClick = { onQrScanned(manualQrInput) },
                            enabled = manualQrInput.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("verify_manual_qr_button")
                        ) {
                            Text(if (isHi) "हस्ताक्षर और फ़िंगरप्रिंट की जाँच करें" else "Validate Signature & Compare Fingerprints")
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onCancelPairing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("cancel_pairing_button")
            ) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isHi) "पेयरिंग रद्द करें" else "Cancel Pairing")
            }
        }
    }
}

/**
 * Screen 7: Contact Verification Screen (Section 7.7)
 * Shows both local and peer Classical + Post-Quantum fingerprints in a side-by-side comparison format.
 * Requires explicit confirmation before saving contact.
 * Displays a prominent blocking security alert if a peer's identity changed unexpectedly!
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactVerificationScreen(
    language: AppLanguage,
    localIdentity: PublicIdentitySummary?,
    scannedInvite: ParsedQrInvite?,
    contactAlias: String,
    identityReplacementAlert: IdentityReplacementAlert?,
    onAliasChanged: (String) -> Unit,
    onConfirmVerifiedContact: (Boolean) -> Unit,
    onCancel: () -> Unit
) {
    BackHandler(onBack = onCancel)
    val isHi = language == AppLanguage.HINDI
    var explicitFingerprintConfirmed by remember { mutableStateOf(false) }
    var explicitReplacementOverrideConfirmed by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isHi) "फ़िंगरप्रिंट सत्यापन" else "CRYPTOGRAPHIC FINGERPRINT VERIFICATION",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("contact_verification_screen"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (scannedInvite == null) {
                Text("No pending QR invite to verify.")
                return@Column
            }

            // Prominent Security Warning if Peer Identity Changed Unexpectedly (Section 3.2)
            if (identityReplacementAlert != null) {
                Surface(
                    color = CrimsonDanger.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, CrimsonDanger, RoundedCornerShape(14.dp))
                        .testTag("identity_replacement_warning_banner")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = CrimsonDanger)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isHi) {
                                    "गंभीर सुरक्षा चेतावनी: पीयर की पहचान बदल गई है!"
                                } else {
                                    "CRITICAL SECURITY WARNING: PEER IDENTITY CHANGED!"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = CrimsonDanger
                            )
                        }
                        Text(
                            text = if (isHi) {
                                "${identityReplacementAlert.existingContact.alias} (${scannedInvite.onionAddress.take(16)}…) के लिए पहले से पिन किया गया फ़िंगरप्रिंट नए QR से मेल नहीं खाता है। यह MITM हमले का संकेत हो सकता है।"
                            } else {
                                "The `.onion` address for '${identityReplacementAlert.existingContact.alias}' previously pinned a different Classical or Post-Quantum key. Silent replacement is blocked."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Previous PQ FP: ${identityReplacementAlert.existingContact.pqSafetyDigits}\nNew PQ FP:      ${scannedInvite.pqSafetyDigits}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CrimsonDanger
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = explicitReplacementOverrideConfirmed,
                                onCheckedChange = { explicitReplacementOverrideConfirmed = it }
                            )
                            Text(
                                text = if (isHi) {
                                    "मैंने व्यक्ति से व्यक्तिगत रूप से पुष्टि की है कि उन्होंने अपनी पहचान रीसेट की है।"
                                } else {
                                    "I have verified out-of-band that this peer intentionally generated a new identity."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = CrimsonDanger
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = contactAlias,
                onValueChange = onAliasChanged,
                label = { Text(if (isHi) "स्थानीय संपर्क उपनाम (Alias)" else "Local Contact Alias") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("contact_alias_input")
            )

            // Peer Verified Attributes Card
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, EmeraldPrimary.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (isHi) "पीयर की क्रिप्टोग्राफ़िक पहचान (स्कैन किया गया)" else "PEER CRYPTOGRAPHIC IDENTITY (SCANNED)",
                        style = MaterialTheme.typography.labelLarge,
                        color = EmeraldPrimary
                    )
                    Text(
                        text = "Onion: ${scannedInvite.onionAddress}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanAccent
                    )
                    Text(
                        text = "Classical Safety Digits (Ed25519 + X25519):\n${scannedInvite.classicalSafetyDigits}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Post-Quantum Safety Digits (ML-DSA-87 + ML-KEM-1024):\n${scannedInvite.pqSafetyDigits}",
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldPrimary
                    )
                    Text(
                        text = "Pairing Nonce: ${scannedInvite.pairingNonceHex} • Capabilities: ${scannedInvite.capabilities}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Local Identity Comparison Card
            if (localIdentity != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isHi) "मेरी पहचान (तुलना के लिए)" else "MY LOCAL IDENTITY (FOR SIDE-BY-SIDE COMPARISON)",
                            style = MaterialTheme.typography.labelLarge,
                            color = CyanAccent
                        )
                        Text(
                            text = "My Classical Safety Digits:\n${localIdentity.classicalSafetyDigits}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "My Post-Quantum Safety Digits:\n${localIdentity.pqSafetyDigits}",
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldPrimary
                        )
                    }
                }
            }

            // Explicit Verification Confirmation Checkbox
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = explicitFingerprintConfirmed,
                        onCheckedChange = { explicitFingerprintConfirmed = it },
                        modifier = Modifier.testTag("confirm_fingerprint_checkbox")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isHi) {
                            "मैंने क्लासिकल और पोस्ट-क्वांटम दोनों सुरक्षा फ़िंगरप्रिंट का मिलान कर लिया है।"
                        } else {
                            "I have compared and verified both the Classical and Post-Quantum safety fingerprints."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            val canSave = explicitFingerprintConfirmed &&
                (identityReplacementAlert == null || explicitReplacementOverrideConfirmed)

            Button(
                onClick = { onConfirmVerifiedContact(explicitReplacementOverrideConfirmed) },
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("confirm_save_contact_button")
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isHi) "सत्यापित संपर्क सहेजें" else "CONFIRM & SAVE VERIFIED CONTACT",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
