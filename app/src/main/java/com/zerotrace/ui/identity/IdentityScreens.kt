package com.zerotrace.ui.identity

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.zerotrace.bridge.PublicIdentitySummary
import com.zerotrace.network.ConnectivityState
import com.zerotrace.security.HardwareSecurityLevel
import com.zerotrace.security.SecureWindowManager
import com.zerotrace.ui.AppLanguage
import com.zerotrace.ui.pairing.QrCodeEngine
import com.zerotrace.ui.theme.AmberWarning
import com.zerotrace.ui.theme.CyanAccent
import com.zerotrace.ui.theme.EmeraldPrimary

/**
 * Screen 3: Identity Creation Screen (Section 7.3)
 */
@Composable
fun IdentityCreationScreen(
    language: AppLanguage,
    isGenerating: Boolean,
    identitySummary: PublicIdentitySummary?,
    hardwareSecurityLevel: HardwareSecurityLevel,
    connectivityState: ConnectivityState,
    onGenerateIdentity: () -> Unit,
    onContinueToHome: () -> Unit
) {
    val isHi = language == AppLanguage.HINDI

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("identity_creation_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (isHi) "दोहरी पोस्ट-क्वांटम पहचान निर्माण" else "CRYPTOGRAPHIC IDENTITY CREATION",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = if (isHi) {
                "आपका डिवाइस स्वतंत्र रूप से क्लासिकल (Ed25519 + X25519) और पोस्ट-क्वांटम (NIST FIPS 204 ML-DSA-87 + FIPS 203 ML-KEM-1024) कुंजियाँ उत्पन्न करता है और उन्हें Android कीस्टोर AES-256-GCM के तहत सुरक्षित करता है।"
            } else {
                "Your device independently generates Classical (Ed25519 + X25519) and Post-Quantum (NIST FIPS 204 ML-DSA-87 + FIPS 203 ML-KEM-1024) keypairs and wraps private material under Android Keystore AES-256-GCM."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (identitySummary == null) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                    .padding(20.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = if (isHi) {
                            "CSPRNG एंट्रॉपी से कुंजियाँ उत्पन्न करने के लिए नीचे टैप करें"
                        } else {
                            "Ready to generate hybrid identity from OS CSPRNG"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (isGenerating) {
                        CircularProgressIndicator(color = EmeraldPrimary)
                        Text(
                            text = if (isHi) {
                                "ML-DSA-87 (2592B PK) और ML-KEM-1024 (1568B EK) कुंजियाँ बन रही हैं…"
                            } else {
                                "Generating ML-DSA-87 & ML-KEM-1024 lattices + Keystore AES-256-GCM wrap…"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldPrimary
                        )
                    } else {
                        Button(
                            onClick = onGenerateIdentity,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("generate_identity_button")
                        ) {
                            Text(
                                text = if (isHi) "सुरक्षित डिवाइस पहचान बनाएं" else "GENERATE PROTECTED IDENTITY",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        } else {
            IdentitySummaryCard(
                language = language,
                summary = identitySummary,
                hardwareSecurityLevel = hardwareSecurityLevel,
                connectivityState = connectivityState
            )

            Button(
                onClick = onContinueToHome,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("identity_continue_home_button")
            ) {
                Text(
                    text = if (isHi) "होम स्क्रीन में प्रवेश करें" else "ENTER ZEROTRACE CONSOLE",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/**
 * Screen 5: My Identity Screen (Section 7.5)
 * Displays Onion address, Classical fingerprint, PQ fingerprint, Device hardware capability,
 * and temporary signed pairing QR. Never exposes private keys.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyIdentityScreen(
    language: AppLanguage,
    summary: PublicIdentitySummary?,
    hardwareSecurityLevel: HardwareSecurityLevel,
    connectivityState: ConnectivityState,
    pairingQrString: String?,
    pairingQrExpirySec: Long,
    onRefreshQr: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val isHi = language == AppLanguage.HINDI
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isHi) "मेरी क्रिप्टोग्राफ़िक पहचान" else "MY CRYPTOGRAPHIC IDENTITY",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("my_identity_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
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
                .testTag("my_identity_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (summary != null) {
                IdentitySummaryCard(
                    language = language,
                    summary = summary,
                    hardwareSecurityLevel = hardwareSecurityLevel,
                    connectivityState = connectivityState
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            SecureWindowManager.copyWithAutoScrub(
                                context = context,
                                label = "ZeroTrace Onion Address",
                                value = summary.onionAddress,
                                scope = scope
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("copy_onion_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isHi) ".onion कॉपी करें" else "Copy .onion")
                    }

                    OutlinedButton(
                        onClick = {
                            SecureWindowManager.copyWithAutoScrub(
                                context = context,
                                label = "ZeroTrace Fingerprints",
                                value = "Classical: ${summary.classicalFingerprintHex}\nPQ: ${summary.pqFingerprintHex}",
                                scope = scope
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("copy_fingerprints_button")
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isHi) "फ़िंगरप्रिंट कॉपी" else "Copy Prints")
                    }
                }

                // Signed Ephemeral Pairing QR Artifact
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, EmeraldPrimary.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.QrCode2, contentDescription = null, tint = EmeraldPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isHi) "हस्ताक्षरित पेयरिंग QR (ZT2|v1)" else "SIGNED PAIRING QR (ZT2|v1)",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            IconButton(onClick = onRefreshQr, modifier = Modifier.testTag("refresh_qr_button")) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh QR", tint = EmeraldPrimary)
                            }
                        }

                        if (pairingQrString != null) {
                            val qrBitmap = remember(pairingQrString) {
                                QrCodeEngine.encodeQrBitmap(pairingQrString, 560)
                            }
                            Box(
                                modifier = Modifier
                                    .size(230.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(2.dp, EmeraldPrimary, RoundedCornerShape(12.dp))
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = qrBitmap,
                                    contentDescription = "Signed ZeroTrace Pairing QR Code",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Text(
                                text = if (isHi) {
                                    "समाप्ति (Unix): $pairingQrExpirySec • Ed25519 हस्ताक्षरित + PQ प्रतिबद्धता"
                                } else {
                                    "Expires (Unix): $pairingQrExpirySec • Ed25519-Signed + PQ Commitment"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IdentitySummaryCard(
    language: AppLanguage,
    summary: PublicIdentitySummary,
    hardwareSecurityLevel: HardwareSecurityLevel,
    connectivityState: ConnectivityState
) {
    val isHi = language == AppLanguage.HINDI
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, EmeraldPrimary.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = EmeraldPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isHi) "निजी कुंजियाँ कभी भी UI में उजागर नहीं होतीं" else "PRIVATE KEYS ISOLATED IN SECURITY CORE",
                    style = MaterialTheme.typography.labelLarge,
                    color = EmeraldPrimary
                )
            }

            KeyDetailBlock(
                label = if (isHi) "TOR V3 अनियन पता (नेटवर्क पहचान)" else "TOR V3 ONION ENDPOINT (SEPARATE FROM IDENTITY)",
                value = summary.onionAddress,
                accentColor = CyanAccent
            )

            KeyDetailBlock(
                label = if (isHi) "क्लासिकल फ़िंगरप्रिंट (Ed25519 + X25519 SHA-256)" else "CLASSICAL FINGERPRINT (Ed25519 + X25519)",
                value = "${summary.classicalSafetyDigits}\nHEX: ${summary.classicalFingerprintHex.chunked(16).joinToString(" ")}",
                accentColor = EmeraldPrimary
            )

            KeyDetailBlock(
                label = if (isHi) "पोस्ट-क्वांटम फ़िंगरप्रिंट (FIPS 204 ML-DSA-87 + FIPS 203 ML-KEM-1024)" else "POST-QUANTUM FINGERPRINT (ML-DSA-87 + ML-KEM-1024)",
                value = "${summary.pqSafetyDigits}\nHEX: ${summary.pqFingerprintHex.chunked(16).joinToString(" ")}",
                accentColor = EmeraldPrimary
            )

            KeyDetailBlock(
                label = if (isHi) "डिवाइस हार्डवेयर सुरक्षा स्तर" else "DEVICE KEYSTORE SECURITY CAPABILITY",
                value = if (isHi) hardwareSecurityLevel.labelHi else hardwareSecurityLevel.labelEn,
                accentColor = if (hardwareSecurityLevel.isHardwareBacked) EmeraldPrimary else AmberWarning
            )

            KeyDetailBlock(
                label = if (isHi) "वर्तमान Tor स्थिति" else "CURRENT TOR / ONION CIRCUIT STATUS",
                value = "${connectivityState.code} — ${if (isHi) connectivityState.labelHi else connectivityState.labelEn}",
                accentColor = EmeraldPrimary
            )
        }
    }
}

@Composable
private fun KeyDetailBlock(
    label: String,
    value: String,
    accentColor: androidx.compose.ui.graphics.Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
