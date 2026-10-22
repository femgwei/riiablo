package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.PlayerCorpse;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import org.junit.jupiter.api.Test;

class ClientUnitLifecycleSystemTest {
  @Test void capturedLocalRespawnIsAdmittedOnlyOnTickAndRevalidated() {
    ClientUnitLifecycleSystem.LocalAuthority authority = mock(ClientUnitLifecycleSystem.LocalAuthority.class);
    ClientUnitLifecycleSystem input = new ClientUnitLifecycleSystem(authority, null);
    when(authority.isPlayerDead(7)).thenReturn(true);
    when(authority.canRespawnPlayer(7)).thenReturn(true);
    input.requestRespawn(7);
    verifyNoInteractions(authority);
    input.processSystem();
    verify(authority).respawnPlayerAtTown(7);
    input.requestRespawn(7);
    when(authority.canRespawnPlayer(7)).thenReturn(false);
    input.processSystem();
    verify(authority, times(1)).respawnPlayerAtTown(7);
  }

  @Test void remoteRespawnNeverCallsLocalAuthorityAndRequiresCorpseMode() {
    ClientUnitLifecycleSystem.LocalAuthority authority = mock(ClientUnitLifecycleSystem.LocalAuthority.class);
    ClientUnitLifecycleSystem input = new ClientUnitLifecycleSystem(authority, null);
    input.receiver = mock(ClientNetworkReceiver.class);
    input.network = mock(ClientNetworkSynchronizer.class);
    AuthoritativeUnitLifecycleState store = new AuthoritativeUnitLifecycleState();
    store.commit(7, AuthoritativeUnitLifecycleStateTest.packet(10, (int) Engine.Player.MODE_DT, true, null));
    when(input.receiver.authoritativeLifecycle(7)).thenReturn(store.get(7));
    input.requestRespawn(7); input.processSystem();
    verifyNoInteractions(input.network);
    store.commit(7, AuthoritativeUnitLifecycleStateTest.packet(11, (int) Engine.Player.MODE_DD, true, null));
    when(input.receiver.authoritativeLifecycle(7)).thenReturn(store.get(7));
    input.requestRespawn(7); input.processSystem();
    verify(input.network).requestPlayerRespawn();
    verifyNoInteractions(authority);
  }

  @Test void replicaDeathAndRespawnDoNotSettleInventoryOrPosition() {
    ClientUnitLifecycleSystem.Presentation view = mock(ClientUnitLifecycleSystem.Presentation.class);
    ClientUnitLifecycleSystem system = new ClientUnitLifecycleSystem(null, view);
    World world = new World(new WorldConfigurationBuilder().with(system).build());
    try {
      int player = world.create();
      Position position = world.getMapper(Position.class).create(player);
      position.initializeNativeCell(22, 33);
      world.getMapper(Velocity.class).create(player);
      world.process();
      AuthoritativeUnitLifecycleState store = new AuthoritativeUnitLifecycleState();
      store.commit(player, AuthoritativeUnitLifecycleStateTest.packet(10, (int) Engine.Player.MODE_DT, true, null));
      system.applySnapshot(player, store.get(player));
      assertTrue(world.getMapper(PlayerCorpse.class).has(player));
      assertFalse(world.getMapper(Velocity.class).has(player));
      assertEquals(22f, position.position.x);
      assertTrue(position.hasNativePrecision());
      store.commit(player, AuthoritativeUnitLifecycleStateTest.packet(11, (int) Engine.Player.MODE_NU, false, null));
      system.applySnapshot(player, store.get(player));
      assertFalse(world.getMapper(PlayerCorpse.class).has(player));
      assertEquals(22f, position.position.x);
      verify(view).applyLifecycle(player, store.get(player), true);
    } finally { world.dispose(); }
  }

  @Test void lifecycleProjectionMarksStateReplicaSnapshotOnly() {
    ClientUnitLifecycleSystem.Presentation view = mock(ClientUnitLifecycleSystem.Presentation.class);
    ClientUnitLifecycleSystem system = new ClientUnitLifecycleSystem(null, view);
    World world = new World(new WorldConfigurationBuilder().with(system).build());
    try {
      int player = world.create();
      AuthoritativeUnitLifecycleState store = new AuthoritativeUnitLifecycleState();
      store.commit(player, AuthoritativeUnitLifecycleStateTest.packet(10, (int) Engine.Player.MODE_NU, false, true));
      system.applySnapshot(player, store.get(player));
      assertTrue(world.getMapper(com.riiablo.engine.server.component.UnitStates.class).get(player).snapshotOnly);
      assertFalse(world.getMapper(PlayerCorpse.class).has(player));
    } finally { world.dispose(); }
  }
}
