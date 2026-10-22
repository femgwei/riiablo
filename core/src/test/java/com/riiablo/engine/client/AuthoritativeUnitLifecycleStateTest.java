package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.net.packet.d2gs.CofReferenceP;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.net.packet.d2gs.StateP;
import com.riiablo.net.packet.d2gs.VitalsP;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuthoritativeUnitLifecycleStateTest {
  @Test void valuesAndStateListSurviveBufferReuseAndReplicaMutation() {
    AuthoritativeUnitLifecycleState store = new AuthoritativeUnitLifecycleState();
    EntitySync wire = packet(10, (int) Engine.Player.MODE_DD, true, true);
    assertTrue(store.commit(4, wire));
    AuthoritativeUnitLifecycleState.Snapshot value = store.get(4);
    java.nio.ByteBuffer buffer = wire.getByteBuffer();
    for (int i = buffer.position(); i < buffer.limit(); i++) buffer.put(i, (byte) 0);
    assertEquals(10, value.tick);
    assertEquals(Engine.Player.MODE_DD, value.mode);
    assertEquals(AuthoritativeUnitLifecycleState.Phase.CORPSE, value.phase);
    assertTrue(value.vitals.dead);
    assertEquals(80f, value.vitals.maxHitpoints);
    assertEquals(1, value.states.size());
    assertEquals(StateId.POISON, value.states.get(0).id);
    assertEquals(12, value.states.get(0).duration);
    assertEquals(6, value.states.get(0).periodicCountdownFrames);
    assertEquals(7, value.states.get(0).sourceEntityId);
    assertThrows(UnsupportedOperationException.class, () -> value.states.clear());
  }

  @Test void newerRespawnCannotBeOverwrittenByOldDeathOrClockRegression() {
    AuthoritativeUnitLifecycleState store = new AuthoritativeUnitLifecycleState();
    store.commit(4, packet(10, (int) Engine.Player.MODE_DD, true, true));
    store.commit(4, packet(12, (int) Engine.Player.MODE_NU, false, null));
    AuthoritativeUnitLifecycleState.Snapshot alive = store.get(4);
    assertFalse(store.commit(4, packet(11, (int) Engine.Player.MODE_DT, true, null)));
    assertSame(alive, store.get(4));
    assertFalse(alive.dead());
    assertEquals(1, alive.states.size(), "omitted StateP retains its committed values");
    assertTrue(store.commit(4, packet(0, null, null, null)));
    assertFalse(store.commit(4, packet(11, (int) Engine.Player.MODE_DT, true, null)),
        "legacy packets cannot erase the stamped watermark");
  }

  @Test void emptyStatesClearWhileUnrelatedPartialPacketsRetainAlivePhase() {
    AuthoritativeUnitLifecycleState store = new AuthoritativeUnitLifecycleState();
    store.commit(4, packet(10, (int) Engine.Player.MODE_DD, true, true));
    AuthoritativeUnitLifecycleState.Snapshot dead = store.get(4);
    store.commit(4, packet(11, null, false, false));
    assertFalse(store.get(4).dead(), "fresh vitals can precede the respawn mode");
    assertTrue(store.get(4).states.isEmpty());
    store.commit(4, packet(12, null, null, null));
    assertFalse(store.get(4).dead(), "a retained DD mode must not undo fresh vitals");
    assertEquals(1, dead.states.size());
    assertTrue(dead.dead());
  }

  @Test void modeOnlyDeathAndReviveDoNotDependOnPreviousVitals() {
    AuthoritativeUnitLifecycleState store = new AuthoritativeUnitLifecycleState();
    store.commit(4, packet(10, (int) Engine.Player.MODE_DT, true, null));
    store.commit(4, packet(11, (int) Engine.Player.MODE_NU, null, null));
    assertFalse(store.get(4).dead());
    store.commit(4, packet(12, null, null, null));
    assertFalse(store.get(4).dead());
    store.commit(4, packet(13, (int) Engine.Player.MODE_DD, null, null));
    assertTrue(store.get(4).dead());
  }

  @Test void removeAndBaselineClearDropSnapshotsAndIncarnationWatermarks() {
    AuthoritativeUnitLifecycleState store = new AuthoritativeUnitLifecycleState();
    store.commit(4, packet(10, (int) Engine.Player.MODE_DD, true, true));
    store.remove(4);
    assertNull(store.get(4));
    assertTrue(store.commit(4, packet(1, (int) Engine.Player.MODE_NU, false, null)));
    store.commit(5, packet(10, (int) Engine.Player.MODE_DD, true, true));
    store.clear();
    assertNull(store.get(4));
    assertNull(store.get(5));
    assertTrue(store.commit(5, packet(1, (int) Engine.Player.MODE_NU, false, null)));
  }

  @Test void replicaWriterPreservesIndependentSourceLayers() {
    FlatBufferBuilder b = new FlatBufferBuilder(256);
    int ids = StateP.createStateIdVector(b, new short[] {(short) StateId.POISON, (short) StateId.POISON});
    int durations = StateP.createDurationVector(b, new int[] {12, 13});
    int sources = StateP.createSourceEntityIdVector(b, new int[] {7, 8});
    int skills = StateP.createSkillIdVector(b, new int[] {4, 5});
    int values = StateP.createRuntimeValueVector(b, new short[] {11, 22});
    int states = StateP.createStateP(b, ids, durations, 0, 0, values, 0, 0, 0, 0, 0, sources, skills, 0, 0);
    int types = EntitySync.createComponentTypeVector(b, new byte[] {ComponentP.StateP});
    int components = EntitySync.createComponentVector(b, new int[] {states});
    b.finish(EntitySync.createEntitySync(b, 100, Class.Type.PLR.ordinal(), 0, types, components,
        10, 400, 0, 0, 0, -1));
    AuthoritativeUnitLifecycleState store = new AuthoritativeUnitLifecycleState();
    store.commit(4, EntitySync.getRootAsEntitySync(b.dataBuffer()));
    com.riiablo.engine.server.component.UnitStates replica = new com.riiablo.engine.server.component.UnitStates();
    ClientUnitSnapshotWriter.states(4, replica, store.get(4));
    assertTrue(replica.snapshotOnly);
    assertEquals(2, replica.stateList.getStates().size);
    assertEquals(11, replica.stateList.getStateLayer(StateId.POISON, 7, 4).runtimeValue);
    assertEquals(22, replica.stateList.getStateLayer(StateId.POISON, 8, 5).runtimeValue);
    replica.stateList.getStateLayer(StateId.POISON, 7, 4).duration = 1;
    assertEquals(12, store.get(4).states.get(0).duration);
  }

  static EntitySync packet(long tick, Integer mode, Boolean dead, Boolean states) {
    FlatBufferBuilder builder = new FlatBufferBuilder(256);
    List<Integer> values = new ArrayList<>();
    List<Byte> types = new ArrayList<>();
    if (mode != null) {
      values.add(CofReferenceP.createCofReferenceP(builder, mode, 0, 0));
      types.add(ComponentP.CofReferenceP);
    }
    if (dead != null) {
      values.add(VitalsP.createVitalsP(builder, dead ? 0f : 37f, 80f, 9f, 20f, dead, 3f, 15f));
      types.add(ComponentP.VitalsP);
    }
    if (states != null) {
      int ids = StateP.createStateIdVector(builder, states ? new short[] {(short) StateId.POISON} : new short[0]);
      int durations = StateP.createDurationVector(builder, new int[] {12});
      int levels = StateP.createLevelVector(builder, new byte[] {2});
      int sources = StateP.createSourceEntityIdVector(builder, new int[] {7});
      int countdown = StateP.createPeriodicCountdownFramesVector(builder, new int[] {6});
      values.add(StateP.createStateP(builder, ids, durations, levels, 0, 0, 0, 0,
          0, 0, 0, sources, 0, 0, countdown));
      types.add(ComponentP.StateP);
    }
    int[] offsets = new int[values.size()];
    byte[] tags = new byte[types.size()];
    for (int i = 0; i < offsets.length; i++) { offsets[i] = values.get(i); tags[i] = types.get(i); }
    int components = EntitySync.createComponentVector(builder, offsets);
    int componentTypes = EntitySync.createComponentTypeVector(builder, tags);
    builder.finish(EntitySync.createEntitySync(builder, 100, Class.Type.PLR.ordinal(), 0,
        componentTypes, components, tick, tick * 40, 0, 0, 0, -1));
    return EntitySync.getRootAsEntitySync(builder.dataBuffer());
  }
}
