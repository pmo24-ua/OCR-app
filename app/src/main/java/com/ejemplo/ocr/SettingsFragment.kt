package com.ejemplo.ocr

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.stripe.android.PaymentConfiguration
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult
import com.stripe.android.paymentsheet.PaymentSheetResultCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private lateinit var paymentSheet: PaymentSheet
    private var paymentIntentClientSecret: String? = null

    // Temporalmente hardcodeada; luego puedes pasarla vía BuildConfig
    private val STRIPE_PUBLISHABLE_KEY_HARDCODED =
        "pk_test_51ReXyIH180xSNuLMytRVzNn0GWVIXbvXGHt8q2mhGqlHd9Yj3yodW0cTPtyB35AJe4JmwuT63wFmgdTobJJlPdcx00ANwqNFgH"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inicializa SDK de Stripe con la clave "publishable"
        PaymentConfiguration.init(
            requireContext(),
            STRIPE_PUBLISHABLE_KEY_HARDCODED
        )
        // Configura el PaymentSheet y su callback
        paymentSheet = PaymentSheet(
            this,
            PaymentSheetResultCallback { result ->
                onPaymentSheetResult(result)
            }
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ← Flecha atrás
        view.findViewById<MaterialToolbar>(R.id.toolbarSet)
            .setNavigationOnClickListener {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }

        // ─── Datos de usuario (prefs) ───
        val user = AuthManager.getCurrentUser(requireContext())
        val tvName  = view.findViewById<TextView>(R.id.tvName)
        val tvEmail = view.findViewById<TextView>(R.id.tvEmail)
        val chipAcc = view.findViewById<Chip>(R.id.chipAccount)

        if (user != null) {
            tvName.text  = user.displayName ?: user.email
            tvEmail.text = user.email
            chipAcc.text = if (user.isFree)
                getString(R.string.tipo_cuenta_free)
            else
                getString(R.string.cuenta_premium)
        } else {
            tvName.text       = getString(R.string.no_logeado)
            tvEmail.text      = getString(R.string.inicia_para_ver)
            chipAcc.visibility = View.GONE
        }

        // Botón “Cerrar sesión”
        view.findViewById<MaterialButton>(R.id.btnLogout)
            .setOnClickListener {
                if (!AuthManager.isLoggedIn(requireContext())) {
                    toast(getString(R.string.no_hay_sesion))
                    return@setOnClickListener
                }
                AuthManager.logout(requireContext())
                startActivity(
                    Intent(requireContext(), HomeActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
            }

        // Botón “Borrar historial”
        view.findViewById<MaterialButton>(R.id.btnClearHist)
            .setOnClickListener {
                if (!AuthManager.isLoggedIn(requireContext())) {
                    toast(getString(R.string.no_hay_sesion))
                    return@setOnClickListener
                }
                AlertDialog.Builder(requireContext())
                    .setMessage(R.string.confirmar_borrado_total)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        lifecycleScope.launch {
                            try {
                                clearHistory(requireContext())
                                toast(getString(R.string.borrado_ok))
                            } catch (e: Exception) {
                                toast(getString(R.string.error_fmt, e.message))
                            }
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }

        // Botón “Seleccionar motor OCR”
        val btnSelect = view.findViewById<MaterialButton>(R.id.btnSelectEngine)
        btnSelect.setOnClickListener {
            showEngineSelectionDialog()
        }
        btnSelect.isEnabled = AuthManager.isLoggedIn(requireContext())
    }

    // ─── Diálogo para escoger motor OCR ───
    private fun showEngineSelectionDialog() {
        val options = arrayOf(
            getString(R.string.option_easyocr),
            getString(R.string.option_googlevision)
        )
        val prefs = requireContext()
            .getSharedPreferences("ocr_prefs", Context.MODE_PRIVATE)
        val current = prefs.getInt("engine", 0)
        var selected = current

        AlertDialog.Builder(requireContext())
            .setTitle("Selecciona motor OCR")
            .setSingleChoiceItems(options, current) { _, which ->
                selected = which
            }
            .setPositiveButton("Aceptar") { _, _ ->
                if (selected == 1 && AuthManager.getCurrentUser(requireContext())?.isFree == true) {
                    // Quiere Google Vision pero aún es free → pago
                    startPayment()
                } else {
                    // EasyOCR o ya premium
                    saveEnginePreference(selected)
                    toast("Motor seleccionado: ${options[selected]}")
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }


    // ─── Iniciar flujo de pago  ───
    private fun startPayment() {
        lifecycleScope.launch {
            try {
                // 1) JWT
                val token = AuthManager.token(requireContext())
                    ?: throw IOException("Sin token")

                // 2) Body vacío + JSON
                val emptyJson = "".toRequestBody("application/json".toMediaType())

                // 3) Llamada al endpoint que acabamos de crear
                val request = Request.Builder()
                    .url("${BuildConfig.BASE_URL}/create-payment-intent")
                    .post(emptyJson)
                    .addHeader("Authorization", "Bearer $token")
                    .build()

                // 4) Ejecutar en IO
                val response = withContext(Dispatchers.IO) {
                    OkHttpClient().newCall(request).execute()
                }
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}")
                }

                // 5) Extraer client_secret
                val clientSecret = JSONObject(response.body!!.string())
                    .getString("client_secret")
                paymentIntentClientSecret = clientSecret

                // 6) Mostrar PaymentSheet con PaymentIntent
                paymentSheet.presentWithPaymentIntent(
                    clientSecret,
                    PaymentSheet.Configuration(
                        merchantDisplayName = "Mi OCR App",
                        allowsDelayedPaymentMethods = false
                    )
                )

            } catch (e: Exception) {
                e.printStackTrace()
                toast("Error al iniciar pago: ${e.message}")
            }
        }
    }


    // ─── Resultado del PaymentSheet ───
    private fun onPaymentSheetResult(paymentResult: PaymentSheetResult) {
        when (paymentResult) {
            is PaymentSheetResult.Completed -> {
                toast("Pago completado")
                confirmUpgradeOnServer()
            }
            is PaymentSheetResult.Canceled  ->
                toast("Pago cancelado")
            is PaymentSheetResult.Failed    ->
                toast("Error en pago: ${paymentResult.error.localizedMessage}")
        }
    }

    // ─── Avisar al backend para marcar premium y renovar token ───
    private fun confirmUpgradeOnServer() {
        lifecycleScope.launch {
            try {
                val secret = paymentIntentClientSecret
                    ?: throw IllegalStateException("No hay clientSecret")
                val piId = secret.substringBefore("_secret")

                val body = JSONObject()
                    .put("payment_intent_id", piId)
                    .toString()
                    .toRequestBody("application/json".toMediaType())

                val token = AuthManager.token(requireContext())
                    ?: throw IOException("Sin token")

                val req = Request.Builder()
                    .url("${BuildConfig.BASE_URL}/upgrade")
                    .post(body)
                    .addHeader("Authorization", "Bearer $token")
                    .build()

                // <<< Aquí el cambio principal
                val resp = withContext(Dispatchers.IO) {
                    OkHttpClient().newCall(req).execute()
                }

                if (!resp.isSuccessful) {
                    throw IOException("HTTP ${resp.code}")
                }

                val newToken = JSONObject(resp.body!!.string())
                    .getString("token")

                AuthManager.saveToken(requireContext(), newToken)
                toast("¡Ahora eres premium!")
                view?.findViewById<Chip>(R.id.chipAccount)
                    ?.text = getString(R.string.cuenta_premium)
                saveEnginePreference(1)

            } catch (e: Exception) {
                e.printStackTrace()
                toast("Error al confirmar upgrade: ${e.message}")
            }
        }
    }

    // ─── Guardar pref. local de motor OCR ───
    private fun saveEnginePreference(selected: Int) {
        requireContext()
            .getSharedPreferences("ocr_prefs", Context.MODE_PRIVATE)
            .edit()
            .putInt("engine", selected)
            .apply()
    }

    // ─── Helpers ───
    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    private suspend fun clearHistory(ctx: Context) = withContext(Dispatchers.IO) {
        val tok = AuthManager.token(ctx) ?: throw IOException("Sin token")
        val req = Request.Builder()
            .url("${BuildConfig.BASE_URL}/history")
            .delete()
            .addHeader("Authorization", "Bearer $tok")
            .build()
        OkHttpClient().newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
        }
    }
}
