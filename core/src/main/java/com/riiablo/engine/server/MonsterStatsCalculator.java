package com.riiablo.engine.server;

import com.riiablo.Riiablo;
import com.riiablo.codec.excel.MonLvl;
import com.riiablo.codec.excel.MonStats;

/**
 * Calculates monster stats based on level, following D2MOD's DATATBLS_CalculateMonsterStatsByLevel logic.
 * 
 * Reference: D2MOD source/D2Common/src/DataTbls/MonsterTbls.cpp:562
 */
public class MonsterStatsCalculator {
  
  /**
   * Result structure for calculated monster stats
   */
  public static class MonsterStatsInit {
    public int minHP;
    public int maxHP;
    public int AC;
    public int TH;
    public int Exp;
    public int A1MinD;
    public int A1MaxD;
    public int A2MinD;
    public int A2MaxD;
    public int S1MinD;
    public int S1MaxD;
    public int ElMinD;
    public int ElMaxD;
    public int ElDur;
  }
  
  /**
   * Calculate monster stats by level
   * 
   * @param monsterId Monster ID
   * @param gameType 0 = classic, 1 = expansion
   * @param difficulty 0 = normal, 1 = nightmare, 2 = hell
   * @param level Monster level
   * @param flags Bit flags: 1=HP, 2=AC, 4=Exp, 8=A1, 0x10=A2, 0x20=S1
   * @param result Output structure
   * @return true if successful
   */
  public static boolean calculateMonsterStatsByLevel(
      int monsterId, 
      int gameType, 
      int difficulty, 
      int level, 
      short flags, 
      MonsterStatsInit result) {
    
    MonStats.Entry monstats = Riiablo.files.monstats.get(monsterId);
    if (monstats == null) {
      return false;
    }
    
    // Clamp difficulty to valid range
    if (difficulty < 0) difficulty = 0;
    else if (difficulty > 2) difficulty = 2;
    
    // Clamp level (MonLvl table typically has entries up to level 99)
    if (level < 0) level = 0;
    else if (level > 99) level = 99;
    
    // Get MonLvl entry for this level
    MonLvl.Entry monLvl = null;
    if (Riiablo.files.MonLvl != null) {
      // MonLvl table is indexed by Level (0-99)
      // Use get() with level as key, or get by index if Level is the key field
      if (level >= 0 && level < Riiablo.files.MonLvl.size()) {
        monLvl = Riiablo.files.MonLvl.get(level);
      }
    }
    
    // Calculate offset for expansion (3 for expansion, 0 for classic)
    // In D2MOD: nOffset = 3 * (nGameType != 0)
    // For expansion, use LHP/LAC/etc. arrays instead of HP/AC/etc.
    int offset = (gameType != 0) ? 3 : 0;
    
    // If MonLvl table is available, use it; otherwise fall back to simplified calculation
    boolean useMonLvlTable = (monLvl != null);
    
    // Calculate HP if flag 1 is set
    if ((flags & 1) != 0) {
      if (monstats.noRatio) {
        // If noRatio flag is set, use MonStats values directly
        result.minHP = monstats.minHP[difficulty];
        result.maxHP = monstats.maxHP[difficulty];
      } else if (useMonLvlTable) {
        // Use actual MonLvl table data
        // In D2MOD: ApplyRatio(MonLvl.dwHP[difficulty + offset], MonStats.minHP[difficulty], 100)
        int[] hpArray = (offset > 0) ? monLvl.LHP : monLvl.HP;
        if (hpArray != null && hpArray.length > difficulty) {
          int hpMultiplier = hpArray[difficulty];
          result.minHP = applyRatio(monstats.minHP[difficulty], hpMultiplier, 100);
          result.maxHP = applyRatio(monstats.maxHP[difficulty], hpMultiplier, 100);
        } else {
          // Fallback if array is null or too short
          result.minHP = monstats.minHP[difficulty];
          result.maxHP = monstats.maxHP[difficulty];
        }
        
        // Ensure minimum values
        if (result.minHP < 1) result.minHP = 1;
        if (result.maxHP < result.minHP) result.maxHP = result.minHP;
      } else {
        // Fallback: use simplified level-based multiplier
        float levelMultiplier = calculateLevelMultiplier(level, difficulty, offset);
        result.minHP = (int)(monstats.minHP[difficulty] * levelMultiplier / 100f);
        result.maxHP = (int)(monstats.maxHP[difficulty] * levelMultiplier / 100f);
        
        // Ensure minimum values
        if (result.minHP < 1) result.minHP = 1;
        if (result.maxHP < result.minHP) result.maxHP = result.minHP;
      }
    }
    
    // Calculate AC if flag 2 is set
    if ((flags & 2) != 0) {
      if (monstats.noRatio) {
        result.AC = monstats.AC[difficulty];
      } else if (useMonLvlTable) {
        int[] acArray = (offset > 0) ? monLvl.LAC : monLvl.AC;
        if (acArray != null && acArray.length > difficulty) {
          result.AC = applyRatio(monstats.AC[difficulty], acArray[difficulty], 100);
        } else {
          result.AC = monstats.AC[difficulty];
        }
        if (result.AC < 0) result.AC = 0;
      } else {
        float levelMultiplier = calculateLevelMultiplier(level, difficulty, offset);
        result.AC = (int)(monstats.AC[difficulty] * levelMultiplier / 100f);
        if (result.AC < 0) result.AC = 0;
      }
    }
    
    // Calculate Exp if flag 4 is set
    if ((flags & 4) != 0) {
      if (monstats.noRatio) {
        result.Exp = monstats.Exp[difficulty];
      } else if (useMonLvlTable) {
        int[] xpArray = (offset > 0) ? monLvl.LXP : monLvl.XP;
        if (xpArray != null && xpArray.length > difficulty) {
          result.Exp = applyRatio(monstats.Exp[difficulty], xpArray[difficulty], 100);
        } else {
          result.Exp = monstats.Exp[difficulty];
        }
        if (result.Exp < 0) result.Exp = 0;
      } else {
        float levelMultiplier = calculateLevelMultiplier(level, difficulty, offset);
        result.Exp = (int)(monstats.Exp[difficulty] * levelMultiplier / 100f);
        if (result.Exp < 0) result.Exp = 0;
      }
    }
    
    // Calculate A1 (Attack 1) stats if flag 8 is set
    if ((flags & 8) != 0) {
      if (monstats.noRatio) {
        result.TH = monstats.A1TH[difficulty];
        result.A1MinD = monstats.A1MinD[difficulty];
        result.A1MaxD = monstats.A1MaxD[difficulty];
      } else if (useMonLvlTable) {
        int[] thArray = (offset > 0) ? monLvl.LTH : monLvl.TH;
        int[] dmArray = (offset > 0) ? monLvl.LDM : monLvl.DM;
        if (thArray != null && thArray.length > difficulty && 
            dmArray != null && dmArray.length > difficulty) {
          result.TH = applyRatio(monstats.A1TH[difficulty], thArray[difficulty], 100);
          result.A1MinD = applyRatio(monstats.A1MinD[difficulty], dmArray[difficulty], 100);
          result.A1MaxD = applyRatio(monstats.A1MaxD[difficulty], dmArray[difficulty], 100);
        } else {
          result.TH = monstats.A1TH[difficulty];
          result.A1MinD = monstats.A1MinD[difficulty];
          result.A1MaxD = monstats.A1MaxD[difficulty];
        }
        if (result.A1MinD < 0) result.A1MinD = 0;
        if (result.A1MaxD < result.A1MinD) result.A1MaxD = result.A1MinD;
      } else {
        float levelMultiplier = calculateLevelMultiplier(level, difficulty, offset);
        result.TH = (int)(monstats.A1TH[difficulty] * levelMultiplier / 100f);
        result.A1MinD = (int)(monstats.A1MinD[difficulty] * levelMultiplier / 100f);
        result.A1MaxD = (int)(monstats.A1MaxD[difficulty] * levelMultiplier / 100f);
        if (result.A1MinD < 0) result.A1MinD = 0;
        if (result.A1MaxD < result.A1MinD) result.A1MaxD = result.A1MinD;
      }
    }
    
    // Calculate A2 (Attack 2) stats if flag 0x10 is set
    if ((flags & 0x10) != 0) {
      if (monstats.noRatio) {
        result.TH = monstats.A2TH[difficulty];
        result.A2MinD = monstats.A2MinD[difficulty];
        result.A2MaxD = monstats.A2MaxD[difficulty];
      } else if (useMonLvlTable) {
        int[] thArray = (offset > 0) ? monLvl.LTH : monLvl.TH;
        int[] dmArray = (offset > 0) ? monLvl.LDM : monLvl.DM;
        if (thArray != null && thArray.length > difficulty && 
            dmArray != null && dmArray.length > difficulty) {
          result.TH = applyRatio(monstats.A2TH[difficulty], thArray[difficulty], 100);
          result.A2MinD = applyRatio(monstats.A2MinD[difficulty], dmArray[difficulty], 100);
          result.A2MaxD = applyRatio(monstats.A2MaxD[difficulty], dmArray[difficulty], 100);
        } else {
          result.TH = monstats.A2TH[difficulty];
          result.A2MinD = monstats.A2MinD[difficulty];
          result.A2MaxD = monstats.A2MaxD[difficulty];
        }
        if (result.A2MinD < 0) result.A2MinD = 0;
        if (result.A2MaxD < result.A2MinD) result.A2MaxD = result.A2MinD;
      } else {
        float levelMultiplier = calculateLevelMultiplier(level, difficulty, offset);
        result.TH = (int)(monstats.A2TH[difficulty] * levelMultiplier / 100f);
        result.A2MinD = (int)(monstats.A2MinD[difficulty] * levelMultiplier / 100f);
        result.A2MaxD = (int)(monstats.A2MaxD[difficulty] * levelMultiplier / 100f);
        if (result.A2MinD < 0) result.A2MinD = 0;
        if (result.A2MaxD < result.A2MinD) result.A2MaxD = result.A2MinD;
      }
    }
    
    // Calculate S1 (Skill 1) stats if flag 0x20 is set
    if ((flags & 0x20) != 0) {
      if (monstats.noRatio) {
        result.TH = monstats.S1TH != null && monstats.S1TH.length > difficulty ? monstats.S1TH[difficulty] : 0;
        result.S1MinD = monstats.S1MinD != null && monstats.S1MinD.length > difficulty ? monstats.S1MinD[difficulty] : 0;
        result.S1MaxD = monstats.S1MaxD != null && monstats.S1MaxD.length > difficulty ? monstats.S1MaxD[difficulty] : 0;
      } else if (useMonLvlTable) {
        int[] thArray = (offset > 0) ? monLvl.LTH : monLvl.TH;
        int[] dmArray = (offset > 0) ? monLvl.LDM : monLvl.DM;
        int s1TH = (monstats.S1TH != null && monstats.S1TH.length > difficulty) ? monstats.S1TH[difficulty] : 0;
        int s1MinD = (monstats.S1MinD != null && monstats.S1MinD.length > difficulty) ? monstats.S1MinD[difficulty] : 0;
        int s1MaxD = (monstats.S1MaxD != null && monstats.S1MaxD.length > difficulty) ? monstats.S1MaxD[difficulty] : 0;
        if (thArray != null && thArray.length > difficulty && 
            dmArray != null && dmArray.length > difficulty) {
          result.TH = applyRatio(s1TH, thArray[difficulty], 100);
          result.S1MinD = applyRatio(s1MinD, dmArray[difficulty], 100);
          result.S1MaxD = applyRatio(s1MaxD, dmArray[difficulty], 100);
        } else {
          result.TH = s1TH;
          result.S1MinD = s1MinD;
          result.S1MaxD = s1MaxD;
        }
        if (result.S1MinD < 0) result.S1MinD = 0;
        if (result.S1MaxD < result.S1MinD) result.S1MaxD = result.S1MinD;
      } else {
        float levelMultiplier = calculateLevelMultiplier(level, difficulty, offset);
        int s1TH = (monstats.S1TH != null && monstats.S1TH.length > difficulty) ? monstats.S1TH[difficulty] : 0;
        int s1MinD = (monstats.S1MinD != null && monstats.S1MinD.length > difficulty) ? monstats.S1MinD[difficulty] : 0;
        int s1MaxD = (monstats.S1MaxD != null && monstats.S1MaxD.length > difficulty) ? monstats.S1MaxD[difficulty] : 0;
        result.TH = (int)(s1TH * levelMultiplier / 100f);
        result.S1MinD = (int)(s1MinD * levelMultiplier / 100f);
        result.S1MaxD = (int)(s1MaxD * levelMultiplier / 100f);
        if (result.S1MinD < 0) result.S1MinD = 0;
        if (result.S1MaxD < result.S1MinD) result.S1MaxD = result.S1MinD;
      }
    }

    // D2Common uses 0x40, 0x80 and 0x100 for the three MonStats elemental
    // attack profiles. These values are damage ratios just like A1/A2 and
    // must be scaled through MonLvl before being attached to an attack.
    int elementIndex = (flags & 0x40) != 0 ? 0
        : (flags & 0x80) != 0 ? 1
        : (flags & 0x100) != 0 ? 2 : -1;
    if (elementIndex >= 0) {
      int[] minValues = elementIndex == 0 ? monstats.El1MinD
          : elementIndex == 1 ? monstats.El2MinD : monstats.El3MinD;
      int[] maxValues = elementIndex == 0 ? monstats.El1MaxD
          : elementIndex == 1 ? monstats.El2MaxD : monstats.El3MaxD;
      int[] durations = elementIndex == 0 ? monstats.El1Dur
          : elementIndex == 1 ? monstats.El2Dur : monstats.El3Dur;
      int rawMin = arrayValue(minValues, difficulty);
      int rawMax = arrayValue(maxValues, difficulty);
      if (monstats.noRatio) {
        result.ElMinD = rawMin;
        result.ElMaxD = rawMax;
      } else if (useMonLvlTable) {
        int[] dmArray = offset > 0 ? monLvl.LDM : monLvl.DM;
        int multiplier = arrayValue(dmArray, difficulty);
        result.ElMinD = applyRatio(rawMin, multiplier, 100);
        result.ElMaxD = applyRatio(rawMax, multiplier, 100);
      } else {
        float levelMultiplier = calculateLevelMultiplier(level, difficulty, offset);
        result.ElMinD = (int) (rawMin * levelMultiplier / 100f);
        result.ElMaxD = (int) (rawMax * levelMultiplier / 100f);
      }
      result.ElMinD = Math.max(0, result.ElMinD);
      result.ElMaxD = Math.max(result.ElMinD, result.ElMaxD);
      result.ElDur = Math.max(0, arrayValue(durations, difficulty));
    }
    
    return true;
  }

  private static int arrayValue(int[] values, int index) {
    return values != null && index >= 0 && index < values.length ? values[index] : 0;
  }
  
  /**
   * Calculate level-based multiplier for monster stats
   * 
   * This is a simplified approximation since we don't have the MonLvl table.
   * In D2MOD: HP = ApplyRatio(MonLvl.dwHP[difficulty + offset], MonStats.minHP/maxHP[difficulty], 100)
   * Which means: HP = MonLvl.dwHP * MonStats.minHP/maxHP / 100
   * 
   * Based on user feedback: MonStats.maxHP[0] = 181, but actual HP should be 15-20
   * This suggests MonLvl.dwHP[0] should be around 10-15 (181 * 10 / 100 = 18.1)
   * 
   * Typical MonLvl.dwHP values from D2:
   * - Level 1: ~10-15 (for normal difficulty, normal monsters)
   * - Level 5: ~20-30
   * - Level 10: ~40-60
   * - Level 20: ~80-120
   * 
   * Formula: baseMultiplier * (1 + (level - 1) * growthRate)
   */
  private static float calculateLevelMultiplier(int level, int difficulty, int offset) {
    // Base multiplier for level 1 (MonLvl.dwHP[0] for normal difficulty)
    // Based on user feedback: if MonStats.maxHP = 181 and actual HP = 15-20,
    // then MonLvl.dwHP[0] ≈ 10-15 (181 * 10 / 100 = 18.1, 181 * 15 / 100 = 27.15)
    // Using 12 as a middle ground
    float baseMultiplier = 12f;
    
    // Growth rate per level (MonLvl values increase roughly 8-12% per level)
    float growthRate = 0.10f; // 10% per level
    
    // Calculate multiplier: base * (1 + (level - 1) * growth)
    // For level 1: 12 * (1 + 0 * 0.1) = 12
    // For level 5: 12 * (1 + 4 * 0.1) = 12 * 1.4 = 16.8
    // For level 10: 12 * (1 + 9 * 0.1) = 12 * 1.9 = 22.8
    float multiplier = baseMultiplier * (1f + (level - 1) * growthRate);
    
    // Adjust for difficulty (nightmare and hell use different MonLvl array indices)
    // In D2MOD: nOffset = 3 * (nGameType != 0), then uses dwHP[difficulty + nOffset]
    // For expansion: difficulty 0 uses dwLHP[0], difficulty 1 uses dwLHP[1], etc.
    // For classic: difficulty 0 uses dwHP[0], difficulty 1 uses dwHP[1], etc.
    // Nightmare and Hell typically have higher multipliers
    if (difficulty == 1) { // Nightmare
      multiplier *= 1.5f;
    } else if (difficulty == 2) { // Hell
      multiplier *= 2.0f;
    }
    
    // Adjust for expansion (offset = 3 means use LHP arrays)
    // Expansion typically has slightly higher values (about 10% more)
    if (offset > 0) {
      multiplier *= 1.1f;
    }
    
    return multiplier;
  }
  
  /**
   * Apply ratio calculation (value * multiplier / divisor)
   * Reference: D2MOD DATATBLS_ApplyRatio
   */
  private static int applyRatio(int value, int multiplier, int divisor) {
    if (divisor == 0) return value;
    // Handle overflow cases similar to D2MOD
    if (value <= 0x100000 && multiplier <= 0x10000) {
      return multiplier * value / divisor;
    }
    // For larger values, use long to avoid overflow
    return (int)((long)multiplier * value / divisor);
  }
}
