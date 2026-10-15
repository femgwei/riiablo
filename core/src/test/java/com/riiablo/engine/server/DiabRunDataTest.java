package com.riiablo.engine.server;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.codec.excel.MonStats;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DiabRunDataTest extends RiiabloTest {
  @Test void originalSkillAndSequence() {
    Skills.Entry skill = Riiablo.files.skills.get("DiabRun");
    assertNotNull(skill);
    assertTrue(NativeDiabRun.supports(skill));
    assertEquals("xx", skill.monanim); assertEquals("20", skill.calc1);
    assertArrayEquals(new int[]{8,14,5,13,16,6,0,0}, skill.Param);
    assertEquals(5, skill.ResultFlags); assertEquals(0, skill.HitFlags); assertEquals(0, skill.SrcDam);
    assertEquals("", skill.EType); assertEquals(17, skill.MinDam); assertEquals(33, skill.MaxDam);
    assertEquals(8, skill.HitShift);
    assertArrayEquals(new int[]{9,9,9,9,9}, skill.MinLevDam);
    assertArrayEquals(new int[]{9,9,9,9,9}, skill.MaxLevDam);
    int consumers=0;
    for(Skills.Entry row:Riiablo.files.skills) if(row.srvdofunc==103) {assertEquals(198,row.Id);consumers++;}
    assertEquals(1,consumers);
    for (String name : new String[]{"diablo","diabloclone"}) {
      MonStats.Entry mon = Riiablo.files.monstats.get(name);
      assertEquals("DiabRun",mon.Skill5); assertEquals("seq_diablorun",mon.Sk5mode); assertEquals(5,mon.Sk5lvl);
    }
    int points=0;
    for(String line:Riiablo.mpqs.resolve("data/global/excel/monseq.txt").readString("windows-1252").split("\\r?\\n")) {
      String[] cols=line.split("\\t",-1);
      if(!cols[0].equals("seq_diablorun"))continue;
      assertEquals("RN",cols[1]); assertEquals(points,Integer.parseInt(cols[2]));
      assertEquals(NativeDiabRun.action(points),cols[4].isEmpty()?0:Integer.parseInt(cols[4])); points++;
    }
    assertEquals(22,points); assertEquals(31.25f,NativeDiabRun.baseSpeed(skill,5));
  }

  @Test void independentSequenceCountdownAndLowBitsSurviveNativeSeeks() {
    NativeDiabRun run=new NativeDiabRun(4,new com.badlogic.gdx.math.Vector2(118.75f,100.5f));
    assertEquals(new com.badlogic.gdx.math.Vector2(118,100),run.destination);
    assertEquals(22<<8,run.remaining);
    run.cursor=(13<<8)|17;run.seek(6,16);
    assertEquals((6<<8)|17,run.cursor);assertEquals(16<<8,run.remaining);
    run.seek(14,8);assertEquals((14<<8)|17,run.cursor);assertEquals(8<<8,run.remaining);
  }
}
