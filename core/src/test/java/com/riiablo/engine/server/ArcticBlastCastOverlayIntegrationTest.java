package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.audio.Audio;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.event.SkillStartEvent;
import com.riiablo.presentation.component.Overlay;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class ArcticBlastCastOverlayIntegrationTest extends RiiabloTest {
  private JsonValue original() throws Exception {
    try(java.io.InputStream in=getClass().getResourceAsStream("/original/arctic-blast-overlay-1.10f.json")) {
      assertNotNull(in);return new JsonReader().parse(new String(in.readAllBytes(),StandardCharsets.UTF_8));
    }
  }
  private Overlay overlay(ArcticBlastSequenceIntegrationTest.Fixture f) {
    Overlay root=f.world.getMapper(Overlay.class).get(f.caster);
    if(root==null)return null;
    if(root.entry!=null&&"arctic_blast_cast".equals(root.entry.overlay))return root;
    for(Overlay child:root.layers)if(child.entry!=null&&"arctic_blast_cast".equals(child.entry.overlay))return child;
    return null;
  }

  @Test void realStartPlaysOneNativeOverlayAndOneStartSoundForTheWholeHeldAction() {
    Audio saved=Riiablo.audio,audio=Riiablo.audio=mock(Audio.class);
    try(ArcticBlastSequenceIntegrationTest.Fixture f=new ArcticBlastSequenceIntegrationTest.Fixture()) {
      assertNotNull(Riiablo.files.NativeSkills,"lossless skill table");
      assertEquals("arctic_blast_cast",Riiablo.files.NativeSkills.get(230).string("prgoverlay"),
          Riiablo.files.NativeSkills.get(230).skill);
      assertEquals(15,Riiablo.files.skills.get(230).cltstfunc);
      f.start();assertNotNull(overlay(f));
      for(int i=0;i<60;i++){f.input(true,new Vector2(4530,5642));f.step();}
      assertEquals(1,f.overlays.requests);assertNull(overlay(f));
      assertTrue(f.world.getMapper(Casting.class).has(f.caster),"ONCE layer ends even while the channel continues");
      verify(audio,times(1)).play("druid_windcast",true);
    } finally {Riiablo.audio=saved;}
  }

  @Test void everyObservedActiveCursorMatchesTheProductionResourceClock() throws Exception {
    JsonValue fixture=original();
    assertTrue(fixture.getBoolean("background_verified"));assertTrue(fixture.getBoolean("owned_game_closed"));
    assertEquals(21,fixture.get("resource").getInt("frames"));
    try(ArcticBlastSequenceIntegrationTest.Fixture f=new ArcticBlastSequenceIntegrationTest.Fixture()) {
      f.start();Map<Integer,Integer> frames=new HashMap<>();
      for(int tick=0;tick<23;tick++) {
        f.input(true,new Vector2(4530,5642));f.step();Overlay layer=overlay(f);
        if(layer==null)continue;
        assertTrue(layer.isLoaded);assertTrue(layer.usesNativeRendering());assertFalse(layer.persistent);
        assertEquals(256,layer.animation.getFrameDelta());
        frames.put(layer.nativeFrame,layer.animation.getFrame());
      }
      int active=0,terminal=0;
      for(JsonValue capture:fixture.get("captures"))for(JsonValue state:capture.get("states")) {
        assertEquals(2,state.getInt("overlay_type"));
        int cursor=state.getInt("cursor");
        if(state.getInt("value_08")==0){assertEquals(5376,cursor);terminal++;continue;}
        assertEquals(cursor>>>8,frames.get(cursor/256));active++;
      }
      assertEquals(42,active);assertEquals(2,terminal);assertNull(overlay(f));
    }
  }

  @Test void lateAssetsSeekToAgeWithoutRestartingTheOnceLayer() {
    try(ArcticBlastSequenceIntegrationTest.Fixture f=new ArcticBlastSequenceIntegrationTest.Fixture()) {
      f.overlayLoadAfter=12;f.start();
      for(int i=1;i<=12;i++){f.input(true,new Vector2(4530,5642));f.step();}
      Overlay layer=overlay(f);assertNotNull(layer);assertTrue(layer.isLoaded);
      assertEquals(11,layer.nativeFrame);assertEquals(11,layer.animation.getFrame());
      for(int i=0;i<12;i++){f.input(true,new Vector2(4530,5642));f.step();}
      assertNull(overlay(f));assertEquals(1,f.overlays.requests);
    }
  }

  @Test void missingAssetsExpireOnTimeAndCannotFlashWhenTheyBecomeAvailable() {
    try(ArcticBlastSequenceIntegrationTest.Fixture f=new ArcticBlastSequenceIntegrationTest.Fixture()) {
      f.overlayLoadAfter=1000;f.start();
      assertEquals(1,f.overlays.requests);assertNotNull(overlay(f));
      for(int i=0;i<24;i++){f.input(true,new Vector2(4530,5642));f.step();}
      assertNull(overlay(f));f.overlayLoadAfter=0;f.step();assertNull(overlay(f));
      assertTrue(f.world.getMapper(Casting.class).has(f.caster));
    }
  }

  @Test void repeatedStartsAreDeduplicatedButANewCastGetsANewBirthClock() {
    try(ArcticBlastSequenceIntegrationTest.Fixture f=new ArcticBlastSequenceIntegrationTest.Fixture()) {
      f.start();f.world.getSystem(EventSystem.class).dispatch(SkillStartEvent.obtain(f.caster,230,-1,new Vector2(4530,5642),11,15));
      assertEquals(1,f.overlays.requests);
      for(int i=0;i<60;i++)f.step();assertNull(overlay(f));
      f.start();assertEquals(2,f.overlays.requests);f.step();
      assertEquals(0,overlay(f).nativeFrame);assertEquals(0,overlay(f).animation.getFrame());
    }
  }

  @Test void renderUpdatesAtSixtyHzKeepTheTwentyFiveHzOnceClock() {
    try(ArcticBlastSequenceIntegrationTest.Fixture f=new ArcticBlastSequenceIntegrationTest.Fixture()) {
      f.world.setDelta(1f/60f);f.start();
      for(int i=0;i<52;i++) {
        f.step();Overlay layer=overlay(f);
        int age=(int)Math.floor(i*25.0/60+.00001);
        if(age<21){assertNotNull(layer);assertEquals(age,layer.nativeFrame);assertEquals(age,layer.animation.getFrame());}
        else assertNull(layer);
      }
    }
  }

  @Test void completingTheCastLayerPreservesAnUnrelatedPersistentLayer() {
    try(ArcticBlastSequenceIntegrationTest.Fixture f=new ArcticBlastSequenceIntegrationTest.Fixture()) {
      com.riiablo.codec.DCC other=spy(com.riiablo.codec.DCC.loadFromFile(Riiablo.mpqs.resolve("data/global/overlays/FireEnchant.dcc")));
      doNothing().when(other).loadDirection(org.mockito.ArgumentMatchers.anyInt());
      f.dccs.add(other);f.resources.put("fireenchant",other);
      f.overlays.setPersistent(f.caster,12345,"enchant");f.start();
      for(int i=0;i<24;i++){f.input(true,new Vector2(4530,5642));f.step();}
      assertNull(overlay(f));Overlay root=f.world.getMapper(Overlay.class).get(f.caster);
      assertNotNull(root);assertEquals("enchant",root.entry.overlay);assertTrue(root.persistent);
    }
  }
}
