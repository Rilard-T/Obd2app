package com.example.ui.components

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.BuildConfig
import com.example.R
import com.example.obd.data.PlayBillingBridge
import com.example.obd.i18n.l10nStr
import com.example.obd.model.BillingUiState
import com.example.ui.theme.CarbonObsidian
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.CockpitSurface
import com.example.ui.theme.CockpitSurfaceElevated
import com.example.ui.theme.MotorsportAmber
import com.example.ui.theme.NominalEmerald
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextSecondary

@Composable
fun SafetyDisclaimerDialog(
    onAccept: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onAccept,
        icon = {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = null,
                tint = TelemetryCyan,
                modifier = Modifier.size(28.dp)
            )
        },
        title = { Text(l10nStr(R.string.safety_title)) },
        text = { Text(l10nStr(R.string.safety_body), color = TextSecondary) },
        confirmButton = {
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan),
                modifier = Modifier.testTag("accept_safety_disclaimer_button")
            ) {
                Text(l10nStr(R.string.safety_accept_btn), color = CarbonObsidian)
            }
        }
    )
}

@Composable
fun ProUpgradeDialog(
    isEnglish: Boolean,
    isProPreviewEnabled: Boolean,
    billingUiState: BillingUiState,
    onToggleProPreview: (Boolean) -> Unit,
    onPurchaseSku: (Activity, String) -> Unit,
    onDismiss: () -> Unit
) {
    val activity = LocalContext.current as? Activity
    val context = LocalContext.current
    val isGithub = BuildConfig.IS_GITHUB_BUILD

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pro_upgrade_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = CockpitSurface,
            border = BorderStroke(1.dp, MotorsportAmber.copy(alpha = 0.65f))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.WorkspacePremium,
                        contentDescription = null,
                        tint = MotorsportAmber,
                        modifier = Modifier.size(26.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isGithub) {
                                l10nStr(R.string.pro_github_card_title)
                            } else {
                                l10nStr(R.string.pro_dialog_title)
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when {
                                isGithub -> l10nStr(R.string.pro_github_badge)
                                billingUiState.hasProEntitlement -> l10nStr(R.string.pro_entitled_badge)
                                else -> l10nStr(R.string.pro_play_standard_badge)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isGithub || billingUiState.hasProEntitlement) NominalEmerald else TelemetryCyan
                        )
                    }
                }

                if (!isGithub && billingUiState.isPlayStoreAvailable && billingUiState.isReady) {
                    Text(
                        text = l10nStr(R.string.pro_play_ready_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = TelemetryCyan
                    )
                }

                PlayBillingBridge.proBenefits(isEnglish).forEach { benefit ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = TelemetryCyan,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = benefit,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (!isGithub) {
                    PlayBillingBridge.offerings.forEach { sku ->
                        val displayPrice = billingUiState.livePricesBySku[sku.productId]
                            ?: if (isEnglish) sku.priceDisplayEn else sku.priceDisplayRu
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = CockpitSurfaceElevated,
                            border = BorderStroke(1.dp, CockpitBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isEnglish) sku.titleEn else sku.titleRu,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "$displayPrice • SKU: ${sku.productId}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MotorsportAmber
                                    )
                                }
                                if (activity != null && !billingUiState.hasProEntitlement) {
                                    OutlinedButton(
                                        onClick = { onPurchaseSku(activity, sku.productId) },
                                        border = BorderStroke(1.dp, MotorsportAmber.copy(alpha = 0.7f)),
                                        modifier = Modifier.testTag("buy_sku_${sku.productId}")
                                    ) {
                                        Text(l10nStr(R.string.pro_buy_btn), color = MotorsportAmber)
                                    }
                                }
                            }
                        }
                    }

                    billingUiState.statusMessage?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelMedium,
                            color = MotorsportAmber
                        )
                    }

                    if (BuildConfig.DEBUG) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = l10nStr(R.string.pro_preview_toggle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            )
                            Switch(
                                checked = isProPreviewEnabled,
                                onCheckedChange = onToggleProPreview,
                                colors = SwitchDefaults.colors(checkedTrackColor = MotorsportAmber)
                            )
                        }
                    }
                } else if (BuildConfig.GITHUB_RELEASES_URL.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.GITHUB_RELEASES_URL))
                                )
                            }
                        },
                        border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_releases_button")
                    ) {
                        Text(l10nStr(R.string.pro_github_releases_btn), color = TelemetryCyan)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(l10nStr(R.string.action_close), color = TelemetryCyan)
                    }
                }
            }
        }
    }
}
