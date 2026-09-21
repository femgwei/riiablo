package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Objects;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.NativeObjectState;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Object;
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

  @Test
  void normalizesUnactivatedShrineFromTransientOperateModeToNeutral() {
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new CofManager(), new ObjectInitializer())
        .build()
        .register("map", new Map(7, 0)));
    try {
      int entityId = world.create();
      Objects.Entry shrine = new Objects.Entry();
      shrine.Id = 136;
      shrine.Token = "zd";
      shrine.OperateFn = 2;
      shrine.SubClass = Engine.Object.SUBCLASS_SHRINE;
      world.getMapper(Object.class).create(entityId).base = shrine;
      world.getMapper(CofReference.class).create(entityId)
          .set(shrine.Token, Engine.Object.MODE_OP);
      world.getMapper(NativeObjectState.class).create(entityId)
          .set(0, 0, shrine.Id, Engine.Object.MODE_OP, false, false,
              NativePresetObjectResolver.Kind.SHRINE);

      world.process();

      assertEquals(Engine.Object.MODE_NU,
          world.getMapper(CofReference.class).get(entityId).mode);
      assertEquals(Engine.Object.MODE_NU,
          world.getMapper(NativeObjectState.class).get(entityId).currentMode);
    } finally {
      world.dispose();
    }
  }
}
