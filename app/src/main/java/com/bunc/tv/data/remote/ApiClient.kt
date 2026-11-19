package com.bunc.tv.data.remote

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    /**
     * Identify as a normal Android client (the system's own HTTP user agent) instead of
     * "okhttp/x.y" — some IPTV panels answer library user agents with 403/404.
     */
    val userAgent: String =
        System.getProperty("http.agent")?.takeIf { it.isNotBlank() } ?: "Dalvik/2.1.0 (Linux; U; Android)"

    /**
     * Client identities tried at login, in order. Some panels only answer certain clients
     * (and reply 404 to others); the one that works is saved with the profile.
     */
    val userAgentCandidates: List<Pair<String, String>> = listOf(
        "Android" to userAgent,
        "VLC" to "VLC/3.0.20 LibVLC/3.0.20",
        "Browser" to "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36",
        "okhttp" to "okhttp/4.12.0"
    )

    /** Identity chosen for the signed-in account (null = default [userAgent]). */
    @Volatile var accountUserAgent: String? = null

    fun currentUserAgent(): String = accountUserAgent ?: userAgent

    private val http: OkHttpClient by lazy {
        // Load lots of channel/poster logos in parallel so grids fill fast.
        val dispatcher = okhttp3.Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 16
        }
        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .addInterceptor { chain ->
                val req = chain.request()
                chain.proceed(
                    if (req.header("User-Agent") == null) req.newBuilder().header("User-Agent", currentUserAgent()).build()
                    else req
                )
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // Keep more connections warm so category switches reuse sockets.
            .connectionPool(okhttp3.ConnectionPool(12, 5, TimeUnit.MINUTES))
            .build()
    }

    /** Panels send numbers as text and text as numbers; accept either instead of failing the whole list. */
    private val tolerantInt = object : TypeAdapter<Int?>() {
        override fun write(out: JsonWriter, value: Int?) {
            if (value == null) out.nullValue() else out.value(value)
        }

        override fun read(reader: JsonReader): Int? = when (reader.peek()) {
            JsonToken.NULL -> { reader.nextNull(); null }
            JsonToken.NUMBER -> reader.nextDouble().toInt()
            JsonToken.STRING -> reader.nextString().trim().toDoubleOrNull()?.toInt()
            JsonToken.BOOLEAN -> if (reader.nextBoolean()) 1 else 0
            else -> { reader.skipValue(); null }
        }
    }

    private val tolerantString = object : TypeAdapter<String?>() {
        override fun write(out: JsonWriter, value: String?) {
            if (value == null) out.nullValue() else out.value(value)
        }

        override fun read(reader: JsonReader): String? = when (reader.peek()) {
            JsonToken.NULL -> { reader.nextNull(); null }
            JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
            JsonToken.BOOLEAN -> reader.nextBoolean().toString()
            else -> { reader.skipValue(); null }
        }
    }

    val gson: Gson = GsonBuilder()
        .setLenient()
        .registerTypeAdapter(Int::class.javaObjectType, tolerantInt)
        .registerTypeAdapter(Int::class.javaPrimitiveType, tolerantInt)
        .registerTypeAdapter(String::class.java, tolerantString)
        .create()

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
