package com.riiablo.presentation;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.States;
import com.riiablo.codec.excel.Overlay;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.riiablo.codec.DCC;
import com.riiablo.codec.util.BBox;
import com.riiablo.graphics.BlendMode;

class AuraOverlayResourceTest extends RiiabloTest {
  @Test void realManagerInstallsNativeAuraRateBlendAndPreservesOtherOverlays() {
    AssetManager saved = Riiablo.assets;
    com.riiablo.Colors savedColors = Riiablo.colors;
    Riiablo.colors = new com.riiablo.Colors();
    AssetManager assets = Riiablo.assets = mock(AssetManager.class);
    DCC dcc = mock(DCC.class);
    when(dcc.getNumDirections()).thenReturn(1);
    when(dcc.getNumFramesPerDir()).thenReturn(16);
    when(dcc.getBox(anyInt())).thenReturn(new BBox());
    when(assets.isLoaded(any(AssetDescriptor.class))).thenReturn(true);
    when(assets.get(any(AssetDescriptor.class))).thenReturn(dcc);
    OverlayManager manager = new OverlayManager();
    World world = new World(new WorldConfigurationBuilder().with(manager).build()
        .register("clientContext", ClientGameplayContext.fromGlobals()));
    try {
      States.Entry state = Riiablo.files.States.get("might");
      assertNotNull(state);
      int entity = world.create();
      manager.setPersistent(entity, state.id, state.overlays[0]);
      world.process();
      com.riiablo.presentation.component.Overlay effect = world.getMapper(
          com.riiablo.presentation.component.Overlay.class).get(entity);
      assertTrue(effect.nativeAura);
      assertTrue(effect.isLoaded);
      assertEquals(BlendMode.ADDITIVE, effect.animation.getLayer(0).getBlendMode());
      assertEquals(.04f, effect.animation.getFrameDuration(), .000001f);
      // The asset's frame bounds remain owned by DCC/Animation, not table offsets.
      assertSame(dcc, effect.animation.getLayer(0).getDC());
      manager.set(entity, state.overlays[0]);
      world.process();
      assertFalse(effect.layers.first().nativeAura, "unrelated transient policy is unchanged");
      assertEquals(BlendMode.LUMINOSITY,
          effect.layers.first().animation.getLayer(0).getBlendMode());
      manager.clearPersistent(entity, state.id);
      assertFalse(effect.nativeAura, "retired pooled effects must not retain aura policy");
    } finally {
      world.dispose();
      Riiablo.assets = saved;
      Riiablo.colors = savedColors;
    }
  }

  @Test void realAuraRowsUseSupportedDrawModesAndExistingDccs() {
    int count = 0;
    for (States.Entry state : Riiablo.files.States) {
      if (!state.aura) continue;
      for (String key : state.overlays) {
        if (key == null || key.isEmpty()) continue;
        Overlay.Entry row = Riiablo.files.Overlay.get(key);
        assertNotNull(row, state.state + ": " + key);
        assertTrue(row.Trans >= 0 && row.Trans <= 3 || row.Trans == 5,
            key + " unsupported native draw mode " + row.Trans);
        assertTrue(Riiablo.mpqs.contains("data\\global\\overlays\\" + row.Filename + ".dcc"), key);
        System.out.println("AURA_DRAW " + state.state + " " + key + " trans=" + row.Trans
            + " rate=" + row.AnimRate + " offsets=" + row.Xoffset + "," + row.Yoffset
            + " height2=" + row.Height2 + " predraw=" + row.PreDraw);
        count++;
      }
    }
    assertTrue(count >= 10, "must inspect real aura resources, not an empty fixture");
  }
}
