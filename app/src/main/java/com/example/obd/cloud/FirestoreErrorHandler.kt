package com.example.obd.cloud

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestoreException

object FirestoreErrorHandler {
    private const val TAG = "FirestoreSync"

    fun handleFirestoreError(e: Exception, isEnglish: Boolean): String {
        return when (e) {
            is FirebaseFirestoreException -> when (e.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                    Log.w(TAG, "Permission denied in Firestore", e)
                    if (isEnglish) "Permission denied. Sign in with Google again."
                    else "Нет доступа к облаку. Войдите в аккаунт Google заново."
                }
                FirebaseFirestoreException.Code.UNAUTHENTICATED -> {
                    Log.w(TAG, "Unauthenticated Firestore request", e)
                    if (isEnglish) "Google sign-in required for cloud sync."
                    else "Для облачной синхронизации требуется вход в Google."
                }
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> {
                    Log.w(TAG, "Firestore network unavailable", e)
                    if (isEnglish) "Cloud unavailable. Changes saved locally."
                    else "Облако временно недоступно. Данные сохранены локально."
                }
                else -> {
                    Log.e(TAG, "Firestore error ${e.code}", e)
                    e.localizedMessage ?: if (isEnglish) "Cloud sync error" else "Ошибка облачной синхронизации"
                }
            }
            else -> {
                Log.e(TAG, "Cloud sync error", e)
                e.localizedMessage ?: if (isEnglish) "Cloud sync error" else "Ошибка облачной синхронизации"
            }
        }
    }
}
