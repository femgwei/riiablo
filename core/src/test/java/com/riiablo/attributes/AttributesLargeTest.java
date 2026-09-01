package com.riiablo.attributes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AttributesLargeTest {
  @Test
  void allocatesLargeCapacityForPersistentBaseStats() {
    Attributes attributes = Attributes.obtainLarge();

    assertEquals(StatList.MAX_SIZE, attributes.base().parent().maxSize());
    assertEquals(StatList.MAX_SIZE, attributes.aggregate().parent().maxSize());
  }
}
