package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.server.component.Missile;
import org.junit.jupiter.api.Test;

/**
 * Locks the client-only poison cloud presentation contract to the loaded
 * vanilla Missiles.txt data.
 *
 * <p>Phrozen Keep's MoveFunc 4/3 notes describe the native graphical callback
 * chain, but the concrete rows are version/data-pack dependent.  In the
 * vanilla table shipped with Riiablo the chain is PoisonSparks cloud ->
 * poisonpuff (PoisonSmokePuff.dcc); there is no poisonjavcloudsub row.</p>
 */
class PoisonCloudPresentationTest extends RiiabloTest {
  @Test
  void vanillaPoisonCloudRowsExposeTheNativeClientVisualContract() {
    Missiles.Entry cloud = row("poisonjavcloud");
    Missiles.Entry plagueCloud = row("plaguejavcloud");
    Missiles.Entry puff = row("poisonpuff");

    assertEquals(4, cloud.pCltDoFunc);
    assertEquals(3, cloud.pSrvDoFunc);
    assertEquals("PoisonSparks", cloud.CelFile);
    assertEquals(31, cloud.AnimLen);
    assertEquals(60, cloud.Range);
    assertEquals(1, cloud.Trans);
    assertEquals(2, at(cloud.Param, 0));
    assertEquals(4, at(cloud.Param, 1));
    assertEquals(24, at(cloud.CltParam, 0));
    assertEquals(1, at(cloud.CltParam, 1));
    assertEquals(6, at(cloud.CltParam, 2));
    assertEquals("poisonpuff", cloud.CltSubMissile[0]);

    // Plague Javelin uses the same cloud/puff visual chain.
    assertEquals(4, plagueCloud.pCltDoFunc);
    assertEquals("PoisonSparks", plagueCloud.CelFile);
    assertEquals("poisonpuff", plagueCloud.CltSubMissile[0]);

    assertEquals(1, puff.pCltDoFunc);
    assertEquals(1, puff.pSrvDoFunc);
    assertEquals("PoisonSmokePuff", puff.CelFile);
    assertEquals(26, puff.AnimLen);
    assertEquals(25, puff.Range);
    assertEquals(1, puff.Trans);
    assertTrue(MissileLoader.isPoisonSmokePuff(puff));

    // This name appears in the supplied document, but is not part of the
    // loaded vanilla data and must not be invented by the client renderer.
    assertNull(Riiablo.files.Missiles.get("poisonjavcloudsub"));
  }

  @Test
  void plagueJavelinTrailAndCloudCallbacksFormTheClientChain() {
    Missiles.Entry javelin = row("plaguejavelin");
    Missiles.Entry cloud = row("plaguejavcloud");
    Missiles.Entry puff = row("poisonpuff");

    assertEquals(3, javelin.pCltDoFunc);
    assertEquals("plaguejavcloud", javelin.CltSubMissile[0]);
    assertTrue(MissileImpactPresentationSystem.isClientFlightFunction(javelin.pCltDoFunc));

    assertEquals(4, cloud.pCltDoFunc);
    assertEquals("poisonpuff", cloud.CltSubMissile[0]);
    assertTrue(MissileImpactPresentationSystem.isClientFlightFunction(cloud.pCltDoFunc));
    assertEquals(26, puff.AnimLen);

    Missile visualCloud = new Missile();
    visualCloud.missile = cloud;
    visualCloud.presentationOnly = true;
    Missile terminalPuff = new Missile();
    terminalPuff.missile = puff;
    terminalPuff.presentationOnly = true;
    assertTrue(MissileImpactPresentationSystem.shouldProcessClientFlightCallback(visualCloud));
    assertFalse(MissileImpactPresentationSystem.shouldProcessClientFlightCallback(terminalPuff));
  }

  private static Missiles.Entry row(String name) {
    Missiles.Entry row = Riiablo.files.Missiles.get(name);
    assertNotNull(row, name);
    return row;
  }

  private static int at(int[] values, int index) {
    return values != null && index >= 0 && index < values.length ? values[index] : 0;
  }
}
