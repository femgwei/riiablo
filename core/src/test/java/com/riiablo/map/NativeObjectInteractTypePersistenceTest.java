package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.riiablo.engine.server.component.NativeObjectState;

class NativeObjectInteractTypePersistenceTest {
  @Test
  void retainsNativeByteAcrossEntityStateRecreation() {
    Map.NativeObject source = new Map.NativeObject(1, 0, 10, 20);
    NativeObjectState first = state(source);
    first.persistInteractType(0x188);

    NativeObjectState recreated = state(source);
    assertEquals(0x88, recreated.interactType);
    assertEquals(0x88, source.interactType());
  }

  private static NativeObjectState state(Map.NativeObject source) {
    return new NativeObjectState().set(source, source.presetIndex,
        source.presetIndex, source.presetIndex, source.mode,
        source.ds1Raw, source.spawned, NativePresetObjectResolver.Kind.ORDINARY);
  }
}
