# 2026-09-22 Blood Moor 绿色菱形问题交接

## 当前状态

此问题尚未修复。

Blood Moor 一处房屋门口会固定显示一个深绿色/青绿色的完整等距菱形。它不跟随玩家，边缘与单个 320x160 DT1 tile 完全对齐。用户在当前提交前再次运行确认，绿色菱形仍然存在。

## 已确认的信息

- 问题发生在 Blood Moor，level id 为 `2`。
- 当前地图日志：`grid=56x96`，`zoneTiles=56x96`，`zone.tx=56`，`zone.ty=-48`。
- zone 世界范围：subtile `X[280,560] Y[-240,240]`。
- 问题附近的原生对象：
  - entity `130`：object class `247`，`bed act 1`，位置 `(383,58)`，碰撞范围 `381,57,4x2`。
  - entity `131`：object class `243`，`glchest3L general`，位置 `(379,59)`，碰撞范围 `379,59,1x1`。
  - D2MOO preset：`presetIndex=38`，原始 `objectId=580 -> classId=243`，关卡内 subtile `(99,299)`。
  - 两个对象都进入 RoomEx `27`。
- `(99,299)` 是关卡内 subtile 坐标，因此目标本地 tile 约为 `(19,59)`，不是之前误推算的 `(75,11)`。
- Blood Moor 导出摘要在修复前为：`floor=5056 wall=1224 shadow=178`，所有 DT1 resolve 失败计数均为 `0`。

## 已排除或已尝试但无效

1. `Map.ID.getColor` 的特殊格调试颜色不是来源。
   - 临时把 special id `12`/`16` 的颜色从绿色/青色改成黄色后，画面仍然是绿色。
   - 该临时颜色修改已经撤销。
2. `RenderSystem` 默认调试覆盖层不是唯一来源。
   - 已把 `RenderSystem.DEBUG` 默认值改为 `false`，用户重新运行后问题仍存在。
3. D2MOO 隐藏 floor/shadow 记录不是唯一来源。
   - `D2MooTileApplier` 已过滤带 `DrlgTileExporter.FLAG_HIDDEN` 的 floor/shadow，同时保留隐藏 wall marker 的 warp 语义。
   - 单元测试验证过滤行为通过，但用户重新运行后绿色块仍存在。
4. 绿色块不像对象 shadow。
   - 图形是完整 DT1 等距 tile，而 object `247` 的碰撞占地只有 `4x2` subtile，object `243` 为 `1x1` subtile。
   - 仍需用逐层禁用或渲染日志最终确认，不能仅凭尺寸彻底排除实体动画层。

## 本次保留的代码改动

- `RenderSystem.DEBUG=false`：默认关闭旧地图调试覆盖层。
- `D2MooTileApplier`：隐藏 floor/shadow 不再写入可见层，隐藏 wall marker 继续保留。
- `D2MooTileApplierTest.skipsHiddenFloorAndShadowGraphics`：对应回归测试。

这些改动本身合理且测试通过，但没有消除本问题，后续不要把它们当作已经完成的修复。

## 下一台机器建议的定位步骤

1. 在 `Act1MapBuilderD2MOD.applyTileGridLayers` 针对本地 tile `(19,59)` 及周围一圈打印：
   - `floorId`、全部 `wallIds`、`shadowId`。
   - orientation、mainIndex、subIndex。
   - `floorSourceFiles` 对应的 DT1 文件。
   - `dt1s.get(sourceFile, id)` 与 fallback `dt1s.get(id)` 是否得到不同实例。
   - resolved tile 的宽高、block 数量和实际 source file。
2. 在 `RenderSystem.drawFloors`、`drawShadows`、`drawWalls` 对该本地 tile 分层临时跳过渲染，确认绿色块具体来自哪个 pass。
3. 若来自 floor：重点检查 source-aware lookup 是否因 source 为空或路径规范化失败而 fallback 到相同 tile id 的错误 DT1。
4. 若来自 map shadow：记录 shadow id 和 DT1 来源，检查是否把普通 floor/特殊 marker 当成 shadow texture。
5. 若地图三层都不是来源：在 `drawShadows` 和 `drawEntities` 分别临时排除 object class `247`、`243`，并打印 COF layer 的 component、shadow flag、DC/DCC/DC6 bbox。
6. 最快的视觉二分方式是分别关闭 `drawFloors`、地图 shadow、实体 shadow、walls 和 entities，每次只关闭一个 pass，不要再修改 `Map.ID` 调试颜色。

## 验证状态

- `./gradlew.bat :core:compileJava --no-daemon`：通过。
- `./gradlew.bat :core:test --tests com.riiablo.map.d2moo.D2MooTileApplierTest --no-daemon`：通过。
- `./gradlew.bat :desktop:jar --no-daemon`：通过。
- 项目无窗口实际渲染未能执行：`D:/Games/Diablo.II.v1.10F` 被 offscreen runner 判定为不完整的 MPQ 安装。
- 用户实际运行确认：绿色菱形仍存在。

## 本地文件

- `game.log` 和 `_tmp_run.ps1` 仅用于本机诊断，不提交 Git。
- 用户最后一张截图文件名：`codex-clipboard-fd61cec6-abec-4d89-abff-763b1a15565e.png`。截图位于原机器临时目录，不在仓库中。
