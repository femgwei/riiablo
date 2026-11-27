package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.*;
import com.riiablo.engine.server.ai.NecroPet;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.component.Class;
import com.riiablo.map.Box2DPhysics;
import com.riiablo.map.Map;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Exercises the local factory entry point after DeathHandler's DD removals. */
class ReviveLocalMovementTest extends RiiabloTest {
  @Test void playerReviveRestoresPhysicsBodyAndAiCanFollowOwner() {
    World previous = Riiablo.engine;
    com.riiablo.Colors previousColors = Riiablo.colors;
    Riiablo.colors = new com.riiablo.Colors();
    ClientEntityFactory factory = new ClientEntityFactory();
    Map map = mock(Map.class);
    Pathfinder pathfinder = mock(Pathfinder.class);
    when(pathfinder.findPath(anyInt(), any(Vector2.class), anyBoolean(), anyInt())).thenReturn(true);
    DynamicUnitCollisionSystem collision = mock(DynamicUnitCollisionSystem.class);
    when(collision.tryMove(anyInt(), anyFloat(), anyFloat())).thenReturn(true);
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new CofManager(), pathfinder, factory, collision,
            new Box2DSynchronizerPre(), new Box2DPhysics(1f / 25f), new Box2DSynchronizerPost(),
            mock(MenuManager.class), mock(DialogManager.class), mock(OverlayManager.class),
            mock(ObjectInteractor.class), mock(WarpInteractor.class), mock(ItemInteractor.class))
        .build().register("map", map).register("factory", factory)
        .register("townPortalRegistry", mock(com.riiablo.engine.server.portal.TownPortalRegistry.class))
        .register("iso", new com.riiablo.camera.IsometricCamera())
        .register("clientContext", ClientGameplayContext.fromGlobals()));
    Riiablo.engine = world;
    try {
      int owner = world.create();
      world.getMapper(Player.class).create(owner);
      world.getMapper(Position.class).create(owner).position.set(20, 10);
      int id = world.create();
      world.getMapper(Monster.class).create(id).set(Riiablo.files.monstats.get("fallen1"),
          Riiablo.files.monstats2.get(Riiablo.files.monstats.get("fallen1").MonStatsEx));
      world.getMapper(Position.class).create(id).position.set(10, 10);
      world.getMapper(Class.class).create(id).type = Class.Type.MON;
      world.getMapper(Size.class).create(id).size = 1;
      world.getMapper(Angle.class).create(id);
      world.getMapper(CofReference.class).create(id).set("FA", Engine.Monster.MODE_DD);
      Attributes attrs = Attributes.obtainStandard();
      attrs.base().put(Stat.hitpoints, 0);
      attrs.base().put(Stat.maxhp, 40);
      attrs.reset();
      world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
      world.getMapper(Corpse.class).create(id).reset(Corpse.DEFAULT_DURATION, true);
      world.process();
      assertFalse(world.getMapper(Box2DBody.class).has(id));
      assertTrue(factory.reviveMonster(id, owner));
      world.getMapper(SummonedPet.class).create(id).set(owner, "revive", 95, 5, false, 4500);
      world.process();
      assertTrue(world.getMapper(AIWrapper.class).get(id).ai instanceof NecroPet);
      assertNotNull(world.getMapper(Box2DBody.class).get(id).body,
          "Revive must rebuild the body removed in DD");
      assertTrue(world.getMapper(com.riiablo.presentation.component.AnimationWrapper.class).has(id));
      assertEquals(Engine.Monster.MODE_NU, world.getMapper(CofReference.class).get(id).mode);
      NecroPet ai = (NecroPet) world.getMapper(AIWrapper.class).get(id).ai;
      // Bound the path stub to a clear straight line; use production velocity
      // synchronization, Box2D stepping and committed positions thereafter.
      when(pathfinder.findPath(eq(id), any(Vector2.class), anyBoolean(), anyInt()))
          .thenAnswer(call -> {
            Vector2 target = call.getArgument(1);
            world.getMapper(Velocity.class).get(id).velocity.set(target)
                .sub(world.getMapper(Position.class).get(id).position).nor().scl(2f);
            return true;
          });
      ai.useSkillBranch(false).update(1f / 25f);
      assertEquals("FOLLOW", ai.getState());
      verify(pathfinder).findPath(eq(id), eq(new Vector2(20, 10)), eq(false), eq(owner));

      com.badlogic.gdx.physics.box2d.Body body = world.getMapper(Box2DBody.class).get(id).body;
      Vector2 pathVelocity = new Vector2(world.getMapper(Velocity.class).get(id).velocity);
      assertTrue(pathVelocity.x > 0f);
      for (int tick = 0; tick < 5; tick++) {
        // The path stub republishes its requested velocity each tick, as
        // Pathfinder does before physics overwrites it with displacement.
        world.getMapper(Velocity.class).get(id).velocity.set(pathVelocity);
        world.setDelta(1f / 25f); world.process();
      }
      assertTrue(body.getPosition().x > 10f, "restored body must advance");
      assertEquals(body.getPosition(), world.getMapper(Position.class).get(id).position);
    } finally {
      world.dispose(); map.dispose(); Riiablo.engine = previous; Riiablo.colors = previousColors;
    }
  }
}
