package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.Animation;
import com.riiablo.codec.COF;
import com.riiablo.codec.DCC;
import com.riiablo.engine.Direction;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.presentation.component.AnimationWrapper;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Actual original submissions are the direction oracle; no GPU/pixel assertion. */
class BonePrisonOriginalLayerTest extends RiiabloTest {
  @Test void originalLayerDirectionIsIndependentOfServerFacing() {
    Riiablo.colors = new com.riiablo.Colors();
    JsonValue fixture = new JsonReader().parse(getClass().getResourceAsStream(
        "/original/bone-prison-layers-1.10f.json"));
    assertTrue(fixture.getBoolean("background_verified"));
    assertTrue(fixture.getBoolean("owned_game_closed"));
    assertEquals(fixture.getInt("calls_observed"), fixture.getInt("returns_observed"));
    World world = new World(new WorldConfigurationBuilder().with(new DirectionResolver()).build());
    Map<String, COF> cofs = new HashMap<>();
    Map<String, DCC> assets = new HashMap<>();
    Map<Integer, Integer> entities = new HashMap<>();
    Set<Integer> deathFrames = new HashSet<>();
    int count = 0;
    try {
      for (JsonValue row : fixture.get("samples")) {
        String mode = row.getString("mode_name");
        String resource = row.getString("resource");
        assertEquals("BW", row.getString("token"));
        assertEquals("TR", row.getString("component_name"));
        assertEquals(1, row.getInt("component"));
        COF cof = cofs.get(mode);
        if (cof == null) {
          cof = COF.loadFromFile(Riiablo.mpqs.resolve(
              "data/global/monsters/BW/cof/BW" + mode + "hth.cof"));
          cofs.put(mode, cof);
        }
        DCC dcc = assets.get(resource);
        if (dcc == null) {
          dcc = DCC.loadFromFile(Riiablo.mpqs.resolve(resource));
          assets.put(resource, dcc);
        }
        assertEquals(row.getInt("dcc_directions"), dcc.getNumDirections());
        assertEquals(row.getInt("dcc_frames"), dcc.getNumFramesPerDir());
        assertNotNull(dcc.getFrame(row.getInt("dcc_direction"), row.getInt("frame")));
        if (row.getInt("mode") == Engine.Monster.MODE_DT) deathFrames.add(row.getInt("frame"));
        int id = entities.computeIfAbsent(row.getInt("unit_id"), ignored -> world.create());
        world.getMapper(SummonedPet.class).create(id).skillId = SkillId.BONE_PRISON;
        world.getMapper(CofReference.class).create(id).set("BW", (byte) row.getInt("mode"));
        Angle angle = world.getMapper(Angle.class).create(id);
        Vector2 facing = new Vector2(-row.getInt("cell_offset_x"), -row.getInt("cell_offset_y")).nor();
        angle.set(facing);
        Animation animation = world.getMapper(AnimationWrapper.class).create(id).animation;
        animation.setCOF(cof);
        // Start from another direction to verify immediate correction as
        // well as repeated resolution of stationary server-facing units.
        animation.setDirection(3);
        world.process();
        assertEquals(row.getInt("dcc_direction"), animation.getDirection(),
            "original layer submission " + row.getInt("index"));
        assertTrue(angle.angle.epsilonEquals(facing, .0001f), "preserve server-facing semantics");
        count++;
      }
      assertEquals(fixture.getInt("selections_observed"), count);
      assertEquals(12, entities.size());
      assertEquals(15, deathFrames.size(), "original DT frames 0 through 14 are represented");
    } finally {
      world.dispose();
      for (DCC asset : assets.values()) asset.dispose();
    }
  }

  @Test void boneWallAndOtherSummonsKeepTheirOrdinaryDirectionResolution() {
    Riiablo.colors = new com.riiablo.Colors();
    COF cof = COF.loadFromFile(Riiablo.mpqs.resolve("data/global/monsters/BW/cof/BWNUhth.cof"));
    World world = new World(new WorldConfigurationBuilder().with(new DirectionResolver()).build());
    try {
      int id = world.create();
      world.getMapper(SummonedPet.class).create(id).skillId = SkillId.BONE_WALL;
      world.getMapper(CofReference.class).create(id).set("BW", Engine.Monster.MODE_NU);
      Animation animation = world.getMapper(AnimationWrapper.class).create(id).animation;
      animation.setCOF(cof);
      Angle angle = world.getMapper(Angle.class).create(id);
      for (int direction = 0; direction < 8; direction++) {
        angle.set(new Vector2(1, 0).rotateDeg(direction * 45));
        world.process();
        world.process();
        assertEquals(Direction.radiansToDirection(angle.angle.angleRad(), 4), animation.getDirection());
      }
    } finally {
      world.dispose();
    }
  }
}
