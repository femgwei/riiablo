package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.skill.SummonEquipment;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.Quality;
import com.riiablo.save.CharData;
import java.util.ArrayList;
import java.util.List;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class SummonInitializationIntegrationTest extends RiiabloTest {
  @Test void hydraBoltsUseSummonLevelCopiedMasteryAndOwnerSynergies() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      Skills.Entry hydra = Riiablo.files.skills.get("Hydra");
      Skills.Entry attack = Riiablo.files.skills.get("HydraMissile");
      int owner = world.create();
      CharData data = CharData.createRemote("hydra-test", (byte) Riiablo.SORCERESS);
      data.setSkillLevel(Riiablo.files.skills.get("Fire Bolt").Id, 10);
      data.setSkillLevel(Riiablo.files.skills.get("Fire Ball").Id, 10);
      world.getMapper(Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(5, 5);
      Attributes attrs = attributes(80);
      attrs.base().put(Stat.passive_fire_mastery, 75);
      attrs.base().put(Stat.passive_fire_pierce, 15);
      attrs.reset();
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attrs;
      world.getMapper(UnitStates.class).create(owner).init(owner);
      int previousDamage = 0;
      for (int level : new int[] {1, 20}) {
        data.setSkillLevel(hydra.Id, level);
        world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
            owner, hydra.Id, -1, new Vector2(10, 10), hydra.srvdofunc, hydra.cltdofunc));
        int pet = factory.pets.get(factory.pets.size() - 1);
        Attributes petAttrs = world.getMapper(AttributesWrapper.class).get(pet).attrs;
        assertEquals(75, petAttrs.getValue(Stat.passive_fire_mastery, 0));
        assertEquals(15, petAttrs.getValue(Stat.passive_fire_pierce, 0));
        world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
            pet, attack.Id, -1, new Vector2(15, 10), attack.srvdofunc, attack.cltdofunc));
        Missile bolt = factory.bolts.get(factory.bolts.size() - 1);
        assertTrue(bolt.damageSnapshot);
        assertEquals(level, bolt.damageLevel);
        assertEquals(hydra.Id, bolt.skillId);
        int expectedMin = hydraDamageFixed(hydra, level, true);
        int expectedMax = hydraDamageFixed(hydra, level, false);
        assertEquals(expectedMin >> 8, bolt.damage.getValue(Stat.firemindam, 0));
        assertEquals(expectedMax >> 8, bolt.damage.getValue(Stat.firemaxdam, 0));
        assertNotNull(bolt.nativeElemental);
        assertEquals(expectedMin, bolt.nativeElemental.minimum);
        assertEquals(expectedMax, bolt.nativeElemental.maximum);
        assertEquals(15, bolt.damage.getValue(Stat.passive_fire_pierce, 0));
        assertTrue(bolt.damage.getValue(Stat.firemaxdam, 0) > previousDamage);
        previousDamage = bolt.damage.getValue(Stat.firemaxdam, 0);
      }
    } finally { world.dispose(); }
  }

  @Test void monEquipUsesSkillTiersAndResolvedGearSurvivesAttributeReset() {
    ItemGenerator generator = new ItemGenerator(Riiablo.files);
    List<Item> low = SummonEquipment.generate(Riiablo.files, generator, "valkyrie",
        1, 25, 0, null, NativeUnitRandom.monster(new Monster(), 17, 1));
    List<Item> high = SummonEquipment.generate(Riiablo.files, generator, "valkyrie",
        17, 73, 0, null, NativeUnitRandom.monster(new Monster(), 17, 1));
    assertEquals("spr", equipped(low, BodyLoc.RARM).code);
    assertEquals("ful", equipped(low, BodyLoc.TORS).code);
    assertEquals("7p7", equipped(high, BodyLoc.RARM).code);
    assertEquals("uar", equipped(high, BodyLoc.TORS).code);
    assertEquals(2, low.size());
    assertEquals(7, high.size());
    assertEquals(Quality.RARE, equipped(high, BodyLoc.RARM).quality);
    assertEquals(73, equipped(high, BodyLoc.RARM).ilvl & 255);
    Attributes pet = SummonEquipment.apply(attributes(80), high);
    int min = pet.getValue(Stat.mindamage, 0);
    int defense = pet.getValue(Stat.armorclass, 0);
    assertTrue(min > 0, "the equipped two-handed spear contributes actual attack damage");
    assertTrue(defense > 0, "equipped armor contributes actual defense");
    pet.reset();
    assertEquals(min, pet.getValue(Stat.mindamage, 0));
    assertEquals(defense, pet.getValue(Stat.armorclass, 0));
  }

  @Test void summonBaseStatsAddMonLvlWhileVinesKeepDirectLevelAndShadowsOwnerLevel() throws Exception {
    RecordingFactory factory = new RecordingFactory();
    ServerSkillSystem skills = new ServerSkillSystem(true);
    World world = new World(new WorldConfigurationBuilder().with(new EventSystem(), skills, factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int owner = world.create(), pet = world.create();
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes(80);
      Attributes attrs = attributes(1);
      attrs.base().put(Stat.tohit, 7); attrs.base().put(Stat.armorclass, 9); attrs.reset();
      world.getMapper(AttributesWrapper.class).create(pet).attrs = attrs;
      java.lang.reflect.Method apply = ServerSkillSystem.class.getDeclaredMethod(
          "applySummonBaseStats", int.class, int.class, int.class, int.class);
      apply.setAccessible(true);
      apply.invoke(skills, owner, pet, 0, 10);
      assertEquals(70, attrs.base().getValue(Stat.level, 0));
      assertEquals(7 + Riiablo.files.MonLvl.get(70).LTH[0], attrs.base().getValue(Stat.tohit, 0));
      assertEquals(9 + Riiablo.files.MonLvl.get(70).LAC[0], attrs.base().getValue(Stat.armorclass, 0));
      for (int skillId : new int[] {com.riiablo.engine.server.skill.SkillId.POISON_CREEPER, 268}) {
        Skills.Entry row = Riiablo.files.skills.get(skillId);
        CharData data = CharData.createRemote("summon-test", (byte) (row.srvdofunc == 49
            ? Riiablo.ASSASSIN : Riiablo.DRUID));
        data.setSkillLevel(row.Id, 1);
        world.getMapper(Player.class).create(owner).data = data;
        world.getMapper(Position.class).create(owner).position.set(5, 5);
        world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
            owner, row.Id, -1, new Vector2(10, 10), row.srvdofunc, row.cltdofunc));
        Attributes result = world.getMapper(AttributesWrapper.class)
            .get(factory.pets.get(factory.pets.size() - 1)).attrs;
        if (row.srvdofunc == 115) {
          assertEquals(0, result.getValue(Stat.tohit, 0), row.skill + " must not receive MonLvl TH");
        } else {
          assertEquals(80, result.getValue(Stat.level, 0), "Shadows retain the owner's level");
        }
      }
    } finally { world.dispose(); }
  }

  @Test void statFormulaReadsAggregateAndRejectsUnknownFields() {
    assertEquals(110, SkillFormula.evaluate("stat('passive_fire_mastery'.accr)+ulvl", null,
        20, name -> 0, name -> null, 35, name -> 75));
    assertEquals(0, SkillFormula.evaluate("stat('passive_fire_mastery'.unknown)", null,
        20, name -> 0, name -> null, 35, name -> 75));
  }

  @Test void shadowsGenerateFreshGearFromOwnerBaseCodes() {
    ItemGenerator generator = new ItemGenerator(Riiablo.files);
    CharData owner = CharData.createRemote("shadow-gear", (byte) Riiablo.ASSASSIN);
    Item armor = generator.generateLootItem("uar", 80, Quality.RARE, 123, 0);
    Item helm = generator.generateLootItem("crn", 80, Quality.RARE, 456, 0);
    owner.getItems().equipItem(BodyLoc.TORS, owner.getItems().add(armor));
    owner.getItems().equipItem(BodyLoc.HEAD, owner.getItems().add(helm));
    List<Item> items = SummonEquipment.generate(Riiablo.files, generator, "shadowwarrior",
        3, 22, 0, owner.getItems(), NativeUnitRandom.monster(new Monster(), 55, 1));
    assertEquals(4, items.size());
    Item copiedArmor = equipped(items, BodyLoc.TORS);
    assertEquals("uar", copiedArmor.code);
    assertNotSame(armor, copiedArmor);
    assertEquals(Quality.MAGIC, copiedArmor.quality);
    assertEquals(22, copiedArmor.ilvl & 255);
    assertEquals("crn", equipped(items, BodyLoc.HEAD).code);
  }

  @Test void minionSkillDamageUsesEffectiveLevelAndReachesBothAttackModes() throws Exception {
    RecordingFactory factory = new RecordingFactory();
    ServerSkillSystem skills = new ServerSkillSystem(true);
    World world = new World(new WorldConfigurationBuilder().with(new EventSystem(), skills, factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int owner = world.create(), pet = world.create();
      Skills.Entry damageSkill = Riiablo.files.skills.get("Summon Spirit Wolf");
      CharData data = CharData.createRemote("wolf-bonus", (byte) Riiablo.DRUID);
      data.setSkillLevel(damageSkill.Id, 1);
      world.getMapper(Player.class).create(owner).data = data;
      world.getMapper(UnitStates.class).create(owner).init(owner).stateList
          .addState(com.riiablo.engine.server.state.StateId.BATTLECOMMAND, 100, 1, owner)
          .skillModifier = 3;
      world.getMapper(AttributesWrapper.class).create(pet).attrs = attributes(80);
      Monster monster = world.getMapper(Monster.class).create(pet).setAttack2Profile(2, 4, 7);
      java.lang.reflect.Method apply = ServerSkillSystem.class.getDeclaredMethod(
          "applyDruidSkillDamage", int.class, int.class, MonStats.Entry.class);
      apply.setAccessible(true);
      apply.invoke(skills, owner, pet, Riiablo.files.monstats.get("spiritwolf"));
      int min = MissileDamageResolver.skillPhysicalDamage(damageSkill, 4, true, name -> 0);
      int max = MissileDamageResolver.skillPhysicalDamage(damageSkill, 4, false, name -> 0);
      assertEquals(min, world.getMapper(AttributesWrapper.class).get(pet).attrs.getValue(Stat.mindamage, 0));
      assertEquals(min + 2, monster.attack2MinDamage);
      assertEquals(max + 4, monster.attack2MaxDamage);
      assertEquals(7 + SkillFormula.evaluate("toht", damageSkill, 4), monster.attack2ToHit);
    } finally { world.dispose(); }
  }

  private static Item equipped(List<Item> items, BodyLoc loc) {
    return items.stream().filter(item -> item.bodyLoc == loc).findFirst().orElseThrow(AssertionError::new);
  }

  private static int hydraDamageFixed(Skills.Entry hydra, int level, boolean minimum) {
    int damage = minimum ? hydra.EMin : hydra.EMax;
    int[] steps = minimum ? hydra.EMinLev : hydra.EMaxLev;
    for (int slvl = 2; slvl <= level; slvl++) {
      damage += steps[slvl <= 8 ? 0 : slvl <= 16 ? 1 : slvl <= 22 ? 2 : slvl <= 28 ? 3 : 4];
    }
    damage <<= hydra.HitShift;
    // Ten hard points in both Fire Bolt and Fire Ball: native par8=3% each.
    damage += damage * 60 / 100;
    return damage + damage * 75 / 100;
  }

  private static Attributes attributes(int level) {
    Attributes attrs = Attributes.obtainLarge();
    attrs.base().put(Stat.level, level);
    attrs.base().put(Stat.hitpoints, 100f); attrs.base().put(Stat.maxhp, 100f);
    attrs.reset(); return attrs;
  }

  private static final class RecordingFactory extends EntityFactory {
    final List<Integer> pets = new ArrayList<>();
    final List<Missile> bolts = new ArrayList<>();
    @Override public int createSummonedPet(int owner, MonStats.Entry row, String type,
        int skill, int level, int max, boolean passive, int duration, float x, float y) {
      int id = world.create();
      world.getMapper(Position.class).create(id).position.set(x, y);
      world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(1);
      world.getMapper(UnitStates.class).create(id).init(id);
      world.getMapper(Monster.class).create(id).set(row, Riiablo.files.monstats2.get(row.MonStatsEx));
      world.getMapper(SummonedPet.class).create(id).set(owner, type, skill, level, passive, duration);
      pets.add(id); return id;
    }
    @Override public int createMissile(int id, Vector2 direction, Vector2 position, int owner) {
      Missiles.Entry row = Riiablo.files.Missiles.get(id);
      int entity = world.create();
      Missile bolt = world.getMapper(Missile.class).create(entity).set(row, position, row.Range).setOwner(owner);
      world.getMapper(Position.class).create(entity).position.set(position);
      world.getMapper(Velocity.class).create(entity).velocity.set(direction);
      bolts.add(bolt); return entity;
    }
    @Override public int createMissile(int id, Vector2 angle, Vector2 position) { return -1; }
    @Override public int createPlayer(CharData data, Vector2 position) { return -1; }
    @Override public int createMonster(int id, float x, float y) { return -1; }
    @Override public int createDynamicObject(int act, int id, float x, float y) { return -1; }
    @Override public int createStaticObject(int act, int id, float x, float y) { return -1; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return -1; }
    @Override public int createWarp(int index, float x, float y) { return -1; }
    @Override public int createItem(Item item, float x, float y) { return -1; }
  }
}
