package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;
import org.junit.jupiter.api.Test;

/** Headless authoritative coverage for D2MOO SrvSt12/SrvDo021. */
class SorceressTelekinesisIntegrationTest extends RiiabloTest {
  @Test
  void monsterDamageUsesLightningMasteryAndCreatesNoMissile() {
    try (Harness test = new Harness()) {
      int caster = test.player(0, 0, 20, 50);
      int target = test.monster(1, 0, 100, 100, 0);

      test.cast(caster, target);

      assertEquals(70f, test.hp(target), 0.001f);
      assertEquals(1, test.damage.count);
      assertEquals(DamageEvent.MISSILE, test.damage.kind);
      assertEquals(30f, test.damage.lightning, 0.001f);
      assertEquals(0f, test.damage.physical, 0.001f);
      assertEquals(0, test.world.getAspectSubscriptionManager()
          .get(Aspect.all(Missile.class)).getEntities().size());
    }
  }

  @Test
  void resistanceImmunityAndAbsorbUseTheNativeElementalPipeline() {
    try (Harness test = new Harness()) {
      int caster = test.player(0, 0, 20, 0);
      int resisted = test.monster(1, 0, 100, 100, 50);
      int immune = test.monster(1, 1, 100, 100, 100);
      int absorbing = test.monster(1, -1, 50, 100, 0);
      test.attrs(absorbing).base().put(Stat.item_absorblight_percent, 50);
      test.attrs(absorbing).reset();

      test.cast(caster, resisted);
      test.cast(caster, immune);
      test.cast(caster, absorbing);

      assertEquals(90f, test.hp(resisted), 0.001f);
      assertEquals(100f, test.hp(immune), 0.001f);
      assertEquals(50f, test.hp(absorbing), 0.001f);
    }
  }

  @Test
  void hostilePlayerUsesPvpScalarWhileInvalidTargetsAreSkipped() {
    try (Harness test = new Harness()) {
      int caster = test.player(0, 0, 20, 0);
      int hostile = test.player(1, 0, 1, 0);
      int neutral = test.player(1, 1, 1, 0);
      assertTrue(test.parties.declareHostility(caster, hostile));
      test.cast(caster, hostile);
      test.cast(caster, neutral);
      assertEquals(97f, test.hp(hostile), 0.001f);
      assertEquals(100f, test.hp(neutral), 0.001f);

      int outside = test.monster(test.range() + 1, 0, 100, 100, 0);
      test.cast(caster, outside);
      assertEquals(100f, test.hp(outside), 0.001f);

      int town = test.monster(1, -1, 100, 100, 0);
      test.zone(caster, new Map.Zone());
      test.zone(town, new Map.Zone() {
        @Override public boolean isTown() { return true; }
      });
      test.cast(caster, town);
      assertEquals(100f, test.hp(town), 0.001f);

      int corpse = test.monster(1, -2, 100, 100, 0);
      test.world.getMapper(Corpse.class).create(corpse);
      test.cast(caster, corpse);
      assertEquals(100f, test.hp(corpse), 0.001f);

      int monsterCaster = test.monster(0, 0, 100, 100, 0);
      int monsterTarget = test.player(1, 0, 1, 0);
      test.cast(monsterCaster, monsterTarget);
      assertEquals(100f, test.hp(monsterTarget), 0.001f);
    }
  }

  private static final class Harness implements AutoCloseable {
    final PartyManager parties = new PartyManager();
    final NoopFactory factory = new NoopFactory();
    final DamageRecorder damage = new DamageRecorder();
    final ServerSkillSystem skills = new ServerSkillSystem(true);
    final Map map = new Map(0, 0);
    final World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), damage, skills, factory).build()
        .register("factory", factory)
        .register("map", map)
        .register("partyManager", parties));

    int player(float x, float y, int skillLevel, int lightningMastery) {
      int id = unit(x, y, 100, 100, 0);
      CharData data = CharData.createRemote("sorceress-" + id, (byte) Riiablo.SORCERESS);
      data.setSkillLevel(SkillId.TELEKINESIS, skillLevel);
      world.getMapper(Player.class).create(id).data = data;
      if (lightningMastery != 0) {
        attrs(id).base().put(Stat.passive_ltng_mastery, lightningMastery);
        attrs(id).reset();
      }
      return id;
    }

    int monster(float x, float y, float life, float maxLife, int lightningResistance) {
      int id = unit(x, y, life, maxLife, lightningResistance);
      MonStats.Entry row = Riiablo.files.monstats.get("fallen1");
      Monster monster = world.getMapper(Monster.class).create(id);
      monster.monstats = row;
      monster.monstats2 = row != null ? Riiablo.files.monstats2.get(row.MonStatsEx) : null;
      return id;
    }

    int unit(float x, float y, float life, float maxLife, int lightningResistance) {
      int id = world.create();
      world.getMapper(Position.class).create(id).position.set(x, y);
      world.getMapper(AttributesWrapper.class).create(id).attrs =
          attributes(life, maxLife, lightningResistance);
      world.getMapper(UnitStates.class).create(id).init(id);
      return id;
    }

    void cast(int caster, int target) {
      Skills.Entry skill = Riiablo.files.skills.get(SkillId.TELEKINESIS);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          caster, skill.Id, target, Vector2.Zero, skill.srvdofunc, skill.cltdofunc));
    }

    int range() {
      Skills.Entry skill = Riiablo.files.skills.get(SkillId.TELEKINESIS);
      return SkillFormula.evaluate(skill.aurarangecalc, skill, 20);
    }

    void zone(int entityId, Map.Zone zone) {
      world.getMapper(MapWrapper.class).create(entityId).set(map, zone);
    }

    Attributes attrs(int entityId) {
      return world.getMapper(AttributesWrapper.class).get(entityId).attrs;
    }

    float hp(int entityId) {
      return attrs(entityId).get(Stat.hitpoints).asFixed();
    }

    @Override public void close() {
      world.dispose();
    }
  }

  private static Attributes attributes(
      float life, float maxLife, int lightningResistance) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.level, 20);
    attrs.base().put(Stat.hitpoints, life);
    attrs.base().put(Stat.maxhp, maxLife);
    attrs.base().put(Stat.mana, 100);
    attrs.base().put(Stat.maxmana, 100);
    attrs.base().put(Stat.lightresist, lightningResistance);
    attrs.reset();
    return attrs;
  }

  private static final class DamageRecorder extends PassiveSystem {
    int count;
    byte kind;
    float physical;
    float lightning;

    @Subscribe public void onDamage(DamageEvent event) {
      count++;
      kind = event.kind;
      physical = event.physicalDamage;
      lightning = event.lightningDamage;
    }
  }

  private static final class NoopFactory extends EntityFactory {
    @Override public int createPlayer(CharData data, Vector2 position) {
      return Engine.INVALID_ENTITY;
    }

    @Override public int createDynamicObject(int act, int id, float x, float y) {
      return Engine.INVALID_ENTITY;
    }

    @Override public int createStaticObject(int act, int id, float x, float y) {
      return Engine.INVALID_ENTITY;
    }

    @Override public int createStaticObjectByClassId(int id, float x, float y) {
      return Engine.INVALID_ENTITY;
    }

    @Override public int createMonster(int id, float x, float y) {
      return Engine.INVALID_ENTITY;
    }

    @Override public int createWarp(int index, float x, float y) {
      return Engine.INVALID_ENTITY;
    }

    @Override public int createItem(Item item, float x, float y) {
      return Engine.INVALID_ENTITY;
    }

    @Override public int createMissile(int id, Vector2 angle, Vector2 position) {
      return Engine.INVALID_ENTITY;
    }

    @Override public int createMissile(
        int id, Vector2 angle, Vector2 position, int ownerId) {
      return Engine.INVALID_ENTITY;
    }
  }
}
