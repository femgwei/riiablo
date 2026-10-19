# dark-magic 抗性显示借鉴（2026-10-19）

## 并行范围检查

当前活动任务 `r (11)` 修改怪物命中/减伤、战斗快照及相关回归；`r (12)` 修改
伤害结算事件、受击动作、AI 通知及客户端持续/瞬时表现。两者共同经过的战斗调用链
不是本批修改范围。本批只涉及 widget 抗性显示、新测试和本文件。
不编辑共享的进度路线图、构建脚本、网络 schema、StateUpdater 或战斗数值模块。

## 上游依据与本地差异

- 上游默认分支 `deafc12a`，抗性相关 PR #456，核心提交 `9377b207`、`23f9c6df`。
  `ui/character_resistances.lua` 对权威有效抗性按有效上限进行显示截断；会话层对可变
  resistance 指针作值复制，并验证 owner 隔离和历史快照不被后续更新串改。
- riiablo 已有四元素（包括上游尚缺的毒素）及难度惩罚；CharData 初始化玩家的
  `max*resist` 为 75。本批读取本地已经解析的上限，不再次加 75 或重复扣难度惩罚。
- 旧 StatLabel 用原始抗性作为显示数字，仅颜色查询上限。因此 140 抗性、75 上限会
  显示金色 140；现在显示金色 75，原始 140 继续留给权威数值使用。
- ResistanceDisplayValue 只保存 primitive 值，不保存复用的 StatRef 或 Attributes；
  后续属性变化不改变保留的显示快照。上限下降而原始抗性未变时，显示仍会刷新。
  缺少上限时保留原值，不猜测缺失数据的含义。普通属性不走抗性截断。

## 版本和验收边界

D2MOO `D2Game/src/UNIT/SUnitDmg.cpp` 的抗性结算用于区分实际抗性和上限；
本批不修改该结算，也不将其当作 D2Client 面板实现的证明。面板显示截断参考上游
行为实现，原版 1.10f D2Client 的逐指令/像素一致性尚未验收。

本批没有更改网络同步，测试中的两份 Attributes 是两个玩家的独立属性实例，
不是双客户端连接。不能据此声称完整网络抗性链、重连或原版画面已通过。

体力加点继续保留后续核查：已找到 D2MOO `PlayerStats.cpp` 的最大值→当前值→封顶
顺序，以及 `ItemMode.cpp` 的最大资源改变比例回调。完整接入涉及正在变更的临时状态
上限/资源写入职责，因此本批不接入。NPC 投影端到端门槛也保留到共享表现改动稳定后。

## 验证

在独立临时 worktree 中，以 `3e988c3e8` 为基线，仅复制本批的源文件和测试，
通过 D2_HOME 注入合法的本地 1.10f MPQ，运行：

```text
./gradlew.bat :core:test --tests com.riiablo.widget.ResistanceDisplayValueTest --tests com.riiablo.widget.StatLabelColorizerTest --no-daemon
```

结果：BUILD SUCCESSFUL，ResistanceDisplayValueTest 3 项、StatLabelColorizerTest 2 项，
共 5 项通过，零失败、零跳过。覆盖四元素不同上限、原始/保存属性不被截断、上限撤销、
负抗、历史快照不变、其他玩家隔离、缺失上限和非法属性拒绝。
