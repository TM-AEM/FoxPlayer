package com.example

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
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
import com.example.ui.player.PipHelper
import com.example.ui.player.PlayerScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      window.attributes.layoutInDisplayCutoutMode =
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
    setContent {
      MyApplicationTheme {
        FoxPlayerHome()
      }
    }
  }

  override fun onUserLeaveHint() {
    super.onUserLeaveHint()
    // If a video is currently actively playing, seamlessly transition into PiP mode
    val engine = PlaybackService.currentEngine
    val isPlaying = engine?.player?.isPlaying ?: false
    if (isPlaying && PipHelper.isPipSupported(this)) {
      val videoSize = engine?.player?.videoSize
      val aspect = if (videoSize != null && videoSize.width > 0 && videoSize.height > 0) {
        val pixelRatio = if (videoSize.pixelWidthHeightRatio > 0f) videoSize.pixelWidthHeightRatio else 1.0f
        (videoSize.width.toFloat() * pixelRatio) / videoSize.height.toFloat()
      } else {
        16f / 9f
      }
      PipHelper.enterPip(this, aspect)
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
