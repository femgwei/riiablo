package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.D2;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.skill.SkillFormula;
import org.junit.jupiter.api.Test;

class DiabWallDataTest extends RiiabloTest {
  @Test void originalRowsAndTableOwnedFractionalDamage() {
    Skills.Entry skill = Riiablo.files.skills.get(197);
    assertTrue(NativeDiabWall.supports(skill));
    assertEquals("DiabWall", skill.skill); assertEquals(0, skill.srvstfunc);
    assertEquals("S1", skill.monanim); assertEquals("lvl", skill.calc1);
    assertEquals("", skill.EType); assertEquals(0, skill.EMin); assertEquals(0, skill.EMax);
    int users = 0;
    for (Skills.Entry row : Riiablo.files.skills) if (row.srvdofunc == 102) {assertEquals(197, row.Id); users++;}
    assertEquals(1, users);
    Missiles.Entry maker = Riiablo.files.Missiles.get(skill.srvmissilea);
    assertEquals(179, maker.Id); assertEquals(12, maker.pSrvDoFunc);
    assertEquals(80, maker.Range); assertEquals(0, maker.LevRange);
    assertEquals(20, maker.Vel); assertEquals(0, maker.VelLev);
    assertEquals(3, maker.CollideType); assertFalse(maker.CollideKill);
    assertEquals("diabwall", maker.SubMissile[0]);
    Missiles.Entry fire = Riiablo.files.Missiles.get(maker.SubMissile[0]);
    assertEquals(180, fire.Id); assertEquals(1, fire.pSrvDoFunc);
    assertEquals(0, fire.pSrvDmgFunc); assertEquals(0, fire.Vel); assertEquals(36, fire.Range);
    assertEquals("", fire.Skill); assertFalse(fire.MissileSkill); assertFalse(fire.ApplyMastery);
    assertEquals("fire", fire.EType); assertEquals(3, fire.HitShift);
    assertEquals(12, fire.EMin); assertEquals(25, fire.Emax);
    assertArrayEquals(new int[] {5,5,5,5,5}, fire.MinELev);
    assertArrayEquals(new int[] {5,5,5,5,5}, fire.MaxELev);
    assertEquals(41, fire.DamageRate); assertEquals(2, fire.Size);
    D2 animations = D2.loadFromFile(Riiablo.mpqs.resolve("data/global/eanimdata.d2"));
    for (String name : new String[] {"diablo", "diabloclone"}) {
      MonStats.Entry mon = Riiablo.files.monstats.get(name);
      int level = name.equals("diablo") ? 3 : 2;
      assertEquals("DiabWall", mon.Skill4); assertEquals("S3", mon.Sk4mode);
      assertEquals(level, mon.Sk4lvl); assertEquals(level, SkillFormula.evaluate(skill.calc1, skill, level));
      D2.Entry anim = animations.getEntry((mon.Code + mon.Sk4mode + "HTH").toUpperCase());
      int markers = 0;
      for (int i = 0; i < anim.framesPerDir; i++) if (anim.data[i] != 0) markers++;
      assertEquals(1, markers);
      Missile child = new Missile(); child.missile = fire;
      assertTrue(MissileDamageResolver.initializeMeteorFireArea(child, null, false, level, null));
      assertEquals(name.equals("diablo") ? 176 : 136, child.elementalMinRateFixed);
      assertEquals(name.equals("diablo") ? 280 : 240, child.elementalMaxRateFixed);
    }
  }

  @Test void initializerUsesTargetXAndUnsignedBranchRollThenOneFirstPathRoll() {
    Missiles.Entry row = Riiablo.files.Missiles.get("diabwallmaker");
    for (int targetX : new int[] {1, 13, 118, 100000}) for (int ordinal = 0; ordinal < 20; ordinal++) {
      Missile maker = new Missile(); maker.missile = row;
      Vector2 direction = new Vector2(1, 0);
      long branch = Integer.toUnsignedLong(targetX + ordinal) * 0x6AC690C5L + 666;
      boolean charged = Integer.remainderUnsigned((int) branch, 100) >= 20;
      NativeDiabWall.initialize(maker, 3, ordinal, 100, 100, targetX, 100, direction);
      assertEquals(charged, maker.chargedBoltPath);
      long expected = charged ? Integer.toUnsignedLong((int) branch) * 0x6AC690C5L + (branch >>> 32) : branch;
      assertEquals((int) expected, maker.chargedBoltSeedLow);
      assertEquals((int) (expected >>> 32), maker.chargedBoltSeedHigh);
      assertEquals(77, maker.nativeLifetimeFrames);
      assertEquals(0, maker.skillId); assertFalse(maker.diabWallMoved);
    }
    Missiles.Entry shortRow = new Missiles.Entry(); shortRow.Range = 24;
    Missile shortMaker = new Missile(); shortMaker.missile = shortRow;
    NativeDiabWall.initialize(shortMaker, 1, 0, 10, 10, 20, 10, new Vector2(1,0));
    assertEquals(24, shortMaker.nativeLifetimeFrames);
  }
}
