package com.zerotrace.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PublicOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zerotrace.ui.AppLanguage
import com.zerotrace.ui.theme.AmberWarning
import com.zerotrace.ui.theme.EmeraldPrimary

/**
 * Screen 1: Splash Screen (Section 7.1)
 */
@Composable
fun ZeroTraceSplashScreen(language: AppLanguage) {
    val isHi = language == AppLanguage.HINDI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(2.dp, EmeraldPrimary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "ZeroTrace Cryptographic Shield",
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(46.dp)
                )
            }

            Text(
                text = "ZEROTRACE",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = if (isHi) {
                    "निजी संवाद। कोई केंद्रीय सर्वर नहीं। कोई स्थायी चैट इतिहास नहीं।"
                } else {
                    "Private Communication. No Central Server. No Persistent Conversations."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            CircularProgressIndicator(
                color = EmeraldPrimary,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(32.dp)
            )

            Text(
                text = if (isHi) {
                    "हार्डवेयर कीस्टोर और क्रिप्टोग्राफ़िक कोर सत्यापित हो रहा है…"
                } else {
                    "INITIALIZING KEYSTORE & POST-QUANTUM CORE…"
                },
                style = MaterialTheme.typography.labelSmall,
                color = EmeraldPrimary
            )
        }
    }
}

/**
 * Screen 2: Onboarding & Mandatory Security Limitations Acceptance (Section 7.2)
 */
@Composable
fun ZeroTraceOnboardingScreen(
    language: AppLanguage,
    limitationsAccepted: Boolean,
    onSelectLanguage: (AppLanguage) -> Unit,
    onToggleAcceptance: (Boolean) -> Unit,
    onProceed: () -> Unit
) {
    val isHi = language == AppLanguage.HINDI

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("onboarding_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header + Language Switcher
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ZEROTRACE 2.0",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppLanguage.entries.forEach { lang ->
                    FilterChip(
                        selected = language == lang,
                        onClick = { onSelectLanguage(lang) },
                        label = {
                            Text(
                                text = if (lang == AppLanguage.ENGLISH) "EN" else "हिन्दी",
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("lang_chip_${lang.code}")
                    )
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, EmeraldPrimary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (isHi) "सख्त अल्पकालिक (STRICT EPHEMERAL) मोड" else "STRICT EPHEMERAL ARCHITECTURE",
                    style = MaterialTheme.typography.labelLarge,
                    color = EmeraldPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isHi) {
                        "ZeroTrace को बिना किसी केंद्रीय सर्वर, फ़ोन नंबर या स्थायी डेटाबेस के डिज़ाइन किया गया है। आगे बढ़ने से पहले नीचे दिए गए नियमों और सीमाओं की समीक्षा करें।"
                    } else {
                        "ZeroTrace operates with zero central servers, zero account identifiers, and RAM-only message storage. Review and accept the operational constraints below."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        val rules = if (isHi) {
            listOf(
                Triple(
                    Icons.Default.PublicOff,
                    "कोई फ़ोन नंबर या केंद्रीय सर्वर नहीं",
                    "फ़ोन नंबर, ईमेल, उपयोगकर्ता नाम या कंपनी-संचालित मैसेजिंग सर्वर की आवश्यकता नहीं है।"
                ),
                Triple(
                    Icons.Default.Lock,
                    "दोनों उपयोगकर्ताओं का एक साथ ऑनलाइन होना आवश्यक है",
                    "कोई ऑफ़लाइन संदेश कतार (queue) या क्लाउड सिंक नहीं है। संवाद के समय दोनों पीयर Tor पर ऑनलाइन होने चाहिए।"
                ),
                Triple(
                    Icons.Default.Memory,
                    "केवल अस्थायी RAM में बातचीत",
                    "संदेश केवल अस्थायी मेमोरी (RAM) में रहते हैं। डिस्कनेक्ट होने या ऐप बंद होने पर बातचीत हमेशा के लिए मिट जाती है।"
                ),
                Triple(
                    Icons.Default.CheckCircle,
                    "हाइब्रिड पोस्ट-क्वांटम क्रिप्टोग्राफी + Tor",
                    "X25519 + NIST FIPS 203 ML-KEM-1024 और Ed25519 + FIPS 204 ML-DSA-87 के साथ Tor अनियन सर्विस।"
                ),
                Triple(
                    Icons.Default.WarningAmber,
                    "ईमानदार सुरक्षा सीमाएं (कोई झूठा दावा नहीं)",
                    "Tor कनेक्टिविटी धीमी हो सकती है। यदि आपका डिवाइस या Android कर्नेल पहले से हैक (compromised) है, तो कोई भी ऐप पूर्ण सुरक्षा की गारंटी नहीं दे सकता।"
                )
            )
        } else {
            listOf(
                Triple(
                    Icons.Default.PublicOff,
                    "No Phone Number & No Central Server",
                    "Zero phone numbers, emails, accounts, or company-operated relay servers. Each device generates its own cryptographic identity."
                ),
                Triple(
                    Icons.Default.Lock,
                    "Simultaneous Online Requirement",
                    "There is no offline message queue or cloud database. Both participants must be online simultaneously via Tor Onion Services."
                ),
                Triple(
                    Icons.Default.Memory,
                    "Volatile RAM-Only Conversations",
                    "Messages exist exclusively in volatile RAM. Disconnecting or closing the session permanently erases the conversation."
                ),
                Triple(
                    Icons.Default.CheckCircle,
                    "Hybrid Post-Quantum E2EE over Tor",
                    "Combines classical X25519/Ed25519 with NIST FIPS 203 ML-KEM-1024 and FIPS 204 ML-DSA-87 over fail-closed Tor circuits."
                ),
                Triple(
                    Icons.Default.WarningAmber,
                    "Explicit Security Limitations",
                    "Tor routing introduces latency. No software can guarantee protection against a fully compromised OS kernel, hardware keylogger, or live physical RAM extraction."
                )
            )
        }

        rules.forEachIndexed { idx, (icon, title, body) ->
            OnboardingRuleCard(
                icon = icon,
                title = title,
                body = body,
                isWarning = idx == rules.lastIndex
            )
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .clickable { onToggleAcceptance(!limitationsAccepted) }
                .padding(12.dp)
                .testTag("accept_limitations_row")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = limitationsAccepted,
                    onCheckedChange = onToggleAcceptance,
                    modifier = Modifier.testTag("accept_limitations_checkbox")
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isHi) {
                        "मैं समझता/समझती हूँ कि दोनों उपयोगकर्ताओं को ऑनलाइन रहना होगा, चैट इतिहास सहेजा नहीं जाता है, और कोई भी ऐप हैक किए गए ऑपरेटिंग सिस्टम से पूर्ण सुरक्षा की गारंटी नहीं दे सकता।"
                    } else {
                        "I understand and accept the simultaneous-online requirement, volatile RAM-only message lifecycle, and explicit device security limitations."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Button(
            onClick = onProceed,
            enabled = limitationsAccepted,
            colors = ButtonDefaults.buttonColors(
                containerColor = EmeraldPrimary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("onboarding_continue_button")
        ) {
            Text(
                text = if (isHi) "पहचान निर्माण पर आगे बढ़ें" else "PROCEED TO IDENTITY CREATION",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun OnboardingRuleCard(
    icon: ImageVector,
    title: String,
    body: String,
    isWarning: Boolean
) {
    val accent = if (isWarning) AmberWarning else EmeraldPrimary
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
