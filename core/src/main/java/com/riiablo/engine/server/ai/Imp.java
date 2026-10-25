package com.riiablo.engine.server.ai;

import com.riiablo.Riiablo;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Monster;

/** AITHINK_Fn122_Imp. Its parameters deliberately come from imp1..imp4. */
public class Imp extends NativeDecisionAI {
  private int previousTarget = Engine.INVALID_ENTITY;
  private int mountedSkill = -1;
  @Override void writeRuntime(java.io.DataOutputStream out) throws java.io.IOException {
    super.writeRuntime(out);
    out.writeInt(previousTarget);
    out.writeInt(mountedSkill);
  }
  @Override void readRuntime(java.io.DataInputStream in, boolean apply) throws java.io.IOException {
    super.readRuntime(in, apply);
    int savedPreviousTarget = in.readInt();
    int savedMountedSkill = in.readInt();
    if (apply) {
      previousTarget = savedPreviousTarget;
      mountedSkill = savedMountedSkill;
    }
  }

  public Imp(int entityId) { super(entityId); }

  private boolean castMounted(int slot, int targetId, Vector2 target) {
    MonStats.Entry row = files().monstats.get("imp1");
    if (row == null) return false;
    String name = slot == 0 ? row.Skill1 : slot == 2 ? row.Skill3 : row.Skill5;
    String mode = slot == 0 ? row.Sk1mode : slot == 2 ? row.Sk3mode : row.Sk5mode;
    com.riiablo.codec.excel.Skills.Entry skill = files().skills.get(name);
    if (skill == null) return false;
    int animation = files().MonMode.index(mode);
    return castFixed(skill.Id, animation >= 0 ? animation : Engine.Monster.MODE_S1,
        targetId, target);
  }

  @Override protected void beforeThink(float delta) {
    int owner = monster.nativeOwnerId;
    if (owner < 0) return;
    if (!mPosition.has(owner) || !mMonster.has(owner)
        || monster.nativeOwnerIdentity != null
            && !monster.nativeOwnerIdentity.isCurrent(Riiablo.engine, owner)) {
      monster.nativeOwnerId = -1;
      monster.nativeOwnerIdentity = null;
      monster.nativeRequestedMountId = -1;
      monster.nativeRequestedMountIdentity = null;
      com.riiablo.engine.server.component.UnitStates states = mUnitStates.get(entityId);
      if (states != null && states.stateList != null)
        states.stateList.removeState(com.riiablo.engine.server.state.StateId.ATTACHED);
      mNativeUnitFlags.create(entityId).set(
          com.riiablo.engine.server.component.NativeUnitFlags.MONSTER_TARGET);
      com.riiablo.engine.server.component.Box2DBody physics = Riiablo.engine.getMapper(
          com.riiablo.engine.server.component.Box2DBody.class).get(entityId);
      if (physics != null && physics.body != null) physics.body.setActive(true);
      mountedSkill = -1;
      return;
    }
    stopMovement();
    mPosition.get(entityId).invalidateNativePrecision();
    mPosition.get(entityId).position.set(mPosition.get(owner).position);
    com.riiablo.engine.server.component.MapWrapper mapping = mMapWrapper.get(owner);
    if (mapping != null) mMapWrapper.create(entityId).set(mapping.map, mapping.zone);
    com.riiablo.engine.server.component.Box2DBody physics = Riiablo.engine.getMapper(
        com.riiablo.engine.server.component.Box2DBody.class).get(entityId);
    if (physics != null && physics.body != null) {
      physics.body.setActive(false);
      physics.body.setTransform(mPosition.get(entityId).position, physics.body.getAngle());
    }
  }

  @Override protected void thinkWithoutTarget() {
    if (monster.nativeOwnerId >= 0 && nativeLifePercent(monster.nativeOwnerId) <= 0) {
      teleportInRange();
      return;
    }
    idle(10);
  }

  private int parameter(String rowId, int slot) {
    MonStats.Entry row = files().monstats.get(rowId);
    if (row == null) return 0;
    int[][] columns = {row.aip1, row.aip2, row.aip3, row.aip4};
    return difficultyValue(columns[slot], resolveAiDifficulty());
  }

  private void teleportInRange() {
    int range = parameter("imp1", 1);
    Vector2 source = mPosition.get(entityId).position;
    int x = range > 0 ? (int) (rollAiRandomNumber() % (2L * range)) : 0;
    int y = range > 0 ? (int) (rollAiRandomNumber() % (2L * range)) : 0;
    Vector2 target = new Vector2((int) source.x + x - range, (int) source.y + y - range);
    if (monster.nativeOwnerId >= 0) castMounted(0, Engine.INVALID_ENTITY, target);
    else castAt(0, Engine.INVALID_ENTITY, target);
  }

  @Override protected void think(int targetId, float distance, boolean combat) {
    if (monster.nativeOwnerId >= 0) {
      if (mountedSkill < 0) {
        int first = ((int) (rollAiRandomNumber() & 1L) + 1) % 2;
        int[] choices = {2, 4};
        mountedSkill = hasSkill(choices[first]) ? choices[first]
            : hasSkill(choices[1 - first]) ? choices[1 - first] : 0;
      }
      int ownerId = monster.nativeOwnerId;
      Monster owner = mMonster.get(ownerId);
      if (distance > 24 || owner == null || nativeLifePercent(ownerId) <= 0
          || monster.nativeOwnerIdentity != null && !monster.nativeOwnerIdentity.isCurrent(Riiablo.engine, ownerId)
          || owner.monstats.Align != 0 || owner.converted || mountedSkill == 0) {
        teleportInRange(); monster.nativeRequestedMountId = -1; return;
      }
      if (distance < parameter("imp2", 1) && rollAiChance(parameter("imp2", 2))) {
        if (castMounted(mountedSkill, targetId, mPosition.get(targetId).position)) return;
      } else if (rollAiPercentage() < 50) {
        attack(targetId, Engine.Monster.MODE_S1); return;
      }
      idle(20); return;
    }
    mountedSkill = -1;
    int mountId = monster.nativeRequestedMountId;
    if (mountId >= 0 && monster.monstats.Align == 0 && !monster.converted) {
      Monster mount = mMonster.get(mountId);
      if (mount == null || !mPosition.has(mountId) || nativeLifePercent(mountId) <= 0
          || monster.nativeRequestedMountIdentity != null
              && !monster.nativeRequestedMountIdentity.isCurrent(Riiablo.engine, mountId)
          || mount.nativeOwnerId >= 0 || mount.monstats.Align != 0 || mount.converted) {
        monster.nativeRequestedMountId = -1;
      } else {
        int range = parameter("imp2", 0);
        if (mPosition.get(entityId).position.dst2(mPosition.get(mountId).position)
            > range * range) { approach(mountId, 0); return; }
        if (hasSkill(0)) { cast(0, mountId); return; }
      }
    }
    if (combat) {
      if (hasSkill(0) && nativeLifePercent(entityId) < parameter("imp1", 0)) {
        teleportInRange(); return;
      }
      if (rollAiChance(parameter("imp3", 1)) && escape(targetId, 5, 0)) return;
    }
    if (hasSkill(0) && rollAiChance(parameter("imp1", 2))) {
      teleportInRange(); return;
    }
    if (distance < parameter("imp3", 0) && rollAiChance(parameter("imp3", 1))) {
      escape(targetId, 5, 0); return;
    }
    if (hasSkill(3)) {
      float[] selectedDistance = {Float.MAX_VALUE};
      int selected = findTargetWithContinuity(previousTarget, selectedDistance);
      previousTarget = selected;
      if (selected != Engine.INVALID_ENTITY) {
        if (selectedDistance[0] < parameter("imp3", 2)) {
          if (rollAiChance(parameter("imp3", 3))) { cast(3, selected); return; }
        } else if (selectedDistance[0] < parameter("imp4", 2)
            && rollAiChance(parameter("imp4", 3))) { cast(3, selected); return; }
      }
    }
    if (rollAiPercentage() < 33) {
      if (!pathfinder.findNativePath(entityId, targetId, 4, 0)) idle(10);
    } else if (rollAiPercentage() < 20) walkClose(8, 0);
    else idle(10);
  }
}
