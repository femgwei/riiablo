package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.combat.CombatSystem;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.skill.SorceressSkills;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Headless authoritative coverage for D2MOO SrvDo020. */
class SorceressStaticFieldIntegrationTest extends RiiabloTest {
  @Test
  void nativeIntegerLifeAndDifficultyFloorArithmeticIsPreserved() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.STATIC_FIELD);
    assertNotNull(skill);
    assertTrue(SorceressSkills.isStaticField(skill));
    assertEquals(25, SorceressSkills.getStaticFieldDamagePercent(skill, 1));
    assertEquals(0, SorceressSkills.getStaticFieldMinimumDamageFixed(skill, 1));
    assertEquals(25 << 8, SorceressSkills.calculateStaticFieldRawDamageFixed(
        100 << 8, 100 << 8, 25, 0, 0));
    assertEquals(0, SorceressSkills.calculateStaticFieldRawDamageFixed(
        1 << 8, 100 << 8, 25, 0, 0), "Static Field cannot remove the last life");
    assertEquals(8 << 8, SorceressSkills.calculateStaticFieldRawDamageFixed(
        34 << 8, 100 << 8, 25, 0, 33),
        "the native floor gates a cast but does not clamp its post-hit life");
    assertEquals(0, SorceressSkills.calculateStaticFieldRawDamageFixed(
        33 << 8, 100 << 8, 25, 0, 33));
  }

  @Test
  void normalHitsEveryValidTargetAndUsesResistanceAbsorbAndNoMissiles() {
    try (Harness test = new Harness(0)) {
      int caster = test.player(0, 0, true);
      int plain = test.monster(4, 0, 100, 0);
      int resisted = test.monster(4, 1, 100, 50);
      int negative = test.monster(4, -1, 100, -50);
      int immune = test.monster(3, 1, 100, 100);
      int absorbing = test.monster(3, -1, 50, 0);
      Attributes absorbingAttrs = test.attrs(absorbing);
      absorbingAttrs.base().put(Stat.maxhp, 100);
      absorbingAttrs.base().put(Stat.item_absorblight_percent, 50);
      absorbingAttrs.reset();

      test.cast(caster);

      assertEquals(75f, test.hp(plain), 0.001f);
      assertEquals(87.5f, test.hp(resisted), 0.001f);
      assertEquals(75f, test.hp(negative), 0.01f,
          "native negative-resistance compensation prevents amplified Static Field damage");
      assertEquals(100f, test.hp(immune), 0.001f);
      assertEquals(50f, test.hp(absorbing), 0.001f,
          "50% absorb heals the same amount as the reduced packet removes");
      assertEquals(0, test.world.getAspectSubscriptionManager()
          .get(Aspect.all(Missile.class)).getEntities().size(),
          "SrvDo020 is immediate damage and must not create a missile entity");
    }
  }

  @Test
  void expansionDifficultyThresholdIsAnEligibilityGate() {
    try (Harness nightmare = new Harness(1)) {
      int caster = nightmare.player(0, 0, true);
      int justAbove = nightmare.monster(2, 0, 34, 0);
      Attributes attrs = nightmare.attrs(justAbove);
      attrs.base().put(Stat.maxhp, 100);
      attrs.reset();
      int atFloor = nightmare.monster(2, 1, 33, 0);
      nightmare.attrs(atFloor).base().put(Stat.maxhp, 100);
      nightmare.attrs(atFloor).reset();

      nightmare.cast(caster);
      assertEquals(26f, nightmare.hp(justAbove), 0.001f);
      assertEquals(33f, nightmare.hp(atFloor), 0.001f);
      nightmare.cast(caster);
      assertEquals(26f, nightmare.hp(justAbove), 0.001f);
    }
    try (Harness hell = new Harness(2)) {
      int caster = hell.player(0, 0, true);
      int atFloor = hell.monster(2, 0, 50, 0);
      hell.attrs(atFloor).base().put(Stat.maxhp, 100);
      hell.attrs(atFloor).reset();
      hell.cast(caster);
      assertEquals(50f, hell.hp(atFloor), 0.001f);
    }
    try (Harness classic = new Harness(2)) {
      int caster = classic.player(0, 0, false);
      int belowExpansionFloor = classic.monster(2, 0, 40, 0);
      classic.attrs(belowExpansionFloor).base().put(Stat.maxhp, 100);
      classic.attrs(belowExpansionFloor).reset();
      classic.cast(caster);
      assertEquals(30f, classic.hp(belowExpansionFloor), 0.001f,
          "classic games do not apply the expansion StaticFieldMin cap");
    }
  }

  @Test
  void filterSkipsRangeTownZoneCorpseNpcOwnedAndConvertedUnits() {
    try (Harness test = new Harness(0)) {
      int caster = test.player(0, 0, true);
      int outside = test.monster(6, 0, 100, 0);
      int corpse = test.monster(2, 0, 100, 0);
      test.world.getMapper(Corpse.class).create(corpse);
      int npc = test.monster(2, 1, 100, 0);
      MonStats.Entry npcRow = new MonStats.Entry();
      npcRow.npc = true;
      test.world.getMapper(Monster.class).get(npc).monstats = npcRow;
      int pet = test.monster(2, -1, 100, 0);
      test.world.getMapper(SummonedPet.class).create(pet).ownerId = caster;
      int converted = test.monster(3, 0, 100, 0);
      test.world.getMapper(Monster.class).get(converted).converted = true;
      test.world.getMapper(Monster.class).get(converted).conversionOwnerId = caster;
      int town = test.monster(3, 1, 100, 0);
      Map.Zone townZone = new Map.Zone() {
        @Override public boolean isTown() { return true; }
      };
      test.zone(town, townZone);
      int otherZone = test.monster(3, -1, 100, 0);
      test.zone(caster, new Map.Zone());
      test.zone(otherZone, new Map.Zone());

      test.cast(caster);

      assertEquals(100f, test.hp(outside));
      assertEquals(100f, test.hp(corpse));
      assertEquals(100f, test.hp(npc));
      assertEquals(100f, test.hp(pet));
      assertEquals(100f, test.hp(converted));
      assertEquals(100f, test.hp(town));
      assertEquals(100f, test.hp(otherZone));
    }
  }

  @Test
  void hostilePlayerReceivesNativePvpScalarWhileNeutralPlayerIsIgnored() {
    try (Harness test = new Harness(0)) {
      int caster = test.player(0, 0, true);
      int hostile = test.player(2, 0, true);
      int neutral = test.player(3, 0, true);
      assertTrue(test.parties.declareHostility(caster, hostile));

      test.cast(caster);

      assertEquals(95.75f, test.hp(hostile), 0.001f);
      assertEquals(100f, test.hp(neutral), 0.001f);
    }
  }

  private static final class Harness implements AutoCloseable {
    final PartyManager parties = new PartyManager();
    final NoopFactory factory = new NoopFactory();
    final ServerSkillSystem skills = new ServerSkillSystem(true);
    final Map map;
    final World world;

    Harness(int difficulty) {
      map = new Map(0, difficulty);
      world = new World(new WorldConfigurationBuilder()
          .with(new EventSystem(), skills, factory).build()
          .register("factory", factory)
          .register("map", map)
          .register("partyManager", parties));
    }

    int player(float x, float y, boolean expansion) {
      int id = unit(x, y, 100, 0);
      CharData data = CharData.createRemote("sorceress-" + id, (byte) Riiablo.SORCERESS);
      data.setSkillLevel(SkillId.STATIC_FIELD, 1);
      if (!expansion) data.flags = 0;
      world.getMapper(Player.class).create(id).data = data;
      return id;
    }

    int monster(float x, float y, float life, int lightningResistance) {
      int id = unit(x, y, life, lightningResistance);
      MonStats.Entry row = Riiablo.files.monstats.get("fallen1");
      Monster monster = world.getMapper(Monster.class).create(id);
      monster.monstats = row;
      monster.monstats2 = row != null ? Riiablo.files.monstats2.get(row.MonStatsEx) : null;
      return id;
    }

    int unit(float x, float y, float life, int lightningResistance) {
      int id = world.create();
      world.getMapper(Position.class).create(id).position.set(x, y);
      world.getMapper(AttributesWrapper.class).create(id).attrs =
          attributes(life, lightningResistance);
      world.getMapper(UnitStates.class).create(id).init(id);
      return id;
    }

    void cast(int caster) {
      Skills.Entry skill = Riiablo.files.skills.get(SkillId.STATIC_FIELD);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          caster, skill.Id, Engine.INVALID_ENTITY, Vector2.Zero,
          skill.srvdofunc, skill.cltdofunc));
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

  private static Attributes attributes(float life, int lightningResistance) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.level, 20);
    attrs.base().put(Stat.hitpoints, life);
    attrs.base().put(Stat.maxhp, life);
    attrs.base().put(Stat.mana, 100);
    attrs.base().put(Stat.maxmana, 100);
    attrs.base().put(Stat.lightresist, lightningResistance);
    attrs.reset();
    return attrs;
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
