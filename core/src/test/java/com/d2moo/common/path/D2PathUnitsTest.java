package com.d2moo.common.path;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import org.junit.jupiter.api.Test;

import static com.d2moo.common.path.D2PathContinuation.Decision.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class D2PathUnitsTest {
  private static D2Path.Point p(int x, int y) { return new D2Path.Point(x, y); }
  private static D2PathUnits.Unit dynamic(int type, int size, int x, int y, int direction) {
    return D2PathUnits.Unit.dynamic(type, size, new D2PathUnits.DynamicPath(
        D2PathStep.toFp16Center(x), D2PathStep.toFp16Center(y), direction));
  }
  private static D2PathUnits.Prepared prepare(D2PathUnits.Unit target, int speed) {
    return D2PathUnits.prepare(p(10, 10), p(30, 30), target, speed);
  }

  @Test void dynamicCoordinatesUseUnsignedHighWordsNotRoundedOrSignedCoordinates() {
    D2PathUnits.Unit unit = D2PathUnits.Unit.dynamic(1, 2,
        new D2PathUnits.DynamicPath(0xffff0001, 0x8000ffff, 255));
    assertEquals(p(65535, 32768), D2PathUnits.coordinates(unit));
    assertEquals(p(65535, 32768), D2PathUnits.sample(unit, 0));
  }

  @Test void dynamicTypesIncludeMissilesAndNullPathCoordinatesAndDirectionAreZero() {
    for (int type : new int[] {0, 1, 3}) {
      D2PathUnits.Unit unit = D2PathUnits.Unit.dynamic(type, 2, null);
      assertEquals(p(0, 0), D2PathUnits.coordinates(unit));
      assertEquals(p(0, 0), D2PathUnits.sample(unit, 0));
      assertEquals(p(2, 0), D2PathUnits.sample(unit, 2));
    }
  }

  @Test void staticCoordinatesStayInt32UntilSamplingNarrowsThem() {
    for (int type : new int[] {2, 4, 5}) {
      D2PathUnits.Unit unit = D2PathUnits.Unit.stationary(type, 1, p(65538, -1));
      assertEquals(p(65538, -1), D2PathUnits.coordinates(unit));
      assertEquals(p(2, 65535), D2PathUnits.sample(unit, 0));
    }
  }

  @Test void speedParityRotatesLeadByHalfATurnRatherThanScalingVelocity() {
    assertEquals(p(99, 100), D2PathUnits.lead(p(100, 100), 1, 0));
    assertEquals(p(102, 100), D2PathUnits.lead(p(100, 100), 2, 0));
    assertEquals(p(100, 99), D2PathUnits.lead(p(100, 100), 1, 16));
    assertEquals(p(100, 102), D2PathUnits.lead(p(100, 100), 2, 16));
    assertEquals(p(101, 100), D2PathUnits.lead(p(100, 100), 1, 32));
    assertEquals(p(100, 101), D2PathUnits.lead(p(100, 100), 1, 48));
  }

  @Test void diagonalProductsTruncateTowardZeroAndDoNotRoundOrFloor() {
    assertEquals(p(100, 100), D2PathUnits.lead(p(100, 100), 1, 8));
    assertEquals(p(101, 101), D2PathUnits.lead(p(100, 100), 2, 8));
    assertEquals(p(98, 98), D2PathUnits.lead(p(100, 100), 3, 8));
    assertEquals(p(102, 102), D2PathUnits.lead(p(100, 100), 4, 8));
  }

  @Test void leadWritesUint16AndAcceptsTheFullUnsignedSpeedAndDirectionBytes() {
    assertEquals(p(65535, 0), D2PathUnits.lead(p(0, 0), 1, 0));
    assertEquals(p(1, 65535), D2PathUnits.lead(p(65535, 65535), 2, 0));
    assertEquals(p(65281, 0), D2PathUnits.lead(p(0, 0), 255, 0));
    assertEquals(D2PathUnits.lead(p(100, 100), 255, 63),
        D2PathUnits.lead(p(100, 100), 255, 255));
    assertEquals(p(2, 65535), D2PathUnits.lead(p(65538, -1), 0, 0));
  }

  @Test void exactFogBitsAreNotMirroredOrRegeneratedIdealSine() {
    assertEquals(0x3e47c5c2, Float.floatToRawIntBits(D2PathUnits.sineAtPathAngle(2)));
    assertEquals(0x3e47c5bc, Float.floatToRawIntBits(D2PathUnits.sineAtPathAngle(30)));
    assertEquals(0xb3bbbd2e, Float.floatToRawIntBits(D2PathUnits.sineAtPathAngle(32)));
    assertEquals(0xbf61c596, Float.floatToRawIntBits(D2PathUnits.sineAtPathAngle(53)));
    assertEquals(D2PathUnits.sineAtPathAngle(2), D2PathUnits.sineAtPathAngle(66));
  }

  @Test void noUnitKeepsCoordinateTargetAndUsesToleranceOne() {
    D2PathUnits.Prepared r = prepare(null, 255);
    assertEquals(p(30, 30), r.target);
    assertEquals(1, r.tolerance);
  }

  @Test void playerAndMonsterPrepareCurrentCoordinatesAndLeadWhenSpeedIsNonzero() {
    for (int type : new int[] {0, 1}) {
      D2PathUnits.Unit target = dynamic(type, 2, 20, 30, 0);
      assertEquals(p(20, 30), prepare(target, 0).target);
      assertEquals(p(19, 30), prepare(target, 1).target);
      assertEquals(p(22, 30), prepare(target, 2).target);
      assertEquals(1, prepare(target, 2).tolerance);
    }
  }

  @Test void itemAndNonDoorObjectHaveToleranceTwoAndIgnoreSpeedDuringPreparation() {
    for (int type : new int[] {2, 4}) {
      D2PathUnits.Unit target = D2PathUnits.Unit.stationary(type, 1, p(20, 30));
      assertEquals(p(20, 30), prepare(target, 255).target);
      assertEquals(2, prepare(target, 255).tolerance);
    }
  }

  @Test void missileAndTilePreparationDoesNotLeadAndUsesToleranceOne() {
    D2PathUnits.Unit missile = dynamic(3, 1, 20, 30, 0);
    D2PathUnits.Unit tile = D2PathUnits.Unit.stationary(5, 0, p(20, 30));
    assertEquals(p(20, 30), prepare(missile, 1).target);
    assertEquals(p(19, 30), D2PathUnits.sample(missile, 1));
    assertEquals(1, prepare(missile, 1).tolerance);
    assertEquals(p(20, 30), prepare(tile, 1).target);
    assertEquals(1, prepare(tile, 1).tolerance);
  }

  @Test void bothZeroCoordinatesReturnToleranceZeroBeforeLeadOrDoorAdjustment() {
    for (D2PathUnits.Unit target : new D2PathUnits.Unit[] {
        D2PathUnits.Unit.dynamic(1, 2, null),
        D2PathUnits.Unit.stationary(4, 1, p(65536, 65536)),
        D2PathUnits.Unit.door(2, p(0, 0), true)}) {
      assertEquals(p(0, 0), prepare(target, 2).target);
      assertEquals(0, prepare(target, 2).tolerance);
    }
    assertEquals(p(2, 0), D2PathUnits.sample(D2PathUnits.Unit.dynamic(1, 2, null), 2),
        "sampling lacks preparation's zero-coordinate early exit");
  }

  @Test void thisPhaseDoesNotInventTheEarlierZeroAxisOrLaterCoincidenceGates() {
    D2PathUnits.Unit oneZero = dynamic(1, 2, 0, 10, 0);
    assertEquals(p(0, 10), prepare(oneZero, 0).target);
    assertEquals(1, prepare(oneZero, 0).tolerance);
    D2PathUnits.Unit same = dynamic(1, 2, 10, 10, 0);
    assertEquals(p(10, 10), prepare(same, 0).target);
    assertEquals(1, prepare(same, 0).tolerance);
  }

  @Test void doorsUseNativeOrientationSourceSideAndToleranceTwoWithoutLead() {
    assertEquals(p(18, 30), prepare(D2PathUnits.Unit.door(2, p(20, 30), false), 1).target);
    assertEquals(p(20, 28), prepare(D2PathUnits.Unit.door(2, p(20, 30), true), 1).target);
    D2PathUnits.Unit door = D2PathUnits.Unit.door(2, p(10, 10), true);
    assertEquals(p(10, 12), prepare(door, 1).target);
    assertEquals(2, prepare(door, 1).tolerance);
  }

  @Test void samplingStaticLeadRequiresExplicitAliasedDirectionInsteadOfAssumingZero() {
    for (int type : new int[] {2, 4, 5}) {
      D2PathUnits.Unit missing = D2PathUnits.Unit.stationary(type, 1, p(20, 30));
      assertThrows(IllegalArgumentException.class, () -> D2PathUnits.sample(missing, 1));
      D2PathUnits.Unit explicit = D2PathUnits.Unit.stationary(type, 1, p(20, 30), 16);
      assertEquals(p(20, 29), D2PathUnits.sample(explicit, 1));
    }
    assertEquals(p(20, 29), D2PathUnits.sample(D2PathUnits.Unit.door(2, p(20, 30), true, 16), 1));
  }

  @Test void shortDistanceNegativeSentinelsReturnZeroWithoutSmallSizeIncrement() {
    for (int firstSize = 0; firstSize <= 3; firstSize++) {
      for (int secondSize = 0; secondSize <= 3; secondSize++) {
        assertEquals(0, D2PathUnits.distance(p(10, 10), firstSize, p(10, 10), secondSize));
        assertEquals(0, D2PathUnits.distance(p(10, 10), firstSize, p(11, 10), secondSize));
      }
    }
  }

  @Test void shortDistanceSizeThreeSubtractsOnlyOnceThenSmallUnitAddsOnlyOnce() {
    assertEquals(2, D2PathUnits.distance(p(0, 0), 2, p(3, 2), 2));
    assertEquals(1, D2PathUnits.distance(p(0, 0), 3, p(3, 2), 3));
    assertEquals(3, D2PathUnits.distance(p(0, 0), 1, p(3, 2), 1));
    assertEquals(2, D2PathUnits.distance(p(0, 0), 3, p(3, 2), 1));
    assertEquals(0, D2PathUnits.distance(p(0, 0), 3, p(2, 1), 2));
    assertEquals(1, D2PathUnits.distance(p(0, 0), 3, p(2, 1), 1));
  }

  @Test void distanceEightAndSizeFourUseRadiusSubtractionBeforeTheLongMetric() {
    assertEquals(8, D2PathUnits.distance(p(0, 0), 2, p(7, 0), 2));
    assertEquals(12, D2PathUnits.distance(p(0, 0), 2, p(8, 0), 2));
    assertEquals(10, D2PathUnits.distance(p(0, 0), 4, p(8, 0), 2));
    assertEquals(0, D2PathUnits.distance(p(0, 0), 4, p(2, 1), 2));
    assertEquals(12, D2PathUnits.distance(p(0, 0), 3, p(8, 0), 3));
    assertEquals(8, D2PathUnits.distance(p(0, 0), 5, p(8, 0), 5));
  }

  @Test void distanceUsesRawStaticInt32CoordinatesNotNarrowedSamplePoints() {
    D2PathUnits.Unit first = D2PathUnits.Unit.stationary(4, 1, p(65536, 10));
    D2PathUnits.Unit second = dynamic(0, 2, 0, 10, 0);
    assertEquals(131070, D2PathUnits.distance(first, second));
    assertEquals(p(0, 10), D2PathUnits.sample(first, 0));
  }

  @Test void longDistanceRetainsNativeInt32ResultWrappingNotSaturation() {
    assertEquals(-2, D2PathUnits.distance(p(0, 0), 0, p(Integer.MAX_VALUE, 0), 0));
    assertThrows(IllegalArgumentException.class,
        () -> D2PathUnits.distance(p(Integer.MIN_VALUE, 0), 0, p(0, 0), 0));
  }

  @Test void distanceIsSymmetricAcrossSignsAxesAndResolvedSizeOrder() {
    for (int dx = 0; dx <= 10; dx++) {
      for (int dy = 0; dy <= 10; dy++) {
        for (int firstSize = 0; firstSize <= 6; firstSize++) {
          for (int secondSize = 0; secondSize <= 6; secondSize++) {
            int result = D2PathUnits.distance(p(20, 20), firstSize, p(20 + dx, 20 + dy), secondSize);
            assertEquals(result, D2PathUnits.distance(p(20 + dx, 20 + dy), secondSize, p(20, 20), firstSize));
            assertEquals(result, D2PathUnits.distance(p(20, 20), firstSize, p(20 - dy, 20 - dx), secondSize));
          }
        }
      }
    }
  }

  @Test void continuationObservationUsesRealDistanceBeforeAttemptingStaticLead() {
    D2PathContinuation.State state = new D2PathContinuation.State(0);
    D2PathUnits.Unit owner = dynamic(0, 2, 10, 10, 0);
    D2PathUnits.Unit target = D2PathUnits.Unit.stationary(2, 4, p(11, 10));
    state.targetUnit = D2PathUnits.observation(owner, target, 1);
    assertEquals(STOP, D2PathContinuation.decide(state), "range stops before unavailable static direction");
  }

  @Test void continuationObservationUsesLedPointForTheStrictFiveDisplacementGate() {
    D2PathContinuation.State state = new D2PathContinuation.State(0);
    state.currentPoint = p(10, 10);
    state.finalTargetPoint = p(100, 10);
    state.currentPointIndex = 0;
    state.pathPointCount = 1;
    state.previousTargetPoint = p(20, 10);
    D2PathUnits.Unit owner = dynamic(0, 2, 10, 10, 0);
    D2PathUnits.Unit target = dynamic(1, 2, 25, 10, 0);
    state.targetUnit = D2PathUnits.observation(owner, target, 0);
    assertEquals(KEEP, D2PathContinuation.decide(state));
    state.targetUnit = D2PathUnits.observation(owner, target, 2);
    assertEquals(REPLAN_UNIT, D2PathContinuation.decide(state));
    assertEquals(p(20, 10), state.previousTargetPoint);
  }

  @Test void preparedUnitTargetComposesWithBlockedTargetPreparationAndTowardKernel() {
    D2PathUnits.Prepared unit = prepare(dynamic(1, 2, 20, 10, 0), 2);
    D2PathTarget.Result search = D2PathTarget.prepare(p(10, 10), unit.target, 0x1000, false,
        (x, y) -> x == 22 && y == 10);
    assertEquals(p(21, 10), search.target);
    assertTrue(search.canSearch);
    D2Toward.Result path = D2Toward.compute(p(10, 10), search.target, 77, unit.tolerance, 0, (x, y) -> false);
    assertEquals(p(21, 10), path.points().get(path.points().size() - 1));
    assertEquals(p(22, 10), unit.target, "search candidate must not overwrite unit target snapshot");
  }

  @Test void invalidUnitLayoutsAndBytesAreRejectedInsteadOfReinterpreted() {
    assertThrows(IllegalArgumentException.class, () -> D2PathUnits.Unit.dynamic(2, 2, null));
    assertThrows(IllegalArgumentException.class, () -> D2PathUnits.Unit.dynamic(6, 2, null));
    assertThrows(IllegalArgumentException.class, () -> D2PathUnits.Unit.stationary(1, 2, p(0, 0)));
    assertThrows(IllegalArgumentException.class, () -> D2PathUnits.Unit.stationary(4, -1, p(0, 0)));
    for (int value : new int[] {-1, 256, Integer.MAX_VALUE}) {
      assertThrows(IllegalArgumentException.class, () -> new D2PathUnits.DynamicPath(0, 0, value));
      assertThrows(IllegalArgumentException.class, () -> D2PathUnits.lead(p(0, 0), value, 0));
      assertThrows(IllegalArgumentException.class, () -> D2PathUnits.lead(p(0, 0), 0, value));
      assertThrows(IllegalArgumentException.class, () -> prepare(null, value));
    }
    assertThrows(IllegalArgumentException.class,
        () -> D2PathUnits.prepare(p(-1, 10), p(10, 10), null, 0));
    assertThrows(IllegalArgumentException.class,
        () -> D2PathUnits.distance(p(0, 0), -1, p(0, 0), 1));
  }

  @Test void installed110fFogBitsAndAllByteDirectionSpeedPairsMatchTheStaticDataOracle() throws Exception {
    ByteBuffer fog = originalDll("Fog.dll", "C4F377CC056E0A5E465D4118D18F3D98E9E650496CF0B5AFEA559057793B0DBA");
    int lut = fileOffset(fog, 0x6ff742a8L);
    for (int i = 0; i < 64; i++) {
      assertEquals(fog.getInt(lut + i * 32), Float.floatToRawIntBits(D2PathUnits.sineAtPathAngle(i)));
    }
    for (int direction = 0; direction <= 255; direction++) {
      for (int speed = 0; speed <= 255; speed++) {
        int angle512 = (8 * direction - (speed << 8)) & 511;
        double cosine = fog.getFloat(lut + ((angle512 + 128) & 511) * 4);
        double sine = fog.getFloat(lut + angle512 * 4);
        D2Path.Point source = p(direction % 2 == 0 ? 0 : 65535, speed % 2 == 0 ? 0 : 65535);
        D2Path.Point expected = p((source.x + (int) (cosine * speed)) & 65535,
            (source.y + (int) (sine * speed)) & 65535);
        assertEquals(expected, D2PathUnits.lead(source, speed, direction));
      }
    }
  }

  @Test void installed110fCommonShortDistanceDataAndSizeBranchesMatchTheStaticDataOracle() throws Exception {
    ByteBuffer common = originalDll("D2Common.dll", "B9D1A27C6A35992932C7C47438D02E5269F878E899F6E614D507902CECD1BEAB");
    int table = fileOffset(common, 0x6fdd3200L);
    for (int dx = 0; dx <= 10; dx++) {
      for (int dy = 0; dy <= 10; dy++) {
        for (int firstSize = 0; firstSize <= 7; firstSize++) {
          for (int secondSize = 0; secondSize <= 7; secondSize++) {
            int expected;
            if (dx >= 8 || dy >= 8 || firstSize >= 4 || secondSize >= 4) {
              int radius = (firstSize >>> 1) + (secondSize >>> 1);
              int x = Math.max(0, dx - radius), y = Math.max(0, dy - radius);
              expected = Math.min(x, y) + 2 * Math.max(x, y);
            } else {
              int raw = common.getInt(table + (dx + 8 * dy) * 4);
              expected = raw < 0 ? 0 : Math.max(0, raw - (firstSize == 3 || secondSize == 3 ? 1 : 0))
                  + (firstSize <= 1 || secondSize <= 1 ? 1 : 0);
            }
            assertEquals(expected, D2PathUnits.distance(p(10, 10), firstSize, p(10 + dx, 10 + dy), secondSize));
          }
        }
      }
    }
  }

  /** Optional licensed local inputs; never check binaries/resources into the repository. */
  private static ByteBuffer originalDll(String name, String expectedSha256) throws Exception {
    String home = System.getenv("D2_HOME");
    assumeTrue(home != null, "D2_HOME required for optional original 1.10f static-data verification");
    Path path = Paths.get(home, name);
    assumeTrue(Files.isRegularFile(path), "original " + name + " not installed");
    byte[] bytes = Files.readAllBytes(path);
    StringBuilder hash = new StringBuilder();
    for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) hash.append(String.format("%02X", value & 255));
    assumeTrue(expectedSha256.equals(hash.toString()), "static-data fixture requires the pinned 1.10f DLL hash");
    return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
  }

  /** Resolve original VA through PE32 section headers rather than assuming file offset == RVA. */
  private static int fileOffset(ByteBuffer pe, long address) {
    assertEquals(0x5a4d, pe.getShort(0) & 65535);
    int header = pe.getInt(0x3c);
    assertEquals(0x4550, pe.getInt(header));
    assertEquals(0x10b, pe.getShort(header + 24) & 65535);
    long rva = address - Integer.toUnsignedLong(pe.getInt(header + 24 + 28));
    int sections = pe.getShort(header + 6) & 65535;
    int section = header + 24 + (pe.getShort(header + 20) & 65535);
    for (int i = 0; i < sections; i++, section += 40) {
      long start = Integer.toUnsignedLong(pe.getInt(section + 12));
      long length = Integer.toUnsignedLong(pe.getInt(section + 16));
      if (rva >= start && rva < start + length) {
        return Math.toIntExact(Integer.toUnsignedLong(pe.getInt(section + 20)) + rva - start);
      }
    }
    throw new AssertionError("VA not backed by a PE file section");
  }
}
