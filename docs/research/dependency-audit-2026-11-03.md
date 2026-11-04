# 依赖评估：2026-11-03

> 以下为升级前的历史评估；最终采用版本和使用要求见 [0.3.0 升级说明](../migration/0.3.0.md)。

本轮仅评估，未修改依赖或源码。用户补充要求已纳入DLCommonUtil、DLAppUtil的更新评估；用户明确不向Google Play发布应用。来源为官方发布说明、兼容表、当前构建配置及离线 Gradle 依赖树。

## 结论与优先级

1. **优先安全更新：Gradle 8.14.3 → 8.14.6。** 官方8.14.6公告列出三个高危修复。客户端/worker与daemon反序列化问题要求同机攻击条件，并非远程网络即可利用；另有SSL失败后继续搜索后续仓库导致依赖来源漂移的问题。当前多仓库配置使来源控制也值得关注。采用8.x补丁，不需要同时迁移AGP9。[发布说明](https://docs.gradle.org/8.14.6/release-notes.html)、[daemon公告](https://github.com/gradle/gradle/security/advisories/GHSA-xwqc-3h47-hg64)、[worker公告](https://github.com/gradle/gradle/security/advisories/GHSA-mvvg-497x-hmj8)、[仓库公告](https://github.com/gradle/gradle/security/advisories/GHSA-j5m7-59rp-24f5)。
2. **优先兼容性对齐：Kotlin 2.2.0。** 当前AGP8.11.1超出Kotlin2.2.0表中的完整支持上限8.10.0；最小改动候选为Kotlin2.2.21，表中支持AGP8.11.1。当前稳定版2.4.21可作为较完整维护升级候选。它们不是已在本项目验证过的升级结果。[KGP兼容表](https://kotlinlang.org/docs/gradle-configure-project.html)、[版本发布](https://kotlinlang.org/docs/releases.html)。
3. **iOS必须核对工具链，不能仅看编译通过。** 实测本机Xcode27.2；KMP表中Kotlin2.2.0列Xcode16.3，2.2.21列26.0，2.4.21列26.4。建议使用官方列明的匹配组合复现此前RPC超时；即使升到最新稳定Kotlin，也不能据此声称Xcode27.2已被官方完整验证。版本差距是风险，不是已证实的崩溃根因。[KMP兼容表](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html)。
4. **无需因Google Play提高targetSdk。** 用户明确不向Google Play发布，撤回其作为当前升级条件的建议。targetSdk34可先保留；compileSdk仍必须满足所选依赖的AAR元数据要求，两个SDK概念不能混淆。

## 直接依赖清单

| 内容 | 当前 | 官方已核实的候选/建议 | 归属与判断 |
|---|---|---|---|
| Kotlin / Compose compiler | 2.2.0 | 最小对齐2.2.21；完整升级候选2.4.21 | JetBrains官方；两个插件继续同版本 |
| Compose Multiplatform | 1.8.2 | 1.12.2候选，随Kotlin一起回归 | JetBrains官方；Android端使用Google构件，不能机械替换为仅Android依赖 |
| kotlinx-coroutines core/android | 1.10.2 | 1.11.0候选 | Kotlin官方生态；新版以Kotlin2.2.20构建，建议先对齐Kotlin，再同时升core/android |
| Activity Compose | 1.10.1 | 1.13.0候选 | Google官方；升级时检查AAR的compileSdk/AGP要求 |
| Core KTX | 1.16.0 | 首先考虑缩减直接声明；官网稳定版1.19.1 | Google官方；库源码没有androidx.core调用，仅使用androidx.annotation.RequiresPermission，适合直接声明annotation而非依赖core-ktx带入 |
| Lifecycle runtime-compose | 2.9.1 | 2.11.0候选，随CMP对齐 | JetBrains官方KMP适配；App实际使用LifecycleEventEffect，应保留 |
| Lifecycle viewmodel | 2.9.1 | 删除未使用的直接声明 | 没有ViewModel/viewModel源码调用；其他依赖仍可能传递引入 |
| Okio | 3.16.0 | 删除直接声明 | 非Google/JetBrains官方；当前源码没有okio调用，文件IO已经使用原生实现，无须换库 |
| Gradle Versions Plugin | com.github.ben-manes.versions 0.52.0 | 不使用则删除；保留则评估io.github.ben-manes.versions 0.67.0及新配置方式 | 第三方构建工具，不进APK；旧plugin ID已弃用。不能当成只改版本号的无风险更新 |
| AGP | 8.11.1 | 短期可保留；AGP9单独迁移 | Google官方；不是当前功能必须升级的依赖 |
| Gradle | 8.14.3 | 8.14.6 | 官方构建工具；安全补丁优先 |
| kotlin(test) / XCTest | 跟随Kotlin / Xcode | 保留 | 已采用官方推荐测试入口 |

版本来源：[CMP兼容与版本](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)、[Coroutines 1.11.0](https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.11.0)、[Activity](https://developer.android.com/jetpack/androidx/releases/activity)、[Core](https://developer.android.com/jetpack/androidx/releases/core)、[KMP Lifecycle](https://kotlinlang.org/docs/multiplatform/compose-lifecycle.html)、[旧插件ID](https://plugins.gradle.org/plugin/com.github.ben-manes.versions)、[新插件ID](https://plugins.gradle.org/plugin/io.github.ben-manes.versions)、[插件迁移说明](https://github.com/ben-manes/gradle-versions-plugin)。版本是评估候选，不代表该组合已编译验收。

## 第三方能否换成官方

### Okio

最小完整方案是直接删除composeApp里的implementation(libs.okio)，以及无其他使用时的版本目录条目。源码未发现okio导入/调用；Android FileIOUtils使用java.io.File，iOS使用Foundation，现有抽象已经覆盖需求。以后真的要在commonMain统一流式IO时，可以评估JetBrains的kotlinx-io；目前为替换一个未使用依赖而新增它没有收益。[kotlinx-io官方API](https://kotlinlang.org/api/kotlinx-io/)。

### 依赖检查插件

Gradle Plugin Portal托管不等于Gradle官方开发。此插件由Ben Manes维护，只参与构建期版本发现。不常用可删除；常用则保留并评估维护升级。Gradle内置dependencies/dependencyInsight用于查看解析结果，不能等价替代发现所有新版本。IDE更新提示或代码托管平台的依赖更新服务是另一种工作方式，不应自动当作插件的直接替换。

### AndroidX、Compose、Lifecycle、Coroutines

这些已经是Google/JetBrains官方体系，无需为了包名统一而重写。尤其org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose是官方KMP文档直接推荐的坐标；Google也说明JetBrains会包装Android构件并补齐其他平台。不可一律替换成androidx.*而破坏iOS目标。[Google KMP说明](https://developer.android.com/kotlin/multiplatform)、[官方Lifecycle用法](https://kotlinlang.org/docs/multiplatform/compose-lifecycle.html)。

录放音已经直接使用Android AudioRecord/AudioTrack、Apple AVFAudio/AudioToolbox。不存在需要换成官方音频库的第三方播放器引擎；Media3不是跨平台PCM麦克风采集的直接替换方案，本项目没有引入它的必要。

## 实际传递依赖

解析了composeApp debugRuntimeClasspath和DLVoiceUtil debugUnitTestRuntimeClasspath。非DL的Android运行时树中还包括Google Material、Guava listenablefuture、error-prone annotations、JetBrains annotations、JSpecify；没有发现OkHttp。测试树含JUnit4.13.2、Hamcrest1.3，来自kotlin(test)的JVM适配，是正常测试依赖，不随库进入生产运行时。Kotlin文档明确默认JVM测试适配JUnit4，无需为了“只用官方”强行剔除它。[kotlin-test JVM适配](https://kotlinlang.org/docs/gradle-configure-project.html#jvm-variants-of-kotlin-test)。

删除项目直接声明不等于传递依赖一定消失；也不应对DL库依赖使用全局exclude，防止破坏用户自己的库。此次未更改DL版本或其依赖设计。

## 推荐实施批次

1. 小批次：Gradle8.14.6；去掉未使用Okio和ViewModel直接声明；core-ktx收窄到实际使用的annotation；Kotlin至少对齐至2.2.21。保持AGP8，减少与当前音频排查无关的变动。
2. 独立工具链批次：Kotlin2.4.21 + Compose1.12.2 + Lifecycle2.11.0 + Coroutines1.11.0，并逐项确认AndroidX compileSdk要求、DL二进制依赖与Xcode匹配。应用端Activity1.13.0作为同批候选。Android JVM目标字节码1.8/11不是Gradle运行JDK，不能混为一谈。
3. 只有计划正式维护新构建结构时再迁AGP9：库改com.android.kotlin.multiplatform.library；Android应用从KMP composeApp拆到独立Android模块；迁移发布配置和测试任务。[Google迁移说明](https://developer.android.com/kotlin/multiplatform/plugin)。

当前最新版Gradle9.8.2比Kotlin2.4.21兼容表列出的最高完整支持9.7.0还新，因此不推荐把所有组件机械改成“最新”。安全补丁已有8.14.6可选，不必为了Gradle安全更新冒AGP9结构迁移风险。[Gradle最新版说明](https://docs.gradle.org/current/release-notes.html)、[KGP兼容表](https://kotlinlang.org/docs/gradle-configure-project.html)。

## 限制

未执行依赖升级构建、全量SCA/CVE扫描或新的设备回归；没有声称依赖不存在其他漏洞。此次结论覆盖直接声明、Android运行时/单测传递树及官方版本兼容说明；不等同于所有平台和所有构建插件的完整SBOM审计。此前iOS RPC超时仍需独立复现，不能把升级建议当作已验证修复。


## 补充：DL系列更新审查

核实时间：2026-11-03。直接读取对外maven-repo的maven-metadata.xml、Gradle module、发布AAR及sources.jar，并对照维护者升级文档。不是只根据README的“最新”字样推断。

| 依赖 | 当前 | 已发布最新版 | 与本项目有关的收益 | 升级门槛 |
|---|---|---|---|---|
| DLCommonUtil | 0.7.0 | 1.1.0 | Apple空NSData/长度边界修复；字节对转换显式校验；有iosArm64与iosSimulatorArm64变体 | 发布元数据Kotlin2.4.21、coroutines1.11.0、serialization1.12.0；Android AAR明确minCompileSdk=36 |
| DLAppUtil | 2.6.0 | 3.0.0 | 权限请求串行、取消传播及Activity销毁处理；清理legacy-support-v4和View Material传递依赖 | AAR明确minCompileSdk=37；Core1.19.1要求AGP>=9.1.0；维护者建议Kotlin2.4.21/JVM11；同带DLCommonUtil1.1.0 |
| DLAppUtil小步候选 | 2.6.0 | 2.7.1（旧主版本） | 变更日志明确修复ComponentActivity销毁后的launcher映射残留 | 元数据仍Kotlin2.2.0、coroutines1.10.2、DLCommonUtil0.7.0；AAR未增加实质compileSdk门槛，适合保留当前大工具链的小步评估 |

### 实际调用兼容性

DLCommonUtil用于library的ShortArray.toByteArray、ByteArray.toShortArray、ByteArray.toNSData，以及iOS示例NSData.toByteArray；发布sources.jar仍保留这些包名和签名。新toShortArray明确拒绝奇数字节，项目公共播放器已有偶数字节校验；NSData空值转换修复与文件读取直接相关。坐标/轨迹/JS行为变化在本项目没有直接调用。上述为源码级适配判断，尚未实际升级编译。

DLAppUtil只在Android示例使用getCachePath()与suspend PermissionManager.request(String): Boolean；3.0.0发布源码保留这两个入口。权限实现通过ActivityResultContracts和请求管理器处理，升级后重点回归拒绝/重试、协程取消和Activity销毁。缓存路径仍取ctx.cacheDir.absolutePath。

DLAppUtil 3.0.0的Compose/Nav3/DLJetpackComposeUtil是其demo依赖，不是发布AAR的运行时依赖；检查其3.0.0.module后，未发现把alpha Material3传递给DLVoiceUtil的情况，不应据其仓库README误判下游会引入alpha UI。

### 修订后的建议

- **最小更新路线：** Gradle8.14.6、Kotlin2.2.21、DLAppUtil2.7.1；暂留DLCommonUtil0.7.0，targetSdk34不动，删除无用依赖。2.7.1是兼容性较低成本候选，不是已在本项目通过回归的版本。
- **完整维护路线：** Kotlin2.4.21 + DLCommonUtil1.1.0 + DLAppUtil3.0.0 + coroutines1.11.0，compileSdk至少37；AGP9的KMP库插件迁移与Android应用拆分需要一起计划。示例当前JVM目标已经11；DLCommonUtil仍支持Java8，没必要因此把音频库JVM目标也机械提高。
- 新版DLCommonUtil自己用Gradle9.8.2构建，不代表消费方必须用相同Gradle；真正门槛来自Kotlin元数据、Android AAR和传递依赖。DLAppUtil的新版Core依赖才使AGP9成为完整升级路线的重要约束。
- 用户不向Google Play发布，因此无需为发布政策提高targetSdk；compileSdk37只是完整DL依赖升级的技术要求，不改变这个结论。

### DL来源

- [DLCommonUtil版本元数据](https://raw.githubusercontent.com/D10NGYANG/maven-repo/main/repository/com/github/D10NGYANG/DLCommonUtil/maven-metadata.xml)
- [DLCommonUtil 1.1.0升级说明](https://github.com/D10NGYANG/DLCommonUtil/blob/master/doc/Upgrade-1.1.0.md)
- [DLCommonUtil 1.1.0模块元数据](https://raw.githubusercontent.com/D10NGYANG/maven-repo/main/repository/com/github/D10NGYANG/DLCommonUtil/1.1.0/DLCommonUtil-1.1.0.module)
- [DLAppUtil版本元数据](https://raw.githubusercontent.com/D10NGYANG/maven-repo/main/repository/com/github/D10NGYANG/DLAppUtil/maven-metadata.xml)
- [DLAppUtil 3.0.0迁移说明](https://github.com/D10NGYANG/DLAppUtil/blob/master/docs/migration/3.0.0.md)
- [DLAppUtil变更记录](https://github.com/D10NGYANG/DLAppUtil/blob/master/CHANGELOG.md)
- [DLAppUtil 3.0.0模块元数据](https://raw.githubusercontent.com/D10NGYANG/maven-repo/main/repository/com/github/D10NGYANG/DLAppUtil/3.0.0/DLAppUtil-3.0.0.module)
- [DLAppUtil 2.7.1模块元数据](https://raw.githubusercontent.com/D10NGYANG/maven-repo/main/repository/com/github/D10NGYANG/DLAppUtil/2.7.1/DLAppUtil-2.7.1.module)

本轮不对DL库整体源码作重复质量审计，也未修改依赖版本；检查聚焦发布版本、消费门槛和本项目实际调用的迁移风险。
