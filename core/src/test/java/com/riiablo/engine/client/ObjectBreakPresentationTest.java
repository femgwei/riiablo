package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.Riiablo;
import com.riiablo.Colors;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.Animation;
import com.riiablo.codec.COF;
import com.riiablo.codec.excel.Objects;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.ObjectInteractor;
import com.riiablo.engine.server.SequenceHandler;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.NativeObjectState;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.event.AnimDataFinishedEvent;
import com.riiablo.engine.server.event.ObjectInteractionEvent;
import com.riiablo.engine.server.event.ModeChangeEvent;
import com.riiablo.engine.server.object.NativeObjectOperateTable;
import com.riiablo.map.Map;
import com.riiablo.map.NativePresetObjectResolver;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;

/** Retail frame ranges and ECS lifecycle; does not require a GL/audio device. */
class ObjectBreakPresentationTest extends RiiabloTest {
  private static final int[] CONTAINERS = {4, 7, 9, 11, 52, 94, 95, 208, 209, 362};
  private static Colors savedColors;

  @BeforeAll static void initializeColors() {
    savedColors = Riiablo.colors;
    Riiablo.colors = new Colors();
  }

  @AfterAll static void restoreColors() {
    Riiablo.colors = savedColors;
  }

  @Test void destroyedFrameSurvivesPlaybackAndReconstruction() {
    for (int id : CONTAINERS) {
      Objects.Entry base = Riiablo.files.objects.get(id);
      String token = Riiablo.files.ObjType.get(id).Token;
      String path = "data/global/objects/" + token + "/cof/" + token + "ONhth.cof";
      COF cof = COF.loadFromFile(Riiablo.mpqs.resolve(path));
      Animation animation = Animation.newAnimation(cof);
      int expected = Math.min(base.Start[Engine.Object.MODE_ON], cof.getNumFramesPerDir() - 1);
      CofLayerCacher.configureObjectAnimation(animation, base, Engine.Object.MODE_ON);
      assertEquals(expected, animation.getFrame(), "opened object " + id);
      for (int tick = 0; tick < 50; tick++) animation.update(0.04f);
      assertEquals(expected, animation.getFrame(), "must keep the broken frame " + id);
      CofLayerCacher.configureObjectAnimation(animation, base, Engine.Object.MODE_ON);
      assertEquals(expected, animation.getFrame(), "room recreation " + id);
    }
  }

  @Test void nativeBreakBanksResolveToRealMpqSounds() {
    assertEquals("object_wood_break_1", ObjectBreakSoundEmitter.breakSound(7));
    assertEquals("object_barrel_explode", ObjectBreakSoundEmitter.breakSound(11));
    assertEquals("object_urn_break_1", ObjectBreakSoundEmitter.breakSound(4));
    assertEquals("object_basket_1", ObjectBreakSoundEmitter.breakSound(208));
    assertNull(ObjectBreakSoundEmitter.breakSound(283), "a torch is not an urn");
    for (int id : CONTAINERS) {
      com.riiablo.codec.excel.Sounds.Entry sound = Riiablo.files.Sounds.get(
          ObjectBreakSoundEmitter.breakSound(id));
      assertNotNull(sound, "sound bank " + id);
      int variants = Math.max(1, sound.Group_Size);
      for (int v = 0; v < variants; v++) {
        String path = "data/global/sfx/" + Riiablo.files.Sounds.get(sound.Index + v).FileName;
        assertTrue(Riiablo.mpqs.contains(path), path);
      }
    }
  }

  @Test void oneOperationKeepsLiveAndPersistentModesAndEmitsOneBreakSound() {
    for (int id : CONTAINERS) {
      ObjectInteractor interactor = new ObjectInteractor();
      SoundProbe sound = new SoundProbe();
      DropProbe drops = new DropProbe();
      EventSystem events = new EventSystem();
      World world = new World(new WorldConfigurationBuilder().with(events,
          new CofManager(), interactor, new SequenceHandler(), sound, drops)
          .build().register("map", new Map(7, 0)));
      try {
        int entity = world.create();
        Objects.Entry base = Riiablo.files.objects.get(id);
        com.riiablo.engine.server.component.Object object = world.getMapper(
            com.riiablo.engine.server.component.Object.class).create(entity);
        object.base = base;
        world.getMapper(CofReference.class).create(entity).set(base.Token, Engine.Object.MODE_NU);
        world.getMapper(AnimData.class).create(entity).numFrames = base.FrameCnt[1] << 8;
        Map.NativeObject source = new Map.NativeObject(id, Engine.Object.MODE_NU, 0, 0, false, false);
        NativeObjectState state = world.getMapper(NativeObjectState.class).create(entity)
            .set(source, id, id, id, Engine.Object.MODE_NU, false, false,
                NativePresetObjectResolver.Kind.ORDINARY);
        state.persistInteractType(0);
        world.getMapper(Interactable.class).create(entity).set(4f, interactor);
        world.process();
        assertTrue(NativeObjectOperateTable.isNetworkContainer(base), "D2GS admission " + id);
        interactor.interact(100, entity);
        world.process();
        assertEquals(Engine.Object.MODE_OP, object.mode, "replicate the live transition " + id);
        assertEquals(Engine.Object.MODE_ON, source.currentMode(), "persist the final state " + id);
        assertEquals(1, sound.calls);
        assertFalse(world.getMapper(Interactable.class).has(entity));
        interactor.interact(100, entity);
        assertEquals(1, drops.activations, "loot/trap entry must fire only once " + id);
        events.dispatch(AnimDataFinishedEvent.obtain(entity));
        assertEquals(Engine.Object.MODE_ON, object.mode, "final live mode " + id);
        assertEquals(Engine.Object.MODE_ON, world.getMapper(CofReference.class).get(entity).mode);
        assertFalse(world.getMapper(Sequence.class).has(entity));
        interactor.interact(100, entity);
        assertEquals(1, sound.calls);
        assertEquals(1, drops.activations);
        NativeObjectState restored = new NativeObjectState().set(source, id, id, id,
            Engine.Object.MODE_NU, false, false, NativePresetObjectResolver.Kind.ORDINARY);
        assertEquals(Engine.Object.MODE_ON, restored.initialMode);
        assertTrue(id == 11 ? restored.activated : restored.opened);
      } finally {
        world.dispose();
      }
    }
  }

  private static class SoundProbe extends ObjectBreakSoundEmitter {
    int calls;
    @Subscribe @Override public void onModeChanged(ModeChangeEvent event) {
      super.onModeChanged(event);
    }
    @Override protected void play(int entityId, String sound) { calls++; }
  }

  private static class DropProbe extends PassiveSystem {
    int activations;
    @Subscribe public void onInteraction(ObjectInteractionEvent event) {
      if (event.firstActivation()) activations++;
    }
  }
}
