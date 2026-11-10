package com.bunc.tv.data.remote

import com.bunc.tv.data.model.*
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Xtream Codes "player_api.php" endpoints. Base URL is the user's server
 * (http://host:port/). Username/password are passed on every call.
 */
interface XtreamApi {

    @GET("player_api.php")
    suspend fun login(
        @Query("username") user: String,
        @Query("password") pass: String
    ): XtreamAuth

    @GET("player_api.php")
    suspend fun liveCategories(
        @Query("username") user: String,
        @Query("password") pass: String,
        @Query("action") action: String = "get_live_categories"
    ): List<XtCategory>

    @GET("player_api.php")
    suspend fun vodCategories(
        @Query("username") user: String,
        @Query("password") pass: String,
        @Query("action") action: String = "get_vod_categories"
    ): List<XtCategory>

    @GET("player_api.php")
    suspend fun seriesCategories(
        @Query("username") user: String,
        @Query("password") pass: String,
        @Query("action") action: String = "get_series_categories"
    ): List<XtCategory>

    @GET("player_api.php")
    suspend fun liveStreams(
        @Query("username") user: String,
        @Query("password") pass: String,
        @Query("category_id") categoryId: String?,
        @Query("action") action: String = "get_live_streams"
    ): List<XtLiveStream>

    @GET("player_api.php")
    suspend fun vodStreams(
        @Query("username") user: String,
        @Query("password") pass: String,
        @Query("category_id") categoryId: String?,
        @Query("action") action: String = "get_vod_streams"
    ): List<XtVodStream>

    @GET("player_api.php")
    suspend fun series(
        @Query("username") user: String,
        @Query("password") pass: String,
        @Query("category_id") categoryId: String?,
        @Query("action") action: String = "get_series"
    ): List<XtSeries>

    @GET("player_api.php")
    suspend fun seriesInfo(
        @Query("username") user: String,
        @Query("password") pass: String,
        @Query("series_id") seriesId: Int,
        @Query("action") action: String = "get_series_info"
    ): XtSeriesInfo
}
