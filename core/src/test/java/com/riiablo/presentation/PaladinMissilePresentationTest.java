package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.DCC;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.util.BBox;
import com.riiablo.graphics.BlendMode;
import org.junit.jupiter.api.Test;

class PaladinMissilePresentationTest extends RiiabloTest {
  @Test void nativeTargetOverlaysUseNativeClockAndAdditiveCompositing() {
    AssetManager previous = Riiablo.assets;
    com.riiablo.Colors previousColors = Riiablo.colors;
    Riiablo.colors = new com.riiablo.Colors();
    AssetManager assets = Riiablo.assets = mock(AssetManager.class);
    DCC dcc = mock(DCC.class);
    when(dcc.getNumDirections()).thenReturn(1);
    when(dcc.getNumFramesPerDir()).thenReturn(16);
    when(dcc.getBox(anyInt())).thenReturn(new BBox());
    when(assets.isLoaded(any(AssetDescriptor.class))).thenReturn(true);
    when(assets.get(any(AssetDescriptor.class))).thenReturn(dcc);
    OverlayManager overlays = new OverlayManager();
    World world = new World(new WorldConfigurationBuilder().with(overlays).build()
        .register("clientContext", ClientGameplayContext.fromGlobals()));
    try {
      for (String name : new String[] {"handofgod", "healing"}) {
        com.riiablo.codec.excel.Overlay.Entry row = Riiablo.files.Overlay.get(name);
        assertNotNull(row);
        assertEquals(16, row.AnimRate, name);
        assertEquals(3, row.Trans, name);
        assertTrue(Riiablo.mpqs.contains("data\\global\\overlays\\" + row.Filename + ".dcc"));
        int id = world.create();
        overlays.set(id, name);
        world.process();
        com.riiablo.presentation.component.Overlay effect = world.getMapper(
            com.riiablo.presentation.component.Overlay.class).get(id);
        assertTrue(effect.usesNativeRendering());
        assertFalse(effect.persistent);
        assertEquals(.04f, effect.animation.getFrameDuration(), .000001f);
        assertEquals(BlendMode.ADDITIVE, effect.animation.getLayer(0).getBlendMode());
        assertSame(dcc, effect.animation.getLayer(0).getDC());
      }
    } finally {
      world.dispose();
      Riiablo.assets = previous;
      Riiablo.colors = previousColors;
    }
  }

  @Test void nativeProjectileRowsRetainTheirDccAndAnimationContracts() {
    for (String name : new String[] {"holybolt", "fistoftheheavensbolt", "blessedhammer"}) {
      Missiles.Entry row = Riiablo.files.Missiles.get(name);
      assertNotNull(row);
      assertEquals(1, row.pCltDoFunc, name);
      assertEquals(1024, row.animrate, name);
      assertEquals(1, row.Trans, name);
      assertEquals(BlendMode.ADDITIVE, MissileLoader.nativeBlendMode(row), name);
      String path = "data\\global\\missiles\\" + row.CelFile + ".dcc";
      assertTrue(Riiablo.mpqs.contains(path), path);
      DCC dcc = DCC.loadFromFile(Riiablo.mpqs.resolve(path));
      assertTrue(dcc.getNumFramesPerDir() > 0, name);
      assertEquals("blessedhammer".equals(name) ? 6 : 16, row.AnimLen, name);
    }
    assertEquals(9, Riiablo.files.Missiles.get("holybolt").pCltHitFunc);
    assertEquals("healing", Riiablo.files.Missiles.get("holybolt").ProgOverlay);
    assertEquals(26, Riiablo.files.Missiles.get("fistoftheheavensdelay").pCltHitFunc);
  }
}
