package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.core.playback.PlaybackService
import com.example.ui.library.VideoLibraryScreen
import com.example.ui.player.PlayerScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        FoxPlayerHome()
      }
    }
  }
}

@Composable
fun FoxPlayerHome(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  var isPlayerOpen by rememberSaveable { mutableStateOf(false) }

  if (isPlayerOpen) {
    PlayerScreen(
      modifier = modifier,
      onBack = {
        isPlayerOpen = false
      }
    )
  } else {
    VideoLibraryScreen(
      modifier = modifier,
      onVideoClick = { videoItem ->
        PlaybackService.startPlayback(context, videoItem)
        isPlayerOpen = true
      }
    )
  }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
  MyApplicationTheme {
    Greeting("Android")
  }
}
