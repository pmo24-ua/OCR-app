package com.ejemplo.ocr

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import okhttp3.FormBody


class LoginActivity : AppCompatActivity() {
    private val ocrUrl = BuildConfig.BASE_URL

    private lateinit var etMail: TextInputEditText
    private lateinit var etPass: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var prog: ProgressBar
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Si ya está logueado, va a Main y cierra
        if (AuthManager.isLoggedIn(this)) {
            startHome(); return
        }
        setContentView(R.layout.activity_login)
        findViewById<TextView>(R.id.txtGoRegister).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        etMail = findViewById(R.id.etMail)
        etPass = findViewById(R.id.etPass)
        btnLogin = findViewById(R.id.btnLogin)
        prog = findViewById(R.id.progressLogin)

        btnLogin.setOnClickListener { doLogin() }
    }


    private fun doLogin() {
        val email = etMail.text?.toString()?.trim() ?: ""
        val pass = etPass.text?.toString() ?: ""
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etMail.error = "Email inválido"; return
        }
        if (pass.length < 6) {
            etPass.error = "Mínimo 6 caracteres"; return
        }
        prog.visibility = View.VISIBLE
        btnLogin.isEnabled = false

        val json = JSONObject()
            .put("email", email)
            .put("password", pass)
            .toString()

        // Crea el RequestBody de tipo JSON
        val body = json.toRequestBody("application/json".toMediaType())

        val req = Request.Builder()
            .url("$ocrUrl/login")
            .post(body)
            .build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = ui {
                showErr("Red: ${e.localizedMessage}")
            }
            override fun onResponse(call: Call, resp: Response) {
                if (!resp.isSuccessful) return ui { showErr("Error Login: HTTP ${resp.code}") }
                val body = resp.body?.string() ?: return ui { showErr("Vacío") }
                try {
                    val tok = JSONObject(body).getString("token")
                    AuthManager.saveToken(this@LoginActivity, tok)
                    ui { startHome() }
                } catch (e: Exception) {
                    ui { showErr("JSON inválido") }
                }
            }
            private fun ui(b: () -> Unit) = runOnUiThread {
                prog.visibility = View.GONE
                btnLogin.isEnabled = true
                b()
            }
        })
    }

    private fun showErr(msg: String) =
        Snackbar.make(btnLogin, msg, Snackbar.LENGTH_LONG).show()

    private fun startHome() {
        startActivity(
            Intent(this, HomeActivity::class.java).apply {
                // para que el usuario no pueda volver al login
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

}
