package com.riiablo.attributes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ItemStatProjectionTest {
  @Test
  void combinesItemAggregatesIntoStableReadOnlyValues() {
    ItemStatProjection projection = ItemStatProjection.fromValues(
        new short[]{Stat.armorclass, Stat.strength, Stat.mindamage},
        new long[]{25, 17, 4});
    assertEquals(17, projection.get(Stat.strength, 0));
    assertEquals(4, projection.get(Stat.mindamage, 0));
    assertEquals(25, projection.get(Stat.armorclass, 0));
    assertEquals(0, projection.get(Stat.dexterity, 0));
    assertEquals(3, projection.size());
  }

  @Test
  void absentAndNullSourcesDoNotInventStats() {
    ItemStatProjection projection = ItemStatProjection.from(null, Attributes.obtainStandard());
    assertEquals(-3, projection.get(Stat.firemindam, -3));
    assertEquals(0, projection.size());
  }
}
