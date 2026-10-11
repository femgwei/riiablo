# Automap 问题交接记录

更新时间：2026-10-12

## 当前问题

- 与原版截图 `C:/Users/DELL/Desktop/p5.png` 对比，riiablo 的 Automap 在城镇少绘制一面墙。
- 城镇边界应使用双线；非城镇边界应使用单线。
- 当前 riiablo 仍把包括非城镇在内的边界绘制成双线，且城镇缺少目标墙线。
- 用户已确认“同时绘制多个 level 导致双线”的判断不成立，不要沿该方向继续修改。

## 已处理与结论

- 曾提交 `9e1267f9 Render only the active automap level`，试图通过只绘制当前 level 解决问题。
- 用户验证无效并要求回退；该提交已通过 `a753676e Revert "Render only the active automap level"` 非破坏性回退。
- `a753676e` 已推送到 `origin/master`，当前工作区干净。
- 本次回退未编译；用户会在另一台电脑验证。

## 证据与参考文件

- 原版截图：`C:/Users/DELL/Desktop/p5.png`
- riiablo 截图：`C:/Users/DELL/Desktop/p3.png`
- 游戏日志：`C:/Users/DELL/Desktop/game.log`
- 存档/地图输入：`C:/Users/DELL/Desktop/CC4.d2s`、`C:/Users/DELL/Desktop/CC4.ma0`
- 关键实现：`core/src/main/java/com/riiablo/engine/client/automap/AutomapManager.java`
- 相关测试：`core/src/test/java/com/riiablo/engine/client/automap/`

此前离屏验证曾写回 `CC4.ma0`，文件大小由 8658 变为 9156 字节；`CC4.d2s` 未修改。后续分析应先确认输入文件是否仍为该版本。

## 后续工作约束

- 暗黑机制不清楚时，优先查看本地 `F:/3rd_src/D2MOO`；仍不清楚再搜索 The Phrozen Keep。
- 如果从 The Phrozen Keep 得到实施方案，必须先向用户展示方案并等待确认，不能直接实施。
- 任何代码修改完成后都要 commit 并 push 到 `origin/master`，供另一台电脑验证。
- 极小修改可不编译，直接提交推送。
- 当前新聊天应先基于日志、截图和原版行为定位根因，不要直接恢复已回退的 active-level 方案。
