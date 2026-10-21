# WinArs (com.winars.app) = оболочка Windows 11 + ядро Winlator 11.2

- `app/src/main/java/com/winars/app` — оболочка (Compose): рабочий стол, Пуск, Проводник, DeX-режим, S-Pen.
- `app/src/main/java/com/winlator` — исходники Winlator 11.2 (XServer, ContainerManager, XEnvironment, Vortek/Gladio/VirGL компоненты).
- `app/src/main/cpp` — нативное ядро Winlator (CMake): libwinlator, libvortekrenderer, libgladiorenderer, libvirglrenderer, libmidihandler, libadrenotools.
- `app/src/main/assets`, `jniLibs` — rootfs.tzst, box64, Turnip/VirGL/Vortek/Gladio, DXVK/VKD3D (.tzst остаются архивами, распаковка на телефоне).
- `tools/patch_assets.py` — при сборке заменяет `com.winlator/files/rootfs` на `com.winars.app/files/root` (та же длина).

Запуск .exe: `core/WineBackend.kt` → `XServerDisplayActivity` (container_id + exec_path), как в самом Winlator.
targetSdk = 28 (иначе Android запрещает exec из files/).
