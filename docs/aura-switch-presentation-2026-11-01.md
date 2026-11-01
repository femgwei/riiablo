# 主动光环切换：自身旧状态与切换声音

## 原版依据及问题

D2MOO `source/D2Game/src/SKILLS/Skills.cpp`，
`D2GAME_AssignSkill_6FD13800`（约 2934–3001 行）：右键技能改变时，
立即移除旧光环的自身 stat list、关闭 AuraState、删除 PERIODICSKILLS；
新光环按 Immediate 分支执行，否则立即建立自身 AuraState 并安排周期事件。
这不是将所有队友的末次光环效果同时清掉。

当前 AuraManager 的 deactivateStream 原本只对装备光环立即移除自身状态，
主动光环也被留到 perdelay+1 自然到期。StateOverlaySystem 因此继续看到
旧状态，无法立即清除旧 DCC。改为两类来源都立即清除其拥有的自身 winner
状态，仍保留其他受影响目标的原生短期到期行为及来源过滤。
新主动光环仍由现有下一逻辑帧首次 pulse 发布，不伪造客户端状态。

原版 MPQ `States.txt` 的 Might/Prayer 行分别包含
`paladin_aura_might` / `paladin_aura_prayer` 的 onSound；
Skills 的 stsound/dosound 不是本次补音效的来源。
StateOverlaySystem 原本完全未消费 onSound，现在只在 aura 状态从无到有时
播放一次，通过所属世界的 SpatialAudio 做听者/位置检查。
状态刷新不重播，同步快照首次出现也沿用同一条路径。
没有新增音频资源，没有强制停止其他技能或装备光环声音。

## 验证边界

新增 AuraManagerPulseTest：切换当次移除旧自身状态而不移除队友、
下一逻辑帧发布新状态、重复选同技能不重启、切出光环立即清理自身。
新增 StateOverlaySystemTest：真实 MPQ 的 onSound 字段、首次播放、
重复刷新静音、换光环后旧图层清理与新声音只播放一次。

这些是源码/数据与自动化回归证据；没有运行原版客户端录音或逐帧对比，
不代表已验证所有多光环组合的原版声音仲裁。

使用 JDK 17、独立验证 buildDir 执行 AuraManagerPulseTest 和
StateOverlaySystemTest，编译及测试成功；模块边界检查通过。
