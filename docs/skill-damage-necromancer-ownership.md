# Necromancer 技能伤害所有者审计

基线：Diablo II 1.10f

范围：技能 ID 66–95，共 30 项

明细真源：`skill-damage-necromancer-ownership.tsv`

## 结论

- 30/30 个死灵法师技能已经确认伤害所有者和 D2MOO 调用路径。
- 11 项伤害或伤害修正路径已有聚焦生产测试：Amplify Damage、Poison Dagger、Corpse
  Explosion、Clay Golem、Iron Maiden、Poison Explosion、Blood Golem、Iron Golem、
  Lower Resist、Poison Nova 和 Fire Golem。
- 9 项生产路径存在但伤害测试仍不足：Teeth、Skeleton Mastery、Raise Skeleton、
  Weaken、Raise Skeletal Mage、Bone Spear、Decrepify、Bone Spirit 和 Revive。
- 10 项没有独立输出伤害曲线。其中 Bone Armor、Dim Vision、Terror、Bone Wall、
  Golem Mastery、Confuse、Life Tap、Attract 和 Bone Prison 是明确 N/A；Summon Resist
  还暴露一个防御侧实现缺口；其余诅咒和被动只修改后续伤害所有者。
- 本子项只确认所有者与语义，不向黄金矩阵填写 `expected_*`。召唤伤害必须在后续
  DMG-04/06/07 中把 MonStats、召唤技能 stat、装备或原怪物技能分别建模，不能把
  `Skills.txt` 中的零值误写成 0–0 伤害。

## 明确实现缺口

### Summon Resist（89）

D2MOO 的 `SKILLS_RefreshPassiveSkills` 先把 `passive_summon_resist` 安装到施法者，
`D2GAME_SetSummonResistance_6FD0C2E0` 再在召唤时读取该属性并复制到宠物。riiablo 的
`ServerSkillSystem.applySummonResistance` 同样读取施法者的
`Stat.passive_summon_resist`，但 `StateUpdater` 只同步 Barbarian、Paladin、Amazon 和
Sorceress 被动，没有 Necromancer 被动同步入口。代码库中除该读取点外也没有生产写入。

因此当前技能本身没有输出伤害值，但宠物抗性不会按硬点生效。这是已确认的防御侧
实现缺口，应在抗性/召唤继承审计中修复并增加“硬点变化—新召唤物四抗变化”的回归。

## 仍缺聚焦测试的伤害路径

- Teeth（67）：缺一次施法的 `calc1` 导弹数、单枚魔法伤害和多目标碰撞联合回归。
- Skeleton Mastery（69）与 Raise Skeleton（70）：缺固定主人等级下，召唤技能硬点、
  Skeleton Mastery 硬点和原生 MonEquip 对最终近战 min/max 的精确断言。
- Weaken（72）：缺诅咒前后同一武器物理包只衰减一次的最终结算测试。
- Raise Skeletal Mage（80）：缺四种法师元素、Skeleton Mastery、怪物技能导弹快照和
  抗性结算的完整场景。
- Bone Spear（84）：缺 exact magic min/max、穿透多个目标和每目标命中门禁联合测试。
- Decrepify（87）：缺物理抗性、速度和攻击速率三组状态在生产场景中的联合验证。
- Bone Spirit（93）：缺目标重获、追踪命中和 exact magic min/max 联合测试。
- Revive（95）：缺一个固定原怪物的普通攻击与技能伤害在 Revive 被动修正后的精确测试。

## 后续黄金值约束

1. Teeth、Bone Spear 和 Bone Spirit 的基础魔法曲线归 Skills.txt；Missiles.txt 只拥有
   轨迹、碰撞和命中回调。不能把导弹表中空伤害列当作零伤害。
2. Poison Dagger、Poison Explosion 和 Poison Nova 的 `EMin/EMax` 是 8.8 每帧毒率。
   黄金矩阵必须分别保存 rate、duration 和 total，且保留硬点协同与毒素精通的取整顺序。
3. Corpse Explosion 以尸体基础最大生命和施法者/尸体等级关系为输入，再由 `calc3`
   分成物理与火焰；不能为每个技能等级生成脱离尸体场景的固定 min/max。
4. Iron Maiden 必须提供来袭近战物理伤害和攻击者类型；玩家/佣兵使用原生八分之一折算。
5. Raise Skeleton、Raise Skeletal Mage 和四种 Golem 的伤害由 MonStats、技能安装的
   passive/aura stat、生成装备或来源物品以及宠物攻击技能共同决定。需要独立召唤场景矩阵。
6. Fire Golem 至少拆分普通近战、近战附火和 Holy Fire 周期脉冲，不能合成一个“技能伤害”。
7. Revive 保留原怪物 MonStats 和技能槽；黄金值业务键必须包含原怪物、等级和攻击技能。
8. 无输出伤害技能用明确 N/A 原因收口，不能填 0–0 冒充已验证黄金值。

## 可复现验证

```powershell
$env:D2_HOME = 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat :core:test --tests com.riiablo.engine.server.NecromancerDamageOwnershipTest --no-daemon
```

该门禁核对 30 行完整性、Skills.txt 的 ID/名称、每项 D2MOO/riiablo/测试证据、
Summon Resist 缺口以及 9 个聚焦测试缺口不会被静默标记为已实现。
