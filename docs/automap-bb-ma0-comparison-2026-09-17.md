# bb 原版 `.ma0` 与 Riiablo Automap 冲突比较（2026-09-17）

## 基准

- 角色：`bb`，非资料片人物；已在原版 1.10f 进入 Blood Moor 后退出保存。
- `bb.d2s` / `bb.map` 的普通难度地图种子均为 `1262832990`（`0x4B454D5E`）。
- 原版 Blood Moor 记录位于 `bb.ma0` 的 layer 0：730 条记录，其中 floors 556、walls 174。
- 离屏客户端读取 `bb.d2s` header，并保留原版角色 flags（`expansion=false`）、职业、等级和
  map seed；不再使用固定的 `CampSmoke` seed。

## 坐标对齐

Riiablo 运行时 cell 使用世界 subtile 坐标，导出时转换为原版 `.ma` 坐标：

```text
maX = 8 * (floorDiv(worldX, 5) - floorDiv(worldY, 5))
maY = 4 * (floorDiv(worldX, 5) + floorDiv(worldY, 5))
```

比较器按相同 category + cellNo 的大量记录投票，推断出本次 Java → 原版坐标平移为：

```text
nativeX = javaMaX - 80
nativeY = javaMaY + 9408
```

最高候选有 20 个连续墙体锚点匹配，因此不是由单个重复 cell 编号偶然产生的偏移。

## 冲突结论与修复

修复前 Riiablo 有 6 个同坐标不同 cell 组合：

- 四个同类别 wall 冲突：`61/14`、`32/20`、`61/14`、`61/13`；基础墙层为 3，后一个
  候选来自覆盖墙层 4 或 5。
- 两个跨类别组合：`floor 59 + wall 60`、`floor 58 + wall 60`。

原版 fixture 的统计结果：

```text
same-category same-position conflicts = 0
cross-category same-position conflicts = 30
```

这与原版 `D2Client.AddAutomapCell` 的分类坐标树结构一致：每个 floors/walls/objects/extras
树在同一坐标只保留第一个 cell，不同分类的树互不排斥。`d2hackmap` 对该函数的 Shrine
覆盖补丁也只在“同坐标已有节点”分支替换 cell，进一步证明 `wWeight` 是树比较结果
（-1/0/1），不是让同坐标多个 cell 叠画的 DT1 层权重。

Riiablo 已改为每个 category 按坐标去重。由于 DT1 层按低到高遍历，四个 wall 冲突保留
先到的基础层 `61/32/61/61`，丢弃覆盖层 `14/20/14/13`；两个 floor + wall 组合继续保留。
离屏结果从 408 个 cell 降至 404，冲突审计只剩上述两个合法的跨类别组合。

## 验证结果与限制

- AutomapLayer/AutomapCellAudit 定向测试通过。
- 真实 1.10f MPQ、`bb` seed、Blood Moor level 2 的 1×1 离屏测试通过。
- 原版 fixture 与 Java 完整生成地图只重合 20 个 cell；三个已探索冲突坐标在原版记录中是
  普通 floor 4/6/8，而非 Java 的候选墙。这说明当前 D2MOO Java 的 Blood Moor 布局仍未与
  原版同 seed 完全一致，不能用这份 fixture 对所有绝对坐标做逐点画面等价断言。
- 比较脚本会报告坐标偏移、原版同/跨类别冲突统计、每个候选的 translatedMa 和命中状态；
  `-RequireConflictHit` 可在需要严格 fixture 命中时启用。

后续优先级：先对齐 Blood Moor 的原版 DRLG/DT1 生成布局，再扩大 `.ma0` 逐点回归；当前
去重规则已有原版数据结构和 fixture 分类统计双重依据，不需要等待布局完全对齐。
