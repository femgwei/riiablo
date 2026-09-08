package com.riiablo.codec.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class StatesTest {
  @Test
  void projectsD2moo110fStateFieldsWithoutUsingLegacyTxtParser() throws Exception {
    String header = "state\tgroup\tpgsv\tcurse\tplrstaydeath\tmonstaydeath\t"
        + "bossstaydeath\tnoclear\toverlay1\tpgsvoverlay\tcastoverlay\tremoverlay\t"
        + "stat\tsetfunc\tremfunc\tmissile\tskill\tcolorpri\tcolorshift\tlight-r\t"
        + "light-g\tlight-b\tonsound\toffsound\titemtype\titemtrans\tgfxtype\t"
        + "gfxclass\tcltevent\tclteventfunc\tcltactivefunc\tsrvactivefunc\n";
    String row = "frozen\t7\t1\t1\t1\t0\t1\t1\tfreezeoverlay\tprogress\tcast\t"
        + "remove\tcoldlength\t2\t3\ticebolt\tfreeze\t4\t5\t6\t7\t8\ton\toff\t"
        + "armo\tblu\t9\t10\thit\t11\t12\t13\n";
    States states = States.parse((header + "Expansion\n" + row)
        .getBytes(StandardCharsets.ISO_8859_1));

    assertEquals(1, states.size());
    States.Entry frozen = states.get(0);
    assertNotNull(frozen);
    assertEquals(frozen, states.get("FROZEN"));
    assertEquals("frozen", frozen.state);
    assertEquals(7, frozen.group);
    assertTrue(frozen.progressive);
    assertTrue(frozen.curse);
    assertTrue(frozen.playerStayDeath);
    assertFalse(frozen.monsterStayDeath);
    assertTrue(frozen.bossStayDeath);
    assertTrue(frozen.noClear);
    assertEquals("freezeoverlay", frozen.overlays[0]);
    assertEquals("coldlength", frozen.stat);
    assertEquals(13, frozen.serverActiveFunc);
    assertEquals(3, frozen.sourceLine);
    assertNull(states.get(1));
  }
}
