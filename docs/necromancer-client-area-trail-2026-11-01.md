# 死灵诅咒范围及骨矛尾迹（2026-11-01）

本批静态依据为用户提供的原版 1.10f `D2Client.dll`，SHA-256：
`94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282`。
使用 GNU objdump 精确地址反汇编；并非原版运行或逐帧画面对照。

## 原版观察

- 已有技能表 `0x6FB775A8` 的技能 CltDo30 指向 `0x6FAC7FF0`。
  `0x6FAC802F` 读取 Skills+0xEA（cltmissilea）；`0x6FAC807C..97`
  求 aurarangecalc、减三、最小取二；`0x6FAC80D0` 创建编号191的
  cursecenter，在目标点保存范围和具体诅咒视觉导弹编号。
- 导弹 CltDo 表 `0x6FB91260` 的17/18项分别为
  `0x6FB32310` / `0x6FB32470`。
- CltDo17 在 `0x6FB3238C..AB` 比较剩余帧与 CltParam1，且按三取模。
  `0x6FB323B1..C7`：范围大于四时采样间距二、随机界限为半径/2；
  否则间距一、随机界限为半径。`0x6FB35D10` 按整数圆形范围遍历，
  `0x6FB35E09..60` 排除圆外点，并以原生种子限界随机零值决定生成。
- CltDo18 在 `0x6FB324CB..DB` 检查剩余帧 >= InitSteps；随后每次
  更新创建 CltSubMissile1，并在 `0x6FB325AC..DA` 继承父导弹方向/路径
  精度。CltParam1=3 写入创建参数的 nLoops，不是每三帧产生尾迹。
- 实际原版表：bonespear CltDo18、CltSubMissile1=bonespeartrail，
  尾迹 Vel0/Range8/CelFile=BoneSpearTrail；cursecenter CltDo17、
  Range20、CltParam1=7、无CelFile，各诅咒子导弹使用 CurseArea DCC。

字段意义交叉参考 D2MOO `D2Common/include/DataTbls/SkillsTbls.h`、
`MissilesTbls.h` 和 `Units/Missile.h`；服务端伤害/状态不由这些视觉子实体处理。

## 实现及边界

SkillCastHandler 新建不可见 cursecenter callback carrier，保存范围/子导弹；
MissileImpactPresentationSystem 执行17的稀疏范围层和18的静止有限尾迹。
控制器无DCC，不应等待 AnimationWrapper；骨矛尾迹发射时钟也不等待主体冷加载。
尸爆/毒爆 burst 使用 presentation-only factory，避免普通导弹生命周期。

新增 Native row 断言和真实 ECS callback 测试。测试不构成GPU画面验收。
目前仍需实机对照 CurseArea 颜色、层级、范围密度及尾迹方向和叠加；
原版创建 nLoops=3 的细节尚未完全映射到 Animation 的子循环表现，未声称像素级一致。
