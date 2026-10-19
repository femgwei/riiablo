package com.riiablo.engine.server.combat;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.event.DamageEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LeechSettlementTest extends RiiabloTest {
  @Test void nativeRawOrderingIncludesDivisorDrainAndOverkill() {
    assertEquals(2560, NativeLeechMath.percentage(200 * 256, 300 * 256, 30, 3, 50));
    assertEquals(1280, NativeLeechMath.percentage(200 * 256, 300 * 256, 15, 3, 50));
    assertEquals(640, NativeLeechMath.percentage(200 * 256, 50 * 256, 30, 3, 50));
    assertEquals(1, NativeLeechMath.percentage(257, 257, 2, 3, 100));
    assertEquals(0, NativeLeechMath.percentage(200 * 256, 300 * 256, 30, 3, 0));
    assertEquals(0, NativeLeechMath.percentage(0, 300 * 256, 30, 3, 100));
    assertEquals(30 * 256, NativeLeechMath.percentage(200 * 256, 300 * 256, 30, 1, 50));
    assertEquals(2560, NativeLeechMath.percentage(100 * 256, 100 * 256, 10, 0, 100));
  }

  @Test void actualMeleeAndMissileBoundaryRestoresExactlyOnceIncludingFraction() {
    for (boolean missile : new boolean[] {false, true}) {
      World world = world();
      try {
        int attacker = unit(world, 10f), victim = monster(world, 5f, 50);
        world.getMapper(Player.class).create(attacker);
        world.process();
        DamageEvent hit = missile ? DamageEvent.obtainMissile(attacker, victim, 200, 100, null)
            : DamageEvent.obtainMelee(attacker, victim, 200, 100);
        if (missile) {
          hit.snapshotLifeLeechPercent = 10;
          hit.snapshotManaLeechPercent = 5;
        }
        DamageSettlement.commit(world, hit);
        assertEquals(10.25f, attrs(world, attacker).get(Stat.hitpoints).asFixed());
        assertEquals(2.125f, attrs(world, attacker).get(Stat.mana).asFixed());
        assertEquals(.25f, hit.lifeLeeched);
        DamageSettlement.commit(world, hit);
        assertEquals(10.25f, attrs(world, attacker).get(Stat.hitpoints).asFixed());
      } finally { world.dispose(); }
    }
  }

  @Test void zeroDrainElementalReactiveSuppressedAndDeadSourceCannotLeech() {
    for (int scenario = 0; scenario < 6; scenario++) {
      World world = world();
      try {
        int attacker = unit(world, scenario == 4 ? 0f : 10f);
        int victim = monster(world, 100f, scenario == 0 ? 0 : 100);
        world.getMapper(Player.class).create(attacker);
        world.process();
        DamageEvent hit = scenario == 2 ? DamageEvent.obtainReactive(attacker, victim, 20, 20)
            : DamageEvent.obtainMelee(attacker, victim, scenario == 5 ? 0 : 20,
                scenario == 1 ? 0 : 20);
        hit.suppressLeech = scenario == 3;
        DamageSettlement.commit(world, hit);
        assertEquals(0f, hit.lifeLeeched);
        assertEquals(0f, hit.manaLeeched);
      } finally { world.dispose(); }
    }
  }

  @Test void hirelingAndSkillContributionsUseSharedPolicy() {
    World world = world();
    try {
      int attacker = unit(world, 99.5f), victim = monster(world, 100f, 100);
      world.getMapper(Mercenary.class).create(attacker);
      world.process();
      DamageEvent hit = DamageEvent.obtainMelee(attacker, victim, 20, 20);
      hit.extraLifeLeechPercent = 20;
      DamageSettlement.commit(world, hit);
      assertEquals(100f, attrs(world, attacker).get(Stat.hitpoints).asFixed());
      assertEquals(2f, attrs(world, attacker).get(Stat.mana).asFixed());
      assertEquals(.5f, hit.lifeLeeched);
      int player = unit(world, 10f);
      world.getMapper(Player.class).create(player);
      world.process();
      DamageEvent missile = DamageEvent.obtainMissile(player, victim, 20, 20, null);
      missile.snapshotLifeLeechPercent = 10;
      missile.snapshotManaLeechPercent = 5;
      attrs(world, player).get(Stat.lifedrainmindam).set(20);
      missile.extraLifeLeechPercent = 10;
      DamageSettlement.commit(world, missile);
      assertEquals(14f, attrs(world, player).get(Stat.hitpoints).asFixed());
      world.getMapper(UnitStates.class).create(player).snapshotOnly = true;
      DamageSettlement.commit(world, DamageEvent.obtainMelee(player, victim, 20, 20));
      assertEquals(14f, attrs(world, player).get(Stat.hitpoints).asFixed());
    } finally { world.dispose(); }
  }

  @Test void actualDifficultyDataIsLoadedNotHardcoded() {
    assertEquals(1, Riiablo.files.DifficultyLevels.get(0).LifeStealDivisor);
    assertTrue(Riiablo.files.DifficultyLevels.get(2).LifeStealDivisor > 1);
  }

  @Test void ordinaryMonsterDrainIsNotMisinterpretedAsPlayerPercentage() {
    World world = world();
    try {
      int source = monster(world, 10f, 100), victim = monster(world, 100f, 100);
      world.process();
      DamageEvent hit = DamageEvent.obtainMelee(source, victim, 20, 20);
      DamageSettlement.commit(world, hit);
      assertEquals(10f, attrs(world, source).get(Stat.hitpoints).asFixed());
      assertNull(hit.leechOverlay);
    } finally { world.dispose(); }
  }

  @Test void creationSnapshotScalesSkillSourceAndSurvivesEquipmentChange() {
    World world = world();
    try {
      int source = unit(world, 10f);
      Attributes owner = attrs(world, source);
      owner.base().put(Stat.mindamage, 20);
      owner.base().put(Stat.maxdamage, 20);
      owner.reset();
      Missile arrow = new Missile();
      arrow.missile = Riiablo.files.Missiles.get("arrow");
      com.riiablo.codec.excel.Skills.Entry skill = new com.riiablo.codec.excel.Skills.Entry();
      skill.skill = "test-source";
      skill.SrcDam = 96;
      skill.HitShift = 8;
      assertTrue(MissileDamageResolver.initializeSkill(Riiablo.files, arrow, skill, owner, 1));
      assertEquals(7, arrow.damage.getValue(Stat.lifedrainmindam, 0));
      assertEquals(3, arrow.damage.getValue(Stat.manadrainmindam, 0));
      owner.get(Stat.lifedrainmindam).set(90);
      assertEquals(7, arrow.damage.getValue(Stat.lifedrainmindam, 0));
      skill.SrcDam = 0;
      skill.MinDam = 20;
      skill.MaxDam = 20;
      assertTrue(MissileDamageResolver.initializeSkill(Riiablo.files, arrow, skill, owner, 1));
      assertEquals(0, arrow.damage.getValue(Stat.lifedrainmindam, -1));
    } finally { world.dispose(); }
  }

  @Test void hellDifficultyUsesSeparateDivisorsButHirelingDoesNot() {
    World world = world();
    try {
      int source = unit(world, 10f), victim = monster(world, 100f, 50);
      world.getMapper(Player.class).create(source);
      com.riiablo.map.Map map = org.mockito.Mockito.mock(com.riiablo.map.Map.class);
      org.mockito.Mockito.when(map.getDifficulty()).thenReturn(2);
      world.getMapper(MapWrapper.class).create(victim).map = map;
      world.process();
      DamageEvent hit = DamageEvent.obtainMelee(source, victim, 60, 60);
      DamageSettlement.commit(world, hit);
      assertEquals(NativeLeechMath.percentage(60 * 256, 100 * 256, 10,
          Riiablo.files.DifficultyLevels.get(2).LifeStealDivisor, 50) / 256f,
          hit.lifeLeeched);
      int merc = unit(world, 10f);
      world.getMapper(Mercenary.class).create(merc);
      world.process();
      DamageEvent second = DamageEvent.obtainMelee(merc, victim, 20, 20);
      DamageSettlement.commit(world, second);
      assertEquals(1f, second.lifeLeeched);
      assertNotNull(second.leechOverlay);
    } finally { world.dispose(); }
  }

  private static World world() { return new World(new WorldConfigurationBuilder().build()); }
  private static Attributes attrs(World world, int id) {
    return world.getMapper(AttributesWrapper.class).get(id).attrs;
  }
  private static int unit(World world, float hp) {
    int id = world.create();
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, 100f);
    attrs.base().put(Stat.mana, 2f);
    attrs.base().put(Stat.maxmana, 100f);
    attrs.base().put(Stat.lifedrainmindam, 10);
    attrs.base().put(Stat.manadrainmindam, 5);
    attrs.reset();
    world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
    return id;
  }
  private static int monster(World world, float hp, int drain) {
    int id = unit(world, hp);
    MonStats.Entry row = new MonStats.Entry();
    row.Drain = new int[] {drain, drain, drain};
    world.getMapper(Monster.class).create(id).monstats = row;
    return id;
  }
}
