package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.codec.excel.Objects;
import com.riiablo.engine.Engine;
import com.riiablo.engine.client.component.Selectable;
import com.riiablo.engine.server.ObjectInteractor;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.net.packet.d2gs.ObjectP;
import org.junit.jupiter.api.Test;

/** Compatibility checks for applying authoritative dynamic-object state. */
class ClientObjectSnapshotStateTest {
  @Test
  void recognizesNewInteractionFlagsButLeavesLegacyPayloadUntouched() {
    FlatBufferBuilder legacyBuilder = new FlatBufferBuilder(32);
    ObjectP.startObjectP(legacyBuilder);
    ObjectP.addObjectId(legacyBuilder, 580);
    legacyBuilder.finish(ObjectP.endObjectP(legacyBuilder));
    assertFalse(ClientNetworkReceiver.hasObjectStateSnapshot(
        ObjectP.getRootAsObjectP(legacyBuilder.dataBuffer())));

    FlatBufferBuilder activeBuilder = new FlatBufferBuilder(32);
    ObjectP.startObjectP(activeBuilder);
    ObjectP.addObjectId(activeBuilder, 580);
    ObjectP.addStateFlags(activeBuilder, 4);
    activeBuilder.finish(ObjectP.endObjectP(activeBuilder));
    assertTrue(ClientNetworkReceiver.hasObjectStateSnapshot(
        ObjectP.getRootAsObjectP(activeBuilder.dataBuffer())));

    FlatBufferBuilder modeBuilder = new FlatBufferBuilder(32);
    ObjectP.startObjectP(modeBuilder);
    ObjectP.addObjectId(modeBuilder, 580);
    ObjectP.addMode(modeBuilder, 2);
    modeBuilder.finish(ObjectP.endObjectP(modeBuilder));
    assertTrue(ClientNetworkReceiver.hasObjectStateSnapshot(
        ObjectP.getRootAsObjectP(modeBuilder.dataBuffer())));
  }

  @Test
  void appliesAndRebuildsAuthoritativeInteractionState() {
    World world = new World(new WorldConfigurationBuilder().build());
    try {
      TestClientEntityFactory factory = new TestClientEntityFactory();
      factory.bind(world);
      int entityId = world.create();
      Objects.Entry base = new Objects.Entry();
      base.Draw = true;
      base.OperateFn = 4;
      base.OperateRange = 5;
      base.Selectable = new boolean[8];
      base.Selectable[Engine.Object.MODE_NU] = true;
      com.riiablo.engine.server.component.Object object = world.getMapper(
          com.riiablo.engine.server.component.Object.class).create(entityId);
      object.base = base;

      factory.applyAuthoritativeObjectState(entityId, Engine.Object.MODE_NU,
          com.riiablo.engine.server.component.Object.STATE_INTERACTABLE);
      assertEquals(Engine.Object.MODE_NU, object.mode);
      assertTrue(world.getMapper(Interactable.class).has(entityId));
      assertEquals(5f, world.getMapper(Interactable.class).get(entityId).range);
      assertSame(factory.interactor,
          world.getMapper(Interactable.class).get(entityId).interactor);
      assertTrue(world.getMapper(Selectable.class).has(entityId));

      int openedDoorFlags = com.riiablo.engine.server.component.Object.STATE_OPENED
          | com.riiablo.engine.server.component.Object.STATE_INTERACTABLE;
      factory.applyAuthoritativeObjectState(entityId, Engine.Object.MODE_ON, openedDoorFlags);
      assertEquals(openedDoorFlags, object.stateFlags);
      assertTrue(world.getMapper(Interactable.class).has(entityId));
      assertTrue(world.getMapper(Selectable.class).has(entityId));

      int exhaustedFlags = com.riiablo.engine.server.component.Object.STATE_ACTIVATED;
      factory.applyAuthoritativeObjectState(entityId, Engine.Object.MODE_ON, exhaustedFlags);
      assertEquals(Engine.Object.MODE_ON, object.mode);
      assertEquals(exhaustedFlags, object.stateFlags);
      assertFalse(world.getMapper(Interactable.class).has(entityId));
      assertFalse(world.getMapper(Selectable.class).has(entityId));

      factory.applyAuthoritativeObjectState(entityId, Engine.Object.MODE_NU,
          com.riiablo.engine.server.component.Object.STATE_INTERACTABLE);
      assertTrue(world.getMapper(Interactable.class).has(entityId));
      assertEquals(5f, world.getMapper(Interactable.class).get(entityId).range);
      assertSame(factory.interactor,
          world.getMapper(Interactable.class).get(entityId).interactor);
      assertTrue(world.getMapper(Selectable.class).has(entityId));
    } finally {
      world.dispose();
    }
  }

  private static class TestClientEntityFactory extends ClientEntityFactory {
    final ObjectInteractor interactor = new ObjectInteractor();

    void bind(World world) {
      mObject = world.getMapper(com.riiablo.engine.server.component.Object.class);
      mInteractable = world.getMapper(Interactable.class);
      mSelectable = world.getMapper(Selectable.class);
      objectInteractor = interactor;
    }
  }
}
