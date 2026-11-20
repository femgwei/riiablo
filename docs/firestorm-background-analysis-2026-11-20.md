# 德鲁伊火焰风暴：原版后台采集与实现差异

对象：Firestorm，skill 225，原版 1.10f。目标是定位客户端展示缺少或错误的步骤。本轮完成核对、采集工具和离线诊断，**尚未修改 Firestorm 生产实现**。

结论：差异同时存在于火流路径、服务端沿途火区、客户端铺火和动画时钟。当前三条服务端火流重合，客户端用固定偏角与固定间隔另铺视觉火焰；这不能重现原版的一条主流、两条转向侧流及独立的客户端子火链路。地面火存活时间翻倍，且缺少原版短寿命火焰的倒播熄灭。

## 采集条件与隔离验证

- 原版目录 `G:\BaiduNetdiskDownload\Diablo II 1.10F`，固定 loader `D2v110各版本免CD全功能启动.exe -w -res800`。
- 成功批次 `20261120-114630-68996c25`，沿用常驻管理员 broker 24896，无新增 UAC。
- 私人桌面 `riiablo_poison_5c098db594f3`，86 次检查确认其不是输入桌面；输入桌面前后均为 Default，前台句柄前后均为 38472606，分辨率前后均为 1920×1080，`switched_desktop=false`。
- 专用角色 CXFirestorm，Druid class 5，仅一级 Firestorm，无装备、无协同，地图种子 127897359。实际 Save 目录来自注册表，位于 `G:\BaiduNetdiskDownload\Diablo II\Save`，未覆盖用户角色。
- 正常客户端定向右键施法一次，出生位置 `(4523,5642)`、目标 `(4531,5642)`，观察 240 次服务端更新。没有生成怪物目标。
- 调试器已脱离、断点恢复、采集实例关闭，常驻 broker 保持可用。

首次批次 `20261120-114346-b4ac1410` 沿用 Amazon 的投掷模式 11 判断施法开始，而 Druid SC 模式实际为 10，误报 `cast_started=false`。该批安全脱离并关闭；修正采集器的职业模式判定后重采成功，没有修改游戏逻辑。首次批次不作为本报告 oracle。

成功批次目录：
`tools/original-observer/broker/20261116-014209-3a0c898c/runs/20261120-114630-68996c25/orchestrator/`。
`worker/capture` 保留状态和绘制调用，`worker/provenance` 冻结当批程序和脚本。

## 原版执行链路

`SrvDo117` 创建三条 firestormmaker：一条普通主流，两条使用 charged-bolt 路径初始化的侧流。移动后，`SrvDo23` 沿途生成服务端 firestorm 火区。客户端 maker 的 `CltDo6` 根据路径变化创建自己的 firestorm，子火保留父路径的 fp16 精确位置；子火由 `CltDo5` 控制帧游标并在到期前倒播熄灭。

本次观测：

- 服务端：3 个 firestormmaker、36 个 firestorm，合计 660 条状态；maker 各 40 tick，火区各 15 tick。
- 客户端：3 个 maker、17 个 firestorm，合计 355 条状态；初次周期快照有年龄缺口。
- 精灵绘制配对 206 次，实际 DCC 选择 204 条，其中 maker 68、地面火 136；2 次缓存未命中没有计入选帧对照。

36 和 17 是**这一批一次施法的观测数**，不能硬编码为所有施法的目标数量。服务端伤害火区与客户端视觉火焰是独立链路，不能将所有服务端火区逐个显示来替代客户端逻辑。

## 已证实的实现差异

### 1. 三条服务端火流重合，速度偏慢

原版三个 maker 寿命均为 40 tick。主流始终直行；两条侧流分别出现 2、3 种速度向量，显示出转向。初始 fp16 速度 `(24576,0)`，即每 tick 0.375 子格、每秒 9.375 子格；转向分量为 `(17376,±17376)`。主流在年龄 39 时到达 `(4537.625,5642)`。

当前 `ServerSkillSystem.spawnDruidFirestorm` 三次调用 `chargedBoltDirection`，该 helper 返回同一个基础方向，且没有开启 `chargedBoltPath`。实际生产工厂生成三条完全重合的直线，速度均为每秒 8 子格，低约 14.67%；年龄 39 时三条均在 `(4535.473,5642)`。数量与寿命已经匹配，路径和位移尚未匹配。

机制参考：`D2MOO/source/D2Game/src/SKILLS/SkillDruid.cpp` 的 `SKILLS_SrvDo117_Firestorm`、`SKILLS_CreateMissileSpread`；`SkillSor.cpp` 的 `SKILLS_MissileInit_ChargedBolt`；D2Common 的 `PathMisc.cpp`。侧流初始化只在原寿命至少 78 时封顶 77，Firestorm 的 40 tick 不受此限制。

### 2. 缺少服务端沿途伤害火区

原版 `MISSMODE_SrvDo23_24_SuccFireBall_FirestormMaker` 在路径标记表明移动后，从移动前的整数位置创建 SubMissile firestorm。本次 36 个服务端火区均静止、出生在整数格中心，寿命 15 tick。

当前 `MissileCollisionSystem` 没有这条 SrvDo23 子火生成逻辑。真实生产诊断记录 `damage_ground_fire_births=0`。maker 自身仍带伤害快照，并可能进入现有碰撞流程，因此本轮**不能断言技能完全不造成伤害**；可以确认原版沿途短寿命伤害火区未实现。还没有进行怪物命中及生命变化对照。

### 3. 客户端用固定角度、固定频率铺火替代 CltDo6

当前 `ReferenceElementalPresentationSystem` 按 `seed % 3` 为视觉轨迹分配 `-0.2/0/+0.2` 弧度，每 `26/320` 秒铺一次火，每个效果存活 1.2 秒。三条轨迹不保证分别占据三个角度；本次生产分配 `[-0.2,0,0]`，两条重合，总计生成 60 个纯视觉火焰。`MissileImpactPresentationSystem` 因参考表现系统拥有该效果而提前返回，没有执行原生 CltDo6 链路。

这套参数来自参考 APK，见 [历史元素展示记录](elemental-reference-client-2026-11-07.md)，不能作为原版行为依据。

原版 CltDo6 检查 common#10234 的路径变化标记，并将父路径 fp16 精确位置传给客户端子火。本次 17 个客户端子火全部出生在非整数格中心，与服务端火区不同。不能直接套用 Fire Wall 的整数格铺火策略。

`CltParam1=4` 在这段代码中用于决定子火是否带 `0x4000` 不创建灯光标记，**不是每 4 帧铺火或 1/4 铺火概率**。本轮尚未完全归因 17 个子火的确切数量与客户端更新、路径标记及随机流之间的关系。

### 4. maker 动画播放偏慢

原版 maker DCC 为 1 方向、16 帧，动画增量 `unit_4c=512`，即每 tick 前进 2 帧。68 条实际 DCC 选择全部符合 `frame=(native_age×2)%16`。

当前 `MissileLoader.nativeFrameDelta` 返回 256，并使用通用时钟。用实际 MPQ DCC、生产 Loader 和 AnimationStepper 驱动对照，68 个样本有 63 个帧号不一致。修复还需处理整数 tick 与浮点时钟边界，不能只调整名义 FPS。

### 5. 地面火寿命翻倍，缺少倒播熄灭

原版客户端 firestorm 寿命为 15 tick，即 0.6 秒；DCC 为 1 方向、37 帧。本次所有 17 个地面火在观测到的年龄 1..14 对应帧号：

```text
age:    1 2 3  4 5 6 7 8 9 10 11 12 13 14
frame:  1 2 3 10 9 8 7 6 5  4  3  2  1  1
```

CltDo5 静态代码与此一致：remaining 到达 SubStart=12 时重置游标，随后进入倒播熄灭；短寿命火焰在进入中段随机循环前就已经开始退出。

当前视觉效果存活 30 tick，配置 12..36 循环、随机初相位，最后 0.4 秒透明淡出。以相同 native age 对比原版 136 条地面火选择，在本次生产生成的各个初相位下均有 90 条帧号不匹配；没有假定两边随机流一一对应。

## 证据文件与验证范围

- 原版重放 fixture：`core/src/test/resources/original/firestorm-1.10f.json`。
- 真实 MPQ 表行和 DCC 资源头：`tools/original-observer/firestorm-assets-1.10f.json`。
- 静态反汇编：`tools/original-observer/firestorm-static-client-1.10f.txt`，生成脚本 `inspect_firestorm_client.py`，固定 D2Client SHA256 后只读解析 RVA `906F0..90ACF`。
- 生产诊断：`tools/original-observer/firestorm-comparison-1.10f.json`，入口 `FirestormOriginalAnalysis.java`。
- 验证清单及哈希：`tools/original-observer/firestorm-validation-2026-11-20.json`。

生产诊断使用真实技能/工厂、碰撞、Reference/Impact 表现顺序、DCC 加载和选帧；仅屏蔽 GPU 纹理上传。没有地图障碍或怪物，没有 GPU 像素、颜色及 alpha 验收。原版 maker 的 68 条 CellFile 名称被采集器字符过滤，保留 `cell_name=null`、`cell_name_observed=false`；其 class 458、资源表映射及 1×16 资源头已经核对。地面火 136 条名称 `groundFiresmall` 均直接观测到。

本轮未连接 REA MCP，静态证据使用本地 Capstone/pefile。CltDo6 的历史 REA 依据见 [Fire Wall 客户端路径记录](firewall-client-path-2026-11-13.md)；没有为本轮生成或声称新的 REA Evidence ID。

26 项测试通过：DruidFirestormIntegrationTest 3、ReferenceElementalPresentationTest 20、OriginalFirestormFixtureTest 3。它们验证现有机制回归与原版 fixture 完整性，**不表示 Riiablo 已与原版一致**；差异由独立诊断输出明确报告。Python 语法检查、旧毒标枪/瘟疫标枪 fixture 导出字段兼容检查及 `git diff --check` 均通过。

## 复现

使用 Java 17 和现有 Gradle 离线缓存：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat -I tools/original-observer/asset-probe.init.gradle :core:test --tests '*DruidFirestormIntegrationTest' --tests '*ReferenceElementalPresentationTest' --tests '*OriginalFirestormFixtureTest' :core:firestormOriginalAnalysis --offline --console=plain --no-daemon
```

以上重放不启动原版，也不需要前台。重新采集才需向现有 broker 提交白名单 job `firestorm-private-desktop`；本轮无需再采集。

## 建议修复顺序

1. 恢复 maker 原生速度、定点位移和一主两侧路径，先消除服务端三流重合。
2. 实现 SrvDo23 沿途伤害子火，再用独立目标核对持续伤害。
3. 独立实现 CltDo6 客户端铺火，取消固定角度/频率替代，保留客户端视觉火与服务端伤害火的区别。
4. 实现 maker 每 tick 2 帧，以及 Firestorm 专用 CltDo5 倒播和 15 tick 生命周期。

先限定 Firestorm 的分支，避免直接改写所有地面火或其他参考技能。上述差异可继续通过后台状态与实际选帧验证，无需原版切到前台。

## 修复落地（2026-11-20）

按上述顺序已限定 Firestorm 完成修复：

- 新增 `NativeFirestormMotion`，普通主流保持原生速度，两个侧流使用以目标路径点为种子的两格 charged-bolt 路径；实际 120 条原版 maker 状态的坐标和速度逐条匹配。
- `MissileCollisionSystem` 在路径跨越整数格时创建独立的服务端 `firestorm` 子火，复制施法时的火焰伤害快照、穿透和 DamageRate；子火为零速度、15 tick 寿命，发射器结束后仍可继续伤害。
- 客户端不再由 Reference APK 的固定角度/间隔生成 Firestorm。CltDo6 改为按真实 maker 路径跨格创建视觉子火，服务端伤害火和客户端视觉火各自拥有一条链路。
- 新增 Firestorm 专用动画时钟：maker 每 tick 2 帧，地面火按原版 CltDo5 的 15 tick 生长、倒播熄灭顺序；DCC 延迟加载从实际年龄恢复。

修复后生产诊断 `tools/original-observer/firestorm-comparison-after-fix-1.10f.json` 报告：3 个 maker、36 个服务端伤害火、34 个客户端视觉火（单次生产模拟数量不作为固定协议），maker 68/68 帧匹配，地面火 136/136 帧匹配。新增 4 项 Firestorm parity 测试和 1 项沿途伤害/生命周期测试均通过。

本轮仍未启动原版前台，也没有做 GPU 像素、颜色/alpha 或带真实怪物的原版生命变化差分；这些应作为下一轮独立验收。
