package com.riiablo.engine.server.object;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Objects;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.ObjectInteractor;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.NativeObjectState;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.event.NativeTrapInteractionEvent;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.Location;
import com.riiablo.item.StoreLoc;
import com.riiablo.map.Map;
import com.riiablo.map.NativePresetObjectResolver;
import com.riiablo.save.CharData;

import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

class NativeTrappedContainerIntegrationTest extends RiiabloTest {
  @Test
  void emitsTrapOnlyForFirstSuccessfulContainerOpen() {
    TrapProbe probe = new TrapProbe();
    ObjectInteractor interactor = new ObjectInteractor();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), probe, new CofManager(), interactor)
        .build()
        .register("map", new Map(7, 0)));
    try {
      int entityId = world.create();
      Objects.Entry urn = new Objects.Entry();
      urn.Id = 5;
      urn.InitFn = 2;
      urn.OperateFn = 3;
      world.getMapper(com.riiablo.engine.server.component.Object.class)
          .create(entityId).base = urn;
      world.getMapper(CofReference.class).create(entityId).mode = Engine.Object.MODE_NU;
      NativeObjectState state = world.getMapper(NativeObjectState.class)
          .create(entityId).set(1, 5, 5, Engine.Object.MODE_NU, false, false,
              NativePresetObjectResolver.Kind.ORDINARY);
      state.persistInteractType(3);
      world.getMapper(Interactable.class).create(entityId).set(4f, interactor);
      world.process();

      interactor.interact(10, entityId);
      assertEquals(1, probe.events);
      assertEquals(3, probe.trapType);
      assertTrue(state.opened);
      assertFalse(world.getMapper(Interactable.class).has(entityId));

      interactor.interact(10, entityId);
      assertEquals(1, probe.events);
    } finally {
      world.dispose();
    }
  }

  @Test
  void lockedContainerStaysClosedUntilPlayerProvidesKey() {
    TrapProbe probe = new TrapProbe();
    ObjectInteractor interactor = new ObjectInteractor();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), probe, new CofManager(), interactor)
        .build()
        .register("map", new Map(7, 0)));
    try {
      CharData data = CharData.obtain(
          Riiablo.NORMAL, false, "locked-test", (byte) Riiablo.AMAZON);
      int playerId = world.create();
      world.getMapper(Player.class).create(playerId).data = data;

      int entityId = world.create();
      Objects.Entry chest = new Objects.Entry();
      chest.Id = 5;
      chest.InitFn = 3;
      chest.OperateFn = 4;
      world.getMapper(com.riiablo.engine.server.component.Object.class)
          .create(entityId).base = chest;
      world.getMapper(CofReference.class).create(entityId).mode = Engine.Object.MODE_NU;
      NativeObjectState state = world.getMapper(NativeObjectState.class)
          .create(entityId).set(1, 5, 5, Engine.Object.MODE_NU, false, false,
              NativePresetObjectResolver.Kind.ORDINARY);
      state.persistInteractType(0x83);
      world.getMapper(Interactable.class).create(entityId).set(4f, interactor);
      world.process();

      interactor.interact(playerId, entityId);
      assertFalse(state.opened);
      assertTrue(world.getMapper(Interactable.class).has(entityId));
      assertEquals(0, probe.events);

      Item key = new ItemGenerator().generate("key");
      key.location = Location.STORED;
      key.storeLoc = StoreLoc.INVENTORY;
      key.attrs.base().put(Stat.quantity, 1);
      key.attrs.reset();
      data.getItems().add(key);

      interactor.interact(playerId, entityId);
      assertTrue(state.opened);
      assertFalse(world.getMapper(Interactable.class).has(entityId));
      assertTrue(data.getItems().getItems().isEmpty());
      assertEquals(1, probe.events);
      assertEquals(3, probe.trapType);
    } finally {
      world.dispose();
    }
  }

  private static final class TrapProbe extends PassiveSystem {
    int events;
    int trapType;

    @Subscribe
    public void onTrap(NativeTrapInteractionEvent event) {
      events++;
      trapType = event.trapType;
    }
  }
}
