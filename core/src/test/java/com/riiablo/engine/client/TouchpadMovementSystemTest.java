package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.Actioneer;
import com.riiablo.engine.server.component.Position;
import com.riiablo.map.RenderSystem;
import org.junit.jupiter.api.Test;

class TouchpadMovementSystemTest {
  @Test void captureOnlyBecomesAnIntentOnSimulationTickAndReleaseStopsOnce() {
    World world = new World(new WorldConfigurationBuilder().build());
    try {
      int player = world.create();
      Position position = world.getMapper(Position.class).create(player);
      position.initializeNativeCell(10, 20);
      world.process();
      TouchpadMovementSystem input = new TouchpadMovementSystem();
      input.mPosition = world.getMapper(Position.class);
      input.renderer = mock(RenderSystem.class);
      input.actioneer = mock(Actioneer.class);
      when(input.renderer.getSrc()).thenReturn(player);
      when(input.actioneer.canInterrupt(player)).thenReturn(true);

      input.capture(5f, 0f);
      verifyNoInteractions(input.actioneer);
      input.processSystem();
      verify(input.actioneer).moveTo(player, new Vector2(14f, 20f));
      assertEquals(new Vector2(10, 20), position.position);
      assertTrue(position.hasNativePrecision());

      input.capture(0f, 0f);
      input.processSystem();
      input.processSystem();
      verify(input.actioneer, times(1)).moveTo(player, Engine.INVALID_ENTITY);
      // Invalid captured coordinates cannot become movement results.
      input.capture(Float.NaN, Float.POSITIVE_INFINITY);
      input.processSystem();
      verify(input.actioneer, times(1)).moveTo(eq(player), any(Vector2.class));
      assertTrue(position.hasNativePrecision());
    } finally { world.dispose(); }
  }

  @Test void nonInterruptibleUnitCannotReceiveTouchMovement() {
    World world = new World(new WorldConfigurationBuilder().build());
    try {
      int player = world.create();
      world.getMapper(Position.class).create(player).initializeNativeCell(10, 20);
      world.process();
      TouchpadMovementSystem input = new TouchpadMovementSystem();
      input.mPosition = world.getMapper(Position.class);
      input.renderer = mock(RenderSystem.class);
      input.actioneer = mock(Actioneer.class);
      when(input.renderer.getSrc()).thenReturn(player);
      input.capture(1f, 1f);
      input.processSystem();
      verify(input.actioneer, never()).moveTo(anyInt(), any(Vector2.class));
    } finally { world.dispose(); }
  }
}
