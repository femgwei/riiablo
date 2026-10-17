package com.riiablo.engine.server.ai;

import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;

/** D2MOO AITHINK_Fn115_SiegeBeast, 0x6FCE0610. */
public class SiegeBeast extends SiegeTower {
  public SiegeBeast(int entityId) { super(entityId); }
  @Override protected void thinkWithoutTarget() { idle(1); }
  @Override protected void think(int targetId, float distance, boolean combat) {
    if (monster.monstats.Align == 0) recruitImp(params[0] * params[0]);
    if (combat) {
      if (hasSkill(0) && rollAiChance(params[2])) {
        castAt(0, Engine.INVALID_ENTITY, new com.badlogic.gdx.math.Vector2());
      } else if (rollAiChance(params[1])) attack(targetId);
      else idle(params[3]);
      return;
    }
    if (hasSkill(0)) {
      Skills.Entry skill = Riiablo.files.skills.get(monster.monstats.Skill1);
      int range = skill != null && skill.Param != null && skill.Param.length > 4
          ? skill.Param[4] : 0;
      if (distance < range && rollAiChance(params[4])) {
        castAt(0, Engine.INVALID_ENTITY, new com.badlogic.gdx.math.Vector2()); return;
      }
    }
    int bonus = chargeLineClear(targetId) && rollAiChance(params[5])
        ? Math.max(0, Math.min(127, params[6])) : 0;
    if (!walkInRadius(targetId, 12, 0, bonus)) idle(10);
  }
}
