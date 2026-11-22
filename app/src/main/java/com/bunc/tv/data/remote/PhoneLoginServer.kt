package com.bunc.tv.data.remote

import android.os.Handler
import android.os.Looper
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom

/** Login details typed on the phone. */
data class PhoneLogin(
    val mode: String,     // "xtream" | "m3u"
    val name: String,
    val host: String,
    val user: String,
    val pass: String,
    val m3u: String
)

/**
 * A tiny web server on the local network: the TV shows a QR code with
 * http://<tv-ip>:<port>/?t=<code>, the phone opens a form there, and the
 * details are sent back to the TV. It only runs while the QR screen is open,
 * and only accepts requests carrying the one-time code from the QR.
 */
class PhoneLoginServer(private val onLogin: (PhoneLogin) -> Unit) {

    val token: String = run {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val rnd = SecureRandom()
        (1..8).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
    }

    @Volatile private var server: ServerSocket? = null
    private val main = Handler(Looper.getMainLooper())

    /** Starts the server and returns the address to put in the QR code, or null if not on a network. */
    fun start(): String? {
        val ip = lanAddress() ?: return null
        val ss = try {
            ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(8765)) }
        } catch (e: Exception) {
            try { ServerSocket(0) } catch (e2: Exception) { return null }
        }
        server = ss
        Thread({
            while (!ss.isClosed) {
                val s = try { ss.accept() } catch (e: Exception) { break }
                Thread({ handle(s) }, "bunc-phone-login-client").start()
            }
        }, "bunc-phone-login").apply { isDaemon = true }.start()
        return "http://$ip:${ss.localPort}/?t=$token"
    }

    fun stop() {
        try { server?.close() } catch (_: Exception) {}
        server = null
    }

    private fun handle(socket: Socket) {
        socket.use { s ->
            try {
                s.soTimeout = 10_000
                val input = BufferedInputStream(s.getInputStream())
                val requestLine = readLine(input) ?: return
                var contentLength = 0
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    val idx = line.indexOf(':')
                    if (idx > 0 && line.substring(0, idx).trim().equals("Content-Length", true)) {
                        contentLength = line.substring(idx + 1).trim().toIntOrNull()?.coerceIn(0, 16_384) ?: 0
                    }
                }
                val parts = requestLine.split(" ")
                val method = parts.getOrNull(0) ?: "GET"
                val target = parts.getOrNull(1) ?: "/"
                val path = target.substringBefore('?')
                val query = parseForm(target.substringAfter('?', ""))

                if (method == "POST" && path == "/submit") {
                    val body = ByteArray(contentLength)
                    var read = 0
                    while (read < contentLength) {
                        val n = input.read(body, read, contentLength - read)
                        if (n < 0) break
                        read += n
                    }
                    val form = parseForm(String(body, 0, read, Charsets.UTF_8))
                    if (form["t"] != token) {
                        respond(s, 403, page("This code has expired. Scan the QR code on the TV again."))
                        return
                    }
                    val login = PhoneLogin(
                        mode = if (form["mode"] == "m3u") "m3u" else "xtream",
                        name = form["name"].orEmpty().trim(),
                        host = form["host"].orEmpty().trim(),
                        user = form["user"].orEmpty().trim(),
                        pass = form["pass"].orEmpty(),
                        m3u = form["m3u"].orEmpty().trim()
                    )
                    respond(s, 200, page("<b>Sent!</b><br>Look at your TV — Bunc is connecting."))
                    main.post { onLogin(login) }
                } else if (method == "GET" && path == "/") {
                    if (query["t"] != token) {
                        respond(s, 403, page("This code has expired. Scan the QR code on the TV again."))
                    } else {
                        respond(s, 200, formPage())
                    }
                } else {
                    respond(s, 404, page("Not found"))
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun readLine(input: BufferedInputStream): String? {
        val out = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (out.size() == 0) null else out.toString("UTF-8")
            if (b == '\n'.code) break
            if (b != '\r'.code) out.write(b)
            if (out.size() > 8_192) break
        }
        return out.toString("UTF-8")
    }

    private fun parseForm(s: String): Map<String, String> =
        s.split('&').filter { it.isNotEmpty() }.associate { kv ->
            val k = kv.substringBefore('=')
            val v = kv.substringAfter('=', "")
            fun dec(x: String) = try { URLDecoder.decode(x, "UTF-8") } catch (e: Exception) { x }
            dec(k) to dec(v)
        }

    private fun respond(s: Socket, code: Int, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        val status = when (code) { 200 -> "OK"; 403 -> "Forbidden"; else -> "Not Found" }
        val head = "HTTP/1.1 $code $status\r\n" +
            "Content-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        val out = s.getOutputStream()
        out.write(head.toByteArray(Charsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    private val style = """
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <style>
          body{margin:0;background:#0A1130;color:#EAF0FF;font-family:system-ui,-apple-system,Roboto,sans-serif}
          .w{max-width:440px;margin:0 auto;padding:28px 18px}
          h1{margin:0 0 4px;font-size:30px;background:linear-gradient(90deg,#FFB63C,#5FCDFF,#FF8A3C);-webkit-background-clip:text;background-clip:text;color:transparent}
          p{color:#9FB0D0;margin:0 0 18px}
          label{display:block;font-size:14px;color:#9FB0D0;margin:14px 0 6px}
          input{width:100%;box-sizing:border-box;padding:14px;border-radius:10px;border:1px solid #2a3866;background:#121C3C;color:#EAF0FF;font-size:17px}
          input:focus{outline:none;border-color:#FFB63C}
          .tabs{display:flex;gap:8px;margin:8px 0}
          .tabs label{flex:1;margin:0;text-align:center;padding:12px;border-radius:10px;background:#121C3C;color:#EAF0FF;font-weight:600}
          .tabs input{display:none}
          .tabs input:checked+span{color:#0A1130}
          .tabs label:has(input:checked){background:#FFB63C;color:#0A1130}
          button{width:100%;margin-top:22px;padding:16px;border:0;border-radius:12px;background:#FFB63C;color:#0A1130;font-size:18px;font-weight:700}
          .m{font-size:20px;text-align:center;margin-top:40px;line-height:1.6}
        </style>"""

    private fun page(msg: String) =
        "<!doctype html><html><head><meta charset=utf-8><title>Bunc</title>$style</head>" +
            "<body><div class=w><h1>Bunc</h1><div class=m>$msg</div></div></body></html>"

    private fun formPage() = """<!doctype html><html><head><meta charset=utf-8><title>Bunc login</title>$style</head>
<body><div class=w>
<h1>Bunc</h1><p>Type your login here — it will appear on the TV.</p>
<form method=post action="/submit">
<input type=hidden name=t value="$token">
<div class=tabs>
 <label><input type=radio name=mode value=xtream checked onchange="sw()"><span>Xtream</span></label>
 <label><input type=radio name=mode value=m3u onchange="sw()"><span>M3U</span></label>
</div>
<label>Profile name (optional)</label><input name=name autocomplete=off>
<div id=x>
<label>Server URL</label><input name=host inputmode=url placeholder="http://host:port" autocapitalize=off autocorrect=off>
<label>Username</label><input name=user autocapitalize=off autocorrect=off autocomplete=off>
<label>Password</label><input name=pass type=password autocomplete=off>
</div>
<div id=p style="display:none">
<label>M3U playlist URL</label><input name=m3u inputmode=url autocapitalize=off autocorrect=off>
</div>
<button type=submit>Send to TV</button>
</form></div>
<script>function sw(){var m=document.querySelector('input[name=mode]:checked').value;
document.getElementById('x').style.display=m=='m3u'?'none':'';document.getElementById('p').style.display=m=='m3u'?'':'none';}</script>
</body></html>"""

    companion object {
        /** This device's IPv4 address on the local network (Wi-Fi or Ethernet). */
        fun lanAddress(): String? = try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("eth")) 0 else 1 }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress }
                ?.hostAddress
        } catch (e: Exception) {
            null
        }
    }
}
