# Shape Shifting（变形学）阶段结论

Shape Shifting 是 Werewolf/Werebear 的协同技能，不单独产生客户端形态；它通过 `skill('Shape Shifting'.ln12)` 和 `ln34` 被 SrvDo116 的形态状态读取。

已确认并通过定向回归：

- `ln12` 提供变身持续时间增量；
- `ln34` 提供形态生命增量；
- Werewolf/Werebear 的形态状态在切换时重建，不会累加上一轮状态；
- 变身状态移除时，依赖形态的 Feral Rage/Maul 状态也会清理；
- 客户端形态 token、COF 和 morph overlay 由 Werewolf/Werebear 状态负责，Shape Shifting 本身不重复创建视觉层。

本轮统一了 1.10F 表值和公式取整：Werewolf `dm34` 等级 2 为 28；Werebear 等级 3 伤害/防御为 64/35；等级 2 Lycanthropy 联动生命为 75。

验证：`NativeDruidShapeDataTest`、`DruidShapeShiftTest`、`DruidShapePresentationTest`、`DruidFuryTest` 共 24 项通过。

剩余工作属于形态技能共同的原版逐帧攻击回放，不是 Shape Shifting 自身的逻辑缺口。

