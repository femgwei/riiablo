package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.DruidSkills;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Checks the raw MPQ text independently of the typed Excel adapters. */
class NativePlayerCombat110fAuditTest extends RiiabloTest {
  @Test void rawSixMeleeRowsHaveFullSourceAndNoOwnElementalDamage() {
    for (String name : new String[] {"Frenzy", "Berserk", "Zeal", "Sacrifice", "Jab", "Fend"}) {
      Map<String, String> raw = row("skills", name);
      assertEquals("128", raw.get("SrcDam"), name + " " + raw);
      for (String stat : new String[] {"EMin", "EMax"}) {
        String cell = raw.get(stat);
        assertEquals(0, cell == null || cell.isEmpty() ? 0 : Integer.parseInt(cell), name + " " + stat);
      }
    }
  }
  @Test void rawOrdinaryWeaponCoefficientsAndMissileSourceAreLoadedUnchanged() {
    String[] names = {"Hand Axe", "Dagger", "Javelin", "Short Bow", "Light Crossbow"};
    // Empty native cells are decoded as zero by Excel; retain the raw distinction.
    String[] strength = {"100", "75", "75", "", ""};
    String[] dexterity = {"", "75", "75", "100", "100"};
    for (int i = 0; i < names.length; i++) {
      Map<String, String> raw = row("weapons", names[i]);
      assertEquals(strength[i], raw.get("StrBonus"), names[i]);
      assertEquals(dexterity[i], raw.get("DexBonus"), names[i]);
    }
    for (String name : new String[] {"arrow", "bolt", "javelin"}) {
      Map<String, String> raw = row("missiles", name);
      assertEquals("128", raw.get("SrcDamage"), name);
    }
  }

  @Test
  void rawWerebearRowAndProductionStateUse110fCoefficients() {
    Map<String, String> raw = row("skills", "Wearbear");
    assertEquals("50", raw.get("Param1"));
    assertEquals("7", raw.get("Param2"));
    assertEquals("25", raw.get("Param3"));
    assertEquals("5", raw.get("Param4"));
    assertEquals("50", raw.get("Param5"));
    assertEquals("ln12", raw.get("aurastatcalc1"));
    assertEquals("ln34", raw.get("aurastatcalc2"));
    assertEquals("par5 + skill('Shape Shifting'.ln34)", raw.get("aurastatcalc3"));
    Skills.Entry bear = Riiablo.files.skills.get(SkillId.WEREBEAR);
    int[] levels = {1, 2, 3, 20};
    int[] damage = {50, 57, 64, 183};
    int[] defense = {25, 30, 35, 120};
    for (int i = 0; i < levels.length; i++) {
      UnitState state = DruidSkills.applyShapeShiftState(new StateList(1), bear,
          levels[i], 1, name -> 2, name -> Riiablo.files.skills.get(name)).appliedState;
      assertEquals(damage[i], state.damageModifier);
      assertEquals(defense[i], state.defenseModifier);
      assertEquals(75, state.maxLifeModifier);
      assertEquals(damage[i], DruidSkills.calculateWerebearDamageBonus(levels[i]));
      assertEquals(defense[i], DruidSkills.calculateWerebearDefenseBonus(levels[i]));
    }
  }

  @Test
  void rawPoisonNovaRowUses110fRates() {
    Map<String, String> raw = row("skills", "Poison Nova");
    assertEquals("14", raw.get("EMin"));
    assertEquals("25", raw.get("EMax"));
    assertEquals("4", raw.get("HitShift"));
    assertEquals("50", raw.get("ELen"));
    assertEquals("10", raw.get("Param8"));
  }

  @Test
  void rawHammerRowHasUndeadBonusButNoDemonBonus() {
    Map<String, String> raw = row("missiles", "blessedhammer");
    assertEquals("50", raw.get("dParam1"));
    assertEquals("0", raw.get("dParam2"));
  }

  @Test
  void rawShockwaveRowUsesFourFrameTargetWideDelay() {
    Map<String, String> raw = row("missiles", "shockwave");
    assertEquals("1", raw.get("NextHit"));
    assertEquals("4", raw.get("NextDelay"));
  }

  private static Map<String, String> row(String table, String name) {
    String[] lines = Riiablo.mpqs.resolve("data\\global\\excel\\" + table + ".txt")
        .readString("windows-1252").split("\\r?\\n");
    String[] header = lines[0].split("\t", -1);
    for (int i = 1; i < lines.length; i++) {
      String[] cells = lines[i].split("\t", -1);
      if (!name.equals(cells[0])) continue;
      Map<String, String> row = new LinkedHashMap<>();
      for (int column = 0; column < header.length; column++) {
        row.put(header[column], column < cells.length ? cells[column] : "");
      }
      return row;
    }
    throw new AssertionError("Missing " + table + " row: " + name);
  }
}
