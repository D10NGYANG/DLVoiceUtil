# DLVoiceUtil

Kotlin Multiplatform PCM 录音与播放工具，支持 Android、iOS 真机和 Apple Silicon iOS 模拟器。

**当前版本：0.3.0** · [升级说明](docs/migration/0.3.0.md) · [更新记录](CHANGELOG.md)

支持小端、16bit、单声道 PCM，提供录音/播放状态、时长、音量和错误 Flow。

## 安装

在 `settings.gradle.kts` 中添加仓库：

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://raw.githubusercontent.com/D10NGYANG/maven-repo/main/repository") {
            mavenContent { includeGroupAndSubgroups("com.github.D10NGYANG") }
        }
    }
}
```

KMP 项目在 `commonMain.dependencies` 中添加：

```kotlin
implementation("com.github.D10NGYANG:DLVoiceUtil:0.3.0")
```

仅 Android 项目也可选择平台坐标，二者择一：

```kotlin
implementation("com.github.D10NGYANG:DLVoiceUtil-android:0.3.0")
```

DLCommonUtil 1.1.0 和 Coroutines 1.11.0 通过依赖元数据解析；仅使用本库 API 无须额外声明 DLCommonUtil。直接调用其 API 时再显式声明对应依赖。

## 使用要求

- Android：`minSdk 26`，`compileSdk` 至少 37；库 JVM 字节码目标仍为 Java 8。
- Kotlin 元数据基线为 2.4.21，建议消费方对齐 Kotlin 2.4.21。Compose Compiler 插件与 Kotlin 同版本。
- iOS 提供 `iosArm64` 和 `iosSimulatorArm64` 制品；运行时版本还须满足宿主及其其他依赖要求。
- 本仓库示例使用 `targetSdk 34`；提高 `compileSdk` 不要求消费方同时提高 `targetSdk`。

Android 声明并在运行时申请麦克风权限：

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

iOS 在 `Info.plist` 中添加麦克风用途说明，并取得用户授权：

```xml
<key>NSMicrophoneUsageDescription</key>
<string>用于录制语音</string>
```

## 录音与播放

```kotlin
val sampleRate = 8000
val recorder = createPCMVoiceRecorder(sampleRate)
val player = createPCMVoicePlayer()

// 已取得系统麦克风授权后开始录音。
recorder.start()

// 在协程中停止，等待采集和原生资源清理完成。
val pcm = recorder.stopAndGetData()
if (pcm.isNotEmpty()) player.start(pcm, sampleRate)
```

完整界面与权限处理参考 [App.kt](composeApp/src/commonMain/kotlin/com/d10ng/voice/demo/App.kt)。

- `start()` 不允许同一实例重复启动；运行中再次调用会抛出 `IllegalStateException`。
- 保留的录音 `stop()` 同样等待清理完成，但会阻塞调用线程；界面协程优先使用 `stopAndGetData()`。
- `getErrorFlow()` 提供异步错误，下次启动会清除上次错误；同步参数和播放启动错误仍需调用方捕获。
- 播放数据不能为空，字节数必须为偶数；采样率须为正数，具体支持范围由平台校验。
- 音量事件为 0–1 的显示比例，慢订阅者不会阻塞采集；它不是校准声压级。
- 页面或业务生命周期结束时应停止音频。示例进入后台即停止；库不提供后台录音服务。
- 完整录音保存在内存，业务应限制录音长度。

## 工程与验证

| 模块 | 用途 |
|---|---|
| `library`（Gradle 名称 `DLVoiceUtil`） | Android/iOS 音频 KMP 库 |
| `composeApp` | 共享 Compose 界面与平台实现，导出 `ComposeApp` framework |
| `androidApp` | Android 应用入口、Manifest 和资源 |
| `iosApp` | Swift/Xcode 宿主与 `AudioAcceptance` 测试 |

本仓库构建组合：Gradle 9.8.2、AGP 9.3.1、Kotlin/Compose Compiler 2.4.21、Compose Multiplatform 1.12.2；构建 JDK 17+。

```bash
./gradlew :androidApp:assembleDebug :androidApp:assembleRelease :androidApp:lintDebug
./gradlew :DLVoiceUtil:testAndroidHostTest
./gradlew :DLVoiceUtil:iosSimulatorArm64Test -PiosSimulatorId=<UDID>
```

Android APK 位于 `androidApp/build/outputs/apk/`。iOS 音频行为需要完整应用宿主；启动模拟器、安装应用并授予麦克风权限后，可运行：

```bash
xcodebuild -project iosApp/iosApp.xcodeproj -scheme AudioAcceptance \
  -destination 'platform=iOS Simulator,id=<UDID>' CODE_SIGNING_ALLOWED=NO test
```

测试覆盖与工具链边界见 [0.3.0 发布说明](docs/releases/0.3.0.md)。模拟器不能替代 iPhone 真机的蓝牙路由、通话中断和音质验收。
