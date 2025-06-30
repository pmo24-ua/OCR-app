package com.ejemplo.ocr

import android.content.Context
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val ctx: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val tok = AuthManager.token(ctx)
        val req = if (tok != null) {
            chain.request()
                .newBuilder()
                .addHeader("Authorization", "Bearer $tok")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(req)
    }
}
