package com.ejemplo.ocr

import android.content.Intent
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.progressindicator.LinearProgressIndicator
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import androidx.core.graphics.scale
import com.google.android.material.appbar.MaterialToolbar

class MainActivity : AppCompatActivity() {


    private fun getOcrUrl(): String {
        val prefs = getSharedPreferences("ocr_prefs", Context.MODE_PRIVATE)
        return if (prefs.getInt("engine", 0) == 0) {
            "${BuildConfig.BASE_URL}/ocr"
        } else {
            "${BuildConfig.BASE_URL}/ocr_google"
        }
    }
    // --- UI ---
    private lateinit var imgPreview: ImageView
    private lateinit var txtResult: TextView
    private lateinit var prog: ProgressBar
    private lateinit var uploadProgress: LinearProgressIndicator
    private lateinit var btnOcr: Button

    // --- Estado ---
    private var currentBitmap: Bitmap? = null
    private var cameraTempUri: Uri? = null

    // --- OkHttp Client ---
    private val client = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(this))
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private lateinit var pickImageLauncher: ActivityResultLauncher<String>
    private lateinit var takePhotoLauncher: ActivityResultLauncher<Uri>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AuthManager.isLoggedIn(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_single_scan)
        findViewById<MaterialToolbar>(R.id.topAppBar).setNavigationOnClickListener {
            finish()           // cierra y vuelve a la pantalla anterior
        }

        findViewById<Button>(R.id.btnPick).setOnClickListener { pickImage() }
        findViewById<Button>(R.id.btnCamera).setOnClickListener { takePhoto() }
        btnOcr = findViewById(R.id.btnOcr)
        imgPreview = findViewById(R.id.imgPreview)
        txtResult = findViewById(R.id.txtResult)
        prog = findViewById(R.id.progress)
        uploadProgress = findViewById(R.id.uploadProgress)

        btnOcr.setOnClickListener {
            currentBitmap?.let { runOcrOnServer(it) }
                ?: toast("Primero selecciona imagen")
        }

        pickImageLauncher = registerForActivityResult(GetContent()) { uri: Uri? ->
            uri?.let(::loadBitmapFromUri)
        }
        takePhotoLauncher = registerForActivityResult(TakePicture()) { ok ->
            if (ok) cameraTempUri?.let(::loadBitmapFromUri)
        }

        requestRuntimePermissionsIfNeeded()

        when (intent.getStringExtra("mode")) {
            "camera"  -> takePhoto()   // abre cámara directamente
            "gallery" -> pickImage()   // abre selector de imágenes
        }
    }


    private fun pickImage() {
        // seleccionar imagen
        pickImageLauncher.launch("image/*")
    }

    private fun takePhoto() {
        if (!checkPerm(Manifest.permission.CAMERA)) {
            requestPerms(arrayOf(Manifest.permission.CAMERA))
            return
        }
        val imgFile = try {
            File.createTempFile("CamTmp_", ".jpg", cacheDir)
        } catch (e: IOException) {
            toast("Error creando archivo: ${e.localizedMessage}")
            return
        }

        val tempUriForLaunch = FileProvider.getUriForFile(
            this,
            "${packageName}.fp",
            imgFile
        )

        this.cameraTempUri = tempUriForLaunch
        takePhotoLauncher.launch(tempUriForLaunch)
    }

    private fun loadBitmapFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { ins ->
                val bmp = BitmapFactory.decodeStream(ins)
                currentBitmap = bmp
                imgPreview.setImageBitmap(bmp)
                // Limpiar texto previo
                txtResult.text = ""
            }
        } catch (e: Exception) {
            toast("Error cargando imagen: ${e.localizedMessage}")
        }
    }



    private fun runOcrOnServer(bitmap: Bitmap) {
        // Escalar si es necesario
        val scaled = scaleBitmapIfNeeded(bitmap, 1024)
        val imgBytes = bitmapToJpegByteArray(scaled, 80)

        val realBody = imgBytes.toRequestBody("image/jpeg".toMediaType())

        val countingBody = CountingRequestBody(realBody) { bytesWritten, totalBytes ->
            val pct = (bytesWritten * 100 / totalBytes).toInt()
            runOnUiThread {
                uploadProgress.apply {
                    isIndeterminate = false
                    progress = pct
                }
            }
        }

        val reqBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                name = "file",
                filename = "image.jpg",
                body = countingBody
            )
            .build()
        val request = Request.Builder()
            .url(getOcrUrl())
            .post(reqBody)
            .build()

        // mostrar progreso
        prog.visibility = ProgressBar.VISIBLE
        btnOcr.isEnabled = false
        val t0 = System.currentTimeMillis()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) =
                ui { showError("Fallo de red: ${e.localizedMessage}") }

            override fun onResponse(call: Call, response: Response) {
                val t1 = System.currentTimeMillis()
                if (!response.isSuccessful) {
                    ui { showError("Error HTTP: ${response.code}") }
                    return
                }
                val bodyStr = response.body?.string() ?: ""
                try {
                    val json = JSONObject(bodyStr)
                    val text = json.optString("text", "(sin texto)")
                    val latSrv = json.optLong("latency_ms", -1)
                    val output = buildString {
                        appendLine("Reconocido:")
                        appendLine(text)
                        appendLine("\nLatencia servidor: ${latSrv} ms")
                        appendLine("Latencia total cliente: ${t1 - t0} ms")
                    }
                    ui { txtResult.text = output }
                } catch (ex: Exception) {
                    ui { showError("JSON malformado") }
                }
            }

            fun ui(block: () -> Unit) = runOnUiThread {
                prog.visibility = ProgressBar.GONE
                btnOcr.isEnabled = true
                block()
            }
        })
    }


    private fun scaleBitmapIfNeeded(src: Bitmap, maxWidth: Int): Bitmap {
        if (src.width <= maxWidth) return src

        val newHeight = (src.height * maxWidth / src.width.toFloat()).toInt()
        // src.scale(ancho, alto, filtro)
        return src.scale(maxWidth, newHeight, true)
    }

    private fun bitmapToJpegByteArray(bmp: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().apply {
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, this)
        }.toByteArray()


    private fun requestRuntimePermissionsIfNeeded() {
        val list = mutableListOf<String>()

        if (!checkPermReadExt()) {
            list += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (!checkPerm(Manifest.permission.CAMERA)) {
            list += Manifest.permission.CAMERA
        }
        if (list.isNotEmpty()) {
            requestPerms(list.toTypedArray())
        }
    }

    private fun checkPerm(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) ==
                PackageManager.PERMISSION_GRANTED

    private fun checkPermReadExt() =
        Build.VERSION.SDK_INT >= 33 || checkPerm(Manifest.permission.READ_EXTERNAL_STORAGE)

    private fun requestPerms(perms: Array<String>) =
        requestPermissions(perms, 1234)

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1234 && grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
            toast("Permisos requeridos para funcionar")
        }
    }


    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun showError(msg: String) {
        txtResult.text = msg
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }
}
