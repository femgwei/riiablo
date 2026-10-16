package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.D2;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.SimulationClock;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.event.*;
import com.riiablo.engine.server.monster.MonsterRank;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.map.Map;
import java.util.ArrayList;
import java.util.List;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;
import org.junit.jupiter.api.Test;

/** Real S2 animation entry, not an injected SkillDo or generic melee hit. */
class DiabColdIntegrationTest extends RiiabloTest {
  @Test void fractionalRemainderDies() { deathBoundary(255f / 256f, false); }
  @Test void exactlyOneLifeSurvives() { deathBoundary(1f, false); }
  @Test void aboveOneLifeSurvives() { deathBoundary(257f / 256f, false); }
  @Test void cancelledDamagePreservesFractionalLife() { deathBoundary(.5f, true); }

  private static void deathBoundary(float remainder, boolean cancel) {
    try (Fixture f = new Fixture("diablo", true, 0)) {
      // The native roll varies. At the damage event boundary, arrange exact
      // defender life relative to that roll without changing its damage packet.
      f.probe.beforeDamage = hit -> {
        assertTrue(hit.magicDamage >= 42f && hit.magicDamage < 61f);
        f.attrs().get(Stat.hitpoints).set(cancel ? remainder : hit.damage + remainder);
        if (cancel) hit.damage = 0f;
      };
      f.begin(); f.untilHit();
      boolean fatal = !cancel && remainder < 1f;
      assertEquals(fatal ? 0f : remainder, f.hp(), .0001f);
      assertEquals(fatal ? 1 : 0, f.probe.deaths);
      f.finish();
      assertEquals(1, f.probe.hits.size());
      assertEquals(fatal ? 1 : 0, f.probe.deaths);
    }
  }

  @Test void zeroDamagePreservesFractionalLife() {
    try (Fixture f = new Fixture("diablo", true, 0)) {
      f.stat(Stat.magic_damage_reduction, 1000);
      f.attrs().get(Stat.hitpoints).set(.5f);
      f.begin(); f.untilHit();
      assertEquals(0f, f.probe.hits.get(0).damage);
      assertEquals(.5f, f.hp());
      assertEquals(0, f.probe.deaths);
    }
  }

  @Test void delayedFullAbsorbDoesNotHeal() { absorbBoundary(true, true); }
  @Test void delayedPartialAbsorbStillTakesDamage() { absorbBoundary(true, false); }
  @Test void livingFullAbsorbStillHeals() { absorbBoundary(false, true); }

  private static void absorbBoundary(boolean delayed, boolean full) {
    try (Fixture f = new Fixture("diablo", true, 0)) {
      f.stat(Stat.hitpoints, 500);
      f.stat(Stat.item_absorbmagic, full ? 1000 : 10);
      if (delayed) f.states().addState(StateId.DEATH_DELAY, 1000, 1, f.source);
      f.begin(); f.untilHit();
      DamageEvent hit = f.probe.hits.get(0);
      if (full) {
        assertEquals(0f, hit.damage);
        if (delayed) assertEquals(500f, f.hp());
        else assertTrue(f.hp() >= 542f && f.hp() < 561f);
      } else {
        assertTrue(hit.magicDamage >= 32f && hit.magicDamage < 51f);
        assertEquals(500f - hit.damage, f.hp(), .0001f);
      }
      assertEquals(0, f.probe.deaths);
      assertEquals(250, f.states().getState(StateId.COLD).initialDuration);
      assertEquals(0, f.missiles());
    }
  }

  @Test void coordinateOnlyFriendlyAndAlreadyDeadTargetsNeverReceiveAFakeHit() {
    for (int invalid = 0; invalid < 3; invalid++) {
      try (Fixture f = new Fixture("diablo", invalid != 1, 0)) {
        f.begin();
        if (invalid == 0) f.cast().targetId = Engine.INVALID_ENTITY;
        else if (invalid == 1) f.world.getMapper(SummonedPet.class).remove(f.target);
        else f.stat(Stat.hitpoints, 0);
        f.finish();
        assertTrue(f.probe.hits.isEmpty());
        assertEquals(0, f.missiles());
        assertFalse(f.states().hasState(StateId.COLD));
        assertFalse(f.states().hasState(StateId.FREEZE));
      }
    }
  }

  @Test void playerDifficultyPenaltyAndRaisedColdCapApplyToLengthNotMagicResistance() {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      try (Fixture f = new Fixture("diablo", true, difficulty)) {
        f.begin(); f.untilHit();
        int penalty = com.riiablo.engine.server.monster.MonsterUtil.getResistancePenalty(difficulty);
        assertEquals(250 * (100 - penalty) / 100, f.states().getState(StateId.COLD).initialDuration);
        assertTrue(f.probe.hits.get(0).magicDamage >= 42 && f.probe.hits.get(0).magicDamage < 61,
            "difficulty penalty must not lower magic resistance");
      }
    }
    try (Fixture f = new Fixture("diablo", true, 0)) {
      f.stat(Stat.coldresist, 200); f.stat(Stat.maxcoldresist, 20);
      f.begin(); f.untilHit();
      assertEquals(12, f.states().getState(StateId.COLD).initialDuration);
      assertTrue(f.probe.hits.get(0).magicDamage >= 42);
    }
  }

  @Test void monsterResistanceHasNoPlayerCapAndHalfLengthPrecedesResistanceRounding() {
    try (Fixture f = new Fixture("diablo", false, 0)) {
      f.stat(Stat.coldresist, 90); f.begin(); f.untilHit();
      assertEquals(25, f.states().getState(StateId.FREEZE).initialDuration);
    }
    try (Fixture f = new Fixture("diablo", true, 0)) {
      f.stat(Stat.item_halffreezeduration, 1); f.stat(Stat.coldresist, -100);
      assertEquals(150, com.riiablo.engine.server.combat.CombatSystem.INSTANCE.resolveColdLengthBeforeState(
          f.attrs(), true, 151, 0, f.states(), 0));
    }
  }

  @Test void nativeDirectDamageReducesBeforeResistanceAndCapsPercentAbsorbAtForty() {
    com.riiablo.engine.server.combat.CombatSystem combat = com.riiablo.engine.server.combat.CombatSystem.INSTANCE;
    try (Fixture f = new Fixture("diablo", false, 0)) {
      f.stat(Stat.magicresist, 90);
      assertEquals(10, combat.calculateNativeFixedElementalDamage(f.attrs(), false, false,
          com.riiablo.engine.server.combat.CombatSystem.DAMAGE_MAGIC, 100, 0, f.states(), 0).totalDamage);
    }
    try (Fixture f = new Fixture("diablo", true, 0)) {
      f.stat(Stat.magic_damage_reduction, 10); f.stat(Stat.magicresist, 50);
      assertEquals(45, combat.calculateNativeFixedElementalDamage(f.attrs(), true, false,
          com.riiablo.engine.server.combat.CombatSystem.DAMAGE_MAGIC, 100, 0, f.states(), 0).totalDamage);
      f.stat(Stat.item_absorbmagic_percent, 100);
      assertEquals(27, combat.calculateNativeFixedElementalDamage(f.attrs(), true, false,
          com.riiablo.engine.server.combat.CombatSystem.DAMAGE_MAGIC, 100, 0, f.states(), 0).totalDamage);
      f.begin(); f.untilHit();
      assertTrue(f.probe.hits.get(0).magicDamage >= 10 && f.probe.hits.get(0).magicDamage <= 15);
      assertEquals(250, f.states().getState(StateId.COLD).initialDuration);
    }
  }

  @Test void realS2MarkerAppliesMagicNotColdOrWeaponDamageAndPlayerChill() {
    for (String row : new String[] {"diablo", "diabloclone"}) {
      try (Fixture f = new Fixture(row, true, 0)) {
        f.stat(Stat.armorclass, 1000000); f.stat(Stat.toblock, 100);
        f.sourceAttrs().base().put(Stat.mindamage, 9999);
        f.sourceAttrs().base().put(Stat.maxdamage, 9999); f.sourceAttrs().reset();
        f.begin();
        assertEquals(1000f, f.hp()); assertFalse(f.states().hasState(StateId.COLD));
        f.untilHit();
        DamageEvent hit = f.probe.hits.get(0);
        int min = row.equals("diablo") ? 42 : 24, max = row.equals("diablo") ? 61 : 37;
        assertTrue(hit.magicDamage >= min && hit.magicDamage < max);
        assertEquals(DamageEvent.MISSILE, hit.kind);
        assertEquals(0, hit.physicalDamage); assertEquals(0, hit.coldDamage);
        assertEquals(1000f - hit.damage, f.hp(), .001f);
        assertTrue(f.states().hasState(StateId.COLD)); assertFalse(f.states().hasState(StateId.FREEZE));
        assertEquals(row.equals("diablo") ? 250 : 150, f.states().getState(StateId.COLD).initialDuration);
        assertEquals(f.source, f.states().getState(StateId.COLD).sourceEntityId);
        while (f.cast() != null) f.tick();
        assertEquals(1, f.probe.hits.size()); assertEquals(1, f.probe.finished);
        assertEquals(0, f.missiles());
      }
    }
  }

  @Test void magicResistanceColdResistanceAndCannotBeFrozenAreIndependent() {
    try (Fixture f = new Fixture("diablo", true, 0)) {
      f.stat(Stat.magicresist, 50); f.stat(Stat.coldresist, 50);
      f.stat(Stat.item_halffreezeduration, 1);
      f.begin(); f.untilHit();
      assertTrue(f.probe.hits.get(0).magicDamage >= 21 && f.probe.hits.get(0).magicDamage <= 30);
      assertEquals(62, f.states().getState(StateId.COLD).initialDuration);
    }
    try (Fixture f = new Fixture("diablo", true, 0)) {
      f.stat(Stat.item_cannotbefrozen, 1); f.begin(); f.untilHit();
      assertTrue(f.hp() < 1000); assertFalse(f.states().hasState(StateId.COLD));
      assertFalse(f.states().hasState(StateId.FREEZE));
    }
  }

  @Test void mdrAndAbsorbCanRemoveDamageButDoNotShortenFreezeLength() {
    try (Fixture f = new Fixture("diablo", true, 0)) {
      f.stat(Stat.magic_damage_reduction, 1000); f.begin(); f.untilHit();
      assertEquals(1000f, f.hp()); assertEquals(0, f.probe.hits.get(0).damage);
      assertEquals(250, f.states().getState(StateId.COLD).initialDuration);
    }
    try (Fixture f = new Fixture("diablo", true, 0)) {
      f.stat(Stat.item_absorbmagic, 1000); f.stat(Stat.hitpoints, 500);
      f.begin(); f.untilHit();
      assertTrue(f.hp() > 500); assertEquals(0, f.probe.hits.get(0).damage);
      assertEquals(250, f.states().getState(StateId.COLD).initialDuration);
    }
  }

  @Test void ordinaryMonsterUsesFreezeDivisorOnceBossUsesColdAndUninterruptableRejects() {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      try (Fixture f = new Fixture("diablo", false, difficulty)) {
        f.begin(); f.untilHit();
        assertTrue(f.states().hasState(StateId.FREEZE));
        assertFalse(f.states().hasState(StateId.COLD));
        int divisor = Riiablo.files.DifficultyLevels.get(difficulty).MonsterFreezeDivisor;
        assertEquals(250 / divisor, f.states().getState(StateId.FREEZE).initialDuration);
      }
      try (Fixture f = new Fixture("diablo", false, difficulty)) {
        f.world.getMapper(Monster.class).get(f.target).rank = MonsterRank.UNIQUE;
        f.begin(); f.untilHit();
        assertFalse(f.states().hasState(StateId.FREEZE));
        int divisor = Riiablo.files.DifficultyLevels.get(difficulty).MonsterColdDivisor;
        assertEquals(250 / divisor, f.states().getState(StateId.COLD).initialDuration);
      }
    }
    try (Fixture f = new Fixture("diablo", false, 0)) {
      f.states().addState(StateId.UNINTERRUPTABLE, 0, 1, f.target);
      f.begin(); f.finish();
      assertEquals(1000f, f.hp()); assertTrue(f.probe.hits.isEmpty());
      assertFalse(f.states().hasState(StateId.FREEZE));
    }
  }

  @Test void magicImmuneMonsterCanFreezeButColdImmuneMonsterCannot() {
    try (Fixture f = new Fixture("diablo", false, 0)) {
      f.stat(Stat.magicresist, 100); f.begin(); f.untilHit();
      assertEquals(1000f, f.hp()); assertTrue(f.states().hasState(StateId.FREEZE));
    }
    try (Fixture f = new Fixture("diablo", false, 0)) {
      f.stat(Stat.coldresist, 100); f.begin(); f.untilHit();
      assertTrue(f.hp() < 1000); assertFalse(f.states().hasState(StateId.FREEZE));
    }
  }

  @Test void lostTargetNeverFallsBackToCoordinatesAndMovingOutOfMeleeDoesNotRerollRange() {
    try (Fixture f = new Fixture("diablo", true, 0)) {
      f.begin(); f.world.delete(f.target); f.finish();
      assertTrue(f.probe.hits.isEmpty()); assertEquals(0, f.missiles());
    }
    try (Fixture f = new Fixture("diablo", true, 0)) {
      f.begin(); f.world.getMapper(Position.class).get(f.target).position.set(50, 50);
      f.untilHit(); assertTrue(f.hp() < 1000); assertTrue(f.states().hasState(StateId.COLD));
    }
  }

  @Test void lethalHitDispatchesDeathAndChillExpiresNormally() {
    try (Fixture f = new Fixture("diablo", true, 0)) {
      f.stat(Stat.hitpoints, 1); f.begin(); f.untilHit();
      assertEquals(0, f.hp()); assertEquals(1, f.probe.deaths);
    }
    try (Fixture f = new Fixture("diablo", true, 0)) {
      f.begin(); f.untilHit();
      for (int i = 0; i < 251; i++) f.tick();
      assertFalse(f.states().hasState(StateId.COLD));
      assertEquals(1, f.probe.hits.size());
    }
  }

  static final class Probe extends PassiveSystem {
    java.util.function.Consumer<DamageEvent> beforeDamage;
    final List<DamageEvent> hits = new ArrayList<>();
    int finished, deaths;
    @Subscribe public void damage(DamageEvent event) {
      hits.add(event);
      if (beforeDamage != null) beforeDamage.accept(event);
    }
    @Subscribe public void finished(AnimDataFinishedEvent event) { finished++; }
    @Subscribe public void death(DeathEvent event) { deaths++; }
  }

  static final class Fixture implements AutoCloseable {
    final World world;
    final Probe probe = new Probe();
    final Skills.Entry skill = Riiablo.files.skills.get(194);
    final int source, target;
    int ticks;
    Fixture(String rowId, boolean playerTarget, int difficulty) {
      EntityFactory factory = mock(EntityFactory.class);
      world = new World(new WorldConfigurationBuilder().with(new EventSystem(), probe,
          new StateUpdater(), new Actioneer(), new Pathfinder(), new CofManager(),
          new AnimStepper(), new ServerSkillSystem(), factory).build()
          .register("map", new Map(1, difficulty)).register("factory", factory));
      world.setDelta(SimulationClock.STEP_SECONDS);
      source = unit();
      monster(source, rowId);
      target = unit();
      world.getMapper(Position.class).get(target).position.set(13, 10);
      if (playerTarget) world.getMapper(Player.class).create(target);
      else {
        monster(target, "fallen1");
        // A player-owned monster is an actual hostile target for Diablo;
        // an unowned evil Fallen is not. Do not forge an expiring Conversion.
        world.getMapper(SummonedPet.class).create(target).ownerId = target;
      }
      world.process();
    }
    int unit() {
      int id = world.create();
      world.getMapper(Position.class).create(id).position.set(10, 10);
      Attributes attrs = Attributes.obtainStandard();
      attrs.base().put(Stat.hitpoints, 1000); attrs.base().put(Stat.maxhp, 1000);
      attrs.base().put(Stat.level, 30); attrs.reset();
      world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
      world.getMapper(UnitStates.class).create(id).init(world, id);
      return id;
    }
    void monster(int id, String name) {
      MonStats.Entry row = Riiablo.files.monstats.get(name);
      world.getMapper(Monster.class).create(id).set(row, Riiablo.files.monstats2.get(row.MonStatsEx));
    }
    void begin() {
      world.getMapper(Sequence.class).create(source).sequence(Engine.Monster.MODE_S2, Engine.Monster.MODE_NU).started = true;
      world.getMapper(Casting.class).create(source).set(194, target, new Vector2(13, 10));
      MonStats.Entry row = world.getMapper(Monster.class).get(source).monstats;
      D2.Entry entry = D2.loadFromFile(Riiablo.mpqs.resolve("data/global/eanimdata.d2"))
          .getEntry((row.Code + "S2HTH").toUpperCase());
      AnimData anim = world.getMapper(AnimData.class).create(source);
      anim.frame = 0; anim.speed = entry.speed; anim.numFrames = entry.framesPerDir << 8;
      anim.keyframes = entry.data; anim.lastKeyframeIndex = -1;
      world.getSystem(EventSystem.class).dispatch(SkillStartEvent.obtain(source, 194, target,
          new Vector2(13, 10), skill.srvstfunc, skill.cltstfunc));
    }
    void tick() { assertTrue(ticks++ < 1000); world.process(); }
    void untilHit() { while (probe.hits.isEmpty()) tick(); }
    void finish() { while (cast() != null) tick(); }
    Casting cast() { return world.getMapper(Casting.class).get(source); }
    Attributes attrs() { return world.getMapper(AttributesWrapper.class).get(target).attrs; }
    Attributes sourceAttrs() { return world.getMapper(AttributesWrapper.class).get(source).attrs; }
    float hp() { return attrs().get(Stat.hitpoints).asFixed(); }
    void stat(short stat, int value) { attrs().base().put(stat, value); attrs().reset(); }
    StateList states() { return world.getMapper(UnitStates.class).get(target).stateList; }
    int missiles() { return world.getAspectSubscriptionManager().get(Aspect.all(Missile.class)).getEntities().size(); }
    @Override public void close() { world.dispose(); }
  }
}
