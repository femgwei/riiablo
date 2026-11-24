# 法师地狱之火：原版 1.10F 后台与 Riiablo 客户端核对

本轮目标是找出技能展示缺少或错误的步骤，不要求 GPU 像素对齐。已完成一级、无装备和硬点协同、正 X 方向的一次持续施法采集及生产回放。2026-11-21 后续已按该证据修复速度、单火焰选帧和独立客户端创建；以下差异章节保留修复前定位，最新状态见文末。

## 已定位的差异

1. **移动速度偏低，火焰伸展偏短。** 原版每 tick 位移 36864/65536=0.5625 格，25 Hz 下速度为 14.0625 格/秒；Riiablo 的真实工厂仍把表内 Vel=12 当作 12 格/秒，即每 tick 0.48 格，速度短缺约 14.67%。
2. **动画速率公式正确，但实际选帧落后。** 两份资源都为 15 帧，一级火焰寿命均为 10 tick，原版 8.8 帧增量为 384。Riiablo 也设置 384，但普通 `Animation.update(float)` 的累计时间仍造成偶数年龄落后一帧；116 个有效原版 DCC 样本中有 54 个与生产回放不一致。
3. **独立客户端喷射链路被服务端单位复用策略替代。** 原版技能 CltDo24 会选择并创建 A/B 客户端火焰。本轮服务端只有 14 个 infernoflame1，而客户端实际为 6 个 A 加 12 个 B，共 18 个。Riiablo 提前返回并复用服务端火焰，只能换 A/B 资源，无法单靠这个策略恢复独立的客户端创建时机和收尾。

本轮没有核对伤害量、耗蓝、免疫、墙体、不同技能等级或所有方向；不能据此认定这些方面一致或错误。

## 证据与后台边界

- 原版任务：`inferno-private-desktop`，run `20261121-073752-f6bf3010`。
- 原版角色：单独的 CXInferno，法师，技能 41 一级；地图种子 127897359，鲜血荒地。
- 输入：只向私有桌面的原版 HWND 投递按键消息；按住右键 40 个服务端 tick，再松开，连续记录到 120 tick。没有模拟伤害或强行调用渲染。
- 桌面：`riiablo_inferno_b8a8bae517de` 从未成为输入桌面；Default、1920×1080 和前台 HWND 在采集前后相同。结束后自有游戏关闭，调试器脱离且清理确认，桌面句柄关闭。
- 实际图形入口：118 次精灵提交及对应返回，其中 116 次观察到 D2CMP 的 DCC 方向/帧选择；两次缓存未命中样本保留原始记录但不作为选帧 oracle。
- `core/src/test/resources/original/inferno-1.10f.json`：归并后的客户端/服务端状态、DCC 样本、施法者原始字段和冻结采集器哈希。
- `tools/original-observer/inferno-assets-1.10f.json`：有效 MPQ 表行和两个 DCC 头部及 SHA256。
- `tools/original-observer/inferno-static-1.10f.txt`：哈希固定的本地 Capstone 静态反汇编；**不是 REA Evidence，也不是即时调用堆栈**。
- `tools/original-observer/inferno-comparison-1.10f.json`：实际生产工厂、技能、运动和动画系统运行一个火焰的结果；没有用手写公式替代生产系统。
- `tools/original-observer/inferno-analysis-2026-11-21.json`：自动生成的差分结果。

原始逐 tick JSONL、CSV、精灵调用和冻结 EXE/源码位于该 run 的 `orchestrator/worker` 下。未来修改采集器不会改变这次执行的冻结副本。原始 CSV 最初把施法者 +0x48 字段误标为 anim_speed，导出器现改为 `server_unit48` / `client_unit48` 原始字段名，不赋予未经验证的语义；导弹真正的动画速率从 +0x4C 读取。

## 技能回调与导弹回调不是同一张表

原版 Skills.txt：Inferno 的 srvstfunc=11、srvdofunc=19、cltstfunc=15、cltdofunc=24、seqnum=6；srvmissilea 和 cltmissilea 都是 infernoflame1，cltmissileb 是 infernoflame2。

原版 Missiles.txt：60/61 两行的 **pCltDoFunc 都是 1，pSrvDoFunc 也都是 1**。不能把技能 cltdofunc=24 当成火焰导弹的 pCltDoFunc=24。

哈希固定的 D2Client.dll ImageBase 为 0x6FAA0000。客户端技能 helper 在 RVA 0x2A1D0（VA 0x6FACA1D0），wrapper 在 0x2A3B0。静态代码能看到：

1. 读取技能客户端 A/B 导弹行，用施法者 RNG 选择其中一个。
2. 求 calc1 并至少取 1，使用 0x8020 创建新的客户端火焰。
3. 用选中导弹的 AnimLen 设置 `(AnimLen << 8) / lifetime` 的动画速率。
4. 根据 INFERNO 状态及施法者序列字段维持施法动画，而不是给已有服务端导弹附加一个子火焰。

Missile pCltDo1 的实际地址为 RVA 0x9D880：通过已有 CltDo5/6 连续表项反定位 slot 1，再读取其函数体。每个客户端火焰在该普通导弹回调中前进和到期。

服务端 `SKILLS_DoInferno` 在 D2Game RVA 0xE56F0；0xE5940 的 StartInferno 控制首次起手、INFERNO 状态及续放。此次静态字节与该部分 D2MOO 源码相符，但不应因此假设所有函数都与原版二进制一致。

## 资源、位置与寿命

两行火焰的 Vel/MaxVel 均为 12，表内 Range=30，AnimLen=15，LoopAnim=0，Trans=1；运行时一级技能 calc1=`ln12/2`，Param1=20，因此寿命覆盖为 10 tick，而非表内 30。

- A：Flamethrower，1 方向、15 帧，SHA256 `ab1f889649efb4980c274822c38275cb1d03950b745031dfe29cc5e0f57f8ddb`。
- B：Flamethrower2，1 方向、15 帧，SHA256 `6742e20a2bba9e1b4eeee767961f4f1e0494235a788d8bdcbca873429f4bc538`。

因为 DCC 本身只有一个方向，喷射方向来自火焰单位的运动轨迹。本轮不足以验证斜向运动，也没有依据把它改成八向旋转精灵。

原版每次火焰从固定小格中心 `(4523.5, 5642.5)` 创建。Riiablo 世界坐标与之比较时扣除 0.5，起点 `(4523,5642)` 对应一致；不要再加一个全局 0.5 位移来补偿速度错误。原版路径使用 16.16 定点数，当前 Inferno 仍未接入该分支。

在 age=8，原版归一化 X=4527.5，生产回放 X=4526.84，已相差约 0.66 格。需要恢复速度换算和定点路径，而不是简单拉长火焰 DCC。

## 动画选帧与独立收尾

原版选帧按 `(age * 384) >> 8`：age=0..9 分别为 0、1、3、4、6、7、9、10、12、13。这些是实际运行光标和 DCC 选择，两者一致。15 帧资源并不意味着在 10 tick 内每帧都出现一次。

当前生产回放为 0、1、2、4、5、7、8、10、11、13；age=2/4/6/8 各落后一帧。定位为 `AnimationStepper` 普通精灵的浮点累计路径；`MissileLoader.nativeFrameDelta` 的公式本身不用改。应以已完成模拟 tick 的整数 age 求固定光标，冷加载时也恢复到相同年龄。

本轮客户端火焰出生于服务端采样时钟 frame=467..501，间隔均为 2；松手在 frame=495，之后仍有 497/499/501 的新视觉火焰，最后在 frame=509 观察到存活火焰，510 已无火焰。客户端角色在 frame=503 返回待机。服务端最后一次创建在 494。这些时刻是本次观测值，不能硬编码成所有技能等级或所有负载下的协议。

当前 `SkillCastHandler` 对 Inferno 提前返回，令展示依附服务端单位；`MissileLoader.infernoVisualRow` 虽可选 A/B，却以每个导弹的分配种子选择，而原版 helper 用施法者 RNG。另一个 `MissileImpactPresentationSystem` 的 pCltDoFunc==24 分支，在原版两行火焰均为 pCltDo1 的情况下不会触发。因此现有注释所称“CltDo24 修改已有火焰”的解释需要纠正。

仍未在完整生产持键/释放链上回放本次输入，也未对齐原版所有 RNG 使用者。不能仅凭 14/18 的数量差就报告已精确证明 Riiablo 固定少四个火焰；已证明的是原版两条链独立，以及当前生产代码没有该独立客户端技能创建步骤。

## 建议实施顺序

1. 限定 Inferno 修复 75% 原生速度换算和 16.16 路径；保持现有 10 tick 寿命公式，不修改全局导弹速度。
2. 将 Inferno 动画接入 native age / 8.8 光标求帧，验证上述 116 个 DCC 样本；同时测冷加载和 60 Hz 展示更新。
3. 由技能客户端回调维护独立 A/B 火焰创建和持续/松手时钟，并隐藏对应服务端伤害载体的绘制，避免叠画、重复伤害。不要仅取消提前返回后再与服务端一起画。
4. 最后接通并核对施法者 SQ 起手、持续回卷和收尾；保留当前按 INFERNO 状态持有单个循环音效的策略。随后再扩展斜向、等级、墙体和受控伤害/耗蓝核对。

上述步骤不需要原版前台窗口。

## 本轮验证结果

采集器 x86 编译成功，Python 工具语法检查和 diff 检查通过。`OriginalInfernoFixtureTest` 的两项测试校验原版全部路径/光标状态和实际 DCC 选帧，属于冻结证据完整性检查，不是 Riiablo parity 测试。

最终限定回归共 **14 项通过，0 失败、0 错误、0 跳过**，包括原版 fixture、Inferno 服务端单流契约、资源选择、循环音效以及服务端展示复用契约。后者测试通过只表示现有代码契约成立，不能推翻本轮已经观测到的客户端独立创建链路差异。

此前扩展运行的 23 项中有 22 项通过，1 项现有 Blaze 测试失败：`SorceressFireAreaIntegrationTest.blazeStateEmitsOnlyAfterActualMovementAndKeepsFractionalDamage` 仍断言状态 duration=120，但当前实现返回 117。本轮该测试源码和生产源码均未修改；保留失败日志，没有把筛选后的回归冒充全部测试通过。

验证记录：`tools/original-observer/inferno-validation-2026-11-21.json`，包含生产源码哈希、通过与未通过的差分项、两轮测试范围与限制。最终限定回归日志 `inferno-focused-tests.log`，此前扩展运行日志 `inferno-validation.log`。

## 2026-11-21 生产修复

`NativeInfernoMotion` 将原生速度换算与 16.16 路径限定用于 infernoflame1/2；一级正 X 实际生产回放速度已为 14.0625，短缺率由约 14.67% 降为 0。`NativeInfernoPresentation` 按整数 native age 求 8.8 光标；116 个冻结原版 DCC 样本的选帧差异由 54 个降为 0。寿命仍取一级 calc1 的 10 tick，没有修改全局导弹速度。

新增 `InfernoPresentationSystem` 从 SkillDo 创建独立的客户端 A/B 火焰，用施法者维度的独立展示随机流选择素材；这些实体不联网、不参与伤害，服务端伤害载体的渲染被隐藏，防止叠画。这里独立生成的 RNG 初值尚未与原版施法者全部 RNG 消费对齐，不能宣称 A/B 数量和排序已经完全相同。

客户端视觉时钟独立累计 25 Hz tick。60 Hz 展示更新、延迟到第 15 次更新才加载资源、以及资源始终未加载均有回归；运动与 10 tick 清理不等待 DCC。`infernoPresentationTime` 明确属于客户端本地缓存，不写入服务端 checkpoint，恢复时清零。

新报告为 `inferno-comparison-after-fix-1.10f.json` 和 `inferno-analysis-after-fix-2026-11-21.json`，旧比较报告保留未覆盖。生产回放只比较单火焰生命周期；新增系统虽已恢复独立创建，仍使用当前 SkillDo 调度，**完整 SQ 起手、持键/松手回卷与原版 18 客户端/14 服务端出生节奏尚未重建验收**。伤害、斜向、等级与墙体覆盖也未扩大。

最终修复回归（两次不重叠的限定运行）：**63 项通过，0 失败、0 错误、0 跳过**。52 项技能/状态/协议/UnitState checkpoint 测试加 11 项 Missile checkpoint 测试；其中覆盖 60 Hz、冷加载/始终未加载的 Inferno 寿命，以及客户端本地时钟不污染服务端回放字节。验证记录 `tools/original-observer/sorceress-presentation-fix-validation-2026-11-21.json`。这不是全仓库测试通过声明；早期失败日志和范围限制保留。
