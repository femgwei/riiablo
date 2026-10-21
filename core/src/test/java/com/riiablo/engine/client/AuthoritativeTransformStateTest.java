package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.engine.server.component.Position;
import com.riiablo.net.packet.d2gs.AngleP;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.net.packet.d2gs.VelocityP;
import com.riiablo.net.packet.d2gs.PositionP;
import org.junit.jupiter.api.Test;

class AuthoritativeTransformStateTest {
  @Test void snapshotOwnsScalarsEvenWhenWireBufferIsReused() {
    AuthoritativeTransformState state = new AuthoritativeTransformState();
    EntitySync wire = packet(1, 1000, 7f, true);
    assertTrue(state.commit(4, wire));
    AuthoritativeTransformState.Snapshot committed = state.get(4);
    java.nio.ByteBuffer buffer = wire.getByteBuffer();
    for (int i = buffer.position(); i < buffer.limit(); i++) buffer.put(i, (byte) 0);
    assertEquals(7f, committed.x);
    assertEquals(2f, committed.y);
    assertEquals(3f, committed.velocityX);
    assertEquals(4f, committed.velocityY);
    assertEquals(0f, committed.angleX);
    assertEquals(1f, committed.angleY);
    assertTrue(committed.hasPosition && committed.hasVelocity && committed.hasAngle);
  }

  @Test void partialUpdatesRetainOtherFieldsAndOldSnapshotsStayImmutable() {
    AuthoritativeTransformState state = new AuthoritativeTransformState();
    state.commit(4, packet(1, 1000, 7f, true));
    AuthoritativeTransformState.Snapshot first = state.get(4);
    state.commit(4, packet(2, 1040, 9f, false));
    AuthoritativeTransformState.Snapshot next = state.get(4);
    assertEquals(7f, first.x);
    assertEquals(9f, next.x);
    assertEquals(3f, next.velocityX);
    assertEquals(1f, next.angleY);
    assertEquals(2L, next.tick);
    assertEquals(1040L, next.serverTimeMillis);
  }

  @Test void rejectsTickAndTimeRegressionIncludingAfterLegacyPacket() {
    AuthoritativeTransformState state = new AuthoritativeTransformState();
    state.commit(4, packet(10, 1400, 7f, true));
    assertFalse(state.commit(4, packet(9, 1440, -1f, false)));
    assertFalse(state.commit(4, packet(11, 1360, -1f, false)));
    assertEquals(7f, state.get(4).x);
    assertTrue(state.commit(4, packet(0, 0, 8f, false)));
    assertFalse(state.commit(4, packet(9, 1360, -1f, false)));
    assertEquals(8f, state.get(4).x);
    assertTrue(state.commit(4, packet(10, 1400, 9f, false)));
  }

  @Test void removalAndResyncDropValuesAndWatermarks() {
    AuthoritativeTransformState state = new AuthoritativeTransformState();
    state.commit(4, packet(10, 1400, 7f, true));
    state.remove(4);
    assertNull(state.get(4));
    assertTrue(state.commit(4, packet(1, 1000, 2f, false)));
    state.commit(5, packet(10, 1400, 7f, true));
    state.clear();
    assertNull(state.get(4));
    assertNull(state.get(5));
    assertTrue(state.commit(5, packet(1, 1000, 3f, false)));
  }

  @Test void reconciliationAndRenderingCannotChangeCommittedAuthority() {
    AuthoritativeTransformState state = new AuthoritativeTransformState();
    state.commit(4, packet(2, 1040, 5f, true));
    AuthoritativeTransformState.Snapshot authority = state.get(4);
    ClientPredictionBuffer prediction = new ClientPredictionBuffer();
    prediction.reset(4f, 2f);
    prediction.recordSent(1, 5f, 2f);
    prediction.recordSent(2, 6f, 2f);
    ClientPredictionBuffer.Reconciliation replay = prediction.reconcile(
        1, authority.x, authority.y, 7f, 2f, false);
    assertEquals(6f, replay.x);

    AuthoritativeInterpolationSystem interpolation = new AuthoritativeInterpolationSystem();
    World world = new World(new WorldConfigurationBuilder().with(interpolation).build());
    try {
      int player = world.create();
      Position replica = world.getMapper(Position.class).create(player);
      replica.initializeNativeCell((int) replay.x, (int) replay.y);
      world.process();
      interpolation.correctLocal(player, replay.renderOffsetX, replay.renderOffsetY, replay.hard);
      interpolation.beginRender(0.04f);
      assertEquals(7f, ClientTransformView.position(world, player, new Vector2()).x);
      assertEquals(6f, replica.position.x);
      assertTrue(replica.hasNativePrecision());
      interpolation.endRender();
      assertSame(authority, state.get(4));
      assertEquals(5f, authority.x);
      assertEquals(2L, authority.tick);
    } finally { world.dispose(); }
  }

  static EntitySync packet(long tick, long time, float x, boolean complete) {
    FlatBufferBuilder builder = new FlatBufferBuilder(128);
    int position = PositionP.createPositionP(builder, x, 2f);
    int[] values = complete ? new int[] {position,
        VelocityP.createVelocityP(builder, 3f, 4f),
        AngleP.createAngleP(builder, 0f, 1f)} : new int[] {position};
    byte[] types = complete ? new byte[] {ComponentP.PositionP, ComponentP.VelocityP,
        ComponentP.AngleP} : new byte[] {ComponentP.PositionP};
    int components = EntitySync.createComponentVector(builder, values);
    int componentTypes = EntitySync.createComponentTypeVector(builder, types);
    builder.finish(EntitySync.createEntitySync(builder, 100, 1, 0, componentTypes,
        components, tick, time, 0, 0, 0, -1));
    return EntitySync.getRootAsEntitySync(builder.dataBuffer());
  }
}
