# 本机复测证据：Diablo / Diabloclone DiabPrison

2026-10-15，Windows，本机1.10f MPQ：`G:\BaiduNetdiskDownload\Diablo II 1.10F`。
接续基线`b949a7f80`，旧DiabRun快照不覆盖。执行期间共享master另有上游/地图任务，
其`1c55883e3`独立提交了WarpDist与Quill Rat齐射；本轮不包含该任务代码。

## 原版依据与实现

- D2MOO `SkillMonst.cpp::SKILLS_SrvDo104_DiabPrison`（0x6FD09890）：检查Skills记录，
  解析summon；需要目标单位，或无单位目标时按path firstPointY=2 / X=object GUID找对象。
  使用目标当前room，拒绝城镇，调用`sub_6FC6A810(...,summonId,0)`。
  不计算技能伤害、不创建飞弹、不套玩家骨牢Do062。
- `MonsterSpawn.cpp::sub_6FC6A810`（0x6FC6A810）仅接受boneprison1且有目标；
  四条记录为(+1,+1,type1)、(+1,-1,type3)、(-1,-1,type3)、(-1,+1,type3)，
  `unk0x08=1`逐段增加classId，mode8/S1，flags0。
  `sub_6FC6A350`（0x6FC6A350）首段自持owner，其余三段归首段；首段失败不生成子段，
  子段失败后仍尝试后续记录。不是Diablo的宠物，不能随施法者死亡销毁或套宠物上限。
- `D2GAME_SpawnNormalMonster_6FC68E30`在该D2MOO版本仍为nullptr stub，下面保留反编译正文；
  其骨牢分支分配`MONUMOD_KILLMINIONSDEATH`及unit flag0x20000，
  placement用`COLLISION_CheckMaskWithSize`。本轮复用项目普通怪物数值生成链，
  独立精确落点入口检查地形、房间与动态size足迹并即时预留，无邻近重定位。
- `MonsterUnique.cpp::MONSTERUNIQUE_KillMinions`（0x6FC6CE50）及callback
  （0x6FC6CEC0）：任一段进入死亡时解除整组owner/minion关系，再使同组及root进入死亡。
  新专用BonePrison AI只保留Idle/Fn000及此死亡链，无计时器；普通死亡/DT→DD和碰撞释放
  交给已有权威链。组引用验证避免删除段的ECS id复用后误杀无关怪物。
- `SUnit.cpp::SUNIT_GetTargetUnit`（0x6FCBC900）及`sub_6FCBC7E0`验证单位身份，
  不按HP拒绝已保留目标。Actioneer为Do104保留动作点，即使目标已死；真正移除目标则不
  把旧普通地面坐标当成新目标。对象编码投影到本项目Casting.targetVec及ECS对象id。

真实表：Skills199 srvstfunc0/srvdofunc104、summon=boneprison1、pettype=none、monanim=S3；
Diablo/clone的Skill7=DiabPrison、Sk7mode=S3、Sk7lvl1。
boneprison1..4 hcIdx340..343，各自独立BaseId/MonStatsEx、AI=Idle、Velocity0、Size1、
killable/neverCount。无BoneWall技能或600帧寿命，不添加玩家骨墙生命加成。

## 失败与修后证据

- `initial-diagnostic.zip`：首次接入后两行diab-wall的旧后撤寻路被真实骨牢阻挡，
  两场RUNTIME_ERROR，未移除碰撞或覆盖输出。
- `after1-diagnostic.zip`：包含首次集成fixture缺少Riiablo.anim的失败日志，及随后
  修好动画加载后的自然场。测试角色缺少mana stat，普通Attack被现有验证拒绝；
  静止远距角色在DiabRun接近后也不再自然选择骨牢，故四场专属未触发，保留原结果。
- `after2-diagnostic.zip`：补完整角色mana和普通寻路后撤，两个骨牢专属四阶段通过；
  地火场在角色攻击尚未结束时请求移动，旧地火四阶段仍未触发。
- `after3-diagnostic.zip`及`after3-regression.zip`：角色完成破牢攻击后再请求后撤，
  两场地火及两场骨牢全部通过；此时7项骨牢回归通过，非最终累计数。
- 初始共享XML曾被其他Gradle任务清理，相关完整失败栈保留在归档日志；后续使用独立
  编译/测试输出目录，不覆盖其他测试结果。构建脚本验证/并行增量编译失败亦保留日志。

新增自然骨牢场沿用原表AI概率，不强制怪物技能、不注入SkillDo、不传送/关闭碰撞。
四阶段独立核对：真实199/S3动作点与四种原表怪物、精确四角/root归属；S3及各段起身完成，
普通Pathfinder确认无法走出；角色普通Attack实际物理DamageEvent和整组HP归零/碰撞释放；
角色通过普通Pathfinder实际走出>3单位。不是仅凭“生成实体”通过。
骨牢/地火fixture角色有1000物理、10000AR、100mana，仅用于普通攻击破牢，仍保留命中判定。
其他场不添加角色攻击。地火仍使用原有四阶段oracle，未放宽阈值。

## 验证边界

- 原版spawn的完整随机流、所有room/preset/spawncol分支未全量移植；本轮只新增Do104
  所需的四条精确位置链。普通怪物数值、整数坐标/寻路/动画/死亡仍复用项目已有实现。
  ECS id不等于原版GUID/type/generation；组引用保护只处理本链id复用，不宣称全局身份完成。
- 对象分支支持原版encoded object目标及直接对象单位；完整Fn051的Chaos Sanctuary
  portal搜索/选择仍未移植，不把Do104对象入口可用称为完整Diablo AI。
- arena仅Normal/seed1/每场500个25Hz tick，非全Act刷怪、三难度多seed或真实TCP/GL验收。
  Horror/Sentry MonSeq、完整Fn065、Megademon、47AI/188行fallback与全局精度仍待。
- 不宣称全项目测试通过。Baal目标链line317旧失败未纳入/未修；旧证据仍在
  `../20261015-inferno-seed1/baseline-known-failure.xml`。

## 最终结果与复现

最终469项定向通过：core435/56suite，d2gs34/3suite，失败/错误/跳过0。
其中骨牢9项（数据1/集成8），覆盖两Boss真实S3/四角/起身/阻挡/无计时寿命、
四个死亡入口整组清理、独立其他组及施法者存活、首段/子段静态失败、动态占位与同tick预留、
对象编码/城镇/移除目标/保留死亡目标、删除段id复用防误杀。不累计初轮与复跑数字。

全目录557行/1163场（Normal、seed1、500tick）：494 ACTION、376 AI_FALLBACK、
107 WARNING、175 NO_ACTION、7 MOVEMENT_ONLY、4 TERMINAL；
RUNTIME_ERROR/ERROR_LOG/NOT_RUN/SPECIAL_NOT_TRIGGERED均0，49场专属全部成功。
新增骨牢两场均tick196生成/阶段1、210起身和阻挡/阶段2、218实际1000物理击杀/阶段3、
229步行离开/阶段4。地火仍用旧四阶段要求，Diablo为342/344/368/455；
全部保留原表怪物概率、命中判定、地形/动态碰撞和警告。

相对旧1161场：1079全字段相同、82变化、2新增、0丢失，完整字段见`comparison.txt`。
8条仅boneprison1..4 actualAi由共享Idle变为独立BonePrison，status仍NO_ACTION；
两条diab-wall的Do104生产链补齐，WARNING→ACTION且告警1→0，角色普通攻击破牢后再后撤。
其余72条为非Diablo普通飞弹场，落在并行`1c55883e3`更改的公共MonsterAttack发弹等级/齐射
路径，不能归作本轮骨牢效果；本轮未剥离并行提交建立独立前后基线，也不宣称逐项验收该提交。
共享工作区执行，不是干净独立worktree或全项目验收。47AI/188行fallback仍未解决。

最终完整发布输出`server/d2gs/build/monster-ai-arena/20261015-diab-prison-full-final`；
复跑`20261015-diab-prison-repeat`，1163场JSONL SHA256均为：
`D93A98856144BD0DA69A7C2D8984BDA9DD0FAB8B67BAFC19FD9615AAC63E8722`。
`logs.zip`含1163场日志，解压到本目录后report链接可用；`regression-final.zip`按core/d2gs
分目录保存59个XML；`repeat-results.zip`保留复跑JSONL/汇总/COMPLETED。
`build-diagnostics.zip`含构建/测试失败和运行日志。临时Gradle task最初误给JUnit4模块使用
JUnitPlatform，产生“no TestEngine”失败；`regression-initial-run.zip`保留该失败及当时core结果，
修正为core JUnitPlatform/d2gs JUnit4后最终469项通过，未修改任何测试断言。

真实资源全目录命令（输出目录必须不存在）：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat :server:d2gs:monsterAiArena `
 -ParenaOutput=server/d2gs/build/monster-ai-arena/my-diab-prison-full --max-workers=1 --console=plain
```

定向回归沿用前批DiabWall verification全部筛选，加DiabRun/Pathfinder/速度筛选及
`--tests '*DiabPrison*Test'`。本轮为避免并行Gradle清理XML使用临时独立编译/测试task，
精确脚本归档于本目录：将`diab-prison-regression.init.gradle`及`run-diab-prison-final.ps1`
复制到仓库build/后执行`./build/run-diab-prison-final.ps1`，须先改其arenaOutput为新目录。
常规无并行构建的另一台电脑可用`:core:test`/`:server:d2gs:test`运行相同筛选。
