package com.riiablo.attributes;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class EquipmentSnapshotTest {
  @Test void exposesStableRevisionAndSetCounts() {
    EquipmentSnapshot snapshot = EquipmentSnapshot.commit(8,
        ItemStatProjection.fromValues(new short[] {1}, new long[] {25}),
        new short[] {3, 9}, new byte[] {2, 4});
    assertEquals(8, snapshot.revision());
    assertEquals(2, snapshot.setCount((short) 3));
    assertEquals(4, snapshot.setCount((short) 9));
    assertEquals(0, snapshot.setCount((short) 4));
  }

  @Test void rejectsUnsortedSetIds() {
    assertThrows(IllegalArgumentException.class, () -> EquipmentSnapshot.commit(1,
        ItemStatProjection.fromValues(new short[] {1}, new long[] {1}),
        new short[] {9, 3}, new byte[] {1, 1}));
  }
}
