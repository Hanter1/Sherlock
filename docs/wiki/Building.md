# Building

## Требования

- JDK **17**
- Android SDK (compile/target SDK 35)
- Gradle Wrapper из репозитория

## Debug

```bat
set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot
set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
gradlew.bat assembleDebug
```

APK: `app\build\outputs\apk\debug\app-debug.apk`

```bat
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Release (R8)

Release включает minify + shrink resources.

1. Создайте keystore (один раз):

```bat
keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias sherlock
```

2. Скопируйте `keystore.properties.example` → `keystore.properties` в корне и заполните пароли / путь к `.jks`.

3. Соберите:

```bat
gradlew.bat assembleRelease
```

APK: `app\build\outputs\apk\release\app-release.apk`

`keystore.properties` и `*.jks` в git **не** коммитятся. Без них release APK будет unsigned.

## Тесты и lint

```bat
gradlew.bat testDebugUnitTest lintDebug
```

CI на `main`: unit-тесты + `lintDebug` + `assembleDebug` (артефакт APK).

## Версия

`versionName` / `versionCode` — в `app/build.gradle.kts` (`defaultConfig`).
