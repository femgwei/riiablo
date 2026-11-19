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
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.presentation.component.AnimationWrapper;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Independent original sprite-selection oracle; only GPU upload is suppressed. */
class FrozenOrbOriginalSpriteTest extends RiiabloTest {
  @Test void capturedDccDirectionsAndFramesMatchProductionPresentation() {
    Riiablo.colors = new com.riiablo.Colors();
    JsonValue fixture = new JsonReader().parse(getClass().getResourceAsStream(
        "/original/frozen-orb-sprites-1.10f.json"));
    World world = new World(new WorldConfigurationBuilder().with(new DirectionResolver()).build());
    Map<String, DCC> assets = new HashMap<>();
    Map<Integer, Integer> entities = new HashMap<>();
    Set<Integer> novaUnits = new HashSet<>();
    Set<Integer> novaDirections = new HashSet<>();
    int samples = 0;
    try {
      world.setDelta(1f / 25f);
      for (JsonValue row : fixture.get("samples")) {
        String name = row.getString("missile");
        String celFile = Riiablo.files.Missiles.get(name).CelFile;
        assertTrue(row.getString("cell_name").equalsIgnoreCase(celFile), name + " resource name");
        DCC asset = assets.get(name);
        if (asset == null) {
          DCC decoded = DCC.loadFromFile(Riiablo.mpqs.resolve(
              "data\\global\\missiles\\" + celFile + ".dcc"));
          asset = spy(decoded);
          // Keep real decoded metadata/frames/boxes. No GL context or window.
          doNothing().when(asset).loadDirection(anyInt());
          assets.put(name, asset);
        }
        assertEquals(row.getInt("dcc_directions"), asset.getNumDirections());
        assertEquals(row.getInt("dcc_frames"), asset.getNumFramesPerDir());
        int nativeId = row.getInt("unit_id");
        Integer id = entities.get(nativeId);
        if (id == null) {
          id = world.create();
          entities.put(nativeId, id);
          Missile missile = world.getMapper(Missile.class).create(id);
          missile.missile = Riiablo.files.Missiles.get(name);
          missile.frozenOrbNova = "frozenorbnova".equals(name);
          Animation animation = world.getMapper(AnimationWrapper.class).create(id).animation;
          animation.edit().layer(asset).build();
          animation.setMode(Animation.Mode.LOOP);
          animation.setFrameDelta(MissileLoader.nativeFrameDelta(null, missile));
        }
        Angle angle = world.getMapper(Angle.class).create(id);
        angle.set(new Vector2(row.getInt("velocity_x"), row.getInt("velocity_y")).nor());
        world.process();
        // Ordinary missiles use the existing two-pass direction debounce.
        // Nova must match immediately on the first pass after every turn.
        if (!"frozenorbnova".equals(name)) world.process();
        Animation animation = world.getMapper(AnimationWrapper.class).get(id).animation;
        String context = name + " original draw " + row.getInt("index");
        assertEquals(row.getInt("dcc_direction"), animation.getDirection(), context);
        Missile missile = world.getMapper(Missile.class).get(id);
        missile.nativeFrame = row.getInt("animation_cursor") >> 8;
        MissileLoader.initializePlayback(missile, missile.missile, animation, 0);
        assertEquals(row.getInt("frame"), animation.getFrame(), context);
        assertNotNull(asset.getFrame(animation.getDirection(), animation.getFrame()), context);
        if (missile.frozenOrbNova) {
          novaUnits.add(nativeId);
          novaDirections.add(animation.getDirection());
        }
        samples++;
      }
      assertEquals(fixture.getInt("selections_observed"), samples);
      assertEquals(16, novaUnits.size(), "all original final-ring units");
      assertEquals(16, novaDirections.size(), "all DCC directions covered by the final ring");
    } finally {
      world.dispose();
      for (DCC asset : assets.values()) asset.dispose();
    }
  }
}
