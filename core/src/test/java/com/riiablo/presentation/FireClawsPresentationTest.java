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
import com.riiablo.codec.util.BBox;
import com.riiablo.engine.server.event.UnitOverlayEvent;
import com.riiablo.presentation.component.Overlay;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class FireClawsPresentationTest extends RiiabloTest {
  @Test void targetFlashUsesActualTwelveFrameAssetAndNativeAdditiveClockThenRetires() {
    try (Fixture f = new Fixture()) {
      f.events.dispatch(UnitOverlayEvent.obtain(f.target, "fire_hit"));
      f.world.process();
      Overlay overlay = f.overlay();
      assertNotNull(overlay); assertFalse(overlay.persistent); assertFalse(overlay.entry.PreDraw);
      assertTrue(overlay.usesNativeRendering());
      assertEquals(.04f, overlay.animation.getFrameDuration(), .000001f);
      assertEquals(com.riiablo.graphics.BlendMode.ADDITIVE,
          overlay.animation.getLayer(0).getBlendMode());
      assertEquals(0f, AuraRenderPolicy.offsetY(overlay.entry, 1));
      ByteBuffer bytes = ByteBuffer.wrap(Riiablo.mpqs.resolve(overlay.assetDescriptor.fileName)
          .readBytes()).order(ByteOrder.LITTLE_ENDIAN);
      assertEquals(1, bytes.get(2) & 255); assertEquals(12, bytes.getInt(3));
      System.out.println("FIRE_CLAWS_OVERLAY path=" + overlay.assetDescriptor.fileName
          + " dcc_frames=12 table_frames=8 directions=1 AnimRate=" + overlay.entry.AnimRate + " Trans=" + overlay.entry.Trans);
      for (int i = 0; i < 16; i++) f.world.process();
      assertFalse(f.manager.isOneShotActive(f.target, "fire_hit"));
      verify(f.assets).unload("data/global/overlays/FireHit.dcc");
    }
  }

  @Test void repeatedHitRestartsOnlyTheFlashAndPreservesAnotherStateLayer() {
    try (Fixture f = new Fixture()) {
      String might = Riiablo.files.States.get(com.riiablo.engine.server.state.StateId.MIGHT).overlays[0];
      f.manager.setPersistent(f.target, com.riiablo.engine.server.state.StateId.MIGHT, might);
      f.world.process();
      Overlay root = f.overlay(); root.animation.setFrame(4);
      f.events.dispatch(UnitOverlayEvent.obtain(f.target, "fire_hit")); f.world.process();
      assertEquals(might, root.entry.overlay); assertEquals(1, root.layers.size);
      f.events.dispatch(UnitOverlayEvent.obtain(f.target, "fire_hit")); f.world.process();
      assertEquals(1, root.layers.size); assertTrue(root.layers.first().animation.getFrame() <= 1);
      assertSame(root, f.overlay()); assertTrue(root.persistent);
      for (int i = 0; i < 16; i++) f.world.process();
      assertSame(root, f.overlay()); assertEquals(might, root.entry.overlay); assertTrue(root.layers.isEmpty());
    }
  }

  @Test void deletionDuringPendingLoadCancelsTheFlashWithoutRecreatingTheTarget() {
    try (Fixture f = new Fixture()) {
      when(f.assets.isLoaded(any(AssetDescriptor.class))).thenReturn(false);
      f.events.dispatch(UnitOverlayEvent.obtain(f.target, "fire_hit")); f.world.process();
      assertFalse(f.overlay().isLoaded);
      f.world.delete(f.target); f.world.process();
      f.events.dispatch(UnitOverlayEvent.obtain(f.target, "fire_hit")); f.world.process();
      assertFalse(f.manager.isOneShotActive(f.target, "fire_hit"));
      verify(f.assets, times(1)).load(any(AssetDescriptor.class));
      verify(f.assets).unload("data/global/overlays/FireHit.dcc");
    }
  }

  private static final class Fixture implements AutoCloseable {
    final AssetManager previous = Riiablo.assets;
    final com.riiablo.Colors previousColors = Riiablo.colors;
    final AssetManager assets = mock(AssetManager.class);
    final OverlayManager manager = new OverlayManager();
    final EventSystem events = new EventSystem();
    final World world;
    final int target;
    Fixture() {
      Riiablo.assets = assets; Riiablo.colors = new com.riiablo.Colors();
      DCC dcc = mock(DCC.class);
      when(dcc.getNumDirections()).thenReturn(1); when(dcc.getNumFramesPerDir()).thenReturn(12);
      when(dcc.getBox(anyInt())).thenReturn(new BBox());
      when(assets.isLoaded(any(AssetDescriptor.class))).thenReturn(true);
      when(assets.contains(anyString())).thenReturn(true); when(assets.isLoaded(anyString())).thenReturn(true);
      when(assets.get(any(AssetDescriptor.class))).thenReturn(dcc);
      world = new World(new WorldConfigurationBuilder().with(events, manager, new OverlayStepper())
          .build().register("clientContext", ClientGameplayContext.fromGlobals()));
      target = world.create(); world.setDelta(1f/25f); world.process();
    }
    Overlay overlay() { return world.getMapper(Overlay.class).get(target); }
    public void close() { world.dispose(); Riiablo.assets = previous; Riiablo.colors = previousColors; }
  }
}
