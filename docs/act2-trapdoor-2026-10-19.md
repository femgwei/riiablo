# Act II 下水道活板门交互修复

## 原因与依据

本地 D2MOO `source/D2Common/include/DataTbls/ObjectsIds.h` 定义
`OBJECT_TRAP_DOOR = 74`。真实 1.10f `Objects.txt` 对应 `OperateFn=16`。
`source/D2Game/src/OBJECTS/ObjMode.cpp` 的
`OBJECTS_OperateFunction16_TrapDoor` 在 NU 时直接进入 ON；已打开后再次操作，
查找同一活动房间的 UNIT_TILE，再调用 `SUNIT_WarpPlayer`。

riiablo 原先未分类此操作，`ObjectInteractor.operate` 的 case 16 为空，
导致活板门没有原版的打开和传送行为。

## 修改

- 新增 TRAP_DOOR 生命周期，首次操作打开并持久化 ON/opened，保留交互。
- 已打开后选择同 Zone、同 RoomEx 的普通 Warp，调用现有传送链。
- 缺少 RoomEx 的兼容地图仅查找 10 subtile 内最近入口；找不到时记录错误，
  不使用远处出口。
- 复用权威门交互白名单，本地客户端和 D2GS 均可处理此对象。
- 回归使用真实 1.10f 对象 74，验证第一次只打开、第二次转交 Warp，
  并拒绝其他 Zone/RoomEx 的出口。Warp 探针仅验证交互转交，不代表实际换图已验收。

## 验证

定向测试：`TrapDoorInteractionIntegrationTest`、`NativeObjectOperateTableTest`，7 项通过。
真实资源离屏：`:desktop:offscreenCamp -PoffscreenLevel=40 -Pd2Version=1.10f`，
构建成功并生成 `desktop/build/visual-tests/a2-trapdoor/rogue-encampment-manifest.txt`。
此烟测验证 Act II 城镇加载与渲染，不替代实际点击活板门换图验收。
测试使用环境变量 `D2_HOME`/`D2_TEST` 注入资源；实际点击进入及返回需在另一台电脑验证。

工作区其他怪物技能/表现修改不属于本次提交。

## 后续：两个入口的一一对应

用户复测发现城镇的两个下水道入口到达同一位置。独立原因是
`Act2MapBuilderD2MOD.findReverseWarp` 只按目标 LevelId 取第一个反向 marker。
前述活板门操作测试的 Warp 探针不覆盖地图端点配对，故未检出此问题。

对照 D2MOO `DrlgDrlgRoom.cpp::sub_6FD77BB0`：计算源槽之前相同 Vis 目标的出现次数，
选择目标 Vis 中相同次数的反向槽。Java 现按此规则选择 marker，并将运行时目标覆盖
纳入有效 Vis 计算；缺少对应 marker 时不回退复用第一个入口。

新增 `Act2SewerWarpPairingTest`，使用真实 1.10f `Levels.txt` 的 40/47 记录验证
两个入口到达不同端点且反向返回各自入口；另覆盖 marker 哈希遍历顺序、缺失第二端点、
运行时目标覆盖。与 `Act2MapBuilderD2MooWarpTest` 一起执行定向回归，全部通过。
此轮未重跑离屏渲染，两个入口的实际进出仍由另一台电脑复测。
