// app/src/main/java/com/ejemplo/ocr/HomeFragment.kt
package com.ejemplo.ocr

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class HomeFragment : Fragment(R.layout.fragment_home) {

    /** Botón de “Iniciar sesión” (es opcional, podemos ocultarlo) */
    private var btnLogin: MaterialButton? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        /* ─────── BOTÓN LOGIN (esquina superior derecha) ─────── */
        btnLogin = view.findViewById<MaterialButton>(R.id.btnLogin)?.apply {
            setOnClickListener {
                startActivity(Intent(requireContext(), LoginActivity::class.java))
            }
        }

        /* ─────── TARJETA 1 · “Subir imagen” ─────── */
        val galleryCard = view.findViewById<MaterialCardView>(R.id.cardGallery)
        galleryCard.apply {
            // fondo gradiente (ajústalo a tu drawable / color)
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_blue_grad)

            // icono + texto
            findViewById<ImageView>(R.id.imgIcon)
                .setImageResource(R.drawable.ic_upload)
            findViewById<TextView>(R.id.tvLabel).apply {
                text = getString(R.string.subir_imagen)
                setTextColor(ContextCompat.getColor(context, R.color.white))
            }

            // acción
            setOnClickListener {
                startActivity(
                    Intent(requireContext(), MainActivity::class.java)
                        .putExtra("mode", "gallery")
                )
            }
        }

        /* ─────── TARJETA 2 · “Escaneo por lotes” ─────── */
        val batchCard = view.findViewById<MaterialCardView>(R.id.cardBatch)
        batchCard.apply {
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_green_grad)

            findViewById<ImageView>(R.id.imgIcon)
                .setImageResource(R.drawable.ic_doc_multi)
            findViewById<TextView>(R.id.tvLabel).apply {
                text = getString(R.string.escaneo_lotes)
                setTextColor(ContextCompat.getColor(context, R.color.white))
            }

            setOnClickListener {
                startActivity(Intent(requireContext(), BatchScanActivity::class.java))
            }
        }

        updateLoginButton()   // primera comprobación
    }

    /** Se vuelve a chequear cada vez que el fragmento vuelve al primer plano */
    override fun onResume() {
        super.onResume()
        updateLoginButton()
    }

    /** Muestra u oculta el botón de login según exista token en prefs */
    private fun updateLoginButton() {
        btnLogin?.visibility =
            if (AuthManager.isLoggedIn(requireContext()))
                View.GONE           // ya hay sesión → ocultar
            else
                View.VISIBLE        // no hay sesión → mostrar
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
}
