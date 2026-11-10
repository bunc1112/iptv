package com.bunc.tv.data.remote

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private val http: OkHttpClient by lazy {
        // Load lots of channel/poster logos in parallel so grids fill fast.
        val dispatcher = okhttp3.Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 16
        }
        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // Keep more connections warm so category switches reuse sockets.
            .connectionPool(okhttp3.ConnectionPool(12, 5, TimeUnit.MINUTES))
            .build()
    }

    private val gson = GsonBuilder().setLenient().create()

    /** Build an Xtream API bound to a specific server base URL. */
    fun xtream(baseUrl: String): XtreamApi {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(http)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(XtreamApi::class.java)
    }

    fun okHttp(): OkHttpClient = http
}
