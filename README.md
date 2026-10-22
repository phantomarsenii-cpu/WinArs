# WinArs (com.winars.app) — своя Windows-подобная оболочка, Winlator = невидимое ядро

Единственная Activity — `com.winars.app.MainActivity` (Compose). Интерфейса Winlator в приложении нет.

## Что взято из Winlator (ядро, `app/src/main/java/com/winlator`)
- `xenvironment` (RootFS, RootFSInstaller без UI, XEnvironment, компоненты), `container` (модель + ContainerManager),
  `core` (Box64/Wine/FileUtils и т.д.), `xserver`, `winhandler`, `renderer` + `widget/XServerView` (GL-рендер X-экрана), `cpp` (CMake).
- `WineSession.java` — НОВОЕ: заменяет XServerDisplayActivity. Поднимает XServer + Box64 + Wine без Activity
  и отдаёт X-экран как обычный View, который WinArs встраивает в своё окно.
- `WinlatorConstants.java` — константы из удалённых MainActivity/SettingsFragment.

## Удалено
XServerDisplayActivity, MainActivity, ControlsEditorActivity, ExternalControllerBindingsActivity, все *Fragment,
ActiveWindowsDialog, ScreenEffectDialog, TaskManagerDialog, ShortcutSettingsDialog.

## Оболочка WinArs (`com.winars.app`)
- `DesktopState` — окна, ярлыки (`files/Desktop/*.json`: exePath, icon, containerId), обои, Wine-сессия.
- `WineBackend.kt` — `launch(exe)`: распаковка rootfs → контейнер → WineSession (без Intent в Activity Winlator).
- `ui/WineScreen.kt` — окно WinArs, внутри которого рисуется X-экран Wine.
- `Explorer` — настоящий диск C: контейнера; `WineShortcutImporter` — .lnk из Wine → ярлыки WinArs (+иконки из exe).

## Сборка
GitHub Actions: JDK 17, NDK 24.0.8215888, CMake 3.22.1, Gradle 8.9, AGP 8.7.3, targetSdk 28.
`tools/patch_assets.py` заменяет `com.winlator/files/rootfs` → `com.winars.app/files/root` (та же длина) в .tzst и jniLibs.
