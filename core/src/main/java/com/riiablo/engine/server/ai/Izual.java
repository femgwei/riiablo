package com.riiablo.engine.server.ai;

import com.riiablo.Riiablo;
import com.riiablo.codec.Animation;
import com.riiablo.engine.server.quest.Act4QuestSystem;

/** D2MOO AiThink.cpp AITHINK_Fn055_Izual (0x6FCDB3E0). */
public class Izual extends NativeDecisionAI {
  static final int PARAM_ATTACK_CHANCE = 0;
  static final int PARAM_ENGAGE_CHANCE = 1;
  static final int PARAM_NOVA_AT_RANGE_CHANCE = 2;
  static final int PARAM_NOVA_IN_MELEE_CHANCE = 3;
  static final int PARAM_POST_NOVA_DOLDRUMS = 4;
  static final int PARAM_NUM_SWINGS = 5;

  private boolean activated;
  private int doldrums;
  private int swings;

  public Izual(int entityId) {
    super(entityId);
  }

  @Override
  protected void beforeThink(float delta) {
    // Native invokes ACT4Q1_OnIzualActivated once, before any ordinary branch.
    if (activated) return;
    activated = true;
    if (Riiablo.engine != null) {
      Act4QuestSystem quests = Riiablo.engine.getSystem(Act4QuestSystem.class);
      if (quests != null) quests.onIzualActivated(entityId);
    }
  }

  @Override
  protected void think(int targetId, float distance, boolean combat) {
    if (doldrums > 0) {
      idle(doldrums);
      doldrums = 0;
      return;
    }

    if (!combat) {
      if (hasSkill(0) && distance < 10f) {
        if (swings > 0) {
          approach(targetId, 7);
          return;
        }
        if (rollAiChance(params[PARAM_NOVA_AT_RANGE_CHANCE])) {
          nova(targetId);
          return;
        }
      }

      if (swings <= 0 && !rollAiChance(params[PARAM_ENGAGE_CHANCE])) {
        if (distance <= 10f) idle(aidelFrames());
        else approach(targetId, 6);
        return;
      }
      approach(targetId, 7);
      return;
    }

    if (swings <= 0) {
      if (!rollAiChance(params[PARAM_ATTACK_CHANCE])) {
        swings = 0;
        if (!hasSkill(0) || !rollAiChance(params[PARAM_NOVA_IN_MELEE_CHANCE])) {
          idle(aidelFrames());
        } else {
          nova(targetId);
        }
        return;
      }
    } else {
      --swings;
    }
    attack(targetId);
  }

  private void nova(int targetId) {
    // AITACTICS_UseSkill failure leaves the native branch as a ten-frame idle;
    // it must never be silently replaced with a basic attack.
    cast(0, targetId);
    doldrums = Math.max(0, params[PARAM_POST_NOVA_DOLDRUMS]);
    swings = Math.max(0, params[PARAM_NUM_SWINGS]);
  }

  private int aidelFrames() {
    if (!Float.isFinite(SLEEP) || SLEEP <= 0f) return 1;
    return Math.max(1, Math.round(SLEEP / Animation.FRAME_DURATION));
  }

  /** Exposed for integration tests without making the native state mutable. */
  int doldrums() {
    return doldrums;
  }

  /** Exposed for integration tests without making the native state mutable. */
  int swings() {
    return swings;
  }
}
