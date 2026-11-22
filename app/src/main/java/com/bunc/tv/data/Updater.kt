package com.bunc.tv.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.bunc.tv.BuildConfig
import com.bunc.tv.data.remote.ApiClient
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/**
 * Checks the GitHub releases of the app's repository for a newer build, downloads it
 * and hands it to Android's installer. Builds are numbered by CI (versionCode = run number,
 * tag v1.0.<run>), so a higher number means a newer build.
 */
object Updater {
    private const val LATEST = "https://api.github.com/repos/bunc1112/iptv/releases/latest"

    data class Release(val code: Int, val name: String, val apkUrl: String, val notes: String)

    sealed class State {
        object Idle : State()
        object Checking : State()
        object UpToDate : State()
        class Available(val release: Release) : State()
        class Downloading(val release: Release, val progress: Float) : State()
        class Failed(val message: String) : State()
    }

    var state by mutableStateOf<State>(State.Idle)
        private set
    /** Show the update dialog (set by an automatic check that found something, or a manual check). */
    var showDialog by mutableStateOf(false)
    private var checkedThisRun = false

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    /** Automatic check, once per app start; only bothers the user if an update exists. */
    suspend fun autoCheck() {
        if (checkedThisRun) return
        checkedThisRun = true
        check(manual = false)
    }

    suspend fun check(manual: Boolean) {
        if (state is State.Downloading) return
        state = State.Checking
        if (manual) showDialog = true
        state = try {
            val r = fetchLatest()
            if (r != null && r.code > BuildConfig.VERSION_CODE) {
                showDialog = true
                State.Available(r)
            } else State.UpToDate
        } catch (e: Exception) {
            if (manual) State.Failed("Couldn't check for updates. Check your internet connection.") else State.Idle
        }
        if (!manual && state !is State.Available) showDialog = false
    }

    private suspend fun fetchLatest(): Release? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(LATEST).header("Accept", "application/vnd.github+json").build()
        ApiClient.okHttp().newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            val o = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
            val tag = o.get("tag_name")?.asString.orEmpty()               // v1.0.14
            val code = tag.substringAfterLast('.').toIntOrNull() ?: return@use null
            val apk = o.getAsJsonArray("assets")
                ?.map { it.asJsonObject }
                ?.firstOrNull { it.get("name")?.asString?.endsWith(".apk", true) == true }
                ?.get("browser_download_url")?.asString ?: return@use null
            val notes = o.get("body")?.takeIf { !it.isJsonNull }?.asString.orEmpty().trim()
            Release(code, tag.removePrefix("v"), apk, notes)
        }
    }

    /** Download the release and open the system installer. */
    suspend fun downloadAndInstall(context: Context, release: Release) {
        val app = context.applicationContext
        // Android 8+: the user must allow Bunc to install apps once.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !app.packageManager.canRequestPackageInstalls()) {
            state = State.Failed("Allow Bunc to install updates, then choose Update again.")
            val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { app.startActivity(i) }
            state = State.Available(release)
            return
        }
        state = State.Downloading(release, 0f)
        val file = try {
            download(app, release)
        } catch (e: Exception) {
            state = State.Failed("Download failed. Check your internet connection and try again.")
            return
        }
        state = State.Available(release)
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.updates", file)
        val install = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            app.startActivity(install)
        } catch (e: Exception) {
            state = State.Failed("Couldn't open the installer on this device.")
        }
    }

    private suspend fun download(context: Context, release: Release): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "Bunc-${release.name}.apk")
        val req = Request.Builder().url(release.apkUrl).build()
        ApiClient.okHttp().newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            val body = resp.body ?: throw IllegalStateException("empty")
            val total = body.contentLength()
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastShown = 0f
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val p = done.toFloat() / total
                            if (p - lastShown >= 0.01f) {
                                lastShown = p
                                withContext(Dispatchers.Main) { state = State.Downloading(release, p) }
                            }
                        }
                    }
                }
            }
        }
        out
    }

    fun dismiss() {
        showDialog = false
        if (state !is State.Downloading) state = State.Idle
    }
}
