# dark-magic 剩余候选一次性移植（2026-10-19）

本批统一处理此前列出的四项：普通怪物固定吸取、完整耐力上限联动、
Blade Fury 服务器/客户端导弹分离回归、声音重定向适用性。前三项落实代码或测试；
最后一项经真实 1.10f 数据核对不适用，不添加不存在的 Redirect 字段。
不包含同时进行的城镇屋顶与 Finger Mage 飞行/表现改动。

## 参考依据

- dark-magic `0ed24e8a` / `5bb71212`：吸取候选；以 D2MOO 的生成与结算为准。
- D2MOO `D2Game/src/UNIT/SUnitDmg.cpp`，SUNITDMG_FillDamageValues：
  普通怪物按生命、法力、耐力顺序滚动 source-stat 端点（<<8），
  再加 SrcDam/128 的份额；默认128并非只乘一次，而是增加相同份额。
  只有生命吸取通道加入总生命伤害，法力/耐力独立扣资源。
- `D2Game/src/MONSTER/MonsterMode.cpp`，sub_6FC627B0：
  EType6/7/8 写对应 drain min/max；同通道后行覆盖而非累加。
  ApplyCriticalDamage 不翻倍 drain，且 drain 不走五元素抗性/吸收。
- `SUnitDmg.cpp`，SUNITDMG_ExecuteEvents：普通怪物恢复生命时，生命吸取受
  已封顶的物理伤害约束，法力/耐力贡献分别封顶到受害者原有资源；合计按
  MonStats.Drain 缩放，封顶到攻击者缺失生命。仅耐力吸取不会进入恢复外层门。
  实际扣目标法力/耐力不按 Drain 缩放，扣后不足256 raw units归零。
- `D2Common/src/Units/Missile.cpp` / `D2Game/src/MISSILES/MissMode.cpp`：
  命中读取发射快照上的 drain MIN，不重新滚动、不再<<8，也不查询 owner 当前值。
  来源复制生命/法力通道；不虚构 source stamina 继承。表自带 life/mana/stam
  则生成 raw8.8 drain；stamina不参与 CalculateFinalDamage 的 SrcDam 缩放。
- dark-magic `f51cd620` 与 D2MOO `PlayerStats.cpp` / `ItemMode.cpp`：
  最大耐力改变先按旧/新上限比例重算当前耐力，再加入分配体力点的固定职业增量。
  旧上限分母至少256，保留零，正值截断并封顶。
- `D2Common/src/D2StatList.cpp` op9：只有 bonus vitality 贡献额外耐力，
  使用 CharStats.StaminaPerVitality <<6，不重复计算基础体力。
- dark-magic `eecd52c9`：Blade Fury 故意使用不同逻辑/视觉导弹的回归保护；
  riiablo 的 SrvDo048 已使用 srvmissilea，本批增加保护测试，不改为客户端回退。
- dark-magic `b3715dd0`：声音 Redirect 后需从目标组起点选组成员；
  本地 1.10f MPQ 的 Sounds.txt 表头只有 Sound/Index/FileName/Group Size 等列，
  没有 Redirect，故该变化不适用于本项目的数据版本。

## 实现边界

### 固定吸取

NativeMonsterModeElements 与 NativeAttackData/NativeDamagePacket 显式区分固定
drain 与玩家/佣兵百分比吸取。普通怪物 melee 请求、零生命伤害资源命中以及
普通导弹 snapshot→collision→DamageSettlement 接通；不复用旧 lifeStolen 估算写血。
恢复发生在目标生命/资源写入前，扣法力/耐力发生在目标生命写入后，重复 commit
不会重复扣除或恢复。steallife 只在普通怪物实际恢复生命时播放。

继续保持此前范围：SrcMissDmg 来源导弹继承、特殊服务器回调改写 drain、全部技能
覆盖与玩家百分比分支对防守方法力的特殊后续扣除，不宣称在本批实现。

### 最大耐力

Attributes 持有唯一的 runtime StaminaMaximum；装备重算、StateUpdater、分配体力点
共用它，移除 UnitStates 重复的耐力基础/结果缓存。相同 stat 的装备与状态百分比
先合并，再对未加百分比上限计算；162/163 两种 stat 各自截断。按 D2StatList
的 nPreviousOpStatValue，装备 bonus vitality 的职业 raw8.8 增量独立加入，
装备每级耐力等 op 增量也独立加入，不再乘上限百分比，也不重复计算基础体力。
升级增长同样接入共享上限逻辑。
装备重算保留实时当前耐力，ItemData 持久化重算后的值，不能把旧值覆盖回来。
分配体力点只把固定增量写入 base maximum，状态刷新不会再次放大已解析上限。
不改变生命/法力现有百分比策略。

### 回归与适用性

Blade Fury 测试令逻辑导弹 Range40/Vel22、视觉导弹 Range33/Vel99，验证权威
类型、生命周期、速度及武器快照仍来自服务器；缺少服务器声明不回退视觉导弹。
声音测试直接读取安装资源的 Sounds.txt 表头，保护 1.10f 适用性结论。

## 验证

使用本地 1.10f MPQ，独立 buildDir 与独立 project-cache-dir，避免并行 Gradle
的 stale-output 清理互相删除编译结果。相关回归全部通过：core 21 个测试类、
245 项，server-domain 全模块 29 个测试类、286 项，共 531 项；
失败、错误、跳过均为 0。
自动测试不代替另一台电脑的实际战斗、声音/画面和联网验收。
