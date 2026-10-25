# FoxPlayer — تقرير المرحلة 0 والمرحلة 1

**نوع المستند:** تقرير خط أساس + تدقيق معماري  
**تاريخ التنفيذ:** 2026-10-23  
**النطاق المنفذ:** المرحلة 0 (تثبيت نقطة البداية) والمرحلة 1 (التدقيق المعماري) فقط  
**حالة الكود:** لم يُعدَّل أي ملف مصدر أو مورد أو اختبار في هذه الخطوة.

---

## 0. الملخص التنفيذي

- النسخة المرجعية: `8c89c21168db91b3de77451972dc1ce71df629e8` على الفرع الحالي `freebuff/4d154a0662795ea019ef2620`، وشجرة العمل نظيفة قبل البدء.
- البناء والاختبارات قابلة لإعادة الإنتاج في بيئة JVM: **223 اختبارًا نجحت، 0 فشل، 0 خطأ، 0 تخطٍّ**، واختبار لقطة الشاشة (Roborazzi) نجح، وLint اكتمل بـ **60 تحذيرًا و0 أخطاء**، وAPK التصحيح بُني بنجاح (15 MB) بعد إنشاء `debug.keystore` المطلوب.
- لم تُشغَّل اختبارات Android instrumentation لعدم توفر جهاز أو Emulator، ولا يوجد سوى اختبار instrumentation تجريبي واحد في المستودع.
- أهم مشكلات المعمارية: **انقسام عقد التشغيل بين `PlayerEngine` و`Player` المباشر**، و**دورات اعتماد بين `ui` و`data` و`core`**، و**تمركز مسؤوليات `PlayerScreen` (783 سطرًا)**، و**تسرب كيانات Room إلى الواجهة**، و**تكرار فعلي في محرك التشغيل و`MediaRepository` مع اختلاف سلوكي**.
- التوصية: البقاء داخل وحدة `:app` حاليًا، والبدء بالمرحلة 2 (اختبارات حماية) قبل أي نقل. لا تُنشأ Gradle modules الآن.

---

## 1. نطاق هذه الخطوة وما لم يتم

تم تنفيذ ما تطلبه القسم «أول مهمة تنفيذية»: فحص المستودع الفعلي، تسجيل نقطة البداية، تشغيل ما يمكن تشغيله من فحوص، ثم إنتاج خريطة مسؤوليات واعتماديات وقائمة مخاطر وخطة نقل.

لم يتم:

- نقل أي ملف أو حزمة.
- تغيير أي سلوك أو تصميم أو نص.
- إضافة ميزات أو حذف كود أو موارد.
- إنشاء commits أو دفع أي شيء إلى GitHub.
- تشغيل اختبارات جهاز/Emulator أو اختبارات يدوية للـ Surface أو PiP.

---

## 2. المرحلة 0 — خط الأساس

### 2.1 حالة Git

| العنصر | القيمة |
|---|---|
| الفرع الحالي | `freebuff/4d154a0662795ea019ef2620` |
| HEAD | `8c89c21168db91b3de77451972dc1ce71df629e8` |
| رسالة HEAD | `test: add regression tests for playback stability` |
| `git status` قبل البدء | نظيف (`--porcelain` فارغ) |
| `git status` بعد الفحوص | نظيف قبل كتابة التقرير؛ بعده يظهر `?? docs/` فقط (هذا الملف). مخرجات `build/` و`debug.keystore` مستثناة في `.gitignore` |
| remote | `origin` → `https://github.com/TM-AEM/FoxPlayer.git` |
| ملاحظة | لا يوجد ref محلي لـ `origin/main` في نسخة العمل هذه، ولم يُجرَ fetch؛ المرجع المعتمد هو HEAD أعلاه |

الفرع `freebuff-base` موجود لكنه ليس سلفًا لـ HEAD (تاريخهما غير مترابط)، لذا لا يُستخدم كمرجع للخط الزمني.

### 2.2 سلسلة الأدوات والإصدارات الفعلية

| المكوّن | الإصدار / القيمة | المصدر |
|---|---|---|
| JDK | Temurin 21.0.13+9 | بيئة التشغيل المثبتة للفحص |
| Gradle Wrapper | 9.3.1 | `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin | 9.1.1 | `gradle/libs.versions.toml` |
| Kotlin | 2.2.10 | `gradle/libs.versions.toml` |
| Compose BOM | 2024.09.00 | `gradle/libs.versions.toml` |
| Media3 | 1.5.1 | `gradle/libs.versions.toml` |
| Room | 2.7.0 | `gradle/libs.versions.toml` |
| DataStore Preferences | 1.1.7 | `gradle/libs.versions.toml` |
| Robolectric | 4.16.1 | `gradle/libs.versions.toml` |
| Roborazzi | 1.59.0 | `gradle/libs.versions.toml` |
| compileSdk | 36.1 | `app/build.gradle.kts` |
| minSdk / targetSdk | 24 / 36 | `app/build.gradle.kts` |
| versionName / versionCode | 1.0.0 / 2 | `version.properties` |
| Android SDK المستخدم | Platform 36.1، Build-Tools 36.1.0 و36.0.0، Platform-Tools 37.0.1 | بيئة التشغيل |

### 2.3 الأوامر والنتائج

| الأمر | النتيجة | الملاحظات |
|---|---|---|
| `sh ./gradlew :app:testDebugUnitTest --no-daemon -Dorg.gradle.jvmargs=-Xmx2g` | **نجح** — BUILD SUCCESSFUL in 4m 42s | 25 صنف اختبار، **223 اختبارًا**، 0 failure، 0 error، 0 skipped |
| `sh ./gradlew :app:verifyRoborazziDebug --no-daemon -Dorg.gradle.jvmargs=-Xmx2g` | **نجح** — BUILD SUCCESSFUL in 1m 2s | تم التحقق من اللقطة المرجعية `app/src/test/screenshots/player_hud.png` |
| `sh ./gradlew :app:lintDebug --no-daemon -Dorg.gradle.jvmargs=-Xmx2g` | **نجح** — BUILD SUCCESSFUL in 55s | **60 Warning، 0 Error** |
| `sh ./gradlew :app:assembleDebug --no-daemon -Dorg.gradle.jvmargs=-Xmx2g` | **فشل أولًا** ثم **نجح** بعد إنشاء `debug.keystore` | انظر 2.5؛ الناتج `app/build/outputs/apk/debug/app-debug.apk` بحجم 15 MB |
| اختبارات instrumentation (`connectedDebugAndroidTest`) | **لم تُشغَّل** | لا يوجد جهاز/Emulator؛ لا يوجد سوى `ExampleInstrumentedTest` |

توزيع الاختبارات الناجحة يشمل المجالات الحرجة التالية:

| المجال | أمثلة على الأصناف | عدد الاختبارات |
|---|---|---|
| حالة التشغيل ومصدرها | `ActivePlaybackSourceTest`، `ControllerLifecycleTest`، `PlayerScreenPlaybackSourceTest` | 8 + 5 + 13 |
| الخدمة والمحرك | `PlaybackServiceTest`، `FoxPlayerEngineTest` | 7 + 14 |
| واجهة المشغل والإيماءات | `PlayerScreenTest`، `PlayerGestureTest`، `PlayerVolumeGestureRegressionTest`، `PlayerHudTest`، `PlayerHudScreenshotTest` | 8 + 15 + 13 + 6 + 1 |
| المكتبة والوسائط والصلاحيات | `VideoLibraryViewModelTest`، `MediaRepositoryTest`، `PermissionMatrixTest` | 6 + 9 + 42 |
| الإعدادات واللغة | `AppSettingsTest`، `GestureSettingsPersistenceTest`، `LocaleIntegrationTest` | 7 + 11 + 9 |
| Snackbar | `MainActivitySnackbarTest`، `MainActivitySnackbarLocaleTest` | 16 + 1 |
| أخرى | قاعدة البيانات، الصور المصغرة، التنسيق، PiP، أمثلة القالب | 3 + 6 + 6 + 5 + 3 |

### 2.4 تحذيرات Lint المسجلة كخط أساس

إجمالي 60 تحذيرًا، منها:

- `GradleDependency` (25) و`NewerVersionAvailable` (13) و`AndroidGradlePluginVersion` (2): تحديثات إصدارات متاحة. **لم تُحدَّث** لأن الخطة تمنع ترقية الاعتماديات أثناء إعادة الهيكلة.
- `UnusedResources` (11): ألوان القالب (`purple_*`، `teal_*`، `black`، `white`) ونصوص غير مستخدمة (`library_title`، `player_speed`، `player_gestures`، `player_track_default`). لا تُحذف الآن بلا دليل استخدام.
- `UseKtx` (3): استخدام `String.toUri` في المحرك والمكتبة والصور المصغرة.
- `PictureInPictureIssue` (1): النشاط يدعم PiP و`targetSdk >= 31`، ويوصي Lint باستدعاء `setAutoEnterEnabled(true)` و`setSourceRectHint(...)`. الثانية غير موجودة حاليًا.
- `ExportedService` (1): خدمة التشغيل `exported=true` بلا permission؛ سلوك MediaSessionService مقصود لكنه يستحق توثيقًا أمنيًا.
- `IconDipSize` (2): تفاوت كثافات أيقونات التطبيق.
- `UnusedAttribute` (1)، `RedundantLabel` (1).

هذه التحذيرات ليست أخطاء بناء، لكنها جزء من خط الأساس حتى يمكن تمييز أي تحذير جديد لاحقًا.

### 2.5 فجوات إعادة الإنتاج المكتشفة

1. **`gradlew` غير قابل للتنفيذ في Git:** الملف متتبع بالوضع `100644`، لذا `./gradlew` يفشل بـ `Permission denied` بعد checkout نظيف. CI يعالجه عبر `chmod +x ./gradlew`، لكن README يطلب `./gradlew` مباشرة. استُخدم `sh ./gradlew` في هذا الفحص.
2. **`debug.keystore` مطلوب لكنه غير موجود:** `app/build.gradle.kts` يضبط `signingConfigs.debugConfig` على `debug.keystore` في جذر المشروع، والملف مستثنى في `.gitignore`. `assembleDebug` فشل أولًا بـ:
   `Keystore file '.../debug.keystore' not found for signing config 'debugConfig'`
   ثم نجح بعد إنشائه بنفس أمر CI. README لا يذكر هذه الخطوة.
3. **`androidTest` شكلي:** لا يوجد سوى اختبار القالب `ExampleInstrumentedTest`؛ لا توجد تغطية instrumentation فعلية لـ Surface أو PiP أو دورة حياة الخدمة.
4. **ذاكرة البيئة:** `gradle.properties` يطلب `-Xmx4g`، وبيئة الفحص 3 GB؛ استُخدم `-Dorg.gradle.jvmargs=-Xmx2g` كتجاوز بيئي فقط، وليس تغييرًا في المشروع.

هذه الفجوات لا تمنع إعادة إنتاج اختبارات JVM، لكنها تُوثَّق هنا ولا تُصلَح في مرحلتي 0/1.

---

## 3. المرحلة 1 — التدقيق المعماري

### 3.1 حجم الكود وتوزيعه

| القياس | القيمة |
|---|---|
| ملفات Kotlin في `main` | 42 |
| أسطر Kotlin في `main` | 6,790 |
| ملفات Kotlin في `test` | 25 |
| أسطر Kotlin في `test` | 6,061 |
| اختبارات JVM | 223 في 25 صنفًا |
| ملفات `main` أكبر من 300 سطر | 7 ملفات |

أكبر الملفات:

| الملف | الأسطر | المسؤولية الفعلية |
|---|---:|---|
| `ui/player/PlayerScreen.kt` | 783 | تركيب الشاشة + اتصال MediaController + مصدر الحالة + Surface + الإيماءات + HUD + PiP + إعدادات الإيماءات + المسارات + السرعة + شريط النظام |
| `ui/library/VideoLibraryScreen.kt` | 596 | شريط علوي + بحث + فرز + حالات الصلاحية/التحميل/الخطأ/الفراغ + قائمة + كرت الفيديو مع سجل المشاهدة |
| `ui/player/PlayerSettingsSheet.kt` | 582 | لوحة إعدادات المشغل: السرعة، النسبة، الصوت، الترجمة، حساسية الإيماءات |
| `core/playback/FoxPlayerEngine.kt` | 452 | ExoPlayer + الاستئناف + حالة التشغيل + كتابة سجل المشاهدة + حفظ الموضع |
| `data/repository/MediaRepository.kt` | 354 | استعلام MediaStore + ContentObserver + تحويل Cursor + تجميع المجلدات |
| `ui/settings/SettingsScreen.kt` | 346 | واجهة إعدادات اللغة/المظهر/الاستئناف |
| `ui/player/PlayerHud.kt` | 345 | طبقة HUD: شريط علوي، تشغيل/إيقاف، شريط زمني، RTL/LTR |
| `ui/player/GestureFeedbackOverlay.kt` | 291 | عرض ردود الإيماءات المرئية |
| `ui/player/PlayerTrackModels.kt` | 237 | نماذج المسارات + مساعد Media3 لاختيار الصوت/الترجمة |
| `ui/player/PlayerGestureDetector.kt` | 225 | اكتشاف النقر/السحب/الضغط الطويل |
| `core/thumbnail/ThumbnailEngine.kt` | 206 | استخراج الصور المصغرة + LruCache + منع الطلبات المكررة |
| `core/playback/PlaybackService.kt` | 205 | MediaSessionService + MediaSession + دورة حياة المحرك + أوامر الـ Controller |
| `ui/player/PlayerGestureController.kt` | 198 | الصوت النظامي والسطوع |
| `ui/library/VideoLibraryViewModel.kt` | 194 | الصلاحيات + الملاحظة + البحث + الفرز + سجل المشاهدة |
| `MainActivity.kt` | 187 | اللغة/السمة + التنقل بين ثلاث شاشات + Snackbar + PiP عند مغادرة التطبيق |

### 3.2 المسؤوليات الفعلية ومن يملكها

| المسؤولية | المالك الحالي | ملاحظة |
|---|---|---|
| تشغيل الفيديو ودورة حياة ExoPlayer | `PlaybackService` + `FoxPlayerEngine` | الخدمة تنشئ الجلسة والمحرك، والمحرك يدير ExoPlayer |
| مصدر حالة التشغيل | `FoxPlayerEngine.playbackState` | مصدر واحد للحالة الأساسية، لكن الواجهة تضيف مصادر محلية أخرى |
| أوامر التشغيل | `PlayerScreen` مباشرة على `Player` | ليس عبر `PlayerEngine`؛ انظر R1 |
| ربط Surface | `PlayerScreen` عبر `AndroidView` | يربط/يفصل السطح على `activePlayer` |
| دورة حياة MediaController | `PlayerScreen` + `ControllerLifecycle` | مصممة جيدًا ومختبرة، لكنها تعيش داخل الشاشة |
| HUD وإخفاؤه التلقائي | `PlayerScreen` + `PlayerHud` | المؤقت 3.5 ثانية داخل الشاشة |
| الإيماءات | `PlayerGestureDetector` (عرض) + `PlayerScreen` (منطق) + `PlayerGestureController` (صوت/سطوع) | المنطق موزع بين الثلاثة |
| PiP | `PipHelper` + `PlayerScreen` + `MainActivity.onUserLeaveHint` | ثلاث نقاط دخول محتملة |
| سجل المشاهدة | `FoxPlayerEngine` يكتب، `VideoLibraryViewModel` يقرأ | الكيان نفسه يصل إلى الواجهة |
| إعدادات التطبيق | `AppSettingsRepositoryImpl` (DataStore) | يقرأها `MainActivity` و`SettingsViewModel` و`FoxPlayerEngine` |
| إعدادات الإيماءات | `GestureSettingsRepositoryImpl` (DataStore) | تقرأها `PlayerScreen` وتكتبها لوحة الإعدادات |
| الصور المصغرة | `ThumbnailEngineImpl` + `VideoThumbnail` | تصميم جيد: LruCache + dedupe + إلغاء |
| المكتبة والبحث والفرز | `VideoLibraryViewModel` + `VideoLibraryScreen` | البحث والفرز في ViewModel، والعرض في الشاشة |
| المفضلة وقوائم التشغيل | DAOs وكيانات Room فقط | **ميزات نائمة**: لا repository ولا واجهة ولا ربط |

### 3.3 خريطة الاعتماديات الفعلية

استُخرجت من `import` والمراجع المؤهلة بالكامل. `com.example.R` اعتُبر حافة إلى موارد Android المولدة، وليس حزمة منطقية.

```text
com.example (MainActivity / FoxPlayerHome / ScreenState)
 ├──> ui.library ──> core.model, core.util, data.repository,
 │                   data.local.database.{FoxPlayerDatabase, HistoryDao, PlaybackHistoryEntity}
 ├──> ui.player ───> core.playback, core.model, core.util, data.repository
 ├──> ui.settings ─> core.model, data.repository
 ├──> ui.theme ────> core.model
 ├──> data.repository
 └──> core.playback

core.playback ──> core.model
              ──> data.repository (AppSettingsRepository + Impl)
              ──> data.local.database (FoxPlayerDatabase, HistoryDao, PlaybackHistoryEntity)
              ──> com.example.MainActivity (PendingIntent لنشاط الجلسة)

data.repository ──> core.model, core.util
                ──> ui.player (GestureSettingsState, GestureSensitivity, DoubleTapSeekDuration)

data.local.database ──> DAOs + Entities
core.model ──> com.example.R
core.thumbnail ──> Android APIs فقط
core.util ──> Android APIs فقط
ui.components ──> core.thumbnail, core.util, R
```

**دورات الاعتماد المؤكدة:**

1. `data.repository ↔ ui.player`: الأول يستورد أنواع إعدادات الإيماءات من الثاني (`GestureSettingsRepository.kt:11-13`)، والثاني يستخدم repository الإيماءات (`PlayerScreen.kt:84,89`).
2. `core.playback → data.repository → ui.player → core.playback`: المحرك يعتمد على إعدادات التطبيق في `data`، و`data` تعتمد على أنواع الإيماءات في `ui`، و`ui.player` يعتمد على `PlaybackService` في `core.playback`.
3. `core.playback ↔ com.example`: `PlaybackService` يستخدم `MainActivity` لبناء `PendingIntent` (`PlaybackService.kt:14,133`)، و`MainActivity` يستخدم `PlaybackService` (`MainActivity.kt:38,101,136-148`).
4. `core.model ↔ com.example` عبر `R` فقط (`AppSettings.kt:4`)، وهو اقتران بنماذج العرض أكثر منه دورة منطقية.
5. عند تضمين الحزمة الجذرية `com.example` في التحليل، تندمج كل الحزم في SCC واحدة لأن `MainActivity` تستورد كل الواجهات. هذا يعني أنه **لا توجد حدود حزم يمكن فرضها اليوم**، وليس أن كل الاعتماديات دائرية بالمعنى الدقيق.

**الخلاصة:** اتجاه الاعتماد المستهدف في الخطة (`ui → domain/core ← data`) غير موجود حاليًا؛ الموجود اتجاهات متقاطعة، وأقواها اقتران `core` بـ`data` و`data` بـ`ui`.

### 3.4 تدفق تشغيل الفيديو من الواجهة إلى المحرك

1. `MainActivity` تستقبل اختيار فيديو من `VideoLibraryScreen`، وتنادي `PlaybackService.startPlayback(context, videoItem)` (`MainActivity.kt:136-157`).
2. `startPlayback` تبني `Intent` يحمل URI والعنوان وبيانات الفيديو، وتحاول `startForegroundService` ثم `startService` كبديل، وتعيد `Boolean` (`PlaybackService.kt:69-92`).
3. `MediaSessionService` ينشئ `FoxPlayerEngine` و`MediaSession` عند `onCreate` (`PlaybackService.kt:119-185`).
4. تُنشر `currentSessionToken` قبل المحرك، ثم `publishEngine(engine)` يحدّث `currentEngine` و`currentEngineFlow` معًا (`PlaybackService.kt:179-180`).
5. `onStartCommand` تعيد بناء `VideoItem` وتنادي `playerEngine?.prepare(videoItem, playWhenReady = true)` (`PlaybackService.kt:94-117`).
6. `FoxPlayerEngine.prepare` تضع `MediaItem` و`prepare()`، ثم تقرأ سجل المشاهدة وإعداد `resumePlayback` بشكل غير متزامن وتُنفّذ `seekTo` عند الحاجة (`FoxPlayerEngine.kt:163-267`).
7. `Player.Listener` في المحرك يحدّث `PlaybackState`، ويبدأ/يوقف حلقة تحديث الموضع كل 250 ms، ويكتب سجل المشاهدة عند الإيقاف/النهاية/التحرير وبفاصل 5 ثوانٍ أثناء التشغيل (`FoxPlayerEngine.kt:96-140,407-433`).
8. `MainActivity` تنقل الشاشة إلى `PLAYER`، و`PlayerScreen` تراقب `currentEngineFlow` وتحلّ العامل النشط عبر `ActivePlaybackResolver` (`PlayerScreen.kt:113-235`).
9. `ActivePlaybackResolver` تختار `MediaController` المتصل إن وُجد، وإلا `engine.player`، وتبقي الحالة دائمًا من المحرك (`ActivePlaybackSource.kt:55-96`).
10. الشاشة تربط `SurfaceView` مباشرة بالعامل النشط (`PlayerScreen.kt:498-518`)، وتضيف مستمعي `Player` خاصين بها للمسارات والسرعة وحجم الفيديو (`PlayerScreen.kt:371-399`).
11. أوامر HUD والإيماءات تُنفّذ مباشرة على `activePlayer` (`play/pause/seekTo/setPlaybackSpeed/trackSelectionParameters`) وليس عبر `PlayerEngine`.
12. عند العودة إلى المكتبة: يُفصل السطح (`onRelease`/`clearVideoSurfaceView`)، لكن التشغيل يستمر في الخدمة. `MainActivity.onUserLeaveHint` تدخل PiP إذا كان المحرك يعرض شيئًا (`MainActivity.kt:98-112`).

### 3.5 تدفق البيانات والإعدادات

| البيانات | مصدر الحقيقة | القراءة | الكتابة | ملاحظة |
|---|---|---|---|---|
| قائمة الفيديوهات | MediaStore | `MediaRepository.getVideosFlow()` عبر ContentObserver | لا يوجد | تُحوَّل إلى `VideoItem` |
| سجل المشاهدة والموضع | Room `playback_history` | `HistoryDao.getAllHistory()` في المكتبة، و`getHistoryByUriDirect` في المحرك | `upsertHistory` من المحرك | الكيان يصل إلى `VideoLibraryUiState` والواجهة |
| المفضلة | Room `favorite_videos` | لا شيء | لا شيء | DAO + كيان فقط، بلا ميزة |
| قوائم التشغيل | Room `playlists` / `playlist_items` | لا شيء | لا شيء | DAO + كيان فقط، بلا ميزة |
| إعدادات التطبيق | DataStore `foxplayer_app_settings` | `MainActivity`، `SettingsViewModel`، المحرك | `SettingsViewModel` | المفاتيح: `app_language`، `app_theme`، `app_resume_playback` |
| إعدادات الإيماءات | DataStore `foxplayer_gesture_settings` | `PlayerScreen` | `PlayerSettingsPanel` عبر repository | المفاتيح: حساسية الصوت/السطوع، مدة النقر المزدوج (نص + عدد) |
| الصور المصغرة | ذاكرة LRU + MediaStore | `VideoThumbnail` | `ThumbnailEngine` | لا يوجد تخزين على القرص |
| لغة/سمة التطبيق | إعدادات + `AppCompatDelegate` | `MainActivity` | `SettingsViewModel` | توجد قراءة مكررة للإعدادات في `MainActivity` |

### 3.6 ملكية دورة الحياة

| المكوّن | يملك | لا يملك / يجب ألا يملك |
|---|---|---|
| `MainActivity` | اللغة، السمة، التنقل بين الشاشات، Snackbar، PiP عند مغادرة التطبيق | تشغيل الوسائط أو حالة المشغل |
| `PlaybackService` | `MediaSession`، `FoxPlayerEngine`، دورة حياة الخدمة، أوامر MediaSession | قواعد الواجهة أو التنقل |
| `PlayerScreen` | `MediaController`، Surface، HUD، PiP، الإيماءات، لوحة الإعدادات | ينبغي ألا تملك كتابة سجل المشاهدة أو سياسة الاستئناف |
| `FoxPlayerEngine` | `ExoPlayer`، حالة التشغيل، الاستئناف، كتابة السجل | لا ينبغي أن تعرف Room أو DataStore مباشرة |
| `ControllerLifecycle` | توكيد الجيل ومنع controller قديم من إزاحة أحدث | لا تملك الجلسة نفسها |
| `PlayerGestureDetector` | تفسير اللمس وإنتاج أحداث | لا يغيّر حالة التشغيل بنفسه |
| `PlayerGestureController` | صوت النظام وسطوع النافذة | لا يلمس `Player.volume` |
| `ThumbnailEngine` | الاستخراج والتخزين المؤقت ومنع التكرار | لا يعرف Compose |

**نقاط غموض الملكية الحالية:**

- Surface مملوك للشاشة، لكن Player مملوك للخدمة/المحرك؛ إعادة الربط تعتمد على إعادة التركيب.
- Speed boost مملوك للشاشة، بينما السرعة جزء من حالة التشغيل التي يفترض أن المحرك مصدرها.
- PiP له ثلاث نقاط: زر HUD، و`updateAutoEnterPip`، و`onUserLeaveHint`.
- كتابة سجل المشاهدة مملوكة للمحرك (core) رغم أنها سياسة بيانات.

---

## 4. النتائج والمخاطر مرتبة حسب الأثر

### R1 — انقسام عقد التشغيل بين `PlayerEngine` و`Player` المباشر (خطورة عالية)

**الدليل:**

- `PlayerEngine` يعرّف `play/pause/playPause/seekTo/seekForward/seekBackward/setPlaybackSpeed/setVolume/stop` (`core/playback/PlayerEngine.kt:35-50`).
- `PlayerScreen` ينفّذ الأوامر على `Player` مباشرة: `setPlaybackSpeed` (`PlayerScreen.kt:320,329,714`)، `play/pause` (`529-538`, `670-688`)، `seekTo` (`544-566`, `594-599`, `680-688`)، `trackSelectionParameters` (`729,740`)، `setVideoSurfaceView` (`508`).
- بحث المراجع يُظهر أن `playPause`، `seekForward`، `seekBackward`، `setVolume`، `stop` غير مستخدمة في `main` إطلاقًا، وباقي أوامر المحرك تُستخدم في الاختبارات فقط؛ الواجهة تستخدم Media3 `Player` مباشرة.

**الأثر:** لا توجد نقطة واحدة لتطبيق سياسة تشغيل (حفظ الموضع عند الإيقاف، قيود السرعة، تسجيل الأحداث). أي تغيير في سلوك زر HUD قد لا ينطبق على إيماءة مكافئة. كما أن `PlayerEngine` يبدو عقدًا معماريًا لكنه ليس العقد الفعلي.

**لا تغيير الآن.**

### R2 — دورات اعتماد واقتران طبقات (خطورة عالية)

**الدليل:**

- `core.playback` → `data.repository` و`data.local.database` (`FoxPlayerEngine.kt:18-20,32-33`).
- `data.repository` → `ui.player` (`GestureSettingsRepository.kt:11-13`).
- `ui.player` → `data.repository` (`PlayerScreen.kt:84,89`).
- `PlaybackService` → `MainActivity` (`PlaybackService.kt:14,133`)، و`MainActivity` → `PlaybackService` (`MainActivity.kt:38,101,136-148`).
- `core.model.AppSettings` → `R` (`AppSettings.kt:4`).
- `VideoLibraryViewModel` → `FoxPlayerDatabase`/`HistoryDao` (`VideoLibraryViewModel.kt:58-59`).

**الأثر:** لا يمكن فرض اتجاه اعتماد داخل `:app` بالحزم وحدها؛ أي إعادة تنظيم لاحقة قد تعيد إنشاء الدورة. كما أن اختبار `core` يتطلب Android/Room/DataStore.

**الترتيب المقترح للمعالجة:** أنواع إعدادات الإيماءات (المرحلة 6) → سجل المشاهدة (المرحلة 5) → إعدادات التشغيل (المرحلة 5/6) → نشاط الجلسة (المرحلة 4) → نموذج `AppSettings` (المرحلة 6).

### R3 — `PlayerScreen` مركز مسؤوليات (خطورة عالية)

**الدليل — نطاقات المسؤولية داخل الملف:**

| النطاق | الأسطر | المسؤولية |
|---|---:|---|
| إعداد شريط النظام الغامر | 93-111 | إخفاء/استعادة system bars |
| اتصال MediaController | 122-204 | بناء/تحرير controller، الاستماع للانفصال |
| حل العامل النشط | 206-235 | `ActivePlaybackResolver` |
| الإيماءات وردود الفعل | 237-340، 521-649 | swipe، double tap، volume/brightness، boost، feedback |
| HUD وإخفاؤه | 249-357 | الحالة المرئية ومؤقت 3.5s |
| PiP | 251-282، 436-438 | مستمع PiP + auto-enter |
| المسارات/السرعة/حجم الفيديو | 298-399 | مستمعو Player محليون |
| النسبة وSurface | 440-519 | حساب الأبعاد وربط SurfaceView |
| لوحة الإعدادات | 709-780 | توصيل الأحداث والتخزين |

**الأثر:** تغيير في الإيماءات قد يلمس PiP أو Surface أو HUD؛ اختبارات الشاشة واسعة لكنها لا تعزل كل مسؤولية. هذا هو أكبر عائق أمام «تعديل ميزة دون فهم التطبيق كله».

### R4 — تسرب كيان Room إلى الواجهة (خطورة متوسطة-عالية)

**الدليل:**

- `VideoLibraryViewModel` ينشئ `FoxPlayerDatabase.getInstance(application).historyDao()` مباشرة (`VideoLibraryViewModel.kt:58-59`).
- `VideoLibraryUiState.historyMap` نوعه `Map<String, PlaybackHistoryEntity>` (`:39-45`).
- `VideoLibraryScreen` يستقبل ويعرض `PlaybackHistoryEntity` مباشرة (`VideoLibraryScreen.kt:281,312`).

**الأثر:** أي تغيير في مخطط Room ينتقل إلى الواجهة؛ لا يمكن استبدال مصدر السجل في الاختبارات بسهولة؛ ولا يمكن إضافة خصائص عرض (مثل «مكتمل») دون تعديل الكيان.

### R5 — تكرار فعلي مع اختلاف سلوكي (خطورة متوسطة)

**الدليل:**

1. `FoxPlayerEngine.prepare(uri)` (`163-195`) و`prepare(videoItem)` (`197-267`) يكرران منطق الاستئناف والتحقق من الإعداد والاستماع للإلغاء. أي إصلاح يجب أن يُطبَّق مرتين.
2. `MediaRepository.queryVideosDirect()` (`104-243`) و`getVideoByUri()` (`245-333`) يكرران الإسقاط وتحويل Cursor. الفرق ليس شكليًا:
   - `queryVideosDirect` يحسب `bucketId` ويعبّئه، بينما `getVideoByUri` لا يضبط `bucketId` فيبقى القيمة الافتراضية `""` (`VideoItem.kt:19`).
   - مسار `DATA` البديل لما قبل Android Q موجود في الأول وغير موجود في الثاني، فقد يختلف `relativePath` على الأجهزة القديمة.
3. `MainActivity` تقرأ إعدادات التطبيق مرتين: مرة في `lifecycleScope` لتطبيق اللغة (`64-77`) ومرة في Compose للسمة (`81-82`).

**الأثر:** divergence صامت بين قائمة المكتبة وتفاصيل فيديو واحد؛ ومنطق استئناف غير متطابق بين مساري التشغيل.

### R6 — حالة عامة في الخدمة ونقاط PiP متعددة (خطورة متوسطة)

**الدليل:**

- `PlaybackService.currentEngine` و`currentSessionToken` و`currentEngineFlow` حالة `static`/`@Volatile` (`PlaybackService.kt:40-56`).
- `MainActivity` تستخدم `currentEngine` مباشرة (`99-101`)، بينما `PlayerScreen` تستخدم `currentEngineFlow` (`113`). هناك مقبضان لنفس الحقيقة، يُحدَّثان معًا عبر `publishEngine`.
- PiP يُدخل من `PlayerScreen` (`436-438` وزر HUD)، ومن `MainActivity.onUserLeaveHint` (`98-112`)، مع دوال مساعدة في `PipHelper` (`enterPip`، `updateAutoEnterPip`، `disableAutoEnterPip`).

**الأثر:** صعوبة عزل الاختبارات (الحالة تبقى بين الاختبارات)، واحتمال دخول PiP من شاشة المكتبة إذا كان التشغيل مستمرًا. سلوك يحتاج تأكيدًا على جهاز قبل أي تغيير.

### R7 — ميزات نائمة قد تُفهم خطأً ككود ميت (خطورة متوسطة مستقبلية)

**الدليل:**

- `FavoritesDao` و`PlaylistDao` والكيانات الأربعة مستخدمة فقط في `FoxPlayerDatabase` واختبار قاعدة البيانات؛ لا repository ولا ViewModel ولا واجهة.
- `MediaRepository.getVideoFolders()` و`VideoFolder` غير مستخدمين في `main`.
- قاعدة البيانات بالإصدار 1 وتصدّر المخطط (`exportSchema = true`).

**الأثر:** إذا بدأت المرحلة 7 (المفضلة/القوائم) دون قرار واضح، قد تُغيَّر المخططات دون ترحيل. لا تُحذف الآن؛ تُوثَّق كميزات مؤجلة.

### R8 — فجوات تغطية الاختبارات (خطورة متوسطة)

**قوي حاليًا:**

- مصدر الحالة النشط ودورة حياة Controller والتبديل بين المحركات.
- إعادة اتصال/انفصال controller، وأوامر MediaSession الأساسية.
- استئناف الموضع، حفظ السجل عند التحرير، عدم حجب الخيط الرئيسي.
- إيماءات الصوت التراكمية والسطوع، Snackbar، الصلاحيات، البحث/الفرز، الاستمرارية عبر DataStore.

**غير مغطى أو ضعيف:**

- حالات `VideoLibraryScreen` المرئية (permission/loading/error/empty/search/sort) — يغطي الاختبار ViewModel فقط.
- التنقل: Library → Settings → Back، Library → Player → Back، واستعادة الشاشة بعد إعادة إنشاء Activity.
- Speed boost عبر إعادة تركيب/تبديل العامل، وتزامن `currentSpeed` مع `PlaybackState.playbackSpeed`.
- ربط/فك Surface عند تغيير العامل أو الخروج، وإعادة الربط بعد إعادة إنشاء Activity.
- تكافؤ `prepare(uri)` و`prepare(videoItem)`، و`getVideoByUri` مقابل `queryVideosDirect`.
- `onTaskRemoved`، الإشعار، foreground service، `onConnect` بالكامل، الصوت/noisy، دورة حياة PiP.
- اختبارات جهاز حقيقية: Surface، PiP، تدوير الشاشة، موت العملية، اللمس الفعلي، أزرار الصوت.
- أداء الصور المصغرة والتمرير (لا قياس).

### R9 — فجوات بناء/توثيق (خطورة منخفضة-متوسطة)

- `gradlew` غير قابل للتنفيذ في المستودع، وREADME يفترض العكس.
- `debug.keystore` مطلوب لبناء debug وغير موثق.
- CI لا يشغّل instrumentation tests.
- Lint به 60 تحذيرًا، منها PiP وservice exported؛ لا توجد بوابة «لا تحذيرات جديدة» بعد.

### R10 — صلاحية ذاكرة الصور المصغرة (خطورة منخفضة-متوسطة)

- مفتاح الذاكرة هو `"$uri@${width}x$height"` (`ThumbnailEngine.kt:190-192`) ولا يتضمن وقت تعديل الملف أو حجمه. إذا استُبدل ملف بنفس URI، قد تُعرض صورة قديمة حتى إعادة تشغيل العملية.
- لا يوجد تخزين على القرص، ولا قياس أداء. التصميم الحالي جيد (LruCache + dedupe + إلغاء)، وأي إضافة disk cache يجب أن تسبقها قياسات.

### R11 — تحذيرات Lint القائمة (خطورة منخفضة)

مسجلة في 2.4. لا تُصلَح ضمن إعادة الهيكلة إلا إذا كانت مرتبطة مباشرة بسلوك نُقل.

---

## 5. ما يجب أن يبقى ثابتًا (قائمة ثوابت السلوك)

هذه القيم يجب أن تظل كما هي أثناء المراحل 2-8، وتُستخدم كأساس لاختبارات الحماية:

- **HUD:** يظهر افتراضيًا، يختفي تلقائيًا بعد 3500 ms أثناء التشغيل، ويظهر عند التفاعل؛ يُخفى في PiP؛ إعدادات المشغل تمنع الإخفاء.
- **ردود الإيماءات:** مدة العرض الافتراضية 1200 ms، النقر المزدوج 900 ms، سحب التقديم 1500 ms.
- **النقر المزدوج:** 5/10/15/30 ثانية، الافتراضي 10، يمين = تقديم ويسار = إرجاع.
- **السرعة:** النطاق المسموح 0.25×–4.0×، والقائمة المدعومة 0.25/0.5/0.75/1/1.25/1.5/1.75/2/2.5/3/4، والضغط الطويل يرفع إلى 2× ثم يعيد السرعة السابقة.
- **الصوت:** يعدّل `STREAM_MUSIC` ولا يلمس `Player.volume`؛ يتراكم ما دون الخطوة؛ يقرأ القيمة الفعلية بعد التطبيق؛ يحترم `isVolumeFixed`.
- **السطوع:** على مستوى نافذة Activity فقط، دون `WRITE_SETTINGS`.
- **الاستئناف:** أقل من 3 ثوانٍ → البداية؛ فيديو ≤ 15 ثانية: لا استئناف عند آخر 3 ثوانٍ أو ≥ 80%؛ فيديو > 15 ثانية: لا استئناف عند آخر 5 ثوانٍ أو ≥ 95%.
- **الصور المصغرة:** LruCache بحجم 1/8 من heap، منع طلبات مكررة، `loadThumbnail` على API 29+ ثم `MediaMetadataRetriever`.
- **PiP:** النسبة محصورة 0.42–2.38، auto-enter على API 31+ أثناء التشغيل، وتعطيله عند مغادرة شاشة المشغل.
- **الواجهة:** شريط الزمن يظل LTR فعليًا مع أرقام غربية حتى في العربية؛ RTL العام يظل محترمًا.
- **التنقل:** رجوع المشغل يغلق لوحة الإعدادات أولًا ثم يعود للمكتبة؛ رجوع المكتبة يخرج من التطبيق؛ التشغيل يستمر بعد مغادرة الشاشة.
- **Snackbar:** رسالة فشل البدء، إجراء إعادة المحاولة، مدة Short، إلغاء Snackbar السابق بدل تكديسه، ومحاولتا بدء الخدمة.
- **MediaSession:** معرّف `FoxPlayerMediaSession`، أوامر السطح/العنصر الحالي متاحة، و`ACTION_PLAY_VIDEO` ومفاتيح `EXTRA_*` كما هي.
- **التخزين:** مخطط Room الإصدار 1 كما هو، ومفاتيح DataStore الحالية كما هي؛ لا ترحيل أو إعادة تسمية أثناء إعادة الهيكلة.
- **اللغة/السمة:** `AppCompatDelegate` هو المسار الوحيد للغة، دون كتابة مزدوجة إلى `LocaleManager`.

---

## 6. ترتيب النقل المقترح (بعد المرحلة 2)

الترتيب يتبع مراحل الخطة، مع تفصيل الخطوات داخل كل مرحلة. كل خطوة يجب أن تكون diff صغيرًا مستقلًا.

### المرحلة 3 — تفكيك `PlayerScreen`

1. **`PlayerSurface` + دالة حساب الأبعاد:** نقل `BoxWithConstraints`/حساب `videoAspectRatio`/`AndroidView`/`SurfaceView` إلى مكوّن مستقل، مع الحفاظ على `testTag("player_surface_view")` و`setVideoSurfaceView`/`clearVideoSurfaceView`.
2. **`HudVisibilityState`:** نقل `isHudVisible`/`lastInteractionTime`/`isSettingsOpen`/`isHorizontalSwiping` ومؤقت 3.5s إلى state holder، مع نفس الشروط.
3. **`PlayerImmersiveEffect` و`PlayerPipEffect`:** فصل شريط النظام ومستمع PiP و`updateAutoEnterPip`/`disableAutoEnterPip`.
4. **`PlayerTrackState`:** تجميع مستمعي `videoSize`/`tracks`/`trackSelectionParameters`/`playbackParameters` في حامل حالة واحد، بدل 4 حالات محلية.
5. **`PlayerGestureLayer`:** تجميع `PlayerGestureDetector` + `GestureFeedback` + `PlayerGestureController` + swipe/boost في طبقة واحدة، مع إبقاء `PlayerGestureDetector` عرضيًا.
6. **تقسيم `PlayerSettingsPanel`:** أقسام خاصة (Speed/Aspect/Audio/Subtitle/Gestures) داخل نفس الملف أو ملفات فرعية، دون تغيير التاغات أو النصوص.
7. **`PlayerPlaybackConnection`:** فصل بناء `MediaController` ودورة حياته وحل `ActivePlaybackSource` عن الشاشة.
8. **النتيجة:** `PlayerScreen` يبقى تركيبًا وربطًا، والهدف المعياري تقليل المسؤوليات لا الوصول إلى عدد أسطر محدد.

**مخاطر المرحلة:** متوسطة. كل خطوة تُختبر بـ `PlayerScreenTest`، `PlayerScreenPlaybackSourceTest`، `PlayerGestureTest`، `PlayerHudTest`، `PlayerHudScreenshotTest`، ثم المجموعة كاملة.

### المرحلة 4 — تثبيت حدود التشغيل ودورة الحياة

1. إدخال واجهة أوامر تشغيل (مثل `PlaybackCommands`) تلتف حول `Player` النشط، وتستخدمها الشاشة والإيماءات بدل الاستدعاءات المباشرة.
2. توحيد حالة المسارات/السرعة/حجم الفيديو في مصدر واحد بجانب `PlaybackState`، وإزالة `currentSpeed` المكرر.
3. توحيد `currentEngine`/`currentEngineFlow` في مقبض واحد observable، مع إبقاء `currentSessionToken` مرتبطًا بدورة الجلسة.
4. فصل بناء `PendingIntent` لنشاط الجلسة عن `MainActivity` (مزوّد intent أو واجهة صغيرة).
5. مراجعة أوامر `MediaSession` مقابل Media3 1.5.1 وإثبات توفر `COMMAND_SET_VIDEO_SURFACE` و`COMMAND_GET_CURRENT_MEDIA_ITEM`.
6. اختبار جهاز/Emulator: Surface، إعادة إنشاء Activity، PiP، الإشعار، تدوير الشاشة، فقدان الصوت/noisy.

**مخاطر المرحلة:** عالية. لا تُنفَّذ قبل اختبارات الحماية، ولا تُضاف تأخيرات أو polling أو fallback غير موثق.

### المرحلة 5 — فصل البيانات والتخزين

1. إدخال `PlaybackHistoryRepository` (أو عقد مكافئ) في البيانات، ونموذج عرض `WatchProgress` بدل `PlaybackHistoryEntity` في `VideoLibraryUiState` والواجهة.
2. إزالة اعتماد `FoxPlayerEngine` على `FoxPlayerDatabase` و`HistoryDao` مباشرة، وحقن عقد الكتابة/الاستئناف.
3. إبقاء `calculateResumePosition` دالة سياسة نقية ومختبرة، ونقلها إلى مكان لا يعتمد على Android إن أمكن.
4. إزالة تكرار `MediaRepository` عبر إسقاط/تحويل مشترك، وإصلاح `bucketId` في `getVideoByUri` **فقط** إذا اعتُمد كإصلاح سلوكي موثق.
5. عدم تغيير مخطط Room أو ترحيل البيانات في هذه المرحلة.

**مخاطر المرحلة:** متوسطة. اختبارات: `HistoryRepositoryTest`، `VideoLibraryHistoryModelTest`، `MediaRepositoryMappingParityTest`، واختبارات المحرك الحالية.

### المرحلة 6 — توحيد الإعدادات

1. نقل `GestureSensitivity`/`DoubleTapSeekDuration`/`GestureSettingsState` من `ui.player` إلى حزمة محايدة (مثل `core.model`)، وكسر `data → ui`.
2. توحيد واجهة الإعدادات خلف facade واحد يقرأ من مخزنين (دون تغيير المفاتيح أو دمج التخزين)، أو دمج آمن مع ترحيل مختبر إذا تقرر لاحقًا.
3. إزالة `R` من `AppSettings` بنقل خرائط التسميات إلى الواجهة.
4. إزالة التحميلات المكررة (`updateDoubleTapSeekDuration(Int)` و`updateDoubleTapSeekSeconds`) وتثبيت عقد واحد.
5. اختبار القيم الافتراضية/المفقودة/غير الصالحة، وإعادة التشغيل، والترقية، والحفاظ على القيم الحالية.

**مخاطر المرحلة:** متوسطة (فقدان تفضيلات إن تغيرت المفاتيح). القاعدة: لا تغيير للمفاتيح.

### المرحلة 7 — المكتبة والميزات

1. تقسيم `VideoLibraryScreen` إلى شريط علوي/بحث/فرز + حالات + كرت فيديو، مع نموذج عرض للسجل من المرحلة 5.
2. عند جدولة المفضلة/القوائم: بناء repository واختبارات بيانات وواجهة؛ DAOs موجودة لكنها ليست ميزة مكتملة.
3. المجلدات: `getVideoFolders` و`VideoFolder` نائمان؛ لا تُبنى واجهة قبل قرار ميزة.
4. لا تغيير بصري في المكتبة أثناء إعادة الهيكلة.

**مخاطر المرحلة:** متوسطة-منخفضة.

### المرحلة 8 — الصور المصغرة

1. تضمين وقت التعديل/الحجم في مفتاح الذاكرة لمنع الصور القديمة.
2. قياس زمن الاستخراج والتمرير قبل أي disk cache أو concurrency إضافي.
3. إبقاء الإلغاء/الخطأ/التكرار مغطى بالاختبارات الحالية.

**مخاطر المرحلة:** منخفضة، بشرط وجود قياس.

### المرحلة 9 — قرار الوحدات

انظر القسم 7.

---

## 7. اختبارات الحماية المطلوبة قبل كل نقل

### P1 — يجب إضافتها قبل المرحلة 3/4

| الاختبار | السلوك الذي يحميه | المستوى |
|---|---|---|
| `PlayerScreenCommandRoutingTest` | كل زر HUD/إيماءة/إعداد يصل إلى العامل النشط مرة واحدة، ولا يصل إلى controller منفصل | Robolectric + fake Player |
| `PlayerScreenSpeedBoostTest` | 2× أثناء الضغط الطويل، الاستعادة للسرعة السابقة، السلوك عند الإيقاف/التبديل | Robolectric |
| `PlayerSurfaceBindingTest` | ربط/فك السطح عند تغيير العامل والخروج، وعدم مسح سطح أحدث | Robolectric / Shadow |
| `PlayerHudVisibilityTest` | 3.5s، إعادة الظهور، عدم الإخفاء أثناء السحب/الإعدادات | Compose test |
| `PlayerPipEffectTest` | تفعيل/تعطيل auto-enter وفق API وحالة التشغيل، دون استدعاء على API < 31 | Robolectric |
| `PlaybackResumeParityTest` | تطابق `prepare(uri)` و`prepare(videoItem)` في الاستئناف والإعداد | JVM/Robolectric |
| `MainActivityNavigationTest` | Library ↔ Player ↔ Settings والرجوع، مع استعادة الحالة | Robolectric |

### P2 — قبل المرحلة 5/6

| الاختبار | السلوك |
|---|---|
| `PlaybackHistoryRepositoryTest` | كتابة/قراءة/حذف السجل، تدفق التحديثات، غياب الملف/الصف |
| `VideoLibraryHistoryModelTest` | تحويل السجل إلى `WatchProgress`، حالة «مكتمل»، القيم الحدية |
| `MediaRepositoryMappingParityTest` | تطابق حقول `VideoItem` بين القائمة و`getVideoByUri`، بما فيها `bucketId` |
| `SettingsFacadeTest` | الحفاظ على المفاتيح والقيم، القيم الافتراضية/غير الصالحة، عدم فقدان التفضيلات |
| `LibraryScreenStatesTest` | permission/loading/error/empty/search/sort كما تُعرض فعلًا |

### P3 — قبل المرحلة 7/8 أو عند توفر جهاز

| الاختبار | السلوك |
|---|---|
| `FavoritesRepositoryTest` / `PlaylistsRepositoryTest` | CRUD، الترتيب، الحذف المتسلسل، البيانات القديمة |
| `ThumbnailCacheInvalidationTest` | تغيير ملف بنفس URI لا يعيد صورة قديمة |
| `PlaybackServiceDeviceTest` | Surface، الإشعار، الصوت/noisy، `onTaskRemoved`، PiP، تدوير، إعادة إنشاء Activity |
| قياس أداء | زمن استخراج الصورة والتمرير على جهاز مرجعي |

**ملاحظة:** اختبارات JVM الحالية قوية في منطق Kotlin وMedia3/Compose تحت Robolectric، لكنها لا تثبت استمرارية Surface أو PiP أو دورة حياة الخدمة على جهاز حقيقي. هذه الحالات تُوثَّق كـ «تحتاج جهازًا» ولا تُحوَّل إلى ادعاء نجاح.

---

## 8. قرار Gradle modules

**التوصية: البقاء داخل `:app` حاليًا.**

الأسباب:

- المشروع 42 ملف Kotlin و6,790 سطرًا في `main`؛ لا توجد إعادة استخدام خارج التطبيق.
- لا توجد ملكية فريق/وحدة مستقلة تفرض حدودًا.
- البناء التزايدي سريع (12s لـ debug بعد الإعداد)، والبناء البارد 4m42s مع الاختبارات؛ لا مشكلة بناء تبرر التعقيد.
- الحدود المطلوبة (منع `core → data`، منع `data → ui`) يمكن فرضها بالحزم والمراجعة والاختبارات المعمارية قبل إنشاء modules.
- إنشاء `:domain`/`:core` الآن سيزيد إعدادات البناء والاختبارات دون فائدة مثبتة، ويخالف مبدأ «لا تجريد بلا حاجة».

**معايير إعادة النظر لاحقًا (المرحلة 9):**

- الحاجة إلى اختبار أو بناء جزء مستقل فعليًا.
- إعادة استخدام حقيقية خارج التطبيق.
- تعقيد بناء أو زمن بناء يبرر الفصل.
- حاجة إلى فرض حدود اعتماد لا تكفي الحزم لفرضها.
- ملكية واضحة لمجال مستقل.

إن تقرر لاحقًا إنشاء module، فليكن واحدًا صغيرًا (مثل `:core:model`) مع نقل محدود واختبارات، لا تحويل كل حزمة إلى module.

---

## 9. بوابات المرحلتين 0 و1

| البوابة | الحالة | الدليل |
|---|---|---|
| تسجيل SHA والفرع وحالة Git | **متحقق** | القسم 2.1 |
| تسجيل الإصدارات والاعتماديات | **متحقق** | القسم 2.2 |
| إعادة إنتاج البناء | **متحقق جزئيًا** | APK debug بُني بعد إنشاء keystore؛ الفجوة موثقة في 2.5 |
| إعادة إنتاج الاختبارات | **متحقق** | 223/223 نجحت |
| Lint | **متحقق** | 60 تحذيرًا / 0 خطأ |
| اختبارات اللقطات | **متحقق** | `verifyRoborazziDebug` نجح |
| اختبارات الجهاز | **غير متاح** | لا جهاز/Emulator؛ موثق |
| خريطة المسؤوليات | **متحقق** | 3.2 |
| خريطة الاعتماديات | **متحقق** | 3.3 |
| قائمة المخاطر | **متحقق** | القسم 4 |
| خطة النقل والاختبارات | **متحقق** | 6 و7 |
| قرار الوحدات | **متحقق** | 8 |

**قرار المرحلة:** المرحلة 0 والمرحلة 1 **ناجحتان مع قيود موثقة** (لا اختبارات جهاز، وفجوتا `gradlew`/`debug.keystore`).

---

## 10. الخطوة التالية

الخطوة التالية هي **المرحلة 2 فقط: اختبارات حماية السلوك**، وليس نقل الكود. يُفضَّل البدء بـ P1 لأنها تحمي أخطر نقلين (المرحلة 3 و4). بعد إضافة الاختبارات وتشغيل المجموعة كاملة، تُراجع النتائج قبل اعتماد أي نقل.

**لا يبدأ أي نقل قبل:**

1. اعتماد هذا التقرير.
2. إضافة اختبارات P1 وتشغيلها بنجاح.
3. تحديد ملف/نطاق واحد للمرحلة 3.

---

## الملحق أ — الملفات الرئيسية ومسؤولياتها

| الملف | المسؤولية |
|---|---|
| `MainActivity.kt` | نقطة الدخول، اللغة/السمة، التنقل، Snackbar، PiP عند مغادرة التطبيق |
| `ui/player/PlayerScreen.kt` | تركيب المشغل وكل تنسيق الحالة/الأحداث/السطح/الإيماءات |
| `ui/player/PlayerHud.kt` | عرض HUD فقط |
| `ui/player/PlayerGestureDetector.kt` | اكتشاف اللمس |
| `ui/player/PlayerGestureController.kt` | صوت النظام والسطوع |
| `ui/player/ControllerLifecycle.kt` | توكيد جيل controller ومنع الإزاحة |
| `ui/player/PlayerTrackModels.kt` | نماذج المسارات واختيارها عبر Media3 |
| `ui/player/PlayerSettingsSheet.kt` | لوحة إعدادات المشغل |
| `ui/player/GestureFeedbackOverlay.kt` | ردود الإيماءات المرئية |
| `core/playback/PlaybackService.kt` | الخدمة والجلسة والمحرك وأوامر MediaSession |
| `core/playback/FoxPlayerEngine.kt` | ExoPlayer، الحالة، الاستئناف، كتابة السجل |
| `core/playback/ActivePlaybackSource.kt` | حل العامل النشط ومصدر الحالة |
| `core/playback/PlayerEngine.kt` | عقد التشغيل النظري (غير مستخدم بالكامل من الواجهة) |
| `core/thumbnail/ThumbnailEngine.kt` | استخراج/تخزين الصور المصغرة |
| `data/repository/MediaRepository.kt` | MediaStore |
| `data/repository/AppSettingsRepository.kt` | DataStore للتطبيق |
| `data/repository/GestureSettingsRepository.kt` | DataStore للإيماءات |
| `data/local/database/*` | Room: السجل، المفضلة، القوائم |
| `ui/library/VideoLibraryScreen.kt` | المكتبة والبحث/الفرز/الحالات |
| `ui/library/VideoLibraryViewModel.kt` | الصلاحيات والملاحظة والبحث/الفرز وسجل المشاهدة |
| `ui/settings/SettingsScreen.kt` | واجهة الإعدادات |
| `ui/settings/SettingsViewModel.kt` | حالة الإعدادات وتطبيق اللغة/السمة |
