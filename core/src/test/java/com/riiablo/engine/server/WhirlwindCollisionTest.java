package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.riiablo.engine.server.component.Box2DBody;
import org.junit.jupiter.api.Test;

class WhirlwindCollisionTest {
  @Test void sweepClipsConstructionAtObjectsButMovementOnlyAtStaticBarriers() {
    com.riiablo.map.Map.Zone zone = new com.riiablo.map.Map.Zone() {
      @Override public int staticFlagsWorld(int x, int y) { return x >= 4 ? 1 : 0; }
      @Override public int objectCollisionReferences(int x, int y) { return x == 2 ? 1 : 0; }
      @Override public int doorCollisionReferences(int x, int y) { return 0; }
    };
    com.riiablo.map.Map map = new com.riiablo.map.Map(0, 0) {
      @Override public Zone getZone(int x, int y) { return zone; }
    };
    Vector2 last = new Vector2();
    assertTrue(WhirlwindCollision.cast(map, new Vector2(), new Vector2(8, 0), 1, true, last));
    assertTrue(last.x < 2);
    assertTrue(WhirlwindCollision.cast(map, new Vector2(), new Vector2(8, 0), 1, false, last));
    assertTrue(last.x > 2 && last.x < 4);
    assertFalse(WhirlwindCollision.cast(map, new Vector2(), new Vector2(1, 0), 1, true, last));
    assertEquals(1f, last.x);
  }

  @Test void constructionAndMovementUseNativeStageMasks() {
    assertTrue(WhirlwindCollision.blocks(1, 0, 0, true));
    assertTrue(WhirlwindCollision.blocks(1, 0, 0, false));
    assertTrue(WhirlwindCollision.blocks(0, 1, 0, true));
    assertTrue(WhirlwindCollision.blocks(0, 1, 1, true));
    assertFalse(WhirlwindCollision.blocks(0, 1, 1, false));
    assertFalse(WhirlwindCollision.blocks(4, 0, 0, true));
    assertTrue(WhirlwindCollision.blocks(4, 0, 0, false));
    assertTrue(WhirlwindCollision.blocks(0x20, 0, 0, false));
    assertFalse(WhirlwindCollision.blocks(8, 0, 0, true));
    assertFalse(WhirlwindCollision.blocks(8, 0, 0, false));
  }

  @Test void physicsSensorsRestoreTheirOriginalValuesAfterSkillMove() {
    Box2D.init();
    World physics = new World(new Vector2(), true);
    CircleShape shape = new CircleShape();
    try {
      shape.setRadius(0.5f);
      Box2DBody wrapper = new Box2DBody();
      wrapper.body = physics.createBody(new BodyDef());
      Fixture solid = wrapper.body.createFixture(shape, 1);
      Fixture sensor = wrapper.body.createFixture(shape, 1);
      sensor.setSensor(true);
      Box2DSynchronizerPre.applySkillMoveCollision(wrapper, true);
      Box2DSynchronizerPre.applySkillMoveCollision(wrapper, true);
      assertTrue(solid.isSensor());
      assertTrue(sensor.isSensor());
      Box2DSynchronizerPre.applySkillMoveCollision(wrapper, false);
      assertFalse(solid.isSensor());
      assertTrue(sensor.isSensor());
      assertEquals(0, wrapper.skillMoveFixtures.size);
      assertEquals(0, wrapper.skillMoveSensors.size);
    } finally { shape.dispose(); physics.dispose(); }
  }
}
