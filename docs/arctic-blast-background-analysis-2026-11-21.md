# 极地风暴客户端冰流核对（1.10f，2026-11-21）

本轮完成单条冰流的移动、寿命、动画时钟和独立客户端 A/B 资源接线。尚未完成玩家从按下、持续循环到松手的整段施法时序，也未采集 GPU、实际 DCC 方向选择或原版服务端伤害，不能将其登记为完整技能一致。

## 原版证据

成功 run：`20261121-181537-2a5ba484`，常驻 broker 会话 `20261121-165133-4490d598`。独立角色 `CXArctic`，德鲁伊、裸装、仅 Arctic Blast(230) 一点，其他技能零点，地图种子 127897359。同一进程在 Blood Moor 的 `(4522,5642)` 向 `(4530,5642)` 分别保持右键约 640 ms、1600 ms，每组观察六秒。

采用未显示的私有桌面 `riiablo_arctic_1f0abffcc609`，通过原版私有 HWND 的 PostMessage 输入和只读进程内存轮询采集。没有调试器附加、暂停、内存写入、远程调用、SwitchDesktop、全局输入或像素读取。成功报告核验输入桌面和屏幕尺寸前后不变，所属游戏关闭；常驻接收器保留。

采集是非原子的，不能据此宣称每个原版 tick 均被观察到。640 ms 组记录 6 条冰流、96 个去重状态；1600 ms 组记录 19 条、304 个状态，共 25 条、400 个状态。两组均出现 arcticblast1(459) / arcticblast2(460)，结束时无冰流残留。

- 原版每条冰流 TotalFrames=17，动画总游标长度 4096，步进 240，即 `(16<<8)/17`。
- 已观察年龄均满足 `cursor=age*240`，显示帧为 `cursor>>8`；本批次没有捕获年龄 1，不将缺失状态当作证据。
- 原版位置为出生格中心 fp16，轴向每 tick 移动 36864，即 0.5625 格，25 Hz 下 14.0625 格/秒。
- 每条状态均满足 `x_fixed=(4522<<16)+32768+age*36864`，y 不变。
- 玩家技能模式为 SQ(18)。短按组约 922 ms 返回 NU，长按组约 1937 ms 返回 NU；松手后仍观察到冰流出生和旧冰流收尾。这是采集器时钟上的观察，不能直接用这些毫秒值替代原生施法调度。

资源清单已读取真实 DCC 头：ArcticBlastIce / ArcticBlastIce_2 都为 16 方向、16 帧；ColdWindOverlay 为 1 方向、21 帧。资源头存在不代表本轮记录了它们的实际绘制调用。

`inspect_arctic_blast.py` 对固定哈希 D2Client 做离线核对：技能回调表 RVA `0xD75A8` 的槽 24 指向 `0x2A3B0`，转入 `0x2A1D0`。它与 Inferno 共用生成器：一次角色 RNG 选择 A/B，按 calc1 覆盖寿命，再设置动画率。技能 CltDo24 与导弹 pCltDo1 属于不同表。本地 Capstone/Win32 记录不标为 REA Evidence，也没有运行时调用堆栈。

## Riiablo 差异与修复

修复前的真实生产回放通过 ServerSkillSystem、ServerEntityFactory、MissileCollisionSystem、MissileLoader、AnimationStepper 运行：寿命和动画率已正确，但速度为 12 格/秒，位置走浮点路径；没有独立客户端冰流，只呈现服务端 arcticblast1。400 个观察中有 375 个位置不同、400 个速度不同，最大位置差约 1.3203 格。

`NativeInfernoMotion` 将 Arctic Blast 两行接入已审计的原生速度和格中心精度，两个技能共用的路径适配器保留不变。`NativeInfernoPresentation` 按技能与导弹行成对筛选，为冰流提供整数年龄动画时钟；原有 TTL、冷资源加载与 60 Hz 展示更新的分离机制因此也适用于冰流。

`InfernoPresentationSystem` 扩展到 skill230，每次收到技能客户端回调独立创建一个 A/B 客户端冰流，移除 Networked，标记 presentationOnly，不作为权威伤害实体。安装该系统的世界隐藏对应服务端载体；没有该系统的世界仍可显示服务端回退。`SkillCastHandler` 避免通用分支再次创建冰流。原版共享 helper 每次 A/B 选择只消费一次角色随机数，本地也移除了仅为展示种子而额外消费的第二次随机数；本地初始种子与原版分配器尚未对齐，不能宣称具体 A/B 序列相同。

没有改动伤害计算公式、冷却效果或持续施法的 mana 调度。相关已有测试通过，但本次客户端轮询不能证明原版服务端伤害一致。

## 验证、复现与剩余工作

限定验证日志 `tools/original-observer/arctic-blast-validation.log`：24 项通过，零失败、零错误、零跳过，覆盖 ArcticBlastOriginalParityTest(5)、DruidWindStreamIntegrationTest(5)、InfernoAudioSystemTest(2)、SorceressPresentationParityTest(5)、AuthoritativeAreaSkillPresentationTest(7)。保留的 XML 在 `arctic-blast-test-results/`。

客户端单条生产回放按年龄对照全部 400 个位置、速度和动画光标：无缺失、零差异。另验证 60 Hz 更新且 DCC 延迟 12 次更新后仍按正确年龄恢复，寿命到达后删除；连续 16 次客户端回调可产生两种冰流资源。测试中的回调由诊断程序提供，**不表示完整持键调度已复现**。本地权威轨迹也与同一客户端轨迹对上，这不是原版服务端轨迹或伤害的独立证据。

原始与导出入口：

- `tools/original-observer/broker/20261121-165133-4490d598/runs/20261121-181537-2a5ba484/orchestrator/worker/`
- `core/src/test/resources/original/arctic-blast-1.10f.json`
- `tools/original-observer/arctic-blast-before-fix-1.10f.json` / `arctic-blast-after-fix-1.10f.json`
- `tools/original-observer/class-skill-batch-2026-11-21/druid-arctic-blast-comparison-1.10f.json`

`export_arctic_blast.py RUN FIXTURE --batch-directory DIRECTORY` 核验成功状态、私有桌面、只读行为和角色输入哈希，去重并冻结源文件/采集器哈希。`compare_arctic_blast.py FIXTURE BEFORE AFTER OUTPUT --register-in-batch` 重建比较和覆盖索引。真实生产回放通过 `asset-probe.init.gradle` 的 `:core:arcticBlastOriginalAnalysis` 运行。不要用通用 Java 点分类名过滤测试，`probeIncludes` 必须使用 `**/ArcticBlastOriginalParityTest.class` 这种类文件模式。

失败记录保留：`181232-16913aae` 被角色固定名单拒绝，游戏未启动；`181402-63bfcdfe` 已读到有效冰流，但采集器误将 SQ 按 SC(10) 检查，因此标失败并关闭所属游戏。修正模式为 18 后重新完整采集，失败 run 不作为通过依据。初次本地诊断还修正了扩展目录资源键的斜杠归一化问题，失败日志保留在 arctic-blast-baseline.log，正式前置基线为 arctic-blast-baseline-fixed-harness.log。

下一步优先对照真实玩家输入路径：SQ 初段、客户端回调循环、转向与松手后的结束段，并核对 arctic_blast_cast 的挂载起止；继续采用后台私有桌面。当前未验收：这些施法阶段、实际 DCC 方向、遮挡/混合后的 GPU 效果、其他等级、其他方向、墙体/目标碰撞和原版伤害。

后续已补采 SQ/INFERNO 状态并修正 SC 回退、S1 源帧与持续输入路径，详见[角色序列记录](arctic-blast-caster-sequence-analysis-2026-11-21.md)。40 项相关回归通过，身体源帧阶段顺序匹配；两组仍各少一次客户端回调，绝对相位、原版服务端伤害和附着特效未宣称一致。另确认原版输入角色为99级、技能1级，完整输入回放已使用这一配置；此前“一级冰流”指技能等级。
