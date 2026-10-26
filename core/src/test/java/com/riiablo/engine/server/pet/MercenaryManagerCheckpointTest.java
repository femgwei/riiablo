package com.riiablo.engine.server.pet;

import com.riiablo.engine.server.NativeHirelingExperienceTable;
import com.riiablo.engine.server.pet.MercenaryManager.*;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MercenaryManagerCheckpointTest {
  private static MercenaryManager manager() {
    MercenaryManager m = new MercenaryManager(); m.enableCheckpointRuntime(12345);
    MercenaryCallback c = mock(MercenaryCallback.class);
    when(c.getPlayerGold(anyInt())).thenReturn(100000); when(c.deductPlayerGold(anyInt(), anyInt())).thenReturn(true);
    when(c.getPlayerLevel(anyInt())).thenReturn(98); when(c.createMercenaryEntity(anyInt(), any(), anyInt(), anyInt(), anyInt())).thenReturn(10);
    m.setCallback(c); return m;
  }
  private static byte[] bytes(MercenaryManager m) { return MercenaryManagerCheckpoint.capture(m).bytes(); }

  @Test void fullGraphRoundTripsWithExistingRecordsArraysDefinitionsAndCallbackIdentity() throws Exception {
    MercenaryManager a = manager(), b = manager();
    for (MercenaryManager m : new MercenaryManager[] {a, b}) {
      m.getAvailableMercenaries(150, 20); m.getAvailableMercenaries(4, 198, 30);
      assertTrue(m.hireMercenary(2, 150, 0));
    }
    ActiveMercenary active = b.getPlayerMercenary(2); int[] equipment = active.equipment;
    MercenaryDefinition definition = b.mercDefinitions.get(0).first(); int[] resistances = definition.baseResistances;
    NpcMercenaryList list = b.npcLists.get(150); Object available = list.available; AvailableMercenary offer = list.available.first();
    MercenaryCallback callback = b.checkpointCallback(); clearInvocations(a.checkpointCallback(), callback);
    a.getPlayerMercenary(2).experience = 123456789; a.getPlayerMercenary(2).currentLife = 17;
    a.advanceRuntimeMillis(300); a.rosterDifficulty = 2;
    a.mercDefinitions.get(0).first().skills = new int[] {1, -1, 37};
    MercenaryManagerCheckpoint image = MercenaryManagerCheckpoint.capture(a); image.restore(b);
    assertSame(active, b.getPlayerMercenary(2)); assertSame(equipment, active.equipment);
    assertSame(definition, b.mercDefinitions.get(0).first()); assertSame(resistances, definition.baseResistances);
    assertSame(list, b.npcLists.get(150)); assertSame(available, list.available); assertSame(offer, list.available.first());
    assertSame(callback, b.checkpointCallback()); assertArrayEquals(image.bytes(), bytes(b));
    assertSame(b.getPlayerMercenary(2).definition, b.npcLists.get(150).available.first().definition);
    verifyNoInteractions(a.checkpointCallback(), callback);
  }

  @Test void allDefinitionScalarsAndArraysHaveExplicitRoundTripPolicy() throws Exception {
    MercenaryManager a = manager(), b = manager(); MercenaryDefinition source = a.mercDefinitions.get(0).first();
    int value = -100;
    for (Field f : MercenaryDefinition.class.getDeclaredFields()) if (!Modifier.isStatic(f.getModifiers())) {
      if (f.getType() == int.class) f.setInt(source, value++);
    }
    source.hirelingId = -1; source.mercType = 0; source.description = "佣兵\u0000identity";
    source.skills = new int[] {Integer.MIN_VALUE, 0, Integer.MAX_VALUE}; source.baseResistances = null;
    MercenaryManagerCheckpoint.capture(a).restore(b); MercenaryDefinition target = b.mercDefinitions.get(0).first();
    for (Field f : MercenaryDefinition.class.getDeclaredFields()) if (!Modifier.isStatic(f.getModifiers())) {
      if (f.getType() == int[].class) assertArrayEquals((int[]) f.get(source), (int[]) f.get(target), f.getName());
      else assertEquals(f.get(source), f.get(target), f.getName());
    }
    assertArrayEquals(bytes(a), bytes(b));
  }

  @Test void clockExpiryAndFutureRostersContinueWithoutGlobalRandomOrWallTime() {
    MercenaryManager a = manager(), b = manager(), other = manager();
    a.getAvailableMercenaries(1, 150, 20); a.getAvailableMercenaries(150, 20);
    a.advanceRuntimeMillis(599960); MercenaryManagerCheckpoint.capture(a).restore(b);
    int seed = a.getAvailableMercenaries(1, 150, 20).first().seed;
    for (int tick = 0; tick < 5; tick++) {
      other.getAvailableMercenaries(3 + tick, 198, 30); // extra draws in a different manager cannot perturb either continuation
      a.advanceRuntimeMillis(40); b.advanceRuntimeMillis(40);
      a.getAvailableMercenaries(1, 150, 20); b.getAvailableMercenaries(1, 150, 20);
      a.getAvailableMercenaries(150, 20); b.getAvailableMercenaries(150, 20);
      assertArrayEquals(bytes(a), bytes(b));
      if (tick == 0) assertEquals(seed, b.getAvailableMercenaries(1, 150, 20).first().seed);
      if (tick == 1) assertNotEquals(seed, b.getAvailableMercenaries(1, 150, 20).first().seed);
    }
    assertNotEquals(a.rosterRng.drawCount(), other.rosterRng.drawCount());
  }

  @Test void deadRecordWithoutCorpseAndAbsenceRemoveRecordsWithoutCallbacks() {
    MercenaryManager a = manager(), b = manager();
    assertTrue(a.restoreMercenary(4, 0, 25, 1234, 42, 3, true));
    assertTrue(b.restoreMercenary(2, 0, 5, 111, 22, 1, false));
    clearInvocations(a.checkpointCallback(), b.checkpointCallback());
    MercenaryManagerCheckpoint.capture(a).restore(b);
    assertFalse(b.hasMercenary(2)); assertEquals(-1, b.getPlayerMercenary(4).entityId);
    assertEquals(MercenaryManager.STATE_DEAD, b.getPlayerMercenary(4).state);
    verifyNoInteractions(a.checkpointCallback(), b.checkpointCallback());
    MercenaryManagerCheckpoint.capture(manager()).restore(b); assertFalse(b.hasMercenary(4));
  }

  @Test void selectedOfferHiredFlagAndStaleSeedContinueAfterRestore() {
    MercenaryManager a = manager(), b = manager();
    AvailableMercenary offer = a.getAvailableMercenaries(2, 198, 20).first();
    MercenaryManagerCheckpoint.capture(a).restore(b);
    assertFalse(b.hireSelectedMercenary(2, 198, 0, offer.seed ^ 1));
    assertTrue(a.hireSelectedMercenary(2, 198, 0, offer.seed));
    assertTrue(b.hireSelectedMercenary(2, 198, 0, offer.seed));
    assertFalse(b.hireSelectedMercenary(2, 198, 0, offer.seed));
    assertArrayEquals(bytes(a), bytes(b));
    verify(b.checkpointCallback(), times(1)).deductPlayerGold(eq(2), anyInt());
  }

  @Test void dataMismatchAndUncoveredEquipmentRejectBeforeChangingClocksOrRecords() {
    MercenaryManager a = manager(), b = manager(); assertTrue(b.restoreMercenary(2, 0, 5, 111, 22, 1, false));
    MercenaryManagerCheckpoint image = MercenaryManagerCheckpoint.capture(a);
    b.expTable[98]++; byte[] before = bytes(b);
    assertThrows(IllegalStateException.class, () -> image.restore(b)); assertArrayEquals(before, bytes(b));
    b.expTable[98]--; before = bytes(b);
    b.getPlayerMercenary(2).equipment[1] = 123;
    assertThrows(IllegalStateException.class, () -> image.restore(b));
    assertThrows(IllegalStateException.class, () -> bytes(b)); b.getPlayerMercenary(2).equipment[1] = 0;
    assertArrayEquals(before, bytes(b)); assertThrows(IllegalStateException.class, () -> bytes(new MercenaryManager()));
  }

  @Test void exactNativeRowDigestRejectsOfferOrChanceChangesWithoutMutatingTarget() {
    MercenaryManager a = manager(), b = manager();
    NativeHirelingExperienceTable.Row row = new NativeHirelingExperienceTable.Row(0, 1, 100);
    a.setNativeExperienceTable(new NativeHirelingExperienceTable().add(new NativeHirelingExperienceTable.Row(0, 1, 100)));
    b.setNativeExperienceTable(new NativeHirelingExperienceTable().add(row));
    MercenaryManagerCheckpoint image = MercenaryManagerCheckpoint.capture(a); image.restore(b);
    for (int change = 0; change < 2; change++) {
      if (change == 0) row.chances[0]++; else row.description = "changed";
      byte[] before = bytes(b); assertThrows(IllegalStateException.class, () -> image.restore(b)); assertArrayEquals(before, bytes(b));
    }
  }

  @Test void malformedImagesRejectEveryTruncationTrailingDataCountsAndNegativeClocks() {
    MercenaryManager m = manager(); byte[] bytes = bytes(m);
    for (int n = 0; n < bytes.length; n++) {
      byte[] truncated = Arrays.copyOf(bytes, n); assertThrows(IllegalArgumentException.class, () -> MercenaryManagerCheckpoint.fromBytes(truncated));
    }
    assertThrows(IllegalArgumentException.class, () -> MercenaryManagerCheckpoint.fromBytes(Arrays.copyOf(bytes, bytes.length + 1)));
    for (int[] change : new int[][] {{0, 9}, {12, 3}, {821, Integer.MAX_VALUE}, {837, 4}}) {
      byte[] bad = bytes.clone(); ByteBuffer.wrap(bad).putInt(change[0], change[1]);
      assertThrows(IllegalArgumentException.class, () -> MercenaryManagerCheckpoint.fromBytes(bad));
    }
    for (int offset : new int[] {4, 20}) {
      byte[] bad = bytes.clone(); ByteBuffer.wrap(bad).putLong(offset, -1L);
      assertThrows(IllegalArgumentException.class, () -> MercenaryManagerCheckpoint.fromBytes(bad));
    }
    byte[] bool = bytes.clone(); bool[28] = 2; assertThrows(IllegalArgumentException.class, () -> MercenaryManagerCheckpoint.fromBytes(bool));
    MercenaryManagerCheckpoint image = MercenaryManagerCheckpoint.fromBytes(bytes); bytes[3] = 99;
    byte[] owned = image.bytes(); owned[3] = 99; assertNotEquals(99, image.bytes()[3]);
  }

  @Test void zeroRngStateAndOverflowChecksDoNotReseedOrWrap() {
    MercenaryManager a = manager(), b = manager(); a.rosterRng.restore(0, 37);
    MercenaryManagerCheckpoint.capture(a).restore(b); assertEquals(0, b.rosterRng.state()); assertEquals(37, b.rosterRng.drawCount());
    b.runtimeMillis = Long.MAX_VALUE;
    assertThrows(IllegalArgumentException.class, () -> b.advanceRuntimeMillis(1)); assertEquals(Long.MAX_VALUE, b.runtimeMillis);
    assertThrows(IllegalArgumentException.class, () -> b.advanceRuntimeMillis(-1));
    b.rosterRng.restore(0, Long.MAX_VALUE);
    assertThrows(IllegalStateException.class, () -> b.getAvailableMercenaries(150, 20)); assertEquals(0, b.rosterRng.state());
    assertEquals(Long.MAX_VALUE, b.rosterRng.drawCount());
  }

  @Test void managerAndNestedRuntimeFieldsHaveAnExplicitCodecOrOwnerPolicy() {
    assertFields(MercenaryManager.class, "mercDefinitions npcLists playerLists playerMercs expTable nativeExperienceTable rosterDifficulty runtimeMillis rosterRng callback");
    assertFields(ActiveMercenary.class, "ownerId entityId definition nameId seed state level experience currentLife maxLife equipment hireTime");
    assertFields(AvailableMercenary.class, "definition seed nameId hired level");
    assertFields(NpcMercenaryList.class, "npcId mercType available lastRefreshTime");
    assertFields(MercenaryDefinition.class, "hirelingId nativeGold nativeLevel description mercType nameId skills baseStrength baseDexterity baseLife baseDefense baseAttackRating baseDamageMin baseDamageMax strengthPerLevel dexterityPerLevel lifePerLevel defensePerLevel attackRatingPerLevel damageMinPerLevel damageMaxPerLevel baseResistances hireCostMultiplier resurrectCostMultiplier");
    assertFields(NativeHirelingExperienceTable.Row.class, "hirelingId level experiencePerLevel hitpoints hitpointsPerLevel defense defensePerLevel strength strengthPerLevel dexterity dexterityPerLevel attackRate attackRatePerLevel damageMin damageMax damagePerLevel resist resistPerLevel defaultChance chances chancePerLevels skills skillModes skillLevels skillLevelsPerLevel seller difficulty gold nameCount description nameFirst");
  }
  private static void assertFields(Class<?> type, String expected) {
    Set<String> actual = new TreeSet<>(); for (Field f : type.getDeclaredFields()) if (!Modifier.isStatic(f.getModifiers()) && !f.isSynthetic()) actual.add(f.getName());
    assertEquals(new TreeSet<>(Arrays.asList(expected.split(" "))), actual, type.getName());
  }
}
