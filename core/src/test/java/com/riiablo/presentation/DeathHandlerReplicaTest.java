package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.CharacterClass;
import com.riiablo.Colors;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.Engine;
import com.riiablo.engine.client.AuthoritativeUnitLifecycleState;
import com.riiablo.engine.client.ClientNetworkReceiver;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.event.ModeChangeEvent;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.net.packet.d2gs.*;
import com.riiablo.save.CharData;
import java.lang.reflect.Method;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class DeathHandlerReplicaTest extends RiiabloTest {
  @Test void replicaEventsCannotSettleDeathAndSnapshotsOnlyProjectAppearance() throws Exception {
    Colors previousColors = Riiablo.colors;
    Riiablo.colors = new Colors();
    DeathHandler death = new DeathHandler();
    World world = new World(new WorldConfigurationBuilder().with(new EventSystem(), new CofManager(), death)
        .build().register("clientContext", ClientGameplayContext.fromGlobals())
        .register("map", mock(com.riiablo.map.Map.class)));
    try {
      CharData data = CharData.obtain().clear().set(0, false, "Replica", Riiablo.AMAZON);
      data.initializeStartItems(CharacterClass.AMAZON.entry());
      data.getStats().base().put(Stat.hitpoints, 37f);
      data.getStats().base().put(Stat.maxhp, 80f);
      data.getStats().reset();
      int player = world.create();
      world.getMapper(Player.class).create(player).data = data;
      world.getMapper(Position.class).create(player).initializeNativeCell(22, 33);
      world.getMapper(AttributesWrapper.class).create(player).attrs = data.getStats();
      world.getMapper(CofReference.class).create(player).set("AM", Engine.Player.MODE_NU);
      world.getMapper(com.riiablo.engine.server.component.Class.class).create(player).type =
          com.riiablo.engine.server.component.Class.Type.PLR;
      UnitStates states = world.getMapper(UnitStates.class).create(player).init(player);
      states.snapshotOnly = true;
      states.stateList.addState(StateId.FREEZE, 100, 1, player);
      world.process();
      java.lang.reflect.Field receiver = DeathHandler.class.getDeclaredField("receiver");
      receiver.setAccessible(true);
      receiver.set(death, mock(ClientNetworkReceiver.class));
      Item weapon = data.getItems().getSlot(BodyLoc.RARM);
      death.onDeathEvent(DeathEvent.obtain(player, player));
      death.onModeChanged(ModeChangeEvent.obtain(player, Engine.Player.MODE_DD));
      death.respawnPlayerAtTown(player);
      assertFalse(world.getMapper(PlayerCorpse.class).has(player));
      assertTrue(states.stateList.hasState(StateId.FREEZE));
      assertSame(weapon, data.getItems().getSlot(BodyLoc.RARM));
      PlayerCorpse marker = world.getMapper(PlayerCorpse.class).create(player);
      marker.playerId = player;
      marker.deathLocation.set(22, 33);
      AuthoritativeUnitLifecycleState.Snapshot dead = snapshot(true);
      death.applyLifecycle(player, dead, false);
      assertTrue(marker.equippedItems.size == 0);
      assertSame(weapon, data.getItems().getSlot(BodyLoc.RARM));
      world.getMapper(PlayerCorpse.class).remove(player);
      death.applyLifecycle(player, snapshot(false), true);
      assertEquals(37f, data.getStats().aggregate().getValue(Stat.hitpoints, 0f));
      assertEquals(new Vector2(22, 33), world.getMapper(Position.class).get(player).position);
      assertTrue(world.getMapper(Position.class).get(player).hasNativePrecision());
      assertTrue(dead.dead());
    } finally { world.dispose(); Riiablo.colors = previousColors; }
  }

  private static AuthoritativeUnitLifecycleState.Snapshot snapshot(boolean dead) throws Exception {
    FlatBufferBuilder b = new FlatBufferBuilder(128);
    int cof = CofReferenceP.createCofReferenceP(b, dead ? Engine.Player.MODE_DD : Engine.Player.MODE_NU, 0, 0);
    int vitals = VitalsP.createVitalsP(b, dead ? 0f : 37f, 80f, 9f, 20f, dead, 3f, 15f);
    int types = EntitySync.createComponentTypeVector(b, new byte[] {ComponentP.CofReferenceP, ComponentP.VitalsP});
    int components = EntitySync.createComponentVector(b, new int[] {cof, vitals});
    b.finish(EntitySync.createEntitySync(b, 100, 2, 0, types, components, 1, 40, 0, 0, 0, -1));
    AuthoritativeUnitLifecycleState store = new AuthoritativeUnitLifecycleState();
    Method commit = AuthoritativeUnitLifecycleState.class.getDeclaredMethod("commit", int.class, EntitySync.class);
    commit.setAccessible(true);
    commit.invoke(store, 0, EntitySync.getRootAsEntitySync(b.dataBuffer()));
    return store.get(0);
  }
}
