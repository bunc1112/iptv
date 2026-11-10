package com.bunc.tv.data.model

import com.google.gson.annotations.SerializedName

/* ----------  Xtream Codes API response models  ---------- */

data class XtreamAuth(
    @SerializedName("user_info") val userInfo: UserInfo?,
    @SerializedName("server_info") val serverInfo: ServerInfo?
)

data class UserInfo(
    @SerializedName("auth") val auth: Int?,
    @SerializedName("status") val status: String?,
    @SerializedName("username") val username: String?
)

data class ServerInfo(
    @SerializedName("url") val url: String?,
    @SerializedName("port") val port: String?
)

data class XtCategory(
    @SerializedName("category_id") val categoryId: String?,
    @SerializedName("category_name") val categoryName: String?
)

data class XtLiveStream(
    @SerializedName("name") val name: String?,
    @SerializedName("stream_id") val streamId: Int?,
    @SerializedName("stream_icon") val streamIcon: String?,
    @SerializedName("category_id") val categoryId: String?
)

data class XtVodStream(
    @SerializedName("name") val name: String?,
    @SerializedName("stream_id") val streamId: Int?,
    @SerializedName("stream_icon") val streamIcon: String?,
    @SerializedName("category_id") val categoryId: String?,
    @SerializedName("container_extension") val containerExtension: String?
)

data class XtSeries(
    @SerializedName("name") val name: String?,
    @SerializedName("series_id") val seriesId: Int?,
    @SerializedName("cover") val cover: String?,
    @SerializedName("category_id") val categoryId: String?
)

/* get_series_info */
data class XtSeriesInfo(
    @SerializedName("episodes") val episodes: Map<String, List<XtEpisode>>?
)

data class XtEpisode(
    @SerializedName("id") val id: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("season") val season: Int?,
    @SerializedName("container_extension") val containerExtension: String?
)

/* ----------  UI-facing domain models  ---------- */

enum class Section { LIVE, MOVIES, SERIES }

data class Category(
    val id: String,
    val name: String
)

/** A browsable tile: a channel, a movie, or a series. */
data class Content(
    val id: Int,
    val name: String,
    val logo: String?,
    val section: Section,
    val containerExt: String? = null, // movies
    val seriesId: Int? = null         // series
)

data class Episode(
    val id: String,
    val title: String,
    val ext: String
)
