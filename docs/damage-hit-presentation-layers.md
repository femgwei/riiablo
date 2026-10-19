# 伤害、受击动作与状态表现分层（2026-10-19）

## 职责边界

- 数值计算继续由 `CombatSystem` / native combat 模块处理：命中、防御、伤害通道、
  抗性、减伤、吸收和吸取。不在客户端重新掷骰。
- `DamageSettlement` 是扣血入口。`prepare` 发布可修改的 `DamageEvent`，供能量护盾、
  白骨装甲、反伤和冰甲等权威回调使用；调用者按原有顺序处理吸收/状态；`commit`
  写入生命后发布不可变的 `DamageAppliedEvent`。保留两种历史不足 1 HP 策略，
  不借分层修改技能伤害公式。重复提交同一请求不重复扣血。
- `finishDeath` 留在技能自己的碎冰/终结技等副作用之后。按请求及单位双重去重，
  支持主要目标由近战调用者统一发布死亡；复活后首次合法伤害重置单位死亡闸门。
  环境火焰仍可把击杀归属记到 owner。自杀/献祭生命代价和显式处决不伪装成普通命中。
- `HitReactionResolver` 统一近战与导弹的 GH/BL 转换及怪物 soft-hit；模式仍经既有
  CofReference/Sequence 同步。格挡尊重 Skills.txt `interrupt`，死亡动作不被受击覆盖。
  保留近战 Casting、导弹已开始 Sequence 的旧 soft-GH 策略和亚马逊被动防御兼容策略。
- `DamageHandler` / `ElementalHitPresentationSystem` 仅消费扣血后的通知，处理声音和
  通用元素闪光。DOT 不逐帧播放痛叫/撞击，原生导弹客户端命中表现继续由导弹层拥有。
  客户端不再通过 `AI.hit()` 修改权威寻路缓存；服务端 `AIStepper` 处理已扣血的 AI 回调。
- `StateOverlaySystem` 直接读取同步的 UnitStates 与 States.txt `overlay1..4`、
  `castoverlay`、`removerlay`，诅咒无需伤害事件也能显示，重连快照也可恢复持续层。
  `OverlayManager` 分别持有每个持续/瞬时效果及资源引用；渲染、逐帧更新、灯光遍历
  全部层，按 PreDraw 分到身体前后。清理一个状态不会清掉另一个增益或命中闪光。

## 原版依据与保留边界

先核对本地 `F:/3rd_src/D2MOO`：

- `D2Game/src/UNIT/SUnitDmg.cpp` 约 1137–1145：近战/导弹伤害单位事件先于生命写入；
  约 1960–2120：玩家/怪物死亡、格挡、击退、get-hit、soft-hit 分支；约 2322：伤害 overlay。
- `D2Game/src/PLAYER/PlrModes.cpp` 的 `PLRMODES_First`：Skills.txt interrupt 门槛。
- `D2Common/src/DataTbls/DataTbls.cpp` 的 States 编译字段，以及
  `D2Common/include/D2DataTbls.h:D2StatesTxt.wOverlay[4]`：四个状态表现槽与 cast/rem 字段。
- 集成测试直接读取本机 1.10f MPQ 的 States/Overlay/Skills，不以记忆手填原版值。

本次是职责拆分，不宣称完整实现原版 FHR 门槛/动作速度、击退、亚马逊 dodge 动作，
也不宣称所有状态客户端 activefunc、声音、`nooverlays/notondead` 等标志已完整移植。
D2MOO 不含完整 D2Client 视觉实现；本轮尝试读取 The Phrozen Keep 状态指南超时，
未将不明确的客户端细节写成确定的原版规则。

未新增网络 schema：持续状态及 GH/BL 继续使用现有 StateP/COF 同步。新的
DamageAppliedEvent 是世界内事实通知，不是新增的网络伤害广播；本轮不声称补齐了远端
客户端所有通用元素命中声音/overlay 传输。

## 验证与另一台电脑复验

专项包含统一扣血时序、护盾抵消/吸收、fractional HP、DOT、死亡去重/复活、终结技死亡归属、
近战/导弹格挡/Jab、原生诅咒状态、多层资源释放、提交后才播放特效。
同时回归现有职业、反伤、护甲、陷阱、神殿、导弹和死亡场景。

另一台电脑拉取后，优先实机检查：

1. 近战与弓箭命中：GH/BL、Jab 不可打断、死亡不被切回受击。
2. 白骨装甲/能量护盾、反伤、冰甲：生命/法力变化及死亡奖励只发生一次。
3. 诅咒与增益同在时再受元素攻击：持续图层不被瞬时闪光替换；单独解咒/到期只清自己。
4. 毒/灼烧不每帧痛叫；冷伤击杀碎冰、聚气终结技范围击杀、复活后再次死亡。
5. 联机及重连：状态图层与 GH/BL 同步；远端瞬时元素特效按上述既有网络边界单独记录。

服务端编译通过；真实 1.10f 隐藏营地完成 55 帧，manifest `result=PASS`，
`CLIENT_SHUTDOWN result=PASS`。营地烟测只覆盖启动、渲染及清理，不替代上述受击实机验收。
manifest 位于 `desktop/build/visual-tests/damage-layer-20261019/rogue-encampment-manifest.txt`。

扩大回归订正了毒环旧测试的边界：零毒抗目标受到施法快照内 10% 穿透时，安装后的每帧
毒伤按 110% 计算，再转换 8.8；不能再与穿透前的导弹上限比较。依据为 D2MOO
`SUNITDMG_ApplyResistancesAndAbsorb`（约 957–962），未改生产毒伤公式。
另补齐旧生命周期测试的 clientContext 注册，并更新甲虫测试消费扣血后的通知。

并发任务曾覆盖默认构建目录，引发已有类的 ClassNotFound；最终回归改用任务独立构建目录。
最终独立构建验证：76 个测试类、508 项通过，失败/错误/跳过均为 0。
自动化日志位于忽略的 `core/build/damage-layer-isolated-verified.log`；JUnit XML 位于
`build/damage-layer-isolated/core/test-results/test/`。`git diff --check` 通过。
