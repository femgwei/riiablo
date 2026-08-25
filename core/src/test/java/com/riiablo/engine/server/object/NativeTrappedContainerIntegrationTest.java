package com.riiablo.engine.server.object;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Objects;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.ObjectInteractor;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.NativeObjectState;
import com.riiablo.engine.server.event.NativeTrapInteractionEvent;
import com.riiablo.map.Map;
import com.riiablo.map.NativePresetObjectResolver;

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
