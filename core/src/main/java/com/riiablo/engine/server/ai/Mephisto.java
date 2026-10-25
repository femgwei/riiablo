package com.riiablo.engine.server.ai;

import com.artemis.utils.IntBag;
import com.badlogic.gdx.ai.utils.Collision;
import com.badlogic.gdx.ai.utils.Ray;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.map.DT1;

/** D2MOO AiThink.cpp AITHINK_Fn050_Mephisto (0x6FCDA300). */
public class Mephisto extends NativeDecisionAI {
  @Override void writeRuntime(java.io.DataOutputStream out) throws java.io.IOException {
    super.writeRuntime(out);
    out.writeInt(burstRemaining);
    out.writeInt(spellCounter);
    out.writeInt(phase);
  }
  @Override void readRuntime(java.io.DataInputStream in, boolean apply) throws java.io.IOException {
    super.readRuntime(in, apply);
    int savedBurstRemaining = in.readInt();
    int savedSpellCounter = in.readInt();
    int savedPhase = in.readInt();
    if (apply) {
      burstRemaining = savedBurstRemaining;
      spellCounter = savedSpellCounter;
      phase = savedPhase;
    }
  }
  static final int PARAM_CAST_OR_MELEE_CHANCE = 0;
  static final int PARAM_LOWEST_LIFE_TARGET_CHANCE = 1;
  // Native callback tests the caster's UNIT_PLAYER type, not the candidate's.
  // Its second target bucket is therefore empty for a monster Mephisto.
  static final int PARAM_SECOND_TARGET_CHANCE = 2;

  private int burstRemaining;
  private int spellCounter;
  private int phase;
  private final Vector2 destination = new Vector2();
  private final Ray<Vector2> ray = new Ray<>(new Vector2(), new Vector2());
  private final Collision<Vector2> collision = new Collision<>(new Vector2(), new Vector2());

  public Mephisto(int entityId) {
    super(entityId);
  }

  @Override
  protected void think(int targetId, float distance, boolean combat) {
    int life = lifePercentage();
    int lifeChance = Math.max((100 - life) / 5, 0);
    int state = phase;
    if (combat) {
      // Native uses <= here: a parameter of zero still accepts a zero roll.
      if (rollAiPercentage() <= params[PARAM_CAST_OR_MELEE_CHANCE]) {
        state = 2;
        burstRemaining = (int) (rollAiRandomNumber() % 3) + 3;
      } else {
        state = 3;
      }
    } else if (distance > 20f) {
      state = 4;
    } else if (phase == 0) {
      if (life <= 20 && distance < 5f && rollAiChance(40)) {
        state = 1;
      } else if (rollAiPercentage() >= lifeChance + 50) {
        pauseOrMove(targetId, distance);
        phase = 0;
        return;
      } else {
        state = 2;
        burstRemaining = (int) (rollAiRandomNumber() % 3) + 3;
      }
    }

    switch (state) {
      case 0:
        idle(5);
        phase = 4;
        return;
      case 1:
        state = 0;
        if (escape(targetId)) {
          phase = state;
          return;
        }
        if (combat) {
          cast(2, targetId);
          phase = state;
          return;
        }
        break;
      case 2:
        break;
      case 3:
        if (rollAiPercentage() >= lifeChance + 80) {
          if (!orbit(targetId, 3f, 50)) wander(12, 50);
        } else if (rollAiPercentage() >= 80 - lifeChance) {
          cast(2, targetId);
        } else {
          attack(targetId);
        }
        phase = 0;
        return;
      case 4:
        // Approximation of WalkToOwner(6) / WalkInRadiusToTarget(12, 6).
        // Keep the +50 native walking bonus, not a switch to run mode.
        if (!walkToPoint(mPosition.get(targetId).position, 50, targetId)
            && !orbit(targetId, 12f, 50)) wander(12, 50);
        phase = 0;
        return;
      default:
        pauseOrMove(targetId, distance);
        phase = 0;
        return;
    }

    if (--burstRemaining == 0) state = 0;
    if (spellCounter == 0) {
      spellCounter = 2;
      if (!orbit(targetId, 3f, 0)) wander(12, 0);
      phase = state;
      return;
    }

    ++spellCounter;
    int castTarget = findSpellTarget(targetId);
    int validSkills = 0;
    while (validSkills < 8 && hasSkill(validSkills)) ++validSkills;
    if (validSkills == 0) {
      wander(6, 0);
    } else {
      int roll = rollAiPercentage();
      int difficulty = resolveAiDifficulty();
      boolean barrier = difficulty != 0 && hasMissileBarrier(castTarget);
      cast(selectSkillSlot(roll, validSkills, distance, difficulty, barrier), castTarget);
    }
    if (rollAiPercentage() < 50 - lifeChance) spellCounter = 0;
    phase = state;
  }

  /** Slot count includes melee Skill3, even though ranged selection never picks it. */
  static int selectSkillSlot(int roll, int validSkills, float distance,
      int difficulty, boolean barrier) {
    int chance = 100 / validSkills;
    if (difficulty != 0 && (barrier || distance > 30f)) return 5;
    if (difficulty != 0 && distance < 15f && roll < chance) return 4;
    if (roll < 2 * chance) return 3;
    if (roll < 3 * chance) return 1;
    return 0;
  }

  /** Native callback prefers lowest absolute integer HP, not lowest percentage. */
  int findSpellTarget(int fallback) {
    int selected = Engine.INVALID_ENTITY;
    int lowestLife = Integer.MAX_VALUE;
    Vector2 source = mPosition.get(entityId).position;
    IntBag targets = getEnemyEntities().getEntities();
    for (int i = 0; i < targets.size(); i++) {
      int id = targets.get(i);
      if (!isValidEnemyTarget(id) || mCorpse.has(id)) continue;
      Vector2 position = mPosition.get(id).position;
      long dx = (long) Math.round(source.x) - Math.round(position.x);
      long dy = (long) Math.round(source.y) - Math.round(position.y);
      if (dx * dx + dy * dy > 1024) continue;
      int hp = (int) statValue(id, Stat.hitpoints, 0f);
      if (hp < lowestLife) {
        selected = id;
        lowestLife = hp;
      }
    }
    return selected != Engine.INVALID_ENTITY
        && rollAiChance(params[PARAM_LOWEST_LIFE_TARGET_CHANCE]) ? selected : fallback;
  }

  private void pauseOrMove(int targetId, float distance) {
    if (rollAiPercentage() >= 65) {
      idle(10);
    } else if (rollAiPercentage() < 65 || distance > 5f) {
      if (!orbit(targetId, 4f, 0)) idle(10);
    } else {
      approach(targetId, 0);
    }
  }

  private boolean escape(int targetId) {
    Vector2 source = mPosition.get(entityId).position;
    Vector2 target = mPosition.get(targetId).position;
    destination.set(Math.round(source.x) + 8 * Integer.signum(Math.round(source.x) - Math.round(target.x)),
        Math.round(source.y) + 8 * Integer.signum(Math.round(source.y) - Math.round(target.y)));
    return walkToPoint(destination, 50, Engine.INVALID_ENTITY);
  }

  private boolean orbit(int targetId, float radius, int bonus) {
    // Consume native path-type RNG, but project path types 5/6 onto a side-step.
    boolean clockwise = (rollAiRandomNumber() & 0xFF) < 128;
    Vector2 target = mPosition.get(targetId).position;
    destination.set(mPosition.get(entityId).position).sub(target);
    if (destination.isZero(0.0001f)) destination.set(1f, 0f);
    destination.rotate90(clockwise ? 1 : -1).nor().scl(radius).add(target);
    return walkToPoint(destination, bonus, targetId);
  }

  private void wander(int radius, int bonus) {
    boolean xEdge = (rollAiRandomNumber() & 1) != 0;
    int offset = (int) (rollAiRandomNumber() % radius);
    int dx = xEdge ? radius : offset;
    int dy = xEdge ? offset : radius;
    if ((rollAiRandomNumber() & 1) != 0) dx = -dx;
    if ((rollAiRandomNumber() & 1) != 0) dy = -dy;
    Vector2 source = mPosition.get(entityId).position;
    destination.set(Math.round(source.x) + dx, Math.round(source.y) + dy);
    if (!walkToPoint(destination, bonus, Engine.INVALID_ENTITY)) idle(10);
  }

  private int lifePercentage() {
    int hp = (int) statValue(entityId, Stat.hitpoints, 100f);
    int maxHp = (int) statValue(entityId, Stat.maxhp, 100f);
    return maxHp == 0 ? 0 : (int) (100L * hp / maxHp);
  }

  private float statValue(int id, short stat, float fallback) {
    if (!mAttributesWrapper.has(id)) return fallback;
    Attributes attrs = mAttributesWrapper.get(id).attrs;
    return attrs == null ? fallback : attrs.getValue(stat, fallback);
  }

  private boolean hasMissileBarrier(int targetId) {
    MapWrapper map = mMapWrapper.has(entityId) ? mMapWrapper.get(entityId) : null;
    if (map == null || map.map == null || map.zone == null) return false;
    ray.set(mPosition.get(entityId).position, mPosition.get(targetId).position);
    return map.map.castRay(map.zone, ray, DT1.Tile.FLAG_BLOCK_JUMP, 0, collision, false);
  }
}
