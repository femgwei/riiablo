# 怪物 AI 移植依赖与进度

范围：1.10f 目录中原先缺少专用实现的 37 类 AI（136 个 MonStats 行）。
小依赖随对应 AI 一起实现；只有确认的大依赖在此登记。新增类或消除 fallback 本身不代表完成。
每完成一类，核对原版分支并通过测试后更新剩余量，向用户汇报。

## 本轮实现

- Trap-Melee：已完成 Fn087 的攻击概率、失败等待、非近战及无目标固定 40 帧等待；补齐表名到 Java 类的显式映射。隔离源码测试通过，证据见 `ai-arena/20261016-trap-melee/`。本轮完成 1 类，剩余 36 类。测试覆盖 AI 决策与共享动作生命周期，未进行图形客户端场景验收。

## 已确认的大依赖

暂无登记。后续条目必须注明受影响 AI、原版函数/位置、缺少的生产能力、行为影响及后续工作。未核实的依赖不作阻塞依据。

## 待完成（36）

7TIllusion、AbyssKnight、Ancient、BaalThrone、BaalToStairs、BatDemon、Catapult、CatapultSpotter、ClawViperEx、DarkWanderer、FetishBlowgun、FingerMage、GenericSpawner、HighPriest、Imp、InvisoSpawner、MinionSpawner、Mosquito、Nihlathak、OblivionKnight、Overseer、QuillMother、ReanimatedHorde、Regurgitator、SiegeBeast、SiegeTower、SuccubusWitch、SuicideMinion、ThornHulk、Trap-LeftArrow、Trap-Missile、Trap-Nova、Trap-Poison、Trap-RightArrow、VileMother、WillOWisp。

Baboon、Scarab 已有专用类，不在本轮 37 类中；此处不将其视为完整原版验收。
