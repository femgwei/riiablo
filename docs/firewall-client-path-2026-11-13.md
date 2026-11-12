# Fire Wall 客户端生成条件修正

## 问题与更正

`aff82286c` 把 `firewallmaker.CltParam1=4` 解释为每帧 1/4 概率生成火斑。
这是错误结论，会随机漏掉中间和末端，导致相同施法条件的可见长度变化。
本次移除该概率门槛，也不使用固定四帧取模代替它。

## 静态证据

- 目标：本机 Diablo II 1.10f `D2Client.dll`，SHA256
  `94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282`。
- REA/Ghidra Evidence：
  `ev_134f18efe2c1459f752e6b67f7082bfa7fb173b57c2aa92f636be581dc395a70`，
  保留文件 `rea-function-clt6-real.json`，函数 `0x6FB30820..0x6FB30ACF`。
- CltDo6 首先调用 D2Common ordinal 10234，返回零则直接执行通用更新。
  D2MOO `D2Common/src/Path/Step.cpp` 中 `D2Common_10234` 读取 path flag 8；
  `sub_6FDAD5E0` 在整数地面坐标改变时设置该位，否则清除。
- 通过上述检查后，CltDo6 三选一选取 `CltSubMissile1..3`，随后抽取
  `CltParam1` 随机数。后者只影响创建 flags 的 `0x4000` 位；两条分支
  都到达 `0x6FB30A95`，调用 `0x6FB3E070` 创建子弹。
- 补充 objdump 静态反汇编（不是新的 REA Evidence）：创建函数
  `0x6FB3EA24..29` 检查 `0x4000`，跳过的是动态光创建。
  被跳过分支读取行偏移 `0x130` 的 Light 及 RGB，调用 `0x6FAA4280`。
  该位不取消火焰精灵创建。
- 同一创建函数 `0x6FB3E2FB..31F` 计算 `(Vel + level*VelLev/8)<<8`，
  `0x6FB3E3F9..468` 再乘 `75/100`。与 D2MOO
  `D2Game/src/MISSILES/Missiles.cpp` 的创建速度一致。
- CltDo6 最后才调用通用更新 `0x6FB3D880`，故采样移动前的位置。
  本轮没有运行原版程序观察这些调用。

## 实现

- 按原生 12 位方向向量、fp16 单帧位移重建客户端 maker 路径，
  每个 25 Hz 回调最多生成一团，只有整数 subtile 改变才生成。
  每团出生在整数地面坐标，不在浮点路径上重复堆叠。
- 生成器的路径和宽度不依赖随机种子；三种大小仍随机选择。
  保留灯光抽样的 RNG 消耗，但当前渲染器没有实现该创建标志的独立灯光行为。
- 服务器中心和沿路伤害体继续隐藏。正向 maker 单独重建一团中心视觉，
  避免两枚 maker 重叠生成中心，也避免隐藏规则造成中心空洞。
  这是 riiablo 客户端适配规则，不是原版 CltDo6 自行生成中心的证据。
- 网络包只有当前位置、速度和年龄，没有出生坐标。
  首次按现有服务端直线运动反推并取整出生点，之后固定该视觉路径；
  不改写网络副本的位置、速度或已接受的服务端年龄。
  该反推依赖现有服务端恒速运动，不表示恢复了网络中未传输的原生精度。
- 本地跳帧按完整路径逐 tick 补齐，不把多帧火斑挤到同一点。
  服务端伤害生成逻辑没有随本次客户端修正调整。

不能把截图中的大火团直接等同于精灵数。本次 1 级、中心为整数格、
无阻挡、9 个回调的测试条件下，沿坐标轴为 10 个精灵（含中心），
45 度为 7 个；这来自整数格边界与方向，未硬编码成固定 5～6 个。
相同方向、等级和落点的重复施法布局一致。不同方向的投影和交错大小
仍会影响肉眼看到的大火团数量。

## 验证范围

新增 `FireWallPresentationLifecycleTest` 使用真实 ECS 回调及真实原版数据，
覆盖 64 个种子的布局一致、大小资源选择、中心唯一、逐步延伸、
同格不重复、跳帧/网络补帧、权威 RNG 与网络位置不受修改。
以下 5 套专项测试共 37 项全部通过（失败、错误、跳过均为 0）：
`FireWallPresentationLifecycleTest`、`NativeSorceressFireAreaDataTest`、
`SorceressFireAreaIntegrationTest`、`ImmolationArrowPresentationTest`、
`PlagueJavelinPresentationLifecycleTest`。
`:desktop:compileJava` 和 `verifyModuleBoundaries` 通过。
尚未完成游戏窗口视觉验收。
