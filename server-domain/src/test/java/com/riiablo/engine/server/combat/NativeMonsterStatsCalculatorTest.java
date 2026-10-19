package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.*;

import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.MonLvl;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.monster.MonsterRank;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class NativeMonsterStatsCalculatorTest {
  static NativeCombatContext context(int difficulty, boolean expansion, int count) {
    return NativeCombatContext.forGameplay(difficulty, null, expansion).withPlayerCount(count);
  }

  static MonStats.Entry monster() {
    MonStats.Entry row = new MonStats.Entry();
    row.Level = new int[] {5, 40, 85};
    row.minHP = new int[] {100, 100, 100}; row.maxHP = new int[] {200, 200, 200};
    row.AC = new int[] {150, 150, 150}; row.Exp = new int[] {200, 200, 200};
    row.A1TH = new int[] {100, 100, 100}; row.A2TH = new int[] {200, 200, 200};
    row.S1TH = new int[] {300, 300, 300};
    row.A1MinD = new int[] {100, 100, 100}; row.A1MaxD = new int[] {200, 200, 200};
    row.A2MinD = new int[] {300, 300, 300}; row.A2MaxD = new int[] {400, 400, 400};
    row.S1MinD = new int[] {500, 500, 500}; row.S1MaxD = new int[] {600, 600, 600};
    row.El1MinD = new int[] {100, 100, 100}; row.El1MaxD = new int[] {200, 200, 200};
    row.El2MinD = new int[] {300, 300, 300}; row.El2MaxD = new int[] {400, 400, 400};
    row.El3MinD = new int[] {500, 500, 500}; row.El3MaxD = new int[] {600, 600, 600};
    row.El1Dur = new int[] {17, 18, 19}; row.El2Dur = new int[] {27, 28, 29};
    row.El3Dur = new int[] {37, 38, 39};
    return row;
  }

  static MonLvl.Entry level() {
    MonLvl.Entry row = new MonLvl.Entry(); row.Level = 40;
    row.LHP = new int[] {101, 101, 101}; row.HP = new int[] {51, 51, 51};
    row.LAC = new int[] {20, 20, 20}; row.AC = new int[] {10, 10, 10};
    row.LTH = new int[] {40, 40, 40}; row.TH = new int[] {20, 20, 20};
    row.LDM = new int[] {10, 10, 10}; row.DM = new int[] {5, 5, 5};
    row.LXP = new int[] {80, 80, 80}; row.XP = new int[] {40, 40, 40};
    return row;
  }

  @Test void selectsClassicAndLodColumnsWithoutApplyingSpawnOrModeBonuses() {
    NativeMonsterStatsCalculator.TableStats lod = new NativeMonsterStatsCalculator.TableStats();
    NativeMonsterStatsCalculator.TableStats classic = new NativeMonsterStatsCalculator.TableStats();
    assertTrue(NativeMonsterStatsCalculator.calculateTableStats(context(1,true,8), monster(), level(), 15, lod));
    assertTrue(NativeMonsterStatsCalculator.calculateTableStats(context(1,false,8), monster(), level(), 15, classic));
    assertEquals(101, lod.minHP); assertEquals(202, lod.maxHP); assertEquals(30, lod.AC);
    assertEquals(160, lod.Exp); assertEquals(40, lod.TH); assertEquals(10, lod.A1MinD);
    assertEquals(51, classic.minHP); assertEquals(20, classic.TH); assertEquals(5, classic.A1MinD);
  }

  @Test void flagsKeepUnselectedFieldsAndAttackOverwriteOrder() {
    NativeMonsterStatsCalculator.TableStats out = new NativeMonsterStatsCalculator.TableStats();
    out.minHP = 777; out.ElDur = 888;
    NativeMonsterStatsCalculator.calculateTableStats(context(0,true,1), monster(), level(), 0x38, out);
    assertEquals(777, out.minHP); assertEquals(888, out.ElDur);
    assertEquals(120, out.TH); assertEquals(10, out.A1MinD);
    assertEquals(30, out.A2MinD); assertEquals(50, out.S1MinD);
  }

  @Test void elementalFlagsAndNoRatioPreserveGenericCompatibilityContract() {
    MonStats.Entry row = monster(); row.noRatio = true;
    NativeMonsterStatsCalculator.TableStats out = new NativeMonsterStatsCalculator.TableStats();
    NativeMonsterStatsCalculator.calculateTableStats(context(2,true,8), row, level(), 0x180, out);
    assertEquals(300, out.ElMinD); assertEquals(400, out.ElMaxD); assertEquals(29, out.ElDur);
    NativeMonsterStatsCalculator.calculateTableStats(context(2,true,8), row, level(), 0x42, out);
    assertEquals(100, out.ElMinD); assertEquals(19, out.ElDur);
    assertEquals(150, out.AC);
  }

  @Test void missingStatColumnsStayZeroAndInvalidRowsDoNotMutateOutput() {
    MonStats.Entry row = monster(); row.A1TH = new int[] {42};
    NativeMonsterStatsCalculator.TableStats out = new NativeMonsterStatsCalculator.TableStats();
    NativeMonsterStatsCalculator.calculateTableStats(context(2,true,1), row, level(), 8, out);
    assertEquals(0, out.TH);
    out.minHP = 77;
    assertFalse(NativeMonsterStatsCalculator.calculateTableStats(context(0,true,1), null, level(), 1, out));
    assertEquals(77, out.minHP);
    assertFalse(NativeMonsterStatsCalculator.calculateTableStats(context(0,true,1), row, null, 1, out));
  }

  @Test void selectsAreaLevelOnlyForEligibleExpansionMonsters() {
    MonStats.Entry row = monster(); Levels.Entry area = new Levels.Entry();
    area.MonLvlEx = new int[] {9, 44, 88};
    assertEquals(5, NativeMonsterStatsCalculator.resolveMonsterLevel(row, area, context(0,true,1)));
    assertEquals(44, NativeMonsterStatsCalculator.resolveMonsterLevel(row, area, context(1,true,1)));
    assertEquals(40, NativeMonsterStatsCalculator.resolveMonsterLevel(row, area, context(1,false,1)));
    row.boss = true;
    assertEquals(40, NativeMonsterStatsCalculator.resolveMonsterLevel(row, area, context(1,true,1)));
    row.boss = false; row.noRatio = true;
    assertEquals(40, NativeMonsterStatsCalculator.resolveMonsterLevel(row, area, context(1,true,1)));
    row.noRatio = false; area.MonLvlEx = new int[] {0}; row.Level = new int[] {7};
    assertEquals(7, NativeMonsterStatsCalculator.resolveMonsterLevel(row, area, context(2,true,1)));
  }

  @Test void snapshotKeepsBaseLevelAndEachAttackRatingSeparate() {
    MonsterStatsSnapshot out = NativeMonsterStatsCalculator.spawnSnapshot(context(1,true,2),
        monster(), level(), 40, MonsterRank.UNIQUE);
    assertEquals(40, out.baseLevel); assertEquals(40, out.tableLevel); assertEquals(43, out.level);
    assertEquals(453, out.minHp); // (101 + floor(101/2)) * 3
    assertEquals(909, out.maxHp); // (202 + 101) * 3
    assertEquals(1200, out.experience);
    assertEquals(42, out.attack1.attackRating); assertEquals(85, out.attack2.attackRating);
    assertEquals(127, out.skill1.attackRating); assertEquals(8, out.combatMultiplier);
    assertEquals(31, out.attack2.minDamage);
  }

  @Test void spawnScalesTheDrawnBaseHpRatherThanDrawingUniformlyFromScaledEndpoints() {
    MonStats.Entry row = monster(); row.noRatio = true;
    row.minHP = new int[] {3,3,3}; row.maxHP = new int[] {5,5,5};
    MonsterStatsSnapshot out = NativeMonsterStatsCalculator.spawnSnapshot(context(0,true,2), row,
        level(), 5, MonsterRank.NORMAL);
    assertEquals(4, out.minHp); assertEquals(7, out.maxHp);
    assertEquals(6, out.hitpointsForBaseRoll(4));
    assertThrows(IllegalArgumentException.class, () -> out.hitpointsForBaseRoll(6));
  }

  @Test void explicitCountsKeepNinePlusBranchesButContextOverloadKeepsItsExistingCap() {
    MonsterStatsSnapshot explicit = NativeMonsterStatsCalculator.spawnSnapshot(context(1,true,10),
        monster(), level(), 40, 0, 10);
    MonsterStatsSnapshot convenience = NativeMonsterStatsCalculator.spawnSnapshot(context(1,true,10),
        monster(), level(), 40, 0);
    assertEquals(10, explicit.playerCount); assertEquals(64, explicit.combatMultiplier);
    assertEquals(400, explicit.hpBonusPercent); assertEquals(505, explicit.minHp);
    assertEquals(736, explicit.experience);
    assertEquals(8, convenience.playerCount); assertEquals(56, convenience.combatMultiplier);
  }

  @Test void alliedMonstersIgnoreConnectedPlayerBonuses() {
    MonStats.Entry row = monster(); row.Align = 1;
    MonsterStatsSnapshot out = NativeMonsterStatsCalculator.spawnSnapshot(context(2,true,8), row,
        level(), 85, 0, 10);
    assertEquals(1, out.playerCount); assertEquals(0, out.combatMultiplier);
    assertEquals(101, out.minHp); assertEquals(160, out.experience);
  }

  @Test void snapshotOwnsValuesAndDoesNotRetainMutableRows() {
    MonStats.Entry row = monster(); MonLvl.Entry level = level();
    MonsterStatsSnapshot out = NativeMonsterStatsCalculator.spawnSnapshot(context(1,true,1), row, level, 40, 0);
    row.minHP[1] = 999; row.A2TH[1] = 999; level.LHP[1] = 999; level.LTH[1] = 999;
    assertEquals(101, out.minHp); assertEquals(80, out.attack2.attackRating);
    assertEquals(101, out.hitpointsForBaseRoll(101));
  }

  @Test void largeSpawnPercentPreservesWideMultiplyWhileTableRatioKeepsEarlyDivision() {
    assertEquals(1_500_001, NativeMonsterStatsCalculator.spawnHitpoints(1_000_001, 2, 0, 0));
    assertEquals(3_000_298, NativeMonsterStatsCalculator.spawnHitpoints(2_000_199, 2, 0, 0));
    assertEquals(1_000_050, NativeMonsterStatsCalculator.applyRatio(2_000_199, 50, 100));
    assertEquals(NativeMonsterStatsCalculator.MAX_HITPOINTS,
        NativeMonsterStatsCalculator.spawnHitpoints(8_000_000, 8, MonsterRank.UNIQUE, 0));
  }

  @Test void normalizationRetainsZeroReversedAndNegativeAttackContracts() {
    MonStats.Entry row = monster(); row.noRatio = true;
    row.A2MinD = new int[] {20,20,20}; row.A2MaxD = new int[] {10,10,10};
    row.A2TH = new int[] {-1,-1,-1};
    NativeMonsterStatsCalculator.Attack out = NativeMonsterStatsCalculator.attackProfile(
        context(1,true,2), row, level(), 0x10, 2);
    assertEquals(21, out.minDamage); assertEquals(21, out.maxDamage); assertEquals(0, out.attackRating);
    assertThrows(IllegalArgumentException.class, () -> NativeMonsterStatsCalculator.attackProfile(
        context(1,true,1), row, level(), 0x18, 1));
  }

  @Test void immutableOutputsAreHostFree() throws Exception {
    for (Class<?> type : new Class<?>[] {MonsterStatsSnapshot.class, NativeMonsterStatsCalculator.Attack.class}) {
      assertTrue(Modifier.isFinal(type.getModifiers()));
      for (java.lang.reflect.Field field : type.getDeclaredFields()) {
        assertTrue(Modifier.isFinal(field.getModifiers()), field.getName());
        assertTrue(field.getType().isPrimitive() || field.getType() == NativeMonsterStatsCalculator.Attack.class);
      }
    }
    assertThrows(ClassNotFoundException.class, () -> Class.forName("com.riiablo.engine.server.MonsterStatsCalculator"));
  }
}
