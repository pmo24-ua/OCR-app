package com.ejemplo.ocr

import okhttp3.RequestBody
import okio.*

/**
 * Envuelve un RequestBody existente y emite callbacks de progreso (0-100 %).
 */
class ProgressRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (percent: Int) -> Unit
) : RequestBody() {

    override fun contentType()   = delegate.contentType()
    override fun contentLength() = delegate.contentLength()

    override fun writeTo(sink: BufferedSink) {
        val countingSink = object : ForwardingSink(sink) {
            var written = 0L
            val total   = contentLength().takeIf { it > 0 } ?: 1L
            override fun write(source: Buffer, byteCount: Long) {
                super.write(source, byteCount)
                written += byteCount
                val pct = (written * 100 / total).toInt()
                onProgress(pct.coerceIn(0, 100))
            }
        }
        val buffered = countingSink.buffer()
        delegate.writeTo(buffered)
        buffered.flush()
    }
}
