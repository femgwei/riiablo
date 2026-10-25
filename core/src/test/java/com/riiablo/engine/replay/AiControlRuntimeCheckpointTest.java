package com.riiablo.engine.replay;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.engine.server.component.NativeAiCommand;
import com.riiablo.engine.server.component.NativeAiPathCache;
import com.riiablo.engine.server.component.NativePathIdentity;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiControlRuntimeCheckpointTest {
  private static final int[] IDS = {0, 1, 2};

  @Test void newComponentFieldsRequireAnExplicitSchemaDecision() throws Exception {
    fields(NativeAiCommand.class, "ownerId", "targetId", "command", "expiresAtFrame",
        "targetIdentity", "ownerIdentity", "pending");
    fields(java.lang.Class.forName(NativeAiCommand.class.getName() + "$Saved"),
        "owner", "command", "target", "identity", "ownerIdentity", "expiry");
    fields(NativeAiPathCache.class, "targetX", "targetY", "coordinatesKnown", "pointsKnown", "hasPoints");
  }

  private static void fields(java.lang.Class<?> type, String... expected) {
    Set<String> actual = new HashSet<>();
    for (Field field : type.getDeclaredFields()) if (!Modifier.isStatic(field.getModifiers())
        && !field.isSynthetic()) actual.add(field.getName());
    assertEquals(new HashSet<>(Arrays.asList(expected)), actual);
  }

  @Test void currentDisplacedAndLegacyCommandsResumeInExactPopOrder() {
    try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
      NativeAiCommand command = source.command(0).set(0, NativeAiCommand.ATTACK, 1);
      command.expiresAtFrame = -7;
      command.copy(source.world, 1, NativeAiCommand.MOVE, 2).expiresAtFrame = Long.MAX_VALUE;
      source.identity(2).allocated(source.world, 2); // Middle command's retained target becomes stale.
      command.copy(source.world, 0, 999, 1).expiresAtFrame = 77;
      AiControlRuntimeCheckpoint saved = AiControlRuntimeCheckpoint.capture(source.world, IDS);
      target.command(2).set(-1, 55, -1);
      target.path(2).allocated();
      saved.restore(target.world);
      assertFalse(target.world.getMapper(NativeAiCommand.class).has(2));
      assertFalse(target.world.getMapper(NativeAiPathCache.class).has(2));
      assertArrayEquals(saved.bytes(), AiControlRuntimeCheckpoint.capture(target.world, IDS).bytes());
      NativeAiCommand restored = target.command(0);
      assertEquals(999, restored.command); assertEquals(77, restored.expiresAtFrame);
      assertSame(target.identity(0).token(), restored.ownerIdentity);
      assertSame(target.identity(1).token(), restored.targetIdentity);
      assertNotSame(command.targetIdentity, restored.targetIdentity);
      assertTrue(restored.pop()); assertEquals(14, restored.command);
      assertEquals(Long.MAX_VALUE, restored.expiresAtFrame);
      assertFalse(restored.targetIsCurrent(target.world)); assertTrue(restored.ownerIsCurrent(target.world));
      assertTrue(restored.pop()); assertEquals(1, restored.command);
      assertEquals(-7, restored.expiresAtFrame); assertNull(restored.targetIdentity); assertNull(restored.ownerIdentity);
      assertFalse(restored.pop()); assertEquals(0, restored.command);
      assertEquals(-1, restored.ownerId); assertEquals(-1, restored.targetId);
      assertEquals(Long.MAX_VALUE, restored.expiresAtFrame);
      // Capture never consumed the original stack.
      assertEquals(3, command.captureCheckpoint(source.world).size());
      assertThrows(UnsupportedOperationException.class, () -> command.captureCheckpoint(source.world).clear());
    }
  }

  @Test void rawPathCoordinatesAndAllKnownUnknownCombinationsSurvive() {
    try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
      for (int mask = 0; mask < 8; mask++) {
        NativeAiPathCache path = source.path(1);
        path.targetX = Integer.MIN_VALUE; path.targetY = Integer.MAX_VALUE;
        path.coordinatesKnown = (mask & 1) != 0; path.pointsKnown = (mask & 2) != 0;
        path.hasPoints = (mask & 4) != 0;
        AiControlRuntimeCheckpoint saved = AiControlRuntimeCheckpoint.capture(source.world, IDS);
        saved.restore(target.world);
        assertArrayEquals(saved.bytes(), AiControlRuntimeCheckpoint.capture(target.world, IDS).bytes());
        assertEquals(Integer.MIN_VALUE, target.path(1).targetX);
        assertEquals(Integer.MAX_VALUE, target.path(1).targetY);
      }
    }
  }

  @Test void aRestoredCurrentTokenStillExpiresWhenItsIdIsReused() {
    try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
      source.command(0).copy(source.world, 1, 1, 2);
      AiControlRuntimeCheckpoint.capture(source.world, IDS).restore(target.world);
      target.identity(1).allocated(target.world, 1); target.identity(2).allocated(target.world, 2);
      assertFalse(target.command(0).ownerIsCurrent(target.world));
      assertFalse(target.command(0).targetIsCurrent(target.world));
    }
  }

  @Test void missingLaterLifetimeFailsBeforeAnyEarlierHostChanges() {
    try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
      source.command(0).set(-1, 12, -1);
      source.command(2).copy(source.world, 2, 1, 1);
      AiControlRuntimeCheckpoint saved = AiControlRuntimeCheckpoint.capture(source.world, IDS);
      target.command(0).set(-1, 99, -1);
      target.world.getMapper(NativePathIdentity.class).remove(1);
      byte[] before = AiControlRuntimeCheckpoint.capture(target.world, IDS).bytes();
      assertThrows(IllegalStateException.class, () -> saved.restore(target.world));
      assertArrayEquals(before, AiControlRuntimeCheckpoint.capture(target.world, IDS).bytes());
    }
  }

  @Test void uncoveredLocalHostsAndCurrentReferencesFailClosedButStaleMissingTargetsDoNot() {
    try (Fixture f = new Fixture()) {
      f.command(2);
      assertThrows(IllegalStateException.class, () -> AiControlRuntimeCheckpoint.capture(f.world, new int[] {0, 1}));
      f.world.getMapper(NativeAiCommand.class).remove(2);
      f.command(0).copy(f.world, 0, 1, 2);
      assertThrows(IllegalArgumentException.class, () -> AiControlRuntimeCheckpoint.capture(f.world, new int[] {0, 1}));
      f.world.delete(2); f.world.process();
      AiControlRuntimeCheckpoint saved = AiControlRuntimeCheckpoint.capture(f.world, new int[] {0, 1});
      saved.restore(f.world);
      assertFalse(f.command(0).targetIsCurrent(f.world));
      f.path(1);
      assertThrows(IllegalStateException.class, () -> AiControlRuntimeCheckpoint.capture(f.world, new int[] {0}));
      assertThrows(IllegalStateException.class, () -> AiControlRuntimeCheckpoint.capture(f.world, IDS));
      assertThrows(IllegalArgumentException.class, () -> AiControlRuntimeCheckpoint.capture(f.world, new int[] {0, 0}));
    }
  }

  @Test void malformedImagesAreRejectedAndOwnedBytesCannotBeMutated() {
    try (Fixture f = new Fixture()) {
      f.command(0).copy(f.world, 0, 1, 1); f.path(0).allocated();
      AiControlRuntimeCheckpoint saved = AiControlRuntimeCheckpoint.capture(f.world, IDS);
      byte[] bytes = saved.bytes();
      for (int length = 0; length < bytes.length; length++) {
        byte[] truncated = Arrays.copyOf(bytes, length);
        assertThrows(IllegalArgumentException.class, () -> AiControlRuntimeCheckpoint.fromBytes(truncated));
      }
      assertThrows(IllegalArgumentException.class, () -> AiControlRuntimeCheckpoint.fromBytes(Arrays.copyOf(bytes, bytes.length + 1)));
      for (int offset : new int[] {3, 12, 37, 38, 48, 49, 50}) {
        byte[] bad = bytes.clone(); bad[offset] = 3;
        assertThrows(IllegalArgumentException.class, () -> AiControlRuntimeCheckpoint.fromBytes(bad), "offset " + offset);
      }
      for (int count : new int[] {0, -1, 65537}) {
        byte[] bad = bytes.clone(); ByteBuffer.wrap(bad).putInt(13, count);
        assertThrows(IllegalArgumentException.class, () -> AiControlRuntimeCheckpoint.fromBytes(bad));
      }
      byte[] badRef = bytes.clone(); ByteBuffer.wrap(badRef).putInt(25, 7);
      assertThrows(IllegalArgumentException.class, () -> AiControlRuntimeCheckpoint.fromBytes(badRef));
      byte[] owned = saved.bytes(); owned[0] = 1;
      assertArrayEquals(bytes, saved.bytes());
      AiControlRuntimeCheckpoint decoded = AiControlRuntimeCheckpoint.fromBytes(bytes);
      bytes[0] = 1; assertEquals(0, decoded.bytes()[0]);
    }
  }

  @Test void componentRestorePreflightsTheEntireStackAndSetClearsPending() {
    try (Fixture f = new Fixture()) {
      NativeAiCommand command = f.command(0).copy(f.world, 0, 1, 1);
      command.copy(f.world, 0, 14, 2);
      List<NativeAiCommand.Checkpoint> invalid = Arrays.asList(
          new NativeAiCommand.Checkpoint(-1, 55, -1, 8, 0, 0),
          new NativeAiCommand.Checkpoint(0, 1, 2, 9, 1, 1));
      f.world.getMapper(NativePathIdentity.class).remove(2);
      assertThrows(IllegalStateException.class, () -> command.restoreCheckpoint(f.world, invalid));
      assertEquals(14, command.command); assertEquals(2, command.captureCheckpoint(f.world).size());
      command.set(-1, 13, -1); assertEquals(1, command.captureCheckpoint(f.world).size());
      assertFalse(command.pop());
      assertThrows(IllegalArgumentException.class, () -> command.restoreCheckpoint(f.world, Collections.emptyList()));
      assertThrows(IllegalArgumentException.class, () -> new NativeAiCommand.Checkpoint(-1, 1, -1, 0, 1, 0));
    }
  }

  @Test void removedCommandComponentCannotLeakItsStackOrTokensIntoPooledReuse() {
    try (Fixture f = new Fixture()) {
      f.command(0).set(0, 14, 1).copy(f.world, 0, 1, 2).expiresAtFrame = 99;
      AiControlRuntimeCheckpoint saved = AiControlRuntimeCheckpoint.capture(f.world, IDS);
      saved.restore(f.world);
      f.world.delete(0); f.world.process();
      int reused = f.world.create();
      NativeAiCommand command = f.command(reused);
      assertEquals(NativeAiCommand.NONE, command.command);
      assertEquals(-1, command.ownerId); assertEquals(-1, command.targetId);
      assertEquals(Long.MAX_VALUE, command.expiresAtFrame);
      assertNull(command.ownerIdentity); assertNull(command.targetIdentity);
      assertEquals(1, command.captureCheckpoint(f.world).size()); assertFalse(command.pop());
    }
  }

  private static final class Fixture implements AutoCloseable {
    final World world = new World(new WorldConfigurationBuilder().build());
    Fixture() { for (int id : IDS) { assertEquals(id, world.create()); identity(id).allocated(world, id); } world.process(); }
    NativePathIdentity identity(int id) { return world.getMapper(NativePathIdentity.class).create(id); }
    NativeAiCommand command(int id) { return world.getMapper(NativeAiCommand.class).create(id); }
    NativeAiPathCache path(int id) { return world.getMapper(NativeAiPathCache.class).create(id); }
    @Override public void close() { world.dispose(); }
  }
}
