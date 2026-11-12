# 2026-11-13 跨职业召唤初始化修复

依据本地 D2MOO 和 `G:\BaiduNetdiskDownload\Diablo II 1.10F` 的真实 MPQ 数据。
本轮接续德鲁伊修复；旧审计中已经实现的 Calc1、召唤被动、Summon Resist 不重复应用。

## 已修复

- `SkillNec.cpp / SetSummonBaseStats`：在原版调用点按召唤等级、难度、资料片规则追加
  MonLvl AC/TH。覆盖骷髅、石魔、骨墙/骨牢、Amazon、陷阱及 Raven/狼/熊/灵魂；
  藤蔓、Shadow、Revive 保留各自的原版等级路径。
- `SkillSor.cpp / SrvDo144`：三个 Hydra 均执行被动与 SumSkill 初始化。
  真实数据为 `MonStats.Skill1=HydraMissile, Sk1lvl=1`，通过
  `SumSkill=HydraMissile, SumSkCalc=lvl` 获得攻击等级；`Missiles.Skill=Hydra` 才是伤害行。
  火弹现在使用召唤等级、主人 Fire Bolt/Fire Ball 硬点协同和复制的火焰支配/穿透，
  并保存经过加成的 8.8 元素伤害包。公式解析器支持 `stat('name'.accr)`。
- 动态 AI 技能等级保存到 `Casting.monsterSkillLevel`，Actioneer 和服务端 SrvSt/SrvDo
  使用同一快照，检查点也保留它；影子不再只有日志显示正确等级、实际执行退回表默认等级。
- `SkillAss.cpp / SrvDo049`：保留主人等级、生命成长及每项使用第二公式的原版规则；
  补齐首次 AITHINK 的 20 帧等待、AuraLenCalc 临时寿命、装备生成。后续间隔仍用原表。
- 新增 MonEquip 加载和按 TXT 顺序的等级分段选择。女武神 ilvl 使用 Calc2，影子使用
  Param5/Param6。高等级装备先占栏位；空 item 只复制主人同位置的基础代码后重新生成，
  不复制原物品及词缀。候选位置、质量及等级使用原版表语义。
- 装备局部伤害/防御先通过物品属性更新器计算，再将全局贡献保存到大容量基础快照。
  主手物理伤害和武器 StrBonus/DexBonus 进入战斗，避免双爪物理伤害直接相加或重复力量。
  属性 reset 和检查点恢复不会丢失这些固定贡献。
- 召唤属性、装备、Druid SkillDamage 同时更新单独保存的 A2 攻击数据。SkillDamage 使用
  主人有效技能等级，协同使用硬点；真实表只有 Raven/狼/熊填写此列，未套给其他职业。
- 客户端从 SumOverlay 恢复诱饵的 `dopllezon_appear`，首次识别召唤身份时播放一次。
  临时召唤到期复用 SummonedPetSystem，不添加没有消费者的 UMod 标记。
- 刺客陷阱在缺少 serverContext 时回退到现有 MPQ，修复 `seq_chargesentry` 被空资源
  句柄跳过后退化为普通 S2 的问题；AssassinTrapSystem 仍唯一负责发射。

## 回归和验收边界

- 最终定向回归 18 类 / 183 项全部通过，失败、错误、跳过均为 0；
  `:server:d2gs:compileJava` 与模块边界检查通过，使用 Java 17 和本地 1.10f 数据。
- 新增 Hydra 1/20 级伤害、支配/穿透/协同、装备 1/17 级分段、主人基础代码复制、
  召唤 AC/TH、有效 SkillDamage/A2、动态施法等级/检查点、诱饵出现特效测试。
- 旧诱饵预期生命计入已经实现的 Calc1；Hydra 测试工厂创建真实实体承载属性。
  旧 v3 检查点样本构造同时移除 v4/v5/v6 的 AI/action/path 后缀。
- 扩大 Amazon 集合的爆炸箭断言 `explodingArrowCreatesAuthoritativeAreaSubMissile` 仍失败。
  将旧提交 `3f7ce6289` 的 Files、SkillFormula、MissileDamageResolver 编译后置于测试
  classpath 首位同样复现；此前路线图也已登记该问题，不宣称全 core 通过。
- 装备作为固定战斗属性快照保存；没有增加宠物物品交易/掉落接口。影子所有技能的
  左右手选择、装备触发技能及实机动画仍需各自专项验证，初始化测试不代表全部原版一致。
  另一台电脑重点复验 Hydra 等级差异、女武神攻击、影子施法、陷阱序列和旧狼/藤蔓行为。
