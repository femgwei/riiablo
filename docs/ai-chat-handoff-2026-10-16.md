# 怪物 AI 工作交接（2026-10-16）

用户要求保存当前信息、关闭旧聊天并在新聊天继续逐怪 AI 对齐。新聊天读取本文件后直接
推进下一项，不重跑已完成批次，不需要再询问是否继续。中文沟通。

## 仓库与协作约定

- 仓库 `F:\3rd_src\riiablo`，PowerShell，shared master；参考 `F:\3rd_src\D2MOO\source`。
- MPQ：`$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'`。
- 每批完成后 commit、push origin/master，并用 ls-remote 核对远端 SHA。
- 不主动派子代理。修改用 apply_patch，搜索优先 rg。
- 其他聊天并行处理路径/物品/UI。只提交本人文件，使用 `git commit --only ... -- <明确路径>`。
  修改共享文档前检查 diff，不混入其他聊天改动，不回滚他人文件。
- 当前他人改动：D2PathContinuation.java、D2PathDispatch.java、D2PathUnits.java、
  D2PathDispatchTest.java、D2PathKnockbackTest.java（未跟踪）、
  docs/d2moo-upstream-port-2026-10-15.md。实际状态以新聊天 git status 为准。
- 不消警、不改自然概率、不放宽行为 oracle，不注入 SkillDo 冒充自然成功；
  不关闭碰撞/传送。技能事件不等于伤害通过，headless 不冒充 GL/TCP。
- 独立 Gradle 构建必须同时隔离 buildDir、JavaCompile.destinationDirectory、
  sourceSet.java.destinationDirectory / output.classesDirs，及 --project-cache-dir。
  参考本批 isolated.init.gradle。共用 task history 曾导致不同目录输出被自动删除。

## 已完成批次，不要重做

1. DeathMauler Fn130、MonSeq、Do136 已逐步完成服务端接入。
   - `f93f0738fecec4cbfa1953dd1366e4b3a289b588`：Do136，静止502、整数距离寿命、
     Activate倒计时、NextDelay10重复命中、Skills物理伤害快照；92项定向测试，
     全目录557行/1180场独立复跑一致，六个自然专项技能伤害通过。
   - `61dab6f1d3e0179a27f120438306eed7843d3757`：六行×三难度×seed1/7/42，
     含独立复跑324次执行，108次自然技能断言通过，九组一致，无生产行为修改。
   - 证据 `docs/ai-arena/20261016-deathmaul-do136/` 和 `20261016-deathmaul-matrix/`。
   - CltDo76/trail、真实GL/TCP/地形仍待。本地D2MOO/OpenD2等没有可靠客户端实现，
     不凭表字段猜算法。SkillCastHandler 已复用服务端 root，勿重复造导弹。

2. `6e2242cb20fe4f54bb6762f0ecb31048bdcb560c`：Minion Fn116 普通无命令分支。
   - 11行；近战概率slot0，A2概率slot1，近战等待slot1，接近概率slot2，远等待slot3。
     保留原版错误：A2概率使用slot1，不是文档slot4。
   - 原版命令队列定向目标/失效/强制动作尚未实现，不能声称完整Fn116。
   - 51项定向通过，三难度seed1近远66场及独立复跑132次，三组一致。
     66次近场实际攻击伤害，66次远场NO_ACTION，原因未定，不算远场验收。
   - “18距离超出激活距离”假设测试失败，已保留失败证据并移除错误假设，未改生产。
   - 证据 `docs/ai-arena/20261016-minion-seed1/verification.md`。

3. 当前最新 AI 提交 `e1a487b8c79841bdf60d75898e2cbe72ab10dd44`：Spirit Fn097。
   - 已 push，远端核对一致。生产新增 `core/src/main/java/com/riiablo/engine/server/ai/Spirit.java`。
   - 原版 `AITHINK_Fn097_Spirit`，AiThink.cpp 0x6FCE28A0：首次近战先置标记再A1，
     已攻击等待50帧，攻击前非近战等待10帧；无目标也对应等待；目标丢失/owner warp不重置。
   - 57项测试通过，无失败/错误/跳过：Spirit6、Minion5、BaalMinion3（通配符额外匹配）、
     DeathMauler7、DoomKnight5、VileDog5、Act1NativeAi26。
   - Spirit测试覆盖三难度、一次性、busy、10/50帧边界、有/无目标、丢失/再获取、warp、death。
   - 真实MPQ，mephistospirit三难度seed1，near/far各500ticks，6场+独立复跑6场。
     三组JSONL逐字节一致；near各一次普通SkillDo后等待，far无动作，无fallback/警告/错误。
   - Normal near没有伤害；Nightmare/Hell near各一次实际伤害79/131，复跑一致。
     曾误以为所有难度都0伤害，verifier失败已记录并纠正。Normal伤害仍未验收。
   - 复跑曾被并行路径源码 `RayResult.point` 字段编译错误阻塞，保留失败日志；
     对方改为endpoint后重试成功，本任务未改路径文件。
   - 证据 `docs/ai-arena/20261016-spirit-seed1/verification.md`、verification.log、
     final-tests.log、XML、JSONL、scenes.zip。Hell最终复跑输出目录有 spirit-retry 名。

## 当前覆盖与限制

- docs/monster-ai-arena.md 与 docs/d2moo-progress-roadmap.md 顶部有最新批次说明。
- filtered arena仍输出全目录1180条；未选的是NOT_RUN，归档原始journal，不能计执行/通过。
  Spirit每轮选2条、1178未选；Minion选22条、1158未选。
- 最近两批没有全目录回归声明，避免并行AI/寻路改动产生错误归因。
- 原生单位RNG合流、全局调度、真实GL/TCP/地形、狼相邻落点碰撞问题仍未完成。
- 自然场景未加专属oracle时保持 BASELINE_ONLY，不把动作数当作全行为通过。

## 下一步

继续下一个尚fallback的怪物专用AI，先读取原版完整函数及依赖再选题。
剩余较多：SuicideMinion、MinionSpawner、FetishBlowgun、Succubus/Witch、Imp、WillOWisp、
BatDemon、ReanimatedHorde、ClawViperEx、ThornHulk、FingerMage、SiegeBeast等。
Minion/Spirit已接入，旧全目录fallback统计未重跑，不直接沿用旧数量减法作验收。

已查看但未动的原版入口：
- Fn033_HellMeteor (0x6FCD7EB0)：无需目标，Skill1概率slot0；整数自身坐标加两个
  limited RNG(2*slot2)-slot2，失败等slot1。若选择此项需正确支持无目标自主决策/
  null-target坐标施法，别被NativeDecisionAI默认有目标入口限制。
- Fn099_TrappedSoul (0x6FCE9980)：NOTC标记、首次S2、坐标象限A1/A2/S1与5/35帧门。
- Fn123_Catapult (0x6FCE2570)：概率slot0，无目标A1，失败等15；需核实攻击事件的
  null-target效果及原版后续链，不要把普通玩家定向攻击替代无目标动作。
- Fn070_FingerMage：复杂逃跑/血量/owner/技能分支，完整函数不可只移植片段。

下一批遵循：真实表选择测试与修前失败证据 → 最小有依据生产实现 → 有意义定向测试 →
真实MPQ自然场及独立复跑 → 诚实记录未通过项 → 仅本人文件commit/push/核对远端。
