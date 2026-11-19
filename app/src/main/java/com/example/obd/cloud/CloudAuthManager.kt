package com.example.obd.cloud

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.obd.model.CloudUserSummary
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result ->
        if (cont.isActive) cont.resume(result)
    }
    addOnFailureListener { exc ->
        if (cont.isActive) cont.resumeWithException(exc)
    }
    addOnCanceledListener {
        if (cont.isActive) cont.cancel()
    }
}

class CloudAuthManager(context: Context) {

    private val appContext = context.applicationContext
    private val credentialManager = CredentialManager.create(appContext)
    private val webClientId: String by lazy {
        val resId = appContext.resources.getIdentifier(
            "default_web_client_id",
            "string",
            appContext.packageName
        )
        if (resId != 0) appContext.getString(resId) else ""
    }

    fun currentUserSummary(): CloudUserSummary? {
        if (FirebaseApp.getApps(appContext).isEmpty()) return null
        return runCatching { Firebase.auth.currentUser?.toSummary() }.getOrNull()
    }

    suspend fun attemptSilentSignIn(activityContext: Context): CloudUserSummary? {
        if (FirebaseApp.getApps(appContext).isEmpty()) return null
        runCatching { Firebase.auth.currentUser }.getOrNull()?.let { return it.toSummary() }
        if (webClientId.isBlank()) return null

        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val result = credentialManager.getCredential(
                request = request,
                context = activityContext
            )
            handleCredentialResponse(result.credential).getOrNull()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun signInWithGoogle(
        activityContext: Context,
        isEnglish: Boolean
    ): Result<CloudUserSummary> {
        if (FirebaseApp.getApps(appContext).isEmpty() || webClientId.isBlank()) {
            return Result.failure(
                IllegalStateException(
                    if (isEnglish) {
                        "Firebase / Google Web Client ID is not configured in this build"
                    } else {
                        "Firebase / Google Web Client ID не настроен в этой сборке"
                    }
                )
            )
        }
        return try {
            val signInOption = GetSignInWithGoogleOption.Builder(webClientId)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()
            val result = credentialManager.getCredential(
                request = request,
                context = activityContext
            )
            handleCredentialResponse(result.credential)
        } catch (_: GetCredentialCancellationException) {
            Result.failure(
                IllegalStateException(
                    if (isEnglish) "Sign-in cancelled" else "Вход отменён пользователем"
                )
            )
        } catch (_: NoCredentialException) {
            Result.failure(
                IllegalStateException(
                    if (isEnglish) "No Google account available on this device"
                    else "На устройстве не найдены аккаунты Google"
                )
            )
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Credential Manager sign-in failed", e)
            Result.failure(
                IllegalStateException(
                    e.localizedMessage ?: if (isEnglish) "Google sign-in failed" else "Ошибка входа через Google"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected Google sign-in error", e)
            Result.failure(e)
        }
    }

    private suspend fun handleCredentialResponse(
        credential: androidx.credentials.Credential
    ): Result<CloudUserSummary> {
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return Result.failure(IllegalStateException("Unexpected credential type"))
        }
        return try {
            val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
            val authResult = Firebase.auth.signInWithCredential(firebaseCred).awaitResult()
            val user = authResult.user
                ?: return Result.failure(IllegalStateException("Firebase user missing after sign-in"))
            Result.success(user.toSummary())
        } catch (e: Exception) {
            Log.e(TAG, "Firebase credential exchange failed", e)
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        withContext(NonCancellable) {
            runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
            runCatching { Firebase.auth.signOut() }
        }
    }

    private fun FirebaseUser.toSummary(): CloudUserSummary {
        return CloudUserSummary(
            uid = uid,
            displayName = displayName?.takeIf { it.isNotBlank() },
            email = email?.takeIf { it.isNotBlank() }
        )
    }

    companion object {
        private const val TAG = "CloudAuthManager"
    }
}
