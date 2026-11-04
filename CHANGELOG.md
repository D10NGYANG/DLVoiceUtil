# 更新记录

## 0.3.0 — 2026-11-04

- 修复 PCM 解码、时长、有效读取长度与长录音累积开销
- 增加可等待的录音停止 API 和错误 Flow，修复跨平台资源及会话生命周期问题
- 升级 Kotlin 2.4.21、AGP 9.3.1、Gradle 9.8.2，添加 iOS arm64 模拟器目标
- 升级 DLCommonUtil 1.1.0、示例 DLAppUtil 3.0.0；拆分 Android 应用模块
- Android 消费方需 compileSdk 37，minSdk 保持 26
- [升级说明](docs/migration/0.3.0.md) · [发布说明](docs/releases/0.3.0.md)
