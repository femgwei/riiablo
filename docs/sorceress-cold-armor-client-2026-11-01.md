# 法师冰甲客户端主动发弹修正

## 原版依据

- 项目已存档的 `docs/d2client-static-skill-evidence.tsv` 第 40、50、60
  技能记录：1.10f `cltstfunc[0]`、`cltdofunc[0]` 均为 NULL。
  本次读取该既有证据，未重新反汇编 DLL。
- D2MOO-reference `source/D2Game/src/SKILLS/SkillSor.cpp`：
  `SKILLS_SrvDo018_DefensiveBuff` 仅添加防御状态、属性与事件监听，不发射反击弹。
- `SKILLS_EventFunc01_ChillingArmor`：在事件回调中检查来袭弹体的
  `ReturnFire`，再按 `SrvMissileA` 创建朝向来袭弹体所有者的反击弹。
- 原版 Skills.txt 对应三种不同事件：Frozen Armor（40）为
  `damagedinmelee` / EventFunc02；Shiver Armor（50）为
  `attackedinmelee` / EventFunc03；Chilling Armor（60）为
  `hitbymissile` / EventFunc01。只有最后一种发射反击冰弹。

## 问题与修正

`SkillCastHandler.cltDoGenericMissile` 在通用客户端回调中回退读取
`cltmissilea`，将 Chilling Armor 的反击弹参数误当作施法弹体。
服务端 `ServerSkillSystem` 的防御状态分支本来已提前返回；
真正的反击由 `StateUpdater` 受击事件创建，不应在施法时重复生成。

客户端通用弹体路径现在排除这三种防御冰甲，保留已有声音、施法覆盖层、
状态覆盖层及服务端受击反击流程，不改动其他技能的通用回退行为。
补充资源回归用例，逐一派发三种冰甲的 SkillDo，检查不生成弹体；
已有亚马逊箭矢用例继续覆盖正常通用弹体路径。

按极小修改约定，本次未编译或运行测试；需另一台电脑验证：空地施法不发弹，
Chilling Armor 遭可反击的远程弹体命中才反击，另外两种冰甲不发射冰弹。
此项是施法/反击触发路径核对，不代表全部法师技能已完成逐帧原版视觉对照。
