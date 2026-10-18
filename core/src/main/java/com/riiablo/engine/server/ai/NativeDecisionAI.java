package com.riiablo.engine.server.ai;

import com.riiablo.codec.Animation;
import com.riiablo.engine.Engine;
import com.riiablo.skill.SkillCodes;

/** Shared action lifecycle for directly ported AITHINK decision functions. */
abstract class NativeDecisionAI extends AI {
  // AITACTICS_Idle (D2Game.0x6FCD0110) schedules an absolute game frame.
  // Float countdowns can leave a positive remainder on the requested frame,
  // and stop advancing altogether while AIStepper leaves a room dormant.
  private long nextThinkFrame;
  private String state = "IDLE";
  private boolean dead;
  private double elapsedFrames;
  private long decisionCount;
  private int lastDecisionTarget = Engine.INVALID_ENTITY;
  private String lastApproach = "NOT_REQUESTED";

  @Override
  public String getDecisionDiagnostics() {
    return "decisions=" + decisionCount + " target=" + lastDecisionTarget
        + " approach=" + lastApproach + " frame=" + nativeFrame()
        + " due=" + nextThinkFrame + " componentActionLock="
        + (mCasting.has(entityId) || mSequence.has(entityId));
  }

  NativeDecisionAI(int entityId) {
    super(entityId);
  }

  @Override
  public final void update(float delta) {
    elapsedFrames += Math.max(0f, delta) / (double) Animation.FRAME_DURATION;
    if (monster == null || !mPosition.has(entityId)) return;
    if (dead) { thinkWhileDead(delta); return; }
    beforeThink(delta);
    if (dead) return;
    if (actionBlocksDecision() || nativeFrame() < nextThinkFrame) return;
    nextThinkFrame = Float.isInfinite(SLEEP) ? Long.MAX_VALUE
        : nativeFrame() + Math.max(1, Math.round(SLEEP / Animation.FRAME_DURATION));
    if (!beforeDecision()) return;
    float[] distance = {Float.MAX_VALUE};
    int targetId = findNearestTargetWithAidist(distance);
    decisionCount++;
    lastDecisionTarget = targetId;
    if (targetId == Engine.INVALID_ENTITY) {
      thinkWithoutTarget();
      return;
    }
    think(targetId, distance[0], isInNativeMeleeRange(targetId));
  }

  /** Hook for native AIs with timers that advance while an action is active. */
  protected void beforeThink(float delta) {}
  protected void thinkWhileDead(float delta) {}

  protected final long nativeFrame() {
    com.riiablo.engine.server.AuthoritativeSimulation simulation =
        com.riiablo.engine.server.AuthoritativeSimulation.current();
    return simulation != null ? simulation.tickNumber() : (long) (elapsedFrames + .00001);
  }

  /** Some native skills hold their final sequence point while AI timers keep running. */
  protected boolean actionBlocksDecision() {
    return mCasting.has(entityId) || mSequence.has(entityId);
  }

  /** Lifecycle checks that run at decision cadence, even without an enemy. */
  protected boolean beforeDecision() { return true; }

  protected void thinkWithoutTarget() { idle(1); }

  protected abstract void think(int targetId, float distance, boolean combat);

  protected final boolean nativeUncontrolled() {
    com.riiablo.engine.server.component.NativeAiState s = com.riiablo.Riiablo.engine
        .getMapper(com.riiablo.engine.server.component.NativeAiState.class).get(entityId);
    return s != null && s.isUncontrolled();
  }

  protected final int monsterFootprint(String rowId) {
    com.riiablo.codec.excel.MonStats.Entry row = files().monstats.get(rowId);
    com.riiablo.codec.excel.MonStats2.Entry dimensions = row == null ? null
        : files().monstats2.get(row.MonStatsEx);
    return dimensions == null ? 1 : Math.max(1, dimensions.SizeX);
  }

  protected final boolean nativeNearbyMonster(int id) {
    if (!mMonster.has(id) || !mPosition.has(id)) return false;
    com.riiablo.engine.server.component.MapWrapper source = mMapWrapper.get(entityId);
    com.riiablo.engine.server.component.MapWrapper target = mMapWrapper.get(id);
    if (source == null || source.zone == null) return target == null || target.zone == null;
    if (target == null || target.map != source.map || target.zone != source.zone) return false;
    com.badlogic.gdx.math.Vector2 s = mPosition.get(entityId).position;
    com.badlogic.gdx.math.Vector2 t = mPosition.get(id).position;
    return source.zone.areRoomsAdjacent(s.x, s.y, t.x, t.y);
  }

  protected final int nativeLifePercent(int id) {
    if (!mAttributesWrapper.has(id)) return 0;
    com.riiablo.attributes.Attributes attrs = mAttributesWrapper.get(id).attrs;
    int max = (int) attrs.getValue(com.riiablo.attributes.Stat.maxhp, 0f);
    return max == 0 ? 0 : (int) (100L * (int) attrs.getValue(
        com.riiablo.attributes.Stat.hitpoints, 0f) / max);
  }

  /** Schedules a decision without changing a just-started skill's mode. */
  protected final void delayDecision(int frames) {
    nextThinkFrame = nativeFrame() + Math.max(1, frames);
  }

  protected final void idle(int frames) {
    stopMovement();
    state = "IDLE";
    delayDecision(frames);
  }

  protected final void approach(int targetId, int velocityBonus) {
    approach(targetId, velocityBonus, false);
  }

  protected final void approach(int targetId, int velocityBonus, boolean running) {
    state = "APPROACH";
    boolean moved = running
        ? runTo(mPosition.get(targetId).position, velocityBonus, targetId)
        : walkTo(mPosition.get(targetId).position, velocityBonus, targetId);
    lastApproach = moved ? "ACCEPTED" : "REJECTED";
    if (!moved) idle(10);
  }

  /** Coordinate movement whose caller owns native fallback/retry decisions. */
  protected final boolean walkToPoint(com.badlogic.gdx.math.Vector2 destination,
      int velocityBonus, int targetId) {
    if (!walkTo(destination, velocityBonus, targetId)) return false;
    state = "MOVE";
    return true;
  }

  /** Native path types 5/6 with a unit-seed direction choice and step budget. */
  protected final void circle(int targetId, float radius) {
    state = "CIRCLE";
    if (!pathfinder.findNativeOrbit(entityId, targetId, (int) radius,
        (rollAiRandomNumber() & 255L) < 128L)) idle(10);
  }

  protected final boolean escape(int targetId, int distance, int bonus) {
    com.badlogic.gdx.math.Vector2 source = mPosition.get(entityId).position;
    com.badlogic.gdx.math.Vector2 target = mPosition.get(targetId).position;
    return walkToPoint(new com.badlogic.gdx.math.Vector2(
        (int) source.x + Integer.compare((int) source.x, (int) target.x) * distance,
        (int) source.y + Integer.compare((int) source.y, (int) target.y) * distance),
        bonus, Engine.INVALID_ENTITY);
  }

  protected final boolean walkClose(int radius, int bonus) {
    int x, y;
    boolean axis = (rollAiRandomNumber() & 1L) != 0;
    int random = radius > 0 ? (int) (rollAiRandomNumber() % radius) : 0;
    x = axis ? radius : random;
    y = axis ? random : radius;
    if ((rollAiRandomNumber() & 1L) != 0) x = -x;
    if ((rollAiRandomNumber() & 1L) != 0) y = -y;
    com.badlogic.gdx.math.Vector2 source = mPosition.get(entityId).position;
    return walkToPoint(new com.badlogic.gdx.math.Vector2((int) source.x + x,
        (int) source.y + y), bonus, Engine.INVALID_ENTITY);
  }

  protected final boolean walkInRadius(int targetId, int maxStep, int desiredDistance,
      int bonus) {
    com.badlogic.gdx.math.Vector2 s = mPosition.get(entityId).position;
    com.badlogic.gdx.math.Vector2 t = mPosition.get(targetId).position;
    // AITACTICS_MoveInRadiusToTarget uses AIUTIL's full source-size
    // coordinate distance, not D2Common's combat-distance lookup table.
    // Use the same host cell projection as the live melee predicate. In
    // negative map coordinates, truncation can report zero approach distance
    // while the rounded combat cells are still outside attack range.
    int sx = Math.round(s.x), sy = Math.round(s.y);
    int tx = Math.round(t.x), ty = Math.round(t.y);
    int size = mSize.has(entityId) ? mSize.get(entityId).size : 1;
    int dx = Math.abs(tx - sx), dy = Math.abs(ty - sy);
    int xSizeDistance = Math.abs(dx - size), ySizeDistance = Math.abs(dy - size);
    int distance = (Math.min(xSizeDistance, ySizeDistance)
        + 2 * Math.max(xSizeDistance, ySizeDistance)) / 2;
    int sign = Integer.compare(distance, desiredDistance);
    int diff = Math.min(Math.abs(distance - desiredDistance), maxStep);
    int sum = Math.max(dx + dy, diff), x = 0, y = 0;
    if (sum > 0) {
      x = diff * dx / sum; y = diff * dy / sum;
      while (x + y < diff) { x++; y++; }
    }
    return walkToPoint(new com.badlogic.gdx.math.Vector2(
        sx + sign * x * Integer.compare(tx, sx),
        sy + sign * y * Integer.compare(ty, sy)),
        bonus, Engine.INVALID_ENTITY);
  }

  protected final boolean chargeLineClear(int targetId) {
    com.riiablo.engine.server.component.MapWrapper source = mMapWrapper.get(entityId);
    if (source == null || source.map == null || source.zone == null) return false;
    com.badlogic.gdx.math.Vector2 s = mPosition.get(entityId).position;
    com.badlogic.gdx.math.Vector2 t = mPosition.get(targetId).position;
    int distance = com.riiablo.engine.server.combat.NativeMeleeDistance.between(
        (int) s.x, (int) s.y, mSize.has(entityId) ? mSize.get(entityId).size : 1,
        (int) t.x, (int) t.y, mSize.has(targetId) ? mSize.get(targetId).size : 1);
    int offset = distance < 3 ? 2 : distance < 11 ? 3 : distance < 25 ? 4 : 3;
    int dx = Integer.compare((int) s.x, (int) t.x) * offset;
    int dy = Integer.compare((int) s.y, (int) t.y) * offset;
    com.badlogic.gdx.ai.utils.Ray<com.badlogic.gdx.math.Vector2> ray =
        new com.badlogic.gdx.ai.utils.Ray<>(s.cpy(), t.cpy());
    com.badlogic.gdx.ai.utils.Collision<com.badlogic.gdx.math.Vector2> hit =
        new com.badlogic.gdx.ai.utils.Collision<>(new com.badlogic.gdx.math.Vector2(),
            new com.badlogic.gdx.math.Vector2());
    for (int i = 0; i < 3; i++) {
      ray.end.set(t.x + (i == 1 ? -dy : i == 2 ? dy : 0),
          t.y + (i == 1 ? dx : i == 2 ? -dx : 0));
      if (!source.map.castRay(source.zone, ray, 0xC01, 0, hit, false)) return true;
    }
    return false;
  }

  protected final void attack(int targetId) {
    attack(targetId, Engine.Monster.MODE_A1);
  }

  /** Starts the native ordinary attack action in the requested monster mode. */
  protected final void attack(int targetId, int attackMode) {
    attackAt(targetId, attackMode, mPosition.get(targetId).position);
  }

  protected final void attackAt(int targetId, int attackMode,
      com.badlogic.gdx.math.Vector2 target) {
    invalidateNativePathCache();
    stopMovement();
    if (targetId != Engine.INVALID_ENTITY) lookAt(targetId);
    state = "ATTACK";
    mSequence.create(entityId).sequence((byte) attackMode, Engine.Monster.MODE_NU);
    mCasting.create(entityId).set(SkillCodes.attack, targetId, target);
    com.riiablo.audio.MonsterAudio.play(entityId, monsound + "_attack_1", true);
    nextThinkFrame = 0;
  }

  protected final boolean hasSkill(int slot) {
    if (monster == null || monster.monstats == null) return false;
    String name;
    switch (slot) {
      case 0: name = monster.monstats.Skill1; break;
      case 1: name = monster.monstats.Skill2; break;
      case 2: name = monster.monstats.Skill3; break;
      case 3: name = monster.monstats.Skill4; break;
      case 4: name = monster.monstats.Skill5; break;
      case 5: name = monster.monstats.Skill6; break;
      case 6: name = monster.monstats.Skill7; break;
      case 7: name = monster.monstats.Skill8; break;
      default: return false;
    }
    return name != null && !name.isEmpty();
  }

  protected final void cast(int slot, int targetId) {
    castAt(slot, targetId, mPosition.get(targetId).position);
  }

  protected final boolean castAt(int slot, int targetId, com.badlogic.gdx.math.Vector2 target) {
    return castAt(slot, targetId, target, Engine.INVALID_MODE);
  }

  /** Known native MonSeq backing mode, without losing the shared cast lifecycle. */
  protected final boolean castAt(int slot, int targetId, com.badlogic.gdx.math.Vector2 target,
      int sequenceMode) {
    if (useMonsterSkill(slot, targetId, target, sequenceMode)) {
      state = "CAST";
      nextThinkFrame = 0;
      return true;
    } else {
      // Native AITACTICS_UseSkill failure waits ten frames, not a basic attack.
      idle(10);
      return false;
    }
  }

  protected final boolean castFixed(int skillId, int mode, int targetId,
      com.badlogic.gdx.math.Vector2 target) {
    if (useFixedMonsterSkill(skillId, mode, targetId, target)) {
      state = "CAST";
      nextThinkFrame = 0;
      return true;
    }
    idle(10);
    return false;
  }

  /** Starts a native mode-only action for MonStats rows without a Skills.txt entry. */
  protected final void startMode(int mode, int targetId) {
    invalidateNativePathCache();
    stopMovement();
    if (targetId != Engine.INVALID_ENTITY && mPosition.has(targetId)) lookAt(targetId);
    state = "CAST";
    mSequence.create(entityId).sequence((byte) mode, Engine.Monster.MODE_NU);
    nextThinkFrame = 0;
  }

  @Override
  public void kill() {
    if (dead) return;
    dead = true;
    invalidateNativePathCache();
    state = "DEAD";
    stopMovement();
    mCasting.remove(entityId);
    mSequence.create(entityId).sequence(Engine.Monster.MODE_DT, Engine.Monster.MODE_DD);
    com.riiablo.audio.MonsterAudio.play(entityId, monsound + "_death_1", true);
  }

  @Override
  public void onOwnerWarp() {
    super.onOwnerWarp();
    nextThinkFrame = 0;
    if (!dead) state = "IDLE";
  }

  @Override
  public String getState() {
    return state;
  }
}
