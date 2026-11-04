# 完整升级交付记录

日期：2026-11-04。此记录描述 0.3.0 发布前的升级验证。正式使用要求见 [升级说明](../../migration/0.3.0.md)。

## 最终版本与结构

Gradle9.8.2、AGP9.3.1、Kotlin/Compose Compiler2.4.21、Compose Multiplatform1.12.2、Coroutines1.11.0、Lifecycle2.11.0、Activity1.13.0、Annotation1.11.0、DLCommonUtil1.1.0、DLAppUtil3.0.0。

compileSdk37，targetSdk34，minSdk26。音频库JVM字节码仍为8，示例为11。构建环境使用JDK21。

library和composeApp使用官方Android-KMP插件；androidApp接收原Android入口/Manifest/资源，applicationId保持com.d10ng.voice.demo。Android资源与迁移前逐字节一致。iOS framework仍为ComposeApp，Xcode现有调用路径无需修改。

删除Okio、未使用的ViewModel直接声明及第三方版本检查插件；库Core KTX收窄为Annotation。Compose依赖改成版本目录中的明确坐标，预览注解改用当前官方入口。没有使用全局force/exclude、legacy Android插件开关或跳过元数据检查。

## 验证

| 项目 | 结果 |
|---|---|
| Android testAndroidHostTest | 10/10通过 |
| iOS iosSimulatorArm64Test | 12/12通过 |
| Android debug/release APK | 通过 |
| Android lintDebug | 0错误；保留目标SDK、图标和更新提示等警告 |
| iOS库真机arm64、示例真机arm64/模拟器arm64 Kotlin编译 | 通过 |
| Xcode build-for-testing | 通过 |
| 本地Maven发布 | 所有Android/iOS/元数据publication通过，发布到隔离的本地目录验证 |
| 独立消费工程 | Android、iOS arm64和模拟器arm64编译通过；公开Flow类型无需手动补协程依赖 |
| 华为手机 | 录音保存240956字节（15.05975秒、119412个非零样本）；自然播放结束、3次连续启停、后台停止全部通过 |
| iOS26.5应用宿主XCTest | 连续两次完整流程通过：录音、保存、播放、连续启停、后台停止 |
| OCR委托复检 | 31/31可审查文件覆盖100%；额外核对移动/默认排除文件，未发现新增未处理问题 |

NotificationPermission lint错误来自DLAppUtil未被本示例使用的NotificationController。androidApp/lint.xml仅按该诊断中的完整类名过滤；未添加通知权限、未关闭整体通知检查、未创建忽略所有问题的baseline。

Android运行时依赖树已核对，没有意外alpha/beta依赖；仍可能存在官方组件的合理传递依赖。音频库发布坐标group/artifact及三个平台变体保持可消费。AAR声明minCompileSdk37。

## 可复现入口

```bash
./gradlew :DLVoiceUtil:testAndroidHostTest :DLVoiceUtil:iosSimulatorArm64Test -PiosSimulatorId=<UDID>
./gradlew :androidApp:assembleDebug :androidApp:assembleRelease :androidApp:lintDebug
./gradlew :DLVoiceUtil:publishAllPublicationsToMavenRepository -PlocalMavenRepository=/tmp/dlvoice-upgrade-maven
xcodebuild -project iosApp/iosApp.xcodeproj -scheme AudioAcceptance -destination 'platform=iOS Simulator,id=<UDID>' CODE_SIGNING_ALLOWED=NO test
```

XCTest前需安装应用并授予测试所需麦克风权限。JSON摘要见本目录 tests.json、huawei-evidence.json、ocr-review.json。

## 边界与风险

- Gradle9.8.2为获得安全修复而采用，超出Kotlin2.4.21官方完整支持矩阵的9.7.0上界；本机Xcode27.2也超出其列明的26.4。此次构建和验收通过不等于扩大官方支持承诺。
- 旧版本的iOS AudioUnitInitialize RPC超时在此次两轮应用测试未复现；未证明根因，也不声称永久消失。未测iPhone真机、蓝牙/通话中断和长期压力。
- Native链接器对系统缓存对象的调试信息路径给出警告；测试二进制成功链接运行。仍有插件内部Configuration.setVisible弃用警告，未通过屏蔽警告隐藏。
- 升级验证最初使用隔离的 0.2.8 产物；发布版本使用 0.3.0，旧版制品不覆盖。下游工具链与 compileSdk 门槛已提高。
- 本任务模拟器关闭删除；手机应用停止、测试录音删除、临时麦克风授权撤回。调试应用保留供手工测试。CodeGraph已同步。
