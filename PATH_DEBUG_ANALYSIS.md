# Blood Moor 路径不显示 - 根因分析

## 日志观察

- `applyTileGridToZone: applied 4480 tiles`：瓦片已写入 zone 的 floorLayer
- `pathId=1180160`（BM）、`pathId=787200`（Rogue Encampment/CP）：来自 findPathFloorId
- `getPathTileIdForZone` 返回 -1：path preset 瓦片未被使用（preset 的 DS1 瓦片可能不在 zone.dt1s 中）

## 可能原因

### 1. 瓦片不可见（最可能）
pathId 来自 floorLayer/兄弟 zone/grid，多为**草地或与背景相同的瓦片**，导致路径难以分辨。  
D2MOO 使用专用路径瓦片（byte_6FDCF958 + LvlSub），riiablo 当前用通用地板瓦片。

### 2. Grid/Zone 坐标
- BM zone: 56×96 tiles，grid: 80×80
- 当前假设 grid(0,0)=zone(0,0)，直接拷贝
- 若 zone 有偏移 (zone.tx, zone.ty)，需验证映射是否正确

### 3. 渲染流程
- `drawFloors` 使用 `zone.get(FLOOR_OFFSET, tx, ty)` 读取 `tiles[0]`
- `applyTileGridToZone` 写入 `floorLayer` (=tiles[0])
- 流程一致，理论上有路径应能显示

## 建议修复

1. **优先使用更易区分的瓦片**：在 findPathFloorId 中优先尝试 Stones.dt1 等与草地差异大的瓦片
2. **确认 path preset 瓦片可用**：检查 path preset 的 Dt1Mask 是否包含在 zone 的 dt1s 中
