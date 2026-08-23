package com.riiablo.save;

import com.riiablo.Riiablo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharDataWaypointTest {
  @Test
  void mapsGlobalWaypointNumbersToActLocalSaveBits() {
    assertEquals(0, CharData.getWaypointIndex(Riiablo.ACT1, 0));
    assertEquals(8, CharData.getWaypointIndex(Riiablo.ACT1, 8));
    assertEquals(0, CharData.getWaypointIndex(Riiablo.ACT2, 9));
    assertEquals(8, CharData.getWaypointIndex(Riiablo.ACT2, 17));
    assertEquals(0, CharData.getWaypointIndex(Riiablo.ACT3, 18));
    assertEquals(8, CharData.getWaypointIndex(Riiablo.ACT3, 26));
    assertEquals(0, CharData.getWaypointIndex(Riiablo.ACT4, 27));
    assertEquals(2, CharData.getWaypointIndex(Riiablo.ACT4, 29));
    assertEquals(0, CharData.getWaypointIndex(Riiablo.ACT5, 30));
    assertEquals(8, CharData.getWaypointIndex(Riiablo.ACT5, 38));
  }

  @Test
  void rejectsWaypointNumberFromAnotherAct() {
    assertThrows(IllegalArgumentException.class,
        () -> CharData.getWaypointIndex(Riiablo.ACT2, 8));
    assertThrows(IllegalArgumentException.class,
        () -> CharData.getWaypointIndex(Riiablo.ACT4, 30));
  }

  @Test
  void activationIsIdempotentAndStoredInTheCorrectAct() {
    CharData data = new CharData().set(Riiablo.NORMAL, false);

    assertTrue(data.activateWaypoint(Riiablo.ACT2, 9));
    assertFalse(data.activateWaypoint(Riiablo.ACT2, 9));
    assertTrue(data.isWaypointActivated(Riiablo.ACT2, 9));
    assertEquals(1, data.getWaypoints(Riiablo.ACT2));
    assertEquals(0, data.getWaypoints(Riiablo.ACT1));
  }

  @Test
  void activationIsIsolatedByDifficulty() {
    CharData data = new CharData().set(Riiablo.NORMAL, false);
    data.activateWaypoint(Riiablo.ACT1, 0);

    data.set(Riiablo.NIGHTMARE, false);
    assertFalse(data.isWaypointActivated(Riiablo.ACT1, 0));

    data.set(Riiablo.NORMAL, false);
    assertTrue(data.isWaypointActivated(Riiablo.ACT1, 0));
  }

  @Test
  void canInitializeTheDefaultTownWaypointForEveryDifficulty() {
    CharData data = new CharData().set(Riiablo.NORMAL, false);
    for (int difficulty = 0; difficulty < Riiablo.NUM_DIFFS; difficulty++) {
      assertTrue(data.activateWaypoint(difficulty, Riiablo.ACT1, 0));
    }

    for (int difficulty = 0; difficulty < Riiablo.NUM_DIFFS; difficulty++) {
      data.set(difficulty, false);
      assertTrue(data.isWaypointActivated(Riiablo.ACT1, 0));
    }
  }

  @Test
  void writerKeepsGlobalWaypointBitLayout() throws Exception {
    CharData data = new CharData().set(Riiablo.NORMAL, false);
    data.activateWaypoint(Riiablo.ACT1, 8);
    data.activateWaypoint(Riiablo.ACT2, 9);
    data.activateWaypoint(Riiablo.ACT4, 29);
    data.activateWaypoint(Riiablo.ACT5, 38);

    Method createWaypointData =
        D2SWriter96.class.getDeclaredMethod("createWaypointData", CharData.class);
    createWaypointData.setAccessible(true);
    D2S.WaypointData saved =
        (D2S.WaypointData) createWaypointData.invoke(null, data);

    byte[] flags = saved.flags[Riiablo.NORMAL];
    assertTrue(isSet(flags, 8));
    assertTrue(isSet(flags, 9));
    assertTrue(isSet(flags, 29));
    assertTrue(isSet(flags, 38));
  }

  private static boolean isSet(byte[] flags, int bit) {
    return (flags[bit / 8] & (1 << (bit % 8))) != 0;
  }
}
