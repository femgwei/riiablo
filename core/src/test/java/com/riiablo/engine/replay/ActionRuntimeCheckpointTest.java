package com.riiablo.engine.replay;

import static org.junit.jupiter.api.Assertions.*;

import com.artemis.World;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.Leap;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.item.Item;
import org.junit.jupiter.api.Test;

class ActionRuntimeCheckpointTest {
  @Test void capturesAndRestoresEveryPortableActionPhaseValue() {
    try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
      int sourceId = source.world.create();
      int targetId = target.world.create();
      Casting casting = source.world.getMapper(Casting.class).create(sourceId)
          .set(41, 77, new Vector2(12.5f, -3.25f), 901L);
      casting.actionId = 0x1122334455667788L;
      casting.nativeSummonClass = 14;
      casting.nativeSummonMode = 3;
      casting.nativeSummonPosition.set(9.25f, 4.5f);
      casting.shapeShiftProcessed = true;
      casting.jabRemainingStrikes = 2;
      casting.jabStrikeProcessed = true;
      casting.fendCurrentTargetId = 33;
      casting.fendRemainingStrikes = 4;
      casting.fendInitialized = true;
      casting.strafeTargetIds.addAll(new int[] {7, 11, 19});
      casting.monInferno = new com.riiablo.engine.server.NativeMonInferno(9, 3, 13, true, false);
      casting.monInferno.frame = 4; casting.monInferno.nextUpdate = 7;
      casting.monInferno.missiles = 2; casting.monInferno.sequenceCursor = 11;
      casting.diabRun = new com.riiablo.engine.server.NativeDiabRun(77, new Vector2(21, 34), true);
      casting.diabRun.cursor = 0x120; casting.diabRun.remaining = 0x800;
      casting.diabRun.moving = true; casting.diabRun.pathStarted = true; casting.diabRun.loops = 2;
      Sequence sequence = source.world.getMapper(Sequence.class).create(sourceId)
          .sequence((byte) 8, (byte) 13);
      sequence.started = true;
      sequence.nativeMummy = true;
      sequence.nativeMummyFrame = 13;
      sequence.nativeSubmergeLastPoint = -1;
      sequence.nativeSubmergeHeld = true;
      sequence.nativeJab = true;
      sequence.nativeJabWeaponClass = 6;
      sequence.nativeJabFrame = 17;
      sequence.nativeJabFrameCount = 29;
      sequence.nativeJabSpeed = 241;
      Leap leap = source.world.getMapper(Leap.class).create(sourceId)
          .set(new Vector2(1.5f, 2.5f), new Vector2(30.25f, -4.75f), 1.75f, 55);
      leap.elapsed = 0.625f;

      target.world.getMapper(Casting.class).create(targetId);
      target.world.getMapper(Sequence.class).create(targetId);
      target.world.getMapper(Leap.class).create(targetId);
      ActionRuntimeCheckpoint image = ActionRuntimeCheckpoint.capture(source.world, sourceId);
      image.validateHost(target.world, targetId);
      ActionRuntimeCheckpoint.restore(target.world, targetId, image);

      assertArrayEquals(image.bytes(), ActionRuntimeCheckpoint.capture(target.world, targetId).bytes());
      assertEquals(casting.actionId, target.world.getMapper(Casting.class).get(targetId).actionId);
      assertEquals(casting.monInferno.frame,
          target.world.getMapper(Casting.class).get(targetId).monInferno.frame);
      assertEquals(casting.diabRun.cursor,
          target.world.getMapper(Casting.class).get(targetId).diabRun.cursor);
      assertEquals(sequence.nativeJabFrame, target.world.getMapper(Sequence.class).get(targetId).nativeJabFrame);
      assertEquals(leap.elapsed, target.world.getMapper(Leap.class).get(targetId).elapsed);
    }
  }

  @Test void rejectsAliveNativeObjectInsteadOfDroppingActionPhase() {
    try (Fixture fixture = new Fixture()) {
      int id = fixture.world.create();
      Casting casting = fixture.world.getMapper(Casting.class).create(id);
      casting.impaleWeapon = new Item();
      assertThrows(IllegalStateException.class, () -> ActionRuntimeCheckpoint.capture(fixture.world, id));
    }
  }

  private static final class Fixture implements AutoCloseable {
    final World world = new World();
    @Override public void close() { world.dispose(); }
  }
}
