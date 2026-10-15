package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.annotations.Wire;
import com.badlogic.gdx.utils.Array;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.NativeHirelingExperienceTable;
import com.riiablo.engine.server.NativeHirelingStatsUpdater;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.npc.HirelingOffer;
import com.riiablo.engine.server.pet.MercenaryManager;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Real MPQ rows + the production selected-offer/wallet/save transaction; entity factory is isolated. */
class NativeHirelingOfferIntegrationTest extends RiiabloTest {
  @Test void actFiveNpcConstantsAreNativeHcIdxNotTextRowNumbers() {
    int[] ids = {
        com.riiablo.engine.server.monster.MonsterType.LARZUK,
        com.riiablo.engine.server.monster.MonsterType.DREHYA,
        com.riiablo.engine.server.monster.MonsterType.MALAH,
        com.riiablo.engine.server.monster.MonsterType.NIHLATHAK,
        com.riiablo.engine.server.monster.MonsterType.QUAL_KEHK,
        com.riiablo.engine.server.monster.MonsterType.TYRAEL3
    };
    String[] names = {"larzuk", "drehya", "malah", "nihlathak", "qual-kehk", "tyrael3"};
    for (int i = 0; i < ids.length; i++) {
      assertEquals(ids[i], Riiablo.files.monstats.get(names[i]).hcIdx);
      assertEquals(names[i], Riiablo.files.monstats.get(ids[i]).Id);
    }
    assertEquals("act5hire2", Riiablo.files.monstats.get(
        com.riiablo.engine.server.monster.MonsterType.HIRELING_BARBARIAN).Id);
  }
  @Test void skillCategoryCodesResolveRealNativeLabels() {
    assertEquals("Combat", Riiablo.string.lookup(
        com.riiablo.screen.panel.HireMercenaryPanel.descriptionKey("comb")));
    assertEquals("Defensive", Riiablo.string.lookup(
        com.riiablo.screen.panel.HireMercenaryPanel.descriptionKey("def")));
    assertEquals("Offensive", Riiablo.string.lookup(
        com.riiablo.screen.panel.HireMercenaryPanel.descriptionKey("off")));
    for (String code : new String[] {"farw", "carw", "fire", "cold", "ltng"}) {
      assertFalse(Riiablo.string.lookup(com.riiablo.screen.panel.HireMercenaryPanel.descriptionKey(code))
          .startsWith("ERROR:"));
    }
  }
  @Test void originalRowsOfferAllActTwoSkillsInEachDifficulty() {
    NativeHirelingExperienceTable table = NativeHirelingExperienceTable.load();
    assertTrue(table.size() > 0);
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      MercenaryManager manager = new MercenaryManager();
      manager.setNativeExperienceTable(table);
      manager.setRosterDifficulty(difficulty);
      Array<HirelingOffer> offers = manager.hirelingOffers(1, 198, 80);
      com.badlogic.gdx.utils.IntSet ids = new com.badlogic.gdx.utils.IntSet();
      for (HirelingOffer offer : offers) {
        ids.add(offer.hirelingId);
        NativeHirelingExperienceTable.Row row = table.row(offer.hirelingId, offer.level);
        assertEquals(row.description, offer.description);
        assertEquals(NativeHirelingExperienceTable.hireCost(row, offer.level), offer.cost);
        assertEquals(table.stats(offer.hirelingId, offer.level).hitpoints, offer.life);
        assertEquals(table.stats(offer.hirelingId, offer.level).defense, offer.defense);
      }
      for (int id = 6 + 3*difficulty; id < 9 + 3*difficulty; id++) assertTrue(ids.contains(id));
      for (int npc : new int[] {150, 252, 515}) {
        Array<HirelingOffer> otherAct = manager.hirelingOffers(1, npc, 80);
        assertTrue(otherAct.size > 0, "native roster for NPC " + npc + " difficulty " + difficulty);
        for (HirelingOffer offer : otherAct) {
          assertEquals(MercenaryManager.mercenaryTypeForNpc(npc), offer.mercType);
          assertEquals(offer.mercType, NativeHirelingExperienceTable.mercenaryType(offer.hirelingId));
        }
      }
    }
  }

  @Test void selectedHireUsesBankGoldPersistsSkillAndRestoresTheSameVariant() {
    StubFactory rewards = new StubFactory();
    World world = new World(new WorldConfigurationBuilder().with(new EventSystem(), rewards).build());
    try {
      int player = world.create();
      CharData data = CharData.obtain().clear().set(0, false, "HireTest", Riiablo.AMAZON);
      world.getMapper(Player.class).create(player).data = data;
      world.getMapper(AttributesWrapper.class).create(player).attrs = data.getStats();
      data.getStats().base().put(Stat.level, 70);
      data.getStats().aggregate().put(Stat.level, 70);
      data.getStats().base().put(Stat.gold, 1);
      data.getStats().aggregate().put(Stat.gold, 1);
      data.getStats().base().put(Stat.goldbank, 100_000);
      data.getStats().aggregate().put(Stat.goldbank, 100_000);
      world.process();
      Array<HirelingOffer> offers = rewards.hirelingOffers(player, 198);
      HirelingOffer selected = null;
      for (HirelingOffer offer : offers) if (offer.hirelingId == 8 && offer.level >= 18) {
        selected = offer;
        break;
      }
      assertNotNull(selected);
      assertEquals(1, data.getStats().get(Stat.gold).asInt());
      assertEquals(0, rewards.created);
      assertNull(rewards.hireSelectedMercenary(player, 198, selected.index, selected.seed, 0));
      assertEquals(selected.seed, data.getMerc().seed);
      assertEquals(8, data.getMerc().type);
      assertEquals(0, data.getStats().get(Stat.gold).asInt());
      assertEquals(100_001 - selected.cost, data.getStats().get(Stat.goldbank).asInt());
      int entity = rewards.mercenaryEntityId(player);
      Mercenary component = world.getMapper(Mercenary.class).get(entity);
      assertEquals(1, component.mercType);
      assertEquals(8, component.hirelingId);
      assertEquals(Riiablo.files.skills.index("Blessed Aim"), component.skills[1]);
      int created = rewards.created;
      assertEquals("MERCENARY_CHANGED", rewards.hireSelectedMercenary(player, 198,
          selected.index, selected.seed, 0));
      assertEquals(created, rewards.created);
      byte[] encoded = new com.riiablo.save.D2SWriter96().writeD2S(
          com.riiablo.save.D2SWriter96.createD2S(data));
      rewards.unloadPersistedMercenary(player);
      world.process();
      data = CharData.obtain().clear().set(Riiablo.NORMAL, false)
          .load(com.riiablo.save.D2SReader.INSTANCE.readComplete(encoded));
      world.getMapper(Player.class).get(player).data = data;
      world.getMapper(AttributesWrapper.class).get(player).attrs = data.getStats();
      assertEquals(selected.seed, data.getMerc().seed);
      assertEquals(8, data.getMerc().type);
      assertTrue(rewards.restorePersistedMercenary(player));
      component = world.getMapper(Mercenary.class).get(rewards.mercenaryEntityId(player));
      assertEquals(8, component.hirelingId);
      assertEquals(Riiablo.files.skills.index("Blessed Aim"), component.skills[1]);
      assertEquals(100_001 - selected.cost, data.getStats().get(Stat.goldbank).asInt());
    } finally { world.dispose(); }
  }

  @Test void failedAndUnconfirmedReplacementPreservesOldSaveAndSuccessfulReplacementClearsDeath() {
    StubFactory rewards = new StubFactory();
    World world = new World(new WorldConfigurationBuilder().with(new EventSystem(), rewards).build());
    try {
      int player = world.create();
      CharData data = CharData.obtain().clear().set(0, false, "ReplaceTest", Riiablo.AMAZON);
      world.getMapper(Player.class).create(player).data = data;
      world.getMapper(AttributesWrapper.class).create(player).attrs = data.getStats();
      for (short stat : new short[] {Stat.level, Stat.gold}) {
        data.getStats().base().put(stat, stat == Stat.level ? 70 : 100_000);
        data.getStats().aggregate().put(stat, stat == Stat.level ? 70 : 100_000);
      }
      world.process();
      HirelingOffer first = rewards.hirelingOffers(player, 198).first();
      assertNull(rewards.hireSelectedMercenary(player, 198, first.index, first.seed, 0));
      HirelingOffer replacement = rewards.hirelingOffers(player, 252).first();
      int gold = data.getStats().get(Stat.gold).asInt();
      data.getMerc().flags |= MercenaryManager.FLAG_DEAD;
      data.getMerc().getStats().base().put(Stat.item_magicbonus, 123);
      assertEquals("MERCENARY_CHANGED", rewards.hireSelectedMercenary(player, 252,
          replacement.index, replacement.seed, 0));
      rewards.fail = true;
      assertEquals("HIRE_REJECTED", rewards.hireSelectedMercenary(player, 252,
          replacement.index, replacement.seed, first.seed));
      assertEquals(first.seed, data.getMerc().seed);
      assertEquals(gold, data.getStats().get(Stat.gold).asInt());
      assertEquals(123, data.getMerc().getStats().base().get(Stat.item_magicbonus).asInt());
      assertTrue((data.getMerc().flags & MercenaryManager.FLAG_DEAD) != 0);
      rewards.fail = false;
      assertNull(rewards.hireSelectedMercenary(player, 252,
          replacement.index, replacement.seed, first.seed));
      assertEquals(replacement.seed, data.getMerc().seed);
      assertEquals(replacement.hirelingId, data.getMerc().type);
      assertEquals(0, data.getMerc().flags);
      assertNull(data.getMerc().getStats().base().get(Stat.item_magicbonus));
      assertEquals(gold - replacement.cost, data.getStats().get(Stat.gold).asInt());
    } finally { world.dispose(); }
  }

  @Wire(failOnNull = false, injectInherited = true)
  private static final class StubFactory extends NativeMercenaryRewardSystem {
    int created;
    boolean fail;
    @Override public int createMercenaryEntity(int owner, MercenaryManager.MercenaryDefinition def,
        int level, int seed, int name) {
      if (fail) return Engine.INVALID_ENTITY;
      created++;
      int entity = world.create();
      Mercenary merc = world.getMapper(Mercenary.class).create(entity).set(owner, def.mercType, level, seed, name);
      merc.hirelingId = def.hirelingId;
      Attributes attrs = Attributes.obtainLarge();
      world.getMapper(AttributesWrapper.class).create(entity).attrs = attrs;
      NativeHirelingStatsUpdater.applySkills(merc,
          NativeHirelingExperienceTable.load().stats(def.hirelingId(), level));
      return entity;
    }
  }
}
