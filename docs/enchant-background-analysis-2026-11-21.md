# 法师强化（Enchant）：原版后台与客户端展示研究

本轮完成原版 1.10F 一级、无协同和火焰支配、装备普通右手标枪的两次自身强化，以及 Riiablo 生产系统无窗口回放。2026-11-21 后续已修复重复激活、native 时钟与混色路径；下文保留修复前定位，最新状态见文末。未做 GPU 像素验收。

## 结论

原版强化由一次性 `FireEnchant` 激活特效、持续 ENCHANT 状态和武器红色变换组成。没有生成常驻喷火导弹的步骤，不应靠增加持续火焰粒子来“补全”这个技能。

本次一级样本中，Riiablo 的火焰伤害加成 **8～10**、攻击准确率加成 **20%**、状态长度 **3600 tick（144 秒）** 与原版一致。再次施法刷新状态期限，不把伤害加成叠加两遍。这里只比较状态贡献，尚未比较实际攻击扣血。

已确认的展示差异：

1. **激活特效播放慢。** 原版 Overlay.AnimRate=16 被换算为 8.8 增量 `16 << 4 = 256`，在 25 Hz 下是 25 FPS；Riiablo 非 native overlay 分支直接把它当 16 FPS。17 帧素材的理论播放周期分别约为 0.68 秒和 1.0625 秒，不应把 AnimRate 当成 FPS。
2. **混色仍走旧分支。** 32 次实际 FireEnchant 绘制调用全部传入原版 draw mode=3；Riiablo 回放使用 `BlendMode.LUMINOSITY`。现有 `AuraRenderPolicy` 将原版模式 3 映射为 ADDITIVE，而强化尚未接入该路径。两套枚举不能直接比较数字大小：LUMINOSITY=2、ADDITIVE=14 属于 Java 枚举值域。
3. **刷新不重新播放激活。** 原版两次施法各调用一次 overlay 153 创建，均有完整返回并重新提交 FireEnchant 帧。生产回放在 tick 0 和 45 执行两次真实 SrvDo025，只记录到 tick 0 的一次激活请求；`StateOverlaySystem` 仅按 state id 是否首次出现判断，漏掉了状态仍在时的刷新激活。

## 原版数据与采集范围

- 技能 52：srvdofunc=25，cltdofunc 为空，施法模式 SC，起手声音 `sorceress_enchant`。不能按技能编号 25 推断客户端也应运行 CltDo25。
- States.txt enchant：id=16，`castoverlay=enchant`，`itemtype=weap`，`itemtrans=cred`；overlays1..4 无常驻特效。
- Overlay.txt enchant：索引 153，Filename=FireEnchant，17 帧，1 方向，AnimRate=16，Trans=3，InitRadius=1、Radius=9、RGB=255/178/64。
- FireEnchant.dcc SHA256：`c2ee8558719e81ffa9500925720f60431fa78191ae54010a3c3dbb667acdb162`。
- Colors.txt：cred 是 Crystal Red，表索引 9；Riiablo 的 packed transform 使用索引加 1，故为 10。

主 run：`20261121-081404-1ed47852`，经现有常驻接收器执行 `enchant-private-desktop`。独立角色 CXEnchant，地图种子 127897359，鲜血荒地；只向该未显示桌面内的游戏 HWND 投递鼠标消息。tick 0/45 按下、2/47 松开，采集到 100 tick。

保留了 202 条客户端/服务端状态、33 条特效对象状态、2 次创建/返回和 **32 次 FireEnchant 绘制调用/返回**。两次创建位于服务端采样时钟 frame=466、511；状态到期字段分别为 4066、4111，正好是生效帧加 3600。客户端快照中的到期字段为 0，因此期限比较只使用服务端字段。

两次实际绘制的帧参数均依次为 1..16；首帧创建后已经推进，不能因为本次没有提交 frame 0 就断言缺了一帧资源。原始对象的增量为 256，总光标为 `17 << 8 = 4352`。服务端和客户端异步，33 条对象样本中有一次重复年龄，不能用服务端采样时钟假设客户端每次一定只更新一次。

整个过程没有 SwitchDesktop、SetForegroundWindow、全局鼠标或强制渲染。输入桌面始终为 Default，屏幕分辨率保持 1920×1080；结束后自有游戏关闭，调试器脱离和临时代码恢复均已确认。前台 HWND 在批次内发生过变化，它由用户桌面管理；没有把这个变化算成游戏被切到前台的证据，也没有把 HWND 相等作为后台成立的必要条件。

## 函数与源码定位

原版版本由 SHA256 固定，沿用此前记录的 D2Game、D2Client、D2Common 1.10F 文件。本轮使用本地 Win32 进程探针和 Capstone 静态反汇编，不是 REA Evidence。

- D2Game RVA `0xE7040`：SrvDo025_Enchant，选择友好目标或回退自身，安装状态贡献并设置该状态的图形更新标志。
- D2Client RVA `0xBBA70`：overlay 创建和 native 时钟初始化；RVA `0xBBE13` 读取 AnimRate 并左移 4。
- 实际 FireEnchant 绘制 caller 为 RVA `0xBA6E0`，返回 `0xBA6E5`，经过 `0xCB850` 的图形导入 thunk。按传入 cell 的 `FireEnchant` 名称筛选，绘制模式读取真实调用栈参数。
- D2Client `0x88400` getter 返回 unit+0x54；创建代码把 overlay 链表放在 gfx+0x2C，next 在 overlay+0xA4。通过这条已验证链路采样动画光标。
- 状态、伤害和法力的原始读取，在三个检查点与 D2Common 原生 stat getter（RVA `0x77C30`，#10519）逐项核对一致；只调用 getter，没有伪造属性值。

生产定位：

- `OverlayManager.process` 的普通分支使用 `1f / entry.AnimRate`，并把 Trans=3 配为 LUMINOSITY；强化既不是 aura，也未列入已审计的 Barbarian/Paladin overlay 名单，`usesNativeRendering()` 返回 false。
- `OverlayStepper` 每 25 Hz 更新其浮点动画，本轮不是素材缺失，而是时钟单位和混色路径选择错误。
- `StateOverlaySystem.reconcileStateOverlays` 只有 `!previous.contains(id)` 才播放 castOverlay。同一 state id 的移除/重新添加发生在两个展示 tick 之间，previous 仍含 id，刷新便被吞掉。
- `ServerSkillSystem.applyEnchant` 和 `SorceressSkills.applyEnchantState` 已创建正确的一级状态数值并替换旧层；无需先重写伤害公式来修复上述展示问题。

## 武器颜色已做到什么程度

当前 `StateOverlaySystem.reconcileWeaponTransforms` 读取 ENCHANT 后应用 cred；生产回放得到 packed transform=10。模拟移除状态后，RH/LH 从 10 恢复到预置的 3/7，表明已有基础颜色保存/恢复路径。

这些基础颜色是无 GPU 回放中的合成输入，不是原版武器像素 oracle。尚未逐像素核对标枪的原版 palette，也未采集盾牌、双色武器、换装、双持、Venom 优先级或网络重连。不能将现有颜色测试通过写成“全部武器染色已经与原版一致”。本轮也没有自然等待完整 3600 tick 到期；原版期限来自原生状态字段，生产移除后的恢复是单独验证。

## 证据文件与验证

- `core/src/test/resources/original/enchant-1.10f.json`：主 run 的状态、特效时钟、调用与返回及冻结采集器哈希。
- `tools/original-observer/enchant-assets-1.10f.json`：MPQ 有效技能、状态、overlay、颜色行和 DCC 头部。
- `tools/original-observer/enchant-static-1.10f.txt`：范围受限的哈希固定反汇编。
- `tools/original-observer/enchant-comparison-1.10f.json`：实际生产技能、StateOverlaySystem、OverlayManager、OverlayStepper 的两次施法回放。直接派发 SkillDo，不模拟人物起手关键帧和实际音频。
- `tools/original-observer/enchant-analysis-2026-11-21.json` / `enchant-validation-2026-11-21.json`：差分结果、源码哈希、测试范围及早期尝试情况。
- 原始 JSONL、状态、冻结 EXE/源码位于主 run 的 `orchestrator/worker` 下。导出器复核执行文件及源码哈希、创建/绘制返回配对和清理状态。

早期 map overlay 绘制探针 `0xB7E4D` 没有命中 FireEnchant；该次失败记录未删除。后来从所有绘制调用入口筛选资源，找到了 type 4 附着特效实际使用的 `0xBA6E0` 路径。另一次在启动游戏前因存档归一化检查被拒绝：原版会压缩统计字段并归一化已装备物品的无用网格坐标。校验现按各自技能块偏移比较，并仅忽略装备物品的 gridX/gridY，仍严格校验物品其余字节、装备位置、角色和技能；没有覆盖已有角色。

**限定回归共 21 项全部通过，0 失败、0 错误、0 跳过。** 包括原版 fixture 完整性、Enchant 黄金数值、伤害归属、状态展示和已有 native overlay 策略。原版 fixture 测试检查冻结证据，现有代码契约测试通过也不代表本轮的三个展示差异消失。Python 语法、x86 编译、模块边界和 diff 检查通过。

## 建议修复顺序

1. 先只将 enchant 激活特效接入 native overlay 时钟与混色策略，保留 17 帧素材和长期武器颜色机制，不全局替换所有 overlay。
2. 给刷新激活提供一次性事件或刷新代号；同一状态仍保留单份数值，每次成功强化各播放一次短特效。避免靠反复删除/重建持久状态模拟激活。
3. 验证两次施法、冷加载和多人目标后，再扩展换装、盾牌、叠加其他染色及自然到期恢复。
4. 最后核对完整 SC 起手和目标音效，以及带协同/火焰支配的实际近战、远程攻击伤害；本轮不足以声称这些已经完成。

## 2026-11-21 生产修复

只将 `enchant` 激活特效接入现有 native overlay 策略，AnimRate=16 解释为每 native tick 增加 256 光标，即 25 FPS；17 帧资源不变。原版实际 drawmode=3 对应现有加法 overlay 策略，Java BlendMode=14，不再使用原来的 LUMINOSITY。`OverlayStepper` 按整数年龄求帧。

每次成功强化分配状态的 `presentationRevision`，`StateOverlaySystem` 根据新代号播放一次激活特效，重复网络快照不重播。该代号随 StateP 可选向量、不可变客户端快照和副本写入器传递；老包缺少向量时读取零。UnitState checkpoint 升级为 v7 并兼容读取 v1..6，复制和池复位也包含该字段，恢复后继续取活动代号最大值。

实际生产回放在 tick=0/45 均请求一次 enchant 特效，帧时长 0.04 秒，native 渲染为 true，BlendMode=14。原有一级 8–10 火伤、20% AR、3600 tick 状态期限不叠加，合成手部基础色 3/7 在移除状态后仍能恢复。新报告 `enchant-comparison-after-fix-1.10f.json` / `enchant-analysis-after-fix-2026-11-21.json` 六项限定检查全部为 true，修复前报告未覆盖。

上述结论仍受原有范围限制：没有原版武器像素 oracle、所有装备/目标组合、完整自然到期或实际攻击伤害验收；SkillDo 直接回放也不等于角色 SC 起手链已验证。

最终修复回归（两次不重叠的限定运行）：**63 项通过，0 失败、0 错误、0 跳过**。52 项技能/状态/协议/UnitState checkpoint 测试加 11 项 Missile checkpoint 测试；其中覆盖 60 Hz、冷加载/始终未加载的 Inferno 寿命，以及客户端本地时钟不污染服务端回放字节。验证记录 `tools/original-observer/sorceress-presentation-fix-validation-2026-11-21.json`。这不是全仓库测试通过声明；早期失败日志和范围限制保留。
