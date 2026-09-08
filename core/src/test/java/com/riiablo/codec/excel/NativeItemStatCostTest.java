package com.riiablo.codec.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class NativeItemStatCostTest {
  @Test
  void keepsNativeRowIdAndD2mooBitFieldsSeparateFromCompatibilityFixes() throws Exception {
    String header = "Stat\tID\tSend Other\tSigned\tSend Bits\tSend Param Bits\t"
        + "UpdateAnimRate\tSaved\tCSvSigned\tCSvBits\tCSvParam\tfCallback\tfMin\tMinAccr\t"
        + "Encode\tAdd\tMultiply\tDivide\tValShift\t1.09-Save Bits\t1.09-Save Add\t"
        + "Save Bits\tSave Add\tSave Param Bits\tkeepzero\top\top param\top base\top stat1\t"
        + "op stat2\top stat3\tdirect\tmaxstat\titemspecific\tdamagerelated\titemevent1\t"
        + "itemeventfunc1\titemevent2\titemeventfunc2\tdescpriority\tdescfunc\tdescval\t"
        + "descstrpos\tdescstrneg\tdescstr2\tdgrp\tdgrpfunc\tdgrpval\tdgrpstrpos\tdgrpstrneg\t"
        + "dgrpstr2\tstuff\t*eol\n";
    String row = "strength\t99\t1\t1\t8\t2\t1\t1\t0\t3\t4\t1\t0\t5\t"
        + "4\t6\t7\t8\t9\t10\t11\t12\t13\t14\t1\t2\t3\tbase\tstat2\t\t\t1\t"
        + "maxstrength\t0\t1\tevent\t15\t\t0\t16\t17\t18\t19\t20\t21\t22\t23\t24\t"
        + "25\t26\t27\t28\t29\t30\t31\t32\t33\t34\n";
    NativeItemStatCost table = NativeItemStatCost.parse(
        (header + row).getBytes(StandardCharsets.ISO_8859_1));

    NativeItemStatCost.Entry entry = table.get(0);
    assertNotNull(entry);
    assertEquals(0, entry.id);
    assertEquals(99, entry.txtId);
    assertEquals("strength", table.get("STRENGTH").stat);
    assertTrue(entry.sendOther);
    assertTrue(entry.damageRelated);
    assertFalse(entry.itemSpecific);
    assertEquals(6, entry.add);
    assertEquals("base", entry.opBase);
    assertEquals("stat2", entry.opStats[0]);
    assertEquals(28, entry.stuff);
  }
}
