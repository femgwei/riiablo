# Act1 路径与地图构建能力 - 修正评估

本文档根据另一 agent 对 D2MOO_JAVA 的深入分析，对 Act1 路径「高级特性」和迷宫生成能力进行修正评估，并与 C++/D2MOD 行为对齐。

---

## 一、Act1 路径高级特性 - 修正结论

### 1. 方向对齐（Direction Alignment）——已实现

- **位置**：`DrlgOutPlace.java` 中的 **sub_6FD81430**
- **功能**：根据方向（0–3）计算相邻关卡位置，并应用偏移量对齐（如 a4 == 1 时应用 -16/+16 等）
- **实现**：`switch (a3)` 按 4 个方向设置 `pDrlgCoord2` 的 PosX/PosY，并在 a4 == 1 时做坐标微调
- **调用**：在 sub_6FD823C0 的关卡链接流程中调用（如第 1254、1501 行附近）

### 2. 城镇顶点/方向（Town Vertex Offset）——已实现

- **位置**：`DrlgOutPlace.java` 的 **sub_6FD823C0**（约第 1094–1098 行）
- **功能**：为 `LEVEL_ROGUEENCAMPMENT` 设置方向，使城镇出口与路径生成结果对齐
- **代码**：
  ```java
  if (pLevel.getLevelId() == D2LevelIds.LEVEL_ROGUEENCAMPMENT) {
      pPreset.setNDirection(pLevelLinkData.getNRand(0)[i]);
  }
  ```
- **说明**：城镇方向由路径生成阶段得到的随机方向数组决定，即「城镇顶点偏移/对齐」在 D2MOO_JAVA 中是通过预设的 direction 体现的。

### 3. 路径生成算法——回溯，非 A*

- **位置**：`DrlgOutPlace.java` 的 **sub_6FD823C0**
- **实现方式**：
  - 通过**回溯**尝试不同方向组合
  - 使用 `sub_6FD82050`（荒野）和 `sub_6FD82130`（修道院）验证布局有效性
  - 失败时回退并尝试下一组方向
- **结论**：Act1 关卡连接路径**未使用 A\***，与 C++ 源码一致（使用回溯生成可行布局）。

### 4. 泥土路径绘制——已实现

- **位置**：`DrlgOutdoors.generateDirtPath`、`drawLine`（Bresenham）
- **功能**：根据路径起点与 Hub 在房间的 pDirtPathGrid 上画线，与前述关卡链接/方向逻辑配合使用。

---

## 二、完整迷宫生成流程——已实现

- **位置**：`DrlgMaze.java` 的 **generateLevel**
- **包含**：
  - **initBasicMazeLayout**：基础迷宫布局初始化
  - **buildBasicMaze**：构建基础迷宫、添加房间
  - **linkMazeRooms**：房间链接（含方向 0–3：西/北/东/南）
  - **mergeMazeRooms**：房间合并
  - 特殊预设放置（如 placeAct1Barracks、scanReplaceSpecialPreset 等）
- **结论**：完整迷宫生成流程在 D2MOO_JAVA 中已实现，不再视为「仅 40% 或基础接口」。

---

## 三、修正后的 Act1/地图构建能力总表

| 特性                     | 之前评估     | 修正后     | 说明                                       |
|--------------------------|--------------|------------|--------------------------------------------|
| 方向对齐                 | 缺失         | **已实现** | sub_6FD81430，按方向+偏移计算相邻关卡坐标 |
| 城镇顶点/方向            | 缺失         | **已实现** | sub_6FD823C0 中为 ROGUEENCAMPMENT 设 direction |
| A* 寻路                  | 未实现       | **未实现** | 与 C++ 一致，使用回溯而非 A*               |
| 完整迷宫生成             | 约 40%       | **已实现** | DrlgMaze.generateLevel 及 link/merge 等   |
| 泥土路径绘制             | 已实现       | **已实现** | generateDirtPath + Bresenham               |

---

## 四、对 riiablo Act1MapBuilderD2MOD 的启示

1. **方向对齐**：若 riiablo 要对接 D2MOO_JAVA，可复用 `DrlgOutPlace.sub_6FD81430` 的语义（方向 + 偏移计算相邻关卡坐标），无需在 riiablo 再实现一遍「CalculatePathCoordinates」式方向对齐。
2. **城镇出口方向**：D2MOD 侧是通过预设的 `nDirection` 与 `getNRand(0)[i]` 绑定实现的；若 riiablo 当前用固定偏移 (59,19) 等，可视为与「按 direction 选出口」等价的不同实现方式，对接时需做方向↔偏移的映射。
3. **路径生成**：D2MOO_JAVA 与 C++ 一致，用回溯生成关卡连接，**没有** A*；若 riiablo 在 Act1 用的是 A* 网格寻路，那是 riiablo 自己的实现选择，与 D2MOD 原逻辑不同。
4. **迷宫关卡**：地下城/洞穴类可直接依赖 D2MOO_JAVA 的 `DrlgMaze.generateLevel` 及 link/merge 流程，无需在 riiablo 重写完整迷宫生成。

---

## 五、结论（与另一 agent 一致）

- **方向对齐**：已实现（sub_6FD81430）。
- **城镇顶点/方向**：已实现（sub_6FD823C0 中 ROGUEENCAMPMENT 的 direction 设置）。
- **A* 寻路**：未实现，且原版即使用回溯算法。
- **完整迷宫生成流程**：已实现（DrlgMaze.generateLevel 及配套函数）。

**D2MOO_JAVA 的 Act1 路径与迷宫相关能力与 C++ 源码一致，地图构建所需的方向对齐、城镇对齐和迷宫流程均已具备；仅路径生成方式为回溯而非 A*，此为设计一致而非缺失。**
