package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.COF;
import com.riiablo.codec.D2;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.state.StateId;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HungerAnimationDataTest extends RiiabloTest {
  @ParameterizedTest @CsvSource({"wolf,40", "bear,TG"})
  void shapeS3ResolvesNativeCofKeyframeAndAllBodyLayers(String shape, String token) {
    D2 previous = Riiablo.anim;
    Riiablo.anim = D2.loadFromFile(Riiablo.mpqs.resolve("data/global/eanimdata.d2"));
    World world = new World(new WorldConfigurationBuilder().with(new EventSystem(),
        new CofManager(), new DruidShapeShiftResolver(), new AnimDataResolver()).build());
    try {
      int druid = world.create();
      world.getMapper(com.riiablo.engine.server.component.Class.class).create(druid).type =
          com.riiablo.engine.server.component.Class.Type.PLR;
      CofReference ref = world.getMapper(CofReference.class).create(druid).set("DZ", Engine.Player.MODE_S3);
      world.getMapper(UnitStates.class).create(druid).init(druid).stateList
          .addState("wolf".equals(shape) ? StateId.WOLF : StateId.BEAR, 2000, 1, druid);
      world.process();
      assertEquals(token, ref.effectiveToken());
      assertEquals(Engine.Monster.MODE_S3,
          ref.effectiveMode(com.riiablo.engine.server.component.Class.Type.PLR));
      String key = token + "S3HTH";
      D2.Entry anim = Riiablo.anim.getEntry(key); assertNotNull(anim, key);
      AnimData resolved = world.getMapper(AnimData.class).get(druid); assertNotNull(resolved);
      assertEquals(anim.framesPerDir, resolved.numFrames >>> 8);
      assertArrayEquals(anim.data, resolved.keyframes);
      int attacks = 0;
      for (int frame = 0; frame < anim.framesPerDir; frame++) if (anim.data[frame] == Engine.KEYFRAME_ATK) attacks++;
      assertEquals(1, attacks, "Hunger uses a single attack marker");
      COF cof = COF.loadFromFile(Riiablo.mpqs.resolve("data/global/monsters/" + token + "/cof/" + key + ".cof"));
      assertEquals(anim.framesPerDir, cof.getNumFramesPerDir());
      for (int i = 0; i < cof.getNumLayers(); i++) {
        COF.Layer layer = cof.getLayer(i);
        String part = new String[] {"HD","TR","LG","RA","LA","RH","LH","SH",
            "S1","S2","S3","S4","S5","S6","S7","S8"}[layer.component];
        String path = "data/global/monsters/" + token + "/" + part + "/" + token
            + part + "LITS3" + layer.weaponClass + ".dcc";
        assertTrue(Riiablo.mpqs.contains(path), path);
        ByteBuffer header = ByteBuffer.wrap(Riiablo.mpqs.resolve(path).readBytes()).order(ByteOrder.LITTLE_ENDIAN);
        assertTrue(header.getInt(3) >= cof.getNumFramesPerDir());
        assertTrue((header.get(2) & 255) >= cof.getNumDirections());
        System.out.println("HUNGER_BODY path=" + path + " frames=" + header.getInt(3)
            + " directions=" + (header.get(2) & 255));
      }
      assertEquals("S3", Riiablo.files.skills.get(242).anim);
      System.out.println("HUNGER_ANIM shape=" + shape + " key=" + key + " frames="
          + anim.framesPerDir + " speed=" + anim.speed + " atk_markers=" + attacks);
    } finally { world.dispose(); Riiablo.anim = previous; }
  }
}
