# 怪物子代生成与移动速度对齐（2026-10-18）

## 原版依据

- D2MOO `D2Game/src/SKILLS/SkillMonst.cpp`：SrvDo084_MaggotEgg、SrvDo087_MaggotLay。
- `D2Game/src/AI/AiThink.cpp`：AITHINK_Fn066_SandMaggotQueen。
- `D2Common/src/Monsters/Monsters.cpp`：MONSTERS_GetSpawnMode_XY、MONSTERS_GetMinionSpawnInfo。
- `D2Game/src/MONSTER/MonsterSpawn.cpp`：sub_6FC68D70、D2GAME_SpawnNormalMonster_6FC68E30。
- `D2Common/src/Units/Units.cpp`：UNITS_UpdateRunWalkAnimRateAndVelocity。
- `D2Game/src/MONSTER/MonsterUnique.cpp`：MONSTERUNIQUE_UMod6_Fast；MonsterMode.cpp 的临时 AI velocitypercent。

## 本批实现

- 沙虫产卵按方向偏移及 spawn/spawnmode 创建，不再无条件绕过地形、动态单位碰撞。
- 接入缺失的 SrvDo084：计算子代数量、有限重试、子代 NOXP、消耗虫卵；同一 Casting 的重复动作事件不重复孵化。
- 补齐数量公式需要的关系比较及三元条件解析；原版 `(lvl < 5) ? lvl : min(12,5+(lvl-5)/3)` 原先会被算成零，现有 1/2/4/5/7/8/25/26/50 级边界回归。
- 女王按准备、生成、长等待三个阶段运行；按 queen/sandmaggot 类链索引选择子代，采用 x+8/y 基准，只有成功出生才增加计数。
- 新增服务端子代出生适配入口，按体积检查地形和动态占位，并立即预留同帧新单位占位；孵化允许替换虫卵自身的占位，不忽略其他单位。
- 怪物速度改为基础 velocitypercent、AI 临时加速、狂乱、光环及减速相加，再统一使用最低 25% 限幅；特别快速词缀已写入的属性现在实际影响移动。
- 积分层只应用一次移动修正；静止但仍有移动意图时的动画速度也使用有效倍率。玩家移动公式保持原路径。

## 后续更新

普通分支的房间 RNG、SpawnCol mask 分类、虫卵开始孵化和通用 MonSeq 客户端帧跟随已继续接入。最新依据及仍未实现的特殊分支见 [出生搜索专项记录](ai-native-spawn-search-2026-10-18.md)。

## 初批提交时的精度边界（5cd8696b4，后续状态以上述记录为准）

- 子代出生搜索仍是宿主确定性方环适配，不是原版房间 RNG 的周界起点/遍历顺序；也没有完整还原 SpawnCol 碰撞矩阵与女王分配 flags=66。
- SrvSt43 的目标标志清除及原生 dead-collision 切换尚未接入；本批孵化通过出生检查排除虫卵自身占位解决替换，不代表完整原生碰撞生命周期。
- 测试直接驱动技能动作处理器及女王决策阶段，不等同于全部 MonSeq/COF 动作帧、自然地图或客户端视觉验收。
- 不扩展固定点逐帧移动，也不把浮点子代出生坐标标记为已证明的原生 raw 坐标。
- 本批重点是沙虫链；不能据此宣称所有巢穴、通用生成器及 sumumod 组合完全对齐。

## 验证

使用本地 1.10F 数据及隔离构建目录，35 项针对性测试通过：MaggotSpawnExecutionTest（4）、MonsterSpeedCompositionTest（3）、MaggotIntegrationTest（2）、NestSwarmIntegrationTest（3）、MovementMultiplierContractTest（4）、PlayerMovementPercentageTest（3）、VelocityModeChangerTest（11）、VelocityAdderTest（2）、SkillFormulaTest（3）。

`:core:architectureRegression` 同次执行成功。未运行全量 AI/自然地图/客户端验收。
