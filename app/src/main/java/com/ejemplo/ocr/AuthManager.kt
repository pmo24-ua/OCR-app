package com.ejemplo.ocr

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import org.json.JSONObject

/**  Información mínima del usuario sacada del token  */
data class UserInfo(
    val email: String,
    val isFree: Boolean,           // flag FREE / PREMIUM
    val displayName: String? = null
)

object AuthManager {

    private const val KEY_TOKEN = "token"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("auth", Context.MODE_PRIVATE)

    /* ---------- login / logout ---------- */

    fun saveToken(ctx: Context, token: String) {
        prefs(ctx).edit { putString(KEY_TOKEN, token) }
    }

    fun token(ctx: Context): String? =
        prefs(ctx).getString(KEY_TOKEN, null)

    fun isLoggedIn(ctx: Context): Boolean = token(ctx) != null

    fun logout(ctx: Context) = prefs(ctx).edit { clear() }

    /* ---------- NUEVO:  leer info del usuario ---------- */

    /**
     * Devuelve los datos básicos embebidos en el JWT **sin** verificar firma.
     * Retorna null si no hay token o le falta el e-mail.
     */
    fun getCurrentUser(ctx: Context): UserInfo? {
        val jwt = token(ctx) ?: return null
        val payloadB64 = jwt.split(".").getOrNull(1) ?: return null

        return try {
            val json = JSONObject(
                String(Base64.decode(payloadB64, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
            )
            val email     = json.optString("sub", null) ?: return null
            val isPremium = json.optBoolean("is_premium", false)
            val name      = json.optString("name", null)

            // isFree = no es premium
            UserInfo(
                email     = email,
                isFree    = !isPremium,
                displayName = name
            )
        } catch (e: Exception) {
            null
        }
    }

}
