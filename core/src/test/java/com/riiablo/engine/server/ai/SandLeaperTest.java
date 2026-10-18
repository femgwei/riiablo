package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.component.*;
import org.junit.jupiter.api.Test;

class SandLeaperTest {
  @Test void nearbyTargetCannotBypassThinkDelayOrOverwriteAnActiveAction() {
    World world = new World(new WorldConfigurationBuilder().build());
    try {
      int id = world.create(), target = world.create();
      world.getMapper(Position.class).create(id).position.set(10, 10);
      world.getMapper(Position.class).create(target).position.set(12, 10);
      world.getMapper(Angle.class).create(id);
      world.getMapper(Monster.class).create(id).monstats = new MonStats.Entry();
      Probe ai = new Probe(id, target);
      ai.bind(world);
      ai.time = .6f;
      for (int i = 0; i < 10; i++) ai.update(.04f);
      assertEquals(0, ai.casts, "even a guaranteed leap must wait for the next AI decision");
      ai.update(.24f);
      assertEquals(1, ai.casts);
      world.getMapper(Casting.class).create(id).set(132, target, new Vector2(12, 10));
      for (int i = 0; i < 100; i++) ai.update(.04f);
      assertEquals(1, ai.casts);
      world.getMapper(Casting.class).remove(id);
      for (int i = 0; i < 10; i++) ai.update(.04f);
      assertEquals(1, ai.casts, "landing must respect the next native think delay");
      ai.update(.24f);
      assertEquals(2, ai.casts);
    } finally { world.dispose(); }
  }

  static class Probe extends SandLeaper {
    final int target;
    int casts;
    Probe(int id, int target) { super(id); this.target = target; }
    void bind(World world) {
      // This decision-only probe does not run pathfinding or skill systems.
      AI base = this;
      base.mPosition = mPosition = world.getMapper(Position.class);
      base.mMonster = mMonster = world.getMapper(Monster.class);
      base.mSequence = mSequence = world.getMapper(Sequence.class);
      mCasting = world.getMapper(Casting.class);
      mLeap = world.getMapper(Leap.class);
      mAngle = world.getMapper(Angle.class);
      mSize = world.getMapper(Size.class);
      monster = mMonster.get(entityId);
      monster.monstats.Skill1 = "Leap";
      params = new int[] {100, 0, 0, 0};
      SLEEP = .6f;
    }
    @Override protected int findNearestTargetWithAidist(float[] distance) {
      distance[0] = 2;
      return target;
    }
    @Override protected boolean useMonsterSkill(int slot, int target, Vector2 position) {
      casts++;
      return true;
    }
  }
}
