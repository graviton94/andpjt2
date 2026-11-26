package com.graviton94.notificationcleaner.platform

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import org.json.JSONObject

/**
 * Google Play Billing (17 monetization §8, 21 platform §5). Play only says "paid"; what the purchase grants is decided by
 * the core ([grant] → WebSession.buy*), once per claim id: purchase token for one-time products, order id for each
 * subscription period (renewals get a new order id). Grant first, then consume/acknowledge, so a crash in between is
 * healed on the next start by [restore] (the core ignores a claim it has already paid).
 *
 * Product ids are the Play Console ids (tools/play/products.json); the core ids they map to are in [PRODUCTS].
 */
class Billing(private val context: Context, private val grant: (Product, String) -> Boolean) : PurchasesUpdatedListener {
    /** kind: "data" (pack id) · "starter" · "rookie" · "pass" (month/Turbo/Ultra). */
    data class Product(val playId: String, val type: String, val kind: String, val coreId: String)

    private val client = BillingClient.newBuilder(context).setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection().build()
    private val details = HashMap<String, ProductDetails>()
    private var pending: ((Boolean, String) -> Unit)? = null
    var onReady: (() -> Unit)? = null
    /** Purchases that finish later (pending payment methods, restores): page gets a fresh state. */
    var onLateGrant: ((Product) -> Unit)? = null

    val ready get() = client.isReady && details.isNotEmpty()

    fun start() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(r: BillingResult) {
                if (r.responseCode != BillingClient.BillingResponseCode.OK) { Log.w(TAG, "billing setup ${r.responseCode}"); return }
                query(ProductType.INAPP); query(ProductType.SUBS); restore()
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    private fun query(type: String) {
        val list = PRODUCTS.filter { it.type == type }.map { QueryProductDetailsParams.Product.newBuilder().setProductId(it.playId).setProductType(type).build() }
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(list).build()) { r, res ->
            if (r.responseCode == BillingClient.BillingResponseCode.OK) synchronized(details) { res.productDetailsList.forEach { details[it.productId] = it } }
            if (details.isNotEmpty()) onReady?.invoke()
        }
    }

    /** Store prices for the page: Play id → formatted local price (₩5,500, $4.99 …). */
    fun prices(): JSONObject = JSONObject().also { o ->
        synchronized(details) {
            details.values.forEach { d ->
                val p = d.oneTimePurchaseOfferDetails?.formattedPrice
                    ?: d.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
                if (p != null) o.put(d.productId, p)
            }
        }
    }

    /** Page tap → Play purchase sheet. done(ok, reason) comes back once Play answers. */
    fun buy(activity: Activity, playId: String, done: (Boolean, String) -> Unit) {
        val d = synchronized(details) { details[playId] } ?: return done(false, "unavailable")
        if (pending != null) return done(false, "busy")
        val pp = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d)
        d.subscriptionOfferDetails?.firstOrNull()?.let { pp.setOfferToken(it.offerToken) }
        val flow = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(pp.build()))
        // a higher pass replaces the current one; Play charges only the prorated difference (17 §8)
        if (d.productType == ProductType.SUBS) currentSub?.let { old ->
            if (old.products.firstOrNull() != playId) flow.setSubscriptionUpdateParams(
                BillingFlowParams.SubscriptionUpdateParams.newBuilder().setOldPurchaseToken(old.purchaseToken)
                    .setSubscriptionReplacementMode(BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.CHARGE_PRORATED_PRICE).build())
        }
        pending = done
        val r = client.launchBillingFlow(activity, flow.build())
        if (r.responseCode != BillingClient.BillingResponseCode.OK) { pending = null; done(false, "launch_${r.responseCode}") }
    }

    private var currentSub: Purchase? = null

    override fun onPurchasesUpdated(r: BillingResult, purchases: MutableList<Purchase>?) {
        val cb = pending; pending = null
        when (r.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                var any = false; var waiting = false
                purchases?.forEach { p -> if (p.purchaseState == Purchase.PurchaseState.PURCHASED) any = handle(p) || any else if (p.purchaseState == Purchase.PurchaseState.PENDING) waiting = true }
                cb?.invoke(any, if (any) "ok" else if (waiting) "pending" else "none")
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> cb?.invoke(false, "cancel")
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> { restore(); cb?.invoke(false, "owned") }
            else -> cb?.invoke(false, "error_${r.responseCode}")
        }
    }

    /** Grant through the core, then consume (one-time) or acknowledge (subscription). */
    private fun handle(p: Purchase): Boolean {
        val prod = PRODUCTS.firstOrNull { it.playId == p.products.firstOrNull() } ?: return false
        val claim = if (prod.type == ProductType.SUBS) "play:" + (p.orderId ?: p.purchaseToken) else "play:" + p.purchaseToken
        val ok = grant(prod, claim)
        if (prod.type == ProductType.INAPP) client.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { cr, _ -> if (cr.responseCode != 0) Log.w(TAG, "consume ${cr.responseCode}") }
        else {
            currentSub = p
            if (!p.isAcknowledged) client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { ar -> if (ar.responseCode != 0) Log.w(TAG, "ack ${ar.responseCode}") }
        }
        return ok
    }

    /** Unfinished one-time purchases and the live subscription (each renewal pays its new period once). */
    fun restore() {
        if (!client.isReady) return
        for (type in listOf(ProductType.INAPP, ProductType.SUBS)) {
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(type).build()) { r, list ->
                if (r.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
                list.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { p ->
                    val prod = PRODUCTS.firstOrNull { it.playId == p.products.firstOrNull() }
                    if (handle(p) && prod != null) onLateGrant?.invoke(prod)
                }
            }
        }
    }

    companion object {
        private const val TAG = "Billing"
        val PRODUCTS = listOf(
            Product("data_100", ProductType.INAPP, "data", "data_100"),
            Product("data_540", ProductType.INAPP, "data", "data_540"),
            Product("data_1200", ProductType.INAPP, "data", "data_1200"),
            Product("data_3700", ProductType.INAPP, "data", "data_3700"),
            Product("data_8600", ProductType.INAPP, "data", "data_8600"),
            Product("data_16800", ProductType.INAPP, "data", "data_16800"),
            Product("starter_pack", ProductType.INAPP, "starter", "starter"),
            Product("rookie_pass", ProductType.INAPP, "rookie", "rookie"),
            Product("pass_monthly", ProductType.SUBS, "pass", "month"),
            Product("pass_turbo", ProductType.SUBS, "pass", "Turbo"),
            Product("pass_ultra", ProductType.SUBS, "pass", "Ultra"),
        )
    }
}
