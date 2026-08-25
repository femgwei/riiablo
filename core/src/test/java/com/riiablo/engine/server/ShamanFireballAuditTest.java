package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.ComponentMapper;
import com.artemis.BaseSystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.codec.excel.Skills;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.MovementModes;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.event.AnimDataKeyframeEvent;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import com.riiablo.codec.Animation;
import com.riiablo.engine.Engine;
import org.junit.jupiter.api.Test;

/** Data-driven, headless audit for the native shaman projectile chain. */
public class ShamanFireballAuditTest extends RiiabloTest {
  @Test
  void fallenShamanSkillEventReachesMissileFactory() {
    MonStats.Entry row = Riiablo.files.monstats.get("fallenshaman3");
    assertNotNull(row);
    Skills.Entry skill = Riiablo.files.skills.get(row.Skill2);
    assertNotNull(skill);

    RecordingFactory factory = new RecordingFactory();
    ServerSkillSystem skills = new ServerSkillSystem();
    Map map = new Map(0, 0);
    WorldConfiguration config = new WorldConfigurationBuilder()
        .with(skills, factory)
        .build()
        .register("factory", factory)
        .register("map", map);
    World world = new World(config);
    try {
      int shamanId = world.create();
      MonStats2.Entry stats2 = Riiablo.files.monstats2.get(row.MonStatsEx);
      world.getMapper(Monster.class).create(shamanId).set(row, stats2);
      world.getMapper(Position.class).create(shamanId).position.set(10, 10);

      skills.onSkillDo(SkillDoEvent.obtain(shamanId, skill.Id, -1,
          new Vector2(20, 10), skill.srvdofunc, skill.cltdofunc));

      Missiles.Entry expected = Riiablo.files.Missiles.get("shafire3");
      assertNotNull(expected);
      assertEquals(expected.Id, factory.missileId);
      assertEquals(shamanId, factory.ownerId);
      assertEquals(1, factory.creations);
      System.out.println("[SHAMAN_FIREBALL_AUDIT] phase=factory_created monster=" + row.Id
          + " skill=" + skill.skill + " missile=" + expected.Missile
          + " missileId=" + factory.missileId + " owner=" + factory.ownerId);
    } finally {
      world.dispose();
    }
  }

  @Test
  void shamanMissileMovesCollidesAndDamagesPlayer() {
    MathUtils.random.setSeed(0x5A4A4EL);
    MonStats.Entry row = Riiablo.files.monstats.get("fallenshaman3");
    assertNotNull(row);
    Skills.Entry skill = Riiablo.files.skills.get(row.Skill2);
    assertNotNull(skill);

    RecordingFactory factory = new RecordingFactory();
    ServerSkillSystem skills = new ServerSkillSystem();
    MissileCollisionSystem collisions = new MissileCollisionSystem();
    Map map = new Map(0, 0);
    WorldConfiguration config = new WorldConfigurationBuilder()
        .with(new EventSystem(), skills, factory, collisions)
        .build()
        .register("factory", factory)
        .register("map", map);
    World world = new World(config);
    try {
      int shamanId = world.create();
      MonStats2.Entry stats2 = Riiablo.files.monstats2.get(row.MonStatsEx);
      world.getMapper(Monster.class).create(shamanId).set(row, stats2);
      world.getMapper(Position.class).create(shamanId).position.set(10, 10);
      Attributes shamanAttrs = combatAttributes(100, 7, 7, 10_000, 1);
      world.getMapper(AttributesWrapper.class).create(shamanId).attrs = shamanAttrs;

      int playerId = world.create();
      world.getMapper(Player.class).create(playerId);
      world.getMapper(Position.class).create(playerId).position.set(15, 10);
      Attributes playerAttrs = combatAttributes(60, 1, 2, 1, 1);
      world.getMapper(AttributesWrapper.class).create(playerId).attrs = playerAttrs;

      float hpBefore = hitpoints(playerAttrs);
      int attempts = 0;
      while (hitpoints(playerAttrs) >= hpBefore && attempts++ < 16) {
        skills.onSkillDo(SkillDoEvent.obtain(shamanId, skill.Id, playerId,
            new Vector2(15, 10), skill.srvdofunc, skill.cltdofunc));
        world.setDelta(0.1f);
        for (int frame = 0; frame < 12; frame++) world.process();
      }

      float hpAfter = hitpoints(playerAttrs);
      assertTrue(factory.creations > 0, "skill event must create a missile entity");
      assertTrue(hpAfter < hpBefore,
          "authoritative shaman missile must eventually hit and damage the player");
      assertEquals(Riiablo.files.Missiles.get("shafire3").Id, factory.missileId);
      System.out.println("[SHAMAN_PROJECTILE_SCENARIO] phase=damage_assert monster=" + row.Id
          + " owner=" + shamanId + " target=" + playerId
          + " missile=shafire3 attempts=" + attempts
          + " hpBefore=" + hpBefore + " hpAfter=" + hpAfter);
    } finally {
      world.dispose();
    }
  }

  /**
   * Full authoritative cast chain.  The script invokes Actioneer.cast, the
   * real AnimStepper emits the attack keyframe, Actioneer turns that keyframe
   * into SkillDoEvent, and the server missile then resolves DamageEvent and
   * DeathEvent through the normal collision system.
   */
  @Test
  void actioneerKeyframeProjectileDamageAndDeathScenario() {
    MathUtils.random.setSeed(0x5A4A4EL);
    MonStats.Entry row = Riiablo.files.monstats.get("fallenshaman3");
    assertNotNull(row);
    Skills.Entry skill = Riiablo.files.skills.get(row.Skill2);
    assertNotNull(skill);

    RecordingFactory factory = new RecordingFactory();
    Actioneer actioneer = new Actioneer();
    Pathfinder pathfinder = new Pathfinder();
    AnimStepper animStepper = new AnimStepper();
    ServerSkillSystem skills = new ServerSkillSystem();
    MissileCollisionSystem collisions = new MissileCollisionSystem();
    Probe probe = new Probe();
    factory.logTag = "SHAMAN_FULL_CHAIN";
    Map map = new Map(0, 0);
    WorldConfiguration config = new WorldConfigurationBuilder()
        .with(new EventSystem(), probe, actioneer, pathfinder, animStepper, skills, factory, collisions)
        .build()
        .register("factory", factory)
        .register("map", map);
    World world = new World(config);
    try {
      int shamanId = world.create();
      MonStats2.Entry stats2 = Riiablo.files.monstats2.get(row.MonStatsEx);
      world.getMapper(Monster.class).create(shamanId).set(row, stats2);
      world.getMapper(Class.class).create(shamanId).type = Class.Type.MON;
      world.getMapper(Position.class).create(shamanId).position.set(10, 10);
      world.getMapper(Angle.class).create(shamanId);
      world.getMapper(MovementModes.class).create(shamanId).set(
          (byte) Class.Type.MON.getMode("NU"), (byte) Class.Type.MON.getMode("WL"),
          (byte) Class.Type.MON.getMode("RN"));
      world.getMapper(AttributesWrapper.class).create(shamanId).attrs = combatAttributes(100, 7, 7, 10_000, 1);

      int playerId = world.create();
      world.getMapper(Player.class).create(playerId);
      world.getMapper(Position.class).create(playerId).position.set(15, 10);
      Attributes playerAttrs = combatAttributes(7, 1, 2, 1, 1);
      world.getMapper(AttributesWrapper.class).create(playerId).attrs = playerAttrs;

      // Scripted AI decision: Actioneer owns the cast state and animation.
      System.out.println("[SHAMAN_FULL_CHAIN] phase=script_cast entity=" + shamanId
          + " skill=" + skill.skill + " skillId=" + skill.Id + " target=" + playerId);
      actioneer.cast(shamanId, skill.Id, playerId, new Vector2(15, 10));

      AnimData anim = world.getMapper(AnimData.class).create(shamanId);
      anim.speed = 128;
      anim.frame = 0;
      anim.numFrames = 512;
      anim.keyframes = new byte[] {Engine.KEYFRAME_ATK};

      world.setDelta(Animation.FRAME_DURATION);
      for (int frame = 0; frame < 12 && probe.deathEvents == 0; frame++) {
        world.process();
      }

      float hpAfter = hitpoints(playerAttrs);
      System.out.println("[SHAMAN_FULL_CHAIN] phase=summary entity=" + shamanId
          + " target=" + playerId + " animKeyframes=" + probe.animKeyframes
          + " skillDo=" + probe.skillDoEvents + " missiles=" + factory.creations
          + " damage=" + probe.damageEvents + " death=" + probe.deathEvents
          + " hpAfter=" + hpAfter);
      assertTrue(probe.animKeyframes >= 1, "Actioneer must receive the attack keyframe");
      assertEquals(1, probe.skillDoEvents, "keyframe must dispatch exactly one SkillDoEvent");
      assertTrue(factory.creations > 0, "SkillDoEvent must create a server missile");
      assertEquals(1, probe.damageEvents, "missile collision must dispatch DamageEvent");
      assertEquals(1, probe.deathEvents, "lethal missile collision must dispatch DeathEvent");
      assertEquals(0f, hpAfter, 0.001f, "lethal fireball must reduce target HP to zero");
      assertEquals(Riiablo.files.Missiles.get("shafire3").Id, factory.missileId);
    } finally {
      world.dispose();
    }
  }

  @Test
  void shamanRowsResolveSkillsAndMissiles() {
    int matched = 0;
    for (MonStats.Entry row : Riiablo.files.monstats) {
      String id = row.Id == null ? "" : row.Id.toLowerCase(Locale.ROOT);
      String ai = row.AI == null ? "" : row.AI.toLowerCase(Locale.ROOT);
      String name = row.NameStr == null ? "" : row.NameStr.toLowerCase(Locale.ROOT);
      if (!(id.contains("shaman") || ai.contains("shaman") || name.contains("shaman")
          || id.contains("pygmy"))) continue;

      matched++;
      System.out.println("[SHAMAN_FIREBALL_AUDIT] monster=" + row.Id + " base=" + row.BaseId
          + " ai=" + row.AI + " skill1=" + row.Skill1 + " mode1=" + row.Sk1mode
          + " skill2=" + row.Skill2 + " mode2=" + row.Sk2mode
          + " skill3=" + row.Skill3 + " missA1=" + row.MissA1 + " missS1=" + row.MissS1);

      boolean hasProjectileSkill = false;
      String[] skillNames = {row.Skill1, row.Skill2, row.Skill3, row.Skill4,
          row.Skill5, row.Skill6, row.Skill7, row.Skill8};
      for (String skillName : skillNames) {
        if (skillName == null || skillName.isEmpty()) continue;
        Skills.Entry skill = Riiablo.files.skills.get(skillName);
        assertNotNull(skill, "missing skill row " + skillName + " for " + row.Id);
        System.out.println("[SHAMAN_FIREBALL_AUDIT] skill=" + skillName + " id=" + skill.Id
            + " srvDoFunc=" + skill.srvdofunc + " srvA=" + skill.srvmissilea
            + " srvB=" + skill.srvmissileb + " cltA=" + skill.cltmissilea
            + " cltB=" + skill.cltmissileb + " monanim=" + skill.monanim);
        String[] missiles = {skill.srvmissilea, skill.srvmissileb, skill.srvmissilec,
            skill.srvmissiled, skill.cltmissilea, skill.cltmissileb,
            skill.cltmissilec, skill.cltmissiled};
        for (String missileName : missiles) {
          if (missileName == null || missileName.isEmpty()) continue;
          hasProjectileSkill = true;
          Missiles.Entry missile = Riiablo.files.Missiles.get(missileName);
          assertNotNull(missile, "missing missile row " + missileName + " for skill " + skillName);
          System.out.println("[SHAMAN_FIREBALL_AUDIT] skill=" + skillName + " id=" + skill.Id
              + " srvDoFunc=" + skill.srvdofunc + " srvMissile=" + missileName
              + " missileId=" + missile.Id + " vel=" + missile.Vel + " range=" + missile.Range
              + " srvDmgFunc=" + missile.pSrvDmgFunc + " dmg=" + missile.DmgCalc1);
        }
      }
      if (id.contains("fallen") || id.contains("pygmy") || ai.contains("shaman")) {
        String effective = row.AI != null && row.AI.equalsIgnoreCase("FallenShaman")
            ? row.Skill2 : row.Skill1;
        Skills.Entry effectiveSkill = effective == null ? null : Riiablo.files.skills.get(effective);
        boolean effectiveProjectile = hasProjectileSkill;
        if (!effectiveProjectile && effectiveSkill != null) {
          effectiveProjectile = hasProjectile(effectiveSkill);
        }
        System.out.println("[SHAMAN_FIREBALL_AUDIT] result="
            + (effectiveProjectile ? "projectile-configured" : "NO_PROJECTILE")
            + " monster=" + row.Id + " effectiveSkill=" + effective);
        if (row.AI != null && row.AI.equalsIgnoreCase("FallenShaman")
            && "ShamanFire".equalsIgnoreCase(row.Skill2)) {
          assertTrue(effectiveProjectile, "fallen shaman fire skill cannot resolve: " + row.Id);
          Monster monster = new Monster();
          monster.monstats = row;
          String resolvedName = ServerSkillSystem.resolveMonsterChainMissile(
              monster, effectiveSkill.srvmissilea);
          Missiles.Entry resolved = Riiablo.files.Missiles.get(resolvedName);
          assertNotNull(resolved, "native shaman chain missile missing for " + row.Id);
          System.out.println("[SHAMAN_FIREBALL_AUDIT] phase=chain monster=" + row.Id
              + " chainId=" + ServerSkillSystem.getMonsterChainId(row)
              + " missile=" + resolved.Missile + " missileId=" + resolved.Id
              + " celFile=" + resolved.CelFile);
        }
      }
    }
    assertTrue(matched > 0, "D2 data did not contain any shaman rows");
  }

  private static boolean hasProjectile(Skills.Entry skill) {
    return skill != null && (hasText(skill.srvmissilea) || hasText(skill.srvmissileb)
        || hasText(skill.srvmissilec) || hasText(skill.srvmissiled)
        || hasText(skill.cltmissilea) || hasText(skill.cltmissileb)
        || hasText(skill.cltmissilec) || hasText(skill.cltmissiled));
  }

  private static boolean hasText(String value) {
    return value != null && !value.isEmpty();
  }

  private static Attributes combatAttributes(float hp, int minDamage, int maxDamage,
      int attackRating, int level) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, hp);
    attrs.base().put(Stat.mindamage, minDamage);
    attrs.base().put(Stat.maxdamage, maxDamage);
    attrs.base().put(Stat.tohit, attackRating);
    attrs.base().put(Stat.level, level);
    attrs.base().put(Stat.armorclass, 0);
    attrs.reset();
    return attrs;
  }

  private static float hitpoints(Attributes attrs) {
    StatRef hp = attrs.get(Stat.hitpoints, StatRef.obtain());
    return hp != null ? hp.asFixed() : 0f;
  }

  private static final class Probe extends BaseSystem {
    int animKeyframes;
    int skillDoEvents;
    int damageEvents;
    int deathEvents;

    @Subscribe
    public void onAnimKeyframe(AnimDataKeyframeEvent event) {
      animKeyframes++;
      System.out.println("[SHAMAN_FULL_CHAIN] phase=anim_keyframe entity=" + event.entityId
          + " keyframe=" + Engine.getKeyframe(event.keyframe));
    }

    @Subscribe
    public void onSkillDo(SkillDoEvent event) {
      skillDoEvents++;
      System.out.println("[SHAMAN_FULL_CHAIN] phase=skill_do entity=" + event.entityId
          + " skillId=" + event.skillId + " srvDoFunc=" + event.srvdofunc);
    }

    @Subscribe
    public void onDamage(DamageEvent event) {
      damageEvents++;
      System.out.println("[SHAMAN_FULL_CHAIN] phase=damage attacker=" + event.attacker
          + " victim=" + event.victim + " damage=" + event.damage);
    }

    @Subscribe
    public void onDeath(DeathEvent event) {
      deathEvents++;
      System.out.println("[SHAMAN_FULL_CHAIN] phase=death killer=" + event.killer
          + " victim=" + event.victim);
    }

    @Override protected void processSystem() {}
  }

  private static final class RecordingFactory extends EntityFactory {
    protected ComponentMapper<Missile> mMissile;
    protected ComponentMapper<Position> mPosition;
    protected ComponentMapper<Velocity> mVelocity;

    int creations;
    int missileId = -1;
    int ownerId = -1;
    String logTag = "SHAMAN_FIREBALL_AUDIT";

    @Override public int createMissile(int missileId, Vector2 angle, Vector2 position) {
      return createMissile(missileId, angle, position, -1);
    }

    @Override public int createMissile(int missileId, Vector2 angle, Vector2 position, int ownerId) {
      creations++;
      this.missileId = missileId;
      this.ownerId = ownerId;
      Missiles.Entry missile = Riiablo.files.Missiles.get(missileId);
      int entityId = world.create();
      mMissile.create(entityId).set(missile, position, missile.Range).setOwner(ownerId);
      mPosition.create(entityId).position.set(position);
      mVelocity.create(entityId).velocity.set(angle).setLength(missile.Vel);
      System.out.println("[" + logTag + "] phase=missile_created missile=" + missile.Missile
          + " missileId=" + entityId + " owner=" + ownerId + " speed=" + missile.Vel
          + " range=" + missile.Range);
      return entityId;
    }

    @Override public int createPlayer(CharData charData, Vector2 position) { return -1; }
    @Override public int createDynamicObject(int act, int monPresetId, float x, float y) { return -1; }
    @Override public int createStaticObject(int act, int objId, float x, float y) { return -1; }
    @Override public int createStaticObjectByClassId(int objectId, float x, float y) { return -1; }
    @Override public int createMonster(int monsterId, float x, float y) { return -1; }
    @Override public int createWarp(int index, float x, float y) { return -1; }
    @Override public int createItem(Item item, float x, float y) { return -1; }
  }
}
