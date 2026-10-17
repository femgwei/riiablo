package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.NativeAiCommand;
import org.junit.jupiter.api.Test;

class ScarabLeaderCommandTest extends RiiabloTest {
  @Test void explicitLeaderCommandsOnlyOwnedMinionsAndExecutesOwnCommand() {
    for (boolean selfOwner : new boolean[] {false, true}) {
      try (Fixture f = new Fixture("scarab1", 0, 1)) {
        Scarab ai = (Scarab) f.ai;
        if (selfOwner) ai.monster.minionOwnerId = f.actor;
        else ai.monster.nativePackLeader = true;
        ai.params[0] = ai.params[3] = 0;
        ai.params[4] = 100;
        int owned = member(f, f.actor), unrelated = member(f, -1), otherPack = member(f, 9999);
        NativeAiCommand queue = f.world.getMapper(NativeAiCommand.class).create(owned);
        queue.set(f.actor, NativeAiCommand.MOVE, f.target);
        f.world.process();
        Riiablo.gameSeed = 12;
        ai.update(0.04f);
        assertEquals("JAB", ai.getState());
        assertEquals(f.target, f.casting().targetId);
        assertFalse(f.world.getMapper(NativeAiCommand.class).has(f.actor));
        assertEquals(NativeAiCommand.ATTACK, queue.command);
        assertEquals(f.actor, queue.ownerId);
        assertTrue(queue.pop());
        assertEquals(NativeAiCommand.MOVE, queue.command);
        assertFalse(f.world.getMapper(NativeAiCommand.class).has(unrelated));
        assertFalse(f.world.getMapper(NativeAiCommand.class).has(otherPack));
        assertOneDraw(f, ai);
      }
    }
  }
  @Test void distanceTwentyDoesNotGenerateCommand() { verifyNoGeneration(true, 20, false); }
  @Test void unownedMonsterDoesNotGenerateCommand() { verifyNoGeneration(false, 1, false); }
  @Test void existingCommandBlocksLeaderGeneration() { verifyNoGeneration(true, 1, true); }

  private void verifyNoGeneration(boolean leader, int distance, boolean existing) {
    try (Fixture f = new Fixture("scarab1", 0, distance)) {
      Scarab ai = (Scarab) f.ai;
      ai.monster.nativePackLeader = leader;
      ai.params[0] = 0;
      ai.params[4] = 100;
      int owned = member(f, f.actor);
      if (existing) f.world.getMapper(NativeAiCommand.class).create(f.actor)
          .set(f.actor, NativeAiCommand.MOVE, f.target);
      f.world.process();
      Riiablo.gameSeed = 12;
      ai.update(0.04f);
      assertFalse(f.world.getMapper(NativeAiCommand.class).has(owned));
      assertNull(f.casting());
      if (existing) assertEquals(NativeAiCommand.MOVE,
          f.world.getMapper(NativeAiCommand.class).get(f.actor).command);
      assertOneDraw(f, ai); // Ordinary attack chance or initial orbit direction only.
    }
  }
  private int member(Fixture f, int owner) {
    int id = f.world.create();
    f.world.getMapper(Monster.class).create(id).set(
        ((Scarab) f.ai).monster.monstats, ((Scarab) f.ai).monster.monstats2).setMinionOwner(owner);
    return id;
  }
  private void assertOneDraw(Fixture f, Scarab ai) {
    D2Seed seed = new D2Seed(12 ^ (f.actor * 0x45D9F3B), 666);
    Seed.rollRandomNumber(seed);
    assertEquals(Seed.rollRandomNumber(seed) & 0xffffffffL, ai.rollAiRandomNumber());
  }
}
