# 震波（Shock Wave，243）：熊人施法、五道波与眩晕反馈

本轮接在饥饿之后，检查客户端展示链路。本轮没有启动原版进程、前台窗口或全局输入；证据来自本机有效 MPQ 数据、公开重建源码和无窗口测试。

## 有效数据与源码

Skills 行指定 `SrvSt=0`、`SrvDo8`、`CltDo17`、`anim/seqtrans=SC`、`monanim=xx`、`UseAttackRate=1`，只允许 bear。calc1=5，SrvmissileA/B 与 CltmissileA/B 均为 shockwave；没有额外 castoverlay。普通技能一级物理范围为 10..20，Param1/2 为 40/15 个原版游戏帧的眩晕时长。

Missiles 行 shockwave（511）使用 `pCltDo=1`、`pSrvDo=1`、`pSrvDmg=7`，CelFile=`expansion/shockwave`，Vel=20、Range=14、animrate=1024、AnimLen=7、LoopAnim=0、Trans=1、NumDirections=8。NextHit=1、NextDelay=4，ClientCol=1、LastCollide=1；TravelSound=druid_shockwave，HitSound 为空，原表 HitClass=64。

D2MOO `SkillAma.cpp:SKILLS_SrvDo008_MultipleShot_Teeth_ShockWave` 用 calc1 决定弹体数，对施法者到目标的整数向量调用 `SKILLS_EnlargeVector` 和 `SKILLS_MakeShortPerpendicularVector`，然后沿目标处的短垂线逐个创建弹体。这不是把 DCC 的 8 个方向当作 8 道波，也不是固定角度的平均扇形。

D2Common `D2Skills.cpp:D2Common_11014_ConvertShapeShiftedMode` 决定变身后的动作转换，并按 MonStats2 支持情况回退。Riiablo 的实际熊人 SC 可以直接解析，不需要为了震波换成普通 A1 或另外增加施法覆盖层。

`MissMode.cpp:MISSMODE_SrvDmg07_Warcry_ShockWave` 优先使用正数 dParam1，否则以技能 Param1 + (level−1)*Param2 写入 stun length，并将伤害记录的 HitClass 改为 **0x60（96）**。这是命中记录的覆盖，不等于原始 Missiles 行也写着 96。

以上是公开重建源码和静态表的证据，不是官方原始源码或原版实时调用轨迹。

## 发现与修正

现有实现已具备熊人施法、五个有归属且带伤害快照的震波弹体、目标级 NextDelay、命中眩晕状态，以及客户端 stun 图层。新测试在本地 ServerSkillSystem(true) 与 SkillCastHandler 同时存在时核对这些环节，没有发现需要额外补发一组可见震波或额外施法特效的证据。

找到一项确定的命中音效差异：MissileImpactPresentationSystem 直接读取原始 HitClass=64，播放 `impact_lightning_1`，忽略了 SrvDmg07 写入的眩晕命中类别。现在对 pSrvDmgFunc=7 的单位命中按 96 选择 `impact_stun_1`；其他回调继续使用原表类别，墙体/无目标事件没有被解释为执行了 SrvDmg07。

此修改共用于 War Cry/Shock Wave 的 SrvDmg07，不修改原始数据行，不新增伤害或眩晕状态，也不扩展网络协议。没有因为缺少像素对比就推测新增其他震波动画。

## 资源和展示结果

- 熊人施法：`TGSCHTH` COF/AnimData 为 **10 帧、8 方向**，基础 AnimData speed=152，包含一个非零动作关键帧。组成层 `TGTRLITSChth.dcc` 为 10 帧、8 方向。基础速度不是全装备/加速后的最终速度。
- 波体：实际 `data/global/missiles/expansion/shockwave.dcc` 为 **7 帧、8 方向**，与 AnimLen=7 一致；加载后的动画 delta=256，即基础 25 Hz 每次前进一个资源帧，Trans=1 走已有 additive 弹体策略。
- 眩晕：States stunned（21）的 overlay1=stun；实际 `Stun.dcc` 为 **12 帧、单方向**。AnimRate=16 使用已有 25 FPS/additive 策略，状态图层循环；Height1/2/3/4 分别为 14/0/−14/−60。

弹体的 Range=14 是存活相关数据，不等同于 DCC 帧数。动画播放末帧后的具体原版显示行为尚未运行时采集，不能仅据这两个数字推断需要增加或删减帧。

## 无窗口验证

`ShockWaveAnimationDataTest` 用生产 CofManager、DruidShapeShiftResolver、AnimDataResolver，将逻辑玩家 SC 解析到实际熊人 SC，并读取 AnimData、COF 和各组成层 DCC 头。测试没有执行 GPU 绘制。

`ShockWavePresentationTest` 使用真实 ServerSkillSystem(true)、SkillCastHandler、MissileLoader 与状态/覆盖层系统：

1. 分别瞄准四个坐标轴方向和一个对角方向，在非零世界坐标发出一次 SkillDo，确认只有五个带 owner、技能与伤害快照的弹体，没有无归属的第二组客户端视觉弹体。
2. 加载真实资源头对应的无 GPU 动画对象，确认五道波均可建立显示层、朝向向量位于目标半平面、方向布局为 8、混合模式和帧增量符合当前策略。这里只检查向量和资源选择，不把这些断言当作精灵像素朝向已经目视一致。
3. 用 StateSerializer 将眩晕状态恢复到远端目标，创建单个持久 stun 图层；刷新持续时间不重建图层，snapshotOnly 等待权威移除，收到状态移除后释放 Stun.dcc。没有用本地计时器代替服务器过期。

`ShockWaveImpactAudioTest` 通过真实命中事件入口与可听见的目标位置，确认实际 shockwave 行产生 stun 命中声、不产生 lightning 命中声，不创建额外弹体或伪造 UnitStates。声音设备为 mock，未实际播放音频。

既有 `DruidShockWaveTest` 回归五个独立伤害快照、目标级 4 帧 NextDelay、一次碰撞伤害与眩晕、State/Vitals 序列化、致死结算顺序和 boss 眩晕拒绝。其碰撞夹具为了确定命中会关闭攻击评级检查，不代表原版命中率已覆盖。另回归共享 SrvDo008 几何、弹体加载追帧与其他弹体目标图层。

首轮 29 项通过。之后增加音效回归时，测试中误将 Files.Sounds 写成 sounds，发生编译失败；随后 36 项中 1 项因新音效夹具缺少 OverlayManager 注册而初始化失败，已补齐依赖。失败日志与逐项 XML 均保留，没有计为最终通过。最终验证数量、逐项 XML、日志、有效数据行和源码 SHA256 见 `tools/original-observer/shock-wave-presentation-validation-2026-11-24.json`。

最终 JDK 17 定向回归 **36 项通过，0 失败、0 错误、0 跳过**。包含本轮新增动作/展示/音效验证与既有伤害、状态同步、弹体加载和共享表现回归；没有运行整库测试或原版进程。

## 有界结论和后续

本轮验证范围内，施法动作、五道波、伤害到眩晕状态及状态到客户端图层均已接通；修改了明确的错误命中声。不能由此宣称原版完整画面已经一致。

仍未覆盖原版实时出生/消失相位、全部方向的精灵像素朝向、GPU 调色与遮挡、完整装备/攻速矩阵、原版逐格路径和真实 socket 双端时序。未新增音效版本/变体的原版运行时采样。职业清单保留 `runtime_status=not_run_in_class_batch`，只更新有界静态与无窗口验证进度。

244 Volcano、245 Tornado、246 Spirit of Barbs、247 Summon Grizzly 已有前期工作，下一项未处理技能为狂怒（Fury，248），不从头重跑已记录技能。
