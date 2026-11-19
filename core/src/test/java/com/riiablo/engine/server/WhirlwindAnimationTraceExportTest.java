package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.animation.AnimationTraceWriter;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Optional headless exporter used by animation_compare.py. */
class WhirlwindAnimationTraceExportTest extends RiiabloTest {
  @Test
  void exportsCanonicalTraceWhenOutputIsRequested() throws Exception {
    String configured = System.getProperty("riiablo.trace.output",
        System.getenv("RIIABLO_TRACE_OUTPUT") == null ? "" : System.getenv("RIIABLO_TRACE_OUTPUT")).trim();
    assumeTrue(!configured.isEmpty(), "set -Driiablo.trace.output=<path> to export a trace");

    Path output = Path.of(configured);
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("skill", 151);
    metadata.put("level", 1);
    metadata.put("class", Riiablo.BARBARIAN);
    metadata.put("character", "Riiablo-headless-CXBarbWW");
    metadata.put("source", "riiablo-headless-authoritative");
    metadata.put("capabilities", Arrays.asList(
        "mode", "position", "animation_frame", "animation_frame_count",
        "animation_speed", "keyframes"));

    AnimationTraceWriter writer = new AnimationTraceWriter(output, metadata);
    AnimationTraceSystem trace = new AnimationTraceSystem(writer);
    Actioneer actioneer = new Actioneer();
    DummyFactory factory = new DummyFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new net.mostlyoriginal.api.event.common.EventSystem(), actioneer,
            new WhirlwindSystem(), new Pathfinder(),
            new DynamicUnitCollisionSystem(false), new VelocityAdder(), trace, factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
    try {
      Skills.Entry skill = Riiablo.files.skills.get("Whirlwind");
      int id = player(world, 4523.5f, 5641.5f);
      trace.focus(id);
      CofReference cof = world.getMapper(CofReference.class).create(id)
          // Riiablo keeps the native SQ sequence in Sequence while the COF
          // reference points at its A1 transition animation.
          .set("BA", Engine.Player.MODE_A1);
      cof.wclass = Engine.WEAPON_1HS;
      AnimData anim = world.getMapper(AnimData.class).create(id);
      anim.numFrames = 8 << 8;
      anim.speed = 256;
      anim.keyframes = new byte[8];
      anim.keyframes[3] = Engine.KEYFRAME_ATK;
      anim.keyframes[7] = Engine.KEYFRAME_ATK;

      world.getMapper(Casting.class).create(id)
          .set(skill.Id, Engine.INVALID_ENTITY, new Vector2(4543.5f, 5641.5f));
      actioneer.onSkillStart(com.riiablo.engine.server.event.SkillStartEvent.obtain(
          id, skill.Id, Engine.INVALID_ENTITY, new Vector2(4543.5f, 5641.5f),
          skill.srvstfunc, skill.cltstfunc));
      world.setDelta(1f / 25f);
      // Capture the first frame after SrvSt38 installs SQ.  Sampling before
      // the cast records the presentation A1 transition and shifts every
      // subsequent frame by one tick.
      for (int frame = 0; frame <= 60; frame++) {
        anim.frame = (frame % 8) << 8;
        trace.gameFrame(frame);
        world.process();
      }
    } finally {
      trace.close();
      world.dispose();
    }
    if (!Files.isRegularFile(output) || Files.size(output) == 0)
      throw new AssertionError("animation trace was not written: " + output);
  }

  private static int player(World world, float x, float y) {
    int id = world.create();
    world.getMapper(Player.class).create(id);
    world.getMapper(Class.class).create(id).type = Class.Type.PLR;
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(Angle.class).create(id);
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.hitpoints, 1000f);
    attrs.base().put(Stat.maxhp, 1000f);
    attrs.base().put(Stat.mana, 1000f);
    attrs.base().put(Stat.maxmana, 1000f);
    attrs.reset();
    world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
    world.getMapper(UnitStates.class).create(id).init(id);
    world.getMapper(Velocity.class).create(id).setPlayer(6f, 9f);
    return id;
  }

  private static final class DummyFactory extends EntityFactory {
    @Override public int createPlayer(com.riiablo.save.CharData data, Vector2 position) { return -1; }
    @Override public int createDynamicObject(int act, int preset, float x, float y) { return -1; }
    @Override public int createStaticObject(int act, int object, float x, float y) { return -1; }
    @Override public int createStaticObjectByClassId(int object, float x, float y) { return -1; }
    @Override public int createMonster(int monster, float x, float y) { return -1; }
    @Override public int createWarp(int index, float x, float y) { return -1; }
    @Override public int createItem(com.riiablo.item.Item item, float x, float y) { return -1; }
    @Override public int createMissile(int missile, Vector2 angle, Vector2 position) { return -1; }
  }

}
