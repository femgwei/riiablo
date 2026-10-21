package com.riiablo.presentation;


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
import org.junit.jupiter.api.Assumptions;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Retail frame ranges and ECS lifecycle; does not require a GL/audio device. */
class ObjectBreakPresentationTest extends RiiabloTest {
  private static final int[] CONTAINERS = {
      1, 3, 4, 5, 6, 7, 9, 11, 28, 46, 50, 51, 52, 53, 54, 55, 56, 57, 58,
      79, 87, 89, 94, 95, 104, 105, 106, 107, 125, 126, 127, 128, 139, 140,
      142, 143, 154, 155, 158, 159, 163, 169, 174, 175, 178, 179, 180, 182,
      185, 186, 187, 188, 203, 204, 205, 208, 209, 222, 223, 224, 225, 233,
      244, 246, 247, 248, 250, 259, 266, 284, 289, 326, 360, 362, 363, 365,
      372, 373, 383, 384, 416, 418, 419, 420, 424, 425, 430, 431, 432, 433,
      443, 444, 445, 448, 454, 455, 463, 466, 467, 468, 469, 470, 471, 477,
      485, 500, 504, 516, 517, 518, 524, 535, 540, 541, 544, 545, 548, 549,
      550, 551, 556
  };
  private static Colors savedColors;

  @BeforeAll static void initializeColors() {
    savedColors = Riiablo.colors;
    Riiablo.colors = new Colors();
  }

  @AfterAll static void restoreColors() {
    Riiablo.colors = savedColors;
  }


  @Test void nativeBreakBanksResolveToRealMpqSounds() {
    assertEquals("object_wood_break_1", ObjectBreakSoundEmitter.breakSound(7));
    assertEquals("object_barrel_explode", ObjectBreakSoundEmitter.breakSound(11));
    assertEquals("object_urn_break_1", ObjectBreakSoundEmitter.breakSound(4));
    assertEquals("object_basket_1", ObjectBreakSoundEmitter.breakSound(208));
    assertNull(ObjectBreakSoundEmitter.breakSound(283), "a torch is not an urn");
    assertEquals("object_chest_special", ObjectBreakSoundEmitter.breakSound(466));
    assertEquals("object_xbarrel", ObjectBreakSoundEmitter.breakSound(418));
    assertNull(ObjectBreakSoundEmitter.breakSound(104), "racks skip OP");
    int mapped = 0;
    for (Objects.Entry base : Riiablo.files.objects) {
      for (byte mode = Engine.Object.MODE_OP; mode <= Engine.Object.MODE_ON; mode++) {
        String bank = ObjectBreakSoundEmitter.operationSound(base.Id, mode);
        if (bank == null) continue;
        mapped++;
        com.riiablo.codec.excel.Sounds.Entry sound = Riiablo.files.Sounds.get(bank);
        assertNotNull(sound, "sound bank " + base.Id + "/" + mode);
        int variants = Math.max(1, sound.Group_Size);
        for (int v = 0; v < variants; v++) {
          String path = "data/global/sfx/" + Riiablo.files.Sounds.get(sound.Index + v).FileName;
          assertTrue(Riiablo.mpqs.contains(path), path);
        }
      }
    }
    assertEquals(179, mapped, "audited native class/mode sound slots");
  }

  @Test void allContainerSoundSlotsMatchTheOriginal110fClient() throws Exception {
    String home = System.getenv("D2_HOME");
    Assumptions.assumeTrue(home != null, "optional local binary oracle requires D2_HOME");
    Path dll = Paths.get(home, "D2Client.dll");
    Assumptions.assumeTrue(java.nio.file.Files.exists(dll), "original D2Client.dll required");
    byte[] bytes = java.nio.file.Files.readAllBytes(dll);
    byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
    StringBuilder hex = new StringBuilder();
    for (byte b : hash) hex.append(String.format("%02x", b & 0xff));
    assertEquals("94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282",
        hex.toString(), "binary oracle must be the verified 1.10f client");
    ByteBuffer pe = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    int checked = 0;
    for (Objects.Entry base : Riiablo.files.objects) {
      if (!auditedOperateFunction(base.OperateFn) || base.Id == 321 || base.Id == 341) continue;
      int pointer = pe.getInt(binaryOffset(pe, 0x6FB957F0) + 4 * base.Id);
      for (byte mode = Engine.Object.MODE_OP; mode <= Engine.Object.MODE_ON; mode++) {
        int expected = pointer == 0 ? 0 : pe.getInt(binaryOffset(pe, pointer) + mode * 4);
        String actual = ObjectBreakSoundEmitter.operationSound(base.Id, mode);
        if (expected == 0) {
          assertNull(actual, "silent native slot " + base.Id + "/" + mode);
        } else {
          assertNotNull(actual, "missing native slot " + base.Id + "/" + mode);
          assertEquals(expected, Riiablo.files.Sounds.get(actual).Index,
              "native sound id " + base.Id + "/" + mode);
          checked++;
        }
      }
    }
    assertEquals(179, checked);
  }

  private static boolean auditedOperateFunction(int fn) {
    switch (fn) {
      case 1: case 3: case 4: case 5: case 7: case 14: case 19: case 20:
      case 26: case 30: case 33: case 39: case 40: case 41: case 51:
      case 57: case 58: case 59: case 68: return true;
      default: return false;
    }
  }

  private static int binaryOffset(ByteBuffer bytes, int va) {
    int pe = bytes.getInt(0x3c);
    int optional = pe + 24;
    int rva = va - bytes.getInt(optional + 28);
    int sections = optional + (bytes.getShort(pe + 20) & 0xffff);
    for (int i = 0; i < (bytes.getShort(pe + 6) & 0xffff); i++) {
      int section = sections + i * 40;
      int start = bytes.getInt(section + 12);
      int size = Math.max(bytes.getInt(section + 8), bytes.getInt(section + 16));
      if (rva >= start && rva < start + size) return bytes.getInt(section + 20) + rva - start;
    }
    throw new IllegalArgumentException("unmapped 1.10f VA " + Integer.toHexString(va));
  }

  @Test void oneOperationKeepsLiveAndPersistentModesAndEmitsOneBreakSound() {
    for (int id : CONTAINERS) {
      ObjectInteractor interactor = new ObjectInteractor();
      SoundProbe sound = new SoundProbe();
      DropProbe drops = new DropProbe();
      EventSystem events = new EventSystem();
      World world = new World(new WorldConfigurationBuilder().with(events,
          new CofManager(), interactor, new SequenceHandler(), sound, drops)
          .build().register("map", new Map(7, 0))
          .register("clientContext", ClientGameplayContext.fromGlobals()));
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
        boolean instant = NativeObjectOperateTable.resolve(base, null)
            == NativeObjectOperateTable.Lifecycle.INSTANT_CONTAINER;
        assertEquals(instant ? Engine.Object.MODE_ON : Engine.Object.MODE_OP,
            object.mode, "replicate the live transition " + id);
        assertEquals(Engine.Object.MODE_ON, source.currentMode(), "persist the final state " + id);
        assertEquals(ObjectBreakSoundEmitter.operationSound(id, object.mode) == null ? 0 : 1,
            sound.calls, "sound at native mode " + id);
        events.dispatch(ModeChangeEvent.obtain(entity, object.mode, true));
        assertFalse(world.getMapper(Interactable.class).has(entity));
        interactor.interact(100, entity);
        assertEquals(1, drops.activations, "loot/trap entry must fire only once " + id);
        if (!instant) events.dispatch(AnimDataFinishedEvent.obtain(entity));
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

  @Test void restoredSnapshotsAndReusedEntityIdsDoNotReplayOperationSounds() {
    for (int id : new int[] {7, 5, 104, 106, 179}) {
      EventSystem events = new EventSystem();
      CofManager cofs = new CofManager();
      SoundProbe sound = new SoundProbe();
      World world = new World(new WorldConfigurationBuilder().with(events, cofs, sound).build()
          .register("clientContext", ClientGameplayContext.fromGlobals()));
      try {
        byte mode = id == 7 ? Engine.Object.MODE_OP : Engine.Object.MODE_ON;
        int entity = world.create();
        world.getMapper(com.riiablo.engine.server.component.Object.class).create(entity).base =
            Riiablo.files.objects.get(id);
        world.getMapper(CofReference.class).create(entity).set("test", Engine.Object.MODE_NU);
        cofs.setMode(entity, mode); // A snapshot is applied before the first ECS insertion pass.
        world.process();
        assertEquals(0, sound.calls, "snapshot must be silent " + id);
        cofs.setMode(entity, Engine.Object.MODE_NU);
        cofs.setMode(entity, mode);
        assertEquals(1, sound.calls, "a later real operation is audible " + id);
        cofs.setMode(entity, mode, true);
        assertEquals(1, sound.calls, "same-mode restart stays silent " + id);
        world.delete(entity);
        world.process();
        int replacement = world.create();
        assertEquals(entity, replacement, "exercise Artemis entity-id reuse");
        world.getMapper(com.riiablo.engine.server.component.Object.class).create(replacement).base =
            Riiablo.files.objects.get(id);
        world.getMapper(CofReference.class).create(replacement).set("test", Engine.Object.MODE_NU);
        cofs.setMode(replacement, mode);
        world.process();
        assertEquals(1, sound.calls, "reconstructed object stays silent " + id);
      } finally {
        world.dispose();
      }
    }
  }

  @com.artemis.annotations.All(com.riiablo.engine.server.component.Object.class)
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
