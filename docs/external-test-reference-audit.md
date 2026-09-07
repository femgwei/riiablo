# libd2 / dark-magic 测试资产审计

审计日期：2026-09-07。

本工程的原生行为真值仍是 D2MOO 和 Diablo II 1.10f。`libd2` 与
`dark-magic` 当前都以 1.14d 为目标，因此可以移植测试结构、fixture
组织和断言方法，但不能把它们的资源数值、CRC、存档字节或技能公式直接当作
1.10f 的 golden。

## 可直接移植的测试模式

- 固定种子并显式断言 RNG 消耗次数；失败信息必须包含 seed、level、skill
  和第一次差异。
- 对无顺序要求的房间/实体集合先规范化排序，再生成 CRC 或快照校验和。
- 序列化使用 `parse -> write -> parse` round-trip，同时验证损坏签名、截断输入
  和修改后的校验字段。
- 服务端权威状态和客户端表现分层测试；客户端预测不得修改权威快照。
- owner/source/target 三元关系测试，覆盖创建、替换、死亡、离线、恢复和重连。
- real-resource 测试与纯数据测试分开；真实资源缺失时明确跳过，不静默改用另一版本。
- 行为覆盖清单和 skill evidence 清单，记录每个行为的原始函数、数据行、测试和
  尚未覆盖的表现证据。
- 离屏渲染 fixture；逻辑测试通过后仍检查生产资源加载和真实帧输出。

## 可参考位置

`libd2`：

- `packages/drlg/src/coll_crc_verify.zig`：跨 seed、逐 Level 的碰撞 CRC 门槛。
- `packages/drlg/src/tests.zig`、`verify_tests.zig`：DRLG 固定种子和差异定位。
- `packages/pathfinding/src/tests.zig`：路径确定性和边界表驱动测试。
- `packages/save/src/tests.zig`：真实存档字节级 round-trip。
- `packages/item/src/tests.zig`、`verify.zig`：固定 roll、字段级 golden 和 RNG 流。
- `packages/game/src/tests.zig`、`skills_druid.zig`：Skills.txt 驱动的技能公式测试。

`dark-magic`：

- `internal/game/ecs/snapshot_test.go`：规范化快照、实体身份和 allocator 恢复。
- `internal/game/session/reconciliation_test.go`：服务端权威校正和损坏帧拒绝。
- `internal/game/simulation/*_test.go`：确定性 replay、命令顺序和随机流。
- `internal/mod/d2legacy/*_real_test.go`：真实 MPQ 数据契约和纯逻辑测试分层。
- `internal/mod/d2legacy/corpse_summon_skill_real_test.go`：召唤数据、所有权和
  物化依赖的跨表断言。
- `internal/mod/d2legacy/skillevidence`、`behaviorcoverage`：技能证据和行为覆盖清单。
- `internal/presentation/maprender/*_test.go`：表现层与模拟层解耦的渲染 fixture。

## 不可直接采用的 fixture

- `libd2` 的 1.14d DRLG 多 seed CRC golden。
- `libd2` 的 `EpicSorc.d2s` 及其 1.14d 版本号/字节偏移预期。
- 两个工程中由 1.14d `Skills.txt`、`PetType.txt`、`MonStats.txt` 得出的固定数值。
- 1.14d 实机探针记录的物品 roll、地图 RNG 状态和资源内容哈希。

这些 fixture 可以作为兼容性对照，但 1.10f 门槛必须重新从本项目固定的 1.10f
资源和 D2MOO 调用链生成或推导。

## 映射到 Riiablo 的门槛

每个完整功能模块至少需要：

1. `Native*Test`：Skills/TXT、公式、边界和固定 RNG 的纯数据契约。
2. `*IntegrationTest`：Artemis 世界中的实体、所有权、生命周期和网络可观察状态。
3. 涉及客户端世界接线、AI、地图、技能实体或表现时运行真实 1.10f
   `:desktop:offscreenCamp`。
4. 需要多人行为时，增加双客户端权威快照/投影一致性测试；单客户端成功不能替代它。
5. 提交前运行 `git diff --check`，并确认没有混入 1.14d golden 或本机绝对路径。

`offscreenRender` 只验证合成 UI 场景，不能代替真实营地测试。
