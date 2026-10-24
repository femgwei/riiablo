package com.riiablo.attributes;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ProgressionSnapshotGateTest {
  @Test void rejectsStaleMixedRevisionSnapshots() {
    EquipmentSnapshot equipment = EquipmentSnapshot.commit(4,
        ItemStatProjection.fromValues(new short[0], new long[0]), new short[0], new byte[0]);
    ProgressionSnapshotGate gate = new ProgressionSnapshotGate();
    assertTrue(gate.accept(ProgressionSnapshot.commit(1, 3, 20, 2, equipment, false), 8));
    assertFalse(gate.accept(ProgressionSnapshot.commit(1, 2, 20, 2, equipment, false), 9));
    assertFalse(gate.accept(ProgressionSnapshot.commit(1, 4, 20, 2, equipment, false), 7));
    assertEquals(3, gate.revision());
  }
}
