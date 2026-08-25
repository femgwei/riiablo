package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.MathUtils;
import com.riiablo.CharacterClass;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import com.riiablo.codec.excel.SuperUniques;
import com.riiablo.engine.server.combat.CombatSystem;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Headless characterization audit for every hostile monster reachable from
 * Act I Levels/MonPreset data. The audit intentionally reports known wiring
 * gaps without hiding the direct-attack coverage that already works.
 */
class Act1MonsterCombatAuditTest extends RiiabloTest {
  private static final int PLAYER_LEVEL = 2;
  private static final int ATTEMPTS_PER_PROFILE = 512;

  @Test
  void everyAct1DirectAttackProfileCanHitAndDealDamageUsingNativeFormula() {
    Map<String, MonStats.Entry> roster = act1Roster();
    int hostile = 0;
    int directProfiles = 0;
    int rangedProfiles = 0;

    for (MonStats.Entry monster : roster.values()) {
      if (!isHostile(monster)) continue;
      hostile++;
      int level = Math.max(1, value(monster.Level));

      MonsterStatsCalculator.MonsterStatsInit a1 = profile(monster, level, (short) 0x08);
      if (a1.TH > 0 && a1.A1MaxD > 0) {
        auditDamageProfile(monster, "A1", level, a1.TH, a1.A1MinD, a1.A1MaxD,
            hasText(monster.MissA1));
        directProfiles++;
        if (hasText(monster.MissA1)) rangedProfiles++;
      }

      MonsterStatsCalculator.MonsterStatsInit a2 = profile(monster, level, (short) 0x10);
      if (a2.TH > 0 && a2.A2MaxD > 0) {
        auditDamageProfile(monster, "A2", level, a2.TH, a2.A2MinD, a2.A2MaxD,
            hasText(monster.MissA2));
        directProfiles++;
        if (hasText(monster.MissA2)) rangedProfiles++;
      }
    }

    assertFalse(roster.isEmpty());
    assertTrue(hostile > 0);
    assertTrue(directProfiles > 0);
    System.out.println("[ACT1_COMBAT_AUDIT] phase=direct_profiles roster=" + roster.size()
        + " hostile=" + hostile + " profiles=" + directProfiles
        + " rangedProfiles=" + rangedProfiles + " status=PASS");
  }

  @Test
  void reportsAct1ProjectileAndAiWiringGaps() {
    Map<String, MonStats.Entry> roster = act1Roster();
    Set<String> zeroProfileMissiles = new LinkedHashSet<>();
    Set<String> missingMissiles = new LinkedHashSet<>();
    Set<String> fallbackAis = new LinkedHashSet<>();
    Set<String> stationaryFallbacks = new LinkedHashSet<>();
    Set<String> unresolvedPresets = unresolvedAct1Presets();
    int projectileSkills = 0;

    for (MonStats.Entry monster : roster.values()) {
      if (!isHostile(monster)) continue;
      boolean specialized = hasSpecializedAi(monster.AI);
      if (!specialized) {
        fallbackAis.add(monster.Id + "(" + monster.AI + ")");
        if (monster.Velocity <= 0 && hasProjectileSkill(monster)) {
          stationaryFallbacks.add(monster.Id + "(" + monster.AI + ")");
        }
      }

      int level = Math.max(1, value(monster.Level));
      MonsterStatsCalculator.MonsterStatsInit a1 = profile(monster, level, (short) 0x08);
      MonsterStatsCalculator.MonsterStatsInit a2 = profile(monster, level, (short) 0x10);
      auditMissile(monster.Id, "A1", monster.MissA1, a1.TH, a1.A1MaxD,
          zeroProfileMissiles, missingMissiles);
      auditMissile(monster.Id, "A2", monster.MissA2, a2.TH, a2.A2MaxD,
          zeroProfileMissiles, missingMissiles);
      projectileSkills += countProjectileSkills(monster, missingMissiles);
    }

    System.out.println("[ACT1_COMBAT_AUDIT] phase=wiring zeroProfileMissiles="
        + zeroProfileMissiles);
    System.out.println("[ACT1_COMBAT_AUDIT] phase=wiring missingMissiles=" + missingMissiles);
    System.out.println("[ACT1_COMBAT_AUDIT] phase=wiring fallbackAis=" + fallbackAis);
    System.out.println("[ACT1_COMBAT_AUDIT] phase=wiring stationaryFallbacks="
        + stationaryFallbacks);
    System.out.println("[ACT1_COMBAT_AUDIT] phase=wiring unresolvedPresets="
        + unresolvedPresets);
    System.out.println("[ACT1_COMBAT_AUDIT] phase=wiring projectileSkills="
        + projectileSkills + " status="
        + (zeroProfileMissiles.isEmpty() && missingMissiles.isEmpty()
            && stationaryFallbacks.isEmpty() && unresolvedPresets.isEmpty()
            ? "PASS" : "ISSUES"));

    assertFalse(zeroProfileMissiles.isEmpty(),
        "characterization: Act I skeleton mage missiles currently have no combat profile");
    assertTrue(projectileSkills > 0);
  }

  @Test
  void matchesNativeAmazonDefenseAndMonsterHitChance() {
    CharacterClass clazz = CharacterClass.AMAZON;
    CharData player = CharData.obtain().clear()
        .set(Riiablo.NORMAL, false, "DefenseAudit", (byte) clazz.id);
    player.mapSeed = 0x41433144;
    com.riiablo.codec.excel.CharStats.Entry start = clazz.entry();
    player.getStats().base().put(Stat.dexterity, start.dex);
    player.getStats().base().put(Stat.level, PLAYER_LEVEL);
    player.getStats().reset();
    player.initializeStartItems(start);
    player.update();

    Item shield = player.getItems().getSlot(BodyLoc.LARM);
    assertNotNull(shield);
    int shieldDefense = statInt(shield.attrs, Stat.armorclass);
    int expectedNativeDefense = start.dex / 4 + shieldDefense;
    int actualDefense = statInt(player.getStats(), Stat.armorclass);
    int zombieAr = profile(Riiablo.files.monstats.get("zombie1"), 1, (short) 0x08).TH;
    int expectedChance = nativeChance(zombieAr, expectedNativeDefense, 1, PLAYER_LEVEL);
    int actualChance = nativeChance(zombieAr, actualDefense, 1, PLAYER_LEVEL);

    System.out.println("[ACT1_DEFENSE_AUDIT] class=amazon dex=" + start.dex
        + " shieldDefense=" + shieldDefense + " expectedDefense=" + expectedNativeDefense
        + " actualDefense=" + actualDefense + " zombieAr=" + zombieAr
        + " expectedChance=" + expectedChance + " actualChance=" + actualChance
        + " status=" + (expectedNativeDefense == actualDefense ? "PASS" : "FAIL"));

    assertEquals(expectedNativeDefense, actualDefense);
    assertEquals(expectedChance, actualChance);

    // A public character refresh must rebuild item aggregation first; otherwise
    // dexterity / 4 would be added repeatedly to the previous aggregate.
    player.update();
    assertEquals(expectedNativeDefense, statInt(player.getStats(), Stat.armorclass));
  }

  private static void auditDamageProfile(MonStats.Entry monster, String mode, int level,
      int attackRating, int minDamage, int maxDamage, boolean missile) {
    Attributes attacker = combatAttributes(100, level, 0, minDamage, maxDamage, attackRating);
    Attributes defender = combatAttributes(10000, PLAYER_LEVEL, 11, 0, 0, 0);
    int expectedChance = nativeChance(attackRating, 11, level, PLAYER_LEVEL);
    int hits = 0;
    int damagingHits = 0;
    MathUtils.random.setSeed(0x41435431L + monster.hcIdx * 31L + mode.hashCode());
    for (int i = 0; i < ATTEMPTS_PER_PROFILE; i++) {
      CombatSystem.CombatResult result = CombatSystem.INSTANCE.calculateAttack(
          attacker, defender, false, true, missile,
          minDamage, maxDamage, attackRating);
      assertEquals(expectedChance, result.hitChance, monster.Id + " " + mode);
      if (result.hit) {
        hits++;
        if (result.totalDamage > 0) damagingHits++;
      }
    }
    assertTrue(hits > 0, monster.Id + " " + mode + " never hit");
    assertEquals(hits, damagingHits, monster.Id + " " + mode + " hit without damage");
    System.out.println("[ACT1_MONSTER_ATTACK] monster=" + monster.Id + " mode=" + mode
        + " level=" + level + " ar=" + attackRating + " damage=" + minDamage + ".."
        + maxDamage + " chance=" + expectedChance + " hits=" + hits + "/"
        + ATTEMPTS_PER_PROFILE + " missile=" + missile + " status=PASS");
  }

  private static void auditMissile(String monsterId, String mode, String missileName,
      int attackRating, int maxDamage, Set<String> zeroProfiles, Set<String> missing) {
    if (!hasText(missileName)) return;
    Missiles.Entry missile = Riiablo.files.Missiles.get(missileName);
    if (missile == null) {
      missing.add(monsterId + ":" + mode + "=" + missileName);
    } else if (attackRating <= 0 || maxDamage <= 0) {
      zeroProfiles.add(monsterId + ":" + mode + "=" + missileName);
    }
  }

  private static int countProjectileSkills(MonStats.Entry monster, Set<String> missing) {
    String[] names = {monster.Skill1, monster.Skill2, monster.Skill3, monster.Skill4,
        monster.Skill5, monster.Skill6, monster.Skill7, monster.Skill8};
    int count = 0;
    for (String name : names) {
      if (!hasText(name)) continue;
      Skills.Entry skill = Riiablo.files.skills.get(name);
      if (skill == null) {
        missing.add(monster.Id + ":skill=" + name);
        continue;
      }
      String[] missiles = {skill.srvmissilea, skill.srvmissileb, skill.srvmissilec,
          skill.srvmissiled, skill.cltmissilea, skill.cltmissileb,
          skill.cltmissilec, skill.cltmissiled};
      boolean projectile = false;
      for (String missileName : missiles) {
        if (!hasText(missileName)) continue;
        projectile = true;
        if (Riiablo.files.Missiles.get(missileName) == null) {
          missing.add(monster.Id + ":skill=" + name + ":missile=" + missileName);
        }
      }
      if (projectile) count++;
    }
    return count;
  }

  private static boolean hasProjectileSkill(MonStats.Entry monster) {
    return countProjectileSkills(monster, new LinkedHashSet<String>()) > 0;
  }

  private static boolean hasSpecializedAi(String ai) {
    if (!hasText(ai)) return false;
    try {
      java.lang.Class.forName("com.riiablo.engine.server.ai." + ai);
      return true;
    } catch (ClassNotFoundException ignored) {
      return false;
    }
  }

  private static MonsterStatsCalculator.MonsterStatsInit profile(
      MonStats.Entry monster, int level, short flags) {
    assertNotNull(monster);
    MonsterStatsCalculator.MonsterStatsInit result =
        new MonsterStatsCalculator.MonsterStatsInit();
    assertTrue(MonsterStatsCalculator.calculateMonsterStatsByLevel(
        monster.hcIdx, 1, 0, level, flags, result), monster.Id);
    return result;
  }

  private static Attributes combatAttributes(int hp, int level, int defense,
      int minDamage, int maxDamage, int attackRating) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, hp);
    attrs.base().put(Stat.level, level);
    attrs.base().put(Stat.armorclass, defense);
    attrs.base().put(Stat.mindamage, minDamage);
    attrs.base().put(Stat.maxdamage, maxDamage);
    attrs.base().put(Stat.tohit, attackRating);
    attrs.reset();
    return attrs;
  }

  private static int nativeChance(int attackRating, int defense,
      int attackerLevel, int defenderLevel) {
    int divisor = attackRating + defense;
    int factor = divisor == 0 ? 100 : 100 * attackRating / divisor;
    int chance = 2 * attackerLevel * factor / (attackerLevel + defenderLevel);
    return Math.max(5, Math.min(95, chance));
  }

  private static Map<String, MonStats.Entry> act1Roster() {
    Map<String, MonStats.Entry> roster = new LinkedHashMap<>();
    for (Levels.Entry level : Riiablo.files.Levels) {
      if (level.Act != 0) continue;
      addAll(roster, level.mon);
      addAll(roster, level.umon);
    }
    for (int i = 0; i < Riiablo.files.MonPreset.getSize(1); i++) {
      addPreset(roster, Riiablo.files.MonPreset.getPlace(1, i));
    }
    addRelations(roster);
    return roster;
  }

  private static Set<String> unresolvedAct1Presets() {
    Set<String> unresolved = new LinkedHashSet<>();
    for (int i = 0; i < Riiablo.files.MonPreset.getSize(1); i++) {
      String place = Riiablo.files.MonPreset.getPlace(1, i);
      if (Riiablo.files.monstats.get(place) != null) continue;
      SuperUniques.Entry unique = Riiablo.files.SuperUniques.get(place);
      if (unique == null || Riiablo.files.monstats.get(unique.MonClass) == null) {
        unresolved.add(place);
      }
    }
    return unresolved;
  }

  private static void addPreset(Map<String, MonStats.Entry> roster, String place) {
    MonStats.Entry monster = Riiablo.files.monstats.get(place);
    // Native MonPreset uses a placement directive rather than the MonStats id
    // for this quest boss. Include the intended row in the audit even though
    // ServerEntityFactory currently cannot resolve the directive itself.
    if (monster == null && "place_bloodraven".equalsIgnoreCase(place)) {
      monster = Riiablo.files.monstats.get("bloodraven");
    }
    if (monster == null) {
      SuperUniques.Entry unique = Riiablo.files.SuperUniques.get(place);
      if (unique != null) monster = Riiablo.files.monstats.get(unique.MonClass);
    }
    add(roster, monster);
  }

  private static void addRelations(Map<String, MonStats.Entry> roster) {
    boolean changed;
    do {
      changed = false;
      MonStats.Entry[] snapshot = roster.values().toArray(new MonStats.Entry[0]);
      for (MonStats.Entry monster : snapshot) {
        int before = roster.size();
        add(roster, Riiablo.files.monstats.get(monster.minion1));
        add(roster, Riiablo.files.monstats.get(monster.minion2));
        add(roster, Riiablo.files.monstats.get(monster.spawn));
        changed |= roster.size() != before;
      }
    } while (changed);
  }

  private static boolean isHostile(MonStats.Entry monster) {
    return monster.enabled && monster.killable && !monster.npc && monster.Align == 0;
  }

  private static void addAll(Map<String, MonStats.Entry> roster, String[] ids) {
    if (ids == null) return;
    for (String id : ids) add(roster, Riiablo.files.monstats.get(id));
  }

  private static void add(Map<String, MonStats.Entry> roster, MonStats.Entry monster) {
    if (monster != null && hasText(monster.Id)) roster.put(monster.Id, monster);
  }

  private static int statInt(Attributes attrs, short stat) {
    StatRef ref = attrs.get(stat, StatRef.obtain());
    return ref != null ? ref.asInt() : 0;
  }

  private static int value(int[] values) {
    return values != null && values.length > 0 ? values[0] : 0;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isEmpty();
  }
}
