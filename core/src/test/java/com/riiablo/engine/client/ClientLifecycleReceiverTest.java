package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.net.packet.d2gs.D2GS;
import com.riiablo.net.packet.d2gs.D2GSData;
import com.riiablo.net.packet.d2gs.EntityFlags;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.net.packet.d2gs.SnapshotBaseline;
import com.riiablo.net.packet.d2gs.SnapshotBaselinePhase;
import com.riiablo.save.CharData;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class ClientLifecycleReceiverTest extends RiiabloTest {
  @Test void replicaVitalsAreNotChangedByClientResourceSystems() {
    World world = new World(new WorldConfigurationBuilder().with(
        new com.riiablo.engine.server.ManaRecoverySystem(),
        new com.riiablo.engine.server.StaminaSystem()).build());
    try {
      int player = world.create();
      world.getMapper(Player.class).create(player);
      Attributes attrs = Attributes.obtainStandard();
      attrs.base().put(Stat.hitpoints, 80f);
      world.getMapper(AttributesWrapper.class).create(player).attrs = attrs;
      world.getMapper(Velocity.class).create(player).setPlayer(6f, 9f);
      AuthoritativeUnitLifecycleState store = new AuthoritativeUnitLifecycleState();
      store.commit(player, AuthoritativeUnitLifecycleStateTest.packet(10, (int) Engine.Player.MODE_NU, false, true));
      ClientUnitSnapshotWriter.states(player, world.getMapper(UnitStates.class).create(player), store.get(player));
      ClientUnitSnapshotWriter.vitals(world.getMapper(AttributesWrapper.class).get(player), store.get(player).vitals);
      for (int i = 0; i < 25; i++) world.process();
      assertEquals(37f, attrs.aggregate().getValue(Stat.hitpoints, 0f));
      assertEquals(9f, attrs.aggregate().getValue(Stat.mana, 0f));
      assertEquals(3f, attrs.aggregate().getValue(Stat.stamina, 0f));
      assertEquals(80f, attrs.base().getValue(Stat.hitpoints, 0f));
    } finally { world.dispose(); }
  }
  @Test void deathThenRespawnCopiesWireValuesAndDropsLateDeath() throws Exception {
    try (Fixture f = new Fixture()) {
      f.receive(AuthoritativeUnitLifecycleStateTest.packet(10, (int) Engine.Player.MODE_DD, true, true));
      assertTrue(f.receiver.authoritativeLifecycle(f.entity).dead());
      assertTrue(f.world.getMapper(PlayerCorpse.class).has(f.entity));
      UnitStates states = f.world.getMapper(UnitStates.class).get(f.entity);
      assertTrue(states.snapshotOnly);
      assertEquals(12, states.stateList.getState(StateId.POISON).duration);
      assertEquals(6, states.stateList.getState(StateId.POISON).periodicCountdownFrames);
      assertEquals(80f, f.attrs.base().getValue(Stat.hitpoints, 0f));
      assertEquals(0f, f.attrs.aggregate().getValue(Stat.hitpoints, 0f));
      f.receive(AuthoritativeUnitLifecycleStateTest.packet(12, (int) Engine.Player.MODE_NU, false, false));
      AuthoritativeUnitLifecycleState.Snapshot alive = f.receiver.authoritativeLifecycle(f.entity);
      assertFalse(alive.dead());
      assertFalse(f.world.getMapper(PlayerCorpse.class).has(f.entity));
      assertEquals(37f, f.attrs.aggregate().getValue(Stat.hitpoints, 0f));
      assertEquals(80f, f.attrs.base().getValue(Stat.hitpoints, 0f));
      f.receive(AuthoritativeUnitLifecycleStateTest.packet(11, (int) Engine.Player.MODE_DT, true, true));
      assertSame(alive, f.receiver.authoritativeLifecycle(f.entity));
      assertEquals(37f, f.attrs.aggregate().getValue(Stat.hitpoints, 0f));
      assertTrue(states.stateList.getStates().isEmpty());
      assertTrue(f.position.hasNativePrecision(), "lifecycle alone does not move the player");
    }
  }

  @Test void deletionAndBaselineBeginClearCommittedValues() throws Exception {
    try (Fixture f = new Fixture()) {
      f.receive(AuthoritativeUnitLifecycleStateTest.packet(10, (int) Engine.Player.MODE_DD, true, true));
      FlatBufferBuilder builder = new FlatBufferBuilder(64);
      builder.finish(EntitySync.createEntitySync(builder, 100, 2, EntityFlags.deleted,
          0, 0, 11, 440, 0, 0, 0, -1));
      f.receive(EntitySync.getRootAsEntitySync(builder.dataBuffer()));
      assertNull(f.receiver.authoritativeLifecycle(f.entity));
    }
    try (Fixture f = new Fixture()) {
      f.receive(AuthoritativeUnitLifecycleStateTest.packet(10, (int) Engine.Player.MODE_DD, true, true));
      FlatBufferBuilder builder = new FlatBufferBuilder(128);
      SnapshotBaseline.startSnapshotBaseline(builder);
      SnapshotBaseline.addRequestId(builder, 1);
      SnapshotBaseline.addBaselineId(builder, 1);
      SnapshotBaseline.addServerTick(builder, 11);
      SnapshotBaseline.addPhase(builder, SnapshotBaselinePhase.BEGIN);
      SnapshotBaseline.addSuccess(builder, true);
      int marker = SnapshotBaseline.endSnapshotBaseline(builder);
      builder.finish(D2GS.createD2GS(builder, D2GSData.SnapshotBaseline, marker));
      ClientPlayerRespawnResultTest.receive(f.receiver, D2GS.getRootAsD2GS(builder.dataBuffer()));
      assertNull(f.receiver.authoritativeLifecycle(f.entity));
    }
  }

  @Test void disposingSessionDropsLifecycleSnapshots() throws Exception {
    try (Fixture f = new Fixture()) {
      f.receive(AuthoritativeUnitLifecycleStateTest.packet(10, (int) Engine.Player.MODE_DD, true, true));
      f.receiver.close();
      assertNull(f.receiver.authoritativeLifecycle(f.entity));
    }
  }

  private static final class Fixture implements AutoCloseable {
    final ClientUnitLifecycleSystem lifecycle = new ClientUnitLifecycleSystem(null, null);
    final World world = new World(new WorldConfigurationBuilder()
        .with(new NetworkIdManager(), lifecycle).build());
    final Harness receiver = new Harness();
    final int entity = world.create();
    final Attributes attrs = Attributes.obtainStandard();
    final Position position = world.getMapper(Position.class).create(entity);

    Fixture() {
      attrs.base().put(Stat.hitpoints, 80f);
      attrs.aggregate().put(Stat.hitpoints, 80f);
      world.getMapper(AttributesWrapper.class).create(entity).attrs = attrs;
      world.getMapper(CofReference.class).create(entity).set("AM", Engine.Player.MODE_NU);
      position.initializeNativeCell(22, 33);
      receiver.attach(world);
      receiver.syncIds.put(100, entity);
      receiver.clientSession = new ClientNetworkSession(mock(CharData.class), () -> -1);
      lifecycle.receiver = receiver;
      world.process();
    }

    void receive(EntitySync data) throws Exception {
      Method apply = ClientNetworkReceiver.class.getDeclaredMethod("Synchronize", EntitySync.class);
      apply.setAccessible(true);
      apply.invoke(receiver, data);
    }
    @Override public void close() { world.dispose(); }
  }

  @com.artemis.annotations.All
  private static final class Harness extends ClientNetworkReceiver {
    void attach(World attached) {
      world = attached;
      syncIds = attached.getSystem(NetworkIdManager.class);
      cofs = mock(CofManager.class);
      mPosition = attached.getMapper(Position.class);
      mPlayer = attached.getMapper(Player.class);
      mUnitStates = attached.getMapper(UnitStates.class);
      mAttributesWrapper = attached.getMapper(AttributesWrapper.class);
      mCofReference = attached.getMapper(CofReference.class);
    }
    void close() { dispose(); }
  }
}
