# Platforma151

Десктоп-приложение облачных сохранений для сайта base151.

Текущая версия: **3.0.2**.

## Версионирование

- каждое обновление — `+0.0.1` (патч);
- крупное обновление — `+0.1` (минор);
- глобальное обновление — `+1.0` (мажор).

Версия задаётся в `build.gradle` (`version = '…'`) и попадает в `version.txt` / Setup через `writeVersionTxt`.

Игра живёт отдельно: [`../untitled`](../untitled) (`python deploy.py` там собирает Bunker).

## Что умеет

- Вход через сайт (`/desktop/auth` + localhost callback).
- Синхронизация папок сохранений (квота **250 МБ**).
- Системный трей: закрытие окна прячет приложение, не завершает процесс.
- Автозапуск Windows (ярлык в Startup с `--tray`).
- **Один процесс:** повторный запуск поднимает уже открытое окно (`instance.lock` в AppData).
- Размер и позиция окна запоминаются в `%APPDATA%\Platforma151\config.json`.
- Автообновление JAR/установщика с сайта (при старте и каждые 5 часов).
- Сканирование игр по дискам, статусы облака, локальный архив настроек игр.

## Дерево

```
platforma151/
├── src/main/java/     # Swing: PlatformaApp + UI + cloud saves
├── src/main/resources/games-catalog.json
├── assets/icons/      # platforma151.ico / png
├── tools/             # deploy_common.py, build_installer.py
├── deploy.py          # сборка + overlay установки + upload
├── run.bat
├── build.gradle       # jar, writeVersionTxt, packageApp, packageInstaller
└── README.md
```

Main-Class: `com.bunker.survival.platforma.PlatformaApp`.

## Запуск

```bat
run.bat
```

Порядок: установленный `%LOCALAPPDATA%\Programs\Platforma151\Platforma151.exe` → `dist\Platforma151\Platforma151.exe` → `java -jar build\libs\platforma151.jar`.

Dev:

```bat
gradlew.bat run
```

JDK: 17–21. Скрипт ищет `.tmp-jdk21\jdk-21.0.12+8` здесь или в соседнем `../untitled/.tmp-jdk21`. Gradle 8.11 не работает на JDK 22+.

## Данные пользователя

| Путь | Назначение |
|------|------------|
| `%APPDATA%\Platforma151\config.json` | URL API, размер/позиция окна, автообновление |
| `%APPDATA%\Platforma151\auth.json` | токен входа |
| `%APPDATA%\Platforma151\instance.lock` | single-instance |
| `%LOCALAPPDATA%\Programs\Platforma151\` | установленный app-image (Setup) |

## Сборка и деплой

```bat
python deploy.py
```

Вопросы:

1. Собрать Platforma151? `[Y/n]` — `writeVersionTxt jar packageApp packageInstaller`. После сборки копирует `dist/Platforma151` поверх локальной установки (если EXE уже стоит). Запущенный процесс останавливается и поднимается снова.
2. Залить на сервер? `[y/N]` — `/downloads/platforma151.jar`, `Platforma151-Setup.exe`, sha256, `build-platforma.json`.
3. Задеплоить сайт? `[y/N]` — `H:\projects\server151\deploy.py`.

Флаги: `--yes`, `--build` / `--no-build`, `--deploy-app` / `--no-deploy-app`, `--site` / `--no-site`, `--no-local-install`, `--no-local-mirror`, `--no-sha`.

Артефакты:

| Локально | На сервере |
|----------|------------|
| `build/libs/platforma151.jar` | `/downloads/platforma151.jar` |
| `dist/Platforma151-Setup.exe` | `/downloads/Platforma151-Setup.exe` |
| `dist/Platforma151/Platforma151.exe` | (через Setup / overlay) |

Сайт не кладёт бинарники в tar: их пишет только этот `deploy.py`.

## Gradle

```bat
gradlew.bat compileJava
gradlew.bat jar writeVersionTxt
gradlew.bat packageApp
gradlew.bat packageInstaller
```

`packageApp` = jpackage app-image. Нужен полный JDK с `jpackage.exe`.
