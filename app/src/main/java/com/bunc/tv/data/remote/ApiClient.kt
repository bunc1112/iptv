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
