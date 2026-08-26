package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.combat.MonsterModeDamageResolver;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.MovementModes;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.AnimDataKeyframeEvent;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.map.Map;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import org.junit.jupiter.api.Test;

/** Headless native-data coverage for D2MOO SrvSt42/SrvDo083 Fire Hit. */
class FireHitIntegrationTest extends RiiabloTest {
  @Test
  void sandRaiderFireHitUsesNativeS1AndFireProfile() {
    MonStats.Entry row = Riiablo.files.monstats.get("sandraider1");
    assertNotNull(row);
    Skills.Entry skill = Riiablo.files.skills.get(row.Skill1);
    assertNotNull(skill);
    assertEquals(42, skill.srvstfunc);
    assertEquals(83, skill.srvdofunc);

    int level = Math.max(1, row.Level[0]);
    MathUtils.random.setSeed(0xF183L);
    MonsterModeDamageResolver.Profile profile = MonsterModeDamageResolver.resolve(
        row, level, 0, Engine.Monster.MODE_S1);
    MonsterStatsCalculator.MonsterStatsInit nativeS1 =
        new MonsterStatsCalculator.MonsterStatsInit();
    assertTrue(MonsterStatsCalculator.calculateMonsterStatsByLevel(
        row.hcIdx, 1, 0, level, (short) 0x20, nativeS1));
    assertEquals(nativeS1.S1MinD, profile.minDamage);
    assertEquals(nativeS1.S1MaxD, profile.maxDamage);
    assertEquals(nativeS1.TH, profile.attackRating);
    assertTrue(profile.elementalMax[com.riiablo.engine.server.combat.CombatSystem.DAMAGE_FIRE] > 0,
        "Sand Raider S1 must carry its native MonStats fire damage");
    System.out.println("[FIRE_HIT_AUDIT] monster=" + row.Id + " skill=" + skill.skill
        + " level=" + level + " physical=" + profile.minDamage + ".."
        + profile.maxDamage + " ar=" + profile.attackRating + " fire="
        + profile.elementalMin[1] + ".." + profile.elementalMax[1]
        + " elements=" + profile.matchedElementProfiles + " status=PASS");
  }

  @Test
  void fireHitKeyframeDispatchesAuthoritativeDamage() {
    MonStats.Entry row = Riiablo.files.monstats.get("sandraider1");
    Skills.Entry skill = Riiablo.files.skills.get(row.Skill1);
    MonStats2.Entry row2 = Riiablo.files.monstats2.get(row.MonStatsEx);
    Probe probe = new Probe();
    Actioneer actioneer = new Actioneer();
    NoopFactory factory = new NoopFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), probe, actioneer, new Pathfinder(), factory)
        .build().register("map", new Map(0, 0)).register("factory", factory));
    try {
      int raider = world.create();
      world.getMapper(Monster.class).create(raider).set(row, row2);
      world.getMapper(Class.class).create(raider).type = Class.Type.MON;
      world.getMapper(Position.class).create(raider).position.set(10, 10);
      world.getMapper(Angle.class).create(raider);
      world.getMapper(MovementModes.class).create(raider).set(
          Engine.Monster.MODE_NU, Engine.Monster.MODE_WL, Engine.Monster.MODE_RN);
      world.getMapper(AttributesWrapper.class).create(raider).attrs =
          attributes(100, Math.max(1, row.Level[0]), 0, 1, 2, 1);

      int player = world.create();
      world.getMapper(Player.class).create(player);
      world.getMapper(Class.class).create(player).type = Class.Type.PLR;
      world.getMapper(Position.class).create(player).position.set(11, 10);
      Attributes target = attributes(100, Math.max(1, row.Level[0]), 0, 1, 2, 1);
      world.getMapper(AttributesWrapper.class).create(player).attrs = target;

      MathUtils.random.setSeed(0xF183L);
      float hpBefore = hitpoints(target);
      actioneer.cast(raider, skill.Id, player, new Vector2(11, 10));
      world.getSystem(EventSystem.class).dispatch(
          AnimDataKeyframeEvent.obtain(raider, Engine.KEYFRAME_ATK));
      float hpAfter = hitpoints(target);

      assertEquals(1, probe.skillDoEvents);
      assertEquals(1, probe.damageEvents);
      assertTrue(hpAfter < hpBefore);
      assertTrue(hpBefore - hpAfter > 9f,
          "Fire Hit must include the S1 fire profile, not only its 7..9 physical damage");
      assertEquals(0, probe.deathEvents);
      System.out.println("[FIRE_HIT_CHAIN] entity=" + raider + " target=" + player
          + " skillDo=" + probe.skillDoEvents + " damageEvents=" + probe.damageEvents
          + " hp=" + hpBefore + "->" + hpAfter + " status=PASS");
    } finally {
      world.dispose();
    }
  }

  private static Attributes attributes(float hp, int level, int defense,
      int minDamage, int maxDamage, int attackRating) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, hp);
    attrs.base().put(Stat.level, level);
    attrs.base().put(Stat.armorclass, defense);
    attrs.base().put(Stat.mindamage, minDamage);
    attrs.base().put(Stat.maxdamage, maxDamage);
    attrs.base().put(Stat.tohit, attackRating);
    attrs.reset();
    return attrs;
  }

  private static float hitpoints(Attributes attrs) {
    StatRef hp = attrs.get(Stat.hitpoints, StatRef.obtain());
    return hp != null ? hp.asFixed() : 0f;
  }

  private static final class Probe extends BaseSystem {
    int skillDoEvents;
    int damageEvents;
    int deathEvents;
    @Subscribe public void onSkillDo(SkillDoEvent event) { skillDoEvents++; }
    @Subscribe public void onDamage(DamageEvent event) { damageEvents++; }
    @Subscribe public void onDeath(DeathEvent event) { deathEvents++; }
    @Override protected void processSystem() {}
  }

  private static final class NoopFactory extends EntityFactory {
    @Override public int createPlayer(CharData data, Vector2 position) { return -1; }
    @Override public int createDynamicObject(int act, int preset, float x, float y) { return -1; }
    @Override public int createStaticObject(int act, int object, float x, float y) { return -1; }
    @Override public int createStaticObjectByClassId(int object, float x, float y) { return -1; }
    @Override public int createMonster(int monster, float x, float y) { return -1; }
    @Override public int createWarp(int index, float x, float y) { return -1; }
    @Override public int createItem(Item item, float x, float y) { return -1; }
    @Override public int createMissile(int missile, Vector2 angle, Vector2 position) { return -1; }
  }
}
