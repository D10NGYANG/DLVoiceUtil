# DLVoiceUtil
Android音频录音与播放工具，目前支持PCM格式语音文件；

*版本*：`0.0.9`

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
    implementation 'com.github.D10NGYANG:DLVoiceUtil:$ver'
}
```
3 混淆
```properties
-keep class com.d10ng.voice.** {*;}
-dontwarn com.d10ng.voice.**
```

## 使用说明
> 参考demo工程：[MainActivity](app/src/main/java/com/d10ng/voice/app/MainActivity.kt)

## 优化计划
> 无