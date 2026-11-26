package com.graviton94.notificationcleaner.platform

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.graviton94.notificationcleaner.BuildConfig
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.KakaoSdk
import com.kakao.sdk.user.UserApiClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Sign-in and cloud save (21 platform §4). The game is fully playable as a guest; signing in only adds a
 * backup of the save in Firestore `users/{uid}` and moves progress between phones.
 *
 * - Google: Credential Manager → ID token → Firebase Auth.
 * - Kakao: Kakao SDK access token → Cloud Function `login` checks it with Kakao and returns a Firebase custom token
 *   (uid `kakao:<id>`; functions/main.py).
 * - Delete account: Cloud Function `deleteAccount` removes `users/{uid}` and the Firebase user (Play policy: in-app deletion).
 *
 * Everything is off (enabled = false) in builds without google-services.json.
 */
class Account(private val context: Context) {
    val enabled: Boolean = BuildConfig.FIREBASE && FirebaseApp.getApps(context).isNotEmpty()
    val kakaoEnabled: Boolean = enabled && BuildConfig.KAKAO_KEY.isNotBlank()
    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()
    private val functions get() = FirebaseFunctions.getInstance(REGION)

    init { if (kakaoEnabled) runCatching { KakaoSdk.init(context, BuildConfig.KAKAO_KEY) } }

    /** {enabled, kakao, signedIn, provider, name} for the page. */
    fun status(): JSONObject {
        val u = if (enabled) auth.currentUser else null
        val provider = when {
            u == null -> null
            u.uid.startsWith("kakao:") -> "kakao"
            u.providerData.any { it.providerId == "google.com" } -> "google"
            else -> "other"
        }
        return JSONObject().put("enabled", enabled).put("kakao", kakaoEnabled).put("google", enabled && webClientId() != null)
            .put("signedIn", u != null).put("provider", provider ?: JSONObject.NULL)
            .put("name", u?.displayName ?: u?.email ?: JSONObject.NULL)
    }

    val uid: String? get() = if (enabled) auth.currentUser?.uid else null

    private fun webClientId(): String? {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id == 0) null else context.getString(id).takeIf { it.isNotBlank() }
    }

    suspend fun signInGoogle(activity: Activity) {
        val clientId = webClientId() ?: throw IllegalStateException("no web client id")
        val request = GetCredentialRequest.Builder().addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build()).build()
        val res = CredentialManager.create(activity).getCredential(activity, request)
        val c = res.credential
        if (c !is CustomCredential || c.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) throw IllegalStateException("unexpected credential")
        val idToken = GoogleIdTokenCredential.createFrom(c.data).idToken
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
    }

    suspend fun signInKakao(activity: Activity) {
        val token = kakaoToken(activity)
        val res = functions.getHttpsCallable("login").call(mapOf("provider" to "kakao", "token" to token.accessToken)).await()
        val custom = (res.getData() as? Map<*, *>)?.get("token") as? String ?: throw IllegalStateException("no custom token")
        auth.signInWithCustomToken(custom).await()
    }

    private suspend fun kakaoToken(activity: Activity): OAuthToken = suspendCancellableCoroutine { cont ->
        val api = UserApiClient.instance
        val done: (OAuthToken?, Throwable?) -> Unit = { t, e -> if (t != null) cont.resume(t) else cont.resumeWithException(e ?: IllegalStateException("kakao login failed")) }
        if (api.isKakaoTalkLoginAvailable(activity)) {
            api.loginWithKakaoTalk(activity) { t, e ->
                // no account in KakaoTalk or the user backed out of the talk app: fall back to the web login once
                if (t == null && e != null && !(e is com.kakao.sdk.common.model.ClientError && e.reason == com.kakao.sdk.common.model.ClientErrorCause.Cancelled)) api.loginWithKakaoAccount(activity, callback = done)
                else done(t, e)
            }
        } else api.loginWithKakaoAccount(activity, callback = done)
    }

    fun signOut() {
        if (!enabled) return
        val wasKakao = auth.currentUser?.uid?.startsWith("kakao:") == true
        auth.signOut()
        if (wasKakao && kakaoEnabled) UserApiClient.instance.logout { }
    }

    /** Server side first (save + Firebase user), then the local sign-out. The game on this phone stays as a guest. */
    suspend fun deleteAccount() {
        val wasKakao = auth.currentUser?.uid?.startsWith("kakao:") == true
        functions.getHttpsCallable("deleteAccount").call().await()
        auth.signOut()
        if (wasKakao && kakaoEnabled) UserApiClient.instance.unlink { }
    }

    // ---- cloud save: users/{uid} = { save, level, savedAt, app } (firestore.rules: owner only)

    data class Cloud(val save: String, val level: Int, val savedAt: Long)

    suspend fun download(): Cloud? {
        val u = uid ?: return null
        val d = db.collection("users").document(u).get().await()
        val save = d.getString("save") ?: return null
        return Cloud(save, (d.getLong("level") ?: 0L).toInt(), d.getTimestamp("savedAt")?.toDate()?.time ?: 0L)
    }

    suspend fun upload(save: String, level: Int) {
        val u = uid ?: return
        runCatching {
            db.collection("users").document(u).set(mapOf("save" to save, "level" to level, "savedAt" to FieldValue.serverTimestamp(), "app" to BuildConfig.VERSION_NAME), SetOptions.merge()).await()
        }.onFailure { Log.w(TAG, "cloud save failed: ${it.javaClass.simpleName}") }
    }

    companion object {
        const val REGION = "us-central1"
        private const val TAG = "Account"
    }
}
