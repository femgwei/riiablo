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
public final class FirestormOriginalAnalysis {
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
    for (String name : new String[] {"firestormmaker", "firestorm", "firesmall", "firemedium"}) {
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
    RecordingFactory factory = new RecordingFactory();
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
      CharData data = CharData.createRemote("firestorm-probe", (byte) Riiablo.DRUID);
      Map<String, Integer> hardPoints = DruidReplayLoadout.apply(data, original, 225);
      world.getMapper(Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(cast.getInt("x"), cast.getInt("y"));
      Attributes attrs = Attributes.obtainStandard();
      for (short stat : new short[] {Stat.level, Stat.hitpoints, Stat.maxhp, Stat.mana, Stat.maxmana})
        attrs.base().put(stat, stat == Stat.level ? 1 : 1000);
      attrs.reset();
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attrs;
      com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(225);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(owner, 225, -1,
          new Vector2(cast.getInt("target_x"), cast.getInt("target_y")), skill.srvdofunc, skill.cltdofunc));
      List<Integer> heads = new ArrayList<>();
      List<Object> headBirths = new ArrayList<>(), lastHeads = new ArrayList<>();
      for (Birth birth : factory.births) {
        heads.add(birth.id);
        Missile m = world.getMapper(Missile.class).get(birth.id);
        Vector2 v = world.getMapper(Velocity.class).get(birth.id).velocity;
        headBirths.add(Map.of("position", vector(birth.position), "velocity", vector(v),
            "lifetime", m.nativeLifetimeFrames, "charged_bolt_path", m.chargedBoltPath));
        world.getSystem(MissileLoader.class).loadNow(birth.id);
      }
      Map<Integer, Integer> makerFrames = new HashMap<>();
      makerFrames.put(0, world.getMapper(AnimationWrapper.class).get(heads.get(0)).animation.getFrame());
      List<Object> timeline = new ArrayList<>();
      Set<Integer> phases = new TreeSet<>();
      Map<Integer, Float> lanes = new LinkedHashMap<>();
      world.setDelta(1f / 25f);
      for (int tick = 1; tick <= 160; tick++) {
        factory.tick = tick;
        world.process();
        int headCount = 0, serverFire = 0, clientFire = 0;
        com.artemis.utils.IntBag ids = world.getAspectSubscriptionManager()
            .get(Aspect.all(Missile.class, Position.class)).getEntities();
        for (int i = 0; i < ids.size(); i++) {
          int id = ids.get(i);
          Missile m = world.getMapper(Missile.class).get(id);
          if ("firestormmaker".equals(m.missile.Missile)) {
            headCount++;
            Animation a = world.getMapper(AnimationWrapper.class).get(id).animation;
            makerFrames.putIfAbsent(m.nativeFrame, a.getFrame());
            ReferenceElementalEmitter emitter = world.getMapper(ReferenceElementalEmitter.class).get(id);
            if (emitter != null) lanes.put(id, emitter.firestormLaneAngle);
            if (tick == 39) lastHeads.add(Map.of("position", vector(world.getMapper(Position.class).get(id).position),
                "velocity", vector(world.getMapper(Velocity.class).get(id).velocity), "age", m.nativeFrame));
          } else if (Set.of("firestorm", "firesmall", "firemedium").contains(m.missile.Missile)) {
            if (m.presentationOnly) clientFire++; else serverFire++;
            ReferenceElementalEffect effect = world.getMapper(ReferenceElementalEffect.class).get(id);
            if (effect != null) phases.add(effect.phase);
          }
        }
        for (Birth birth : factory.births) if (!birth.finalized && world.getMapper(Missile.class).has(birth.id)) {
          Missile m = world.getMapper(Missile.class).get(birth.id);
          birth.lifetime = m.nativeLifetimeFrames;
          birth.visual = m.presentationOnly;
          birth.finalized = true;
        }
        timeline.add(Map.of("tick", tick, "makers", headCount,
            "damage_ground_fires", serverFire, "visual_ground_fires", clientFire));
      }
      List<Object> births = new ArrayList<>();
      Map<String, Integer> variants = new LinkedHashMap<>();
      int damageFireBirths = 0, visualFireBirths = 0;
      for (Birth birth : factory.births) {
        if (!Set.of("firestorm", "firesmall", "firemedium").contains(birth.name)) continue;
        if (birth.visual) visualFireBirths++; else damageFireBirths++;
        if (birth.visual) variants.merge(birth.name, 1, Integer::sum);
        births.add(Map.of("tick", birth.tick, "position", vector(birth.position),
            "lifetime", birth.lifetime, "presentation_only", birth.visual, "missile", birth.name));
      }
      int makerSamples = 0, makerMismatch = 0;
      for (JsonValue sample : original.get("samples")) if ("firestormmaker".equals(sample.getString("missile"))) {
        Integer frame = makerFrames.get(sample.getInt("native_age"));
        if (frame == null) throw new IllegalStateException("Missing production maker age");
        makerSamples++;
        if (frame != sample.getInt("frame")) makerMismatch++;
      }
      int fireSamples = 0, fireMismatch = 0;
      Missile fire = new Missile().set(Riiablo.files.Missiles.get("firestorm"), Vector2.Zero, 0);
      fire.presentationOnly = true;
      for (JsonValue sample : original.get("samples")) if ("firestorm".equals(sample.getString("missile"))) {
        fireSamples++;
        if (NativeFirestormPresentation.frameAt(fire, 37, sample.getInt("native_age")) != sample.getInt("frame")) fireMismatch++;
      }
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("hard_points", hardPoints);
      result.put("head_births", headBirths);
      result.put("heads_at_age39", lastHeads);
      result.put("visual_lane_angles", new ArrayList<>(lanes.values()));
      result.put("damage_ground_fire_births", damageFireBirths);
      result.put("visual_ground_fire_births", visualFireBirths);
      result.put("visual_ground_fire_variants", variants);
      result.put("ground_fire_births", births);
      result.put("timeline", timeline);
      result.put("maker_sprite_samples", makerSamples);
      result.put("maker_frame_mismatches", makerMismatch);
      result.put("ground_fire_sprite_samples", fireSamples);
      result.put("ground_fire_frame_mismatches", fireMismatch);
      result.put("ground_fire_animation_comparison", "Original native ages, Firestorm-specific CltDo5 clock; no GPU comparison or random-stream pairing");
      return result;
    } finally {
      world.dispose();
      Riiablo.assets = saved;
      for (DCC dcc : dccs.values()) dcc.dispose();
    }
  }

  private static final class Birth {
    int id, tick, lifetime;
    boolean finalized, visual;
    String name;
    final Vector2 position = new Vector2();
  }
  private static final class RecordingFactory extends ServerEntityFactory {
    int tick;
    final List<Birth> births = new ArrayList<>();
    private int record(int id, Missiles.Entry row, Vector2 at) {
      if (id >= 0) {
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
