# 跳跃者返航、频率与落地烟尘

## 现象与根因

用户提供的桌面 `game.log` 中，entity 161 的落点依次为 `(105,-114)`、
`(97,-114)`、`(105,-114)`。旧 `SrvDo077` 在每个 MonSeq 动作点调用
`startLeap`，只检查是否仍有 `Leap` 组件；组件移除后，同一个 Casting 在后续动作点
再次按 `2 * target - currentPosition` 镜像，产生回旋镖式往返。

`SandLeaper.update` 另有近距离即时反应分支，在计时门槛之前重掷概率；普通行动中也可能
覆盖已有 Casting/Sequence。原生 `aip1=20` 的含义是一次有效 AI 决策的 20% 概率，
不能每个 25Hz 模拟帧都重新尝试。

## 1.10f 依据

- D2MOO `AI/AiThink.cpp::AITHINK_Fn017_SandLeaper`：目标距离小于 5，按参数 0
  掷跳跃概率，再通过技能可用性检查。贴身攻击按参数 1 掷概率；空闲为 10 帧。
- D2MOO `SKILLS/SkillBar.cpp::SKILLS_SrvSt40_Leap`、`SKILLS_SrvDo077_Leap`：
  首次预定镜像落点，技能 flags 分离准备、飞行与抵达，不为后续动作点重新起跳。
- `SKILLS_Leap`：SandLeaper 基类飞行时保持动画第 8 帧，抵达后转到第 12 帧。
  原版还有落地后的地面 knockback 路径，此次没有新增该后续路径，不能据此宣称整个
  Leap 技能已完整对齐。
- 本机原版 `D2Client.dll` SHA-256：
  `94466B84C0593AB682F1D3D8DC65EF4F8170F33E430865D802BC752458B2B282`。
  `cltdofunc` 表 `0x6FB775A8` 的索引 43 指向 `0x6FAC1DC0`，调用 `0x6FAC1E40`；
  抵达分支 `0x6FAC1FB5` 后，在 `0x6FAC1FD7..0x6FAC1FEA` 请求 overlay `0x50`、
  模式 2。被调用的 `0x6FB5BA70` 从 DataTables `+0xBBC/+0xBC0` 取 Overlay 表及条目数。
- 真实 1.10f MPQ：Overlay[80] 为 `dust / BAJumpLandPuff01`，对应 DCC 存在。
  `seq_leaperleap` 使用 0..19 帧，动作点在 1、7、9、11；落地后不能从起跳段重新播放。

## 修改

- `Actioneer` 对同一个 Casting 的 SrvDo077 只允许一次成功起跳。
- `AnimStepper` 飞行期间保持第 8 帧；Leap 组件结束后转入第 12 帧与落地尾段，
  发布一次 `LeapLandEvent`，并保留非 SandLeaper 的既有行为。
- `SkillCastHandler` 消费落地事件，使用真实 `dust` overlay。
  事件走本地共享 world 的总线；本次未新增或验证独立网络客户端的落地事件同步协议。
- AI 活动动作期间不重新决策，尊重思考计时；跳跃/攻击概率使用每单位原生 RNG 流。
  跳跃结束后按已有难度对应 `aidel` 等待下一次思考。

## 验证

定向回归使用 `-Pd2Home=<1.10f目录>`，覆盖 `LeapIntegrationTest`、
`RemainingMonsterSkillIntegrationTest`、`NativeMonsterSequenceIntegrationTest`、
`MonsterSkillPresentationIntegrationTest` 和 `SandLeaperTest`。
关键断言为单次起跳、固定落点、空中第 8 帧、一次落地事件、技能结束、迟到动作点不返航、
真实烟尘条目与文件、客户端 overlay 请求、近距离不绕过思考计时。

共享默认构建目录第一次编译测试时缺失 `MenuScreen.class`，因此使用独立构建输出与
project cache 重跑。最终五类定向测试共 17 项通过，失败、错误、跳过均为 0。
使用本机 1.10f MPQ 的 `:desktop:offscreenCamp` 返回 `OFFSCREEN_CAMP result=PASS`
（真实 GL、三帧），资源关闭也返回 `CLIENT_SHUTDOWN result=PASS`；这仅为营地启动冒烟，
不代表已经实机复现和验收跳跃者遭遇。输出清单位于本地
`build/leaper-camp-visual/rogue-encampment-manifest.txt`。
烟尘实际画面和连续遭遇手感仍需用户另一台电脑实机复测。
