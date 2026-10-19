package com.example

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import com.example.core.model.AppLanguage
import com.example.core.model.AppSettings
import com.example.core.playback.PlaybackService
import com.example.data.repository.AppSettingsRepositoryImpl
import com.example.ui.library.VideoLibraryScreen
import com.example.ui.player.PipHelper
import com.example.ui.player.PlayerScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

enum class ScreenState {
  LIBRARY,
  PLAYER,
  SETTINGS
}

class MainActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      window.attributes.layoutInDisplayCutoutMode =
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    setContent {
      val context = LocalContext.current
      val settingsRepository = remember { AppSettingsRepositoryImpl.getInstance(context) }
      val settings by settingsRepository.appSettingsFlow.collectAsState(initial = AppSettings())

      val isRtl = (resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) ||
        (settings.language == AppLanguage.ARABIC)
      val layoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

      CompositionLocalProvider(
        LocalLayoutDirection provides layoutDirection
      ) {
        MyApplicationTheme(appTheme = settings.theme) {
          FoxPlayerHome()
        }
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
  var currentScreen by rememberSaveable { mutableStateOf(ScreenState.LIBRARY) }
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val playbackFailedMessage = stringResource(R.string.player_error_playback_start_failed)
  val retryLabel = stringResource(R.string.library_retry)

  Scaffold(
    modifier = modifier.fillMaxSize(),
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
  ) { contentPadding ->
    val screenModifier = Modifier.padding(contentPadding)
    when (currentScreen) {
      ScreenState.LIBRARY -> {
        VideoLibraryScreen(
          modifier = screenModifier,
          onVideoClick = { videoItem ->
            val started = PlaybackService.startPlayback(context, videoItem)
            if (started) {
              currentScreen = ScreenState.PLAYER
            } else {
              scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                val result = snackbarHostState.showSnackbar(
                  message = playbackFailedMessage,
                  actionLabel = retryLabel,
                  duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                  val retried = PlaybackService.startPlayback(context, videoItem)
                  if (retried) {
                    currentScreen = ScreenState.PLAYER
                  }
                }
              }
            }
          },
          onSettingsClick = {
            currentScreen = ScreenState.SETTINGS
          }
        )
      }

      ScreenState.PLAYER -> {
        BackHandler {
          currentScreen = ScreenState.LIBRARY
        }
        PlayerScreen(
          modifier = screenModifier,
          onBack = {
            currentScreen = ScreenState.LIBRARY
          }
        )
      }

      ScreenState.SETTINGS -> {
        BackHandler {
          currentScreen = ScreenState.LIBRARY
        }
        SettingsScreen(
          modifier = screenModifier,
          onBack = {
            currentScreen = ScreenState.LIBRARY
          }
        )
      }
    }
  }
}
