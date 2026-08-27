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
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.MovementModes;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.event.AnimDataKeyframeEvent;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import org.junit.jupiter.api.Test;

/** Real ECS event-chain scenarios for representative Act I monster skills. */
class Act1MonsterEcsScenarioTest extends RiiabloTest {
  @Test
  void spiderLayInstallsStateAndMovingSpiderEmitsAuthoritativeGoo() {
    MonStats.Entry row = Riiablo.files.monstats.get("arach1");
    assertNotNull(row);
    Skills.Entry skill = skillFor(row, 23);
    assertNotNull(skill);
    Scenario scenario = new Scenario(row, new ServerSkillSystem(true), new StateUpdater());
    try {
      scenario.actioneer.cast(scenario.source, skill.Id, Engine.INVALID_ENTITY,
          scenario.position(13, 10));
      scenario.keyframe(scenario.source);
      UnitStates states = scenario.world.getMapper(UnitStates.class).get(scenario.source);
      assertTrue(states.stateList.hasState(StateId.SPIDERLAY));
      scenario.world.getMapper(Velocity.class).create(scenario.source).setMonster(6f)
          .velocity.set(1, 0);
      scenario.world.setDelta(1f / 25f);
      scenario.world.process();
      assertEquals(1, scenario.factory.missilesCreated);
      assertEquals("spidergoolay", scenario.factory.lastMissile.toLowerCase());
      assertEquals(scenario.source, scenario.factory.lastMissileOwner);
      System.out.println("[ACT1_ECS_CHAIN] skill=SpiderLay state=SPIDERLAY missile="
          + scenario.factory.lastMissile + " owner=" + scenario.source + " status=PASS");
    } finally {
      scenario.close();
    }
  }

  @Test
  void monsterCurseKeyframeAppliesD2MooTargetState() {
    MonStats.Entry row = Riiablo.files.monstats.get("dkmag1");
    assertNotNull(row);
    Skills.Entry skill = skillFor(row, 30);
    assertNotNull(skill, "Doom Knight Mage must expose a curse skill");
    Scenario scenario = new Scenario(row);
    try {
      int target = scenario.target(11, 10);
      scenario.actioneer.cast(scenario.source, skill.Id, target, scenario.position(11, 10));
      scenario.keyframe(scenario.source);
      UnitStates states = scenario.world.getMapper(UnitStates.class).get(target);
      assertTrue(states.stateList.hasState(StateId.DECREPIFY)
          || states.stateList.hasState(StateId.AMPLIFYDAMAGE)
          || states.stateList.hasState(StateId.WEAKEN));
      System.out.println("[ACT1_ECS_CHAIN] skill=" + skill.skill + " curseTarget=" + target
          + " stateCount=" + states.stateList.size() + " status=PASS");
    } finally {
      scenario.close();
    }
  }

  @Test
  void maggotDownKeyframeHealsThroughActioneer() {
    MonStats.Entry row = Riiablo.files.monstats.get("sandmaggot1");
    Skills.Entry skill = Riiablo.files.skills.get(row.Skill2);
    Scenario scenario = new Scenario(row);
    try {
      Attributes life = scenario.attributes(100, 200);
      scenario.attributes(scenario.source).attrs = life;
      scenario.actioneer.cast(scenario.source, skill.Id, Engine.INVALID_ENTITY, scenario.position(10, 10));
      scenario.keyframe(scenario.source);
      assertEquals(110f, life.get(Stat.hitpoints).asFixed(), 0.001f);
      assertEquals(1, scenario.probe.skillDo);
      System.out.println("[ACT1_ECS_CHAIN] skill=MaggotDown keyframe=1 hp=100->"
          + life.get(Stat.hitpoints).asFixed() + " status=PASS");
    } finally {
      scenario.close();
    }
  }

  @Test
  void nestKeyframeCreatesConfiguredSpawnThroughFactory() {
    MonStats.Entry row = Riiablo.files.monstats.get("crownest3");
    Skills.Entry skill = Riiablo.files.skills.get(row.Skill1);
    Scenario scenario = new Scenario(row);
    try {
      System.out.println("[ACT1_ECS_DEBUG] nest row=" + row.Id + " skill1=" + row.Skill1
          + " skillId=" + skill.Id + " spawn=" + row.spawn + " minion1=" + row.minion1
          + " minion2=" + row.minion2);
      int target = scenario.target(12, 10);
      scenario.actioneer.cast(scenario.source, skill.Id, target, scenario.position(12, 10));
      scenario.keyframe(scenario.source);
      assertEquals(1, scenario.factory.monstersCreated);
      String expectedSpawn = row.spawn != null && !row.spawn.isEmpty() ? row.spawn
          : (row.minion1 != null && !row.minion1.isEmpty() ? row.minion1 : row.minion2);
      assertEquals(expectedSpawn, scenario.factory.lastMonster);
      assertEquals(1, scenario.probe.skillDo);
      System.out.println("[ACT1_ECS_CHAIN] skill=Nest spawn=" + row.spawn
          + " created=" + scenario.factory.monstersCreated + " status=PASS");
    } finally {
      scenario.close();
    }
  }

  @Test
  void vampireMeteorKeyframeDelegatesToServerMissileSystem() {
    MonStats.Entry row = Riiablo.files.monstats.get("vampire1");
    Skills.Entry skill = Riiablo.files.skills.get("VampireMeteor");
    Scenario scenario = new Scenario(row, new ServerSkillSystem(true));
    try {
      int target = scenario.target(16, 10);
      scenario.actioneer.cast(scenario.source, skill.Id, target, scenario.position(16, 10));
      scenario.keyframe(scenario.source);
      assertEquals(1, scenario.factory.missilesCreated);
      assertEquals(1, scenario.probe.skillDo);
      System.out.println("[ACT1_ECS_CHAIN] skill=VampireMeteor missile="
          + skill.srvmissilea + " created=" + scenario.factory.missilesCreated + " status=PASS");
    } finally {
      scenario.close();
    }
  }

  @Test
  void fireHitKeyframeAppliesDamageAndDispatchesEvents() {
    MonStats.Entry row = Riiablo.files.monstats.get("sandraider1");
    assertNotNull(row);
    Skills.Entry skill = Riiablo.files.skills.get(row.Skill1);
    assertNotNull(skill);
    Scenario scenario = new Scenario(row);
    try {
      Attributes targetAttrs = combatAttributes(100, 100, 0, 1, 1, 1);
      scenario.attributes(scenario.source).attrs = combatAttributes(100, 100, 10, 20, 10000, 1);
      int target = scenario.target(11, 10, targetAttrs, true);
      MathUtils.random.setSeed(0xF183L);
      float before = hitpoints(targetAttrs);
      scenario.actioneer.cast(scenario.source, skill.Id, target, scenario.position(11, 10));
      scenario.keyframe(scenario.source);
      assertTrue(hitpoints(targetAttrs) < before, "Fire Hit must damage the target");
      assertEquals(1, scenario.probe.damage);
      assertEquals(1, scenario.probe.skillDo);
      assertEquals(0, scenario.probe.death);
      System.out.println("[ACT1_ECS_CHAIN] skill=FireHit damageEvents=" + scenario.probe.damage
          + " hp=" + before + "->" + hitpoints(targetAttrs) + " status=PASS");
    } finally {
      scenario.close();
    }
  }

  @Test
  void chargeKeyframeAppliesOneAuthoritativeMeleeHit() {
    Skills.Entry skill = Riiablo.files.skills.get("Charge");
    assertNotNull(skill);
    MonStats.Entry row = findMonsterWithSkill(skill.skill);
    assertNotNull(row, "No monster exposes Charge in the current MonStats data");
    Scenario scenario = new Scenario(row);
    try {
      Attributes targetAttrs = combatAttributes(80, 80, 0, 1, 1, 1);
      scenario.attributes(scenario.source).attrs = combatAttributes(100, 100, 20, 20, 10000, 1);
      int target = scenario.target(11, 10, targetAttrs, true);
      float before = hitpoints(targetAttrs);
      scenario.actioneer.cast(scenario.source, skill.Id, target, scenario.position(11, 10));
      scenario.keyframe(scenario.source);
      float after = hitpoints(targetAttrs);
      assertTrue(after < before, "Charge must hit an adjacent target");
      assertEquals(1, scenario.probe.damage, "one keyframe must produce one hit");
      assertEquals(1, scenario.probe.skillDo);
      System.out.println("[ACT1_ECS_CHAIN] skill=Charge monster=" + row.Id
          + " damageEvents=" + scenario.probe.damage + " hp=" + before + "->" + after
          + " status=PASS");
    } finally {
      scenario.close();
    }
  }

  @Test
  void maggotLayKeyframeCreatesEggAtNativeDirectionalOffset() {
    MonStats.Entry row = Riiablo.files.monstats.get("sandmaggot1");
    assertNotNull(row);
    Skills.Entry skill = skillFor(row, 87);
    assertNotNull(skill, "Sand Maggot must expose MaggotLay");
    Scenario scenario = new Scenario(row);
    try {
      int target = scenario.target(13, 10);
      scenario.actioneer.cast(scenario.source, skill.Id, target, scenario.position(13, 10));
      scenario.keyframe(scenario.source);
      String expected = row.spawn != null && !row.spawn.isEmpty() ? row.spawn : row.minion1;
      assertEquals(expected, scenario.factory.lastMonster);
      assertEquals(1, scenario.factory.monstersCreated);
      // D2Common_11055 uses the native 32-entry orientation table; an eastward
      // target maps to lookup index 14 and therefore offset (-2, +2).
      assertEquals(8f, scenario.factory.lastMonsterX, 0.01f);
      assertEquals(12f, scenario.factory.lastMonsterY, 0.01f);
      System.out.println("[ACT1_ECS_CHAIN] skill=MaggotLay spawn=" + expected
          + " position=(" + scenario.factory.lastMonsterX + ","
          + scenario.factory.lastMonsterY + ") status=PASS");
    } finally {
      scenario.close();
    }
  }

  @Test
  void vampireFirewallKeyframeCreatesOneFirewallMaker() {
    MonStats.Entry row = Riiablo.files.monstats.get("vampire1");
    assertNotNull(row);
    Skills.Entry skill = Riiablo.files.skills.get("VampireFirewall");
    assertNotNull(skill);
    Scenario scenario = new Scenario(row, new ServerSkillSystem(true));
    try {
      int target = scenario.target(16, 10);
      scenario.actioneer.cast(scenario.source, skill.Id, target, scenario.position(16, 10));
      scenario.keyframe(scenario.source);
      assertEquals(1, scenario.factory.missilesCreated);
      assertEquals(skill.srvmissilea, scenario.factory.lastMissile);
      assertEquals(1, scenario.probe.skillDo);
      System.out.println("[ACT1_ECS_CHAIN] skill=VampireFirewall missile="
          + scenario.factory.lastMissile + " created=1 status=PASS");
    } finally {
      scenario.close();
    }
  }

  @Test
  void fallenShamanFireballKeyframeCreatesResolvedServerMissile() {
    MonStats.Entry row = Riiablo.files.monstats.get("fallenshaman3");
    assertNotNull(row);
    Skills.Entry skill = Riiablo.files.skills.get(row.Skill2);
    assertNotNull(skill);
    Scenario scenario = new Scenario(row, new ServerSkillSystem(true));
    try {
      int target = scenario.target(16, 10);
      scenario.actioneer.cast(scenario.source, skill.Id, target, scenario.position(16, 10));
      scenario.keyframe(scenario.source);
      assertEquals(1, scenario.factory.missilesCreated);
      assertTrue(scenario.factory.lastMissile != null
          && scenario.factory.lastMissile.toLowerCase().startsWith("shafire"),
          "Fallen Shaman must create a ShamanFire missile");
      assertEquals(scenario.source, scenario.factory.lastMissileOwner);
      assertEquals(1, scenario.probe.skillDo);
      System.out.println("[ACT1_ECS_CHAIN] skill=ShamanFire missile="
          + scenario.factory.lastMissile + " owner=" + scenario.source + " status=PASS");
    } finally {
      scenario.close();
    }
  }

  @Test
  void missileCollisionDispatchesDamageAndDeath() {
    Probe probe = new Probe();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), probe, new MissileCollisionSystem()).build());
    try {
      int owner = world.create();
      world.getMapper(com.riiablo.engine.server.component.Player.class).create(owner);
      world.getMapper(Position.class).create(owner).position.set(10, 10);
      world.getMapper(AttributesWrapper.class).create(owner).attrs =
          combatAttributes(100, 100, 20, 20, 10000, 1);

      int target = world.create();
      world.getMapper(Monster.class).create(target);
      world.getMapper(Position.class).create(target).position.set(10, 10);
      Attributes targetAttrs = combatAttributes(5, 5, 0, 0, 1, 1);
      world.getMapper(AttributesWrapper.class).create(target).attrs = targetAttrs;

      com.riiablo.codec.excel.Missiles.Entry missileRow = Riiablo.files.Missiles.get("shafire3");
      assertNotNull(missileRow);
      int missile = world.create();
      world.getMapper(Missile.class).create(missile)
          .set(missileRow, new Vector2(0, 0), 100).setOwner(owner);
      world.getMapper(Position.class).create(missile).position.set(10, 10);
      world.getMapper(com.riiablo.engine.server.component.Velocity.class).create(missile)
          .velocity.setZero();
      MathUtils.random.setSeed(0xC0111DEL);
      world.setDelta(1f / 60f);
      world.process();

      assertEquals(0f, hitpoints(targetAttrs), 0.001f);
      assertEquals(1, probe.damage);
      assertEquals(1, probe.death);
      assertTrue(!world.getMapper(Missile.class).has(missile),
          "a missile must be removed after its first resolved collision");
      System.out.println("[ACT1_ECS_CHAIN] system=MissileCollision missile=" + missile
          + " damageEvents=1 deathEvents=1 targetHp=0 status=PASS");
    } finally {
      world.dispose();
    }
  }

  private static final class Scenario {
    final Probe probe = new Probe();
    final TestFactory factory = new TestFactory();
    final Actioneer actioneer = new Actioneer();
    final World world;
    final int source;

    Scenario(MonStats.Entry row, BaseSystem... extras) {
      WorldConfigurationBuilder builder = new WorldConfigurationBuilder()
          .with(new EventSystem(), probe, actioneer, new Pathfinder(), factory);
      if (extras != null) for (BaseSystem extra : extras) if (extra != null) builder.with(extra);
      world = new World(builder.build().register("map", new WalkableMap())
          .register("factory", factory));
      factory.world = world;
      source = world.create();
      MonStats2.Entry row2 = Riiablo.files.monstats2.get(row.MonStatsEx);
      world.getMapper(Monster.class).create(source).set(row, row2);
      world.getMapper(Class.class).create(source).type = Class.Type.MON;
      world.getMapper(Position.class).create(source).position.set(10, 10);
      world.getMapper(Angle.class).create(source);
      world.getMapper(MovementModes.class).create(source).set(
          Engine.Monster.MODE_NU, Engine.Monster.MODE_WL, Engine.Monster.MODE_RN);
      world.getMapper(AttributesWrapper.class).create(source).attrs = attributes(100, 100);
      world.getMapper(UnitStates.class).create(source).init(source);
    }

    AttributesWrapper attributes(int entity) {
      return world.getMapper(AttributesWrapper.class).get(entity);
    }

    Attributes attributes(float current, float max) {
      Attributes attrs = Attributes.obtainStandard();
      attrs.base().put(Stat.hitpoints, current);
      attrs.base().put(Stat.maxhp, max);
      attrs.base().put(Stat.level, 1);
      attrs.reset();
      return attrs;
    }

    int target(float x, float y) {
      return target(x, y, attributes(100, 100), false);
    }

    int target(float x, float y, Attributes attrs, boolean player) {
      int id = world.create();
      if (player) world.getMapper(com.riiablo.engine.server.component.Player.class).create(id);
      world.getMapper(Class.class).create(id).type = player ? Class.Type.PLR : Class.Type.MON;
      world.getMapper(Position.class).create(id).position.set(x, y);
      if (!player) world.getMapper(Monster.class).create(id);
      world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
      world.getMapper(UnitStates.class).create(id).init(id);
      return id;
    }

    Vector2 position(float x, float y) {
      return new Vector2(x, y);
    }

    void keyframe(int entity) {
      world.getSystem(EventSystem.class).dispatch(
          AnimDataKeyframeEvent.obtain(entity, Engine.KEYFRAME_ATK));
    }

    void close() {
      world.dispose();
    }
  }

  private static final class Probe extends BaseSystem {
    int skillDo;
    int damage;
    int death;
    @Subscribe public void onSkillDo(SkillDoEvent event) { skillDo++; }
    @Subscribe public void onDamage(DamageEvent event) { damage++; }
    @Subscribe public void onDeath(DeathEvent event) { death++; }
    @Override protected void processSystem() {}
  }

  private static final class TestFactory extends EntityFactory {
    World world;
    int monstersCreated;
    int missilesCreated;
    String lastMonster;
    float lastMonsterX;
    float lastMonsterY;
    String lastMissile;
    int lastMissileOwner = -1;

    @Override public int createPlayer(CharData data, Vector2 position) { return -1; }
    @Override public int createDynamicObject(int act, int preset, float x, float y) { return -1; }
    @Override public int createStaticObject(int act, int object, float x, float y) { return -1; }
    @Override public int createStaticObjectByClassId(int object, float x, float y) { return -1; }
    @Override public int createMonster(int monster, float x, float y) {
      monstersCreated++;
      MonStats.Entry row = Riiablo.files.monstats.get(monster);
      lastMonster = row != null ? row.Id : null;
      lastMonsterX = x;
      lastMonsterY = y;
      return 9000 + monstersCreated;
    }
    @Override public int createWarp(int index, float x, float y) { return -1; }
    @Override public int createItem(Item item, float x, float y) { return -1; }
    @Override public int createMissile(int missile, Vector2 angle, Vector2 position) {
      missilesCreated++;
      int id = world.create();
      com.riiablo.codec.excel.Missiles.Entry row = Riiablo.files.Missiles.get(missile);
      lastMissile = row != null ? row.Missile : null;
      world.getMapper(Missile.class).create(id).set(row, position, row != null ? row.Range : 0);
      world.getMapper(Position.class).create(id).position.set(position);
      world.getMapper(com.riiablo.engine.server.component.Velocity.class).create(id)
          .velocity.set(angle);
      return id;
    }
    @Override public int createMissile(int missile, Vector2 angle, Vector2 position, int ownerId) {
      missilesCreated++;
      int id = world.create();
      com.riiablo.codec.excel.Missiles.Entry row = Riiablo.files.Missiles.get(missile);
      lastMissile = row != null ? row.Missile : null;
      lastMissileOwner = ownerId;
      world.getMapper(Missile.class).create(id).set(row, position, row != null ? row.Range : 0)
          .setOwner(ownerId);
      world.getMapper(Position.class).create(id).position.set(position);
      world.getMapper(com.riiablo.engine.server.component.Velocity.class).create(id)
          .velocity.set(angle);
      return id;
    }
  }

  private static Skills.Entry skillFor(MonStats.Entry row, int srvdofunc) {
    String[] names = {row.Skill1, row.Skill2, row.Skill3, row.Skill4,
        row.Skill5, row.Skill6, row.Skill7, row.Skill8};
    for (String name : names) {
      if (name == null || name.isEmpty()) continue;
      Skills.Entry skill = Riiablo.files.skills.get(name);
      if (skill != null && skill.srvdofunc == srvdofunc) return skill;
    }
    return null;
  }

  private static MonStats.Entry findMonsterWithSkill(String skillName) {
    for (MonStats.Entry row : Riiablo.files.monstats) {
      String[] names = {row.Skill1, row.Skill2, row.Skill3, row.Skill4,
          row.Skill5, row.Skill6, row.Skill7, row.Skill8};
      for (String name : names) if (skillName.equalsIgnoreCase(name)) return row;
    }
    return null;
  }

  private static Attributes combatAttributes(float hp, float maxHp,
      int minDamage, int maxDamage, int attackRating, int level) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, maxHp);
    attrs.base().put(Stat.level, level);
    attrs.base().put(Stat.armorclass, 0);
    attrs.base().put(Stat.mindamage, minDamage);
    attrs.base().put(Stat.maxdamage, maxDamage);
    attrs.base().put(Stat.tohit, attackRating);
    attrs.reset();
    return attrs;
  }

  private static float hitpoints(Attributes attrs) {
    com.riiablo.attributes.StatRef hp = attrs.get(Stat.hitpoints,
        com.riiablo.attributes.StatRef.obtain());
    return hp != null ? hp.asFixed() : 0f;
  }

  /** Minimal walkable map so the real Nest placement search can run headless. */
  private static final class WalkableMap extends Map {
    WalkableMap() { super(0, 0); }
    @Override public int flags(int x, int y) { return 0; }
  }
}
