package com.bunc.tv.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.bunc.tv.R
import com.bunc.tv.ui.theme.BuncGold
import com.bunc.tv.ui.theme.BuncNavyDeep
import androidx.compose.foundation.background

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(BuncNavyDeep),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Image(
                painter = painterResource(R.drawable.bunc_wordmark),
                contentDescription = "Bunc",
                modifier = Modifier.width(220.dp)
            )
            Box(Modifier.height(28.dp))
            CircularProgressIndicator(color = BuncGold)
        }
    }
}
