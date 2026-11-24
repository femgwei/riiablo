package com.riiablo.presentation;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
import com.riiablo.save.CharData;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.mostlyoriginal.api.event.common.EventSystem;

/** Reports the current production stream; intentionally does not assert parity. */
public final class InfernoOriginalAnalysis {
  public static void main(String[] args) throws Exception {
    RiiabloTest.setup();
    try {
      JsonValue fixture = new JsonReader().parse(Files.readString(Path.of(args[0])));
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("source_run_id", fixture.getString("run_id"));
      result.put("scope", "One level-one production Inferno stream via real factory, skill, movement and animation systems; no GPU");
      result.put("production", simulate(fixture));
      StringWriter text = new StringWriter();
      JsonWriter writer = new JsonWriter(text);
      writer.setOutputType(JsonWriter.OutputType.json);
      write(writer, result); writer.flush();
      Files.writeString(Path.of(args[1]), text + "\n");
    } finally { RiiabloTest.teardown(); }
  }

  static Map<String, Object> simulate(JsonValue fixture) {
    return simulate(fixture, false, 1f / 25f, 12);
  }

  static Map<String, Object> simulate(JsonValue fixture, boolean independent, float delta, int updates) {
    return simulate(fixture, independent, delta, updates, 0);
  }

  static Map<String, Object> simulate(JsonValue fixture, boolean independent, float delta, int updates, int loadAfterUpdates) {
    Riiablo.colors = new com.riiablo.Colors();
    AssetManager saved = Riiablo.assets;
    AssetManager manager = Riiablo.assets = mock(AssetManager.class);
    Map<String, DCC> dccs = new HashMap<>();
    for (String name : new String[] {"infernoflame1", "infernoflame2"}) {
      Missiles.Entry row = Riiablo.files.Missiles.get(name);
      DCC dcc = spy(DCC.loadFromFile(Riiablo.mpqs.resolve("data/global/missiles/" + row.CelFile + ".dcc")));
      doNothing().when(dcc).loadDirection(anyInt());
      dccs.put(row.CelFile.toLowerCase(Locale.ROOT), dcc);
    }
    when(manager.contains(anyString())).thenReturn(true);
    final int[] elapsedUpdates = {0};
    when(manager.isLoaded(any(AssetDescriptor.class))).thenAnswer(call -> elapsedUpdates[0] >= loadAfterUpdates);
    when(manager.get(any(AssetDescriptor.class))).thenAnswer(call -> {
      AssetDescriptor<?> descriptor = call.getArgument(0);
      String path = descriptor.fileName.replace('\\', '/').toLowerCase(Locale.ROOT);
      for (Map.Entry<String, DCC> entry : dccs.entrySet())
        if (path.endsWith("/" + entry.getKey() + ".dcc")) return entry.getValue();
      throw new IllegalArgumentException(path);
    });
    RecordingFactory factory = new RecordingFactory();
    WorldConfigurationBuilder configuration = new WorldConfigurationBuilder().with(new EventSystem(),
        new ItemManager(), new ObjectInteractor(), new WarpInteractor(), new ItemInteractor(),
        new MissileCollisionSystem(), new OverlayManager(), new MissileImpactPresentationSystem(),
        new ServerSkillSystem(true), factory, new MissileLoader(), new AnimationStepper(),
        new DirectionResolver());
    if (independent) configuration.with(new InfernoPresentationSystem());
    World world = new World(configuration.build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0))
        .register("townPortalRegistry", new com.riiablo.engine.server.portal.TownPortalRegistry())
        .register("clientContext", ClientGameplayContext.fromGlobals()));
    try {
      JsonValue cast = fixture.get("identity");
      int owner = world.create();
      CharData data = CharData.createRemote("inferno-probe", (byte) Riiablo.SORCERESS);
      data.setSkillLevel(41, 1);
      world.getMapper(Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(cast.getInt("x"), cast.getInt("y"));
      Attributes attrs = Attributes.obtainStandard();
      for (short stat : new short[] {Stat.level, Stat.hitpoints, Stat.maxhp, Stat.mana, Stat.maxmana})
        attrs.base().put(stat, stat == Stat.level ? 1 : 1000);
      attrs.reset();
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attrs;
      com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(41);
      Vector2 target = new Vector2(cast.getInt("target_x"), cast.getInt("target_y"));
      if (!world.getSystem(ServerSkillSystem.class).startInfernoChannel(owner, skill, -1, target))
        throw new IllegalStateException("Production Inferno start rejected");
      // First Do019 arms the skill; the second creates a single resident
      // stream. This diagnostic does not supply or verify held-input cadence.
      for (int i = 0; i < 2; i++) world.getSystem(EventSystem.class).dispatch(
          SkillDoEvent.obtain(owner, 41, -1, target, skill.srvdofunc, skill.cltdofunc));
      int id = factory.stream;
      int serverId = -1, visualId = -1, visualCount = 0, serverCount = 0;
      for (int created : factory.ids) {
        Missile candidate = world.getMapper(Missile.class).get(created);
        if (candidate.presentationOnly) { visualId = created; visualCount++; }
        else { serverId = created; serverCount++; }
      }
      if (independent) id = visualId;
      if (id < 0) throw new IllegalStateException("Production did not create Inferno");
      Missile missile = world.getMapper(Missile.class).get(id);
      int lifetime = missile.nativeLifetimeFrames;
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("lifetime", lifetime);
      result.put("visual_count", visualCount); result.put("server_count", serverCount);
      if (independent) {
        world.getSystem(MissileLoader.class).loadNow(serverId);
        result.put("damage_carrier_hidden", !world.getMapper(AnimationWrapper.class).has(serverId));
        result.put("visual_networked", world.getMapper(com.riiablo.engine.server.component.Networked.class).has(id));
      }
      result.put("velocity", vector(world.getMapper(Velocity.class).get(id).velocity));
      result.put("native_precision", world.getMapper(Position.class).get(id).hasNativePrecision());
      world.getSystem(MissileLoader.class).loadNow(id);
      result.put("visual_row", MissileLoader.infernoVisualRow(ClientGameplayContext.fromGlobals(), missile).Missile);
      List<Object> states = new ArrayList<>();
      world.setDelta(delta);
      for (int tick = 0; tick <= updates; tick++) {
        elapsedUpdates[0] = tick;
        if (tick > 0) world.process();
        if (!world.getMapper(Missile.class).has(id)) continue;
        AnimationWrapper animation = world.getMapper(AnimationWrapper.class).get(id);
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("tick", tick); sample.put("native_age", missile.nativeFrame);
        sample.put("position", vector(world.getMapper(Position.class).get(id).position));
        sample.put("loaded", animation != null);
        if (animation != null) {
          sample.put("frame", animation.animation.getFrame());
          sample.put("frame_delta", animation.animation.getFrameDelta());
        }
        states.add(sample);
      }
      result.put("states", states);
      result.put("alive_after_updates", world.getMapper(Missile.class).has(id));
      result.put("limitations", List.of("One stream; no original client cadence replay or caster sequence/GPU comparison",
          "No map collision, targets, damage values or held-input mana comparison",
          "Riiablo cell coordinates correspond to native fixed-point cell centres minus 0.5"));
      return result;
    } finally {
      world.dispose(); Riiablo.assets = saved;
      for (DCC dcc : dccs.values()) dcc.dispose();
    }
  }

  static class RecordingFactory extends ServerEntityFactory {
    int stream = -1;
    final List<Integer> ids = new ArrayList<>();
    @Override public int createMissile(Missiles.Entry row, Vector2 angle, Vector2 at, int owner, int level) {
      int id = super.createMissile(row, angle, at, owner, level);
      ids.add(id);
      if (row != null && "infernoflame1".equals(row.Missile)) stream = id;
      return id;
    }
  }
  private static List<Float> vector(Vector2 v) { return List.of(v.x, v.y); }
  private static void write(JsonWriter w, Object value) throws Exception {
    if (value instanceof Map) {
      w.object(); for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
        w.name(entry.getKey().toString()); write(w, entry.getValue());
      } w.pop();
    } else if (value instanceof Iterable) {
      w.array(); for (Object item : (Iterable<?>) value) write(w, item); w.pop();
    } else w.value(value);
  }
}
