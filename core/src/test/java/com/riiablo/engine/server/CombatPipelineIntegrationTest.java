package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
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
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.MovementModes;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.event.AnimDataKeyframeEvent;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import com.riiablo.skill.SkillCodes;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Headless tests for player actions through the real ECS combat pipeline. */
class CombatPipelineIntegrationTest extends RiiabloTest {
  private CharData previousCharData;

  @AfterEach
  void restoreGlobalCharacter() {
    if (previousCharData != null) Riiablo.charData = previousCharData;
  }

  @Test
  void playerMeleeAttackKeyframeDamagesAndKillsMonster() {
    MathUtils.random.setSeed(0x5A4A4EL);
    Harness harness = new Harness(false);
    try {
      CharData data = newCharacter(CharacterClass.BARBARIAN);
      int player = harness.createPlayer(data, 10, 10, combatAttributes(60, 8, 8, 10_000));
      int monster = harness.createMonster(12, 10, combatAttributes(8, 1, 1, 1));

      System.out.println("[PLAYER_MELEE_CHAIN] phase=script_attack attacker=" + player
          + " target=" + monster + " skill=" + SkillCodes.attack);
      harness.actioneer.cast(player, SkillCodes.attack, monster, new Vector2(12, 10));
      harness.installAttackAnimation(player);
      harness.processUntilDeath(4);

      assertTrue(harness.probe.keyframes >= 1);
      assertEquals(1, harness.probe.skillDoEvents);
      assertEquals(1, harness.probe.damageEvents);
      assertEquals(1, harness.probe.deathEvents);
      assertEquals(0f, hitpoints(harness.attributes(monster)), 0.001f);
      System.out.println("[PLAYER_MELEE_CHAIN] phase=summary keyframes="
          + harness.probe.keyframes + " skillDo=" + harness.probe.skillDoEvents
          + " damage=" + harness.probe.damageEvents + " death="
          + harness.probe.deathEvents + " targetHp="
          + hitpoints(harness.attributes(monster)));
    } finally {
      harness.dispose();
    }
  }

  @Test
  void playerThrowConsumesQuantityCreatesMissileAndDamagesMonster() {
    MathUtils.random.setSeed(0x5A4A4EL);
    previousCharData = Riiablo.charData;
    CharData data = newCharacter(CharacterClass.AMAZON);
    Riiablo.charData = data;
    Item javelin = data.getItems().getEquippedThrowableWeapon();
    assertNotNull(javelin, "Amazon starting weapon must be throwable");
    StatRef quantity = javelin.attrs.base().get(Stat.quantity, StatRef.obtain());
    assertNotNull(quantity);
    int quantityBefore = quantity.asInt();

    Harness harness = new Harness(true);
    try {
      Attributes playerAttrs = combatAttributes(60, 1, 2, 10_000);
      playerAttrs.base().put(Stat.item_throw_mindamage, 7);
      playerAttrs.base().put(Stat.item_throw_maxdamage, 7);
      playerAttrs.reset();
      int player = harness.createPlayer(data, 10, 10, playerAttrs);
      int monster = harness.createMonster(15, 10, combatAttributes(20, 1, 1, 1));
      float hpBefore = hitpoints(harness.attributes(monster));

      System.out.println("[PLAYER_THROW_CHAIN] phase=script_throw attacker=" + player
          + " target=" + monster + " weapon=" + javelin.code
          + " quantityBefore=" + quantityBefore);
      harness.actioneer.cast(player, SkillCodes.throw_, monster, new Vector2(15, 10));
      harness.installAttackAnimation(player);
      harness.processFrames(16);

      int quantityAfter = quantity.asInt();
      float hpAfter = hitpoints(harness.attributes(monster));
      assertTrue(harness.probe.keyframes >= 1);
      assertEquals(1, harness.probe.skillDoEvents);
      assertEquals(1, harness.factory.creations);
      assertEquals(quantityBefore - 1, quantityAfter);
      assertEquals(1, harness.probe.damageEvents);
      assertTrue(hpAfter < hpBefore);
      System.out.println("[PLAYER_THROW_CHAIN] phase=summary keyframes="
          + harness.probe.keyframes + " skillDo=" + harness.probe.skillDoEvents
          + " missiles=" + harness.factory.creations + " damage="
          + harness.probe.damageEvents + " quantity=" + quantityBefore + "->"
          + quantityAfter + " targetHp=" + hpBefore + "->" + hpAfter);
    } finally {
      harness.dispose();
    }
  }

  @Test
  void javelinNormalAttackRemainsMeleeAndCannotHitAtThrowRange() {
    CharData data = newCharacter(CharacterClass.AMAZON);
    assertNotNull(data.getItems().getEquippedThrowableWeapon());
    Harness harness = new Harness(true);
    try {
      int player = harness.createPlayer(data, 10, 10, combatAttributes(60, 8, 8, 10_000));
      int monster = harness.createMonster(20, 10, combatAttributes(20, 1, 1, 1));
      float before = hitpoints(harness.attributes(monster));
      harness.actioneer.cast(player, SkillCodes.attack, monster, new Vector2(20, 10));
      harness.installAttackAnimation(player);
      harness.processFrames(4);
      assertEquals(before, hitpoints(harness.attributes(monster)), 0.001f);
      assertEquals(0, harness.factory.creations);
      assertEquals(0, harness.probe.damageEvents);
      System.out.println("[WEAPON_ATTACK_MATRIX] weapon=javelin skill=Attack type=MELEE "
          + "distance=10 damage=0 missiles=0 status=PASS");
    } finally {
      harness.dispose();
    }
  }

  @Test
  void bowNormalAttackCreatesArrowAndDamagesAtRange() {
    CharData data = newCharacter(CharacterClass.AMAZON);
    data.getItems().unequipItem(BodyLoc.RARM);
    data.getItems().unequipItem(BodyLoc.LARM);
    Item bow = new Item();
    bow.reset();
    bow.setBase(Riiablo.files.weapons.get("sbw"));
    data.getItems().equipItem(BodyLoc.RARM, data.getItems().add(bow));
    Harness harness = new Harness(true);
    try {
      int player = harness.createPlayer(data, 10, 10, combatAttributes(60, 8, 8, 10_000));
      int monster = harness.createMonster(15, 10, combatAttributes(20, 1, 1, 1));
      float before = hitpoints(harness.attributes(monster));
      harness.actioneer.cast(player, SkillCodes.attack, monster, new Vector2(15, 10));
      harness.installAttackAnimation(player);
      harness.processFrames(32);
      assertEquals(1, harness.factory.creations);
      assertEquals(1, harness.probe.damageEvents);
      assertTrue(hitpoints(harness.attributes(monster)) < before);
      System.out.println("[WEAPON_ATTACK_MATRIX] weapon=bow skill=Attack type=RANGED "
          + "missile=arrow damageEvents=1 status=PASS");
    } finally {
      harness.dispose();
    }
  }

  private static CharData newCharacter(CharacterClass clazz) {
    CharData data = CharData.obtain().clear()
        .set(Riiablo.NORMAL, false, "CombatHero", (byte) clazz.id);
    data.mapSeed = 0x434F4D42 + clazz.id;
    data.initializeStartItems(clazz.entry());
    return data;
  }

  private static Attributes combatAttributes(float hp, int minDamage, int maxDamage,
      int attackRating) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, hp);
    attrs.base().put(Stat.mana, 100);
    attrs.base().put(Stat.maxmana, 100);
    attrs.base().put(Stat.mindamage, minDamage);
    attrs.base().put(Stat.maxdamage, maxDamage);
    attrs.base().put(Stat.tohit, attackRating);
    attrs.base().put(Stat.level, 1);
    attrs.base().put(Stat.armorclass, 0);
    attrs.reset();
    return attrs;
  }

  private static float hitpoints(Attributes attrs) {
    StatRef hp = attrs.get(Stat.hitpoints, StatRef.obtain());
    return hp != null ? hp.asFixed() : 0f;
  }

  private static final class Harness {
    final RecordingFactory factory = new RecordingFactory();
    final Actioneer actioneer = new Actioneer();
    final Probe probe = new Probe();
    final World world;

    Harness(boolean missiles) {
      WorldConfigurationBuilder builder = new WorldConfigurationBuilder()
          .with(new EventSystem(), probe, actioneer, new Pathfinder(), new AnimStepper());
      if (missiles) builder.with(new ServerSkillSystem(), factory, new MissileCollisionSystem());
      else builder.with(factory);
      world = new World(builder.build()
          .register("factory", factory)
          .register("map", new Map(0, 0)));
    }

    int createPlayer(CharData data, float x, float y, Attributes attrs) {
      int id = world.create();
      world.getMapper(Player.class).create(id).data = data;
      world.getMapper(Class.class).create(id).type = Class.Type.PLR;
      world.getMapper(Position.class).create(id).position.set(x, y);
      world.getMapper(Angle.class).create(id);
      world.getMapper(MovementModes.class).create(id).set(
          (byte) Class.Type.PLR.getMode("NU"), (byte) Class.Type.PLR.getMode("WL"),
          (byte) Class.Type.PLR.getMode("RN"));
      world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
      return id;
    }

    int createMonster(float x, float y, Attributes attrs) {
      int id = world.create();
      world.getMapper(Monster.class).create(id);
      world.getMapper(Class.class).create(id).type = Class.Type.MON;
      world.getMapper(Position.class).create(id).position.set(x, y);
      world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
      return id;
    }

    void installAttackAnimation(int entityId) {
      AnimData anim = world.getMapper(AnimData.class).create(entityId);
      anim.speed = 128;
      anim.frame = 0;
      anim.numFrames = 512;
      anim.keyframes = new byte[] {Engine.KEYFRAME_ATK};
    }

    void processUntilDeath(int frames) {
      world.setDelta(Animation.FRAME_DURATION);
      for (int i = 0; i < frames && probe.deathEvents == 0; i++) world.process();
    }

    void processFrames(int frames) {
      world.setDelta(Animation.FRAME_DURATION);
      for (int i = 0; i < frames; i++) world.process();
    }

    Attributes attributes(int entityId) {
      return world.getMapper(AttributesWrapper.class).get(entityId).attrs;
    }

    void dispose() {
      world.dispose();
    }
  }

  private static final class Probe extends BaseSystem {
    int keyframes;
    int skillDoEvents;
    int damageEvents;
    int deathEvents;

    @Subscribe public void onKeyframe(AnimDataKeyframeEvent event) { keyframes++; }
    @Subscribe public void onSkillDo(SkillDoEvent event) { skillDoEvents++; }
    @Subscribe public void onDamage(DamageEvent event) { damageEvents++; }
    @Subscribe public void onDeath(DeathEvent event) { deathEvents++; }
    @Override protected void processSystem() {}
  }

  private static final class RecordingFactory extends EntityFactory {
    protected ComponentMapper<Missile> mMissile;
    protected ComponentMapper<Position> mPosition;
    protected ComponentMapper<Velocity> mVelocity;
    int creations;

    @Override public int createMissile(int missileId, Vector2 angle, Vector2 position) {
      return createMissile(missileId, angle, position, -1);
    }

    @Override public int createMissile(int missileId, Vector2 angle, Vector2 position,
        int ownerId) {
      Missiles.Entry missile = Riiablo.files.Missiles.get(missileId);
      int id = world.create();
      creations++;
      mMissile.create(id).set(missile, position, missile.Range).setOwner(ownerId);
      mPosition.create(id).position.set(position);
      mVelocity.create(id).velocity.set(angle).setLength(missile.Vel);
      System.out.println("[PLAYER_THROW_CHAIN] phase=missile_created entity=" + id
          + " missile=" + missile.Missile + " owner=" + ownerId);
      return id;
    }

    @Override public int createPlayer(CharData data, Vector2 position) { return -1; }
    @Override public int createDynamicObject(int act, int preset, float x, float y) { return -1; }
    @Override public int createStaticObject(int act, int object, float x, float y) { return -1; }
    @Override public int createStaticObjectByClassId(int object, float x, float y) { return -1; }
    @Override public int createMonster(int monster, float x, float y) { return -1; }
    @Override public int createWarp(int index, float x, float y) { return -1; }
    @Override public int createItem(Item item, float x, float y) { return -1; }
  }
}
