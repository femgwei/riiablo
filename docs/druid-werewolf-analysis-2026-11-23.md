# Werewolf（狼人变身）阶段结论

Riiablo 已有完整的 SrvDo116 变身链：状态组切换、狼/熊互斥、持续时间、属性状态、COF 视觉覆盖、恢复人形，以及 Fury/Maul/Rabies/Fire Claws 对形态状态的读取。

本轮检查确认：

- Werewolf 使用 `aurastate=wolf`、`SrvDoFunc=116`；
- 客户端覆盖使用 MonStats 形态 token `40`、`druidmorph.dcc` 和 `wolf_into/wolf_undo`；
- 逻辑玩家类型仍保持 PLR，只有视觉组合切到怪物形态；
- `DruidShapePresentationTest` 和形态展示链通过。

形态数值已按 1.10f 表值统一：Werebear 的 `Param1/2` 为 50/7，等级 3 伤害为 64；防御参数为 25/5，等级 3 防御为 35。Werewolf 的 `dm34` 先截断 `110*level/(level+6)`，等级 2 攻速为 28。Lycanthropy 联动后的 Werebear 生命值按 `par5 + ln34` 计算。

## 原版后台采集

批次 `20261123-115609-184da731` 在私有桌面完成，游戏进程已清理，没有切换前台。共 255 个客户端快照：

- 模式包含变身 `10`、移动 `13`、待机 `1`；
- 变身阶段 13 个样本，约 0–373 ms；
- `new_direction` 固定为 58，当前方向从 56、57 收敛到 58；
- 发现 overlay id `198`，总游标 3328，每次推进 320；
- 采集到的 `animation_raw` 和 `animation_rate=208` 可用于客户端时序回放。
- 模式转换为 `10 -> 13 -> 1`：变身、移动、恢复到待机；恢复按再次按 F1 触发，边界约在 2.5 秒。
- 本次自然怪物坐标不在客户端可点击范围内，因此没有把 `13` 误报成攻击；攻击模式没有被确认。

证据：`tools/original-observer/druid-werewolf-original-1.10f.json`，原始逐样本文件位于该批次的 `worker/capture/player-animation.jsonl`。

尚未完成：原版狼形态攻击动画，以及将 overlay 游标与 Riiablo 的 `DruidShapeShiftResolver`/客户端动画时钟逐帧配对。恢复模式边界已有证据，但攻击模式仍待在可点击目标场景重新采集。因此当前状态为“变身、恢复和数值公式已完成，攻击时序待核对”。

