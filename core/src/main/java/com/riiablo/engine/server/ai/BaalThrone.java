package com.riiablo.engine.server.ai;

import com.riiablo.Riiablo;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.codec.excel.Skills;
import com.riiablo.codec.excel.States;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.quest.Act5BaalWaveState;
import com.riiablo.engine.server.quest.Act5QuestSystem;

/** Fn134 actions share the quest system's single authoritative wave clock. */
public class BaalThrone extends NativeDecisionAI {
  private int preparedWave = -1;
  private boolean transformed;
  private int castWave = -1;
  @Override void writeRuntime(java.io.DataOutputStream out) throws java.io.IOException {
    super.writeRuntime(out);
    out.writeInt(preparedWave);
    out.writeBoolean(transformed);
    out.writeInt(castWave);
  }
  @Override void readRuntime(java.io.DataInputStream in, boolean apply) throws java.io.IOException {
    super.readRuntime(in, apply);
    int savedPreparedWave = in.readInt();
    boolean savedTransformed = com.riiablo.engine.replay.CheckpointIO.readBoolean(in);
    int savedCastWave = in.readInt();
    if (apply) {
      preparedWave = savedPreparedWave;
      transformed = savedTransformed;
      castWave = savedCastWave;
    }
  }
  public BaalThrone(int entityId) { super(entityId); }

  private boolean statePresent(int id, String name) {
    if (name == null || name.isEmpty()) return false;
    States.Entry state = files().States.get(name);
    UnitStates states = mUnitStates.get(id);
    return state != null && states != null && states.stateList != null
        && states.stateList.hasState(state.id);
  }

  @Override protected boolean beforeDecision() {
    if (transformed) return false;
    Act5QuestSystem quests = Riiablo.engine.getSystem(Act5QuestSystem.class);
    if (quests == null) { idle(10); return false; }
    Act5BaalWaveState waves = quests.baalWaveStateForAi();
    int pending = quests.pendingBaalWaveForAi();
    if (pending >= 0) {
      if (castWave != pending) {
        Skills.Entry spawn = files().skills.get(286);
        if (spawn != null && castFixed(spawn.Id, Engine.Monster.MODE_S3,
            Engine.INVALID_ENTITY, mPosition.get(entityId).position.cpy().add(0, 13)))
          castWave = pending;
        else idle(10);
      } else idle(10);
      return false;
    }
    if (waves.finished()) {
      com.riiablo.codec.excel.MonStats.Entry stairs = files().monstats.get("baalcrabstairs");
      if (stairs != null && factory != null
          && factory.reinitializeMonster(entityId, stairs.hcIdx, Engine.Monster.MODE_NU)) {
        transformed = true;
        UnitStates states = mUnitStates.get(entityId);
        if (states != null && states.stateList != null) {
          com.riiablo.engine.server.state.UnitState changed = states.stateList.addStateLayer(
              com.riiablo.engine.server.state.StateId.CHANGECLASS, 0, 1, entityId, 0);
          changed.setStatContribution(com.riiablo.attributes.Stat.shortparam1, 0,
              com.riiablo.attributes.NativeStatResolver.Operation.ADD, stairs.hcIdx);
        }
      } else idle(5);
      return false;
    }
    if (waves.preparingWave() && preparedWave != waves.wave()) {
      preparedWave = waves.wave();
      Skills.Entry explosion = files().skills.get(285);
      if (explosion != null) castFixed(explosion.Id, Engine.Monster.MODE_S3,
          entityId, mPosition.get(entityId).position);
      else idle(10);
      return false;
    }
    if (waves.delayTicks() > 0) { idle(Math.min(10, waves.delayTicks())); return false; }
    return true;
  }

  @Override protected void thinkWithoutTarget() { idle(10); }
  @Override protected void think(int targetId, float distance, boolean combat) {
    Act5QuestSystem quests = Riiablo.engine.getSystem(Act5QuestSystem.class);
    if (quests != null && !quests.isBaalThroneClearForAi() && hasSkill(0)) {
      Skills.Entry skill = files().skills.get(monster.monstats.Skill1);
      if (skill != null && !statePresent(entityId, skill.aurastate)
          && !statePresent(targetId, skill.auratargetstate) && rollAiChance(params[0])) {
        cast(0, targetId); return;
      }
    }
    idle(10);
  }
}
