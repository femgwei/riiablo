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
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.DamageAppliedEvent;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.event.UnitOverlayEvent;
import com.riiablo.presentation.component.Overlay;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HungerPresentationTest extends RiiabloTest {
  @ParameterizedTest @ValueSource(strings={"steallife","stealmana"})
  void localFullPoolCueAndRemoteCueUseSameActualAssetClockAndRetire(String name) {
    AssetManager previous=Riiablo.assets;
    com.riiablo.Colors previousColors=Riiablo.colors;
    AssetManager assets=mock(AssetManager.class); Riiablo.assets=assets; Riiablo.colors=new com.riiablo.Colors();
    com.riiablo.codec.excel.Overlay.Entry row=Riiablo.files.Overlay.get(name);
    String path="data/global/overlays/"+row.Filename+".dcc";
    ByteBuffer bytes=ByteBuffer.wrap(Riiablo.mpqs.resolve(path).readBytes()).order(ByteOrder.LITTLE_ENDIAN);
    int frames=bytes.getInt(3); assertEquals(1,bytes.get(2)&255); assertTrue(frames>0);
    DCC dcc=mock(DCC.class);
    when(dcc.getNumDirections()).thenReturn(1); when(dcc.getNumFramesPerDir()).thenReturn(frames);
    when(dcc.getBox(anyInt())).thenReturn(new BBox());
    when(assets.isLoaded(any(AssetDescriptor.class))).thenReturn(true);
    when(assets.contains(anyString())).thenReturn(true); when(assets.isLoaded(anyString())).thenReturn(true);
    when(assets.get(any(AssetDescriptor.class))).thenReturn(dcc);
    EventSystem events=new EventSystem(); OverlayManager manager=new OverlayManager();
    World world=new World(new WorldConfigurationBuilder().with(events,manager,new OverlayStepper(),
        new ElementalHitPresentationSystem()).build().register("clientContext",ClientGameplayContext.fromGlobals()));
    try {
      int attacker=world.create(),target=world.create();
      world.getMapper(Position.class).create(attacker); world.getMapper(Position.class).create(target);
      world.setDelta(1f/25f); world.process();
      DamageEvent hit=DamageEvent.obtainMelee(attacker,target,20,20);
      hit.leechOverlay=name; // The server chose this cue; full resource pools gained zero.
      events.dispatch(new DamageAppliedEvent(hit,100,80)); world.process();
      Overlay overlay=world.getMapper(Overlay.class).get(attacker);
      assertNotNull(overlay); assertEquals(name,overlay.entry.overlay); assertFalse(overlay.persistent);
      assertTrue(overlay.usesNativeRendering()); assertEquals(.04f,overlay.animation.getFrameDuration(),.000001f);
      assertEquals(com.riiablo.graphics.BlendMode.ADDITIVE,overlay.animation.getLayer(0).getBlendMode());
      assertNull(world.getMapper(Overlay.class).get(target),"leech feedback belongs to the attacker");
      assertEquals(0,overlay.layers.size,"dual leech still presents the single authority-selected cue");
      events.dispatch(UnitOverlayEvent.obtain(attacker,name)); world.process();
      assertEquals(0,world.getMapper(Overlay.class).get(attacker).layers.size);
      for(int i=0;i<frames+5;i++) world.process();
      assertFalse(manager.isOneShotActive(attacker,name));
      verify(assets,times(2)).load(any(AssetDescriptor.class)); verify(assets,times(2)).unload(path);
      System.out.println("HUNGER_OVERLAY name="+name+" path="+path+" dcc_frames="+frames
          +" table_frames="+row.Frames+" directions=1 AnimRate="+row.AnimRate+" Trans="+row.Trans);
    } finally { world.dispose(); Riiablo.assets=previous; Riiablo.colors=previousColors; }
  }
}
