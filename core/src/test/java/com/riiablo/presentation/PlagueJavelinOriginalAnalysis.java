package com.riiablo.presentation;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.Animation;
import com.riiablo.codec.DCC;
import com.riiablo.engine.server.*;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.presentation.component.AnimationWrapper;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.mostlyoriginal.api.event.common.EventSystem;

/** Diagnostic companion to the original-fixture tests; no GPU rendering. */
public final class PlagueJavelinOriginalAnalysis {
  public static void main(String[] args) throws Exception {
    RiiabloTest.setup();
    try {
      Riiablo.colors = new com.riiablo.Colors();
      JsonValue original = new JsonReader().parse(Files.readString(Path.of(args[0])));
      Map<String, Object> report = new LinkedHashMap<>();
      report.put("scope", "Production simulation and warm DCC selection after repair; no GPU");
      report.put("source_run_id", original.getString("run_id"));
      Map<String, Object> server = simulation(original, false);
      report.put("server", server);
      report.put("local_client", simulation(original, true));
      report.put("sprite_selection", sprites(original, server));
      StringWriter text = new StringWriter();
      JsonWriter writer = new JsonWriter(text);
      writer.setOutputType(JsonWriter.OutputType.json);
      write(writer, report);
      writer.flush();
      Files.writeString(Path.of(args[1]), text + "\n");
    } finally { RiiabloTest.teardown(); }
  }

  private static Map<String, Object> simulation(JsonValue original, boolean presentation) {
    ServerEntityFactory factory = new ServerEntityFactory();
    WorldConfigurationBuilder builder = new WorldConfigurationBuilder().with(
        new EventSystem(), new ItemManager(), new ObjectInteractor(), new WarpInteractor(),
        new ItemInteractor(), factory, new MissileCollisionSystem());
    if (presentation) builder.with(new OverlayManager(), new MissileImpactPresentationSystem());
    World world = new World(builder.build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0))
        .register("townPortalRegistry", new com.riiablo.engine.server.portal.TownPortalRegistry())
        .register("clientContext", ClientGameplayContext.fromGlobals()));
    try {
      JsonValue nativeHead = null;
      for (JsonValue unit : original.get("units"))
        if ("server".equals(unit.getString("side")) && "plaguejavelin".equals(unit.getString("missile")))
          nativeHead = unit.get("states").child;
      if (nativeHead == null) throw new IllegalArgumentException("Missing native head");
      int owner = world.create();
      world.getMapper(Position.class).create(owner).position.set(
          nativeHead.getInt("x_fixed") / 65536f - .5f,
          nativeHead.getInt("y_fixed") / 65536f - .5f);
      int id = factory.createMissile(Riiablo.files.Missiles.get("plaguejavelin"), Vector2.X,
          new Vector2(nativeHead.getInt("x_fixed") / 65536f - .5f,
              nativeHead.getInt("y_fixed") / 65536f - .5f), owner, 1);
      Missile head = world.getMapper(Missile.class).get(id);
      head.skillId = 25;
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("head_world_velocity_per_second", vector(world.getMapper(Velocity.class).get(id).velocity));
      result.put("head_lifetime", head.nativeLifetimeFrames);
      result.put("native_head_fp16_velocity_per_tick", List.of(nativeHead.getInt("velocity_x"), nativeHead.getInt("velocity_y")));
      world.setDelta(1f / 25f);
      List<Object> trails = new ArrayList<>(), burst = new ArrayList<>();
      for (int tick = 1; tick <= 15; tick++) {
        world.process();
        if (tick == 14 || tick == 15) {
          com.artemis.utils.IntBag ids = world.getAspectSubscriptionManager()
              .get(Aspect.all(Missile.class, Position.class, Velocity.class)).getEntities();
          for (int i = 0; i < ids.size(); i++) {
            int child = ids.get(i);
            Missile m = world.getMapper(Missile.class).get(child);
            if (!"plaguejavcloud".equals(m.missile.Missile)) continue;
            if (tick == 14 && !m.presentationOnly || tick == 15 && m.presentationOnly) continue;
            Vector2 v = world.getMapper(Velocity.class).get(child).velocity;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("age", m.nativeFrame);
            row.put("position", vector(world.getMapper(Position.class).get(child).position));
            row.put("world_velocity_per_second", vector(v));
            row.put("fp16_velocity_per_tick", List.of(Math.round(v.x * 65536f / 25f), Math.round(v.y * 65536f / 25f)));
            row.put("angle", vector(world.getMapper(Angle.class).get(child).angle));
            row.put("lifetime", m.nativeLifetimeFrames);
            (tick == 14 ? trails : burst).add(row);
          }
        }
      }
      result.put("trail_at_tick14", trails);
      result.put("burst_at_tick15", burst);
      result.put("trail_dcc_hidden_by_loader_policy", false);
      return result;
    } finally { world.dispose(); }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> sprites(JsonValue original, Map<String, Object> server) {
    Map<String, Vector2> ringAngles = new HashMap<>();
    for (Object child : (List<?>) server.get("burst_at_tick15")) {
      Map<String, Object> cloud = (Map<String, Object>) child;
      List<Number> velocity = (List<Number>) cloud.get("fp16_velocity_per_tick");
      List<Number> angle = (List<Number>) cloud.get("angle");
      ringAngles.put(velocity.get(0).intValue() + ":" + velocity.get(1).intValue(),
          new Vector2(angle.get(0).floatValue(), angle.get(1).floatValue()));
    }
    Map<Integer, List<JsonValue>> samples = new TreeMap<>();
    Map<Integer, JsonValue> units = new HashMap<>();
    for (JsonValue unit : original.get("units")) if ("client".equals(unit.getString("side")))
      units.put(unit.getInt("unit_id"), unit);
    for (JsonValue sample : original.get("samples"))
      samples.computeIfAbsent(sample.getInt("unit_id"), ignored -> new ArrayList<>()).add(sample);
    Map<String, DCC> assets = new HashMap<>();
    Map<String, Object> result = new LinkedHashMap<>();
    Map<String, int[]> counts = new TreeMap<>();
    List<Object> examples = new ArrayList<>();
    World world = new World(new WorldConfigurationBuilder().with(new DirectionResolver(), new AnimationStepper()).build());
    world.setDelta(1f / 25f);
    try {
      for (Map.Entry<Integer, List<JsonValue>> entry : samples.entrySet()) {
        JsonValue unit = units.get(entry.getKey()), first = unit.get("states").child;
        String name = unit.getString("missile");
        String group = name.equals("plaguejavcloud") ? (first.getInt("total_frames") == 60 ? "trail" : "burst") : name;
        com.riiablo.codec.excel.Missiles.Entry row = Riiablo.files.Missiles.get(name);
        DCC dcc = assets.get(name);
        if (dcc == null) {
          dcc = spy(DCC.loadFromFile(Riiablo.mpqs.resolve("data/global/missiles/" + row.CelFile + ".dcc")));
          doNothing().when(dcc).loadDirection(anyInt());
          assets.put(name, dcc);
        }
        int id = world.create();
        Missile m = world.getMapper(Missile.class).create(id);
        m.missile = row;
        m.presentationOnly = group.equals("trail");
        m.poisonCloudTraveling = group.equals("burst");
        m.nativeLifetimeFrames = first.getInt("total_frames");
        Vector2 angle = group.equals("burst")
            ? ringAngles.get(first.getInt("velocity_x") + ":" + first.getInt("velocity_y"))
            : name.equals("poisonpuff")
                ? new Vector2(first.getInt("velocity_x"), first.getInt("velocity_y")).nor() : Vector2.X;
        if (angle == null) throw new IllegalStateException("Original cloud missing production path");
        world.getMapper(Angle.class).create(id).set(angle);
        Animation a = world.getMapper(AnimationWrapper.class).create(id).animation;
        a.edit().layer(dcc).build();
        a.setMode(row.LoopAnim > 0 ? Animation.Mode.LOOP : Animation.Mode.CLAMP);
        a.setFrameDelta(MissileLoader.nativeFrameDelta(null, m));
        if (row.SubLoop > 0 && a.isLooping()) a.setSubLoop(row.SubStart, row.SubStop);
        MissileLoader.initializePlayback(m, row, a, row.RandStart);
        // DirectionResolver also runs on the birth frame. Without this pass,
        // age-zero smoke samples incorrectly retain Animation's default direction.
        world.process();
        int age = 0;
        for (JsonValue sample : entry.getValue()) {
          while (age < sample.getInt("native_age")) { m.nativeFrame = ++age; world.process(); }
          int[] stats = counts.computeIfAbsent(group, ignored -> new int[3]);
          stats[0]++;
          boolean frame = a.getFrame() != sample.getInt("frame");
          boolean direction = a.getDirection() != sample.getInt("dcc_direction");
          if (frame) stats[1]++;
          if (direction) stats[2]++;
          if ((frame || direction) && examples.size() < 12)
            examples.add(Map.of("group", group, "age", age, "native_frame", sample.getInt("frame"),
                "riiablo_frame", a.getFrame(), "native_direction", sample.getInt("dcc_direction"),
                "riiablo_direction", a.getDirection()));
        }
        world.delete(id);
        world.process();
      }
      for (Map.Entry<String, int[]> entry : counts.entrySet()) {
        int[] c = entry.getValue();
        result.put(entry.getKey(), Map.of("samples", c[0], "frame_mismatches", c[1], "direction_mismatches", c[2]));
      }
      result.put("examples", examples);
      result.put("trail_animation_forced_for_diagnosis", false);
      result.put("scope", "Warm animation from birth with production ring facing; loader visibility tested separately. Not GPU pixels.");
      return result;
    } finally {
      world.dispose();
      for (DCC dcc : assets.values()) dcc.dispose();
    }
  }

  private static List<Float> vector(Vector2 v) { return List.of(v.x, v.y); }
  private static void write(JsonWriter w, Object value) throws Exception {
    if (value instanceof Map) {
      Map<?, ?> map = (Map<?, ?>) value;
      w.object();
      for (Map.Entry<?, ?> entry : map.entrySet()) { w.name(entry.getKey().toString()); write(w, entry.getValue()); }
      w.pop();
    } else if (value instanceof Iterable) {
      Iterable<?> list = (Iterable<?>) value;
      w.array(); for (Object item : list) write(w, item); w.pop();
    } else w.value(value);
  }
}
