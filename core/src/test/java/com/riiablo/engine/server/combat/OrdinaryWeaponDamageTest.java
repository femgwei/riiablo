package com.riiablo.engine.server.combat;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.serializer.VitalsSerializer;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.VitalsP;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrdinaryWeaponDamageTest extends RiiabloTest {
  @Test void equipmentLocalEDIsConsumedOnceAndConcreteHandsDoNotMix() {
    CharData data = character();
    Item right = weapon("hax", 10, 20);
    right.attrs.buildList().put(Stat.item_maxdamage_percent, 100);
    right.aggFlags = 1;
    Item left = weapon("hax", 40, 60);
    equip(data, right, BodyLoc.RARM);
    equip(data, left, BodyLoc.LARM);
    assertEquals(40, right.attrs.getValue(Stat.maxdamage, 0));
    WeaponAttackSource r = new WeaponAttackSource(data.getStats(), data.getItems(), right, false, false);
    WeaponAttackSource l = new WeaponAttackSource(data.getStats(), data.getItems(), left, false, false);
    assertEquals(40, r.maximum);
    assertEquals(60, l.maximum);
    assertEquals(0, r.stat(Stat.item_maxdamage_percent), "weapon ED must not become global ED");
  }

  @Test void daggerAndJavelinUseNativeStrengthAndDexterityCoefficients() {
    CharData data = character();
    data.getStats().aggregate().put(Stat.strength, 101);
    data.getStats().aggregate().put(Stat.dexterity, 99);
    Item dagger = weapon("dgr", 1, 2);
    WeaponAttackSource source = new WeaponAttackSource(data.getStats(), null, dagger, false, false);
    assertEquals(75, ((com.riiablo.codec.excel.Weapons.Entry) dagger.base).StrBonus);
    assertEquals(75, ((com.riiablo.codec.excel.Weapons.Entry) dagger.base).DexBonus);
    assertEquals(149, source.attributePercent, "truncate each coefficient separately");
    Item javelin = weapon("jav", 1, 2);
    assertEquals(149, new WeaponAttackSource(data.getStats(), null, javelin, false, true).attributePercent);
  }

  @Test void bowAndCrossbowUseDexterityNotMeleeStrength() {
    for (String code : new String[] {"sbw", "lxb"}) {
      CharData data = character();
      data.getStats().aggregate().put(Stat.strength, 200);
      data.getStats().aggregate().put(Stat.dexterity, 31);
      Item weapon = weapon(code, 1, 2);
      assertEquals(31, new WeaponAttackSource(data.getStats(), data.getItems(), weapon, false, false).attributePercent);
    }
  }

  @Test void offWeaponFlatAndEndpointPercentAndNormalDamageStayDistinct() {
    CharData data = character();
    Item axe = weapon("hax", 10, 20);
    equip(data, axe, BodyLoc.RARM);
    Item armor = new Item(); armor.reset(); armor.setBase(Riiablo.files.armor.get("cap"));
    com.riiablo.attributes.StatListRef props = armor.attrs.buildList();
    props.put(Stat.mindamage, 3); props.put(Stat.maxdamage, 5);
    props.put(Stat.item_mindamage_percent, 20); props.put(Stat.item_maxdamage_percent, 30);
    props.put(Stat.item_normaldamage, 2); armor.aggFlags = 1;
    equip(data, armor, BodyLoc.HEAD);
    WeaponAttackSource source = new WeaponAttackSource(data.getStats(), data.getItems(), axe, false, false);
    assertEquals(13, source.minimum); assertEquals(25, source.maximum);
    assertEquals(20, source.stat(Stat.item_mindamage_percent));
    assertEquals(30, source.stat(Stat.item_maxdamage_percent));
    assertEquals(2, source.stat(Stat.item_normaldamage));
    data.getItems().unequipItem(BodyLoc.HEAD);
    WeaponAttackSource after = new WeaponAttackSource(data.getStats(), data.getItems(), axe, false, false);
    assertEquals(10, after.minimum); assertEquals(20, after.maximum);
    assertEquals(0, after.stat(Stat.item_normaldamage), "removed slot must not retain bonuses even before location changes");
  }

  @Test void socketFlatAndOffWeaponPerLevelDamageAreNotPremultipliedByArmorED() {
    CharData data = character(); data.getStats().base().put(Stat.level, 8);
    Item axe = weapon("hax", 10, 20); equip(data, axe, BodyLoc.RARM);
    Item armor = new Item(); armor.reset(); armor.setBase(Riiablo.files.armor.get("cap"));
    com.riiablo.attributes.StatListRef props = armor.attrs.buildList();
    props.put(Stat.item_maxdamage_percent, 100);
    props.put(Stat.item_maxdamage_perlevel, 8); armor.aggFlags = 1;
    Item jewel = new Item(); jewel.reset(); jewel.setBase(Riiablo.files.misc.get("jew"));
    com.riiablo.attributes.StatListRef jewelProps = jewel.attrs.buildList();
    jewelProps.put(Stat.mindamage, 1); jewelProps.put(Stat.maxdamage, 2); jewel.aggFlags = 1;
    armor.sockets = new com.badlogic.gdx.utils.Array<>();
    armor.sockets.add(jewel); equip(data, armor, BodyLoc.HEAD);
    WeaponAttackSource source = new WeaponAttackSource(data.getStats(), data.getItems(), axe, false, false);
    assertEquals(11, source.minimum);
    assertEquals(30, source.maximum, "20 weapon +2 jewel +8 per-level; ED remains separate");
    assertEquals(100, source.stat(Stat.item_maxdamage_percent));
  }

  @Test void unequippedHirelingProjectileUsesDexterityRatherThanStrength() {
    Attributes owner = attributes(100f);
    owner.base().put(Stat.mindamage, 1); owner.base().put(Stat.maxdamage, 2);
    owner.base().put(Stat.secondary_mindamage, 1); owner.base().put(Stat.secondary_maxdamage, 1);
    owner.base().put(Stat.strength, 100); owner.base().put(Stat.dexterity, 20); owner.reset();
    WeaponAttackSource source = new WeaponAttackSource(owner, null, null, false, false, true, true);
    Missile missile = new Missile().set(ordinaryRow(), new Vector2(), 40);
    assertTrue(MissileDamageResolver.initializeOrdinaryWeapon(Riiablo.files, missile, owner, source, null, 1, false, n -> 99));
    assertEquals(20, missile.nativePhysical.damagePercent);
    assertEquals(256, missile.nativePhysical.minimum); assertEquals(512, missile.nativePhysical.maximum);
  }

  @Test void runtimeHandSelectionHonorsSwapBrokenWeaponsAndTwoHandedSwordMode() {
    World world = new World();
    try {
      CharData data = character();
      Item sword = weapon("2hs", 2, 8);
      sword.attrs.base().put(Stat.secondary_mindamage, 20);
      sword.attrs.base().put(Stat.secondary_maxdamage, 80);
      equip(data, sword, BodyLoc.RARM);
      int id = world.create();
      world.getMapper(Player.class).create(id).data = data;
      world.getMapper(AttributesWrapper.class).create(id).attrs = data.getStats();
      assertEquals(20, WeaponAttackSource.forUnit(world, id, false, false).minimum);
      Item left = weapon("hax", 40, 60); equip(data, left, BodyLoc.LARM);
      assertEquals(2, WeaponAttackSource.forUnit(world, id, false, false).minimum);
      assertEquals(40, WeaponAttackSource.forUnit(world, id, false, true).minimum);
      left.flags |= Item.ITEMFLAG_BROKEN;
      assertEquals(2, WeaponAttackSource.forUnit(world, id, false, true).minimum);
      Item alternate = weapon("hax", 70, 90); equip(data, alternate, BodyLoc.RARM2);
      data.getItems().setAlternate(1);
      assertEquals(70, WeaponAttackSource.forUnit(world, id, false, false).minimum);
    } finally { world.dispose(); }
  }

  @Test void meleeFractionsSurviveResistanceAndLeechSnapshotAndVitalsWire() {
    CharData data = character();
    data.getStats().base().put(Stat.strength, 10);
    data.getStats().base().put(Stat.lifedrainmindam, 50);
    Item axe = weapon("hax", 1, 2); equip(data, axe, BodyLoc.RARM);
    Attributes target = attributes(10f);
    target.aggregate().put(Stat.damageresist, 50);
    WeaponAttackSource source = new WeaponAttackSource(data.getStats(), data.getItems(), axe, false, false);
    CombatSystem.CombatResult hit = melee(data.getStats(), target, source);
    assertEquals(256, hit.physicalScale);
    assertEquals(140, hit.physicalDamage); // floor((256 + floor(256*10/100))*50/100)
    assertEquals(0, hit.totalDamage, "legacy summary is not authoritative");
    assertEquals(140 / 256f, hit.totalDamageHp());
    World world = new World(new WorldConfigurationBuilder().with(new EventSystem()).build());
    try {
      int attacker = world.create(), victim = world.create();
      world.getMapper(Player.class).create(attacker);
      data.getStats().aggregate().put(Stat.hitpoints, 10f);
      data.getStats().aggregate().put(Stat.maxhp, 100f);
      world.getMapper(AttributesWrapper.class).create(attacker).attrs = data.getStats();
      AttributesWrapper wrapper = world.getMapper(AttributesWrapper.class).create(victim);
      wrapper.attrs = target;
      DamageEvent event = DamageEvent.obtainMelee(attacker, victim, hit.totalDamageHp(), hit.physicalDamageHp(), hit);
      DamageSettlement.commit(world, event);
      DamageSettlement.commit(world, event);
      assertEquals(70 / 256f, event.lifeLeeched);
      assertEquals(10f - 140 / 256f, target.get(Stat.hitpoints).asFixed());
      assertEquals(10f - 140 / 256f, vitalsRoundTrip(wrapper));
    } finally { world.dispose(); }
  }

  @Test void positiveFractionalWeaponHitSnapsSubOneLifeToDeath() {
    CharData data = character();
    data.getStats().base().put(Stat.strength, 10);
    Item axe = weapon("hax", 1, 2); equip(data, axe, BodyLoc.RARM);
    Attributes target = attributes(2f);
    CombatSystem.CombatResult hit = melee(data.getStats(), target,
        new WeaponAttackSource(data.getStats(), data.getItems(), axe, false, false));
    assertEquals(281, hit.physicalDamage);
    World world = new World(new WorldConfigurationBuilder().with(new EventSystem()).build());
    try {
      int id = world.create();
      AttributesWrapper wrapper = world.getMapper(AttributesWrapper.class).create(id); wrapper.attrs = target;
      DamageEvent event = DamageEvent.obtainMelee(-1, id, hit.totalDamageHp(), hit.physicalDamageHp(), hit);
      DamageSettlement.commit(world, event); DamageSettlement.finishDeath(world, event);
      assertTrue(event.lethal); assertTrue(event.deathPublished);
      assertEquals(0f, vitalsRoundTrip(wrapper));
    } finally { world.dispose(); }
  }

  @Test void throwingHalfSourcePreservesFractionsAndSnapshotCannotFollowEquipmentChanges() {
    CharData data = character();
    data.getStats().base().put(Stat.strength, 10);
    data.getStats().base().put(Stat.dexterity, 10);
    Item javelin = weapon("jav", 3, 4);
    javelin.attrs.base().put(Stat.item_throw_mindamage, 1);
    javelin.attrs.base().put(Stat.item_throw_maxdamage, 2);
    equip(data, javelin, BodyLoc.RARM);
    WeaponAttackSource source = new WeaponAttackSource(data.getStats(), data.getItems(), javelin, false, true);
    Missiles.Entry row = ordinaryRow(); row.SrcDamage = 64;
    Missile missile = new Missile().set(row, new Vector2(), 40);
    StateList states = new StateList(1);
    com.riiablo.engine.server.state.UnitState aura = states.addState(
        com.riiablo.engine.server.state.StateId.MIGHT, 100, 1, 1);
    aura.setNativeModifier(Stat.damagepercent, 25);
    aura.setNativeModifier(Stat.item_tohit_percent, 17);
    assertTrue(MissileDamageResolver.initializeOrdinaryWeapon(Riiablo.files, missile, data.getStats(), source, states, 1, true, n -> 99));
    assertEquals(128, missile.nativePhysical.minimum); assertEquals(256, missile.nativePhysical.maximum);
    assertEquals(39, missile.nativePhysical.damagePercent);
    assertEquals(17, missile.damage.getValue(Stat.item_tohit_percent, 0));
    javelin.attrs.aggregate().put(Stat.item_throw_mindamage, 100);
    data.getStats().aggregate().put(Stat.dexterity, 200);
    aura.setNativeModifier(Stat.damagepercent, 999);
    assertEquals(39, missile.nativePhysical.damagePercent);
    assertEquals(128, missile.nativePhysical.minimum);
    assertTrue(MissileDamageResolver.initializeOrdinaryWeapon(Riiablo.files, missile, data.getStats(), source, null, 1, true, n -> { fail("duplicate launch critical"); return 0; }));
  }

  @Test void ordinaryArrowsSnapshotEquipmentElementFractionsAndUseNativeProjectilePolicy() {
    CharData data = character();
    data.getStats().base().put(Stat.firemindam, 1); data.getStats().base().put(Stat.firemaxdam, 2);
    Item bow = weapon("sbw", 1, 2); equip(data, bow, BodyLoc.RARM);
    Missiles.Entry row = ordinaryRow(); row.SrcDamage = 64;
    Missile missile = new Missile().set(row, new Vector2(), 40);
    assertTrue(MissileDamageResolver.initializeOrdinaryWeapon(Riiablo.files, missile, data.getStats(),
        new WeaponAttackSource(data.getStats(), data.getItems(), bow, false, false), null, 1, false, n -> 99));
    assertEquals(128, missile.nativeElemental.minimum(CombatSystem.DAMAGE_FIRE));
    assertTrue(missile.nativePhysical.criticalCaptured);
    assertTrue(missile.ordinaryWeaponSnapshot);
  }

  @Test void hirelingOwnerEquipmentAndInnateBonusesUseSeparateMeleeAndRoguePolicies() {
    World world = new World();
    try {
      CharData data = character();
      Item sword = weapon("hax", 10, 20);
      data.getMerc().getItems().equipItem(BodyLoc.RARM, data.getMerc().getItems().add(sword));
      Attributes hireling = attributes(100f);
      hireling.base().put(Stat.mindamage, 1); hireling.base().put(Stat.maxdamage, 1);
      hireling.base().put(Stat.secondary_mindamage, 5); hireling.base().put(Stat.secondary_maxdamage, 9);
      hireling.base().put(Stat.strength, 20); hireling.reset();
      int owner = world.create(), id = world.create();
      world.getMapper(Player.class).create(owner).data = data;
      Mercenary merc = world.getMapper(Mercenary.class).create(id).set(owner, 2, 1, 1, 1);
      world.getMapper(AttributesWrapper.class).create(id).attrs = hireling;
      WeaponAttackSource melee = WeaponAttackSource.forUnit(world, id, false, false);
      assertEquals(15, melee.minimum); assertEquals(29, melee.maximum); assertEquals(20, melee.attributePercent);
      merc.mercType = com.riiablo.engine.server.pet.MercenaryManager.MERC_TYPE_ROGUE;
      WeaponAttackSource rogue = WeaponAttackSource.forUnit(world, id, false, false);
      Missile missile = new Missile().set(ordinaryRow(), new Vector2(), 40);
      assertTrue(MissileDamageResolver.initializeOrdinaryWeapon(Riiablo.files, missile, hireling, rogue, null, 1, false, n -> 99));
      assertEquals(15 * 256, missile.nativePhysical.minimum);
      assertEquals(29 * 256, missile.nativePhysical.maximum);
    } finally { world.dispose(); }
  }

  @Test void specialAndOriginRowsRemainProtected() {
    Attributes owner = attributes(100f);
    owner.base().put(Stat.mindamage, 1); owner.base().put(Stat.maxdamage, 2); owner.reset();
    WeaponAttackSource source = new WeaponAttackSource(owner, null, null, false, false);
    for (int kind = 0; kind < 6; kind++) {
      Missiles.Entry row = ordinaryRow();
      if (kind == 0) row.Skill = "Fire Arrow";
      if (kind == 1) row.SrcMissDmg = 128;
      if (kind == 2) row.pSrvDmgFunc = 1;
      if (kind == 3) row.DmgSymPerCalc = "lvl";
      if (kind == 4) row.MissileSkill = true;
      if (kind == 5) row.Half2HSrc = true;
      Missile missile = new Missile().set(row, new Vector2(), 40);
      assertFalse(MissileDamageResolver.initializeOrdinaryWeapon(Riiablo.files, missile, owner, source, new StateList(1), 1, false, n -> { fail("protected branch rolled"); return 0; }));
      assertFalse(missile.ordinaryWeaponSnapshot);
    }
  }

  private static CombatSystem.CombatResult melee(Attributes owner, Attributes target, WeaponAttackSource source) {
    return new CombatSystem(n -> 0).calculateOrdinaryWeaponAttack(owner, target, true, false,
        source, null, null, false, NativeCombatContext.normal(), false, false, true);
  }

  private static CharData character() {
    CharData data = CharData.obtain().clear().set(Riiablo.NORMAL, false, "Weapon", Riiablo.BARBARIAN);
    data.getStats().base().put(Stat.strength, 0); data.getStats().base().put(Stat.dexterity, 0);
    data.getStats().base().put(Stat.tohit, 10000); data.getStats().reset(); return data;
  }

  private static Attributes attributes(float hp) {
    Attributes attrs = Attributes.obtainStandard(); attrs.base().put(Stat.level, 1);
    attrs.base().put(Stat.hitpoints, hp); attrs.base().put(Stat.maxhp, hp); attrs.reset(); return attrs;
  }

  private static Item weapon(String code, int min, int max) {
    Item item = new Item(); item.reset(); item.setBase(Riiablo.files.weapons.get(code));
    item.attrs.base().put(Stat.mindamage, min); item.attrs.base().put(Stat.maxdamage, max);
    item.attrs.reset(); return item;
  }

  private static void equip(CharData data, Item item, BodyLoc slot) {
    data.getItems().equipItem(slot, data.getItems().add(item));
  }

  private static Missiles.Entry ordinaryRow() {
    Missiles.Entry row = new Missiles.Entry(); row.Missile = "arrow"; row.SrcDamage = 128; row.ToHit = true;
    return row;
  }

  private static float vitalsRoundTrip(AttributesWrapper wrapper) {
    FlatBufferBuilder builder = new FlatBufferBuilder();
    VitalsSerializer serializer = new VitalsSerializer();
    int component = serializer.putData(builder, wrapper);
    int types = EntitySync.createComponentTypeVector(builder, new byte[] {ComponentP.VitalsP});
    int values = EntitySync.createComponentVector(builder, new int[] {component});
    EntitySync.startEntitySync(builder); EntitySync.addComponentType(builder, types); EntitySync.addComponent(builder, values);
    builder.finish(EntitySync.endEntitySync(builder));
    EntitySync sync = EntitySync.getRootAsEntitySync(builder.dataBuffer());
    AttributesWrapper client = new AttributesWrapper(); client.attrs = attributes(0f);
    serializer.getData(sync, 0, client);
    assertEquals(wrapper.attrs.get(Stat.hitpoints).asFixed() <= 0f, ((VitalsP) sync.component(new VitalsP(), 0)).dead());
    return client.attrs.get(Stat.hitpoints).asFixed();
  }
}
