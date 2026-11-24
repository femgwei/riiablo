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
import com.riiablo.codec.Animation;
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
import com.riiablo.presentation.component.ReferenceElementalEmitter;
import com.riiablo.save.CharData;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.mostlyoriginal.api.event.common.EventSystem;

/** Diagnostic: executes current production, reports differences rather than asserting parity. */
public final class MoltenBoulderOriginalAnalysis {
  public static void main(String[] args) throws Exception {
    RiiabloTest.setup();
    try {
      JsonValue original = new JsonReader().parse(Files.readString(Path.of(args[0])));
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("scope", "Level-one original cast versus production skill/movement/presentation; actual MPQ DCCs; no GPU or target damage");
      result.put("source_run_id", original.getString("run_id"));
      result.put("production_skill_fixes_applied", true);
      result.put("production", simulate(original));
      StringWriter text = new StringWriter();
      JsonWriter writer = new JsonWriter(text);
      writer.setOutputType(JsonWriter.OutputType.json);
      write(writer, result);
      writer.flush();
      Files.writeString(Path.of(args[1]), text + "\n");
    } finally { RiiabloTest.teardown(); }
  }

  static Map<String, Object> simulate(JsonValue original) {
    Riiablo.colors = new com.riiablo.Colors();
    AssetManager saved = Riiablo.assets;
    AssetManager manager = Riiablo.assets = mock(AssetManager.class);
    Map<String, DCC> dccs = new HashMap<>();
    for (String name : new String[] {"moltenboulderemerge", "moltenboulder", "moltenboulderfirepath", "moltenboulderexplode", "moltenboulder-flyingrocks", "firewall", "firesmall", "firemedium"}) {
      Missiles.Entry row = Riiablo.files.Missiles.get(name);
      DCC dcc = spy(DCC.loadFromFile(Riiablo.mpqs.resolve("data/global/missiles/" + row.CelFile + ".dcc")));
      doNothing().when(dcc).loadDirection(anyInt());
      dccs.put(row.CelFile.replace('\\', '/').toLowerCase(Locale.ROOT), dcc);
    }
    when(manager.contains(anyString())).thenReturn(true);
    when(manager.isLoaded(any(AssetDescriptor.class))).thenReturn(true);
    when(manager.get(any(AssetDescriptor.class))).thenAnswer(call -> {
      AssetDescriptor<?> d = call.getArgument(0);
      String path = d.fileName.replace('\\', '/').toLowerCase(Locale.ROOT);
      for (Map.Entry<String, DCC> entry : dccs.entrySet())
        if (path.endsWith("/" + entry.getKey() + ".dcc")) return entry.getValue();
      throw new IllegalArgumentException(path);
    });
    int presentationSeed = 1;
    for (JsonValue unit : original.get("units")) {
      if ("client".equals(unit.getString("side"))
          && "moltenboulder".equals(unit.getString("missile")))
        presentationSeed = unit.get("states").child.getInt("seed_low");
    }
    RecordingFactory factory = new RecordingFactory(presentationSeed);
    World world = new World(new WorldConfigurationBuilder().with(new EventSystem(),
        new ItemManager(), new ObjectInteractor(), new WarpInteractor(), new ItemInteractor(),
        new MissileCollisionSystem(), new OverlayManager(), new ReferenceElementalPresentationSystem(),
        new MissileImpactPresentationSystem(), new ServerSkillSystem(true), factory,
        new MissileLoader(), new AnimationStepper(), new DirectionResolver()).build()
        .register("factory", factory).register("map", new com.riiablo.map.Map(0, 0))
        .register("townPortalRegistry", new com.riiablo.engine.server.portal.TownPortalRegistry())
        .register("clientContext", ClientGameplayContext.fromGlobals()));
    try {
      JsonValue cast = original.get("identity");
      int owner = world.create();
      CharData data = CharData.createRemote("molten-probe", (byte) Riiablo.DRUID);
      Map<String, Integer> hardPoints = DruidReplayLoadout.apply(data, original, 229);
      world.getMapper(Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(cast.getInt("x"), cast.getInt("y"));
      Attributes attrs = Attributes.obtainStandard();
      for (short stat : new short[] {Stat.level, Stat.hitpoints, Stat.maxhp, Stat.mana, Stat.maxmana})
        attrs.base().put(stat, stat == Stat.level ? 1 : 1000);
      attrs.reset();
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attrs;
      com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(229);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(owner, 229, -1,
          new Vector2(cast.getInt("target_x"), cast.getInt("target_y")), skill.srvdofunc, skill.cltdofunc));
      Map<String, Object> root = new LinkedHashMap<>();
      Map<String, Object> rollingBirth = new LinkedHashMap<>();
      List<Object> rollingStates = new ArrayList<>();
      world.setDelta(1f / 25f);
      for (int tick = 0; tick <= 160; tick++) {
        factory.tick = tick;
        if (tick > 0) world.process();
        // Capture immutable birth configuration before Artemis can recycle ids.
        for (Birth birth : factory.births) if (!birth.finalized && world.getMapper(Missile.class).has(birth.id)) {
          Missile m = world.getMapper(Missile.class).get(birth.id);
          birth.lifetime = m.nativeLifetimeFrames;
          birth.visual = m.presentationOnly;
          birth.finalized = true;
          ReferenceElementalEffect effect = world.getMapper(ReferenceElementalEffect.class).get(birth.id);
          if (effect != null) { birth.duration = effect.duration; birth.endOffset.set(effect.endOffset);
            birth.direction = effect.direction; }
          if ("moltenboulderemerge".equals(birth.name) || "moltenboulder".equals(birth.name)) {
            Map<String, Object> details = "moltenboulder".equals(birth.name) ? rollingBirth : root;
            details.put("tick", tick);
            details.put("position", vector(birth.position));
            details.put("velocity", vector(world.getMapper(Velocity.class).get(birth.id).velocity));
            details.put("lifetime", m.nativeLifetimeFrames);
            world.getSystem(MissileLoader.class).loadNow(birth.id);
          }
        }
        com.artemis.utils.IntBag ids = world.getAspectSubscriptionManager()
            .get(Aspect.all(Missile.class, Position.class)).getEntities();
        for (int i=0; i<ids.size(); i++) {
          int id = ids.get(i); Missile m = world.getMapper(Missile.class).get(id);
          if (!"moltenboulder".equals(m.missile.Missile)) continue;
          AnimationWrapper a = world.getMapper(AnimationWrapper.class).get(id);
          rollingStates.add(Map.of("tick", tick, "native_age", m.nativeFrame,
              "position", vector(world.getMapper(Position.class).get(id).position),
              "frame", a == null ? -1 : a.animation.getFrame()));
        }
      }
      Map<String, Integer> visualCounts = new LinkedHashMap<>(), serverCounts = new LinkedHashMap<>();
      List<Object> births = new ArrayList<>();
      for (Birth birth : factory.births) {
        (birth.visual ? visualCounts : serverCounts).merge(birth.name, 1, Integer::sum);
        births.add(Map.of("tick", birth.tick, "missile", birth.name, "position", vector(birth.position),
            "lifetime", birth.lifetime, "presentation_only", birth.visual,
            "duration_seconds", birth.duration, "end_offset_pixels", vector(birth.endOffset),
            "dcc_direction", birth.direction));
      }
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("hard_points", hardPoints);
      result.put("emerge_birth", root);
      result.put("rolling_birth", rollingBirth);
      result.put("rolling_states", rollingStates);
      result.put("visual_counts", visualCounts);
      result.put("server_counts", serverCounts);
      result.put("births", births);
      result.put("visual_seed_source", "Original rolling client's first pre-crossing seed; combat RNG unchanged");
      result.put("limitations", List.of("No map collision or targets in this production simulation",
          "Cast is dispatched at SkillDo; no full caster animation or GPU pixels",
          "Original client and server are asynchronous; compare native ages, not wall-clock frames"));
      return result;
    } finally {
      world.dispose();
      Riiablo.assets = saved;
      for (DCC dcc : dccs.values()) dcc.dispose();
    }
  }

  private static final class Birth {
    int id, tick, lifetime, direction;
    boolean finalized, visual;
    float duration;
    final Vector2 endOffset = new Vector2();
    String name;
    final Vector2 position = new Vector2();
  }
  private static final class RecordingFactory extends ServerEntityFactory {
    final int presentationSeed;
    RecordingFactory(int presentationSeed) { this.presentationSeed = presentationSeed; }
    int tick;
    final List<Birth> births = new ArrayList<>();
    private int record(int id, Missiles.Entry row, Vector2 at) {
      if (id >= 0) {
        if ("moltenboulder".equals(row.Missile)) {
          Missile m = world.getMapper(Missile.class).get(id);
          m.presentationSeedKnown = true;
          m.presentationSeed = presentationSeed;
        }
        Birth b = new Birth(); b.id = id; b.tick = tick; b.name = row.Missile; b.position.set(at);
        births.add(b);
      }
      return id;
    }
    @Override public int createMissile(Missiles.Entry row, Vector2 angle, Vector2 at, int owner, int level) {
      return record(super.createMissile(row, angle, at, owner, level), row, at);
    }
    @Override public int createMissile(int row, Vector2 angle, Vector2 at, int owner) {
      return record(super.createMissile(row, angle, at, owner), Riiablo.files.Missiles.get(row), at);
    }
  }
  private static List<Float> vector(Vector2 v) { return List.of(v.x, v.y); }
  private static void write(JsonWriter w, Object value) throws Exception {
    if (value instanceof Map) {
      w.object();
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) { w.name(entry.getKey().toString()); write(w, entry.getValue()); }
      w.pop();
    } else if (value instanceof Iterable) {
      w.array(); for (Object item : (Iterable<?>) value) write(w, item); w.pop();
    } else w.value(value);
  }
}
