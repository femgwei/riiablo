package com.riiablo.engine.server.ai;

import com.riiablo.engine.Engine;
import com.riiablo.codec.excel.MonStats;

/** D2MOO AiThink.cpp AITHINK_Fn066_SandMaggotQueen (0x6FCDC600). */
public class SandMaggotQueen extends NativeDecisionAI {
  static final int PARAM_MAX_SPAWN = 0;
  static final int PARAM_DELAY = 1;

  private int spawned;
  private int spawnPhase;

  @Override void writeRuntime(java.io.DataOutputStream out) throws java.io.IOException {
    super.writeRuntime(out);
    out.writeInt(spawned);
    out.writeInt(spawnPhase);
  }
  @Override void readRuntime(java.io.DataInputStream in, boolean apply) throws java.io.IOException {
    super.readRuntime(in, apply);
    int savedSpawned = in.readInt();
    int savedSpawnPhase = in.readInt();
    if (apply) {
      spawned = savedSpawned;
      spawnPhase = savedSpawnPhase;
    }
  }
  public SandMaggotQueen(int entityId) {
    super(entityId);
  }

  @Override
  protected void think(int targetId, float distance, boolean combat) {
    if (spawnPhase == 2) {
      spawnPhase = 0;
      idle(25 * Math.max(0, params[PARAM_DELAY]));
      return;
    }
    if (spawnPhase == 0) {
      if (spawned >= params[PARAM_MAX_SPAWN]) {
        idle(params[PARAM_DELAY]);
        return;
      }
      startMode(8, Engine.INVALID_ENTITY);
      spawnPhase = 1;
      idle(params[PARAM_DELAY]);
      return;
    }
    // MONSTERS_GetMinionSpawnInfo maps the queen's chain index to sandmaggot.
    MonStats.Entry queen = files().monstats.get(monster.monstats.BaseId);
    MonStats.Entry minion = files().monstats.get("sandmaggot1");
    java.util.HashSet<String> visited = new java.util.HashSet<>();
    while (queen != null && minion != null && queen.hcIdx != monster.monstats.hcIdx
        && visited.add(queen.Id)) {
      queen = files().monstats.get(queen.NextInClass);
      minion = files().monstats.get(minion.NextInClass);
    }
    if (queen != null && queen.hcIdx == monster.monstats.hcIdx && minion != null
        && factory != null && mPosition.has(entityId)) {
      com.badlogic.gdx.math.Vector2 position = mPosition.get(entityId).position;
      int child = factory.createMonsterChild(entityId, minion.hcIdx, Engine.Monster.MODE_S1,
          position.x + 8, position.y, 6, false,
          com.riiablo.engine.server.component.NativeUnitFlags.NO_EXPERIENCE);
      if (child != Engine.INVALID_ENTITY) ++spawned;
    }
    spawnPhase = 2;
    idle(params[PARAM_DELAY]);
  }
}
