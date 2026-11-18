package com.example.obd.data

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.example.obd.model.BillingUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProSkuOffering(
    val productId: String,
    val titleRu: String,
    val titleEn: String,
    val priceDisplayRu: String,
    val priceDisplayEn: String,
    val badgeRu: String? = null,
    val badgeEn: String? = null
)

object PlayBillingBridge {
    const val SKU_PRO_MONTHLY = "obd2_pro_monthly"
    const val SKU_PRO_LIFETIME = "obd2_pro_lifetime"

    val offerings: List<ProSkuOffering> = listOf(
        ProSkuOffering(
            productId = SKU_PRO_MONTHLY,
            titleRu = "OBD2 Pro — Месяц",
            titleEn = "OBD2 Pro — Monthly",
            priceDisplayRu = "249 ₽ / мес",
            priceDisplayEn = "$2.99 / mo"
        ),
        ProSkuOffering(
            productId = SKU_PRO_LIFETIME,
            titleRu = "OBD2 Pro — Навсегда",
            titleEn = "OBD2 Pro — Lifetime",
            priceDisplayRu = "1 490 ₽ единожды",
            priceDisplayEn = "$14.99 one-time",
            badgeRu = "Выгодно",
            badgeEn = "Best Value"
        )
    )

    fun proBenefits(isEnglish: Boolean): List<String> = if (isEnglish) {
        listOfNotNull(
            "AI Diagnostics: DTC Fault Breakdown, Voice Mechanic, Warning Light Photo Scan & VIN Auto-Fill",
            "Pre-Purchase Fraud Check & A4 PDF Report, OEM Brand PIDs, Custom Torque PIDs, CSV Datalogger & ELM327 Terminal",
            "Virtual Dyno (HP / N·m), 0–100 km/h Timer, GPS Speed Calibration, Maintenance Tracker & HUD Mirror",
            "Firebase Cloud Sync across devices (vehicles, service logs, Torque PIDs & trips)",
            if (com.example.BuildConfig.ALLOW_AA_NAVIGATION_SURFACE) {
                "Android Auto Fullscreen Analog Gauges & 4 Motorsport Cockpit Themes"
            } else {
                null
            }
        )
    } else {
        listOfNotNull(
            "AI-диагностика: умный разбор ошибок, голосовой AI-механик, распознавание значков/чеков по фото и подбор по VIN",
            "Проверка авто перед покупкой + PDF-отчёт А4, OEM PID по маркам, свои Torque PID, даталоггер CSV и терминал ELM327",
            "Виртуальное дино (л.с./Н·м), таймер разгона 0–100, сверка по GPS, расширенный журнал ТО и проекция HUD",
            "Облачная синхронизация Firebase между устройствами (гараж, ТО, Torque PID и поездки)",
            if (com.example.BuildConfig.ALLOW_AA_NAVIGATION_SURFACE) {
                "Полноэкранные стрелочные приборы Android Auto и 4 темы кокпита"
            } else {
                null
            }
        )
    }
}

class PlayBillingManager(
    context: Context,
    private val scope: CoroutineScope,
    private val onProEntitlementChanged: (Boolean) -> Unit
) {
    private val appContext = context.applicationContext
    private val isGithubBuild = com.example.BuildConfig.IS_GITHUB_BUILD
    private val _state = MutableStateFlow(
        if (isGithubBuild) BillingUiState(hasProEntitlement = true) else BillingUiState()
    )
    val state: StateFlow<BillingUiState> = _state.asStateFlow()

    private val productDetailsMap = mutableMapOf<String, ProductDetails>()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (!purchases.isNullOrEmpty()) {
                    handlePurchasesList(purchases)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _state.update { it.copy(statusMessage = "Purchase cancelled") }
            }
            else -> {
                _state.update {
                    it.copy(statusMessage = billingResult.debugMessage.takeIf { m -> m.isNotBlank() })
                }
            }
        }
    }

    private val billingClient: BillingClient? = if (isGithubBuild) {
        null
    } else {
        BillingClient.newBuilder(appContext)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .enablePrepaidPlans()
                    .build()
            )
            .build()
    }

    fun startConnection() {
        val client = billingClient ?: return
        if (client.isReady) {
            queryCatalogAndPurchases()
            return
        }
        runCatching {
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        _state.update {
                            it.copy(isPlayStoreAvailable = true, isReady = true, statusMessage = null)
                        }
                        queryCatalogAndPurchases()
                    } else {
                        Log.i(TAG, "Billing unavailable (${billingResult.responseCode}): ${billingResult.debugMessage}")
                        _state.update {
                            it.copy(
                                isPlayStoreAvailable = false,
                                isReady = false,
                                statusMessage = null
                            )
                        }
                    }
                }

                override fun onBillingServiceDisconnected() {
                    _state.update { it.copy(isReady = false) }
                }
            })
        }.onFailure { err ->
            Log.w(TAG, "Failed to start BillingClient", err)
        }
    }

    fun refreshPurchases() {
        val client = billingClient ?: return
        if (!client.isReady) {
            startConnection()
            return
        }
        queryActivePurchases()
    }

    fun launchPurchaseFlow(activity: Activity, productId: String, isEnglish: Boolean) {
        val client = billingClient ?: return
        val details = productDetailsMap[productId]
        if (!client.isReady || details == null) {
            _state.update {
                it.copy(
                    statusMessage = if (isEnglish) {
                        "Google Play Billing is unavailable on this device or SKU is not published yet"
                    } else {
                        "Google Play Billing недоступен на устройстве или тариф ещё не опубликован в Play Console"
                    }
                )
            }
            return
        }
        val pdParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
        if (details.productType == BillingClient.ProductType.SUBS) {
            val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
            if (offerToken != null) {
                pdParamsBuilder.setOfferToken(offerToken)
            }
        }
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(pdParamsBuilder.build()))
            .build()
        client.launchBillingFlow(activity, flowParams)
    }

    private fun queryCatalogAndPurchases() {
        queryProductDetails(
            productIds = listOf(PlayBillingBridge.SKU_PRO_LIFETIME),
            productType = BillingClient.ProductType.INAPP
        )
        queryProductDetails(
            productIds = listOf(PlayBillingBridge.SKU_PRO_MONTHLY),
            productType = BillingClient.ProductType.SUBS
        )
        queryActivePurchases()
    }

    private fun queryProductDetails(productIds: List<String>, productType: String) {
        val products = productIds.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(productType)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()
        billingClient?.queryProductDetailsAsync(params) { billingResult, list ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val updatedPrices = _state.value.livePricesBySku.toMutableMap()
                list.forEach { pd ->
                    productDetailsMap[pd.productId] = pd
                    val formattedPrice = if (pd.productType == BillingClient.ProductType.SUBS) {
                        pd.subscriptionOfferDetails?.firstOrNull()
                            ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                    } else {
                        pd.oneTimePurchaseOfferDetails?.formattedPrice
                    }
                    if (!formattedPrice.isNullOrBlank()) {
                        updatedPrices[pd.productId] = formattedPrice
                    }
                }
                _state.update { it.copy(livePricesBySku = updatedPrices) }
            }
        }
    }

    private fun queryActivePurchases() {
        val client = billingClient ?: return
        val inAppParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(inAppParams) { inAppRes, inAppList ->
            val subsParams = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
            client.queryPurchasesAsync(subsParams) { subsRes, subsList ->
                val all = mutableListOf<Purchase>()
                if (inAppRes.responseCode == BillingClient.BillingResponseCode.OK) all.addAll(inAppList)
                if (subsRes.responseCode == BillingClient.BillingResponseCode.OK) all.addAll(subsList)
                handlePurchasesList(all)
            }
        }
    }

    private fun handlePurchasesList(purchases: List<Purchase>) {
        var entitled = false
        for (purchase in purchases) {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                if (PlayBillingBridge.SKU_PRO_LIFETIME in purchase.products ||
                    PlayBillingBridge.SKU_PRO_MONTHLY in purchase.products
                ) {
                    entitled = true
                }
                if (!purchase.isAcknowledged) {
                    val ackParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    billingClient?.acknowledgePurchase(ackParams) { ackRes ->
                        Log.i(TAG, "Acknowledge purchase result: ${ackRes.responseCode}")
                    }
                }
            }
        }
        _state.update { it.copy(hasProEntitlement = entitled) }
        if (entitled) {
            scope.launch { onProEntitlementChanged(true) }
        }
    }

    fun endConnection() {
        runCatching { billingClient?.endConnection() }
    }

    companion object {
        private const val TAG = "PlayBillingManager"
    }
}
