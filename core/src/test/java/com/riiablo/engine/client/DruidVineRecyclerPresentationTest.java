package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Missiles;
import org.junit.jupiter.api.Test;

/** Native client timeline coverage for Druid's SrvSt63 recycler delay. */
class DruidVineRecyclerPresentationTest extends RiiabloTest {
  @Test
  void vineRecyclerUsesNativeDelayedClientChildren() {
    Missiles.Entry recycler = Riiablo.files.Missiles.get("vine recycler delay");
    assertNotNull(recycler);
    assertEquals(51, recycler.pCltDoFunc);
    assertEquals(33, recycler.pSrvDoFunc);
    assertEquals(20, MissileImpactPresentationSystem.cltParam(recycler, 0, 0));
    assertEquals(45, MissileImpactPresentationSystem.cltParam(recycler, 1, 0));
    assertEquals("recycler vine", recycler.CltSubMissile[0]);
    assertEquals("recycler explosion", recycler.CltSubMissile[1]);
    assertTrue(MissileImpactPresentationSystem.isClientFlightFunction(
        recycler.pCltDoFunc));
    assertFalse(MissileImpactPresentationSystem.recyclerFrameReached(19, 19, 20));
    assertTrue(MissileImpactPresentationSystem.recyclerFrameReached(19, 20, 20));
    assertTrue(MissileImpactPresentationSystem.recyclerFrameReached(20, 45, 45));
    assertFalse(MissileImpactPresentationSystem.recyclerFrameReached(45, 46, 45));
  }
}
