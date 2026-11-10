package com.bunc.tv.data

import com.bunc.tv.data.local.SavedAccount
import com.bunc.tv.data.model.*
import com.bunc.tv.data.remote.ApiClient
import com.bunc.tv.data.remote.M3uChannel
import com.bunc.tv.data.remote.M3uParser
import com.bunc.tv.data.remote.XtreamApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

sealed class Account {
    data class Xtream(val host: String, val user: String, val pass: String, val name: String) : Account()
    data class M3u(val url: String, val name: String) : Account()
}

object Repository {

    @Volatile var account: Account? = null
        private set

    private var api: XtreamApi? = null
    private var m3uChannels: List<M3uChannel> = emptyList()

    // Session caches so revisiting a section/category is instant (no re-fetch).
    private val catCache = HashMap<Section, List<Category>>()
    private val contentCache = HashMap<String, List<Content>>()
    private val episodeCache = HashMap<Int, List<Episode>>()

    val isM3u: Boolean get() = account is Account.M3u

    private fun clearCaches() {
        catCache.clear(); contentCache.clear(); episodeCache.clear()
    }

    fun reset() {
        account = null; api = null; m3uChannels = emptyList(); clearCaches()
    }

    private fun normalizeHost(raw: String): String {
        var h = raw.trim()
        if (!h.startsWith("http://", true) && !h.startsWith("https://", true)) h = "http://$h"
        while (h.endsWith("/")) h = h.dropLast(1)
        return h
    }

    // ----------  Connect  ----------

    suspend fun connectXtream(host: String, user: String, pass: String, name: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val h = normalizeHost(host)
                val service = ApiClient.xtream(h)
                val auth = service.login(user, pass)
                if (auth.userInfo?.auth == 1 || auth.userInfo?.status.equals("Active", true)) {
                    api = service
                    account = Account.Xtream(h, user, pass, name.ifBlank { user })
                    clearCaches()
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("Login rejected by server"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun connectM3u(url: String, name: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val text = fetch(url.trim())
            val channels = M3uParser.parse(text)
            if (channels.isEmpty()) return@withContext Result.failure(IllegalStateException("No channels found in playlist"))
            m3uChannels = channels
            account = Account.M3u(url.trim(), name.ifBlank { "Playlist" })
            clearCaches()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restore(saved: SavedAccount): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            clearCaches()
            if (saved.type == "xtream") {
                val h = normalizeHost(saved.host)
                api = ApiClient.xtream(h)
                account = Account.Xtream(h, saved.user, saved.pass, saved.name)
            } else {
                val text = fetch(saved.m3uUrl)
                m3uChannels = M3uParser.parse(text)
                account = Account.M3u(saved.m3uUrl, saved.name)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun toSaved(): SavedAccount? = when (val a = account) {
        is Account.Xtream -> SavedAccount(type = "xtream", name = a.name, host = a.host, user = a.user, pass = a.pass)
        is Account.M3u -> SavedAccount(type = "m3u", name = a.name, m3uUrl = a.url)
        null -> null
    }

    // ----------  Browse (cached)  ----------

    suspend fun categories(section: Section): List<Category> = withContext(Dispatchers.IO) {
        catCache[section]?.let { return@withContext it }
        val all = Category("", "All")
        val result = when (val a = account) {
            is Account.Xtream -> {
                val svc = api ?: return@withContext listOf(all)
                val raw = when (section) {
                    Section.LIVE -> svc.liveCategories(a.user, a.pass)
                    Section.MOVIES -> svc.vodCategories(a.user, a.pass)
                    Section.SERIES -> svc.seriesCategories(a.user, a.pass)
                }
                listOf(all) + raw.mapNotNull { c ->
                    val id = c.categoryId ?: return@mapNotNull null
                    Category(id, c.categoryName ?: "Unnamed")
                }
            }
            is Account.M3u -> {
                if (section != Section.LIVE) emptyList()
                else listOf(all) + m3uChannels.map { it.group }.distinct().sorted().map { Category(it, it) }
            }
            null -> emptyList()
        }
        catCache[section] = result
        result
    }

    suspend fun content(section: Section, categoryId: String): List<Content> = withContext(Dispatchers.IO) {
        val key = "$section:$categoryId"
        contentCache[key]?.let { return@withContext it }
        val catOrNull = categoryId.ifBlank { null }
        val result = when (val a = account) {
            is Account.Xtream -> {
                val svc = api ?: return@withContext emptyList()
                when (section) {
                    Section.LIVE -> svc.liveStreams(a.user, a.pass, catOrNull).mapNotNull {
                        val id = it.streamId ?: return@mapNotNull null
                        Content(id, it.name ?: "Channel", it.streamIcon, Section.LIVE)
                    }
                    Section.MOVIES -> svc.vodStreams(a.user, a.pass, catOrNull).mapNotNull {
                        val id = it.streamId ?: return@mapNotNull null
                        Content(id, it.name ?: "Movie", it.streamIcon, Section.MOVIES, containerExt = it.containerExtension)
                    }
                    Section.SERIES -> svc.series(a.user, a.pass, catOrNull).mapNotNull {
                        val id = it.seriesId ?: return@mapNotNull null
                        Content(id, it.name ?: "Series", it.cover, Section.SERIES, seriesId = it.seriesId)
                    }
                }
            }
            is Account.M3u -> {
                if (section != Section.LIVE) emptyList()
                else m3uChannels
                    .filter { catOrNull == null || it.group == catOrNull }
                    .map { M3uParser.toContent(it) }
            }
            null -> emptyList()
        }
        contentCache[key] = result
        result
    }

    suspend fun episodes(seriesId: Int): List<Episode> = withContext(Dispatchers.IO) {
        episodeCache[seriesId]?.let { return@withContext it }
        val a = account as? Account.Xtream ?: return@withContext emptyList()
        val svc = api ?: return@withContext emptyList()
        val info = svc.seriesInfo(a.user, a.pass, seriesId)
        val result = info.episodes?.values?.flatten()?.mapNotNull { e ->
            val id = e.id ?: return@mapNotNull null
            Episode(id, e.title ?: "Episode", e.containerExtension ?: "mp4")
        } ?: emptyList()
        episodeCache[seriesId] = result
        result
    }

    // ----------  Stream URLs  ----------

    fun liveUrl(streamId: Int): String {
        val a = account as? Account.Xtream ?: return ""
        return "${a.host}/live/${a.user}/${a.pass}/$streamId.ts"
    }

    fun movieUrl(streamId: Int, ext: String?): String {
        val a = account as? Account.Xtream ?: return ""
        return "${a.host}/movie/${a.user}/${a.pass}/$streamId.${ext ?: "mp4"}"
    }

    fun episodeUrl(episode: Episode): String {
        val a = account as? Account.Xtream ?: return ""
        return "${a.host}/series/${a.user}/${a.pass}/${episode.id}.${episode.ext}"
    }

    fun m3uUrl(contentId: Int): String =
        m3uChannels.firstOrNull { it.index == contentId }?.url ?: ""

    // ----------  Raw fetch for M3U  ----------

    private fun fetch(url: String): String {
        val req = Request.Builder().url(url).build()
        ApiClient.okHttp().newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            return resp.body?.string() ?: ""
        }
    }
}
