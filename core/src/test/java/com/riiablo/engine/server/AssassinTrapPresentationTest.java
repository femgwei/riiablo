package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.event.SoundEvent;
import com.riiablo.map.Map;
import java.util.ArrayList;
import java.util.List;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;
import org.junit.jupiter.api.Test;

class AssassinTrapPresentationTest extends RiiabloTest {
  @Test void ordinaryShotsFaceTargetPlayNativeSequenceAndReturnToNeutralWithoutAnotherMissile() {
    for (String monster : new String[] {"chargeboltsentry", "lightningsentry", "deathsentry",
        "wakeofdestruction"}) {
      try (Fixture f = new Fixture(monster)) {
        f.tick();
        assertEquals(1, f.pet.shotsFired, monster);
        assertTrue(f.created > 0, monster);
        int emitted = f.created;
        f.pet.attackCooldownFrames = 1000;
        assertEquals(Engine.Monster.MODE_S2, f.reference().mode, monster);
        assertEquals(1f, f.world.getMapper(Angle.class).get(f.source).angle.y, 0.0001f);
        Sequence sequence = f.world.getMapper(Sequence.class).get(f.source);
        assertNotNull(sequence, monster);
        if (!"wakeofdestruction".equals(monster)) {
          assertNotNull(sequence.nativeMonster, monster);
          assertEquals("seq_chargesentry", sequence.nativeMonsterName);
          assertEquals(15, sequence.nativeMonster.frames.length);
        }
        for (int i = 0; i < 20; i++) f.tick();
        assertEquals(Engine.Monster.MODE_NU, f.reference().mode, monster);
        assertFalse(f.world.getMapper(Sequence.class).has(f.source), monster);
        assertFalse(f.world.getMapper(Casting.class).has(f.source), monster);
        assertEquals(emitted, f.created, "presentation keyframe must not emit another missile");
        assertEquals(0, f.probe.skillDos, "no recursive authoritative skill callbacks");
      }
    }
  }

  @Test void deathSentryCorpseAttackUsesS2InsteadOfItsLightningSequence() {
    try (Fixture f = new Fixture("deathsentry")) {
      f.pet.attackCooldownFrames = 1000;
      f.tick();
      f.traps.presentAttack(f.source, f.monster, Riiablo.files.skills.get(f.monster.monstats.Skill1),
          f.target, true);
      f.tick();
      Sequence sequence = f.world.getMapper(Sequence.class).get(f.source);
      assertEquals(Engine.Monster.MODE_S2, sequence.mode1);
      assertNull(sequence.nativeMonster);
      for (int i = 0; i < 8; i++) f.tick();
      assertEquals(Engine.Monster.MODE_NU, f.reference().mode);
      assertEquals(0, f.created);
    }
  }

  @Test void configuredSoundsUsePositionedSoundIntentsAndRepeatedShotsRestart() {
    try (Fixture f = new Fixture("lightningsentry")) {
      f.pet.attackCooldownFrames = 1000;
      f.tick();
      Skills.Entry skill = new Skills.Entry();
      skill.skill = f.monster.monstats.Skill1;
      skill.monanim = "xx";
      skill.stsound = "trap_start";
      skill.dosound = "trap_fire";
      f.traps.presentAttack(f.source, f.monster, skill, f.target, false);
      f.tick();
      int restart = f.reference().animationRestartId;
      f.traps.presentAttack(f.source, f.monster, skill, f.target, false);
      f.tick();
      assertTrue(f.reference().animationRestartId > restart);
      assertEquals(4, f.probe.sounds.size());
      assertEquals("trap_start", f.probe.sounds.get(0).sound);
      assertEquals("trap_fire", f.probe.sounds.get(1).sound);
      for (SoundEvent event : f.probe.sounds) {
        assertEquals(f.source, event.entityId);
        assertTrue(event.positionPresent);
        assertEquals(10f, event.x);
        assertEquals(10f, event.y);
      }
      assertEquals(0, f.probe.skillDos);
      assertFalse(f.world.getMapper(Casting.class).has(f.source));
    }
  }

  static final class Probe extends PassiveSystem {
    final List<SoundEvent> sounds = new ArrayList<>();
    int skillDos;
    @Subscribe public void sound(SoundEvent event) { sounds.add(event); }
    @Subscribe public void skillDo(SkillDoEvent event) { skillDos++; }
  }

  static final class Fixture implements AutoCloseable {
    final AssassinTrapSystem traps = new DeterministicTraps();
    final Probe probe = new Probe();
    final World world;
    final int source, target;
    final Monster monster;
    final SummonedPet pet;
    int created;

    Fixture(String monsterId) {
      EntityFactory factory = mock(EntityFactory.class);
      world = new World(new WorldConfigurationBuilder().with(new EventSystem(), probe,
          new CofManager(), traps, new SequenceHandler(), new AnimStepper(), factory)
          .build().register("factory", factory).register("map", new Map(0, 0)));
      world.setDelta(1f / 25f);
      source = world.create();
      MonStats.Entry row = Riiablo.files.monstats.get(monsterId);
      monster = world.getMapper(Monster.class).create(source)
          .set(row, Riiablo.files.monstats2.get(row.MonStatsEx));
      world.getMapper(Position.class).create(source).position.set(10, 10);
      world.getMapper(Angle.class).create(source);
      world.getMapper(CofReference.class).create(source).set(row.Code, Engine.Monster.MODE_NU);
      AnimData anim = world.getMapper(AnimData.class).create(source);
      anim.speed = 256;
      anim.numFrames = 4 << 8;
      pet = world.getMapper(SummonedPet.class).create(source)
          .set(world.create(), "assassintrap", -1, 1, false, 0);
      pet.maxShots = 20;
      target = world.create();
      world.getMapper(Monster.class).create(target);
      world.getMapper(Position.class).create(target).position.set(10, 14);
      Attributes attrs = Attributes.obtainStandard();
      attrs.base().put(Stat.hitpoints, 10000);
      attrs.reset();
      world.getMapper(AttributesWrapper.class).create(target).attrs = attrs;
      when(factory.createMissile(any(Missiles.Entry.class), any(Vector2.class),
          any(Vector2.class), anyInt(), anyInt())).thenAnswer(call -> {
        created++;
        int id = world.create();
        Missiles.Entry missile = call.getArgument(0);
        world.getMapper(Missile.class).create(id)
            .set(missile, call.getArgument(2), missile.Range).setOwner(source);
        return id;
      });
    }

    CofReference reference() { return world.getMapper(CofReference.class).get(source); }
    void tick() { world.process(); }
    public void close() { world.dispose(); }
  }

  @com.artemis.annotations.All({SummonedPet.class, Monster.class, Position.class})
  static final class DeterministicTraps extends AssassinTrapSystem {
    @Override int roll(Monster monster, int unitId, int bound) { return 0; }
  }
}
