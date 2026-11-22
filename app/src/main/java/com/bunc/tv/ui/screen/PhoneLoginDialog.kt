package com.bunc.tv.ui.screen

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bunc.tv.data.remote.PhoneLogin
import com.bunc.tv.data.remote.PhoneLoginServer
import com.bunc.tv.ui.theme.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

private fun qrBitmap(text: String, size: Int = 512): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { i ->
        if (matrix[i % size, i / size]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

/** Shows a QR code; the phone opens a form and the typed login comes back via [onLogin]. */
@Composable
fun PhoneLoginDialog(onLogin: (PhoneLogin) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val server = PhoneLoginServer { login -> onLogin(login) }
        url = server.start()
        failed = url == null
        onDispose { server.stop() }
    }

    val qr = remember(url) { url?.let { runCatching { qrBitmap(it) }.getOrNull() } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .padding(24.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(BuncNavy)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Log in with your phone", color = BuncText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            if (failed) {
                Text(
                    "This device isn't connected to a Wi-Fi or wired network, so the phone can't reach it.",
                    color = BuncTextDim, textAlign = TextAlign.Center
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(220.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        qr?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "QR code",
                                filterQuality = FilterQuality.None,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.widthIn(max = 380.dp)) {
                        Step("1", "Connect your phone to the same Wi-Fi as this TV.")
                        Step("2", "Scan the QR code with the phone camera.")
                        Step("3", "Type the server, username and password and tap “Send to TV”.")
                        Spacer(Modifier.height(10.dp))
                        Text("Or open in the phone's browser:", color = BuncTextDim, fontSize = 13.sp)
                        Text(url ?: "…", color = BuncGold, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = BuncNavyTop, contentColor = BuncText)
            ) { Text("Cancel") }
        }
    }
}

@Composable
private fun Step(n: String, text: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text(n, color = BuncNavyDeep, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(BuncGold).padding(horizontal = 8.dp, vertical = 2.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = BuncText, fontSize = 15.sp)
    }
}
