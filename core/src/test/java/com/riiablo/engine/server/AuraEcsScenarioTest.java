package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.serializer.StateSerializer;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.engine.server.skill.NativeSkillResolver;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Headless authoritative ECS coverage for native aura targeting and lifecycle. */
class AuraEcsScenarioTest extends RiiabloTest {
  @Test
  void equipmentAurasInitializeWithoutSelectionAndCoexistWithSelectedAura() {
    try (Harness test = new Harness()) {
      int caster = player(test.world, 0, 0);
      int ally = player(test.world, 1, 0);
      int allyPet = summon(test.world, ally, 2, 0);
      int outsider = player(test.world, 2, 1);
      int enemy = monster(test.world, 3, 0);
      party(test.parties, caster, ally);
      equipmentAura(test.world, caster, SkillId.FANATICISM, 9);
      equipmentAura(test.world, caster, SkillId.CONVICTION, 12);
      assertTrue(test.auras.manager().activateAura(caster, SkillId.MIGHT, 3));
      test.ticks(51);
      assertTrue(states(test.world, caster).hasState(StateId.FANATICISM));
      assertTrue(states(test.world, ally).hasState(StateId.FANATICISM));
      assertTrue(states(test.world, allyPet).hasState(StateId.FANATICISM));
      assertFalse(states(test.world, outsider).hasState(StateId.FANATICISM));
      assertTrue(states(test.world, enemy).hasState(StateId.CONVICTION));
      assertTrue(states(test.world, ally).hasState(StateId.MIGHT));

      // Selecting or clearing a different skill must not cancel item streams.
      assertTrue(test.auras.manager().activateAura(caster, SkillId.DEFIANCE, 2));
      test.tick();
      test.auras.clearAura(caster);
      test.ticks(55);
      assertTrue(states(test.world, allyPet).hasState(StateId.FANATICISM));
      assertTrue(states(test.world, enemy).hasState(StateId.CONVICTION));
      assertFalse(states(test.world, ally).hasState(StateId.MIGHT));
      assertFalse(states(test.world, ally).hasState(StateId.DEFIANCE));

      equipmentStats(test.world, caster).clear();
      test.ticks(55);
      assertFalse(states(test.world, caster).hasState(StateId.FANATICISM));
      assertFalse(states(test.world, allyPet).hasState(StateId.FANATICISM));
      assertFalse(states(test.world, enemy).hasState(StateId.CONVICTION));
      assertTrue(test.auras.manager().getEquipmentAura(caster, SkillId.FANATICISM) == null);
    }
  }

  @Test
  void mercenaryEquipmentAuraRestoresAndStopsOnDeathOrUnequip() {
    try (Harness test = new Harness()) {
      int owner = player(test.world, 0, 0);
      int merc = mercenary(test.world, owner, 1, 0);
      equipmentAura(test.world, merc, SkillId.MEDITATION, 17);
      test.ticks(51);
      assertTrue(states(test.world, owner).hasState(StateId.MEDITATION));
      assertEquals(17, test.auras.manager().getEquipmentAura(merc, SkillId.MEDITATION).skillLevel);
      life(test.world, merc, 0);
      test.ticks(55);
      assertFalse(states(test.world, owner).hasState(StateId.MEDITATION));
      life(test.world, merc, 100);
      test.ticks(51);
      assertTrue(states(test.world, owner).hasState(StateId.MEDITATION));
      equipmentStats(test.world, merc).clear();
      test.ticks(55);
      assertFalse(states(test.world, owner).hasState(StateId.MEDITATION));
    }
  }

  @Test
  void sameItemAuraLevelsSumButDoNotAddToSelectedSkillLevel() {
    try (Harness test = new Harness()) {
      int caster = player(test.world, 0, 0);
      int ally = player(test.world, 1, 0);
      party(test.parties, caster, ally);
      // AttributesUpdater merges two equipped item_aura layers with the same
      // skill parameter. Ordinary +skills must not increase this item level.
      com.riiablo.attributes.StatListRef item = Attributes.obtainStandard().buildList();
      equipmentStats(test.world, caster).add(item.putEncoded(Stat.item_aura, SkillId.MIGHT, 7));
      equipmentStats(test.world, caster).add(item.putEncoded(Stat.item_aura, SkillId.MIGHT, 8));
      equipmentStats(test.world, caster).put(Stat.item_allskills, 5);
      assertTrue(test.auras.manager().activateAura(caster, SkillId.MIGHT, 20));
      test.tick();
      assertEquals(15, test.auras.manager().getEquipmentAura(caster, SkillId.MIGHT).skillLevel);
      assertEquals(20, states(test.world, ally).getState(StateId.MIGHT).level);
      test.auras.clearAura(caster);
      test.ticks(51);
      assertEquals(15, states(test.world, ally).getState(StateId.MIGHT).level);
      equipmentAura(test.world, caster, SkillId.MIGHT, 7);
      test.ticks(51);
      assertEquals(7, states(test.world, ally).getState(StateId.MIGHT).level);
    }
  }

  @Test
  void replicatedEquipmentNeverStartsAuthoritativeAuraScheduler() {
    try (Harness test = new Harness()) {
      int replica = player(test.world, 0, 0);
      test.world.getMapper(UnitStates.class).get(replica).snapshotOnly = true;
      equipmentAura(test.world, replica, SkillId.FANATICISM, 9);
      test.ticks(55);
      assertTrue(test.auras.manager().getEquipmentAura(replica, SkillId.FANATICISM) == null);
      assertFalse(states(test.world, replica).hasState(StateId.FANATICISM));
    }
  }

  @Test
  void equippedItemListsRespectWeaponSwitchBrokenAndInventoryBoundaries() {
    try (Harness test = new Harness()) {
      int caster = player(test.world, 0, 0);
      CharData data = CharData.obtain().clear().set(
          Riiablo.NORMAL, false, "item-aura", Riiablo.PALADIN);
      data.getStats().base().put(Stat.hitpoints, 100f);
      data.getStats().base().put(Stat.maxhp, 100f);
      data.getStats().reset();
      test.world.getMapper(Player.class).get(caster).data = data;
      test.world.getMapper(AttributesWrapper.class).get(caster).attrs = data.getStats();
      Item first = auraItem("cap", SkillId.HOLY_SHOCK, 15);
      Item second = auraItem("buc", SkillId.HOLY_SHOCK, 15);
      data.getItems().equipItem(com.riiablo.item.BodyLoc.HEAD, data.getItems().add(first));
      data.getItems().equipItem(com.riiablo.item.BodyLoc.LARM, data.getItems().add(second));
      test.ticks(51);
      assertNotNull(test.auras.manager().getEquipmentAura(caster, SkillId.HOLY_SHOCK));
      assertEquals(30, test.auras.manager().getEquipmentAura(caster, SkillId.HOLY_SHOCK).skillLevel);
      assertEquals(2, test.auras.manager().getEquipmentAuraCount(caster, SkillId.HOLY_SHOCK));
      assertTrue(states(test.world, caster).hasState(StateId.HOLYSHOCK));

      data.getItems().setAlternate(1);
      test.ticks(51);
      assertEquals(15, test.auras.manager().getEquipmentAura(caster, SkillId.HOLY_SHOCK).skillLevel);
      assertEquals(1, test.auras.manager().getEquipmentAuraCount(caster, SkillId.HOLY_SHOCK));
      data.getItems().setAlternate(0);
      test.ticks(51);
      assertEquals(30, test.auras.manager().getEquipmentAura(caster, SkillId.HOLY_SHOCK).skillLevel);
      assertEquals(2, test.auras.manager().getEquipmentAuraCount(caster, SkillId.HOLY_SHOCK));

      data.getItems().unequipItem(com.riiablo.item.BodyLoc.LARM);
      test.ticks(51);
      assertEquals(15, test.auras.manager().getEquipmentAura(caster, SkillId.HOLY_SHOCK).skillLevel);
      assertEquals(1, test.auras.manager().getEquipmentAuraCount(caster, SkillId.HOLY_SHOCK));
      // The unequipped item remains owned; its old EQUIPPED location is not
      // sufficient to retain an event after removal from the actual slot map.
      data.getItems().setAlternate(1);
      test.ticks(51);
      assertEquals(15, test.auras.manager().getEquipmentAura(caster, SkillId.HOLY_SHOCK).skillLevel);
      first.flags |= Item.ITEMFLAG_BROKEN;
      data.getItems().updateStats();
      test.ticks(55);
      assertTrue(test.auras.manager().getEquipmentAura(caster, SkillId.HOLY_SHOCK) == null);
      first.flags &= ~Item.ITEMFLAG_BROKEN;
      data.getItems().updateStats();
      test.ticks(51);
      assertNotNull(test.auras.manager().getEquipmentAura(caster, SkillId.HOLY_SHOCK));
      data.getItems().unequipItem(com.riiablo.item.BodyLoc.HEAD);
      test.ticks(55);
      assertTrue(test.auras.manager().getEquipmentAura(caster, SkillId.HOLY_SHOCK) == null);
      assertFalse(states(test.world, caster).hasState(StateId.HOLYSHOCK));
    }
  }

  private static Item auraItem(String code, int skill, int level) {
    Item item = new Item();
    item.reset();
    if (Riiablo.files.armor.get(code) != null) item.setBase(Riiablo.files.armor.get(code));
    else item.setBase(Riiablo.files.weapons.get(code));
    item.attrs.buildList().putEncoded(Stat.item_aura, skill, level);
    item.aggFlags = com.riiablo.attributes.StatListFlags.FLAG_MAGIC;
    return item;
  }

  @Test
  void everyRegisteredNativePaladinAuraCanBeProvidedByEquipment() {
    int[] skills = {SkillId.MIGHT, SkillId.PRAYER, SkillId.RESIST_FIRE,
        SkillId.RESIST_COLD, SkillId.RESIST_LIGHTNING, SkillId.HOLY_FIRE,
        SkillId.THORNS, SkillId.DEFIANCE, SkillId.BLESSED_AIM,
        SkillId.CONCENTRATION, SkillId.VIGOR, SkillId.HOLY_FREEZE,
        SkillId.HOLY_SHOCK, SkillId.SANCTUARY, SkillId.FANATICISM,
        SkillId.CONVICTION, SkillId.SALVATION, SkillId.CLEANSING,
        SkillId.MEDITATION, SkillId.REDEMPTION};
    for (int skill : skills) {
      try (Harness test = new Harness()) {
        int caster = player(test.world, 0, 0);
        equipmentAura(test.world, caster, skill, 5);
        test.ticks(51);
        assertNotNull(test.auras.manager().getEquipmentAura(caster, skill), "skill=" + skill);
        int state = Riiablo.files.States.get(Riiablo.files.skills.get(skill).aurastate).id;
        assertTrue(states(test.world, caster).hasState(state), "skill=" + skill);
        equipmentStats(test.world, caster).clear();
        test.ticks(55);
        assertFalse(states(test.world, caster).hasState(state), "removed skill=" + skill);
      }
    }
  }

  private static com.riiablo.attributes.StatListRef equipmentStats(World world, int entity) {
    return world.getMapper(AttributesWrapper.class).get(entity).attrs.remaining();
  }

  private static void equipmentAura(World world, int entity, int skill, int level) {
    equipmentStats(world, entity).putEncoded(Stat.item_aura, skill, level);
  }

  @Test
  void mightBuffsOnlyPartyAndConvictionTargetsHostiles() {
    try (Harness test = new Harness()) {
      int caster = player(test.world, 0, 0);
      int ally = player(test.world, 3, 0);
      int neutral = player(test.world, 4, 0);
      int hostilePlayer = player(test.world, 5, 0);
      int monster = monster(test.world, 6, 0);
      party(test.parties, caster, ally);
      assertTrue(test.parties.declareHostility(caster, hostilePlayer));

      assertTrue(test.auras.manager().activateAura(caster, SkillId.MIGHT, 1));
      test.tick();
      assertTrue(states(test.world, caster).hasState(StateId.MIGHT));
      assertTrue(states(test.world, ally).hasState(StateId.MIGHT));
      assertFalse(states(test.world, neutral).hasState(StateId.MIGHT));
      assertFalse(states(test.world, hostilePlayer).hasState(StateId.MIGHT));
      assertFalse(states(test.world, monster).hasState(StateId.MIGHT));
      assertEquals(40, states(test.world, ally).getTotalDamageModifier());

      assertTrue(test.auras.manager().activateAura(caster, SkillId.CONVICTION, 1));
      test.tick();
      assertTrue(states(test.world, hostilePlayer).hasState(StateId.CONVICTION));
      assertTrue(states(test.world, monster).hasState(StateId.CONVICTION));
      assertFalse(states(test.world, ally).hasState(StateId.CONVICTION));
      assertFalse(states(test.world, neutral).hasState(StateId.CONVICTION));
      assertEquals(-30, states(test.world, monster).getTotalResistModifier(0));
      assertEquals(-30, states(test.world, monster).getTotalResistModifier(1));
      assertEquals(-30, states(test.world, monster).getTotalResistModifier(2));
      assertEquals(-49, states(test.world, monster).getTotalDefenseModifier());

      test.ticks(52);
      assertFalse(states(test.world, caster).hasState(StateId.MIGHT));
      assertFalse(states(test.world, ally).hasState(StateId.MIGHT));

      life(test.world, caster, 0);
      test.tick();
      assertFalse(test.auras.manager().hasActiveAura(caster));
      test.ticks(51);
      assertFalse(states(test.world, monster).hasState(StateId.CONVICTION));
    }
  }

  @Test
  void hostileAuraFilterMatrixMatchesD2MooBossPrimeNoAuraAndAttackabilityRules() {
    try (Harness test = new Harness()) {
      int caster = player(test.world, 0, 0);
      int boss = monsterWith(test.world, 3, 0, false, true, false, true, true);
      int primeEvil = monsterWith(test.world, 4, 0, false, false, true, true, true);
      int noAura = monsterWith(test.world, 5, 0, true, false, false, true, true);
      int notAttackable = monsterWith(test.world, 6, 0, false, false, false, false, true);
      int notSelectable = monsterWith(test.world, 7, 0, false, false, false, true, false);

      // D2MOO's Conviction row is SrvDo066 with aurafilter 0xA583: it does
      // not carry IGNBO[B] or IGNPRIME, and SrvDo066 passes bCheckMonAuraFlag=0.
      // Consequently bosses, Prime Evils, and noAura monsters are valid, while
      // FINDISATT/FINDISSEL still reject the two corresponding MonStats2 rows.
      assertTrue(test.auras.manager().activateAura(caster, SkillId.CONVICTION, 1));
      test.tick();
      assertTrue(states(test.world, boss).hasState(StateId.CONVICTION));
      assertTrue(states(test.world, primeEvil).hasState(StateId.CONVICTION));
      assertTrue(states(test.world, noAura).hasState(StateId.CONVICTION));
      assertFalse(states(test.world, notAttackable).hasState(StateId.CONVICTION));
      assertFalse(states(test.world, notSelectable).hasState(StateId.CONVICTION));

      // Holy Freeze is SrvDo081 and uses the same noAura bypass, but its
      // callback adds the native coldeffect immunity gate after aurafilter.
      test.auras.manager().deactivateAura(caster);
      int coldImmune = monsterWith(test.world, 2.5f, 0, false, false, false, true, true);
      MonStats.Entry coldRow = test.world.getMapper(Monster.class).get(coldImmune).monstats;
      coldRow.coldeffect = new int[] {0, 0, 0};
      int coldVulnerable = monsterWith(test.world, 5.5f, 0, true, false, false, true, true);
      MonStats.Entry vulnerableRow = test.world.getMapper(Monster.class).get(coldVulnerable).monstats;
      vulnerableRow.coldeffect = new int[] {-1, -1, -1};

      assertTrue(test.auras.manager().activateAura(caster, SkillId.HOLY_FREEZE, 1));
      test.tick();
      assertFalse(states(test.world, coldImmune).hasState(44),
          "native Holy Freeze rejects monsters with non-negative coldeffect");
      assertTrue(states(test.world, coldVulnerable).hasState(44),
          "SrvDo081 still reaches a susceptible noAura monster");
    }
  }

  @Test
  void strongestSameSkillAuraWinsAndWeakerReturnsOnItsNextPulse() {
    try (Harness test = new Harness()) {
      int weakCaster = player(test.world, 0, 0);
      int strongCaster = player(test.world, 1, 0);
      int ally = player(test.world, 3, 0);
      short party = test.parties.createParty(weakCaster);
      assertTrue(party >= 0);
      assertTrue(test.parties.joinParty(party, strongCaster));
      assertTrue(test.parties.joinParty(party, ally));

      assertTrue(test.auras.manager().activateAura(weakCaster, SkillId.MIGHT, 1));
      assertTrue(test.auras.manager().activateAura(strongCaster, SkillId.MIGHT, 5));
      test.tick();

      StateList allyStates = states(test.world, ally);
      UnitState winner = allyStates.getState(StateId.MIGHT);
      assertNotNull(winner);
      assertEquals(strongCaster, winner.sourceEntityId);
      assertEquals(SkillId.MIGHT, winner.skillId);
      assertEquals(5, winner.level);
      assertEquals(51, winner.duration,
          "the aura runs after state decay and publishes native perdelay+1");
      assertEquals(80, allyStates.getTotalDamageModifier());
      assertEquals(1, allyStates.size(), "the rejected weak aura must not leave a hidden layer");

      life(test.world, strongCaster, 0);
      test.tick();
      assertEquals(strongCaster, allyStates.getState(StateId.MIGHT).sourceEntityId,
          "the strong native layer lives until its short expiry");
      test.ticks(51);

      UnitState restored = allyStates.getState(StateId.MIGHT);
      assertNotNull(restored);
      assertEquals(weakCaster, restored.sourceEntityId);
      assertEquals(1, restored.level);
      assertEquals(40, allyStates.getTotalDamageModifier());
    }
  }

  @Test
  void equalLevelAurasUseStableLowestCasterTieBreakRegardlessOfActivationOrder() {
    try (Harness test = new Harness()) {
      int first = player(test.world, 0, 0);
      int second = player(test.world, 1, 0);
      int ally = player(test.world, 3, 0);
      short party = test.parties.createParty(first);
      assertTrue(party >= 0);
      assertTrue(test.parties.joinParty(party, second));
      assertTrue(test.parties.joinParty(party, ally));

      // Activate the higher entity id first; native selection must not depend
      // on IntMap iteration or packet/activation order when levels match.
      assertTrue(test.auras.manager().activateAura(second, SkillId.MIGHT, 3));
      assertTrue(test.auras.manager().activateAura(first, SkillId.MIGHT, 3));
      test.tick();
      assertEquals(first, states(test.world, ally).getState(StateId.MIGHT).sourceEntityId);
      assertTrue(test.auras.manager().getActiveAura(second).affectedEntities.contains(ally, false));

      test.auras.manager().deactivateAura(first);
      test.ticks(52);
      assertEquals(second, states(test.world, ally).getState(StateId.MIGHT).sourceEntityId,
          "the equal-level fallback must become visible on the next pulse");
    }
  }

  @Test
  void differentAuraStatesStackOnTheSamePartyMember() {
    try (Harness test = new Harness()) {
      int mightCaster = player(test.world, 0, 0);
      int prayerCaster = player(test.world, 1, 0);
      int ally = player(test.world, 3, 0);
      short party = test.parties.createParty(mightCaster);
      assertTrue(party >= 0);
      assertTrue(test.parties.joinParty(party, prayerCaster));
      assertTrue(test.parties.joinParty(party, ally));
      life(test.world, ally, 98);

      assertTrue(test.auras.manager().activateAura(mightCaster, SkillId.MIGHT, 1));
      assertTrue(test.auras.manager().activateAura(prayerCaster, SkillId.PRAYER, 1));
      test.tick();

      StateList allyStates = states(test.world, ally);
      assertTrue(allyStates.hasState(StateId.MIGHT));
      assertTrue(allyStates.hasState(StateId.PRAYER));
      assertEquals(40, allyStates.getTotalDamageModifier());
      assertEquals(100f, life(test.world, ally), 0.001f);
    }
  }

  @Test
  void rangeZoneMercenaryAndSummonUseAuthoritativeOwnerRelations() {
    try (Harness test = new Harness()) {
      int caster = player(test.world, 0, 0);
      int owner = player(test.world, 2, 0);
      int outsider = player(test.world, 3, 0);
      int mercenary = mercenary(test.world, owner, 4, 0);
      int summon = summon(test.world, owner, 5, 0);
      int outsiderSummon = summon(test.world, outsider, 6, 0);
      int noAuraMercenary = mercenary(test.world, owner, 7, 0);
      MonStats.Entry noAuraRow = new MonStats.Entry();
      noAuraRow.noAura = true;
      test.world.getMapper(Monster.class).get(noAuraMercenary).monstats = noAuraRow;
      party(test.parties, caster, owner);

      Map.Zone field = new Map.Zone();
      Map.Zone otherLevel = new Map.Zone();
      zone(test.world, caster, field);
      zone(test.world, owner, field);
      zone(test.world, mercenary, field);
      zone(test.world, summon, field);
      zone(test.world, outsider, field);
      zone(test.world, outsiderSummon, field);
      zone(test.world, noAuraMercenary, field);

      assertTrue(test.auras.manager().activateAura(caster, SkillId.MIGHT, 1));
      test.tick();
      assertTrue(states(test.world, mercenary).hasState(StateId.MIGHT));
      assertTrue(states(test.world, summon).hasState(StateId.MIGHT));
      assertFalse(states(test.world, outsiderSummon).hasState(StateId.MIGHT));
      assertFalse(states(test.world, noAuraMercenary).hasState(StateId.MIGHT));

      zone(test.world, summon, otherLevel);
      position(test.world, mercenary).set(100, 0);
      test.ticks(51);
      assertFalse(states(test.world, mercenary).hasState(StateId.MIGHT));
      assertFalse(states(test.world, summon).hasState(StateId.MIGHT));
    }
  }

  @Test
  void nativePartyAuraAppliesToSelfButSkipsTownRoomTargets() {
    try (Harness test = new Harness()) {
      int caster = player(test.world, 0, 0);
      int ally = player(test.world, 3, 0);
      party(test.parties, caster, ally);
      Map.Zone town = new Map.Zone() {
        @Override public boolean isTown() { return true; }
      };
      zone(test.world, caster, town);
      zone(test.world, ally, town);

      assertTrue(test.auras.manager().activateAura(caster, SkillId.MIGHT, 1));
      test.tick();
      assertTrue(states(test.world, caster).hasState(StateId.MIGHT));
      assertFalse(states(test.world, ally).hasState(StateId.MIGHT));
    }
  }

  @Test
  void prayerUsesNativePeriodAndClampsHealingToMaximumLife() {
    try (Harness test = new Harness()) {
      int caster = player(test.world, 0, 0);
      int ally = player(test.world, 3, 0);
      party(test.parties, caster, ally);
      life(test.world, ally, 97);

      assertTrue(test.auras.manager().activateAura(caster, SkillId.PRAYER, 1));
      test.tick();
      assertEquals(99f, life(test.world, ally), 0.001f);
      test.ticks(50);
      assertEquals(100f, life(test.world, ally), 0.001f,
          "level-one Prayer heals two life but must clamp at maxhp");
    }
  }

  @Test
  void prayerSpendsManaOnlyWhenItsPulseActuallyRestoresLife() {
    try (Harness test = new Harness()) {
      int caster = player(test.world, 0, 0);
      float cost = NativeSkillResolver.manaCost(
          test.auras.manager().getAuraDefinition(SkillId.PRAYER).nativeSkill, 1);
      mana(test.world, caster, 10f);

      assertTrue(test.auras.manager().activateAura(caster, SkillId.PRAYER, 1));
      test.tick();
      assertEquals(10f, mana(test.world, caster), 0.001f,
          "a full-life target must not make Prayer consume a pulse cost");

      life(test.world, caster, 98);
      test.ticks(50);
      assertEquals(100f, life(test.world, caster), 0.001f);
      assertEquals(10f - cost, mana(test.world, caster), 0.001f,
          "one useful Prayer pulse consumes exactly one native cost");
    }
  }

  @Test
  void cleansingUsesPrayerLevelForLinkedPulseHealingOnCasterAndParty() {
    try (Harness test = new Harness()) {
      int caster = player(test.world, 0, 0);
      int ally = player(test.world, 3, 0);
      party(test.parties, caster, ally);
      CharData data = CharData.createRemote("paladin", (byte) Riiablo.PALADIN);
      test.world.getMapper(Player.class).get(caster).data = data;
      data.setSkillLevel(SkillId.PRAYER, 1);
      life(test.world, caster, 98);
      life(test.world, ally, 98);
      states(test.world, caster).addState(StateId.POISON, 100, 1, 77);
      states(test.world, ally).addState(StateId.POISON, 100, 1, 77);

      assertTrue(test.auras.manager().activateAura(caster, SkillId.CLEANSING, 1));
      assertEquals(512, test.auras.manager().getActiveAura(caster).statValues[1],
          java.util.Arrays.toString(test.auras.manager().getActiveAura(caster).statValues));
      test.tick();

      assertEquals(100f, life(test.world, caster), 0.001f,
          "Cleansing must apply its linked Prayer heal to the owner");
      assertEquals(100f, life(test.world, ally), 0.001f,
          "Cleansing must apply its linked Prayer heal to party targets");
      assertTrue(states(test.world, caster).getState(StateId.POISON).duration < 100);
      assertTrue(states(test.world, ally).getState(StateId.POISON).duration < 100);
    }
  }

  @Test
  void meditationUsesPrayerLevelForLinkedPulseHealingWhilePublishingManaRecovery() {
    try (Harness test = new Harness()) {
      int caster = player(test.world, 0, 0);
      int ally = player(test.world, 3, 0);
      party(test.parties, caster, ally);
      CharData data = CharData.createRemote("paladin", (byte) Riiablo.PALADIN);
      test.world.getMapper(Player.class).get(caster).data = data;
      data.setSkillLevel(SkillId.PRAYER, 1);
      life(test.world, caster, 98);
      life(test.world, ally, 98);

      assertTrue(test.auras.manager().activateAura(caster, SkillId.MEDITATION, 1));
      assertEquals(512, test.auras.manager().getActiveAura(caster).statValues[1],
          java.util.Arrays.toString(test.auras.manager().getActiveAura(caster).statValues));
      test.tick();

      assertEquals(100f, life(test.world, caster), 0.001f,
          "Meditation must apply its linked Prayer heal to the owner");
      assertEquals(100f, life(test.world, ally), 0.001f,
          "Meditation must apply its linked Prayer heal to party targets");
      assertTrue(states(test.world, caster).getState(StateId.MEDITATION)
          .getStatContributionValue(Stat.manarecoverybonus) > 0);
      assertTrue(states(test.world, ally).getState(StateId.MEDITATION)
          .getStatContributionValue(Stat.manarecoverybonus) > 0);
    }
  }

  @Test
  void unfundedPrayerKeepsSelectionAndRetriesOnTheNextPulse() {
    try (Harness test = new Harness()) {
      int caster = player(test.world, 0, 0);
      float cost = NativeSkillResolver.manaCost(
          test.auras.manager().getAuraDefinition(SkillId.PRAYER).nativeSkill, 1);
      life(test.world, caster, 90);
      mana(test.world, caster, 0f);

      assertTrue(test.auras.manager().activateAura(caster, SkillId.PRAYER, 1));
      test.tick();
      assertEquals(90f, life(test.world, caster), 0.001f);
      assertTrue(test.auras.manager().hasActiveAura(caster),
          "native mana failure skips one pulse; it does not deselect the aura");
      assertTrue(states(test.world, caster).hasState(StateId.PRAYER));

      mana(test.world, caster, cost);
      test.ticks(50);
      assertEquals(92f, life(test.world, caster), 0.001f);
      assertEquals(0f, mana(test.world, caster), 0.001f);
      assertTrue(test.auras.manager().hasActiveAura(caster));
    }
  }

  @Test
  void holyFireDoesNotDamageInTownAndPulsesInTheField() {
    try (Harness test = new Harness()) {
      int caster = player(test.world, 0, 0);
      int target = monster(test.world, 3, 0);
      Map.Zone town = new Map.Zone() {
        @Override public boolean isTown() { return true; }
      };
      Map.Zone field = new Map.Zone();
      zone(test.world, caster, town);
      zone(test.world, target, town);

      assertTrue(test.auras.manager().activateAura(caster, SkillId.HOLY_FIRE, 1));
      test.tick();
      assertEquals(100f, life(test.world, target), 0.001f);
      assertEquals(6, states(test.world, caster).getTotalStatContribution(Stat.firemindam));
      assertEquals(18, states(test.world, caster).getTotalStatContribution(Stat.firemaxdam));

      zone(test.world, caster, field);
      zone(test.world, target, field);
      test.ticks(50);
      assertTrue(life(test.world, target) < 100f,
          "the native periodic fire packet must damage a hostile field monster");
      assertTrue(life(test.world, target) >= 97f,
          "level-one Holy Fire periodic damage is in the native 1..3 range");
    }
  }

  @Test
  void holyFireDoesNotConsumeManaForItsSelfLayerWhenTownSkipsDamageScan() {
    try (Harness test = new Harness()) {
      int caster = player(test.world, 0, 0);
      int target = monster(test.world, 3, 0);
      Map.Zone town = new Map.Zone() {
        @Override public boolean isTown() { return true; }
      };
      zone(test.world, caster, town);
      zone(test.world, target, town);
      mana(test.world, caster, 10f);

      assertTrue(test.auras.manager().activateAura(caster, SkillId.HOLY_FIRE, 1));
      test.tick();
      assertEquals(10f, mana(test.world, caster), 0.001f,
          "D2MOO SrvDo066 returns before the hostile damage scan in town; self stats alone must not consume mana");
      assertTrue(states(test.world, caster).hasState(StateId.HOLYFIRE));
    }
  }

  @Test
  void fanaticismExpiresAfterRangePulseAndUsesItsNativeAttackRateStat() {
    try (Harness test = new Harness()) {
      int caster = player(test.world, 0, 0);
      int ally = player(test.world, 3, 0);
      party(test.parties, caster, ally);
      Velocity velocity = test.world.getMapper(Velocity.class).create(ally);

      assertTrue(test.auras.manager().activateAura(caster, SkillId.FANATICISM, 1));
      test.tick();
      assertTrue(states(test.world, ally).hasState(StateId.FANATICISM));
      assertEquals(14, states(test.world, ally).getTotalAnimationRateModifier());
      assertEquals(0, states(test.world, ally).getTotalVelocityModifier());
      assertEquals(1f, velocity.stateSpeedMultiplier, 0.001f);

      position(test.world, ally).set(500, 0);
      test.ticks(51);
      assertFalse(states(test.world, ally).hasState(StateId.FANATICISM));
      assertEquals(1f, velocity.stateSpeedMultiplier, 0.001f);

      // Re-entering the aura range must allow the same source to publish a
      // fresh layer on its next native pulse after the old one expires.
      position(test.world, ally).set(3, 0);
      test.ticks(50);
      UnitState restored = states(test.world, ally).getState(StateId.FANATICISM);
      assertNotNull(restored);
      assertEquals(caster, restored.sourceEntityId);
      assertEquals(SkillId.FANATICISM, restored.skillId);
      assertEquals(1f, velocity.stateSpeedMultiplier, 0.001f);
    }
  }

  @Test
  void paladinAuraSnapshotReplacesStaleSourceLayerAcrossReconnect() {
    try (Harness test = new Harness()) {
      int caster = player(test.world, 0, 0);
      int ally = player(test.world, 3, 0);
      party(test.parties, caster, ally);

      assertTrue(test.auras.manager().activateAura(caster, SkillId.MIGHT, 3));
      test.tick();
      UnitState authoritative = states(test.world, ally).getState(StateId.MIGHT);
      assertNotNull(authoritative);
      assertEquals(caster, authoritative.sourceEntityId);
      assertEquals(SkillId.MIGHT, authoritative.skillId);

      StateSerializer serializer = new StateSerializer();
      FlatBufferBuilder builder = new FlatBufferBuilder(256);
      int stateOffset = serializer.putData(builder,
          test.world.getMapper(UnitStates.class).get(ally));
      int types = EntitySync.createComponentTypeVector(builder,
          new byte[] {ComponentP.StateP});
      int components = EntitySync.createComponentVector(builder, new int[] {stateOffset});
      int root = EntitySync.createEntitySync(builder, ally, 0, 0, types, components,
          0L, 0L, 0L, 0L, 0L, -1);
      builder.finish(root);

      UnitStates replica = new UnitStates().init(ally);
      replica.stateList.addStateLayer(StateId.MIGHT, 99, 1, 999, 9001);
      serializer.getData(EntitySync.getRootAsEntitySync(builder.dataBuffer()), 0, replica);

      assertEquals(1, replica.stateList.size(),
          "reconnect must replace the snapshot, not retain a stale source layer");
      assertTrue(replica.stateList.getStateLayer(StateId.MIGHT, 999, 9001) == null);
      UnitState restored = replica.stateList.getStateLayer(
          StateId.MIGHT, caster, SkillId.MIGHT);
      assertNotNull(restored);
      assertEquals(3, restored.level);
      assertEquals(authoritative.duration, restored.duration);
    }
  }

  private static final class Harness implements AutoCloseable {
    final PartyManager parties = new PartyManager();
    final AuraEcsSystem auras = new AuraEcsSystem();
    final NoopFactory factory = new NoopFactory();
    final Map map = new Map(0, 0);
    final World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new StateUpdater(), auras, factory).build()
        .register("factory", factory)
        .register("map", map)
        .register("partyManager", parties));

    Harness() {
      world.setDelta(1f / 25f);
    }

    void tick() {
      world.process();
    }

    void ticks(int count) {
      for (int i = 0; i < count; i++) tick();
    }

    @Override public void close() {
      world.dispose();
    }
  }

  private static void party(PartyManager parties, int leader, int member) {
    short party = parties.createParty(leader);
    assertTrue(party >= 0);
    assertTrue(parties.joinParty(party, member));
  }

  private static int player(World world, float x, float y) {
    int id = unit(world, x, y);
    world.getMapper(Player.class).create(id);
    return id;
  }

  private static int monster(World world, float x, float y) {
    int id = unit(world, x, y);
    world.getMapper(Monster.class).create(id);
    return id;
  }

  private static int monsterWith(World world, float x, float y, boolean noAura,
      boolean boss, boolean primeEvil, boolean isAtt, boolean selectable) {
    int id = monster(world, x, y);
    MonStats.Entry stats = new MonStats.Entry();
    stats.noAura = noAura;
    stats.boss = boss;
    stats.primeevil = primeEvil;
    stats.npc = false;
    world.getMapper(Monster.class).get(id).monstats = stats;
    MonStats2.Entry stats2 = new MonStats2.Entry();
    stats2.isAtt = isAtt;
    stats2.noSel = !selectable;
    world.getMapper(Monster.class).get(id).monstats2 = stats2;
    return id;
  }

  private static int mercenary(World world, int owner, float x, float y) {
    int id = monster(world, x, y);
    world.getMapper(Mercenary.class).create(id).ownerId = owner;
    return id;
  }

  private static int summon(World world, int owner, float x, float y) {
    int id = monster(world, x, y);
    world.getMapper(SummonedPet.class).create(id).ownerId = owner;
    return id;
  }

  private static int unit(World world, float x, float y) {
    int id = world.create();
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(UnitStates.class).create(id).init(id);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes();
    return id;
  }

  private static void zone(World world, int entityId, Map.Zone zone) {
    world.getMapper(MapWrapper.class).create(entityId).zone = zone;
  }

  private static Vector2 position(World world, int entityId) {
    return world.getMapper(Position.class).get(entityId).position;
  }

  private static StateList states(World world, int id) {
    return world.getMapper(UnitStates.class).get(id).stateList;
  }

  private static void life(World world, int entityId, int value) {
    world.getMapper(AttributesWrapper.class).get(entityId).attrs.get(Stat.hitpoints).set(value);
  }

  private static float life(World world, int entityId) {
    return world.getMapper(AttributesWrapper.class).get(entityId)
        .attrs.get(Stat.hitpoints).asFixed();
  }

  private static void mana(World world, int entityId, float value) {
    world.getMapper(AttributesWrapper.class).get(entityId).attrs.get(Stat.mana).set(value);
  }

  private static float mana(World world, int entityId) {
    return world.getMapper(AttributesWrapper.class).get(entityId)
        .attrs.get(Stat.mana).asFixed();
  }

  private static Attributes attributes() {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.hitpoints, 100);
    attrs.base().put(Stat.maxhp, 100);
    attrs.base().put(Stat.mana, 100);
    attrs.base().put(Stat.maxmana, 100);
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
  }
}
