package com.ejemplo.ocr

import com.ejemplo.ocr.BuildConfig
import android.os.Bundle
import android.util.Patterns
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class RegisterActivity : AppCompatActivity() {

    private val BASE_URL = BuildConfig.BASE_URL// AJUSTA PUERTO

    private lateinit var etEmail: EditText
    private lateinit var etPass: EditText
    private lateinit var btnReg: Button
    private lateinit var prog: ProgressBar

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        etEmail = findViewById(R.id.etEmail)
        etPass  = findViewById(R.id.etPass)
        btnReg  = findViewById(R.id.btnRegister)
        prog    = findViewById(R.id.progressReg)

        btnReg.setOnClickListener { submit() }

        // Deshabilitar botón mientras campos vacíos
        etEmail.doOnTextChanged { _,_,_,_ -> checkFields() }
        etPass.doOnTextChanged  { _,_,_,_ -> checkFields() }
        checkFields()
    }

    private fun checkFields() {
        btnReg.isEnabled =
            Patterns.EMAIL_ADDRESS.matcher(etEmail.text).matches() &&
                    etPass.text.length >= 6
    }

    private fun submit() {
        val email = etEmail.text.toString().trim()
        val pass  = etPass.text.toString()

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Email inválido"; return
        }
        if (pass.length < 6) {
            etPass.error = "Debe tener al menos 6 caracteres"; return
        }

        val json = JSONObject().put("email", email).put("password", pass)
        val reqBody = json.toString()
            .toRequestBody("application/json".toMediaType())

        val req = Request.Builder()
            .url("$BASE_URL/register")
            .post(reqBody)
            .build()

        showProgress(true)
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = ui {
                toast("Fallo de red: ${e.localizedMessage}")
                showProgress(false)
            }
            override fun onResponse(call: Call, resp: Response) = ui {
                showProgress(false)
                when (resp.code) {
                    201 -> {
                        toast("¡Registro exitoso!\nInicia sesión.")
                        finish()          // volvemos a LoginActivity
                    }
                    409 -> toast("Ese email ya está registrado")
                    else -> toast("Error ${resp.code}: ${resp.message}")
                }
            }
        })
    }

    private fun ui(block: () -> Unit) = runOnUiThread(block)
    private fun toast(msg: String) =
        Toast.makeText(this@RegisterActivity, msg, Toast.LENGTH_LONG).show()
    private fun showProgress(show: Boolean) {
        prog.visibility = if (show) ProgressBar.VISIBLE else ProgressBar.GONE
        btnReg.isEnabled = !show
    }
}
