package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.utils.Array;
import org.junit.jupiter.api.Test;

/** Verifies the shared compatibility-unit gate for all Act II--V town bridges. */
class Act2Act3Act4Act5TownCompatibilityTest {
  @Test
  void matchingTownSourcesSuppressOnlyDynamicNpcObjects() {
    String[] townSources = {
        "Act2/Town/LutGholein.ds1",
        "Act3/Town/KurastDock.ds1",
        "Act4/Town/PandemoniumFortress.ds1",
        "Act5/Town/Harrogath.ds1"
    };

    for (String source : townSources) {
      Map.Zone town = new Map.Zone();
      town.town = true;
      town.replaceNativeTownMonsters(source,
          new Array<Map.NativeMonster>() {{
            add(new Map.NativeMonster(0, 1, 0, 10, 10, -1, null, false));
          }});

      assertFalse(MapManager.shouldCreateCompatibilityPresetUnit(town,
          "data\\global\\tiles\\" + source, DS1.Object.DYNAMIC_TYPE), source);
      assertTrue(MapManager.shouldCreateCompatibilityPresetUnit(town, source,
          DS1.Object.STATIC_TYPE), source);
      assertTrue(MapManager.shouldCreateCompatibilityPresetUnit(town,
          source + ".other", DS1.Object.DYNAMIC_TYPE), source);
    }
  }
}
