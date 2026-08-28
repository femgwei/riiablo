package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.CharacterClass;
import com.riiablo.Colors;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.ExperienceManager;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.client.DeathHandler;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.MovementModes;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.PlayerCorpse;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import org.junit.jupiter.api.Test;

/**
 * Real ECS progression chain: lethal missile -> death -> XP/level/skill point
 * and loot, followed by player corpse creation and town respawn.
 */
class CombatDeathProgressionEcsScenarioTest extends RiiabloTest {
  @Test
  void lethalCombatAwardsProgressionLootAndRespawnsPlayer() {
    if (Riiablo.colors == null) Riiablo.colors = new Colors();
    MathUtils.random.setSeed(0xD34D1234L);
    EventSystem events = new EventSystem();
    Probe probe = new Probe();
    ExperienceManager experience = new ExperienceManager();
    LootFactory factory = new LootFactory();
    DeathHandler death = new DeathHandler();
    CofManager cofs = new CofManager();
    World world = new World(new WorldConfigurationBuilder()
        .with(events, probe, experience, new DeathRewardSystem(), new ItemGenerator(),
            cofs, death, new PlayerCorpseRetrievalSystem(), factory,
            new MissileCollisionSystem())
        .build()
        .register("factory", factory)
        .register("map", new TownMap()));
    factory.world = world;
    try {
      CharData data = characterAtLevelOne();
      int player = world.create();
      world.getMapper(Player.class).create(player).data = data;
      world.getMapper(Class.class).create(player).type = Class.Type.PLR;
      world.getMapper(Position.class).create(player).position.set(20, 30);
      world.getMapper(CofReference.class).create(player).set("AM", Engine.Player.MODE_NU);
      world.getMapper(MovementModes.class).create(player).set(
          Engine.Player.MODE_TN, Engine.Player.MODE_TW, Engine.Player.MODE_RN);
      world.getMapper(Velocity.class).create(player).set(1f, 2f);
      Attributes playerAttrs = data.getStats();
      playerAttrs.base().put(Stat.mindamage, 20);
      playerAttrs.base().put(Stat.maxdamage, 20);
      playerAttrs.base().put(Stat.tohit, 10_000);
      playerAttrs.base().put(Stat.armorclass, 0);
      playerAttrs.reset();
      world.getMapper(AttributesWrapper.class).create(player).attrs = playerAttrs;

      // A deterministic boss row exercises the production reward calculators.
      MonStats.Entry boss = new MonStats.Entry();
      boss.Id = "ecs_progression_boss";
      boss.Level = new int[] {1, 1, 1};
      boss.Exp = new int[] {600, 600, 600};
      boss.boss = true;
      int monster = world.create();
      world.getMapper(Monster.class).create(monster).set(boss, new MonStats2.Entry());
      world.getMapper(Class.class).create(monster).type = Class.Type.MON;
      world.getMapper(Position.class).create(monster).position.set(20, 30);
      Attributes monsterAttrs = combatAttributes(5, 5, 0, 0, 1, 1);
      monsterAttrs.base().put(Stat.experience, 600);
      monsterAttrs.reset();
      world.getMapper(AttributesWrapper.class).create(monster).attrs = monsterAttrs;

      Missiles.Entry missileRow = Riiablo.files.Missiles.get("shafire3");
      assertNotNull(missileRow);
      int missile = world.create();
      world.getMapper(Missile.class).create(missile)
          .set(missileRow, new Vector2(), 100).setOwner(player);
      world.getMapper(Position.class).create(missile).position.set(20, 30);
      world.getMapper(Velocity.class).create(missile).velocity.setZero();
      world.setDelta(1f / 60f);

      long expBefore = data.getStats().aggregate().getValue(Stat.experience, 0L);
      int levelBefore = data.getStats().aggregate().getValue(Stat.level, 0);
      int skillPointsBefore = data.getStats().aggregate().getValue(Stat.newskills, 0);
      world.process();

      assertEquals(0f, hitpoints(monsterAttrs), 0.001f);
      assertEquals(1, probe.damageEvents);
      assertEquals(1, probe.deathEvents);
      assertTrue(data.getStats().aggregate().getValue(Stat.experience, 0L) > expBefore);
      assertEquals(2, data.getStats().aggregate().getValue(Stat.level, 0));
      assertEquals(levelBefore + 1, data.level & 0xFF);
      assertEquals(skillPointsBefore + 1,
          data.getStats().aggregate().getValue(Stat.newskills, 0));
      assertTrue(factory.itemsCreated >= 4, "boss death must create guaranteed loot");
      float respawnHp = playerAttrs.aggregate().getValue(Stat.maxhp, 0f);
      assertTrue(respawnHp >= 60f && respawnHp < 100f,
          "level-up max life must remain in fixed-point life units");
      System.out.println("[DEATH_ECS_CHAIN] phase=progression player=" + player
          + " monster=" + monster + " exp=" + expBefore + "->"
          + data.getStats().aggregate().getValue(Stat.experience, 0L)
          + " level=" + levelBefore + "->2 skillPoints="
          + data.getStats().aggregate().getValue(Stat.newskills, 0)
          + " loot=" + factory.itemsCreated);

      // Exercise the same player death handler used by GameScreen, including
      // equipment detachment and an independent corpse entity.
      Item right = data.getItems().getSlot(BodyLoc.RARM);
      Item left = data.getItems().getSlot(BodyLoc.LARM);
      assertNotNull(right);
      assertNotNull(left);
      events.dispatch(DeathEvent.obtain(player, player));
      PlayerCorpse marker = world.getMapper(PlayerCorpse.class).get(player);
      assertNotNull(marker);
      assertNull(data.getItems().getSlot(BodyLoc.RARM));
      assertNull(data.getItems().getSlot(BodyLoc.LARM));
      assertEquals(0f, hitpoints(playerAttrs), 0.001f);
      assertTrue(marker.equippedItems.size >= 2);
      Vector2 deathLocation = new Vector2(marker.deathLocation);
      // Artemis publishes the newly created independent corpse to aspect
      // subscriptions at the next world tick. The dead-player guard prevents
      // retrieval during this registration tick.
      world.process();
      int corpseId = findIndependentCorpse(world, player);
      assertTrue(corpseId >= 0, "death must create an independent corpse entity");
      assertTrue(world.getMapper(PlayerCorpse.class).get(corpseId).equippedItems.size >= 2);
      int equipmentOnCorpse = marker.equippedItems.size;

      cofs.setMode(player, Engine.Player.MODE_DD, true);
      assertTrue(death.canRespawnPlayer(player));
      death.respawnPlayerAtTown(player);
      assertFalse(death.isPlayerDead(player));
      assertEquals(new Vector2(99, 101), world.getMapper(Position.class).get(player).position);
      assertEquals(respawnHp, hitpoints(playerAttrs), 0.001f);
      assertNotNull(world.getMapper(Velocity.class).get(player));
      assertTrue(world.getMapper(PlayerCorpse.class).has(corpseId),
          "the corpse and its equipment must remain at the death location after respawn");
      System.out.println("[DEATH_ECS_CHAIN] phase=respawn player=" + player
          + " deathLocation=" + deathLocation + " corpse=" + corpseId
          + " town=(99,101) hp=" + respawnHp + " equipmentOnCorpse="
          + equipmentOnCorpse + " status=PASS");
    } finally {
      world.dispose();
    }
  }

  private static CharData characterAtLevelOne() {
    CharData data = CharData.obtain().clear()
        .set(Riiablo.NORMAL, false, "EcsProgressionHero", Riiablo.AMAZON);
    data.initializeStartItems(CharacterClass.AMAZON.entry());
    data.getStats().base().put(Stat.level, 1);
    data.getStats().base().put(Stat.experience, 0);
    data.getStats().base().put(Stat.hitpoints, 60f);
    data.getStats().base().put(Stat.maxhp, 60f);
    data.getStats().base().put(Stat.mana, 20f);
    data.getStats().base().put(Stat.maxmana, 20f);
    data.getStats().base().put(Stat.stamina, 40f);
    data.getStats().base().put(Stat.maxstamina, 40f);
    data.getStats().reset();
    return data;
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
    StatRef hp = attrs.get(Stat.hitpoints, StatRef.obtain());
    return hp != null ? hp.asFixed() : 0f;
  }

  private static int findIndependentCorpse(World world, int playerId) {
    IntBag entities = world.getAspectSubscriptionManager()
        .get(Aspect.all(PlayerCorpse.class)).getEntities();
    for (int i = 0; i < entities.size(); i++) {
      int entityId = entities.get(i);
      if (entityId == playerId) continue;
      PlayerCorpse corpse = world.getMapper(PlayerCorpse.class).get(entityId);
      if (corpse != null && corpse.playerId == playerId) return entityId;
    }
    return Engine.INVALID_ENTITY;
  }

  private static final class Probe extends BaseSystem {
    int damageEvents;
    int deathEvents;
    @Subscribe public void onDamage(DamageEvent event) { damageEvents++; }
    @Subscribe public void onDeath(DeathEvent event) { deathEvents++; }
    @Override protected void processSystem() {}
  }

  private static final class LootFactory extends EntityFactory {
    World world;
    int itemsCreated;

    @Override public int createItem(Item item, float x, float y) {
      itemsCreated++;
      int id = world.create();
      System.out.println("[DEATH_ECS_CHAIN] phase=loot entity=" + id + " code=" + item.code
          + " position=(" + x + "," + y + ")");
      return id;
    }
    @Override public int createPlayer(CharData data, Vector2 position) { return -1; }
    @Override public int createDynamicObject(int act, int preset, float x, float y) { return -1; }
    @Override public int createStaticObject(int act, int object, float x, float y) { return -1; }
    @Override public int createStaticObjectByClassId(int object, float x, float y) { return -1; }
    @Override public int createMonster(int monster, float x, float y) { return -1; }
    @Override public int createWarp(int index, float x, float y) { return -1; }
    @Override public int createMissile(int missile, Vector2 angle, Vector2 position) { return -1; }
  }

  private static final class TownMap extends Map {
    TownMap() { super(0, 0); }
    @Override public Vector2 find(int id) { return new Vector2(99, 101); }
  }
}
