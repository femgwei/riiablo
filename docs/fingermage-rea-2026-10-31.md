# FingerMage 客户端回调与生命周期复核（2026-10-31）

## 范围与结论

本批只处理 CltDo15 拖尾的客户端时钟、到期边界和回归覆盖，不改服务端追踪、伤害、诅咒、AI、权威导弹数量，也不添加拖尾数量上限。

- D2MOO `source/D2Game/src/MISSILES/MissMode.cpp / MISSMODE_SrvDo11_FingerMageSpider`
  是服务端追踪依据；D2Common `Units/Missile.cpp` 确认相关访问器语义。
- 原版 1.10f MPQ 的 `fingermagespider`：CltDo=15，CltHit=10，
  CltParam=3/20/5/20/2，客户端子导弹仍为 fingermagespider。
- 原版 CltDo15 在调用通用更新前按 CurrentFrame（剩余帧）模 CltParam1 创建子单元。
  子单元 TargetX 被设为 0；每次回调先检查 `TargetX <= -CltParam2`，再递减。
  因而 20 次回调后 TargetX 才变成 -20，第 21 次回调执行删除；旧 Java 实现第 20 次即删除。
- CltDo15 的时钟不应依赖自己的 DCC 是否加载。本批解除该回调的 AnimationWrapper 门禁，
  避免冷加载暂停主体生成相位、延长未加载子拖尾的存活期。普通命中子动画继续保留等待资源策略。
- CltHit10 的 ProgOverlay 目标附着规则与现有实现一致，不重复改写命中逻辑。

## 原版静态证据

DLL：`G:\BaiduNetdiskDownload\Diablo II 1.10F\D2Client.dll`。
SHA-256：`94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282`。

- CltDo 表 `0x6FB91260` 的第 15 项指向 `0x6FB31DD0`。
- CltHit 表 `0x6FB913D0` 的第 10 项指向 `0x6FB366D0`。
- `0x6FB31E42..0x6FB31E73`：读取 TargetX、先检查负阈值、后递减。
- `0x6FB32048..0x6FB3214E`：剩余帧取模、TargetX 正值门禁、创建子单元并 SetTargetX(0)。
- IAT `0x6FB6D29C / 0x6FB6D2AC / 0x6FB6D5D0` 分别是
  GetTargetX #11141、GetCurrentFrame #11124、SetTargetX #11140。
- `0x6FB366D0..0x6FB36732`：读取 Missiles 行 +0x36 的有符号 ProgOverlay，
  正值时调用 `0x6FB5BA70` 附着于命中单位。

REA 6.8.0 / Ghidra 12.1.4 在 Windows 没有自动为上述回调完整建立函数边界。
因此本批使用 REA `read_bytes`、地址到原文件映射，与现有 objdump 的精确范围解码互证，
没有把反编译失败当成函数不存在，也没有修改 Ghidra 数据库或原版 DLL。

REA Evidence：

- 回调表：`ev_e54b3d9ec2c6a622237739da3ecec366354ddb420c87a6fdec3e2fd3ae1b1dc9`
  与 `ev_0b169f90580a3f78d105e8659a10ec47479e525930e6fbc0fff9a5963f5a5be5`。
- 倒计时：`ev_4971fe5aa624166e934c994ecd8fbaf6548928a136da949c1fff402c98df765a`。
- 生成：`ev_772ef918622460349a3a9497935a034714f4b3dda19ebdb2a915deae14c95dfb`。
- 命中：`ev_7e7d8270914340ff96f382bf4001c484134e904fb3fbcf9b56f01ee616f06ea7`。

本机完整导出在 `.gradle/rea-fingermage-20261031-evidence.json`（忽略的生成文件，不随 Git 推送）。
`FingerMageNativeClientEvidenceTest` 可在另一台电脑直接从同一 SHA-256 的原 DLL 重验表项、
导入槽与关键分支字节；未配置 D2_HOME 或缺少 DLL 时两项静态 oracle 会明确跳过。
这些均为静态观察，不是运行原版 DLL 的动态证据。

## 可重复验证

使用 JDK 17 和真实 1.10f 安装执行：

```powershell
.\gradlew.bat :core:fingerMageRegression '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' --no-daemon
```

独立 source set 仅编译本批及相邻导弹表现测试，不修改默认 test 的范围，也不依赖
当前尚未完成迁移的 Client/GameScreen 旧 UI 测试。报告在
`core/build/test-results/fingerMageRegression`；并行工作时可另用独立构建目录。

生命周期测试实际运行生产 MissileImpactPresentationSystem，使用记录型 factory 创建真实 ECS
子实体，验证本地/网络主体生成相位、连续出生位置、静止/非权威/不递归、冷加载、
第 20/21 次更新边界、主体删除后的清理，以及普通命中仍等待 DCC。
它不运行真实客户端网络收包，也不使用 GPU；AnimationWrapper 只是加载完成标记，
不能据此声称 DCC 纹理、透明度或像素画面已验收。

修改前 5 项生命周期测试中 3 项按预期失败（冷加载时钟及两项提前到期），2 项通过。
修正后扩大回归共 9 类、36 项，失败 / 错误 / 跳过均为 0；包括 6 项生命周期测试、
2 项原 DLL 静态 oracle，以及相邻导弹表现与 FingerMage 服务端回归。
生产代码和所选测试编译、模块边界检查均通过，使用 JDK 17 和隔离构建目录
`.gradle/fingermage-rea-validation`，避免与并行地图工作共享编译产物。

默认 `:core:test` 仍被既有 Client/GameScreen 旧 UI 测试的编译错误阻挡；
本批没有修改或跳过默认测试中的失败项，也不宣称全 core 测试通过。

## 仍需另一台电脑实机验收

用同一 1.10f MPQ 对照 Act IV 扼杀者：首次冷加载与再次遭遇、主体飞行/转向、
拖尾密度和消退、单位命中亮斑与撞墙差异，以及网络观察端。
本批不把 ECS 或静态字节检查等同于原版/riiablo 双端逐帧画面对照，
也没有完成后续刺客陷阱及 Automap 批次。
