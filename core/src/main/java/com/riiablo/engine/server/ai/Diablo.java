package com.riiablo.engine.server.ai;

import com.artemis.Aspect;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;
import com.riiablo.codec.Animation;
import com.riiablo.engine.Engine;

/** D2MOO AiThink.cpp AITHINK_Fn051_Diablo (0x6FCE8950). */
public class Diablo extends NativeDecisionAI {
  // Native action numbers.  The values are the indices consumed by the
  // original 17-entry AI_GetRandomArrayIndex table.
  static final int ACTION_WALK = 1;
  static final int ACTION_ATTACK1 = 2;
  static final int ACTION_ATTACK2 = 3;
  static final int ACTION_SKILL4 = 4;
  static final int ACTION_INFERNO = 5;
  static final int ACTION_SKILL3 = 6;
  static final int ACTION_SKILL2 = 7;
  static final int ACTION_SKILL4_ALT = 8;
  static final int ACTION_SKILL7 = 9;
  static final int ACTION_SKILL5 = 10;
  static final int ACTION_STALL = 11;
  static final int ACTION_CIRCLE = 12;
  static final int ACTION_SKILL6 = 13;
  static final int ACTION_RUSH = 14;
  static final int ACTION_PRISON = 15;
  static final int ACTION_CLOSE = 16;

  private int action;
  private int actionCount;

  public Diablo(int entityId) {
    super(entityId);
  }

  @Override
  protected void thinkWithoutTarget() {
    // Fn051's no-target branch selects either a slow idle or a short move and
    // still gives Skill8 (the native aura slot) a chance to become active.
    if (hasSkill(7)) castAt(7, Engine.INVALID_ENTITY, mPosition.get(entityId).position);
    else idle(defaultStallFrames());
    action = 0;
  }

  @Override
  protected int findNearestTargetWithAidist(float[] outDistance) {
    // AITHINK_CullPotentialTargetsForDiablo uses a hard 55-unit same-act
    // radius, independent of the ordinary MonStats aiDist scan.
    EntitySubscription units = getEnemyEntities();
    IntBag entities = units.getEntities();
    int selected = Engine.INVALID_ENTITY;
    float best = Float.MAX_VALUE;
    for (int i = 0; i < entities.size(); i++) {
      int id = entities.get(i);
      if (!isValidEnemyTarget(id) || !mPosition.has(id)) continue;
      float distance = nativeAiDistance(mPosition.get(entityId).position,
          mPosition.get(id).position);
      if (distance < 55f && distance < best) {
        best = distance;
        selected = id;
      }
    }
    outDistance[0] = best;
    if (selected == Engine.INVALID_ENTITY) stopMovement();
    return selected;
  }

  @Override
  protected void think(int targetId, float distance, boolean combat) {
    if (action != 0) {
      execute(targetId, distance);
      return;
    }

    int[] chances = chances(distance, combat, actionCount);
    action = selectAction(chances, rollAiRandomNumber());
    execute(targetId, distance);
  }

  private void execute(int targetId, float distance) {
    switch (action) {
      case ACTION_WALK:
        actionCount = 0;
        action = 0;
        approach(targetId, 20);
        return;
      case ACTION_ATTACK1:
        actionCount++;
        action = 0;
        attack(targetId, Engine.Monster.MODE_A1);
        return;
      case ACTION_ATTACK2:
        actionCount++;
        action = 0;
        attack(targetId, Engine.Monster.MODE_A2);
        return;
      case ACTION_SKILL4:
      case ACTION_SKILL4_ALT:
        castOrStall(3, targetId);
        return;
      case ACTION_INFERNO:
        if (!hasSkill(0)) {
          idle(2);
          action = 0;
          return;
        }
        // Native keeps action 5 armed while Inferno is active, allowing the
        // next tick to toggle the state off instead of launching an attack.
        if (actionCount > 0) {
          actionCount = 0;
          action = 0;
          idle(2);
        } else if (castAt(0, targetId, mPosition.get(targetId).position)) {
          actionCount = 1;
          delayDecision(2);
        } else {
          action = 0;
        }
        return;
      case ACTION_SKILL3:
        castOrStall(2, targetId);
        return;
      case ACTION_SKILL2:
        castOrStall(1, targetId);
        return;
      case ACTION_SKILL7:
        castOrStall(6, targetId);
        return;
      case ACTION_SKILL5:
        castOrStall(4, targetId);
        return;
      case ACTION_CIRCLE:
        action = 0;
        actionCount = 0;
        circle(targetId, 4f);
        return;
      case ACTION_SKILL6:
        castOrStall(5, targetId);
        return;
      case ACTION_RUSH:
      case ACTION_CLOSE:
        int velocityBonus = action == ACTION_RUSH ? 50 : 20;
        action = 0;
        actionCount = 0;
        approach(targetId, velocityBonus);
        return;
      case ACTION_PRISON:
        // The native branch targets a Chaos Sanctuary town portal.  Portal
        // GUIDs are not exposed to this AI layer, so preserve its movement
        // pressure and use Skill7 only when a normal target is available.
        castOrStall(6, targetId);
        return;
      case ACTION_STALL:
      default:
        action = 0;
        idle(defaultStallFrames());
    }
  }

  private void castOrStall(int slot, int targetId) {
    action = 0;
    actionCount++;
    if (!hasSkill(slot) || !castAt(slot, targetId, mPosition.get(targetId).position)) {
      actionCount = 0;
    }
  }

  /** Builds the native table's stable branches without pretending to know
   * the original portal, resistance and missile-barrier state here. */
  static int[] chances(float distance, boolean combat, int counter) {
    int[] chance = new int[17];
    if (combat) {
      chance[2] = 40;
      chance[3] = 70;
      chance[5] = 40;
      chance[6] = 24;
      chance[7] = 40;
      chance[8] = 15;
    } else {
      chance[5] = 25;
      chance[6] = 25;
      chance[8] = 15;
      chance[9] = 20;
      chance[10] = 10;
      chance[12] = 20;
      if (distance > 25f) {
        chance[5] = 0;
        chance[8] = Math.max(0, chance[8] - 5);
        chance[10] = 20;
      }
      if (counter < 2) chance[6] = Math.max(0, chance[6] - 10);
    }
    return chance;
  }

  static int selectAction(int[] chance, long roll) {
    int total = 0;
    for (int value : chance) total += Math.max(0, value);
    if (total <= 0) return ACTION_STALL;
    int selected = (int) (roll % total);
    for (int i = 0; i < chance.length; i++) {
      selected -= Math.max(0, chance[i]);
      if (selected < 0) return i;
    }
    return ACTION_STALL;
  }

  private int defaultStallFrames() {
    switch (resolveAiDifficulty()) {
      case 1: return 8;
      case 2: return 4;
      default: return 12;
    }
  }
}
