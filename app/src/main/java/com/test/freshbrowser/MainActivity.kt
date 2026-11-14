package com.test.freshbrowser

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.UUID
import kotlin.random.Random

/**
 * Test browser: every "New user" tap wipes all browser state and
 * starts a fresh identity, so your app sees a brand-new visitor.
 *
 * NOTE: a browser cannot change the device MAC address, and servers
 * never see the MAC anyway (it stops at your Wi-Fi router).
 * What a server CAN see is reset here instead:
 *   - cookies, localStorage, sessionStorage, IndexedDB, cache
 *   - User-Agent string (randomized)
 *   - Accept-Language (randomized)
 *   - an X-Test-User-Id header you can read on your server for logging
 * The public IP is NOT changed (use mobile data toggle / VPN for that).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var urlBox: EditText
    private lateinit var info: TextView

    private var testUserId = ""
    private var userAgent = ""
    private var language = ""

    private val androidVersions = listOf("10", "11", "12", "13", "14", "15")
    private val devices = listOf(
        "SM-G991B", "SM-S918B", "SM-A546B", "Pixel 6", "Pixel 7", "Pixel 8 Pro",
        "M2101K6G", "CPH2451", "RMX3700", "moto g84 5G", "2201117TY", "SM-A145R"
    )
    private val languages = listOf(
        "en-US,en;q=0.9", "hr-HR,hr;q=0.9,en;q=0.8", "de-DE,de;q=0.9,en;q=0.7",
        "en-GB,en;q=0.9", "it-IT,it;q=0.9,en;q=0.6", "sl-SI,sl;q=0.9,en;q=0.8"
    )

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        web = findViewById(R.id.web)
        urlBox = findViewById(R.id.url)
        info = findViewById(R.id.info)

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                if (r.isForMainFrame && (r.url.scheme == "http" || r.url.scheme == "https")) {
                    load(r.url.toString()); return true
                }
                return false
            }
            override fun onPageFinished(v: WebView, url: String) { urlBox.setText(url) }
        }

        findViewById<Button>(R.id.go).setOnClickListener { load(urlBox.text.toString()) }
        urlBox.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_GO) { load(urlBox.text.toString()); true } else false
        }
        findViewById<Button>(R.id.newId).setOnClickListener { newIdentity(reload = true) }

        newIdentity(reload = false)
    }

    private fun newIdentity(reload: Boolean) {
        // 1. Wipe every kind of stored state
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        web.clearCache(true)
        web.clearHistory()
        web.clearFormData()
        WebViewDatabase.getInstance(this).clearHttpAuthUsernamePassword()

        // 2. Random identity
        testUserId = UUID.randomUUID().toString().take(8)
        val chrome = "${Random.nextInt(124, 131)}.0.${Random.nextInt(6000, 6800)}.${Random.nextInt(50, 200)}"
        userAgent = "Mozilla/5.0 (Linux; Android ${androidVersions.random()}; ${devices.random()}) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome Mobile Safari/537.36"
        language = languages.random()
        web.settings.userAgentString = userAgent

        info.text = "Test user #$testUserId  |  $language\n$userAgent"

        if (reload) {
            val current = urlBox.text.toString()
            if (current.isNotBlank()) load(current)
        }
    }

    private fun load(raw: String) {
        var url = raw.trim()
        if (url.isEmpty()) return
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        web.loadUrl(url, mapOf(
            "Accept-Language" to language,
            "X-Test-User-Id" to testUserId
        ))
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
