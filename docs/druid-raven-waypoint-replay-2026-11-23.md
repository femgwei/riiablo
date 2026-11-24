# Raven waypoint replay阶段结论

## 本轮做了什么

把原版 motion 证据中的路径签名和 Riiablo 生产路径测试分开核对：

- 原版 combat capture：type 13 追击、type 5/6 圆周、distance=20、方向偏移 ±2、攻击期间保持移动状态。
- 原版 town capture：无目标、预算保持 12、主要产生 type 6 路径和持续坐标变化。
- Riiablo production tests：`NativePathRuntimeTest`、`NativeMonsterMovementTest`、`VelocityAdderTest`、`PathfinderTerrainPursuitTest` 和 `RavenAiIntegrationTest`。

本轮扩展了 native helper：每个 motion 样本导出 `PathPoints[]`（最多 78 个点），并导出起始房间及相邻活动房间的完整 16-bit collision grid。后台批次 `20261123-104054-3b89cb9a` 成功完成，未切换桌面，游戏进程已清理。

新增证据文件：`tools/original-observer/druid-raven-motion-waypoint-replay-2026-11-23.json`。combat 与 town 两批均导出了 9 个活动房间；combat 有 181 行非空路点、225 个点，town 有 378 行非空路点、767 个点。所有路点都落在导出的活动房间并集内；本批覆盖 type 6 盘旋和 type 13 追击，随机方向没有出现 type 5，因此不能用单批证明两侧方向都出现。

校验脚本 `tools/original-observer/compare_druid_raven_waypoints.py` 检查 78 点容量、cursor 边界、房间并集和路径类型，输出 `same_layout_ready=true`。这表示输入已经足以开始同布局回放，不表示回放结果已经相同。

## 为什么还不能给出逐点坐标差

现在已经有原版 room bounds、collision flags、相邻房间和 dynamic path `PathPoints[]`。下一步需要把这些输入导入 Riiablo 的 `NativePathRoomGraph`/`D2CollisionGrid`，对相同起点、目标、path type、direction offset 和单位 footprint 调用生产 `NativePathRuntime`，再比较点序列和结束位置。跨运行时导入尚未完成，因此不能把 `same_layout_ready` 写成 waypoint parity。

## 当前结论

本轮没有发现 `NativePathRuntime`、`VelocityAdder` 或方向迟滞的明确错误，因此没有修改路径消费者。已有代码对 native precision、bounded path、目标 token、动态阻挡、传送失效和 direction id/radian 转换有测试保护。

验证日志：`tools/original-observer/native-path-runtime-test-2026-11-23.log` 中 `NativePathRuntimeTest` 单独通过；Python 采集回归 4 项通过。组合 Java 回归仍会被当前 Java 25 与旧 Byte Buddy/Mockito 的兼容性阻断，日志为 `tools/original-observer/druid-raven-ai-waypoint-validation-2026-11-23.log`，该失败不是 Raven 断言失败。

下一步应实现同布局 grid 导入和逐点 replay，而不是重复采集相同的 Raven 攻击或空转样本。
