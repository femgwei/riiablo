package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.ai.AI;
import com.riiablo.engine.server.component.AIWrapper;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.MovementModes;
import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.component.Running;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.Target;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.event.ModeChangeEvent;
import com.riiablo.engine.server.state.StateId;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Verifies the server-only monster death -> corpse lifecycle. */
class ServerMonsterCorpseSystemTest {
  @Test
  void deathDetachesSummonAndRetainedStateNodesWithoutClearingPetOrStatePayload() {
    EventSystem events = new EventSystem();
    World world = new World(new WorldConfigurationBuilder()
        .with(events, new CofManager(), new ServerMonsterCorpseSystem()).build());
    try {
      int id = world.create();
      world.getMapper(Monster.class).create(id).set(new MonStats.Entry(), new MonStats2.Entry());
      SummonedPet pet = world.getMapper(SummonedPet.class).create(id).set(3, "revive", 95, 4, false, 200);
      long quotaOrder = pet.spawnOrder;
      com.riiablo.engine.server.state.StateList states = world.getMapper(UnitStates.class).create(id).init(id).stateList;
      // Basic stat lists survive cleanup; their flags are not target membership.
      states.addState(StateId.CONVERSION, 100, 1, 3).basicStatList = true;
      states.addState(StateId.CONFUSE, 100, 1, 3).basicStatList = true;
      events.dispatch(DeathEvent.obtain(9, id));
      assertEquals(0, pet.nativeTargetOrder());
      assertEquals(0, states.nativeConversionOrder());
      assertEquals(0, states.nativeNode9Order());
      assertTrue(states.hasState(StateId.CONVERSION));
      assertTrue(states.hasState(StateId.CONFUSE));
      assertEquals(quotaOrder, pet.spawnOrder);
      assertEquals(3, pet.ownerId);
      assertEquals(200, pet.durationFrames);
    } finally {
      world.dispose();
    }
  }

  @Test
  void hirelingWithoutHostileAiStillEntersNativeDeathSequence() {
    EventSystem events = new EventSystem();
    World world = new World(new WorldConfigurationBuilder()
        .with(events, new CofManager(), new ServerMonsterCorpseSystem())
        .build());
    try {
      int mercenaryId = world.create();
      world.getMapper(Monster.class).create(mercenaryId)
          .set(new MonStats.Entry(), new MonStats2.Entry());
      world.getMapper(Mercenary.class).create(mercenaryId).set(3, 0, 8, 7, 2);

      events.dispatch(DeathEvent.obtain(9, mercenaryId));

      Sequence sequence = world.getMapper(Sequence.class).get(mercenaryId);
      assertNotNull(sequence);
      assertEquals(Engine.Monster.MODE_DT, sequence.mode1);
      assertEquals(Engine.Monster.MODE_DD, sequence.mode2);
      assertEquals(0, world.getMapper(Mercenary.class).get(mercenaryId).nativeTargetOrder());
    } finally {
      world.dispose();
    }
  }

  @Test
  void deathModesDetachHirelingInsertionAndNeutralModeDoesNotRejoinIt() {
    EventSystem events = new EventSystem();
    World world = new World(new WorldConfigurationBuilder()
        .with(events, new CofManager(), new ServerMonsterCorpseSystem()).build());
    try {
      int id = world.create();
      world.getMapper(Monster.class).create(id).set(new MonStats.Entry(), new MonStats2.Entry());
      Mercenary merc = world.getMapper(Mercenary.class).create(id);
      for (byte mode : new byte[] {Engine.Monster.MODE_DT, Engine.Monster.MODE_DD}) {
        merc.set(3, 0, 8, 7, 2);
        assertTrue(merc.nativeTargetOrder() > 0);
        events.dispatch(ModeChangeEvent.obtain(id, mode));
        assertEquals(0, merc.nativeTargetOrder());
        events.dispatch(ModeChangeEvent.obtain(id, Engine.Monster.MODE_NU));
        assertEquals(0, merc.nativeTargetOrder(), "NU is not UpdateSummonAI");
      }
    } finally {
      world.dispose();
    }
  }

  @Test
  void deathEventKillsAiAndDeadModeCreatesUsableCorpse() {
    EventSystem events = new EventSystem();
    World world = new World(new WorldConfigurationBuilder()
        .with(events, new CofManager(), new ServerMonsterCorpseSystem())
        .build());
    try {
      int monsterId = world.create();
      MonStats.Entry stats = new MonStats.Entry();
      stats.Id = "fallen1";
      Monster monster = world.getMapper(Monster.class).create(monsterId)
          .set(stats, new MonStats2.Entry());

      ProbeAI ai = new ProbeAI(monsterId);
      AIWrapper wrapper = world.getMapper(AIWrapper.class).create(monsterId);
      wrapper.ai = ai;
      world.getMapper(Velocity.class).create(monsterId);
      world.getMapper(MovementModes.class).create(monsterId);
      world.getMapper(Pathfind.class).create(monsterId);
      world.getMapper(Casting.class).create(monsterId);
      world.getMapper(Running.class).create(monsterId);
      world.getMapper(Target.class).create(monsterId);
      world.getMapper(Interactable.class).create(monsterId);
      NativeUnitFlags nativeFlags = world.getMapper(NativeUnitFlags.class).create(monsterId)
          .reset().markMonsterResurrection();

      events.dispatch(DeathEvent.obtain(1, monsterId));
      assertTrue(ai.killed, "server death event must transition the monster AI");
      assertFalse(world.getMapper(Velocity.class).has(monsterId),
          "native deadCol=false releases live collision at death, before DD");
      assertFalse(world.getMapper(Pathfind.class).has(monsterId));
      assertFalse(world.getMapper(Casting.class).has(monsterId));

      events.dispatch(ModeChangeEvent.obtain(monsterId, Engine.Monster.MODE_DD));
      assertEquals(NativeUnitFlags.NO_RESURRECTION_REWARD, nativeFlags.flags(),
          "native corpse mode must clear target bits without restoring XP or treasure rewards");
      Corpse corpse = world.getMapper(Corpse.class).get(monsterId);
      assertNotNull(corpse);
      assertTrue(corpse.usable);
      assertEquals(Float.POSITIVE_INFINITY, corpse.timeRemaining,
          "native monster corpses persist while their RoomEx remains active");
      assertFalse(corpse.fading);
      assertEquals(0f, corpse.fadeTime);
      assertFalse(world.getMapper(Velocity.class).has(monsterId));
      assertFalse(world.getMapper(MovementModes.class).has(monsterId));
      assertFalse(world.getMapper(Pathfind.class).has(monsterId));
      assertFalse(world.getMapper(Casting.class).has(monsterId));
      assertFalse(world.getMapper(Running.class).has(monsterId));
      assertFalse(world.getMapper(Target.class).has(monsterId));
      assertFalse(world.getMapper(Interactable.class).has(monsterId));
    } finally {
      world.dispose();
    }
  }

  @Test
  void holyFreezeShatterDeathDoesNotCreateUsableCorpse() {
    EventSystem events = new EventSystem();
    World world = new World(new WorldConfigurationBuilder()
        .with(events, new CofManager(), new ServerMonsterCorpseSystem())
        .build());
    try {
      int monsterId = world.create();
      world.getMapper(Monster.class).create(monsterId)
          .set(new MonStats.Entry(), new MonStats2.Entry());
      world.getMapper(UnitStates.class).create(monsterId).init(monsterId)
          .stateList.addState(StateId.SHATTER, 51, 1, 7);

      events.dispatch(DeathEvent.obtain(7, monsterId));
      events.dispatch(ModeChangeEvent.obtain(monsterId, Engine.Monster.MODE_DD));

      assertFalse(world.getMapper(Corpse.class).has(monsterId),
          "a shattered frozen monster cannot be selected or resurrected as a corpse");
    } finally {
      world.dispose();
    }
  }

  @Test
  void deadCollisionMonsterRetainsFrozenFootprintAndCannotBeConsumed() {
    EventSystem events = new EventSystem();
    World world = new World(new WorldConfigurationBuilder()
        .with(events, new CofManager(), new ServerMonsterCorpseSystem())
        .build());
    try {
      int monsterId = world.create();
      MonStats2.Entry stats2 = new MonStats2.Entry();
      stats2.deadCol = true;
      world.getMapper(Monster.class).create(monsterId)
          .set(new MonStats.Entry(), stats2);
      Velocity velocity = world.getMapper(Velocity.class).create(monsterId);

      events.dispatch(DeathEvent.obtain(7, monsterId));
      assertTrue(world.getMapper(Velocity.class).has(monsterId));
      assertTrue(velocity.stateMovementLocked, "deadCol=true retains a frozen footprint at death");
      events.dispatch(ModeChangeEvent.obtain(monsterId, Engine.Monster.MODE_DD));

      assertTrue(world.getMapper(Corpse.class).has(monsterId));
      assertFalse(world.getMapper(Corpse.class).get(monsterId).usable);
      assertTrue(world.getMapper(Velocity.class).has(monsterId));
      assertTrue(velocity.stateMovementLocked);
      assertTrue(velocity.velocity.isZero());
    } finally {
      world.dispose();
    }
  }

  private static final class ProbeAI extends AI {
    boolean killed;
    ProbeAI(int entityId) { super(entityId); }
    @Override public void kill() { killed = true; }
  }
}
