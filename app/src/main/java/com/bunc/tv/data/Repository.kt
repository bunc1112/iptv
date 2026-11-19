package com.bunc.tv.data

import com.bunc.tv.data.local.SavedAccount
import com.bunc.tv.data.model.*
import com.bunc.tv.data.remote.ApiClient
import com.bunc.tv.data.remote.M3uChannel
import com.bunc.tv.data.remote.M3uParser
import com.bunc.tv.data.remote.XtreamApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request

sealed class Account {
    data class Xtream(val host: String, val user: String, val pass: String, val name: String, val userAgent: String? = null) : Account()
    data class M3u(val url: String, val name: String, val userAgent: String? = null) : Account()
}

object Repository {

    @Volatile var account: Account? = null
        private set

    @Volatile private var api: XtreamApi? = null
    @Volatile private var m3uChannels: List<M3uChannel> = emptyList()

    // Session caches so revisiting a section/category is instant (no re-fetch).
    // Thread-safe: search loads Live, Movies and Series in parallel.
    private val catCache = java.util.concurrent.ConcurrentHashMap<Section, List<Category>>()
    private val contentCache = java.util.concurrent.ConcurrentHashMap<String, List<Content>>()
    private val episodeCache = java.util.concurrent.ConcurrentHashMap<Int, List<Episode>>()

    // EPG caches: streamId -> (valid until, programmes)
    private val nowCache = java.util.concurrent.ConcurrentHashMap<Int, Pair<Long, List<Program>>>()
    private val fullEpgCache = java.util.concurrent.ConcurrentHashMap<Int, Pair<Long, List<Program>>>()
    // Many tiles ask for "now playing" at once; don't flood the provider.
    private val epgLimiter = Semaphore(6)

    /** EPG is available for Xtream accounts (M3U playlists carry no per-channel EPG API). */
    val hasEpg: Boolean get() = account is Account.Xtream

    val isM3u: Boolean get() = account is Account.M3u

    private fun clearCaches() {
        catCache.clear(); contentCache.clear(); episodeCache.clear()
        nowCache.clear(); fullEpgCache.clear()
    }

    fun reset() {
        account = null; api = null; m3uChannels = emptyList(); clearCaches()
        ApiClient.accountUserAgent = null
    }

    private fun normalizeHost(raw: String): String {
        var h = raw.trim()
        if (!h.startsWith("http://", true) && !h.startsWith("https://", true)) h = "http://$h"
        // People sometimes paste a full API/playlist link; keep only scheme://host:port.
        h = h.substringBefore("/player_api.php").substringBefore("/get.php").substringBefore("?")
        while (h.endsWith("/")) h = h.dropLast(1)
        return h
    }

    // ----------  Connect  ----------

    private sealed class LoginTry {
        class Ok(val service: XtreamApi) : LoginTry()
        object Rejected : LoginTry()                       // server says wrong username/password
        class Refused(val note: String) : LoginTry()       // 404/403/not-Xtream for this client identity
        class Network(val error: Exception) : LoginTry()   // no connection at all
    }

    private suspend fun tryLogin(h: String, user: String, pass: String): LoginTry {
        val service = ApiClient.xtream(h)
        return try {
            val auth = service.login(user, pass)
            if (auth.userInfo?.auth == 1 || auth.userInfo?.status.equals("Active", true)) LoginTry.Ok(service)
            else LoginTry.Rejected
        } catch (e: retrofit2.HttpException) {
            val finalHost = e.response()?.raw()?.request?.url?.host
            val moved = if (finalHost != null && !h.contains(finalHost, ignoreCase = true)) " via $finalHost" else ""
            LoginTry.Refused("HTTP ${e.code()}$moved")
        } catch (e: com.google.gson.JsonParseException) {
            LoginTry.Refused("not Xtream data")
        } catch (e: IllegalStateException) {
            LoginTry.Refused("not Xtream data")
        } catch (e: java.io.IOException) {
            LoginTry.Network(e)
        }
    }

    suspend fun connectXtream(host: String, user: String, pass: String, name: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val h = normalizeHost(host)
            val notes = ArrayList<String>()
            // Some panels only answer certain clients: try each identity until one is accepted.
            for ((label, ua) in ApiClient.userAgentCandidates) {
                ApiClient.accountUserAgent = ua
                when (val r = tryLogin(h, user, pass)) {
                    is LoginTry.Ok -> {
                        api = r.service
                        account = Account.Xtream(h, user, pass, name.ifBlank { user }, ua)
                        clearCaches()
                        return@withContext Result.success(Unit)
                    }
                    is LoginTry.Rejected -> {
                        ApiClient.accountUserAgent = null
                        return@withContext Result.failure(IllegalStateException("The server rejected this username or password."))
                    }
                    is LoginTry.Network -> {
                        ApiClient.accountUserAgent = null
                        return@withContext Result.failure(r.error)
                    }
                    is LoginTry.Refused -> notes += "$label: ${r.note}"
                }
            }
            // No Xtream API answered: many providers still serve the M3U playlist with the same login.
            val url = "$h/get.php?username=${enc(user)}&password=${enc(pass)}&type=m3u_plus&output=ts"
            for ((label, ua) in ApiClient.userAgentCandidates) {
                ApiClient.accountUserAgent = ua
                try {
                    val channels = M3uParser.parse(fetch(url))
                    if (channels.isNotEmpty()) {
                        m3uChannels = channels
                        account = Account.M3u(url, name.ifBlank { user }, ua)
                        clearCaches()
                        return@withContext Result.success(Unit)
                    }
                    notes += "$label playlist: empty"
                } catch (e: Exception) {
                    notes += "$label playlist: ${e.message ?: "failed"}"
                }
            }
            ApiClient.accountUserAgent = null
            Result.failure(
                IllegalStateException(
                    "The server refused every connection type Bunc tried. Check the server address with your provider.\n" +
                        notes.joinToString(" · ")
                )
            )
        }

    suspend fun connectM3u(url: String, name: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            ApiClient.accountUserAgent = null
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
            val ua = saved.userAgent.ifBlank { null }
            ApiClient.accountUserAgent = ua
            if (saved.type == "xtream") {
                val h = normalizeHost(saved.host)
                api = ApiClient.xtream(h)
                account = Account.Xtream(h, saved.user, saved.pass, saved.name, ua)
            } else {
                val text = fetch(saved.m3uUrl)
                m3uChannels = M3uParser.parse(text)
                account = Account.M3u(saved.m3uUrl, saved.name, ua)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun toSaved(): SavedAccount? = when (val a = account) {
        is Account.Xtream -> SavedAccount(type = "xtream", name = a.name, host = a.host, user = a.user, pass = a.pass, userAgent = a.userAgent ?: "")
        is Account.M3u -> SavedAccount(type = "m3u", name = a.name, m3uUrl = a.url, userAgent = a.userAgent ?: "")
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
                    val id = c.categoryId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    Category(id, c.categoryName?.trim()?.ifBlank { null } ?: "Unnamed")
                }.distinctBy { it.id }
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
        }.distinctBy { it.id } // grids need unique ids; some panels list a stream twice
        contentCache[key] = result
        result
    }

    suspend fun episodes(seriesId: Int): List<Episode> = withContext(Dispatchers.IO) {
        episodeCache[seriesId]?.let { return@withContext it }
        val a = account as? Account.Xtream ?: return@withContext emptyList()
        val svc = api ?: return@withContext emptyList()
        val info = svc.seriesInfo(a.user, a.pass, seriesId)
        val result = parseEpisodes(info.episodes)
            .sortedWith(compareBy({ it.season ?: 0 }, { it.episodeNum ?: 0 }))
            .mapNotNull { e ->
                val id = e.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                Episode(id, e.title?.ifBlank { null } ?: "Episode ${e.episodeNum ?: ""}".trim(), e.containerExtension?.ifBlank { null } ?: "mp4")
            }
            .distinctBy { it.id }
        episodeCache[seriesId] = result
        result
    }

    /** Episodes arrive as {"season":[...]} on most panels, but as nested arrays on some. */
    private fun parseEpisodes(el: com.google.gson.JsonElement?): List<XtEpisode> {
        if (el == null || el.isJsonNull) return emptyList()
        fun one(e: com.google.gson.JsonElement): XtEpisode? =
            try { ApiClient.gson.fromJson(e, XtEpisode::class.java) } catch (ex: Exception) { null }
        fun many(e: com.google.gson.JsonElement): List<XtEpisode> = when {
            e.isJsonArray -> e.asJsonArray.mapNotNull { if (it.isJsonObject) one(it) else null }
            e.isJsonObject -> listOfNotNull(one(e))
            else -> emptyList()
        }
        return when {
            el.isJsonObject -> el.asJsonObject.entrySet().flatMap { many(it.value) }
            el.isJsonArray -> el.asJsonArray.flatMap { many(it) }
            else -> emptyList()
        }
    }

    // ----------  EPG  ----------

    /** The current programme and the next couple after it (empty if the channel has no EPG). */
    suspend fun nowNext(streamId: Int): List<Program> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        nowCache[streamId]?.let { (until, list) -> if (until > now) return@withContext list }
        val a = account as? Account.Xtream ?: return@withContext emptyList()
        val svc = api ?: return@withContext emptyList()
        val list = try {
            epgLimiter.withPermit {
                parsePrograms(svc.shortEpg(a.user, a.pass, streamId, 4).listings)
            }.filter { it.end > now }.take(3)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        // Valid until the current programme ends (max 30 min), or retry in 10 min if empty.
        val until = list.firstOrNull()?.end?.coerceIn(now + 60_000, now + 30 * 60_000) ?: (now + 10 * 60_000)
        nowCache[streamId] = until to list
        list
    }

    suspend fun nowPlaying(streamId: Int): Program? {
        val now = System.currentTimeMillis()
        return nowNext(streamId).firstOrNull { it.isOn(now) }
    }

    /** Everything still to come for a channel, starting with what is on now. */
    suspend fun fullEpg(streamId: Int): List<Program> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        fullEpgCache[streamId]?.let { (until, list) -> if (until > now) return@withContext list.filter { it.end > now } }
        val a = account as? Account.Xtream ?: return@withContext emptyList()
        val svc = api ?: return@withContext emptyList()
        var list = try {
            epgLimiter.withPermit { parsePrograms(svc.epgTable(a.user, a.pass, streamId).listings) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        if (list.none { it.end > now }) {
            // Some panels don't support the full table; ask for a long "short" list instead.
            list = try {
                epgLimiter.withPermit { parsePrograms(svc.shortEpg(a.user, a.pass, streamId, 100).listings) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
        fullEpgCache[streamId] = (now + 30 * 60_000) to list
        list.filter { it.end > now }
    }

    private fun parsePrograms(el: com.google.gson.JsonElement?): List<Program> {
        if (el == null || el.isJsonNull) return emptyList()
        val objects = when {
            el.isJsonArray -> el.asJsonArray.filter { it.isJsonObject }
            el.isJsonObject -> el.asJsonObject.entrySet().map { it.value }.filter { it.isJsonObject }
            else -> emptyList()
        }
        return objects.mapNotNull { o ->
            val item = try { ApiClient.gson.fromJson(o, XtEpgItem::class.java) } catch (e: Exception) { null }
                ?: return@mapNotNull null
            val start = item.startTs?.trim()?.toLongOrNull()?.times(1000) ?: parseEpgDate(item.start)
            val end = item.stopTs?.trim()?.toLongOrNull()?.times(1000) ?: parseEpgDate(item.end)
            if (start == null || end == null || end <= start) return@mapNotNull null
            Program(
                title = decodeEpgText(item.title).ifBlank { "No information" },
                description = decodeEpgText(item.description),
                start = start,
                end = end
            )
        }
            .distinctBy { it.start }
            .sortedBy { it.start }
    }

    private fun parseEpgDate(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        return try {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).parse(s.trim())?.time
        } catch (e: Exception) {
            null
        }
    }

    private val base64Like = Regex("^[A-Za-z0-9+/=\\s]+$")

    /** Xtream sends EPG titles base64-encoded; some panels send plain text. Handle both. */
    private fun decodeEpgText(s: String?): String {
        if (s.isNullOrBlank()) return ""
        val t = s.trim()
        if (!base64Like.matches(t)) return t
        return try {
            val decoded = String(android.util.Base64.decode(t, android.util.Base64.DEFAULT), Charsets.UTF_8).trim()
            val looksBroken = decoded.isEmpty() || decoded.any { it == '\uFFFD' || (it.isISOControl() && it != '\n' && it != '\r' && it != '\t') }
            if (looksBroken) t else decoded
        } catch (e: IllegalArgumentException) {
            t
        }
    }

    // ----------  Stream URLs  ----------

    /** Usernames/passwords can contain characters that are not valid in a URL path. */
    private fun enc(s: String): String = android.net.Uri.encode(s)

    fun liveUrl(streamId: Int): String {
        val a = account as? Account.Xtream ?: return ""
        return "${a.host}/live/${enc(a.user)}/${enc(a.pass)}/$streamId.ts"
    }

    fun movieUrl(streamId: Int, ext: String?): String {
        val a = account as? Account.Xtream ?: return ""
        return "${a.host}/movie/${enc(a.user)}/${enc(a.pass)}/$streamId.${ext?.ifBlank { null } ?: "mp4"}"
    }

    fun episodeUrl(episode: Episode): String {
        val a = account as? Account.Xtream ?: return ""
        return "${a.host}/series/${enc(a.user)}/${enc(a.pass)}/${episode.id}.${episode.ext}"
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
