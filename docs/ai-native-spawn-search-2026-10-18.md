# 普通出生搜索与客户端 MonSeq（2026-10-18）

## 依据与搜索规则

D2MOO `MonsterSpawn.cpp` 的 `D2GAME_SpawnNormalMonster_6FC68E30` 仍是 return nullptr 加注释反编译。
因此本轮另行只读反汇编本地 1.10F D2Game.dll，核对 0x6FC691DB–0x6FC69481：

- 精确出生半径为 0；普通范围搜索从 3 开始，每圈 +3，圈内最多检查 8r 次。
- 每圈先 roll 选横/纵边；半径大于零时再 roll，以低 32 位 unsigned 值 % r 取边内偏移；之后再 roll 两次选择 X/Y 符号。
- 半径零也消耗三次随机数，不是零次；先移动一步再检查。
- 转角改方向；初始方向并不随符号归一化，可能重复探测某些坐标。保留原顺序，不去重、不改成均匀随机周界。
- 普通分支候选受当前房间矩形约束，不能把相邻房间当作任意搜索扩展。
- NativeMonsterSpawnSearchTest 用固定起始种子核对访问序列、最终 low/high 状态、精确出生 RNG 消耗及失败尝试数。

`D2Dungeon.cpp::DUNGEON_AllocRoom` 用单独低种子初始化活动房间；`DrlgDrlg.cpp` 分配时从 RoomEx 种子 roll 得到该低种子。
RoomEx.runtimeSeed 首次访问时如已有活动房间，就共享其实时 seed 对象；对象人口和 AI 出生不会各用一份拷贝。
未建立活动房间时仍有旧人口快照回退；无 RoomEx 的旧/合成地图使用工厂私有的按 Zone 分离流。这两种回退不承诺原版初始种子或完整回放一致性。

## 碰撞与动作接入

- SpawnCol 分派按原版 mask：默认 0x3C01、1 为 0x01C0、2 为 0x3F11、3 为零；不再使用单位 walking/flying profile 替代出生策略。
- 普通分支按 size 检查点/十字/3×3；区分静态低位标记、物件/门引用、玩家/怪物足迹、NO_PATH/PET 中心或十字标记，以及落地物品。
- 精确出生也使用房间搜索入口，成功后预留同帧子代占位；出生动作的 COF mode 在分配时立即提交，不先以 NU 请求资源。
- SrvSt43 清除虫卵目标位、移除活体占位；刷新碰撞索引也不会把正在孵化的虫卵重新插回活体层。
- 客户端 AnimationStepper 补上通用 nativeMonster MonSeq，跟随服务器源帧的重复、回退和停留，不再独立按 COF 速度前进；序列结束恢复普通播放。
- COF 资源队列回归夹具补注册当前要求的 clientContext；不通过放宽生产注入要求绕过测试。

## 仍未完成，不能称作完整原版验收

- SpawnCol=1 的通常路径是特殊地板贴图搜索，并有触手/陨石例外；本入口明确拒绝它，未用普通周界假冒。特殊贴图、missile 标记层、部分 no-presence 单位标记及分配 flags=66 等仍需补齐。
- 现有宿主物件层与动态索引不是完整 16 位原生碰撞网格；本批只对已存在的各层按原生 mask 分类，未宣称所有碰撞矩阵/特殊状态组合完成。
- 孵化只替换活体占位，未增加完整 CORPSE 标记与所有 dead-collision 交互。
- 客户端回归使用真实 MPQ COF 和动画状态，不加载 DCC/GL 场景截图；全怪物、自然地图、资源异步切换下的视觉效果仍需实机验收。

## 验证

使用本地 1.10F 数据与隔离构建目录，44 项针对性测试全部通过：NativeMonsterSpawnSearchTest（4）、MaggotSpawnExecutionTest（5）、NativeMonsterSequenceIntegrationTest（2）、AnimationStepperTest（9）、Cof110fModeTransitionTest（1）、CofModeTransitionTest（4）、NestSwarmIntegrationTest（3）、RoomExAdjacencyTest（5）、NativeObjectPopulationResourceTest（10）、NativeContainerPopulationResourceTest（1）。

架构检查起初在字符串扫描正则发生 StackOverflowError；将互斥字符分支的重复改为等价 possessive repetition，未增加预算或放宽依赖规则，之后 `:core:architectureRegression` 三项全部通过。未运行全量测试或客户端 DCC/GL 场景验收。
