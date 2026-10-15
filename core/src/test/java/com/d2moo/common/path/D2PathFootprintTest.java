package com.d2moo.common.path;

import com.d2moo.common.collision.D2Collision;
import com.d2moo.common.collision.D2CommonCollision;
import com.d2moo.common.drlg.D2ActiveRoom;
import com.d2moo.common.drlg.D2DrlgCoords;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class D2PathFootprintTest {
  @Test void runtimeSizePatternTableAndPetConversionAreExact() {
    int[] normal = {0, 1, 1, 2}, pet = {0, 3, 3, 4};
    for (int size = 0; size < 4; size++) {
      assertEquals(normal[size], D2PathFootprint.patternFromSize(size, false, true, false));
      assertEquals(normal[size], D2PathFootprint.patternFromSize(size, true, false, false));
      assertEquals(normal[size], D2PathFootprint.patternFromSize(size, true, true, true));
      assertEquals(pet[size], D2PathFootprint.patternFromSize(size, true, true, false));
    }
  }
  @Test void invalidSizeReturnsSmallUnitBeforePetConversion() {
    for (int size : new int[] {-1, 4, 255, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
      assertEquals(1, D2PathFootprint.patternFromSize(size, true, true, false));
    }
  }
  private static D2Path.Point p(int x, int y) { return new D2Path.Point(x, y); }
  private static D2ActiveRoom room(int x, int y, int width, int height) {
    D2ActiveRoom room = new D2ActiveRoom();
    D2DrlgCoords coords = new D2DrlgCoords();
    coords.setNSubtileX(x); coords.setNSubtileY(y);
    coords.setNSubtileWidth(width); coords.setNSubtileHeight(height);
    room.setCoords(coords);
    D2CommonCollision.allocRoomCollisionGrid(room);
    return room;
  }
  private static D2PathFootprint.Unit dynamic(int type, D2ActiveRoom room, int mode, int pattern, int mask) {
    return D2PathFootprint.Unit.dynamic(D2PathUnits.Unit.dynamic(type, 2,
        new D2PathUnits.DynamicPath(D2PathStep.toFp16Center(10), D2PathStep.toFp16Center(10), 0)), room, mode, pattern, mask);
  }
  private static D2PathFootprint.ObjectData objectData(int door, int vis, int missile, int subclass, int... collision) {
    int[] modes = new int[8];
    System.arraycopy(collision, 0, modes, 0, collision.length);
    return new D2PathFootprint.ObjectData(door, vis, missile, subclass, modes);
  }
  private static D2PathFootprint.Unit object(D2ActiveRoom room, int sx, int sy, int mode, D2PathFootprint.ObjectData data) {
    return D2PathFootprint.Unit.object(D2PathUnits.Unit.stationary(2, sx, p(10, 10)), room, mode, sy, data);
  }
  private static int flags(D2ActiveRoom room, int x, int y) {
    return D2CommonCollision.checkMask(room, x, y, 0xffff);
  }
  private static int[] cells(D2ActiveRoom room) { return room.getPCollisionGrid().getPCellsFlags().clone(); }

  @Test void playerPatternIgnoresSizeAndAddsTheNativeCenterPresenceMarker() {
    D2ActiveRoom room = room(0, 0, 24, 24);
    D2PathFootprint.Unit unit = dynamic(0, room, 1, 1, D2Collision.COLLIDE_PLAYER);
    D2PathFootprint.add(unit);
    for (int y = 7; y <= 13; y++) for (int x = 7; x <= 13; x++) {
      boolean cross = Math.abs(x - 10) + Math.abs(y - 10) <= 1;
      assertEquals((cross ? 0x80 : 0) | (x == 10 && y == 10 ? 0x1000 : 0), flags(room, x, y));
    }
    assertTrue(D2PathFootprint.remove(unit, false));
    assertTrue(Arrays.stream(cells(room)).allMatch(value -> value == 0));
  }

  @Test void monsterBigPatternWritesSquareMaskAndCrossNoPathNotASizeTwoCross() {
    D2ActiveRoom room = room(0, 0, 24, 24);
    D2PathFootprint.Unit unit = dynamic(1, room, 1, 2, D2Collision.COLLIDE_MONSTER);
    D2PathFootprint.add(unit);
    for (int y = 8; y <= 12; y++) for (int x = 8; x <= 12; x++) {
      boolean square = Math.abs(x - 10) <= 1 && Math.abs(y - 10) <= 1;
      boolean cross = Math.abs(x - 10) + Math.abs(y - 10) <= 1;
      assertEquals((square ? 0x100 : 0) | (cross ? 0x1000 : 0), flags(room, x, y));
    }
    assertTrue(D2PathFootprint.remove(unit, false));
    assertTrue(Arrays.stream(cells(room)).allMatch(value -> value == 0));
  }

  @Test void petAndNoPresencePatternsUseTheirOwnMarkers() {
    for (int pattern : new int[] {3, 4, 5}) {
      D2ActiveRoom room = room(0, 0, 24, 24);
      D2PathFootprint.Unit unit = dynamic(1, room, 1, pattern, 0x100);
      D2PathFootprint.add(unit);
      assertEquals(0x100 | (pattern == 5 ? 0 : 0x2000), flags(room, 10, 10));
      assertEquals(pattern == 4 ? 0x100 : 0, flags(room, 9, 9));
      assertEquals(0, flags(room, 10, 10) & 0x1000);
      assertTrue(D2PathFootprint.remove(unit, false));
      assertEquals(0, flags(room, 10, 10));
    }
  }

  @Test void footprintMaskNarrowsAtGridAbiBeforeTheNonzeroPresenceTest() {
    D2ActiveRoom room = room(0, 0, 24, 24);
    D2PathFootprint.Unit unit = dynamic(0, room, 1, 1, 0x12340000);
    assertEquals(0x12340000, D2PathFootprint.collisionMask(unit));
    D2PathFootprint.add(unit);
    assertEquals(0, flags(room, 10, 10), "high DWORD bits must not create a presence marker");
    D2CommonCollision.setMask(room, 10, 10, 0x1000);
    assertTrue(D2PathFootprint.remove(unit, false));
    assertEquals(0x1000, flags(room, 10, 10), "zero effective mask must not clear a presence marker");
    unit = dynamic(0, room, 1, 1, 0x12340080);
    D2PathFootprint.add(unit);
    assertEquals(0x1080, flags(room, 10, 10));
  }

  @Test void allDynamicModesUseTypeSpecificDeathGatesWithForceOverride() {
    for (int type : new int[] {0, 1}) for (int mode = 0; mode <= 20; mode++)
      for (boolean force : new boolean[] {false, true}) {
        D2ActiveRoom room = room(0, 0, 24, 24);
        D2PathFootprint.Unit unit = dynamic(type, room, mode, 1, 0x80);
        D2PathFootprint.add(unit);
        boolean eligible = force || mode != 0 && mode != (type == 0 ? 17 : 12);
        assertEquals(eligible, D2PathFootprint.remove(unit, force));
        assertEquals(eligible ? 0 : 0x1080, flags(room, 10, 10));
      }
  }

  @Test void addDoesNotGateDeadPlayersOrMonsters() {
    for (int type : new int[] {0, 1}) for (int mode : new int[] {0, type == 0 ? 17 : 12}) {
      D2ActiveRoom room = room(0, 0, 24, 24);
      D2PathFootprint.add(dynamic(type, room, mode, 1, 0x80));
      assertEquals(0x1080, flags(room, 10, 10));
    }
  }

  @Test void objectRectanglePreservesEvenDimensionBiasAndDoesNotAddPresence() {
    D2ActiveRoom room = room(0, 0, 24, 24);
    D2PathFootprint.Unit unit = object(room, 2, 4, 1, objectData(0, 0, 0, 0, 0, 1));
    D2PathFootprint.add(unit);
    for (int y = 7; y <= 13; y++) for (int x = 8; x <= 12; x++)
      assertEquals(x >= 9 && x <= 10 && y >= 8 && y <= 11 ? 0x400 : 0, flags(room, x, y));
    assertTrue(D2PathFootprint.remove(unit, false));
    assertTrue(Arrays.stream(cells(room)).allMatch(value -> value == 0));
  }

  @Test void objectModeCollisionBytesAreCopiedAndNonzeroIsNotRestrictedToOne() {
    int[] modes = {0, 0, 0, 0, 0, 0, 0, 255};
    D2PathFootprint.ObjectData data = new D2PathFootprint.ObjectData(0, 0, 0, 0, modes);
    modes[7] = 0;
    for (int mode = 0; mode < 8; mode++) {
      D2ActiveRoom room = room(0, 0, 24, 24);
      D2PathFootprint.Unit unit = object(room, 1, 1, mode, data);
      D2PathFootprint.add(unit);
      assertEquals(mode == 7, D2PathFootprint.hasCollision(unit));
      assertEquals(mode == 7, D2PathFootprint.remove(unit, false));
      assertEquals(mode == 7 ? 0 : 0x400, flags(room, 10, 10));
    }
  }

  @Test void collisionlessObjectsAreStillAddedAndForceRemovalIsAllowed() {
    D2ActiveRoom room = room(0, 0, 24, 24);
    D2PathFootprint.Unit unit = object(room, 1, 1, 2, objectData(0, 0, 0, 0));
    D2PathFootprint.add(unit);
    assertEquals(0x400, flags(room, 10, 10));
    assertFalse(D2PathFootprint.remove(unit, false));
    assertTrue(D2PathFootprint.remove(unit, true));
    assertEquals(0, flags(room, 10, 10));
  }

  @Test void objectMaskPriorityMatchesAllDoorVisibilityMissileAndPortalCombinations() {
    for (int door : new int[] {0, 255}) for (int vis : new int[] {0, 2})
      for (int missile : new int[] {0, 3}) for (int sub : new int[] {0, 4, 5, 8}) {
        int expected = door != 0 ? vis != 0 ? 0x806 : missile != 0 ? 0x804 : 0x400
            : (sub & 4) != 0 ? 0x8000 : missile != 0 ? 0x404 : 0x400;
        D2PathFootprint.Unit unit = object(null, 1, 1, 0, objectData(door, vis, missile, sub));
        assertEquals(expected, D2PathFootprint.collisionMask(unit));
      }
  }

  @Test void doorWithoutVisOrMissileBlockingUsesObjectNotDoorMask() {
    D2ActiveRoom room = room(0, 0, 24, 24);
    D2PathFootprint.Unit unit = object(room, 1, 1, 0, objectData(1, 0, 0, 0, 1));
    D2PathFootprint.add(unit);
    assertEquals(0x400, flags(room, 10, 10));
    assertEquals(0, flags(room, 10, 10) & 0x800);
  }

  @Test void itemAndTileUseSizeAndTheirFixedMasksNotPatternMarkers() {
    for (int type : new int[] {4, 5}) for (int size = 0; size <= 4; size++) {
      D2ActiveRoom room = room(0, 0, 24, 24);
      D2PathFootprint.Unit unit = D2PathFootprint.Unit.stationary(D2PathUnits.Unit.stationary(type, size, p(10, 10)), room);
      int mask = type == 4 ? 0x200 : 1;
      assertEquals(mask, D2PathFootprint.collisionMask(unit));
      assertTrue(D2PathFootprint.hasCollision(unit));
      D2PathFootprint.add(unit);
      for (int y = 8; y <= 12; y++) for (int x = 8; x <= 12; x++) {
        boolean inside = size == 1 ? x == 10 && y == 10
            : size == 2 ? Math.abs(x - 10) + Math.abs(y - 10) <= 1
            : size == 3 && Math.abs(x - 10) <= 1 && Math.abs(y - 10) <= 1;
        assertEquals(inside ? mask : 0, flags(room, x, y));
      }
      assertTrue(D2PathFootprint.remove(unit, false));
      assertTrue(Arrays.stream(cells(room)).allMatch(value -> value == 0));
    }
  }

  @Test void missileUsesDynamicMaskButSizeShapeAndHasNoDeathGate() {
    D2ActiveRoom room = room(0, 0, 24, 24);
    D2PathFootprint.Unit unit = dynamic(3, room, 0, 1, 0x40);
    D2PathFootprint.add(unit);
    assertEquals(0x40, flags(room, 10, 10));
    assertEquals(0x40, flags(room, 9, 10));
    assertEquals(0, flags(room, 9, 9));
    assertTrue(D2PathFootprint.remove(unit, false));
  }

  @Test void absentMissileDynamicPathUsesZeroCoordinatesAndNativeFFFFMaskNotZero() {
    D2ActiveRoom room = room(0, 0, 5, 5);
    D2PathFootprint.Unit unit = D2PathFootprint.Unit.dynamic(D2PathUnits.Unit.dynamic(3, 1, null), room, 0, 0, 0x40);
    assertEquals(0xffff, D2PathFootprint.collisionMask(unit));
    D2PathFootprint.add(unit);
    assertEquals(0xffff, flags(room, 0, 0));
    assertTrue(D2PathFootprint.remove(unit, false));
    assertEquals(0, flags(room, 0, 0));
  }

  @Test void absentPlayerPathIsNotInventedButDeadRemovalCanReturnBeforeReadingIt() {
    D2PathFootprint.Unit unit = D2PathFootprint.Unit.dynamic(D2PathUnits.Unit.dynamic(0, 2, null), null, 17, 1, 0x80);
    assertEquals(0xffff, D2PathFootprint.collisionMask(unit));
    assertFalse(D2PathFootprint.remove(unit, false));
    assertThrows(IllegalStateException.class, () -> D2PathFootprint.remove(unit, true));
    assertThrows(IllegalStateException.class, () -> D2PathFootprint.add(unit));
  }

  @Test void returnTrueDoesNotRequireAChangedGridWithMissingRoomGridOrUnsupportedSize() {
    D2PathFootprint.Unit missingRoom = dynamic(0, null, 1, 1, 0x80);
    assertTrue(D2PathFootprint.remove(missingRoom, false));
    D2PathFootprint.add(missingRoom);
    D2ActiveRoom room = room(0, 0, 24, 24);
    room.setPCollisionGrid(null);
    assertTrue(D2PathFootprint.remove(dynamic(1, room, 1, 1, 0x100), false));
    D2PathFootprint.add(dynamic(1, room, 1, 1, 0x100));
    D2PathFootprint.Unit sizeNone = D2PathFootprint.Unit.stationary(D2PathUnits.Unit.stationary(4, 0, p(10, 10)), room);
    assertTrue(D2PathFootprint.remove(sizeNone, false));
  }

  @Test void staticInt32CoordinatesAreNotNarrowedToPathWaypointsForGridWrites() {
    D2ActiveRoom room = room(65530, -10, 20, 20);
    D2PathFootprint.Unit unit = D2PathFootprint.Unit.stationary(D2PathUnits.Unit.stationary(4, 1, p(65540, -2)), room);
    D2PathFootprint.add(unit);
    assertEquals(0x200, flags(room, 65540, -2));
    assertTrue(D2PathFootprint.remove(unit, false));
    assertEquals(0, flags(room, 65540, -2));
  }

  @Test void dynamicUnsignedPrecisionHighWordsAreNotSignedOrRounded() {
    D2ActiveRoom room = room(65530, 32760, 6, 10);
    D2PathUnits.Unit coords = D2PathUnits.Unit.dynamic(3, 1,
        new D2PathUnits.DynamicPath(0xffffffff, 0x8000ffff, 0));
    D2PathFootprint.Unit unit = D2PathFootprint.Unit.dynamic(coords, room, 0, 0, 0x40);
    D2PathFootprint.add(unit);
    assertEquals(0x40, flags(room, 65535, 32768));
  }

  @Test void patternCrossKeepsExistingNearRoomPolicyAndCanTouchBothSidesOfASeam() {
    D2ActiveRoom a = room(0, 0, 11, 24), b = room(11, 0, 13, 24);
    a.setPpRoomList(new D2ActiveRoom[] {b}); a.setNNumRooms(1);
    b.setPpRoomList(new D2ActiveRoom[] {a}); b.setNNumRooms(1);
    D2PathFootprint.Unit unit = dynamic(0, a, 1, 1, 0x80);
    D2PathFootprint.add(unit);
    assertEquals(0x80, flags(b, 11, 10));
    assertEquals(0x1080, flags(a, 10, 10));
    assertTrue(D2PathFootprint.remove(unit, false));
    assertEquals(0, flags(b, 11, 10));
  }

  @Test void resetUsesNativeBitClearingNotReferenceCountsAndPreservesOtherMaskBits() {
    D2ActiveRoom room = room(0, 0, 24, 24);
    D2PathFootprint.Unit unit = dynamic(0, room, 1, 1, 0x80);
    D2PathFootprint.add(unit); D2PathFootprint.add(unit);
    D2CommonCollision.setMask(room, 10, 10, 0x4000);
    assertTrue(D2PathFootprint.remove(unit, false));
    assertEquals(0x4000, flags(room, 10, 10), "a second same-bit unit is not remembered by native masks");
  }

  @Test void degenerateObjectAddIsExplicitlyOutsideAdapterButEligibleRemoveStillReturnsTrue() {
    for (int[] sizes : new int[][] {{0, 1}, {1, 0}, {0, 0}}) {
      D2ActiveRoom room = room(0, 0, 24, 24);
      D2PathFootprint.Unit unit = object(room, sizes[0], sizes[1], 0, objectData(0, 0, 0, 0, 1));
      assertThrows(IllegalArgumentException.class, () -> D2PathFootprint.add(unit));
      assertTrue(D2PathFootprint.remove(unit, false));
      assertTrue(Arrays.stream(cells(room)).allMatch(value -> value == 0));
    }
  }

  private static D2PathRequest.State request(D2PathFootprint.Unit owner, D2PathFootprint.Unit target, int flags) {
    return request(owner, target, flags, () -> {});
  }

  private static D2PathRequest.State request(D2PathFootprint.Unit owner, D2PathFootprint.Unit target,
      int flags, Runnable guard) {
    D2PathContinuation.State path = new D2PathContinuation.State(owner.coordinates.type);
    path.currentPoint = D2PathUnits.coordinates(owner.coordinates);
    path.targetPoint = p(20, 10); path.previousTargetPoint = p(30, 30); path.finalTargetPoint = p(40, 40);
    path.pathType = 2; path.pathFlags = flags; path.setNewDistance(77);
    if (target != null) path.targetUnit = D2PathUnits.observation(owner.coordinates, target.coordinates, 0, guard);
    D2PathResult.State result = new D2PathResult.State(path); result.velocity = 256;
    D2DrlgCoords c = owner.room.getCoords();
    result.room = new D2PathResult.Room(c.getNSubtileX(), c.getNSubtileY(), c.getNSubtileWidth(), c.getNSubtileHeight());
    return new D2PathRequest.State(result, target == null ? null : target.coordinates,
        new D2PathRequest.Room(result.room, false), false, 0, owner.coordinates.sizeX, owner.pattern,
        0x80 | 0x100 | 0x400 | 0x200 | 1, 70);
  }

  @Test void requestAdapterRemovesRealOwnerAndObjectFootprintsAndRestoresBeforeNormalization() {
    D2ActiveRoom grid = room(0, 0, 40, 40);
    D2PathFootprint.Unit owner = dynamic(0, grid, 1, 1, 0x80);
    D2PathFootprint.Unit target = D2PathFootprint.Unit.object(D2PathUnits.Unit.stationary(2, 2, p(20, 10)), grid, 1, 4,
        objectData(0, 0, 0, 0, 0, 1));
    D2PathFootprint.add(owner); D2PathFootprint.add(target);
    D2PathRequest.State state = request(owner, target, 0x800 | 0x1000);
    D2PathFootprint.RequestEffects effects = new D2PathFootprint.RequestEffects(state, owner, target) {
      @Override public int search(D2PathRequest.Search search) {
        assertEquals(0, flags(grid, 10, 10));
        assertEquals(0, flags(grid, 20, 10));
        assertEquals(0, flags(grid, 19, 8));
        List<D2Path.Point> points = D2Toward.compute(search.start, search.target,
            search.maximumDistance, search.tolerance, 0, (x, y) -> blocked(state, x, y)).points();
        state.result.storePoints(points); return points.size();
      }
    };
    assertEquals(1, D2PathRequest.compute(state, false, effects));
    assertEquals(0x1080, flags(grid, 10, 10));
    assertEquals(0x400, flags(grid, 19, 8));
    assertEquals(0x400, flags(grid, 20, 10));
    assertEquals(4096, state.result.velocityVectorX);
    assertEquals(p(10, 10), state.result.path.currentPoint);
  }

  @Test void ineligibleObjectTargetIsNotRestoredButOwnerIsRestoredEvenIfDeadRemovalWasFalse() {
    D2ActiveRoom grid = room(0, 0, 40, 40);
    D2PathFootprint.Unit owner = dynamic(0, grid, 17, 1, 0x80);
    D2PathFootprint.Unit target = D2PathFootprint.Unit.object(D2PathUnits.Unit.stationary(2, 1, p(20, 10)), grid, 2, 1,
        objectData(0, 0, 0, 0));
    D2PathRequest.State state = request(owner, target, 0x800);
    D2PathFootprint.RequestEffects effects = new D2PathFootprint.RequestEffects(state, owner, target) {
      @Override public int search(D2PathRequest.Search search) {
        assertEquals(0, flags(grid, 10, 10));
        assertEquals(0, flags(grid, 20, 10));
        return 0;
      }
    };
    assertEquals(0, D2PathRequest.compute(state, false, effects));
    assertEquals(0x1080, flags(grid, 10, 10), "native owner add is unconditional even when remove returned false");
    assertEquals(0, flags(grid, 20, 10), "a false target removal must not manufacture its footprint");
  }

  @Test void requestWithoutTargetRemovalFlagKeepsTheObjectVisibleToCollisionQueries() {
    D2ActiveRoom grid = room(0, 0, 40, 40);
    D2PathFootprint.Unit owner = dynamic(0, grid, 1, 1, 0x80);
    D2PathFootprint.Unit target = D2PathFootprint.Unit.object(D2PathUnits.Unit.stationary(2, 1, p(20, 10)), grid, 0, 1,
        objectData(0, 0, 0, 0, 1));
    D2PathFootprint.add(owner); D2PathFootprint.add(target);
    D2PathRequest.State state = request(owner, target, 0);
    D2PathFootprint.RequestEffects effects = new D2PathFootprint.RequestEffects(state, owner, target) {
      @Override public int search(D2PathRequest.Search search) {
        assertTrue(blocked(state, 20, 10));
        assertFalse(blocked(state, 10, 10));
        return 0;
      }
    };
    assertEquals(0, D2PathRequest.compute(state, false, effects));
    assertEquals(0x400, flags(grid, 20, 10));
  }

  @Test void targetFootprintUsesItsOwnRoomNotTheOwnerRoomAndCanBeRestoredOnHostFailure() {
    D2ActiveRoom a = room(0, 0, 15, 24), b = room(15, 0, 25, 24);
    a.setPpRoomList(new D2ActiveRoom[] {b}); a.setNNumRooms(1);
    D2PathFootprint.Unit owner = dynamic(0, a, 1, 1, 0x80);
    D2PathFootprint.Unit target = D2PathFootprint.Unit.stationary(D2PathUnits.Unit.stationary(4, 1, p(20, 10)), b);
    D2PathFootprint.add(owner); D2PathFootprint.add(target);
    D2PathRequest.State state = request(owner, target, 0x800);
    D2PathFootprint.RequestEffects effects = new D2PathFootprint.RequestEffects(state, owner, target) {
      @Override public int search(D2PathRequest.Search search) {
        assertEquals(0, flags(b, 20, 10));
        throw new IllegalStateException("host search failure");
      }
    };
    assertThrows(IllegalStateException.class, () -> D2PathRequest.compute(state, false, effects));
    assertEquals(0x200, flags(b, 20, 10));
    assertEquals(0x1080, flags(a, 10, 10));
  }

  @Test void boundAdapterRejectsMismatchedTargetIdentityOwnerMetadataAndOtherRequests() {
    D2ActiveRoom grid = room(0, 0, 40, 40);
    D2PathFootprint.Unit owner = dynamic(0, grid, 1, 1, 0x80);
    D2PathRequest.State state = request(owner, null, 0);
    D2PathFootprint.RequestEffects effects = new D2PathFootprint.RequestEffects(state, owner, null) {
      @Override public int search(D2PathRequest.Search search) { return 0; }
    };
    assertThrows(IllegalArgumentException.class, () -> effects.blocked(request(owner, null, 0), 10, 10));
    D2PathFootprint.Unit wrongPattern = dynamic(0, grid, 1, 2, 0x80);
    assertThrows(IllegalArgumentException.class, () -> new D2PathFootprint.RequestEffects(state, wrongPattern, null) {
      @Override public int search(D2PathRequest.Search search) { return 0; }
    });
    D2PathFootprint.Unit target = D2PathFootprint.Unit.stationary(D2PathUnits.Unit.stationary(4, 1, p(20, 10)), grid);
    assertThrows(IllegalArgumentException.class, () -> new D2PathFootprint.RequestEffects(state, owner, target) {
      @Override public int search(D2PathRequest.Search search) { return 0; }
    });
  }

  @Test void invalidMetadataAndNullUnitAreRejectedWithoutGuessingNativeMemory() {
    assertThrows(NullPointerException.class, () -> D2PathFootprint.add(null));
    assertThrows(NullPointerException.class, () -> D2PathFootprint.remove(null, true));
    assertThrows(IllegalArgumentException.class, () -> new D2PathFootprint.ObjectData(256, 0, 0, 0, new int[8]));
    assertThrows(IllegalArgumentException.class, () -> new D2PathFootprint.ObjectData(0, 0, 0, 0, new int[7]));
    assertThrows(IllegalArgumentException.class, () -> object(null, 1, 1, 8, objectData(0, 0, 0, 0)));
    assertThrows(IllegalArgumentException.class, () -> D2PathFootprint.Unit.stationary(D2PathUnits.Unit.dynamic(0, 2, null), null));
  }

  @Test void standardObservationRejectsSameCoordinateReplacementOwnerBeforeGridWrites() {
    D2ActiveRoom grid = room(0, 0, 40, 40);
    D2PathFootprint.Unit owner = dynamic(0, grid, 1, 1, 0x80);
    D2PathFootprint.Unit target = dynamic(1, grid, 1, 1, 0x100);
    D2PathRequest.State state = request(owner, target, 0);
    D2PathFootprint.Unit replacement = dynamic(0, grid, 1, 1, 0x80);
    int[] before = cells(grid);
    assertThrows(IllegalArgumentException.class, () -> new D2PathFootprint.RequestEffects(state, replacement, target) {
      @Override public int search(D2PathRequest.Search search) { return 0; }
    });
    assertArrayEquals(before, cells(grid));
  }

  @Test void directEffectsRejectStaleObservationBeforeAnyGridOperation() {
    D2ActiveRoom grid = room(0, 0, 40, 40);
    D2PathFootprint.Unit owner = dynamic(0, grid, 1, 1, 0x80);
    D2PathFootprint.Unit target = dynamic(1, grid, 1, 1, 0x100);
    boolean[] live = {true};
    D2PathRequest.State state = request(owner, target, 0, () -> {
      if (!live[0]) throw new IllegalStateException("stale lifetime");
    });
    D2PathFootprint.RequestEffects effects = new D2PathFootprint.RequestEffects(state, owner, target) {
      @Override public int search(D2PathRequest.Search search) { return 0; }
    };
    D2PathFootprint.add(owner); D2PathFootprint.add(target);
    int[] before = cells(grid); live[0] = false;
    assertThrows(IllegalStateException.class, () -> effects.removeOwner(state));
    assertThrows(IllegalStateException.class, () -> effects.removeTarget(state));
    assertThrows(IllegalStateException.class, () -> effects.addOwner(state));
    assertThrows(IllegalStateException.class, () -> effects.addTarget(state));
    assertThrows(IllegalStateException.class, () -> effects.blocked(state, 10, 10));
    assertArrayEquals(before, cells(grid));
  }

  @Test void directEffectsRejectReplacedObservationAndStaleConstruction() {
    D2ActiveRoom grid = room(0, 0, 40, 40);
    D2PathFootprint.Unit owner = dynamic(0, grid, 1, 1, 0x80);
    D2PathFootprint.Unit target = dynamic(1, grid, 1, 1, 0x100);
    D2PathRequest.State state = request(owner, target, 0);
    D2PathFootprint.RequestEffects effects = new D2PathFootprint.RequestEffects(state, owner, target) {
      @Override public int search(D2PathRequest.Search search) { return 0; }
    };
    state.result.path.targetUnit = D2PathUnits.observation(owner.coordinates, target.coordinates, 0);
    assertThrows(IllegalArgumentException.class, () -> effects.addOwner(state));
    assertThrows(IllegalArgumentException.class, () -> new D2PathFootprint.RequestEffects(state, owner, target) {
      @Override public int search(D2PathRequest.Search search) { return 0; }
    });
  }
}
