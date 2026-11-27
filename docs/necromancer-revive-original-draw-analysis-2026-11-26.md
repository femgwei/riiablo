# 复活：本地移动、灰色调色和支配骷髅加成

本轮针对用户报告的单机复活怪物不移动，以及灰色外观、支配骷髅加成两个疑问。
这些是已有 Revive 实现的遗漏；清单中的 `not_compared_in_class_batch` 不表示此前没有研究。

## 已确认的问题与修改

### 本地复活的物理体没有重建

`DeathHandler` 在怪物进入 DD 后移除 Box2DBody、Velocity、MovementModes、AIWrapper 等。
`ServerEntityFactory.reviveMonster` 原本已经恢复速度和移动模式，并注入 NecroPet AI。
客户端仅覆盖普通 `resurrectMonster`，玩家技能走独立的 `reviveMonster`，没有重新挂上物理体。
因此 AI 可以发出路径请求，而本地 Box2D 没有实体可推动，表现为站着不动。

`ClientEntityFactory` 现在让两个复活入口共用恢复方法，重建物理体标记、动画描述、包围盒和选择标记。
保留尸体实体 ID、原 MonStats 与技能槽；没有把所有复活怪物替换为骷髅。

另一个明确缺口是 Pathfinder 的主人跟随判定只认女武神。携带 MissA1/MissA2 的复活怪物
会把主人的位置套入敌人的远程射程停止条件，过早取消跟随。现在所有非被动、非骨墙宠物
都按其 ownerId 识别友方跟随目标；敌方追击仍使用原射程判断。

`ReviveLocalMovementTest` 使用真实客户端工厂、NecroPet、Box2D、前后同步系统，
把地图和直线路径请求作为受控夹具，验证复活后 AI 请求跟随、速度进入物理步进、位置提交。
这不是完整地图寻路或原版移动速度对比。

### 复活灰色状态未进入客户端调色

1.10f States.txt 的 revive 状态是 ID 96，`colorshift=73`、`colorpri=85`。
原 StateOverlaySystem 只选择 COLD/FREEZE/POISON 的状态色，忽略 REVIVE。
现在复活状态选择原版 PL2 色表第 73 行，逐动画层应用；更高优先级的元素状态仍可覆盖它，
移除后恢复组件原色。每次更新都会处理驻留图层，以支持状态先于素材加载到达。

测试直接读取本机 MPQ 中 ACT1/Pal.pl2 的第 73 行，验证其挂到动画层的 transform row 1，
不改写组件基础颜色，状态删除后清除。无窗口测试使用受控调色对象，不创建 GPU 纹理；
没有据此宣称最终 GPU 黑灰色输出与原版逐像素一致。

### 支配骷髅的伤害与生命公式算成零

Revive 的 `aurastat damagepercent` 使用：

```text
skill('Skeleton Mastery'.lvl) * skill('Skeleton Mastery'.par4)
```

SkillFormula 原本没有正确支持跨技能 `.lvl`、`.parN`；召唤生命 calc1 又没有传引用技能行。
现在支持这些参数，并为召唤公式区分 `.blvl`（学习点）与 `.lvl`（有效等级，含现有装备与状态加成）。
未拥有技能时，全技能状态加成不凭空赋予支配技能。

1.10f Skeleton Mastery Param3=5、Param4=10，因此支配骷髅每级给复活怪物增加 10% 伤害、
5% 生命；Revive 自身 Param1=200，另加 200% 生命。技能被称为 Skeleton Mastery，
其复活怪物加成确实属于原版表项。

集成测试覆盖无支配、无支配但有全技能状态、支配 5 级、支配 5 级再加 2 级四种情况。
基准生命 40 时对应 120/120/130/134；固定物理基础伤害 10 对应 10/10/15/17。
伤害通过实际 CombatSystem 结算检查，而非只检查属性字段。原有怪物弹体伤害回归单独涵盖
damagepercent 的快照、碰撞与结算。没有宣称所有怪物特殊法术均获同样物理加成。

### 复活光效位置与重复创建

通用 CltDo0 原先把 `cltmissilea=revivemedium` 当作从施法者生成的弹体。
现在成功复活后由权威路径在尸体位置生成 presentationOnly 的 revivemedium，客户端通用回调跳过
该技能，避免在施法者脚下额外出现一份。当前重建采用已观察的 medium 变体；small/large
选择规则未在原版运行时采齐，保持为未覆盖项。

## 原版最小化采集证据

任务 `necromancer-revive-d2gfx`，run `20261126-090911-e258bbca`。
本轮复用此前已有采集结果，没有前台恢复、抢焦点或全局输入。

- 原始：`tools/original-observer/broker/20261121-165133-4490d598/runs/20261126-090911-e258bbca/orchestrator/process-session/001-revive-d2gfx-minimized/d2gfx-call-trace.jsonl`
- 归一化：`tools/original-observer/necromancer-revive-original-draw-2026-11-26.json`
- 重建脚本：`tools/original-observer/normalize_d2gfx_necromancer_revive_trace.py`

原版沉沦魔尸体 class 0 / unit 28，坐标 (4521,5646)，施法者 (4521,5642)。
捕获 16 次 missile class 269 的绘制，全部 `window_state=minimized`，屏幕坐标固定 (256,264)，
时间 1109..2063ms。采样帧为 1,2,3,5,7,8,10,12,13,14,16,18,19,21,23,24。
实际 Revive.dcc 为单方向 25 帧；采样不等于每帧均已观察。

原生采集器 `passed`，5373 events，`detached=true`、`cleanup_confirmed=true`。
外层 process-session/broker 任务是 **failed**，原因为 `Original exited`。
两种状态分别记录，不能将整个任务写成通过。

公开重建 D2MOO 的 `source/D2Game/src/SKILLS/SkillNec.cpp`，SrvDo058 和
InitializeRevivedMonster 支持同实体复活、主人绑定、REVIVE 状态、重新调度 AI 的设计。
源码推断、原版绘制采样、Riiablo 无窗口测试是不同证据层级。

## 验证和剩余范围

JDK 17，Gradle 既有缓存；core 与 server:d2gs 分开执行。
最终 core 9 个测试类共 74 项、d2gs 2 个测试类共 7 项，合计 81 项全部通过，
0 失败、错误或跳过。`git diff --check` 通过。
最终逐项 XML、准确数量和命令归档在
`tools/original-observer/necromancer-revive-validation-2026-11-26/validation.json`。
日志为 `necromancer-revive-validation-2026-11-26.log` 和
`necromancer-revive-d2gs-validation-2026-11-26.log`。

原版复活怪物的持续运动、攻击、灰色最终绘制、small/large 光效、到期消失均没有匹配的
运行时双端比较，`individual_runtime_status=observed_minimized_d2gfx_partial`。
本轮完成确定遗漏的实现与定向回归，不把这些未采集项标成 runtime passed。

结束检查：broker ready、active/child_pid 为空、cleanup_blocked=false，无残留原版进程。
保留现有无关修改、接收器 heartbeat、`.gradle-user/` 与 `.tools/flutter/`；后者是另一个
Flutter checkout，未当成本轮技能工作处理。未提交或推送。
