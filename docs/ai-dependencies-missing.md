# 怪物 AI 移植依赖与进度

范围：1.10f 目录中原先缺少专用实现的 37 类 AI（136 个 MonStats 行）。
小依赖随对应 AI 一起实现；只有确认的大依赖在此登记。新增类或消除 fallback 本身不代表完成。
每完成一类，核对原版分支并通过测试后更新剩余量，向用户汇报。

## 本轮实现

累计完成 4/37，剩余 33 类。

- Trap-Melee：已完成 Fn087 的攻击概率、失败等待、非近战及无目标固定 40 帧等待；补齐表名到 Java 类的显式映射。隔离源码测试通过，证据见 `ai-arena/20261016-trap-melee/`。本轮完成 1 类，剩余 36 类。测试覆盖 AI 决策与共享动作生命周期，未进行图形客户端场景验收。
- Trap-Missile：已完成 Fn077 的距离/次数限制、交替等待、无目标/超距/耗尽死亡及禁止掉落；补齐表名映射。6 项专用测试及相关回归共 37 项通过，证据见 `ai-arena/20261016-trap-missile/`。复用现有飞弹攻击和单位标志，无新增大依赖；未进行自然地图飞弹命中验收。完成后剩余 35 类。
- Trap-Nova：已完成 Fn080/092 中的新星陷阱决策，含距离/次数限制、交替等待、缺技能不计数、施放失败仍计数、死亡及禁止掉落；补齐表名映射。真实表 Skill1 为 Trap Nova（SrvDo022，S1），复用现有新星处理。7 项专用测试及相关回归共 65 项通过，证据见 `ai-arena/20261016-trap-nova/`。无新增大依赖，未进行自然地图新星命中验收。完成后剩余 34 类；Trap-Poison 未计入本项。

- Trap-Poison：已接入原版 Fn080/092 共用决策，以自身 PrimePoisonNova（SrvDo099，A1）施放。随 AI 补齐 SrvDo099 两组方向/速度、Calc1 循环数和 Calc2 步长的小依赖。5 项毒云 AI 测试、1 项三难度技能生产路径测试及相关回归共 71 项通过，证据见 `ai-arena/20261016-trap-poison/`。无新增大依赖，未进行自然地图毒云命中验收。完成后剩余 33 类。

## 已确认的大依赖

暂无登记。后续条目必须注明受影响 AI、原版函数/位置、缺少的生产能力、行为影响及后续工作。未核实的依赖不作阻塞依据。

## 待完成（33）

7TIllusion、AbyssKnight、Ancient、BaalThrone、BaalToStairs、BatDemon、Catapult、CatapultSpotter、ClawViperEx、DarkWanderer、FetishBlowgun、FingerMage、GenericSpawner、HighPriest、Imp、InvisoSpawner、MinionSpawner、Mosquito、Nihlathak、OblivionKnight、Overseer、QuillMother、ReanimatedHorde、Regurgitator、SiegeBeast、SiegeTower、SuccubusWitch、SuicideMinion、ThornHulk、Trap-LeftArrow、Trap-RightArrow、VileMother、WillOWisp。

Baboon、Scarab 已有专用类，不在本轮 37 类中；此处不将其视为完整原版验收。
