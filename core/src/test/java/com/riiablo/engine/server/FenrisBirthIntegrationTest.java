package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
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
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.event.*;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class FenrisBirthIntegrationTest extends RiiabloTest {
  @Test void actualSummonInstallsS1AndProductionAnimationReturnsToNeutralWithoutCasting() {
    D2 previous = Riiablo.anim;
    Riiablo.anim = D2.loadFromFile(Riiablo.mpqs.resolve("data/global/eanimdata.d2"));
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder().with(new EventSystem(),
        new CofManager(), new AnimDataResolver(), new SequenceHandler(), new AnimStepper(),
        new ServerSkillSystem(true), factory).build().register("factory", factory)
        .register("map", new Map(0, 0)));
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("fenris-birth", (byte) Riiablo.DRUID);
      data.setSkillLevel(237, 8);
      world.getMapper(Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(10, 10);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes();
      world.getMapper(UnitStates.class).create(owner).init(owner);
      Skills.Entry skill = Riiablo.files.skills.get(237);
      assertEquals(119, skill.srvdofunc); assertEquals("S1", skill.summode);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(owner, 237, -1,
          new Vector2(14, 15), skill.srvdofunc, skill.cltdofunc));
      int wolf = factory.last;
      assertTrue(wolf >= 0); assertEquals(3, factory.maximum);
      Sequence birth = world.getMapper(Sequence.class).get(wolf);
      assertNotNull(birth, "Skills.Summode must not be lost inside generic monster creation");
      assertEquals(Engine.Monster.MODE_S1, birth.mode1);
      assertEquals(Engine.Monster.MODE_NU, birth.mode2);
      assertFalse(world.getMapper(Casting.class).has(wolf));
      world.setDelta(1f/25f); world.process();
      assertEquals(Engine.Monster.MODE_S1, world.getMapper(CofReference.class).get(wolf).mode);
      assertWireMode(world.getMapper(CofReference.class).get(wolf), Engine.Monster.MODE_S1);
      int frames = world.getMapper(AnimData.class).get(wolf).numFrames >>> 8;
      int speed = world.getMapper(AnimData.class).get(wolf).speed;
      assertTrue(frames > 1 && frames < 100, "native record must resolve rather than 2048-frame fallback");
      int ticks = 1;
      while (world.getMapper(Sequence.class).has(wolf) && ticks < 200) { world.process(); ticks++; }
      assertTrue(ticks < 200);
      assertEquals(Engine.Monster.MODE_NU, world.getMapper(CofReference.class).get(wolf).mode);
      assertWireMode(world.getMapper(CofReference.class).get(wolf), Engine.Monster.MODE_NU);
      assertFalse(world.getMapper(Casting.class).has(wolf));
      assertEquals(1, factory.created, "birth keyframes must not recursively summon");
      System.out.println("FENRIS_BIRTH frames=" + frames + " speed=" + speed + " port_ticks=" + ticks);
    } finally { world.dispose(); Riiablo.anim = previous; }
  }

  @Test void nativeBirthAndFeedingCofsAndTheirBodyAssetsExist() {
    MonStats.Entry wolf = Riiablo.files.monstats.get("fenris");
    assertNotNull(wolf);
    assertEquals("DruidWolf", wolf.AI);
    System.out.println("FENRIS_ROW token=" + wolf.Code + " mon_spawnmode=" + wolf.spawnmode
        + " skill1=" + wolf.Skill1 + " skill1mode=" + wolf.Sk1mode
        + " skill2=" + wolf.Skill2 + " skill2mode=" + wolf.Sk2mode);
    for (String mode : new String[] {"S1", wolf.Sk1mode.toUpperCase(java.util.Locale.ROOT)}) {
      String path = "data/global/monsters/" + wolf.Code + "/cof/" + wolf.Code + mode + "HTH.cof";
      assertTrue(Riiablo.mpqs.contains(path), path);
      COF cof = COF.loadFromFile(Riiablo.mpqs.resolve(path));
      assertTrue(cof.getNumFramesPerDir() > 0);
      for (int i = 0; i < cof.getNumLayers(); i++) {
        COF.Layer layer = cof.getLayer(i);
        String component = new String[] {"HD","TR","LG","RA","LA","RH","LH","SH",
            "S1","S2","S3","S4","S5","S6","S7","S8"}[layer.component];
        String dcc = "data/global/monsters/" + wolf.Code + "/" + component + "/" + wolf.Code
            + component + "LIT" + mode + layer.weaponClass + ".dcc";
        assertTrue(Riiablo.mpqs.contains(dcc), dcc);
        ByteBuffer header = ByteBuffer.wrap(Riiablo.mpqs.resolve(dcc).readBytes()).order(ByteOrder.LITTLE_ENDIAN);
        assertTrue((header.get(2) & 255) >= cof.getNumDirections());
        assertTrue(header.getInt(3) >= cof.getNumFramesPerDir());
        System.out.println("FENRIS_DCC path=" + dcc + " frames=" + header.getInt(3)
            + " directions=" + (header.get(2) & 255));
      }
    }
  }

  static Attributes attributes() {
    Attributes a = Attributes.obtainLarge();
    a.base().put(Stat.level, 20); a.base().put(Stat.hitpoints, 100f);
    a.base().put(Stat.maxhp, 100f); a.reset(); return a;
  }

  private static void assertWireMode(CofReference source, byte mode) {
    com.google.flatbuffers.FlatBufferBuilder b = new com.google.flatbuffers.FlatBufferBuilder(64);
    b.finish(new com.riiablo.engine.server.component.serializer.CofReferenceSerializer().putData(b, source));
    com.riiablo.net.packet.d2gs.CofReferenceP wire =
        com.riiablo.net.packet.d2gs.CofReferenceP.getRootAsCofReferenceP(b.dataBuffer());
    assertEquals(mode, wire.mode());
    assertEquals(source.animationRestartId & 0xFFFFFFFFL, wire.animationRestartId());
  }

  private static final class RecordingFactory extends EntityFactory {
    int last = -1, maximum, created;
    @Override public int createSummonedPet(int owner, MonStats.Entry row, String type, int skill,
        int level, int max, boolean passive, int duration, float x, float y) {
      last = world.create(); maximum = max; created++;
      world.getMapper(Monster.class).create(last).set(row, Riiablo.files.monstats2.get(row.MonStatsEx));
      world.getMapper(com.riiablo.engine.server.component.Class.class).create(last).type =
          com.riiablo.engine.server.component.Class.Type.MON;
      world.getMapper(CofReference.class).create(last).set(row.Code, Engine.Monster.MODE_NU);
      world.getMapper(Position.class).create(last).position.set(x, y);
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
    @Override public int createItem(Item item,float x,float y) { return -1; }
    @Override public int createMissile(int a,Vector2 b,Vector2 c) { return -1; }
  }
}
