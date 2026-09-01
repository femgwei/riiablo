package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.CharacterClass;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.ai.AI;
import com.riiablo.engine.server.ai.FallenShaman;
import com.riiablo.engine.server.combat.CombatSystem;
import com.riiablo.engine.server.component.AIWrapper;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Size;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.event.AnimDataKeyframeEvent;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** End-to-end headless regression for automatic combat followed by Shaman resurrection. */
class FallenShamanAutoCombatIntegrationTest extends RiiabloTest {
  @Test
  void autoCombatKillsFallenThenShamanResurrectsIt() throws Exception {
    MathUtils.random.setSeed(0xFA11EL);
    MonStats.Entry shamanRow = Riiablo.files.monstats.get("fallenshaman1");
    MonStats.Entry fallenRow = Riiablo.files.monstats.get("fallen1");
    assertNotNull(shamanRow);
    assertNotNull(fallenRow);
    MonStats2.Entry shamanStats2 = Riiablo.files.monstats2.get(shamanRow.MonStatsEx);
    MonStats2.Entry fallenStats2 = Riiablo.files.monstats2.get(fallenRow.MonStatsEx);
    assertNotNull(shamanStats2);
    assertNotNull(fallenStats2);

    World previousEngine = Riiablo.engine;
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new Actioneer(), new Pathfinder(),
            new CofManager(), new ServerMonsterCorpseSystem(), new AnimStepper(),
            new Probe(), new TestFactory())
        .build()
        .register("map", new Map(0, 0)));
    TestFactory factory = world.getSystem(TestFactory.class);
    Riiablo.engine = world;
    try {
      int player = createPlayer(world);
      int fallen = createMonster(world, fallenRow, fallenStats2, 12, 10, 24);
      int shaman = createMonster(world, shamanRow, shamanStats2, 10, 10, 100);

      // Run the same pure authoritative hit resolver used by ECS attacks until
      // the Fallen reaches zero HP.  A high attack rating makes this bounded
      // while retaining native hit/crit/random handling.
      CombatSystem.AttackerData attacker = new CombatSystem.AttackerData();
      attacker.entityId = player;
      attacker.isPlayer = true;
      attacker.level = 1;
      attacker.attackRating = 10_000;
      attacker.minDamage = 8;
      attacker.maxDamage = 8;
      CombatSystem.DefenderData defender = new CombatSystem.DefenderData();
      defender.entityId = fallen;
      defender.isMonster = true;
      defender.level = 1;
      defender.defense = 0;
      defender.currentLife = 24;
      defender.maxLife = 24;

      int attacks = 0;
      int hits = 0;
      Attributes fallenAttrs = world.getMapper(AttributesWrapper.class).get(fallen).attrs;
      while (defender.currentLife > 0 && attacks++ < 32) {
        CombatSystem.CombatResult result = CombatSystem.INSTANCE.calculateAttack(attacker, defender);
        if (!result.hit || result.blocked) continue;
        hits++;
        defender.currentLife = Math.max(0, defender.currentLife - result.totalDamage);
        fallenAttrs.get(Stat.hitpoints, StatRef.obtain()).set(defender.currentLife);
      }
      assertTrue(hits > 0, "automatic attack loop must produce a hit");
      assertEquals(0f, hitpoints(fallenAttrs), 0.001f);
      world.getSystem(EventSystem.class).dispatch(DeathEvent.obtain(player, fallen));

      // MODE_DD is the native terminal death mode.  The authoritative corpse
      // system creates the usable corpse only after this animation completes.
      world.getMapper(CofReference.class).create(fallen)
          .set(fallenRow.Code, Engine.Monster.MODE_DD);
      world.getSystem(EventSystem.class).dispatch(
          com.riiablo.engine.server.event.ModeChangeEvent.obtain(fallen, Engine.Monster.MODE_DD));
      assertTrue(world.getMapper(Corpse.class).has(fallen), "MODE_DD must create a corpse");

      AI ai = AI.findAI(shaman, shamanRow.AI);
      world.getInjector().inject(ai);
      ai.initialize();
      // Native data controls this chance.  Force the deterministic branch for
      // this smoke test so a random low roll cannot hide a wiring regression.
      Field params = AI.class.getDeclaredField("params");
      params.setAccessible(true);
      int[] values = (int[]) params.get(ai);
      values[0] = 100; // native aip1: resurrect/command chance
      values[3] = 15;  // native aip4: resurrection distance
      ai.update(1f);
      assertTrue(world.getMapper(com.riiablo.engine.server.component.Casting.class).has(shaman),
          "Shaman AI must start the native Resurrect cast");
      com.riiablo.engine.server.component.Casting casting =
          world.getMapper(com.riiablo.engine.server.component.Casting.class).get(shaman);
      assertEquals(fallen, casting.targetId, "Shaman must target the Fallen corpse");
      assertEquals(Riiablo.files.skills.get(shamanRow.Skill1).Id, casting.skillId,
          "Shaman must use MonStats.Skill1 (native Resurrect)");

      AnimData anim = world.getMapper(AnimData.class).create(shaman);
      anim.speed = 128;
      anim.frame = 0;
      anim.numFrames = 512;
      anim.keyframes = new byte[] {Engine.KEYFRAME_ATK};
      world.getSystem(EventSystem.class).dispatch(
          AnimDataKeyframeEvent.obtain(shaman, Engine.KEYFRAME_ATK));

      assertEquals(1, factory.resurrections);
      assertEquals(24f, hitpoints(fallenAttrs), 0.001f);
      assertTrue(!world.getMapper(Corpse.class).has(fallen),
          "successful resurrection must consume the corpse marker");
      System.out.println("[FALLEN_SHAMAN_AUTO_COMBAT] attacks=" + attacks
          + " hits=" + hits + " fallen=" + fallen + " shaman=" + shaman
          + " resurrected=true status=PASS");
    } finally {
      Riiablo.engine = previousEngine;
      world.dispose();
    }
  }

  private static int createPlayer(World world) {
    int id = world.create();
    world.getMapper(Player.class).create(id).data = CharData.obtain().clear()
        .set(Riiablo.NORMAL, false, "AutoCombat", (byte) CharacterClass.BARBARIAN.id);
    world.getMapper(Class.class).create(id).type = Class.Type.PLR;
    world.getMapper(Position.class).create(id).position.set(15, 10);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(100, 100);
    return id;
  }

  private static int createMonster(World world, MonStats.Entry row, MonStats2.Entry stats2,
      float x, float y, float hp) {
    int id = world.create();
    world.getMapper(Monster.class).create(id).set(row, stats2);
    world.getMapper(Class.class).create(id).type = Class.Type.MON;
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(Angle.class).create(id);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(hp, hp);
    world.getMapper(Size.class).create(id).size = 1;
    world.getMapper(Velocity.class).create(id).setMonster(row.Velocity);
    return id;
  }

  private static Attributes attributes(float hp, float maxHp) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, maxHp);
    attrs.base().put(Stat.level, 1);
    attrs.reset();
    return attrs;
  }

  private static float hitpoints(Attributes attrs) {
    StatRef hp = attrs.get(Stat.hitpoints, StatRef.obtain());
    return hp != null ? hp.asFixed() : 0f;
  }

  private static final class Probe extends BaseSystem {
    @Override protected void processSystem() {}
  }

  private static final class TestFactory extends EntityFactory {
    int resurrections;

    @Override public boolean resurrectMonster(int monsterId, int sourceId) {
      if (!world.getMapper(Corpse.class).has(monsterId)) return false;
      Attributes attrs = world.getMapper(AttributesWrapper.class).get(monsterId).attrs;
      attrs.get(Stat.hitpoints, StatRef.obtain()).set(attrs.get(Stat.maxhp).asFixed());
      world.getMapper(Corpse.class).remove(monsterId);
      resurrections++;
      return true;
    }

    @Override public int createPlayer(CharData data, Vector2 position) { return -1; }
    @Override public int createDynamicObject(int act, int preset, float x, float y) { return -1; }
    @Override public int createStaticObject(int act, int object, float x, float y) { return -1; }
    @Override public int createStaticObjectByClassId(int object, float x, float y) { return -1; }
    @Override public int createMonster(int monster, float x, float y) { return -1; }
    @Override public int createWarp(int index, float x, float y) { return -1; }
    @Override public int createItem(com.riiablo.item.Item item, float x, float y) { return -1; }
    @Override public int createMissile(int missileId, Vector2 angle, Vector2 position) { return -1; }
  }
}
