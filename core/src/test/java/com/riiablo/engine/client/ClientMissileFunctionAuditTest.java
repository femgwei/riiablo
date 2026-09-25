package com.riiablo.engine.client;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Missiles;
import org.junit.jupiter.api.Test;

/** Temporary data audit for native client missile callback parameters. */
class ClientMissileFunctionAuditTest extends RiiabloTest {
  @Test
  void dumpClientMissileFunctions() {
    for (Missiles.Entry row : Riiablo.files.Missiles) {
      if (row.pCltDoFunc == 0 && row.pCltHitFunc == 0) continue;
      System.out.println("[CLIENT_MISSILE_FUNC] missile=" + row.Missile
          + " cltDo=" + row.pCltDoFunc + " cltHit=" + row.pCltHitFunc
          + " cltParam=" + java.util.Arrays.toString(row.CltParam)
          + " cltHitPar=" + java.util.Arrays.toString(row.cHitPar)
          + " offset=" + row.xoffset + "," + row.yoffset + "," + row.zoffset
          + " directions=" + row.NumDirections
          + " cltHitSub=" + java.util.Arrays.toString(row.CltHitSubMissile)
          + " cltSub=" + java.util.Arrays.toString(row.CltSubMissile));
    }
  }
}
