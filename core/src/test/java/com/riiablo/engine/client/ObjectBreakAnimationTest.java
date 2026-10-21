package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.Riiablo;
import com.riiablo.Colors;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.Animation;
import com.riiablo.codec.COF;
import com.riiablo.codec.excel.Objects;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.ObjectInteractor;
import com.riiablo.engine.server.SequenceHandler;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.NativeObjectState;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.event.AnimDataFinishedEvent;
import com.riiablo.engine.server.event.ObjectInteractionEvent;
import com.riiablo.engine.server.event.ModeChangeEvent;
import com.riiablo.engine.server.object.NativeObjectOperateTable;
import com.riiablo.map.Map;
import com.riiablo.map.NativePresetObjectResolver;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.nio.file.Paths;

class ObjectBreakAnimationTest extends RiiabloTest {
  private static final int[] CONTAINERS = {
      1, 3, 4, 5, 6, 7, 9, 11, 28, 46, 50, 51, 52, 53, 54, 55, 56, 57, 58,
      79, 87, 89, 94, 95, 104, 105, 106, 107, 125, 126, 127, 128, 139, 140,
      142, 143, 154, 155, 158, 159, 163, 169, 174, 175, 178, 179, 180, 182,
      185, 186, 187, 188, 203, 204, 205, 208, 209, 222, 223, 224, 225, 233,
      244, 246, 247, 248, 250, 259, 266, 284, 289, 326, 360, 362, 363, 365,
      372, 373, 383, 384, 416, 418, 419, 420, 424, 425, 430, 431, 432, 433,
      443, 444, 445, 448, 454, 455, 463, 466, 467, 468, 469, 470, 471, 477,
      485, 500, 504, 516, 517, 518, 524, 535, 540, 541, 544, 545, 548, 549,
      550, 551, 556
  };
  private static Colors savedColors;

  @BeforeAll static void initializeColors() {
    savedColors = Riiablo.colors;
    Riiablo.colors = new Colors();
  }

  @AfterAll static void restoreColors() {
    Riiablo.colors = savedColors;
  }

  @Test void destroyedFrameSurvivesPlaybackAndReconstruction() {
    for (int id : CONTAINERS) {
      Objects.Entry base = Riiablo.files.objects.get(id);
      String token = Riiablo.files.ObjType.get(id).Token;
      String path = "data/global/objects/" + token + "/cof/" + token + "ONhth.cof";
      COF cof = COF.loadFromFile(Riiablo.mpqs.resolve(path));
      Animation animation = Animation.newAnimation(cof);
      int expected = Math.min(base.Start[Engine.Object.MODE_ON], cof.getNumFramesPerDir() - 1);
      CofLayerCacher.configureObjectAnimation(animation, base, Engine.Object.MODE_ON);
      assertEquals(expected, animation.getFrame(), "opened object " + id);
      for (int tick = 0; tick < 50; tick++) animation.update(0.04f);
      assertEquals(expected, animation.getFrame(), "must keep the broken frame " + id);
      CofLayerCacher.configureObjectAnimation(animation, base, Engine.Object.MODE_ON);
      assertEquals(expected, animation.getFrame(), "room recreation " + id);
    }
  }
}
