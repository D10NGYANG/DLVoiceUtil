# DLVoiceUtil
Android音频录音与播放工具，目前支持PCM格式语音文件；

*版本*：`0.2.0`

> 从`0.2.0`版本开始调整为kotlin multiplatform项目，支持Android、iOS，并且只支持16bit、单声道的PCM格式录制。

## 功能
- [x] 录制PCM音频
- [x] 播放PCM音频
- [x] 录取与播放PCM音频时，实时显示音频波形
- [ ] 录音降噪、增益

## 安装说明
1 添加Maven仓库
```gradle
allprojects {
  repositories {
    ...
    maven { url 'https://raw.githubusercontent.com/D10NGYANG/maven-repo/main/repository'}
  }
}
```
2 添加依赖
```gradle
dependencies {
    implementation("com.github.D10NGYANG:DLVoiceUtil:$ver")
    // 通用计算
    implementation("com.github.D10NGYANG:DLCommonUtil:0.5.2")
}
```
3 混淆
```properties
-keep class com.d10ng.voice.** {*;}
-dontwarn com.d10ng.voice.**
```

## 使用说明

> 参考demo工程：[App](composeApp/src/commonMain/kotlin/com/d10ng/voice/demo/App.kt)

iOS项目需要在项目的info.plist中添加如下配置：

```plist
<key>NSMicrophoneUsageDescription</key>
<string>Microphone usage description</string>
```

Android项目需要在AndroidManifest.xml中添加如下配置：

```xml

<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

## 优化计划
> 无