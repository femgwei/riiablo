package com.riiablo.engine.server.ai;

import com.riiablo.Riiablo;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.AuraEcsSystem;
import com.riiablo.codec.excel.Skills;

/** D2MOO AiThink.cpp AITHINK_Fn044_Duriel (0x6FCD8FE0). */
public class Duriel extends NativeDecisionAI {
  static final int PARAM_HOLY_FREEZE_LEVEL = 0;
  static final int PARAM_SMITE_CHANCE = 1;
  static final int PARAM_JAB_CHANCE = 2;
  static final int PARAM_ATTACK2_CHANCE = 3;
  static final int PARAM_CHARGE_CHANCE = 4;

  private boolean holyFreezeSelected;

  public Duriel(int entityId) {
    super(entityId);
  }

  @Override
  protected void think(int targetId, float distance, boolean combat) {
    selectHolyFreeze();
    if (combat) {
      if (hasSkill(2) && rollAiChance(params[PARAM_SMITE_CHANCE])) {
        cast(2, targetId);
      } else if (hasSkill(1) && rollAiChance(params[PARAM_JAB_CHANCE])) {
        cast(1, targetId);
      } else if (rollAiChance(params[PARAM_ATTACK2_CHANCE])) {
        attack(targetId, Engine.Monster.MODE_A2);
      } else {
        attack(targetId, Engine.Monster.MODE_A1);
      }
      return;
    }

    if (hasSkill(0) && rollAiChance(params[PARAM_CHARGE_CHANCE])) {
      cast(0, targetId);
    } else {
      // Native AITACTICS_SetVelocity(pUnit, 13, 0, 0) precedes the flagged walk.
      approach(targetId, 13);
    }
  }

  /** Native setup assigns Holy Freeze as the right-hand skill once per unit. */
  private void selectHolyFreeze() {
    if (holyFreezeSelected) return;
    holyFreezeSelected = true;
    if (!hasSkill(3) || Riiablo.engine == null) return;
    AuraEcsSystem auras = Riiablo.engine.getSystem(AuraEcsSystem.class);
    if (auras == null) return;
    Skills.Entry skill = files().skills.get(monster.monstats.Skill4);
    if (skill != null) {
      auras.selectAuraForMonster(entityId, skill.Id, params[PARAM_HOLY_FREEZE_LEVEL]);
    }
  }
}
