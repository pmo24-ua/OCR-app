package com.ejemplo.ocr

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar

class BatchResultActivity : AppCompatActivity() {

    companion object {
        fun start(ctx: android.content.Context, results: List<String>) =
            ctx.startActivity(
                Intent(ctx, BatchResultActivity::class.java)
                    .putStringArrayListExtra("R", ArrayList(results))
            )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_batch_result)

        /* ───── datos recibidos ───── */
        val results = intent.getStringArrayListExtra("R") ?: arrayListOf()

        /* ───── toolbar sólo con “volver” ───── */
        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            subtitle = getString(R.string.result_n, results.size)
            setNavigationOnClickListener { finish() }
        }

        /* ───── lista de resultados ───── */
        findViewById<RecyclerView>(R.id.recyclerResults).run {
            layoutManager = LinearLayoutManager(this@BatchResultActivity)
            adapter       = BatchResultAdapter(this@BatchResultActivity, results)
        }
    }
}
