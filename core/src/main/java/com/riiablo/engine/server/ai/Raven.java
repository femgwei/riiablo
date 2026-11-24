package com.riiablo.engine.server.ai;

import com.riiablo.engine.Engine;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.skill.SkillId;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.codec.Animation;

/** Native Raven/Fn107 pet behavior with a finite peck budget. */
public final class Raven extends DruidPet {
  private int pecks = -1;
  private long attackDueFrame, nextDecisionFrame;
  private double elapsedFrames;
  private boolean clockwise, moving;
  public Raven(int entityId) { super(entityId); }

  @Override void writeRuntime(java.io.DataOutputStream out) throws java.io.IOException {
    super.writeRuntime(out);
    out.writeInt(pecks);
    out.writeLong(attackDueFrame); out.writeLong(nextDecisionFrame); out.writeDouble(elapsedFrames);
    out.writeBoolean(clockwise); out.writeBoolean(moving);
  }

  @Override void readRuntime(java.io.DataInputStream in, boolean apply) throws java.io.IOException {
    super.readRuntime(in, apply);
    int savedPecks = in.readInt();
    long attackDue = in.readLong(), decisionDue = in.readLong(); double elapsed = in.readDouble();
    boolean side = com.riiablo.engine.replay.CheckpointIO.readBoolean(in);
    boolean inMotion = com.riiablo.engine.replay.CheckpointIO.readBoolean(in);
    if (savedPecks < -1 || attackDue < 0 || decisionDue < 0 || !Double.isFinite(elapsed) || elapsed < 0)
      throw new IllegalArgumentException("Raven runtime");
    if (apply) { pecks = savedPecks; attackDueFrame = attackDue; nextDecisionFrame = decisionDue;
      elapsedFrames = elapsed; clockwise = side; moving = inMotion; }
  }

  @Override public void initialize() {
    super.initialize();
    // createMonster initializes AI before createSummonedPet binds its owner.
    // Native Raven resolves the budget on its first think, not allocation.
    pecks = -1;
    elapsedFrames = 0;
    attackDueFrame = Math.max(0, params[2] + 1);
    nextDecisionFrame = frame() + 25;
    clockwise = (rollAiRandomNumber() & 1) != 0;
    moving = false;
  }

  private long frame() {
    com.riiablo.engine.server.AuthoritativeSimulation simulation =
        com.riiablo.engine.server.AuthoritativeSimulation.current();
    return simulation == null ? (long) (elapsedFrames + .00001) : simulation.tickNumber();
  }

  @Override public void onActionReturnedToNeutral() {
    if (nextDecisionFrame <= frame()) nextDecisionFrame = frame() + thinkDelay();
  }

  private int thinkDelay() {
    int delay = Math.round(SLEEP / Animation.FRAME_DURATION);
    return delay > 0 ? delay : 15;
  }

  private int ownerDistance(int owner) {
    Vector2 a = mPosition.get(entityId).position, b = mPosition.get(owner).position;
    int size = mSize.has(entityId) ? mSize.get(entityId).size : 0;
    int x = Math.abs(Math.abs((int) a.x - (int) b.x) - size);
    int y = Math.abs(Math.abs((int) a.y - (int) b.y) - size);
    return Math.max(x, y) + Math.min(x, y) / 2;
  }

  private boolean circleApproach(int owner, int radius) {
    Vector2 a = mPosition.get(entityId).position, b = mPosition.get(owner).position;
    int size = (mSize.has(entityId) ? mSize.get(entityId).size / 2 : 0)
        + (mSize.has(owner) ? mSize.get(owner).size / 2 : 0);
    int x = Math.max(0, Math.abs((int) a.x - (int) b.x) - size);
    int y = Math.max(0, Math.abs((int) a.y - (int) b.y) - size);
    int distance = Math.max(x,y) + Math.min(x,y) / 2;
    if (distance == 0) return false;
    Vector2 destination = new Vector2((int) b.x + radius * ((int) a.x - (int) b.x) / distance,
        (int) b.y + radius * ((int) a.y - (int) b.y) / distance);
    state = "CIRCLE_APPROACH";
    moving = walkTo(destination, Engine.INVALID_ENTITY);
    return moving;
  }

  private void circleOwner(int owner) {
    int average = (params[0] + params[1]) / 2;
    int distance = ownerDistance(owner);
    if (distance < params[1] || distance > params[0]) {
      if (!circleApproach(owner, average)) { state = "IDLE"; stopMovement(); }
      return;
    }
    state = "CIRCLE";
    moving = pathfinder.findNativeOrbit(entityId, owner, 4, clockwise);
    if (!moving) {
      clockwise = !clockwise;
      moving = pathfinder.findNativeOrbit(entityId, owner, 4, clockwise);
    }
    if (moving) {
      mRunning.remove(entityId);
      if (mVelocity.has(entityId)) mVelocity.get(entityId).clearModeSpeedBonus();
    } else if (!circleApproach(owner, average)) { state = "IDLE"; stopMovement(); }
  }

  private void initializeBudget() {
    SummonedPet pet = mSummonedPet.has(entityId) ? mSummonedPet.get(entityId) : null;
    int level = pet != null ? Math.max(1, pet.skillLevel)
        : monster != null ? Math.max(1, resolveMonsterSkillLevel(0)) : 1;
    Skills.Entry skill = files().skills.get(SkillId.RAVEN);
    pecks = skill.Param[4] + (level - 1) * skill.Param[5];
  }

  @Override public void update(float delta) {
    elapsedFrames += Math.max(0f, delta) / (double) Animation.FRAME_DURATION;
    if (monster == null || !mPosition.has(entityId) || !alive()) return;
    if ("DEAD".equals(state)) return;
    if (ownerId() < 0 || !mPosition.has(ownerId())) { stopMovement(); state = "IDLE_NO_OWNER"; return; }
    // The final A1 must finish before exhaustion starts the DT -> DD sequence.
    if (mCasting.has(entityId) || mSequence.has(entityId)) return;
    if (mPathfind.has(entityId)) { moving = true; return; }
    if (moving) { moving = false; nextDecisionFrame = Math.min(nextDecisionFrame, frame()); }
    if (frame() < nextDecisionFrame) return;
    nextDecisionFrame = frame() + thinkDelay();
    if (pecks < 0) initializeBudget();
    if (pecks <= 0) {
      if (mAttributesWrapper.has(entityId) && mAttributesWrapper.get(entityId).attrs != null
          && mAttributesWrapper.get(entityId).attrs.get(Stat.hitpoints) != null)
        mAttributesWrapper.get(entityId).attrs.get(Stat.hitpoints).set(0);
      kill();
      if (events != null) events.dispatch(DeathEvent.obtain(entityId, entityId));
      return;
    }
    int owner = ownerId();
    if (ownerDistance(owner) > 28) {
      state = "REGROUP";
      int bonus = monster.monstats.Velocity <= 0 ? 100
          : Math.min(100, 100 * monster.monstats.Run / monster.monstats.Velocity - 100);
      moving = walkTo(mPosition.get(owner).position, bonus, owner);
      return;
    }
    float[] distance = { Float.MAX_VALUE };
    targetId = findSummonTarget(targetId, distance, Math.max(0, params[4]));
    // Cooldown is an absolute game deadline and keeps advancing during A1.
    // The random attack gate is consumed before testing target distance.
    if (targetId >= 0 && mPosition.has(targetId) && attackDueFrame < frame()
        && rollAiChance(params[3]) && distance[0] < params[4]) {
      if (ordinaryAttack(targetId)) {
        pecks--; attackDueFrame = frame() + 10L * Math.max(0, params[2]); return;
      }
      state = "APPROACH";
      moving = pathfinder.findNativeChase(entityId, targetId);
      if (moving) {
        mRunning.remove(entityId);
        if (mVelocity.has(entityId)) mVelocity.get(entityId).clearModeSpeedBonus();
      }
      return;
    }
    circleOwner(owner);
  }

  @Override public void kill() {
    if ("DEAD".equals(state)) return;
    state = "DEAD";
    targetId = Engine.INVALID_ENTITY;
    stopMovement();
    mCasting.remove(entityId);
    mSequence.create(entityId).sequence(Engine.Monster.MODE_DT, Engine.Monster.MODE_DD);
    playSound(monsound + "_death_1");
  }
}
