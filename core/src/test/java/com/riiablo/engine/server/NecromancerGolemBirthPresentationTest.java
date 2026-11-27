package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.COF;
import com.riiablo.codec.D2;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.ai.NecroPet;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.map.Map;
import com.riiablo.presentation.AnimationStepper;
import com.riiablo.presentation.component.AnimationWrapper;
import com.riiablo.save.CharData;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Native MPQ records and production birth clock; no display or GPU required. */
class NecromancerGolemBirthPresentationTest extends RiiabloTest {
  private static JsonValue originalPhases(String fixture) {
    return new JsonReader().parse(com.badlogic.gdx.Gdx.files.classpath(
        "original/necromancer-" + fixture + "-golem-draw-1.10f.json")).get("phases");
  }

  @ParameterizedTest
  @CsvSource({"G1,clay", "G2,blood", "G3,fire"})
  void nativeResourcesContainEverySampledOriginalFrame(String token, String fixture) {
    D2 retail = D2.loadFromFile(Riiablo.mpqs.resolve("data/global/animdata.d2"));
    D2 effective = D2.loadFromFile(Riiablo.mpqs.resolve("data/global/eanimdata.d2"));
    for (String mode : new String[] {"S1", "WL", "NU"}) {
      String name = token + mode + "HTH";
      D2.Entry data = effective.getEntry(name);
      D2.Entry original = retail.getEntry(name);
      COF cof = COF.loadFromFile(Riiablo.mpqs.resolve("data/global/monsters/" + token + "/cof/" + name + ".cof"));
      assertNotNull(data); assertNotNull(original);
      assertEquals(original.framesPerDir, data.framesPerDir);
      assertEquals(original.speed, data.speed);
      assertEquals(data.framesPerDir, cof.getNumFramesPerDir());
      ByteBuffer dcc = ByteBuffer.wrap(Riiablo.mpqs.resolve(
          "data/global/monsters/" + token + "/TR/" + token + "TRLIT" + mode + "HTH.dcc").readBytes()).order(ByteOrder.LITTLE_ENDIAN);
      assertTrue((dcc.get(2) & 255) >= cof.getNumDirections());
      assertTrue(dcc.getInt(3) >= data.framesPerDir);
      for (JsonValue phase : originalPhases(fixture)) {
        if (mode.equals(phase.getString("mode"))) {
          for (int frame : phase.get("frames").asIntArray())
            assertTrue(frame >= 0 && frame < data.framesPerDir, "sampled original frame " + frame);
        }
      }
      System.out.println("GOLEM_RESOURCE token=" + token + " mode=" + mode + " frames=" + data.framesPerDir
          + " speed=" + data.speed + " dccFrames=" + dcc.getInt(3)
          + " directions=" + (dcc.get(2) & 255));
    }
  }

  @ParameterizedTest
  @CsvSource({"75,G1,clay", "85,G2,blood", "94,G3,fire"})
  void successfulCastHoldsBirthUntilLastFrameBeforeAiFollows(int skillId, String token, String fixture) {
    D2 previous = Riiablo.anim;
    com.riiablo.Colors colors = Riiablo.colors;
    World previousWorld = Riiablo.engine;
    Riiablo.anim = D2.loadFromFile(Riiablo.mpqs.resolve("data/global/eanimdata.d2"));
    Riiablo.colors = new com.riiablo.Colors();
    Factory factory = new Factory();
    Pathfinder pathfinder = mock(Pathfinder.class);
    when(pathfinder.findPath(anyInt(), any(Vector2.class), anyBoolean(), anyInt())).thenReturn(true);
    World world = new World(new WorldConfigurationBuilder().with(new EventSystem(),
        new CofManager(), new AnimDataResolver(), new SequenceHandler(), new AnimStepper(),
        new AnimationStepper(), new ServerSkillSystem(true), pathfinder, factory).build()
        .register("factory", factory).register("map", new Map(0, 0)));
    Riiablo.engine = world;
    try {
      int owner = world.create();
      CharData data = CharData.createRemote(fixture + "-birth", (byte) Riiablo.NECROMANCER);
      data.setSkillLevel(skillId, 20);
      world.getMapper(Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(10, 10);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes();
      Skills.Entry skill = Riiablo.files.skills.get(skillId);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, skillId, -1, new Vector2(20, 10), skill.srvdofunc, skill.cltdofunc));
      int id = factory.last;
      assertTrue(id >= 0); assertEquals(1, factory.created);
      if (skillId == 94) {
        com.riiablo.engine.server.state.UnitState holyFire = world.getMapper(UnitStates.class)
            .get(id).stateList.getState(com.riiablo.engine.server.state.StateId.HOLYFIRE);
        assertNotNull(holyFire, "Fire Golem must own its native granted Holy Fire");
        assertEquals(27, holyFire.level, "level 20 golem grants ln56 = 8 + 19 * 1");
        assertEquals(50, holyFire.periodicDelayFrames);
      }
      Sequence birth = world.getMapper(Sequence.class).get(id);
      assertNotNull(birth); assertEquals(Engine.Monster.MODE_S1, birth.mode1);
      assertEquals(Engine.Monster.MODE_NU, birth.mode2);
      assertFalse(world.getMapper(Casting.class).has(id));
      world.setDelta(1f / 25f);
      List<Integer> frames = new ArrayList<>();
      COF cof = COF.loadFromFile(Riiablo.mpqs.resolve("data/global/monsters/" + token + "/cof/" + token + "S1HTH.cof"));
      com.riiablo.codec.Animation animation = world.getMapper(AnimationWrapper.class).create(id).animation;
      animation.setCOF(cof);
      frames.add(animation.getFrame());
      NecroPet ai = new NecroPet(id);
      world.getInjector().inject(ai); ai.initialize();
      int ticks = 0;
      while (world.getMapper(Sequence.class).has(id) && ticks < 100) {
        ai.update(1f / 25f);
        verify(pathfinder, never()).findPath(anyInt(), any(Vector2.class), anyBoolean(), anyInt());
        world.process(); ticks++;
        if (world.getMapper(Sequence.class).has(id)) {
          assertEquals(Engine.Monster.MODE_S1, world.getMapper(CofReference.class).get(id).mode);
          frames.add(animation.getFrame());
        }
      }
      assertTrue(ticks < 100);
      assertEquals(Engine.Monster.MODE_NU, world.getMapper(CofReference.class).get(id).mode);
      for (int frame : originalPhases(fixture).get(0).get("frames").asIntArray())
        assertTrue(frames.contains(frame), "original S1 frame " + frame);
      assertTrue(frames.contains(cof.getNumFramesPerDir() - 1), "Riiablo must present the birth tail");
      // The native AI uses a bounded think delay. Let it reach its first
      // post-birth decision before asserting the owner-follow request.
      for (int i = 0; i < 12; i++) ai.update(1f / 25f);
      verify(pathfinder, atLeastOnce()).findPath(eq(id), eq(new Vector2(10, 10)), eq(false), eq(owner));
      assertEquals(new Vector2(20, 10), world.getMapper(Position.class).get(id).position);
      System.out.println("GOLEM_BIRTH skill=" + skillId + " ticks=" + ticks + " presentedFrames=" + frames);
    } finally {
      world.dispose(); Riiablo.anim = previous; Riiablo.colors = colors; Riiablo.engine = previousWorld;
    }
  }

  private static Attributes attributes() {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.level, 20); attrs.base().put(Stat.maxhp, 100f);
    attrs.base().put(Stat.hitpoints, 100f); attrs.reset(); return attrs;
  }

  private static final class Factory extends EntityFactory {
    int last = -1, created;
    @Override public int createSummonedPet(int owner, MonStats.Entry row, String type, int skill,
        int level, int max, boolean passive, int duration, float x, float y) {
      last = world.create(); created++;
      world.getMapper(Monster.class).create(last).set(row, Riiablo.files.monstats2.get(row.MonStatsEx));
      world.getMapper(com.riiablo.engine.server.component.Class.class).create(last).type =
          com.riiablo.engine.server.component.Class.Type.MON;
      world.getMapper(CofReference.class).create(last).set(row.Code, Engine.Monster.MODE_NU);
      world.getMapper(Position.class).create(last).position.set(x, y);
      world.getMapper(Angle.class).create(last);
      world.getMapper(Velocity.class).create(last).setMonsterFromNativeTable(row.Velocity);
      world.getMapper(AttributesWrapper.class).create(last).attrs = attributes();
      world.getMapper(UnitStates.class).create(last).init(last);
      world.getMapper(SummonedPet.class).create(last).set(owner, type, skill, level, passive, duration);
      return last;
    }
    @Override public int createPlayer(CharData data, Vector2 p) { return -1; }
    @Override public int createDynamicObject(int a,int b,float x,float y) { return -1; }
    @Override public int createStaticObject(int a,int b,float x,float y) { return -1; }
    @Override public int createStaticObjectByClassId(int a,float x,float y) { return -1; }
    @Override public int createMonster(int a,float x,float y) { return -1; }
    @Override public int createWarp(int a,float x,float y) { return -1; }
    @Override public int createItem(com.riiablo.item.Item item,float x,float y) { return -1; }
    @Override public int createMissile(int a,Vector2 b,Vector2 c) { return -1; }
  }
}
