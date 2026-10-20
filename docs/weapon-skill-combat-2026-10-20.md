# 六个武器近战技能的定点闭环（2026-10-20）

## 范围与依据

在普通武器批次之后，将 Frenzy（147）、Berserk（152）、Zeal（106）、
Sacrifice（96）、Jab（10）、Fend（30）显式接入具体手的 8.8 伤害来源。
Fend 的表名与技能 ID 为准，不是击退属性。依据为本地 1.10f MPQ 与 D2MOO
revision `0c0bbd1e6bef9181a3da1c046523af622a925416`：

- `D2Game/src/SKILLS/SkillBar.cpp::SKILLS_RollFrenzyDamage` / `SrvDo009_Frenzy`：
  calc1 增伤、calc4 转换，保留前次命中后叠状态、两手交替和重新选目标。
- `SkillBar.cpp::SKILLS_SrvSt39_Berserk`：先生成战斗记录并执行
  `AllocCombat`，再安装防御 -100% 状态；关键帧消费记录，不重新抽命中和伤害。
- `SkillAma.cpp::SKILLS_SrvDo007_Jab`：calc1 增伤；
  `SKILLS_SrvDo013_Fend_Zeal_Fury`：calc2 增伤、calc4 转换。
  保留本项目已有的三次 Jab、Fend/Zeal 目标链和动画生命周期。
- `SkillPal.cpp::SKILLS_SrvSt29_Sacrifice`：calc1 预填物理，设置
  `DAMAGEHITFLAG_1`；随后 FillDamageValues 不重新计算物理，也不抽武器精通／
  被动暴击／致命一击。晚加入的恶魔／亡灵／钝器目标 ED 不用于这份已填物理。
- `SkillPal.cpp::SKILLS_SrvDo064_Sacrifice`：取 AllocCombat 后减伤后的物理，
  以目标当前生命封顶，按 calc2 计算自伤；先执行自伤，再执行目标伤害与吸取。
  自伤不再重复套攻击者抗性或 PvP 系数。
- `D2Game/src/UNIT/SUnitDmg.cpp::SUNITDMG_ApplyDamageBonuses`：
  属性、精通、技能及公共 ED 合并后作用于 8.8 端点，独立 min/max ED 另加；
  排他抽样后再应用物理 SrcDam/128，保留大数提前除法。
- `D2Common/src/Units/Units.cpp::UNITS_GetAttackRate` 与
  `SUNITDMG_GetResultFlags`：玩家基础 AR 为 tohit + 5*(dex-7) + 职业因子，
  技能、物品和精通的百分比相加；非玩家技能 ToHit 是基础 AR 的平加项。

原始 MPQ 审计锁定六行的 SrcDam=128、自有元素 EMin/EMax=0。
本批没有分析或修改安装目录的 exe/dll，也没有引用未读取的网页。

## 实现与独立断言

`WeaponSkillAttackProfile` 仅接受这六个 ID，从各自正确的 calc 槽读取公式和硬点
协同。物理与装备元素分别计算，去掉 Zeal/Sacrifice 在结算后整体乘技能倍率的路径。
Jab 的负增伤、Zeal 首级合法的零增伤不会被缺省值覆盖。

`WeaponAttackSource` 沿用普通攻击已审计的活动武器组、一手／双手、装备本地 ED、
非武器附加伤害及佣兵来源。六技能的物理、元素、具体手吸取进入同一
`DamageSettlement`，再发布结算事件、死亡与 Vitals；不修改网络 schema。

狂战士与牺牲的 `Casting` 保存独立战斗记录、目标及一次性消费标记，消费前先清除
引用，避免重入或重复关键帧再次扣血。重新 set 和池化 reset 均清理记录。

新增测试：

- `NativeWeaponSkillTest`：6 项独立纯数值断言，覆盖加成端点、排他上界、抽样后
  SrcDam、转换小数、牺牲跳过暴击随机数、自伤封顶及大数提前除法。
- `WeaponSkillDamageTest`：6 项宿主公式／通道断言，覆盖六行首级数值、硬点协同、
  AR 百分比相加、元素不乘技能 ED、牺牲特殊分支和狂战士先转换后抗性。
- `WeaponSkillLifecycleTest`：13 项实际 ECS 流程，覆盖狂乱具体双手、Jab 三击、
  Fend/Zeal 目标链、狂战士开始快照与重复帧、牺牲自伤先于吸取、自杀不能被后续
  吸取救回、当前目标生命封顶、未命中／格挡、池化和 Vitals 往返。
- `NativePlayerCombat110fAuditTest`：新增 1 项六行原始表审计，不用类型适配器自证。
  狂战士旧测试的随机种子也移动到真正抽样的开始阶段。

## 复验

统一入口：

```powershell
./gradlew.bat :core:playerCombatRegression :core:monsterCombatAcceptance :desktop:compileJava :server:d2gs:compileJava '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' --continue --console=plain
```

本机使用忽略目录下的隔离 buildDir／项目缓存和低内存 init 脚本，单 worker，
不清理用户构建产物、不跳过失败用例。最终完整组合已通过：按本批新报告去重为 **362 个测试类、2,535 个用例**，失败／错误均为 0；`:core:playerCombatRegression`、怪物 12 个分层入口、`:desktop:compileJava` 与 `:server:d2gs:compileJava` 全部成功。期间发现并修正了热诚原始 Param 曲线断言和火焰爪测试的未固定命中随机性，统计只使用修正后的报告。

## 另一台电脑验收与后续边界

拉取本批后，优先核对两把差异明显的武器连续狂乱、切换武器组、狂战士物免目标、
Zeal 附加元素与协同、Jab 三击及 Fend 多目标；牺牲配吸血，分别测试低自身生命和
低目标生命，观察实际生命、死亡与联机同步。无头 serializer 往返不替代双机联机。

- 这是默认 1.10f 六技能的定点迁移，不是七职业全部技能完成；Fury、盾击、踢击、
  旋风、其他预计算技能、召唤／复活倍率、SrcMissDmg 和 Half2HSrc 不自动切换。
- 自定义 Mod 行新增技能自身元素范围、改变 SrcDam 或 HitFlags 等参数，需要另行
  审计完整 FillDamageValues 语义；本批物理 SrcDam 测试不表示所有 Mod 元素和长度
  缩放都已支持。没有重写技能 stat-list／事件队列的完整调度。
- 压碎性打击及其他 on-hit 修正器仍沿用现有流程，不宣称其时序已全部原版等价。
- 大生命值的 float 精度、初始种子还原、图形客户端、双机联机和原版 EXE 逐击采样
  仍是独立待办。本批不启动客户端或改动安装目录。
