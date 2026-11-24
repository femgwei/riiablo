package com.riiablo.presentation;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.DCC;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.server.*;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.presentation.component.AnimationWrapper;
import com.riiablo.presentation.component.ReferenceElementalEffect;
import com.riiablo.save.CharData;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.mostlyoriginal.api.event.common.EventSystem;

/** Records real production births/poses; differences are reported, never asserted away. */
public final class DruidSurfaceOriginalAnalysis {
  public static void main(String[] args) throws Exception {
    RiiabloTest.setup();
    try {
      Path directory = Path.of(args[0]);
      for (String name : new String[] {"fissure", "volcano"}) {
        JsonValue original = new JsonReader().parse(Files.readString(directory.resolve("druid-" + name + "-replay-input-1.10f.json")));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source_run_id", original.getString("run_id"));
        result.put("skill_id", original.getInt("skill_id"));
        result.put("fixture_sha256", original.get("fixture").getString("sha256"));
        result.put("scope", "Recorded skill points, actual production systems and MPQ DCC headers; no target/map collision, caster sequence or GPU");
        Integer targetSeed = "volcano".equals(name) ? new JsonReader().parse(
            DruidSurfaceOriginalAnalysis.class.getResourceAsStream("/original/volcano-flight-1.10f.json"))
            .get("cases").get("draw").getInt("controller_target_seed") : null;
        result.put("conditioned_controller_target_seed", targetSeed);
        result.put("production", simulate(original, 0, targetSeed));
        StringWriter text = new StringWriter(); JsonWriter writer = new JsonWriter(text);
        writer.setOutputType(JsonWriter.OutputType.json); write(writer, result); writer.flush();
        Files.writeString(directory.resolve("druid-" + name + "-production-1.10f.json"), text + "\n");
      }
    } finally { RiiabloTest.teardown(); }
  }

  static Map<String, Object> simulate(JsonValue original) {
    return simulate(original, 0);
  }

  static Map<String, Object> simulate(JsonValue original, int loadAfterTick) {
    return simulate(original, loadAfterTick, null);
  }

  static Map<String, Object> simulate(JsonValue original, int loadAfterTick, Integer targetSeed) {
    Riiablo.colors = new com.riiablo.Colors();
    AssetManager saved = Riiablo.assets;
    AssetManager manager = Riiablo.assets = mock(AssetManager.class);
    Map<String, DCC> dccs = new HashMap<>();
    boolean wind = original.getInt("skill_id") == 240 || original.getInt("skill_id") == 245;
    String[] resources = wind ? new String[] {"twister", "tornado"} : new String[] {
        "erruption crack 1", "erruption smoke 1", "volcano", "volcano overlay fire",
        "volcano debris 2", "volcano explosion", "volcano small fire"};
    for (String name : resources) {
      Missiles.Entry row = Riiablo.files.Missiles.get(name);
      DCC dcc = spy(DCC.loadFromFile(Riiablo.mpqs.resolve("data/global/missiles/" + row.CelFile + ".dcc")));
      doNothing().when(dcc).loadDirection(anyInt());
      dccs.put(row.CelFile.replace('\\', '/').toLowerCase(Locale.ROOT), dcc);
    }
    when(manager.contains(anyString())).thenReturn(true);
    final int[] elapsedTick = {0};
    when(manager.isLoaded(any(AssetDescriptor.class))).thenAnswer(call -> {
      if (elapsedTick[0] < loadAfterTick) return false;
      String path = ((AssetDescriptor<?>) call.getArgument(0)).fileName.replace('\\', '/').toLowerCase(Locale.ROOT);
      for (String key : dccs.keySet()) if (path.endsWith("/" + key + ".dcc")) return true;
      return false; // Invisible 'null' CelFile controllers have no resource.
    });
    when(manager.get(any(AssetDescriptor.class))).thenAnswer(call -> {
      String path = ((AssetDescriptor<?>) call.getArgument(0)).fileName.replace('\\', '/').toLowerCase(Locale.ROOT);
      for (Map.Entry<String, DCC> dcc : dccs.entrySet()) if (path.endsWith("/" + dcc.getKey() + ".dcc")) return dcc.getValue();
      throw new IllegalArgumentException(path);
    });
    RecordingFactory factory = new RecordingFactory();
    factory.targetSeed = targetSeed;
    World world = new World(new WorldConfigurationBuilder().with(new EventSystem(),
        new ItemManager(), new ObjectInteractor(), new WarpInteractor(), new ItemInteractor(),
        new MissileCollisionSystem(), new OverlayManager(), new ReferenceElementalPresentationSystem(),
        new DruidWindPresentationSystem(),
        new MissileImpactPresentationSystem(), new ServerSkillSystem(true), factory,
        new MissileLoader(), new AnimationStepper(), new DirectionResolver()).build()
        .register("factory", factory).register("map", new com.riiablo.map.Map(0, 0))
        .register("townPortalRegistry", new com.riiablo.engine.server.portal.TownPortalRegistry())
        .register("clientContext", ClientGameplayContext.fromGlobals()));
    try {
      int skillId = original.getInt("skill_id"), owner = world.create();
      CharData data = CharData.createRemote("surface-probe", Riiablo.DRUID);
      Map<String, Integer> points = DruidReplayLoadout.apply(data, original, skillId);
      world.getMapper(Player.class).create(owner).data = data;
      JsonValue cast = original.get("identity");
      world.getMapper(Position.class).create(owner).position.set(cast.getInt("x"), cast.getInt("y"));
      Attributes attrs = Attributes.obtainStandard();
      for (short stat : new short[] {Stat.level, Stat.hitpoints, Stat.maxhp, Stat.mana, Stat.maxmana}) attrs.base().put(stat, stat == Stat.level ? 1 : 1000);
      attrs.reset(); world.getMapper(AttributesWrapper.class).create(owner).attrs = attrs;
      com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(skillId);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(owner, skillId, -1,
          new Vector2(cast.getInt("target_x"), cast.getInt("target_y")), skill.srvdofunc, skill.cltdofunc));
      world.setDelta(1f / 25f);
      for (int tick = 0; tick <= 280; tick++) {
        elapsedTick[0] = tick;
        factory.tick = tick;
        if (tick > 0) world.process();
        for (Birth birth : factory.births) {
          if (birth.removed >= 0) continue;
          Missile m = world.getMapper(Missile.class).get(birth.id);
          if (m == null || !birth.name.equals(m.missile.Missile)) { birth.removed = tick; continue; }
          ReferenceElementalEffect effect = world.getMapper(ReferenceElementalEffect.class).get(birth.id);
          if (!birth.finalized) {
            birth.visual = m.presentationOnly; birth.life = m.nativeLifetimeFrames;
            birth.remaining = m.remainingFrames; birth.persistent = m.persistent;
            birth.velocity.set(world.getMapper(Velocity.class).get(birth.id).velocity);
            if (effect != null) {birth.duration = effect.duration; birth.fps = effect.fps;}
            birth.finalized = true;
          }
          AnimationWrapper wrapper = world.getMapper(AnimationWrapper.class).get(birth.id);
          Map<String, Object> state = new LinkedHashMap<>();
          state.put("tick", tick); state.put("age", m.nativeFrame);
          state.put("position", vector(world.getMapper(Position.class).get(birth.id).position));
          Velocity moving = world.getMapper(Velocity.class).get(birth.id);
          state.put("velocity_x", com.riiablo.engine.server.missile.NativePoisonMissileMotion.frameStep(moving.velocity.x));
          state.put("velocity_y", com.riiablo.engine.server.missile.NativePoisonMissileMotion.frameStep(moving.velocity.y));
          DruidWindPresentationSystem windSystem = world.getSystem(DruidWindPresentationSystem.class);
          state.put("hidden_damage_carrier", windSystem != null && windSystem.hidesCarrier(birth.id));
          state.put("loaded", wrapper != null);
          if (wrapper != null) {state.put("frame", wrapper.animation.getFrame()); state.put("direction", wrapper.animation.getDirection());}
          if (effect != null) state.put("offset", vector(effect.offset));
          Position position = world.getMapper(Position.class).get(birth.id);
          if (position.hasNativePrecision()) {
            state.put("x_fixed", position.nativePrecisionX()); state.put("y_fixed", position.nativePrecisionY());
          }
          birth.states.add(state);
        }
      }
      List<Object> births = new ArrayList<>(); Map<String, Integer> visuals = new TreeMap<>(), servers = new TreeMap<>();
      for (Birth birth : factory.births) {
        (birth.visual ? visuals : servers).merge(birth.name, 1, Integer::sum);
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("serial", birth.serial); record.put("missile", birth.name); record.put("tick", birth.tick);
        record.put("position", vector(birth.position)); record.put("velocity", vector(birth.velocity));
        record.put("presentation_only", birth.visual); record.put("lifetime", birth.life);
        record.put("remaining_frames_at_first_sample", birth.remaining); record.put("persistent", birth.persistent);
        record.put("reference_duration", birth.duration); record.put("reference_fps", birth.fps);
        record.put("removed_tick", birth.removed); record.put("states", birth.states); births.add(record);
      }
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("hard_points", points); result.put("server_counts", servers); result.put("visual_counts", visuals); result.put("births", births);
      result.put("limitations", Arrays.asList("Empty map/no targets: no spatial RNG or hit/damage acceptance", "CPU frame selection only; GPU direction loading mocked", "Lifecycle timestamps sampled after world updates"));
      return result;
    } finally {world.dispose(); Riiablo.assets = saved; for (DCC dcc : dccs.values()) dcc.dispose();}
  }

  private static final class Birth {
    int id, serial, tick, life, remaining, removed = -1; boolean finalized, visual, persistent;
    float duration, fps; String name;
    final Vector2 position = new Vector2(), velocity = new Vector2();
    final List<Object> states = new ArrayList<>();
  }
  private static final class RecordingFactory extends ServerEntityFactory {
    int tick; Integer targetSeed; final List<Birth> births = new ArrayList<>();
    private int record(int id, Missiles.Entry row, Vector2 position) {
      if (targetSeed != null && "volcano".equals(row.Missile)) {
        Missile missile = world.getMapper(Missile.class).get(id);
        missile.presentationSeedKnown = true; missile.presentationSeed = targetSeed;
      }
      for (Birth previous : births) if (previous.id == id && previous.removed < 0) previous.removed = tick;
      Birth birth = new Birth(); birth.id = id; birth.serial = births.size(); birth.tick = tick;
      birth.name = row.Missile; birth.position.set(position); births.add(birth); return id;
    }
    @Override public int createMissile(Missiles.Entry row, Vector2 angle, Vector2 at, int owner, int level) {
      return record(super.createMissile(row, angle, at, owner, level), row, at);
    }
    @Override public int createMissile(int row, Vector2 angle, Vector2 at, int owner) {
      return record(super.createMissile(row, angle, at, owner), Riiablo.files.Missiles.get(row), at);
    }
  }
  private static List<Float> vector(Vector2 v) {return Arrays.asList(v.x, v.y);}
  static void write(JsonWriter writer, Object value) throws Exception {
    if (value instanceof Map) {writer.object();for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {writer.name(e.getKey().toString());write(writer, e.getValue());}writer.pop();}
    else if (value instanceof Iterable) {writer.array();for (Object e : (Iterable<?>) value) write(writer, e);writer.pop();}
    else writer.value(value);
  }
}
