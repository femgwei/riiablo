# AI 移动精度第二批：Duriel 速度参数

基线：`ca97c5636`。本批修正一个已验证的参数翻译错误，不切换生产寻路。

## 原版依据

本地 D2MOO `source/D2Game/src/AI/AiThink.cpp` 的
`AITHINK_Fn044_Duriel`（0x6FCD8FE0，约 3893 行）在非冲锋接敌时调用
`AITACTICS_SetVelocity(pUnit, 13, 0, 0)`，随后使用 flags 7 走向目标。
`AiTactics.cpp` 的 SetVelocity 以及 `MonsterMode.cpp` 的参数保存与模式切换
分别处理路径类型和速度：13 是路径类型，速度参数是 0。

旧实现 `approach(targetId, 13)` 错把路径类型转换为 13% 速度加成。
现改为零加成；完整类型 13、flags 7、fp16 路径行为仍未接入，不能宣称原版等价。

## 验证

- 新测试在修正前复现应为 0、实际为 0.13 的速度错误。
- 最终定向回归 22 项全部通过：Duriel 移动 3、既有 Duriel 决策 1、
  共享等待帧 4、竞技场测试 14。
- 移动测试覆盖三难度、清除旧速度加成与 Running、寻路失败后的等待。
  使用 stub Pathfinder，不是实际碰撞/路径精度验收。
- 真实 1.10F MPQ，Normal、seed 1、500 ticks，Duriel/DeathMauler 六场：
  三场 ACTION_OBSERVED，三场 NO_ACTION，1189 项 NOT_RUN。
  DeathMauler 技能循环观察通过；整体正确性仍为 NOT_EVALUATED。
- 新增 Duriel 10 格自然接敌场景，不强制技能或修改概率，仍无动作；
  两种怪的 18 格场景也无动作，原因尚未确定。

结果目录：`build/ai-duriel-movement-20261017-arena/`。

## 排除与下一步

真实表测试表明，两种怪三难度的有效 aiDist 均允许 18 格目标；
Duriel 的 0 按原版规则回退为 35。此前“半径不足”的假设已被否定。
无竞技场房间限制的夹具能取得 18 格目标，但这不证明真实世界也能取到。
合成地图无 RoomEx 时邻接检查放行，不能据此直接认定房间是根因。

后续应给真实竞技场逐层补充只读诊断：激活/休眠、动作锁、候选目标拒绝原因、
寻路返回值与阻挡；取得证据后再修复。保留通用 Map → A* 生产链，
不为通过观察场景而强制攻击，也不把独立 native-path 测试算作生产移动完成。
