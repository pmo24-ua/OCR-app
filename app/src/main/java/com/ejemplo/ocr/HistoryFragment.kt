package com.ejemplo.ocr

import android.app.AlertDialog
import android.content.*
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.content.getSystemService
import androidx.core.view.GestureDetectorCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.*
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONArray
import java.io.IOException
import java.time.*
import java.time.format.TextStyle
import java.util.*

class HistoryFragment : Fragment(R.layout.fragment_history) {



    /* ─────────── Modelo ─────────── */
    data class HistoryItem(val id: Int, val text: String, val date: LocalDate)

    /** Filas que se muestran en la lista (header o item) */
    private sealed class Row {
        data class Header(val date: LocalDate) : Row()
        data class Item(val data: HistoryItem) : Row()
    }

    /* ─────────── Adaptador ─────────── */
    private class HistoryAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val rows = mutableListOf<Row>()

        fun submit(newRows: List<Row>) {
            rows.clear(); rows += newRows
            notifyDataSetChanged()
        }

        override fun getItemViewType(pos: Int) =
            when (rows[pos]) {
                is Row.Header -> 0
                is Row.Item   -> 1
            }

        /* ---------- ViewHolders ---------- */
        class HeaderVH(v: View) : RecyclerView.ViewHolder(v) {
            val tv: TextView = v.findViewById(R.id.tvHeader)
        }
        class ItemVH(v: View) : RecyclerView.ViewHolder(v) {
            val card   : View        = v.findViewById(R.id.cardRoot)
            val tvText : TextView    = v.findViewById(R.id.tvText)
            val btnCopy: MaterialButton = v.findViewById(R.id.btnCopy)
            val btnDel : MaterialButton   = v.findViewById(R.id.btnDelete)
        }

        override fun onCreateViewHolder(p: ViewGroup, vt: Int): RecyclerView.ViewHolder =
            if (vt == 0) {
                val v = LayoutInflater.from(p.context)
                    .inflate(R.layout.item_header_date, p, false)
                HeaderVH(v)
            } else {
                val v = LayoutInflater.from(p.context)
                    .inflate(R.layout.item_history, p, false)
                ItemVH(v)
            }
        override fun getItemCount() = rows.size

        suspend fun deleteHistoryItem(ctx: Context, id: Int) =
            withContext(Dispatchers.IO) {
                val tok = AuthManager.token(ctx) ?: throw IOException("Sin token")
                val req = Request.Builder()
                    .url("${BuildConfig.BASE_URL}/history/$id")
                    .delete()
                    .addHeader("Authorization", "Bearer $tok")
                    .build()
                OkHttpClient().newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                }
            }

        override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
            when (val row = rows[pos]) {
                is Row.Header -> {
                    val date = row.date
                    val title = buildString {
                        append(
                            date.dayOfWeek.getDisplayName(
                                TextStyle.FULL, Locale("es")
                            ).replaceFirstChar { it.uppercase() }
                        )
                        append(" ").append(date.format(
                            java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")
                                .withLocale(Locale("es"))
                        ))
                    }
                    (h as HeaderVH).tv.text = title
                }
                is Row.Item -> {
                    val vh = h as ItemVH
                    vh.tvText.text = row.data.text
                    vh.btnCopy.setOnClickListener { v ->
                        v.context.getSystemService<ClipboardManager>()
                            ?.setPrimaryClip(ClipData.newPlainText("OCR", row.data.text))
                        Toast.makeText(v.context, R.string.copiado, Toast.LENGTH_SHORT).show()
                    }
                    vh.btnDel.setOnClickListener { v ->
                        AlertDialog.Builder(v.context)
                            .setMessage(R.string.confirmar_borrado)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                // 1) petición DELETE
                                (v.context as? FragmentActivity)?.lifecycleScope?.launch {
                                    try {
                                        deleteHistoryItem(v.context, row.data.id)
                                        // 2) quitar de la lista local y refrescar UI
                                        val idx = rows.indexOf(row)
                                        rows.removeAt(idx)
                                        notifyItemRemoved(idx)
                                        Toast.makeText(v.context,
                                            R.string.borrado_ok, Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(v.context,
                                            v.context.getString(R.string.error_fmt, e.message),
                                            Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                    }
                    // 1) Creamos un solo listener que implemente OnGestureListener y OnDoubleTapListener
                    val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
                        override fun onDoubleTap(e: MotionEvent): Boolean {
                            MaterialAlertDialogBuilder(vh.card.context)
                                .setTitle(R.string.historial_detalle)
                                .setMessage(row.data.text)
                                .setPositiveButton(android.R.string.ok, null)
                                .show()
                            return true
                        }
                    }

                    // 2) Usamos el GestureDetector de plataforma
                    val gd = GestureDetector(vh.card.context, gestureListener).apply {
                        // asignamos también el mismo listener para doble-tap
                        setOnDoubleTapListener(gestureListener)
                    }

                    // 3) Enlazamos al card
                    vh.card.setOnTouchListener { v, event ->
                        // Delega en GestureDetector (captura doble tap)
                        gd.onTouchEvent(event)

                        // Cuando levantamos el dedo, consideramos que fue un "click"
                        if (event.action == MotionEvent.ACTION_UP) {
                            v.performClick()
                        }
                        // Devolvemos false para dejar que el click original también se propague
                        false
                    }


                }
            }
        }
    }

    /* ─────────── Estado & vistas ─────────── */
    private val allItems = mutableListOf<HistoryItem>()     // datos completos
    private var selectedDate: LocalDate? = null             // filtro activo
    private val adapter = HistoryAdapter()

    private lateinit var swipe  : SwipeRefreshLayout
    private lateinit var recycler: RecyclerView

    private var isLoading = false

    /* ─────────── Ciclo de vida ─────────── */
    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)

        // 0) Toolbar con back arrow
        v.findViewById<MaterialToolbar>(R.id.topBarHist)
            .setNavigationOnClickListener {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        val btnLogin = v.findViewById<MaterialButton>(R.id.btnIrLogin)
        // 2) Dale un listener que abra la LoginActivity
        btnLogin.setOnClickListener {
            startActivity(
                Intent(requireContext(), LoginActivity::class.java).apply {
                    // si quieres limpiar el backstack:
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            requireActivity().finish() // opcional, para que no se quede el HistoryFragment abierto
        }

        swipe    = v.findViewById(R.id.swipe)
        recycler = v.findViewById(R.id.recyclerHist)

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter       = adapter

        swipe.setOnRefreshListener { loadHistory() }

        // FAB calendario
        v.findViewById<FloatingActionButton>(R.id.btnPickDate)
            .setOnClickListener { openDatePicker() }

        updateUi()
    }

    override fun onResume() {
        super.onResume(); updateUi()
    }

    /* ─────────── DatePicker & filtrado ─────────── */
    private fun openDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.filtrar_fecha)
            .setSelection(selectedDate?.toEpochDay()?.let {
                it * 86_400_000L /*ms*/ } ?: MaterialDatePicker.todayInUtcMilliseconds()
            )
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            selectedDate = Instant.ofEpochMilli(millis)
                .atZone(ZoneId.systemDefault()).toLocalDate()
            createRowsAndShow()
        }
        picker.addOnNegativeButtonClickListener {
            selectedDate = null      // “cancelar” = quitar filtro
            createRowsAndShow()
        }
        picker.show(parentFragmentManager, "pick")
    }

    /* ─────────── UI según sesión ─────────── */
    private fun updateUi() {
        if (!AuthManager.isLoggedIn(requireContext())) {
            view?.findViewById<View>(R.id.layoutNoSession)?.visibility = View.VISIBLE
            swipe.visibility = View.GONE
        } else {
            view?.findViewById<View>(R.id.layoutNoSession)?.visibility = View.GONE
            swipe.visibility = View.VISIBLE
            if (allItems.isEmpty() && !isLoading) {   // ← evita 2ª llamada
                loadHistory()
            }
        }
    }

    /* ─────────── Red + transformación a filas ─────────── */
    private fun loadHistory() = viewLifecycleOwner.lifecycleScope.launch {
        if (isLoading) return@launch           // ← bloqueo reentrante
        isLoading = true
        swipe.isRefreshing = true
        try {
            allItems.clear(); allItems += fetchHistory(requireContext())
            createRowsAndShow()
        } catch (e: Exception) {
            Toast.makeText(requireContext(),
                getString(R.string.error_fmt, e.message), Toast.LENGTH_LONG).show()
        } finally {
            swipe.isRefreshing = false
        }
    }

    /** Agrupa los items por fecha, añade headers y los pasa al adapter */
    private fun createRowsAndShow() {
        val filtered = selectedDate?.let { sel ->
            allItems.filter { it.date == sel }
        } ?: allItems

        val rows = mutableListOf<Row>()
        filtered.groupBy { it.date }
            .toSortedMap(compareByDescending { it })       // fechas desc
            .forEach { (date, list) ->
                rows += Row.Header(date)
                rows += list.map { Row.Item(it) }
            }
        adapter.submit(rows)

        val empty = view?.findViewById<View>(R.id.layoutEmpty)
        if (rows.isEmpty()) {
            recycler.visibility = View.GONE
            empty?.visibility   = View.VISIBLE
        } else {
            recycler.visibility = View.VISIBLE
            empty?.visibility   = View.GONE
        }
    }

    private suspend fun fetchHistory(ctx: Context, limit: Int = 100)
            : List<HistoryItem> = withContext(Dispatchers.IO) {
        val tok = AuthManager.token(ctx) ?: throw IOException("Sin token")
        val req = Request.Builder()
            .url("${BuildConfig.BASE_URL}/history?limit=$limit")
            .addHeader("Authorization", "Bearer $tok")
            .build()
        OkHttpClient().newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val arr = JSONArray(resp.body!!.string())
            List(arr.length()) { i ->
                arr.getJSONObject(i).run {
                    val date = LocalDate.parse(
                        getString("date").substring(0, 10))   // yyyy-MM-dd
                    HistoryItem(
                        id   = getInt("id"),
                        text = getString("text"),
                        date = date
                    )
                }
            }
        }
    }
}
