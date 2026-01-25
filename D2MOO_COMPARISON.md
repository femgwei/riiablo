# D2MOO vs Riiablo 代码比较分析

本文档详细比较了 D2MOO 和 Riiablo 两个项目的代码实现，找出 D2MOO 中值得 Riiablo 借鉴的地方。

## 项目概述

### D2MOO
- **语言**: C++
- **架构**: 逆向工程重新实现的 Diablo II DLL（D2Common.dll, D2Game.dll 等）
- **目标**: 提供准确的游戏逻辑实现，供模组开发者参考
- **版本**: 基于 Diablo II 1.10f

### Riiablo
- **语言**: Java
- **架构**: 使用 LibGDX 从头开始重建 Diablo II
- **目标**: 跨平台（PC、Android、iOS）的 Diablo II 重制版
- **版本**: 支持 1.13c+ 存档格式

---

## 1. 经验值系统 (Experience System)

### D2MOO 实现亮点

#### 1.1 经验值计算公式 (`SUNITDMG_ComputeExperienceGain`)
**位置**: `D2Game/src/UNIT/SUnitDmg.cpp:3006`

**关键特性**:
- **等级差惩罚**: 当攻击者等级高于防御者时，经验值会按等级差递减
  ```cpp
  constexpr int32_t experienceFactors[] = {
      256, 256, 256, 256, 256, 256, 207, 159, 110, 61, 13
  };
  ```
  - 等级差 0-5: 100% 经验
  - 等级差 6: 80.9% 经验
  - 等级差 7: 62.1% 经验
  - 等级差 8: 43.0% 经验
  - 等级差 9: 23.8% 经验
  - 等级差 10+: 5.1% 经验

- **等级差奖励**: 当防御者等级高于攻击者时，经验值会按比例增加
  ```cpp
  if (nDefenderLevel > nAttackerLevel) {
      nResult = nDefenderExperience + (nDefenderLevel - nAttackerLevel) * nDefenderExperience / 3;
  }
  ```

- **难度系数**: 根据游戏难度调整经验值
- **最大等级限制**: 达到最大等级后不再获得经验

#### 1.2 组队经验值分配 (`SUNITDMG_DistributeExperience`)
**位置**: `D2Game/src/UNIT/SUnitDmg.cpp:2908`

**关键特性**:
- **组队奖励**: 组队时经验值会增加
  ```cpp
  const uint32_t nExperience = nDefenderExperience + 89 * nDefenderExperience * (partyExp.nMembers - 1) / 256;
  ```
  - 2人组队: +34.8% 经验
  - 3人组队: +69.5% 经验
  - 8人组队: +243.4% 经验

- **按等级分配**: 经验值按队伍成员等级比例分配
  ```cpp
  const float multiplier = (float)nExperience / (float)partyExp.nLevelSum;
  ```

- **佣兵经验**: 佣兵获得 86/256 (33.6%) 的经验值（如果击杀者不是佣兵）

#### 1.3 升级处理 (`PLAYERSTATS_LevelUp`)
**位置**: `D2Game/src/PLAYER/PlayerStats.cpp:75`

**关键特性**:
- **属性增长**: 升级时自动增加生命值、法力值、体力值
  ```cpp
  STATLIST_SetUnitStat(pUnit, STAT_MAXHP, (nLevelDiff * pCharStatsTxtRecord->nLifePerLevel << 6) + ...);
  STATLIST_SetUnitStat(pUnit, STAT_MAXMANA, (nLevelDiff * pCharStatsTxtRecord->nManaPerLevel << 6) + ...);
  ```

- **生命/法力恢复**: 升级时恢复满血满蓝
- **技能点/属性点**: 升级时增加技能点和属性点
- **事件触发**: 触发升级事件，通知其他玩家

### Riiablo 当前实现

**位置**: `core/src/main/java/com/riiablo/attributes/ExperienceManager.java`

**缺失功能**:
1. ❌ 等级差惩罚/奖励机制
2. ❌ 组队经验值分配
3. ❌ 佣兵经验值处理
4. ❌ 升级时的生命/法力恢复
5. ❌ 升级事件通知

**建议改进**:
- 实现完整的经验值计算公式
- 添加组队系统支持
- 实现佣兵经验值分配
- 升级时恢复生命和法力

---

## 2. 伤害计算系统 (Damage Calculation)

### D2MOO 实现亮点

#### 2.1 伤害结构 (`D2DamageStrc`)
**位置**: `D2Game/include/UNIT/SUnitDmg.h:65`

**完整伤害类型**:
```cpp
struct D2DamageStrc {
    uint32_t dwHitFlags;          // 命中标志
    uint16_t wResultFlags;         // 结果标志（格挡、闪避等）
    int32_t dwPhysDamage;          // 物理伤害
    int32_t dwFireDamage;          // 火焰伤害
    int32_t dwBurnDamage;          // 燃烧伤害
    int32_t dwBurnLen;              // 燃烧持续时间
    int32_t dwLtngDamage;           // 闪电伤害
    int32_t dwMagDamage;            // 魔法伤害
    int32_t dwColdDamage;           // 冰冷伤害
    int32_t dwPoisDamage;           // 毒素伤害
    int32_t dwPoisLen;               // 毒素持续时间
    int32_t dwColdLen;               // 冰冷持续时间
    int32_t dwFrzLen;                // 冰冻持续时间
    int32_t dwLifeLeech;            // 生命偷取
    int32_t dwManaLeech;             // 法力偷取
    int32_t dwStamLeech;             // 体力偷取
    int32_t dwStunLen;               // 眩晕持续时间
    int32_t dwAbsLife;               // 生命吸收
    int32_t dwDmgTotal;              // 总伤害
    int32_t dwPiercePct;             // 穿透百分比
    int32_t dwDamageRate;            // 伤害速率
    uint32_t dwHitClass;             // 命中类型
    int8_t nConvType;                // 转换类型
    int32_t dwConvPct;                // 转换百分比
};
```

#### 2.2 伤害计算流程 (`SUNITDMG_FillDamageValues`)
**位置**: `D2Game/src/UNIT/SUnitDmg.cpp:153`

**关键步骤**:
1. **基础伤害计算**: 从武器/技能获取基础伤害
2. **属性加成**: 力量/敏捷对物理伤害的加成
3. **伤害百分比**: 技能和装备的伤害百分比加成
4. **元素伤害**: 计算各种元素伤害
5. **特殊伤害**: 对恶魔/亡灵/野兽的额外伤害
6. **伤害偷取**: 计算生命/法力/体力偷取

#### 2.3 抗性和吸收 (`SUNITDMG_ApplyResistancesAndAbsorb`)
**位置**: `D2Game/src/UNIT/SUnitDmg.cpp:161`

**关键特性**:
- **抗性计算**: 抗性上限为 95%，但可以通过技能突破
- **吸收机制**: 先计算抗性，再计算吸收
- **伤害减免**: 物理和魔法伤害有不同的减免机制
- **穿透机制**: 某些技能可以穿透抗性

### Riiablo 当前实现

**位置**: `core/src/main/java/com/riiablo/engine/server/combat/DamageCalculator.java`

**已有功能**:
- ✅ 基础物理伤害计算
- ✅ 元素伤害计算（火焰、闪电、冰冷、魔法、毒素）
- ✅ 生命/法力偷取
- ✅ 暴击计算
- ✅ 命中率计算

**缺失功能**:
1. ❌ 燃烧伤害和持续时间
2. ❌ 毒素伤害的持续时间处理
3. ❌ 冰冷/冰冻持续时间
4. ❌ 眩晕机制
5. ❌ 生命吸收
6. ❌ 穿透百分比
7. ❌ 伤害转换（物理转元素）
8. ❌ 完整的抗性和吸收计算
9. ❌ 对特定怪物类型的额外伤害

**建议改进**:
- 实现完整的伤害结构，包含所有伤害类型和持续时间
- 添加伤害状态效果（燃烧、中毒、冰冻、眩晕）
- 实现完整的抗性和吸收系统
- 添加伤害转换机制

---

## 3. 玩家属性系统 (Player Stats)

### D2MOO 实现亮点

#### 3.1 属性点分配 (`PLAYERSTATS_SpendStatPoint`)
**位置**: `D2Game/src/PLAYER/PlayerStats.cpp:134`

**关键特性**:
- **力量**: 增加物理伤害和装备需求
- **敏捷**: 增加命中率、格挡率和装备需求
- **体力**: 增加生命值和体力值
  ```cpp
  STATLIST_SetUnitStat(pUnit, STAT_MAXHP, pCharStatsTxtRecord->nLifePerVitality << 6, 0);
  STATLIST_SetUnitStat(pUnit, STAT_MAXSTAMINA, pCharStatsTxtRecord->nStaminaPerVitality << 6, 0);
  ```
- **精力**: 增加法力值
  ```cpp
  STATLIST_SetUnitStat(pUnit, STAT_MANA, pCharStatsTxtRecord->nManaPerMagic << 6, 0);
  STATLIST_SetUnitStat(pUnit, STAT_MAXMANA, pCharStatsTxtRecord->nManaPerMagic << 6, 0);
  ```

- **属性点限制**: 检查是否有可用属性点
- **实时更新**: 分配属性点后立即更新相关属性

#### 3.2 升级属性增长 (`PLAYERSTATS_LevelUp`)
**位置**: `D2Game/src/PLAYER/PlayerStats.cpp:75`

**关键特性**:
- **每级增长**: 根据职业表计算每级的生命/法力/体力增长
- **满血满蓝**: 升级时恢复满血满蓝
- **技能点**: 每级增加 1 点技能点
- **属性点**: 每级增加职业特定的属性点

### Riiablo 当前实现

**位置**: `core/src/main/java/com/riiablo/attributes/ExperienceManager.java:77`

**缺失功能**:
1. ❌ 属性点分配系统
2. ❌ 升级时的属性增长计算
3. ❌ 升级时的生命/法力恢复
4. ❌ 技能点分配系统

**建议改进**:
- 实现属性点分配界面和逻辑
- 添加升级时的属性自动增长
- 实现技能点分配系统
- 升级时恢复生命和法力

---

## 4. 物品系统 (Item System)

### D2MOO 实现亮点

#### 4.1 物品耐久度 (`SUNITDMG_DrainItemDurability`)
**位置**: `D2Game/src/UNIT/SUnitDmg.cpp:193`

**关键特性**:
- **耐久度损失**: 攻击时根据武器类型和攻击类型减少耐久度
- **耐久度为 0**: 物品失效，需要修理
- **无形物品**: 无形物品无法修理，耐久度为 0 后消失

#### 4.2 物品生成 (`ITEMS_ItemDrop`)
**位置**: `D2Game/src/ITEMS/Items.cpp`

**关键特性**:
- **掉落率计算**: 根据怪物类型、难度、MF 值计算掉落率
- **物品品质**: 普通、魔法、稀有、套装、暗金
- **物品等级**: 根据怪物等级和区域等级计算
- **词缀生成**: 根据物品等级和品质生成词缀

### Riiablo 当前实现

**位置**: `core/src/main/java/com/riiablo/item/`

**已有功能**:
- ✅ 物品数据结构
- ✅ 物品装备系统
- ✅ 物品显示

**缺失功能**:
1. ❌ 物品耐久度系统
2. ❌ 物品修理系统
3. ❌ 完整的物品生成系统
4. ❌ 词缀生成系统
5. ❌ MF（魔法物品发现）系统

**建议改进**:
- 实现物品耐久度系统
- 添加物品修理功能
- 实现完整的物品生成和掉落系统
- 添加词缀生成逻辑

---

## 5. 技能系统 (Skill System)

### D2MOO 实现亮点

#### 5.1 技能分类
**位置**: `D2Game/include/SKILLS/`

**技能类型**:
- **亚马逊技能** (`SkillAma.h/cpp`)
- **刺客技能** (`SkillAss.h/cpp`)
- **野蛮人技能** (`SkillBar.h/cpp`)
- **德鲁伊技能** (`SkillDruid.h/cpp`)
- **死灵法师技能** (`SkillNec.h/cpp`)
- **圣骑士技能** (`SkillPal.h/cpp`)
- **法师技能** (`SkillSor.h/cpp`)
- **怪物技能** (`SkillMonst.h/cpp`)
- **物品技能** (`SkillItem.h/cpp`)

#### 5.2 技能计算
**关键特性**:
- **技能等级**: 基础等级 + 装备加成 + 技能加成
- **技能协同**: 某些技能可以增强其他技能
- **技能消耗**: 法力消耗、冷却时间
- **技能范围**: 影响范围、目标数量

### Riiablo 当前实现

**位置**: `core/src/main/java/com/riiablo/skill/`

**已有功能**:
- ✅ 基础技能数据结构

**缺失功能**:
1. ❌ 完整的技能实现（每个职业的技能）
2. ❌ 技能协同系统
3. ❌ 技能冷却时间
4. ❌ 技能范围计算
5. ❌ 技能视觉效果

**建议改进**:
- 参考 D2MOO 实现各职业的技能
- 添加技能协同机制
- 实现技能冷却和范围系统

---

## 6. 怪物 AI 系统 (Monster AI)

### D2MOO 实现亮点

#### 6.1 AI 状态机
**位置**: `D2Game/include/AI/AiStates.h`

**AI 状态**:
- **空闲** (Idle)
- **移动** (Move)
- **攻击** (Attack)
- **逃跑** (Retreat)
- **特殊技能** (Special Skills)

#### 6.2 AI 决策
**位置**: `D2Game/include/AI/AiThink.h`

**关键特性**:
- **目标选择**: 选择最近的玩家或威胁最大的目标
- **路径寻找**: 使用 A* 算法寻找路径
- **攻击决策**: 根据距离、生命值、技能冷却选择攻击方式
- **逃跑机制**: 生命值低于阈值时逃跑

### Riiablo 当前实现

**位置**: `core/src/main/java/com/riiablo/ai/`

**已有功能**:
- ✅ 基础 AI 类（Idle, Fallen, QuillRat, Zombie）

**缺失功能**:
1. ❌ 完整的 AI 状态机
2. ❌ 路径寻找算法
3. ❌ 复杂的攻击决策
4. ❌ 怪物技能使用
5. ❌ 怪物特殊行为（如 Fallen 的逃跑）

**建议改进**:
- 实现完整的 AI 状态机
- 添加路径寻找算法（A*）
- 实现更复杂的 AI 决策逻辑

---

## 7. 任务系统 (Quest System)

### D2MOO 实现亮点

#### 7.1 任务结构
**位置**: `D2Game/include/QUESTS/`

**任务组织**:
- **ACT1**: A1Q0-A1Q7（8个任务）
- **ACT2**: A2Q0-A2Q8（9个任务）
- **ACT3**: A3Q0-A3Q7（8个任务）
- **ACT4**: A4Q0-A4Q4（5个任务）
- **ACT5**: A5Q0-A5Q6（7个任务）

#### 7.2 任务状态
**关键特性**:
- **任务状态**: 未开始、进行中、已完成
- **任务奖励**: 经验值、物品、技能点
- **任务触发**: 对话、击杀、到达区域

### Riiablo 当前实现

**位置**: `core/src/main/java/com/riiablo/screen/panel/QuestsPanel.java`

**已有功能**:
- ✅ 任务面板 UI

**缺失功能**:
1. ❌ 完整的任务系统实现
2. ❌ 任务状态管理
3. ❌ 任务奖励系统
4. ❌ 任务触发机制

**建议改进**:
- 参考 D2MOO 实现完整的任务系统
- 添加任务状态管理
- 实现任务奖励和触发机制

---

## 8. 网络和多人游戏 (Networking & Multiplayer)

### D2MOO 实现亮点

#### 8.1 组队系统 (`UNIT/Party.h`)
**关键特性**:
- **组队管理**: 创建、加入、离开队伍
- **经验值共享**: 组队时经验值共享
- **物品共享**: 组队时物品掉落共享
- **队伍状态**: 同步队伍成员状态

#### 8.2 交易系统 (`PLAYER/PlrTrade.h`)
**关键特性**:
- **交易窗口**: 打开交易窗口
- **物品交换**: 交换物品和金币
- **交易确认**: 双方确认后完成交易
- **交易取消**: 可以取消交易

### Riiablo 当前实现

**位置**: `core/src/main/java/com/riiablo/engine/server/party/PartyManager.java`

**已有功能**:
- ✅ 基础组队系统

**缺失功能**:
1. ❌ 完整的组队 UI
2. ❌ 组队经验值共享
3. ❌ 交易系统
4. ❌ 组队状态同步

**建议改进**:
- 实现完整的组队 UI 和功能
- 添加组队经验值共享
- 实现交易系统

---

## 9. 存档系统 (Save System)

### D2MOO 实现亮点

#### 9.1 角色存档 (`PLAYER/PlrSave.h`, `PlrSave2.h`)
**关键特性**:
- **完整数据**: 保存所有角色数据（属性、技能、物品、任务）
- **版本兼容**: 支持多个版本的存档格式
- **数据验证**: 验证存档数据的有效性
- **压缩存储**: 压缩存档数据

### Riiablo 当前实现

**位置**: `core/src/main/java/com/riiablo/save/`

**已有功能**:
- ✅ D2S 存档读取和写入
- ✅ 角色数据保存

**缺失功能**:
1. ❌ 完整的任务进度保存
2. ❌ 地图探索进度保存
3. ❌ 存档数据验证
4. ❌ 存档版本兼容性处理

**建议改进**:
- 添加任务进度保存
- 实现地图探索进度保存
- 添加存档数据验证

---

## 10. UI 系统 (UI System)

### D2MOO 实现亮点

#### 10.1 窗口管理 (`D2Win/`)
**关键特性**:
- **窗口系统**: 完整的窗口管理系统
- **控件系统**: 按钮、文本框、列表等控件
- **事件处理**: 鼠标和键盘事件处理
- **渲染系统**: 高效的 UI 渲染

### Riiablo 当前实现

**位置**: `core/src/main/java/com/riiablo/screen/panel/`

**已有功能**:
- ✅ 基础 UI 面板（Inventory, Character, Skills 等）
- ✅ LibGDX UI 系统

**缺失功能**:
1. ❌ 某些 UI 面板的完整实现
2. ❌ UI 动画效果
3. ❌ UI 音效

**建议改进**:
- 完善所有 UI 面板
- 添加 UI 动画和音效

---

## 总结和建议

### 高优先级改进（核心游戏机制）

1. **经验值系统**
   - 实现等级差惩罚/奖励
   - 添加组队经验值分配
   - 实现佣兵经验值处理

2. **伤害计算系统**
   - 完善伤害结构（添加所有伤害类型和持续时间）
   - 实现完整的抗性和吸收系统
   - 添加伤害状态效果（燃烧、中毒、冰冻、眩晕）

3. **玩家属性系统**
   - 实现属性点分配系统
   - 添加升级时的属性自动增长
   - 实现技能点分配系统

### 中优先级改进（游戏功能）

4. **物品系统**
   - 实现物品耐久度系统
   - 添加物品修理功能
   - 实现完整的物品生成和掉落系统

5. **技能系统**
   - 参考 D2MOO 实现各职业的技能
   - 添加技能协同机制
   - 实现技能冷却和范围系统

6. **怪物 AI**
   - 实现完整的 AI 状态机
   - 添加路径寻找算法（A*）
   - 实现更复杂的 AI 决策逻辑

### 低优先级改进（完善功能）

7. **任务系统**
   - 实现完整的任务系统
   - 添加任务状态管理和奖励

8. **多人游戏**
   - 完善组队系统
   - 实现交易系统

9. **存档系统**
   - 添加任务进度保存
   - 实现地图探索进度保存

---

## 参考文件列表

### D2MOO 关键文件

1. **经验值系统**
   - `D2Game/src/UNIT/SUnitDmg.cpp:3006` - `SUNITDMG_ComputeExperienceGain`
   - `D2Game/src/UNIT/SUnitDmg.cpp:2908` - `SUNITDMG_DistributeExperience`
   - `D2Game/src/PLAYER/PlayerStats.cpp:75` - `PLAYERSTATS_LevelUp`

2. **伤害计算**
   - `D2Game/include/UNIT/SUnitDmg.h:65` - `D2DamageStrc`
   - `D2Game/src/UNIT/SUnitDmg.cpp:153` - `SUNITDMG_FillDamageValues`
   - `D2Game/src/UNIT/SUnitDmg.cpp:161` - `SUNITDMG_ApplyResistancesAndAbsorb`

3. **玩家属性**
   - `D2Game/src/PLAYER/PlayerStats.cpp:134` - `PLAYERSTATS_SpendStatPoint`

4. **物品系统**
   - `D2Game/src/ITEMS/Items.cpp` - 物品生成和掉落
   - `D2Game/src/UNIT/SUnitDmg.cpp:193` - `SUNITDMG_DrainItemDurability`

5. **技能系统**
   - `D2Game/include/SKILLS/` - 各职业技能头文件
   - `D2Game/src/SKILLS/` - 各职业技能实现

6. **怪物 AI**
   - `D2Game/include/AI/AiStates.h` - AI 状态
   - `D2Game/include/AI/AiThink.h` - AI 决策

7. **任务系统**
   - `D2Game/include/QUESTS/` - 各章节任务

8. **组队和交易**
   - `D2Game/include/UNIT/Party.h` - 组队系统
   - `D2Game/include/PLAYER/PlrTrade.h` - 交易系统

### Riiablo 对应文件

1. **经验值系统**
   - `core/src/main/java/com/riiablo/attributes/ExperienceManager.java`
   - `core/src/main/java/com/riiablo/attributes/ExperienceTable.java`

2. **伤害计算**
   - `core/src/main/java/com/riiablo/engine/server/combat/DamageCalculator.java`

3. **玩家属性**
   - `core/src/main/java/com/riiablo/attributes/Attributes.java`

4. **物品系统**
   - `core/src/main/java/com/riiablo/item/`
   - `core/src/main/java/com/riiablo/screen/panel/InventoryPanel.java`

5. **技能系统**
   - `core/src/main/java/com/riiablo/skill/`

6. **怪物 AI**
   - `core/src/main/java/com/riiablo/ai/`

7. **任务系统**
   - `core/src/main/java/com/riiablo/screen/panel/QuestsPanel.java`

8. **组队和交易**
   - `core/src/main/java/com/riiablo/engine/server/party/PartyManager.java`
   - `core/src/main/java/com/riiablo/engine/server/trade/TradeManager.java`

---

## 结论

D2MOO 作为逆向工程的参考实现，提供了非常详细和准确的游戏逻辑实现。Riiablo 作为跨平台重制项目，可以参考 D2MOO 的实现来：

1. **确保游戏逻辑的准确性**: D2MOO 的实现非常接近原版游戏，可以作为参考标准
2. **完善缺失的功能**: 许多在 D2MOO 中已经实现的功能在 Riiablo 中还未实现
3. **优化代码结构**: D2MOO 的代码组织方式可以作为参考

建议优先实现核心游戏机制（经验值、伤害计算、属性系统），然后逐步完善其他功能。
