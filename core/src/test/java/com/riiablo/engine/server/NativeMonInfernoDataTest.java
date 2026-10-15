package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Sequence;
import org.junit.jupiter.api.Test;

class NativeMonInfernoDataTest extends RiiabloTest {
  @Test void originalRowsDistinguish95FromDiablo152AndUnportedMonSeq() {
    int rows = 0;
    for (Skills.Entry skill : Riiablo.files.skills) {
      if (skill.srvstfunc != 53) continue;
      rows++;
      assertTrue(skill.srvdofunc == 95 || skill.Id == 193 && skill.srvdofunc == 152);
      assertNotNull(Riiablo.files.Missiles.get(skill.srvmissilea));
    }
    assertEquals(6, rows);
    assertEquals("par1", Riiablo.files.skills.get(178).calc2);
    assertEquals("3", Riiablo.files.skills.get(178).calc3);
    assertEquals("2", Riiablo.files.skills.get(319).calc3);
    assertEquals("2", Riiablo.files.skills.get(317).calc3);
    int shamans = 0;
    for (MonStats.Entry mon : Riiablo.files.monstats) {
      if (!"FetishShaman".equals(mon.AI)) continue;
      shamans++;
      Monster monster = new Monster().set(mon, Riiablo.files.monstats2.get(mon.MonStatsEx));
      assertEquals(178, Riiablo.files.skills.get(mon.Skill1).Id);
      assertEquals("A1", mon.Sk1mode);
      assertEquals(11, monster.monstats2.InfernoAnim);
      assertEquals(14, monster.monstats2.InfernoLen);
      assertTrue(NativeMonInferno.supports(Riiablo.files.skills.get(178), monster,
          new Sequence().sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU)));
    }
    assertEquals(8, shamans);
    Monster monster = new Monster();
    monster.monstats2 = Riiablo.files.monstats2.get(Riiablo.files.monstats.get("diablo").MonStatsEx);
    for (int id : new int[] {193, 311, 323}) assertFalse(NativeMonInferno.supports(
        Riiablo.files.skills.get(id), monster,
        new Sequence().sequence(Engine.Monster.MODE_XX, Engine.Monster.MODE_NU)));
  }

  @Test void lifetimeAndVelocityUseNativeIntegerFormulasAndClamp() {
    Skills.Entry skill = new Skills.Entry();
    com.riiablo.codec.excel.Missiles.Entry missile = new com.riiablo.codec.excel.Missiles.Entry();
    missile.Param = new int[] {0, 7}; missile.Vel = 10; missile.VelLev = 3;
    assertEquals(10, NativeMonInferno.missileFrames(skill, missile, 4));
    assertEquals(11, NativeMonInferno.velocity(missile, 4));
    skill.calc1 = "0";
    assertEquals(1, NativeMonInferno.missileFrames(skill, missile, -100));
    skill.calc1 = "999";
    assertEquals(255, NativeMonInferno.missileFrames(skill, missile, 1));
    skill.calc1 = "5";
    assertEquals(5, NativeMonInferno.missileFrames(skill, missile, 20));
  }

  @Test void otherNonSequenceModesKeepTheirOriginalMissileMarkers() {
    com.riiablo.codec.D2 data = com.riiablo.codec.D2.loadFromFile(Riiablo.mpqs.resolve("data/global/eanimdata.d2"));
    for (String id : new String[] {"baalcrab", "megademon1"}) {
      MonStats.Entry mon = Riiablo.files.monstats.get(id);
      com.riiablo.codec.D2.Entry anim = data.getEntry((mon.Code + "S1HTH").toUpperCase());
      assertNotNull(anim);
      assertEquals(256, anim.speed);
      assertEquals(id.equals("baalcrab") ? 16 : 12, anim.framesPerDir);
      int marker = id.equals("baalcrab") ? 10 : 8;
      for (int i = 0; i < anim.framesPerDir; i++)
        assertEquals(i == marker ? Engine.KEYFRAME_MIS : 0, anim.data[i]);
    }
  }
}
