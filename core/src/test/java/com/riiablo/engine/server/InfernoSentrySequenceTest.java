package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.MonStats;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.save.CharData;
import java.util.ArrayList;
import java.util.List;
import net.mostlyoriginal.api.event.common.EventSystem;

class InfernoSentrySequenceTest extends RiiabloTest {
  @Test void naturalControllerUsesPointNineDeadlineLastPulseAndFourteenFrameTail() {
    try (Fixture f = new Fixture()) {
      f.tick();
      assertTrue(f.pet.infernoChanneling);
      assertEquals(1, f.pet.shotsFired);
      assertTrue(f.frames.isEmpty());
      for (int i = 0; i < 8; i++) f.tick();
      assertTrue(f.frames.isEmpty());
      f.tick();
      assertEquals(java.util.Arrays.asList(9), f.frames);
      assertEquals(8, f.pet.infernoSequencePoint);
      f.world.getMapper(Position.class).get(f.target).position.y = 12;
      while (f.pet.infernoEndFrame < 0) f.tick();
      assertEquals(java.util.Arrays.asList(9, 12, 15, 18), f.frames);
      assertTrue(f.directions.get(1).y > 0);
      assertEquals(32, f.pet.infernoEndFrame);
      while (f.pet.infernoFrame < 31) f.tick();
      assertTrue(f.world.getMapper(SummonedPet.class).has(f.source));
      assertTrue(f.pet.infernoChanneling);
      f.tick();
      assertFalse(f.pet.infernoChanneling);
      assertFalse(f.states().hasState(StateId.INFERNO));
      f.tick();
      f.world.process();
      assertFalse(f.world.getEntityManager().isActive(f.source));
    }
  }

  @Test void failedCreationAndRemovedTargetUseTailInsteadOfRepeatedRetry() {
    for (boolean remove : new boolean[]{false, true}) {
      try (Fixture f = new Fixture()) {
        f.tick();
        if (remove) f.world.getMapper(Position.class).remove(f.target);
        else f.fail = true;
        while (f.pet.infernoFrame < 9) f.tick();
        assertEquals(23, f.pet.infernoEndFrame);
        assertTrue(f.frames.isEmpty());
        while (f.pet.infernoChanneling) f.tick();
        assertEquals(23, f.pet.infernoFrame);
        assertEquals(1, f.pet.shotsFired);
      }
    }
  }

  @Test void deadTargetStillHasNativeCoordinatesAndNextCastRestartsItsDeadline() {
    try (Fixture f = new Fixture()) {
      f.pet.maxShots = 2;
      f.tick();
      f.world.getMapper(AttributesWrapper.class).get(f.target).attrs.get(Stat.hitpoints).set(0);
      while (f.pet.infernoEndFrame < 0) f.tick();
      assertEquals(java.util.Arrays.asList(9,12,15,18), f.frames);
      while (f.pet.infernoChanneling) f.tick();
      f.world.getMapper(AttributesWrapper.class).get(f.target).attrs.get(Stat.hitpoints).set(10000);
      f.tick();
      assertEquals(2, f.pet.shotsFired);
      assertEquals(0, f.pet.infernoFrame);
      assertEquals(18, f.pet.infernoRemainingFrames);
      for (int i = 0; i < 9; i++) f.tick();
      assertEquals(9, f.frames.get(4));
    }
  }

  @Test void ownerDeathAndControllerDeathCancelQueuedPulses() {
    for (boolean owner : new boolean[]{false,true}) {
      try (Fixture f = new Fixture()) {
        f.tick();
        int entity = owner ? f.pet.ownerId : f.source;
        Attributes attrs = Attributes.obtainStandard();
        attrs.base().put(Stat.hitpoints,0); attrs.reset();
        f.world.getMapper(AttributesWrapper.class).create(entity).attrs = attrs;
        for (int i = 0; i < 12; i++) f.tick();
        assertTrue(f.frames.isEmpty());
        assertFalse(f.pet.infernoChanneling);
      }
    }
  }

  @Test void freezePausesSequenceAndUnsummonCannotEmitQueuedPulse() {
    try (Fixture f = new Fixture()) {
      f.tick();
      f.states().addState(StateId.FREEZE, 100, 1, f.target);
      for (int i = 0; i < 15; i++) f.tick();
      assertEquals(0, f.pet.infernoFrame);
      f.states().removeState(StateId.FREEZE);
      for (int i = 0; i < 9; i++) f.tick();
      assertEquals(1, f.frames.size());
      f.pet.unsummonPending = true;
      for (int i = 0; i < 30; i++) f.tick();
      assertEquals(1, f.frames.size());
    }
  }

  @Test void stateOffAllowsScheduledLastPulseThenTailAndNoTargetPreservesBudget() {
    try (Fixture f = new Fixture()) {
      f.world.getMapper(Position.class).get(f.target).position.set(1000, 1000);
      for (int i = 0; i < 20; i++) f.tick();
      assertEquals(0, f.pet.shotsFired);
      f.world.getMapper(Position.class).get(f.target).position.set(14, 10);
      while (!f.pet.infernoChanneling) f.tick();
      while (f.frames.isEmpty()) f.tick();
      f.states().removeState(StateId.INFERNO);
      while (f.pet.infernoEndFrame < 0) f.tick();
      assertEquals(java.util.Arrays.asList(9, 12), f.frames);
      assertEquals(26, f.pet.infernoEndFrame);
    }
  }

  static final class Fixture implements AutoCloseable {
    final World world;
    final int source, target;
    final SummonedPet pet;
    final List<Integer> frames = new ArrayList<>();
    final List<Vector2> directions = new ArrayList<>();
    boolean fail;
    int ticks;
    Fixture() {
      EntityFactory factory = mock(EntityFactory.class);
      world = new World(new WorldConfigurationBuilder().with(new EventSystem(),
          new AssassinTrapSystem(), factory).build().register("factory", factory)
          .register("map", new com.riiablo.map.Map(1,0)));
      world.setDelta(1f/25f);
      int owner = world.create();
      CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      data.setSkillLevel(262, 3);
      world.getMapper(Player.class).create(owner).data = data;
      source = world.create();
      MonStats.Entry mon = Riiablo.files.monstats.get("infernosentry");
      world.getMapper(Monster.class).create(source).set(mon, Riiablo.files.monstats2.get(mon.MonStatsEx));
      world.getMapper(Position.class).create(source).position.set(10,10);
      world.getMapper(AnimData.class).create(source);
      world.getMapper(UnitStates.class).create(source).init(source);
      pet = world.getMapper(SummonedPet.class).create(source).set(owner,"assassintrap",272,4,false,0);
      pet.maxShots = 1;
      target = world.create();
      world.getMapper(Monster.class).create(target);
      world.getMapper(Position.class).create(target).position.set(14,10);
      Attributes attrs = Attributes.obtainStandard();
      attrs.base().put(Stat.hitpoints,10000); attrs.reset();
      world.getMapper(AttributesWrapper.class).create(target).attrs = attrs;
      when(factory.createMissile(any(Missiles.Entry.class),any(),any(),anyInt(),anyInt())).thenAnswer(call -> {
        if (fail) return -1;
        int id = world.create();
        Missiles.Entry missile = call.getArgument(0);
        world.getMapper(Missile.class).create(id).set(missile,call.getArgument(2),missile.Range).setOwner(source);
        world.getMapper(Velocity.class).create(id);
        frames.add(pet.infernoFrame); directions.add(new Vector2((Vector2)call.getArgument(1)));
        return id;
      });
    }
    com.riiablo.engine.server.state.StateList states() { return world.getMapper(UnitStates.class).get(source).stateList; }
    void tick() { assertTrue(ticks++ < 200); world.process(); }
    public void close() { world.dispose(); }
  }
  @Test void originalSequenceAndMonsterData() {
    int point = 0;
    for (String line : Riiablo.mpqs.resolve("data/global/excel/monseq.txt")
        .readString("windows-1252").split("\\r?\\n")) {
      String[] cell = line.split("\t", -1);
      if (!"seq_infernosentry".equals(cell[0])) continue;
      assertEquals("S2", cell[1]);
      assertEquals(point == 9 ? "1" : "0", cell[2]);
      assertEquals(point == 9 ? "1" : "", cell[4]);
      point++;
    }
    assertEquals(14, point);
    for (MonStats.Entry mon : Riiablo.files.monstats) {
      if (!"mon inferno sentry".equals(mon.Skill1)) continue;
      assertEquals("infernosentry", mon.Id);
      assertEquals("seq_infernosentry", mon.Sk1mode);
      assertEquals(9, Riiablo.files.monstats2.get(mon.MonStatsEx).InfernoAnim);
      assertEquals(14, Riiablo.files.monstats2.get(mon.MonStatsEx).InfernoLen);
    }
  }
}
