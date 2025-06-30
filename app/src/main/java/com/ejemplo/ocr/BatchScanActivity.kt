package com.ejemplo.ocr

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class BatchScanActivity : AppCompatActivity() {

    private fun getBatchUrl(): String {
        val prefs = getSharedPreferences("ocr_prefs", Context.MODE_PRIVATE)
        return if (prefs.getInt("engine", 0) == 0) {
            "${BuildConfig.BASE_URL}/ocr_batch"
        } else {
            "${BuildConfig.BASE_URL}/ocr_batch_google"
        }
    }
    private val imgUris = mutableListOf<Uri>()

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: ThumbAdapter
    private lateinit var badge: TextView
    private lateinit var fabSend: FloatingActionButton
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var progressLayout: View
    private val MAX_IMAGES = 10

    private val client by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(this))
            .connectTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    // ══════════════════════════════════════════════════════════════════
    // Launchers definidos a nivel de propiedad, para evitar registrarlos dentro de funciones:
    // ══════════════════════════════════════════════════════════════════

    // 1) Selector de múltiples imágenes de galería:
    private val pickMulti =
        registerForActivityResult(GetMultipleContents()) { uris: List<Uri> ->
            if (uris.isEmpty()) return@registerForActivityResult          // nada elegido

            val newTotal = imgUris.size + uris.size
            if (newTotal > MAX_IMAGES) {
                toast("Máximo $MAX_IMAGES imágenes por lote")
                return@registerForActivityResult
            }

            val startPos = imgUris.size
            imgUris += uris
            adapter.notifyItemRangeInserted(startPos, uris.size)
            updateBadgeCount()
        }


    // 2) Para tomar foto con cámara: guardamos tmpPhotoUri antes de lanzar
    private var tmpPhotoUri: Uri? = null
    private val takePhoto =
        registerForActivityResult(TakePicture()) { ok: Boolean ->
            if (!ok || tmpPhotoUri == null) return@registerForActivityResult

            if (imgUris.size >= MAX_IMAGES) {
                toast("Máximo $MAX_IMAGES imágenes por lote")
                return@registerForActivityResult
            }

            imgUris += tmpPhotoUri!!
            adapter.notifyItemInserted(imgUris.lastIndex)
            updateBadgeCount()
        }


    // 3) RequestPermission para cámara: registrado también a nivel de clase
    private val requestCameraPermission =
        registerForActivityResult(RequestPermission()) { granted: Boolean ->
            if (granted) {
                launchCamera()
            } else {
                toast("Permiso de cámara denegado")
            }
        }

    // ══════════════════════════════════════════════════════════════════
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AuthManager.isLoggedIn(this)) {
            // Si no está logueado, lo mandamos a Login y cerramos esta Activity
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_batch_scan)

        findViewById<MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { finish() }
        // Referencias a vistas
        recycler       = findViewById(R.id.recyclerThumbs)
        badge          = findViewById(R.id.badgeCount)
        fabSend        = findViewById(R.id.fabSend)
        progressBar    = findViewById(R.id.progressBar)
        progressText   = findViewById(R.id.progressText)
        progressLayout = findViewById(R.id.progressLayout)

        // Configurar RecyclerView
        adapter = ThumbAdapter(imgUris)
        recycler.layoutManager = GridLayoutManager(this, 3)
        recycler.adapter = adapter

        // Clicks para galería y cámara
        findViewById<View>(R.id.cardGallery).setOnClickListener {
            // Lanza selector de múltiples imágenes
            pickMulti.launch("image/*")
        }
        findViewById<View>(R.id.cardCamera).setOnClickListener {
            checkAndTakePhoto()
        }

        // Click para enviar batch
        fabSend.setOnClickListener {
            if (imgUris.isEmpty()) {
                toast("Añade imágenes primero")
            } else {
                uploadBatch()
            }
        }

        updateBadgeCount()
    }

    private fun updateBadgeCount() {
        // Actualiza el texto del badge; podrías reemplazar por getString(R.string.image_count, imgUris.size) si usas strings.xml
        badge.text = "${imgUris.size} imágenes"
    }

    // ══════════════════════════════════════════════════════════════════
    // Cámara y permiso
    // ══════════════════════════════════════════════════════════════════
    private fun checkAndTakePhoto() {
        val perm = Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(this, perm) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera()
        } else {
            // Lanza el permiso. requestCameraPermission se registró en la propiedad.
            requestCameraPermission.launch(perm)
        }
    }

    private fun launchCamera() {
        // Crea un archivo temporal en cache
        val file = try {
            File.createTempFile("cam_", ".jpg", cacheDir)
        } catch (e: IOException) {
            toast("Error al crear archivo temporal para la cámara: ${e.localizedMessage}")
            return // Exit if file creation fails
        }

        // Construye Uri con FileProvider
        val photoUriForLaunch: Uri = try {
            FileProvider.getUriForFile(
                this,
                "${packageName}.fp",  // authority debe coincidir con tu manifest
                file
            )
        } catch (e: IllegalArgumentException) {
            toast("Error al obtener Uri para el archivo: ${e.localizedMessage}")
            // Consider logging e for more details
            return // Exit if URI creation fails
        }

        // Guarda la URI en la propiedad de la clase para usarla en el callback
        this.tmpPhotoUri = photoUriForLaunch

        // Lanza el Intent de cámara con la URI local y no nula
        takePhoto.launch(photoUriForLaunch)
    }

    // ══════════════════════════════════════════════════════════════════
    // Upload en background
    // ══════════════════════════════════════════════════════════════════
    private fun uploadBatch() {

        progressLayout.visibility = View.VISIBLE
        fabSend.isEnabled = false

        Thread {

            /* ───── 1.  Construir MultipartBody con N imágenes ───── */
            val mpBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)

            imgUris.forEachIndexed { i, uri ->
                val bmp   = getBitmap(uri) ?: return@forEachIndexed
                val bytes = bitmapToJpeg(scaleBitmapIfNeeded(bmp))

                mpBuilder.addFormDataPart(
                    "files",                       // <──  nombre plural que tu API espera
                    "img_$i.jpg",
                    bytes.toRequestBody("image/jpeg".toMediaType())
                )
            }
            val mpBody = mpBuilder.build()

            /* ───── 2.  Envolver en ProgressRequestBody ───── */
            val bodyWithProg = ProgressRequestBody(mpBody) { pct ->
                runOnUiThread { progressBar.progress = pct }
            }

            /* ───── 3.  Petición HTTP ───── */
            val req = Request.Builder()
                .url(getBatchUrl())
                .post(bodyWithProg)
                .build()

            try {
                val resp = client.newCall(req).execute()
                if (!resp.isSuccessful) {
                    runOnUiThread { toast("Error HTTP: ${resp.code}") }
                } else {
                    val json = org.json.JSONObject(resp.body?.string().orEmpty())
                    val textos = json.optJSONArray("texts") ?: return@Thread
                    val list   = (0 until textos.length()).map { textos.getString(it) }
                    runOnUiThread {
                        BatchResultActivity.start(this@BatchScanActivity, list)
                    }
                }
            } catch (e: IOException) {
                runOnUiThread { toast("Fallo de red: ${e.localizedMessage}") }
            } finally {
                runOnUiThread {
                    progressLayout.visibility = View.GONE
                    fabSend.isEnabled = true
                }
            }

        }.start()
    }


    // ══════════════════════════════════════════════════════════════════
    // Utils bmp / bytes
    // ══════════════════════════════════════════════════════════════════
    private fun getBitmap(uri: Uri): Bitmap? =
        contentResolver.openInputStream(uri)?.use { ins ->
            BitmapFactory.decodeStream(ins)
        }

    private fun scaleBitmapIfNeeded(bmp: Bitmap): Bitmap {
        val max = 1024
        return if (bmp.width <= max) {
            bmp
        } else {
            val newHeight = (bmp.height * max / bmp.width.toFloat()).toInt()
            // Usa extensión KTX: import androidx.core.graphics.scale
            bmp.scale(max, newHeight)
        }
    }

    private fun bitmapToJpeg(bmp: Bitmap): ByteArray {
        val quality = 80
        return ByteArrayOutputStream().apply {
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, this)
        }.toByteArray()
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
