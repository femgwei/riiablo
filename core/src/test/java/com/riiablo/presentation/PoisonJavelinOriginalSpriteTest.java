package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.Animation;
import com.riiablo.codec.DCC;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.presentation.component.AnimationWrapper;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Replays actual javelin/cloud/smoke DCC choices; GPU texture upload is suppressed. */
class PoisonJavelinOriginalSpriteTest extends RiiabloTest {
  @Test void originalJavelinCloudAndSmokeSelectionsMatchProductionPresentation() {
    Riiablo.colors = new com.riiablo.Colors();
    JsonValue fixture = new JsonReader().parse(getClass().getResourceAsStream("/original/poison-javelin-1.10f.json"));
    replaySprites(fixture);
  }

  @Test void cardinalAndDiagonalBatchMatchesOriginalDccSelections() {
    JsonValue batch = new JsonReader().parse(getClass().getResourceAsStream(
        "/original/poison-javelin-directions-1.10f.json"));
    assertEquals(2, batch.get("casts").size);
    for (JsonValue cast : batch.get("casts")) replaySprites(cast);
  }

  private void replaySprites(JsonValue fixture) {
    Riiablo.colors = new com.riiablo.Colors();
    assertTrue(fixture.getBoolean("background_verified"));
    assertTrue(fixture.getBoolean("owned_game_closed"));
    JsonValue cast = fixture.get("identity");
    Vector2 facing = new Vector2(cast.getInt("target_x") - cast.getInt("x"),
        cast.getInt("target_y") - cast.getInt("y")).nor();
    World world = new World(new WorldConfigurationBuilder().with(new DirectionResolver(), new AnimationStepper()).build());
    world.setDelta(1f / 25f);
    Map<String, DCC> assets = new HashMap<>();
    Set<Integer> trails = new HashSet<>();
    int count = 0;
    try {
      for (JsonValue sample : fixture.get("samples")) {
        String name = sample.getString("missile");
        Missiles.Entry row = Riiablo.files.Missiles.get(name);
        assertTrue(row.CelFile.equalsIgnoreCase(sample.getString("cell_name")));
        DCC dcc = assets.get(name);
        if (dcc == null) {
          dcc = spy(DCC.loadFromFile(Riiablo.mpqs.resolve("data/global/missiles/" + row.CelFile + ".dcc")));
          doNothing().when(dcc).loadDirection(anyInt());
          assets.put(name, dcc);
        }
        assertEquals(sample.getInt("dcc_directions"), dcc.getNumDirections());
        assertEquals(sample.getInt("dcc_frames"), dcc.getNumFramesPerDir());
        int id = world.create();
        Missile missile = world.getMapper(Missile.class).create(id);
        missile.missile = row;
        // Supply production birth facing; poison-specific direction mapping
        // must retain the native default cloud direction and smoke quadrants.
        world.getMapper(Angle.class).create(id).set("poisonjav".equals(name) ? facing
            : "poisonjavcloud".equals(name) ? Vector2.X
            : new Vector2(sample.getInt("velocity_x"), sample.getInt("velocity_y")).nor());
        Animation animation = world.getMapper(AnimationWrapper.class).create(id).animation;
        animation.edit().layer(dcc).build();
        animation.setMode(row.LoopAnim > 0 ? Animation.Mode.LOOP : Animation.Mode.CLAMP);
        animation.setFrameDelta(MissileLoader.nativeFrameDelta(null, missile));
        if (row.SubLoop > 0 && animation.isLooping()) animation.setSubLoop(row.SubStart, row.SubStop);
        missile.nativeFrame = sample.getInt("native_age");
        missile.nativeLifetimeFrames = "poisonjavcloud".equals(name) ? 60 : 25;
        MissileLoader.initializePlayback(missile, row, animation, row.RandStart);
        // Simulate publication at this age, then two presentation passes. The
        // animation system must not add an extra frame to the simulation clock.
        world.process();
        world.process();
        assertEquals(sample.getInt("dcc_direction"), animation.getDirection(), name + " direction");
        assertEquals(sample.getInt("frame"), animation.getFrame(), name + " frame at age " + missile.nativeFrame);
        assertNotNull(dcc.getFrame(animation.getDirection(), animation.getFrame()));
        if ("poisonjavcloud".equals(name)) trails.add(sample.getInt("unit_id"));
        world.delete(id);
        count++;
      }
      assertEquals(fixture.getInt("selections_observed"), count);
      assertEquals(fixture.get("identity").getInt("direction_index", 0) == 4 ? 18 : 23, trails.size());
    } finally {
      world.dispose();
      for (DCC asset : assets.values()) asset.dispose();
    }
  }
}
