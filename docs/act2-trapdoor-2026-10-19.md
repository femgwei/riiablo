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
