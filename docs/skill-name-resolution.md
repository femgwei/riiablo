# 技能名称解析约定

`skills.txt` 永远只保存技能的内部英文 ID。`skill` 列是运行时、存档和数据表使用的逻辑标识，不能直接作为界面文本。

角色属性页、技能树、快捷栏和物品技能属性使用原版数据链：

```text
skills.txt.skill
  -> skills.txt.skilldesc
  -> skilldesc.txt.skilldesc
  -> skilldesc.txt.str name (TBL key)
  -> PatchString.tbl > ExpansionString.tbl > String.tbl
  -> 最终本地化显示文本
```

对应代码为：

```java
Skills.Entry skill = Riiablo.files.skills.get(skillId);
SkillDesc.Entry desc = Riiablo.files.skilldesc.get(skill.skilldesc);
String displayName = Riiablo.string.lookup(desc.str_name);
```

技能树、快捷栏和详情标题使用 `SkillNameResolver.name(skill)`（`str name`）；角色属性页选中技能名称使用 `SkillNameResolver.characterScreenName(skill)`（`str alt`）。资源未加载或 key 缺失时回退到另一层本地化名称，而不是把 `skill.skill` 内部 ID 显示给玩家。

已核对的入口包括：

- `CharacterPanel` 动态攻击/伤害技能名（优先使用 `str alt` 角色页别名）；
- `SpellsPanel` 技能树和详情；
- `SkillDetails` / `HotkeyButton` 快捷技能详情；
- `StatFormatter` 的 `+技能`、按等级施放和 `oskill` 属性；
- `MercenaryHud` 召唤物头像名称。

服务端技能分支仍然可以使用 `Skills.Entry.skill`，存档仍保存数字技能 ID；本地化只发生在 TBL 字符串层，字体只负责绘制最终字符串。
