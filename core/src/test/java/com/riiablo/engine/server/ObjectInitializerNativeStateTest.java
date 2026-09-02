package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Objects;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.NativeObjectState;
import com.riiablo.map.Map;
import com.riiablo.map.NativePresetObjectResolver;

import net.mostlyoriginal.api.event.common.EventSystem;

class ObjectInitializerNativeStateTest extends RiiabloTest {
  @Test
  void removesGenericInteractionFromStaticArcaneSymbol() {
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new CofManager(), new ObjectInitializer())
        .build()
        .register("map", new Map(7, 0)));
    try {
      int entityId = world.create();
      Objects.Entry symbol = new Objects.Entry();
      symbol.Id = 307;
      symbol.InitFn = 0;
      symbol.OperateFn = 0;
      world.getMapper(com.riiablo.engine.server.component.Object.class)
          .create(entityId).base = symbol;
      world.getMapper(NativeObjectState.class).create(entityId)
          .set(0, 582, 307, Engine.Object.MODE_NU, false, false,
              NativePresetObjectResolver.Kind.ARCANE_SYMBOL);
      world.getMapper(Interactable.class).create(entityId);

      world.process();

      assertFalse(world.getMapper(Interactable.class).has(entityId));
    } finally {
      world.dispose();
    }
  }
}
