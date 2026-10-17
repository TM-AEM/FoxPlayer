package com.example

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.res.Configuration
import android.text.TextUtils
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.core.model.AppLanguage
import com.example.core.model.AppSettings
import com.example.data.repository.AppSettingsRepositoryImpl
import com.example.ui.settings.SettingsScreen
import java.util.Locale
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

enum class ScreenState {
  LIBRARY,
  PLAYER,
  SETTINGS
}

class MainActivity : ComponentActivity() {

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

      val localizedContext = remember(context, settings.language) {
        if (settings.language == AppLanguage.SYSTEM) {
          context
        } else {
          val locale = Locale(settings.language.code)
          val config = Configuration(context.resources.configuration)
          config.setLocale(locale)
          config.setLayoutDirection(locale)
          context.createConfigurationContext(config)
        }
      }

      val layoutDirection = when (settings.language) {
        AppLanguage.ARABIC -> LayoutDirection.Rtl
        AppLanguage.ENGLISH -> LayoutDirection.Ltr
        AppLanguage.SYSTEM -> {
          if (TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL) {
            LayoutDirection.Rtl
          } else {
            LayoutDirection.Ltr
          }
        }
      }

      CompositionLocalProvider(
        LocalContext provides localizedContext,
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

  when (currentScreen) {
    ScreenState.LIBRARY -> {
      VideoLibraryScreen(
        modifier = modifier,
        onVideoClick = { videoItem ->
          PlaybackService.startPlayback(context, videoItem)
          currentScreen = ScreenState.PLAYER
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
        modifier = modifier,
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
        modifier = modifier,
        onBack = {
          currentScreen = ScreenState.LIBRARY
        }
      )
    }
  }
}
