# 技能移植交接记录（2026-10-01，Impale quantity/Calc3 durability gate 后）

## 当前 Git 基线

- 仓库：`F:/3rd_src/riiablo`
- 分支：`master`
- HEAD：`346a904ee7638d3d75ff9bd28cd443e38ca7976c`（真实 MPQ Impale quantity/Calc3 durability gate 及交接文档）。
- 远端：`origin/master=346a904ee7638d3d75ff9bd28cd443e38ca7976c`，已用
  `git ls-remote origin refs/heads/master` 核对；push 仍可能输出 credential storage lock
  警告，但远端 SHA 已确认一致。
- 最近关键提交：`e6e377b9` hostile Aura ECS 过滤矩阵，`df61e780` hostile Aura
  真实目标门槛，`bcba2e23` Paladin 20 Aura 清单。
- 本轮提交：`24b6fd9c` 真实 MPQ Impale quantity/非堆叠 Calc3 durability gate。

## dark-magic 对照基线

`F:/3rd_src/dark-magic/internal/content/d2legacy/manifests/skill-behavior-coverage.v1.json`
当前包含 43 个 exact-ID、16 个行为族；所有 manifest evidence 仍为 `partial`。
riiablo 已建立 43/43 的行为对照入口或测试映射，但这只表示“有对照”，不表示四层
验收全部完成。dark-magic 目标为 1.14d，riiablo 目标为 1.10f，数值和函数语义必须
回到 D2MOO/1.10f MPQ 重新确认。

## 已验证的高等级链路

- Sorceress：Fire Ball、Nova 的真实双客户端；Ice Blast、Glacial Spike、Teleport
  有 Native/ECS 首轮对照。
- Necromancer：Poison Nova 真实双客户端；Skeleton/Golem/Revive 事务、配额、回滚、
  召唤物重连有专项测试。
- Paladin：20 个已注册 Aura 的 `headlessPaladinAuraRegression`；Conviction=123
  验证 Boss/Prime Evil/`noAura` 命中和不可攻击/不可选中拒绝；Holy Freeze=114
  验证 `noAura` 命中和冷免疫拒绝，并覆盖重连、跨区撤销。
- Druid：Vine/Vine Beast/reconnect、Cycle of Life/Vines 尸体 recycler 的主要双客户端
  门槛已建立；trail 不应擅自推断毒素伤害。

## 当前优先级

Amazon 没有 dark-magic exact-ID 配置，必须单独按 D2MOO 1.10f 审计。优先处理 Amazon
30 行 Native 数据和当前未提交的 Amazon 测试，补真实 MPQ 的 Jab/Impale/Fend/
Power Strike/Charged Strike，以及失手、死亡、重连、弹药、耐久、ToHit/SrcDam/Calc
边界。当前 ECS 已锁定 Power/Charged 的成功命中耐久和关键帧前死亡清理；真实 MPQ
双客户端 `headlessAmazonMelee` 入口已建立，Jab、Power Strike、Impale、Charged Strike、
Fend、Lightning Strike 六项均已通过真实伤害/双端一致性/重连门槛，且 Lightning Strike
确认链式导弹；Jab 的真实目标死亡和高防 miss gate 也已通过。本轮 Impale 进一步通过
真实 MPQ quantity gate（`jav 16 -> 15`，durability `20/20` 不变）及非堆叠
Calc3 durability gate（`spr 20 -> 19`）和 observer 重连资源保持。下一步是弹药边界
和剩余 Amazon 技能。
完成后再继续 Assassin/Sorceress 尚未达到四层验收的技能，最后处理 Barbarian
和 Druid 全技能树。

## 测试入口

纯 ECS：

```powershell
./gradlew.bat :core:test --tests com.riiablo.engine.server.AuraEcsScenarioTest --no-daemon
```

真实 Paladin hostile 门槛：

```powershell
./gradlew.bat :server:d2gs:headlessPaladinAura -PpaladinAuraSkill=123 -PpaladinAuraTimeout=5 -x :core:compileJava --no-daemon
 ./gradlew.bat :server:d2gs:headlessPaladinAura -PpaladinAuraSkill=114 -PpaladinAuraTimeout=10 -x :core:compileJava --no-daemon
```

真实 Amazon Impale 资源门槛：

```powershell
./gradlew.bat :server:d2gs:headlessAmazonMelee -PamazonMeleeSkill=19 -PamazonMeleeWeapon=jav -PamazonMeleeTimeout=8 --no-daemon
./gradlew.bat :server:d2gs:headlessAmazonMelee -PamazonMeleeSkill=19 -PamazonMeleeWeapon=spr -PamazonMeleeTimeout=8 --no-daemon
./gradlew.bat :core:test --tests com.riiablo.engine.server.AmazonMeleeSkillLifecycleTest --no-daemon
```

## 工作区保护边界

`git status` 目前会列出 ItemEntry、ItemReader、AssassinTrapSystem、StatFormatterTest、
AmazonSkillSpecializationTest；逐个 `git hash-object` 核对后，只有 StatFormatterTest 有
实际内容差异（末尾多一个空行），其余四个文件的工作区 blob 与 HEAD 相同，属于状态/时间戳
假脏，不应据此回滚代码。`game.log`、技能查看器 PNG 和日志目录是之前调试运行生成的
未跟踪产物；`impale-spr*.log` 也是本轮诊断产物，不纳入提交。接手者只能 `git add` 本轮明确修改的文件，禁止 reset、checkout 或覆盖这些
文件。实现改动必须专项测试、commit、push；极小文档修改可不编译，但也必须 commit/push。
