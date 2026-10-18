# 2026-10-19 空属性护身符与占位图标

## 存档证据

只读检查用户提供的 `aaa.d2s`，文件 SHA-256：
`8E180731A2A28439FED8A729FFF7EBF138758D8FF51285B932278781EFE24ACB`。

- 背包 `(4,2)` 的小护身符：`code=cm1, id=1318, ilvl=2,
  quality=NORMAL, qualityId=0, pictureId=-1`，魔法属性列表为空。
- 另一个新生成的大护身符 `cm2, id=1296` 虽有魔法词缀，也缺少图标编号。
- 鉴定书 `ibk` 的实际数量为 5，上限 20。把小护身符拖到书上交换不是补书失败。
- 本轮撤回了基于此前错误假设的补书修改，不改变卷轴合并或容量逻辑。

## 原版依据与原因

本机 1.10f ItemTypes：`scha/mcha/lcha` 均 `Magic=1, Rare=0,
VarInvGfx=3`；其图标分别是 `invch1/4/7`、`invch2/5/8`、`invch3/6/9`。
Misc 的基础 invfile（例如 `cm1=invchm`, `cm2=invwnd`）不是应当直接使用的护身符变体。

D2MOO `source/D2Game/src/ITEMS/Items.cpp`：

- `D2GAME_DropTC_6FC51360` 在品质流程中处理 ItemTypes.Magic。
- `D2GAME_ITEMS_AssignSpecial_6FC4DB60` 对护身符调用
  `ITEMS_AssignCharmAffixes`（0x6FC4DA10），要求至少一个前缀或后缀。
- 物品初始化在 VarInvGfx 非零时调用 `ITEMS_SetVarGfxIndex`，
  从该物品 seed 随机选择类型表中的图标。
- `source/D2Common/src/Items/Items.cpp` 的 `ITEMS_GetVarInvGfxString`
  将图标索引夹在零与 `VarInvGfx-1` 之间。

riiablo 的生成器此前接受普通品质护身符请求，跳过魔法词缀分支；
且没有为生成物品设置 pictureId，显示代码因此回落到基础占位图。
这两条路径均可独立复现，与鉴定书无关。

## 修改与旧存档边界

- 非任务、非强制普通类型的掉落遵守 ItemTypes.Magic 最低品质要求，
  护身符不会因普通/劣质/超强请求生成白色空属性物品。
- 生成时按 VarInvGfx 选择并持久化有效 pictureId，包括魔法奖励。
- 已有物品缺失的 pictureId 在显示时使用第一个变体，越界值按原版夹到末尾，
  不修改其持久化字段、品质或属性；套装/暗金专属图标优先级保留。
- 旧空属性护身符没有可恢复的原始词缀；不擅自删除、重掷或覆盖用户存档。
  本次只纠正它的图标，品质/词缀修复需用户另行选择。

## 验证

23 项定向测试通过（零失败/跳过）。真实 1.10f MPQ 覆盖三种护身符、
五种品质请求、12 个 seed，共 180 个生成/往返案例，
检查魔法属性非空、图标变体范围与随机性、ItemWriter/ItemReader 往返；
并覆盖旧空属性护身符非破坏性显示回退与原有物品生成规则。
追加容器掉落适配、死亡奖励、暗金掉落整合测试共 10 项通过，零失败/跳过。
本轮不属于极小改动，运行定向测试，不作全游戏图形验收声明。
