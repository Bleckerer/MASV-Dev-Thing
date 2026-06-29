package com.cambrian.masv_dev.api

import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object ApiClient {

    private val certificatePinner = CertificatePinner.Builder()
        .add("api.massive.app", "sha256/31pbZjJp98u4VpeO2e+6vNK707ftVWZuOwsDQwYJj2U=")
        .build()

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .certificatePinner(certificatePinner)
            .build()
    }
}