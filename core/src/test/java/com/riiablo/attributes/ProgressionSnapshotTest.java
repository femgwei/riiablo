package com.riiablo.attributes;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ProgressionSnapshotTest {
  @Test void capturesPlayerAndMercenaryState() {
    EquipmentSnapshot equipment = EquipmentSnapshot.commit(12,
        ItemStatProjection.fromValues(new short[] {1}, new long[] {3}),
        new short[0], new byte[0]);
    ProgressionSnapshot player = ProgressionSnapshot.commit(4, 9, 1200, 8, equipment, false);
    ProgressionSnapshot merc = ProgressionSnapshot.commit(5, 9, 400, 4, equipment, true);
    assertFalse(player.mercenary);
    assertTrue(merc.mercenary);
    assertEquals(12, player.equipmentRevision);
  }

  @Test void rejectsInvalidState() {
    EquipmentSnapshot equipment = EquipmentSnapshot.commit(0,
        ItemStatProjection.fromValues(new short[0], new long[0]),
        new short[0], new byte[0]);
    assertThrows(IllegalArgumentException.class,
        () -> ProgressionSnapshot.commit(1, 0, 0, 0, equipment, false));
  }
}
