package com.riiablo.engine.server.object;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ObjectManagerDeterminismTest {
  @Test
  void sameSeedKeepsContainerTrapAndDropSequenceStable() {
    assertEquals(sequence(0x1A10F), sequence(0x1A10F));
  }

  @Test
  void differentSeedsUseIndependentContainerStreams() {
    assertNotEquals(sequence(1), sequence(2));
  }

  private static List<String> sequence(int seed) {
    ObjectManager manager = new ObjectManager(seed);
    List<String> result = new ArrayList<>();
    manager.setDropCallback((object, quality, count) ->
        result.add(object.objectId + ":" + object.trapped + ":" + object.trapDamage + ":" + count));
    for (int i = 0; i < 12; i++) {
      ObjectData chest = manager.createObject(ObjectType.CHEST, 0, 1, i, i);
      manager.interact(chest.objectId, 7);
    }
    return result;
  }
}
