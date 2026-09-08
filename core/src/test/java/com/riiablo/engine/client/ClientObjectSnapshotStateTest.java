package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.flatbuffers.FlatBufferBuilder;
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
}
