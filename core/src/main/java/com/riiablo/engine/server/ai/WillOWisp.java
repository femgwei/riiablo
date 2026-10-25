package com.riiablo.engine.server.ai;

import com.artemis.Aspect;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.riiablo.Riiablo;
import com.riiablo.attributes.NativeStatResolver;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.AIWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.state.UnitState;

/** AITHINK_Fn025, including the synchronized four/five-wisp formations. */
public class WillOWisp extends NativeDecisionAI {
  private static final int[][][] POSITIONS = {
    {{-10,-3},{1,-5},{6,0},{4,10}},
    {{-9,-2},{-6,-11},{-2,-5},{6,1}},
    {{0,13},{-12,0},{7,5},{-3,-11}},
    {{-15,-3},{-6,-3},{-4,-5},{-6,-13}},
    {{-12,-2},{-6,2},{-5,5},{1,11}},
    {{-13,-6},{-8,-11},{-7,0},{1,8}},
    {{-5,-8},{-8,1},{1,-6},{6,9}}
  };
  private static final int[][][] TARGETS = {
    {{1,-5},{6,0},{4,10},{-10,-3}},
    {{1,8},{-9,-2},{-5,4},{1,8}},
    {{-12,0},{7,5},{-3,-11},{11,-2}},
    {{7,9},{-15,-3},{-6,-13},{9,7}},
    {{-5,-7},{-5,-7},{9,0},{-12,-2}},
    {{-8,-11},{-2,-6},{-2,-6},{-13,-6}},
    {{-8,1},{1,-6},{6,9},{0,0}}
  };
  private static final int[][] BAPTISM = {{-5,-5},{3,-5},{6,3},{3,6},{-5,3}};
  private static final int[][] BAPTISM_TARGETS = {{6,3},{3,6},{-5,3},{-5,-5},{3,-5}};
  private int phase;
  private int count;
  // Original dwAiParam[2] alternates between participant index and cooldown.
  private long indexOrDeadline;

  @Override void writeRuntime(java.io.DataOutputStream out) throws java.io.IOException {
    super.writeRuntime(out);
    out.writeInt(phase);
    out.writeInt(count);
    out.writeLong(indexOrDeadline);
  }
  @Override void readRuntime(java.io.DataInputStream in, boolean apply) throws java.io.IOException {
    super.readRuntime(in, apply);
    int savedPhase = in.readInt();
    int savedCount = in.readInt();
    long savedIndexOrDeadline = in.readLong();
    if (apply) {
      phase = savedPhase;
      count = savedCount;
      indexOrDeadline = savedIndexOrDeadline;
    }
  }
  public WillOWisp(int entityId) { super(entityId); }

  @Override protected void think(int targetId, float distance, boolean combat) {
    if (nativeFrame() > indexOrDeadline && phase < 4
        && mPosition.get(entityId).position.dst2(mPosition.get(targetId).position) < 1024
        && rollAiRandomNumber() % 1000 <= resolveAiDifficulty() + 2) phase = 4;
    if (phase == 4) { attemptFormation(); return; }
    if (phase >= 5) { formation(targetId); return; }
    if (phase == 1) {
      if (count <= 0 && (combat || rollAiChance(params[0]))) {
        startMode(Engine.Monster.MODE_S1, Engine.INVALID_ENTITY);
        phase = combat ? 3 : 2;
      } else {
        count--;
        move(targetId, 6);
      }
      return;
    }
    if (combat ? phase == 3 || rollAiChance(params[1])
        : phase == 2 || rollAiChance(params[0])) {
      attack(targetId, combat ? Engine.Monster.MODE_A1 : Engine.Monster.MODE_SC);
      phase = 0;
      return;
    }
    phase = 1;
    count = 3;
    move(targetId, 4);
  }

  private void move(int targetId, int radius) {
    if (rollAiChance(params[2])) approach(targetId, 0);
    else if (!walkClose(radius, 0)) idle(10);
  }

  private void attemptFormation() {
    Array<WillOWisp> group = new Array<>();
    IntBag ids = Riiablo.engine.getAspectSubscriptionManager().get(
        Aspect.all(Monster.class, AIWrapper.class)).getEntities();
    for (int i = 0; i < ids.size(); i++) {
      int id = ids.get(i);
      if (!nativeNearbyMonster(id) || nativeLifePercent(id) <= 0
          || !"willowisp1".equals(mMonster.get(id).monstats.BaseId)
          || mPosition.get(entityId).position.dst2(mPosition.get(id).position) > 1024) continue;
      AI ai = Riiablo.engine.getMapper(AIWrapper.class).get(id).ai;
      if (ai instanceof WillOWisp) group.add((WillOWisp) ai);
    }
    if (group.size != 4 && group.size != 5) { phase = 2; idle(8); return; }
    for (int i = 0; i < group.size; i++) {
      WillOWisp participant = group.get(i);
      participant.phase = group.size == 4 ? 6 : 5;
      participant.count = 0;
      participant.indexOrDeadline = i + 1;
      participant.idle((int) (337 - nativeFrame() % 337));
    }
    idle((int) (337 - nativeFrame() % 337));
  }

  private void formation(int targetId) {
    int index = (int) indexOrDeadline - 1;
    boolean baptism = phase == 5;
    if (index < 0 || index >= (baptism ? 5 : 4) || phase > 12) {
      phase = 2; idle(8); return;
    }
    int shape = phase - 6;
    int[] offset = baptism ? BAPTISM[index] : POSITIONS[shape][index];
    Vector2 center = mPosition.get(targetId).position;
    Vector2 desired = new Vector2((int) center.x + offset[0], (int) center.y + offset[1]);
    Vector2 self = mPosition.get(entityId).position;
    int dx = Math.abs((int) self.x - (int) desired.x);
    int dy = Math.abs((int) self.y - (int) desired.y);
    if (Math.max(dx, dy) + Math.min(dx, dy) / 2 > 3) {
      if (rollAiPercentage() < 35) { if (!walkClose(4, 0)) idle(10); }
      else if (!walkToPoint(desired, 0, Engine.INVALID_ENTITY)) idle(10);
      return;
    }
    int remainder = (int) (nativeFrame() % 67);
    if (remainder != 0) { idle(67 - remainder); return; }
    if (count >= 3) {
      count = 0;
      if (baptism || ++phase > 12) {
        phase = 2;
        indexOrDeadline = nativeFrame() + 1800;
      }
      idle((int) (337 - nativeFrame() % 337));
      if (!baptism && mPlayer.has(targetId) && !mCorpse.has(targetId)) {
        UnitStates states = mUnitStates.get(targetId);
        if (states == null || states.stateList == null)
          states = mUnitStates.create(targetId).init(Riiablo.engine, targetId);
        UnitState buff = states.stateList.addAnonymousStatList(24 * 60 * 60 * 20);
        buff.setStatContribution(Stat.item_magicbonus, 0,
            NativeStatResolver.Operation.ADD, 50 * (resolveAiDifficulty() + 1));
      }
      return;
    }
    offset = baptism ? BAPTISM_TARGETS[index] : TARGETS[shape][index];
    count++;
    if (offset[0] == 0 && offset[1] == 0) { idle(67); return; }
    attackAt(Engine.INVALID_ENTITY, Engine.Monster.MODE_SC,
        new Vector2((int) center.x + offset[0], (int) center.y + offset[1]));
  }
}
