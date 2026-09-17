package com.riiablo.engine.client.automap;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.Cvars;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.codec.excel.Objects;
import com.riiablo.engine.server.monster.MonsterRank;
import org.junit.jupiter.api.Test;

class AutomapMarkerPolicyTest {
  @Test void classifiesNativeMonsterRanks() {
    assertEquals(AutomapIconType.MONSTER,
        AutomapMarkerPolicy.monsterType(MonsterRank.NORMAL));
    assertEquals(AutomapIconType.CHAMPION,
        AutomapMarkerPolicy.monsterType(MonsterRank.CHAMPION));
    assertEquals(AutomapIconType.UNIQUE,
        AutomapMarkerPolicy.monsterType(MonsterRank.UNIQUE));
    assertEquals(AutomapIconType.UNIQUE,
        AutomapMarkerPolicy.monsterType(MonsterRank.SUPER_UNIQUE));
    assertEquals(AutomapIconType.MINION,
        AutomapMarkerPolicy.monsterType(MonsterRank.MINION));
    assertEquals(AutomapIconType.BOSS,
        AutomapMarkerPolicy.monsterType(MonsterRank.BOSS));
  }

  @Test void classifiesQuestAndDungeonObjectsFromTxtFields() {
    Objects.Entry entrance = new Objects.Entry();
    entrance.OpenWarp = true;
    assertEquals(AutomapIconType.ENTRANCE, AutomapMarkerPolicy.objectType(entrance));

    Objects.Entry quest = new Objects.Entry();
    quest.AutoMap = AutomapIconType.CAIN_CAGE;
    assertEquals(AutomapIconType.QUEST, AutomapMarkerPolicy.objectType(quest));

    Objects.Entry ordinary = new Objects.Entry();
    ordinary.AutoMap = AutomapIconType.WAYPOINT;
    assertEquals(AutomapIconType.OBJECT, AutomapMarkerPolicy.objectType(ordinary));
  }

  @Test void hidesOrdinarySceneryWithoutNativeAutomapCell() {
    assertFalse(AutomapMarkerPolicy.shouldDisplayObject(-1, AutomapIconType.OBJECT));
    assertTrue(AutomapMarkerPolicy.shouldDisplayObject(405, AutomapIconType.OBJECT));
    assertTrue(AutomapMarkerPolicy.shouldDisplayObject(-1, AutomapIconType.ENTRANCE));
    assertTrue(AutomapMarkerPolicy.shouldDisplayObject(-1, AutomapIconType.QUEST));
  }

  @Test void hidesNeutralAndDecorativeMonstersButKeepsNpcsAndHostiles() {
    MonStats.Entry monster = new MonStats.Entry();
    MonStats2.Entry visual = new MonStats2.Entry();
    monster.Id = "fallen1";
    monster.Align = 0;
    monster.killable = true;
    assertTrue(AutomapMarkerPolicy.shouldDisplayMonster(monster, visual, false));

    monster.Align = 1;
    assertFalse(AutomapMarkerPolicy.shouldDisplayMonster(monster, visual, false));
    assertTrue(AutomapMarkerPolicy.shouldDisplayMonster(monster, visual, true));

    monster.Align = 0;
    monster.killable = false;
    assertFalse(AutomapMarkerPolicy.shouldDisplayMonster(monster, visual, false));

    monster.killable = true;
    monster.Id = "chicken";
    assertFalse(AutomapMarkerPolicy.shouldDisplayMonster(monster, visual, false));
    monster.Id = "quillrat1";
    assertTrue(AutomapMarkerPolicy.shouldDisplayMonster(monster, visual, false));

    monster.Id = "spearcat1";
    assertTrue(AutomapMarkerPolicy.shouldDisplayMonster(monster, visual, false));
    monster.Id = "ratman1";
    assertTrue(AutomapMarkerPolicy.shouldDisplayMonster(monster, visual, false));

    visual.critter = true;
    assertFalse(AutomapMarkerPolicy.shouldDisplayMonster(monster, visual, false));
    assertTrue(AutomapMarkerPolicy.shouldDisplayMonster(monster, visual, true));

    visual.critter = false;
    visual.noMap = true;
    assertFalse(AutomapMarkerPolicy.shouldDisplayMonster(monster, visual, false));
  }

  @Test void distantPointerPreservesDirectionAndCapsDistance() {
    Vector2 source = new Vector2(-20, 30);
    Vector2 target = new Vector2(380, 330);
    Vector2 out = new Vector2();
    assertTrue(AutomapMarkerPolicy.projectPointer(source, target, true, out));
    assertEquals(AutomapMarkerPolicy.POINTER_RADIUS, source.dst(out), 0.001f);
    Vector2 expectedDirection = target.cpy().sub(source).nor();
    Vector2 actualDirection = out.cpy().sub(source).nor();
    assertEquals(expectedDirection.x, actualDirection.x, 0.0001f);
    assertEquals(expectedDirection.y, actualDirection.y, 0.0001f);
    assertFalse(AutomapMarkerPolicy.projectPointer(source, target, false, out));
    assertEquals(target, out);
  }

  @Test void nearbyTargetRemainsAtItsRealPosition() {
    Vector2 source = new Vector2(-10, -20);
    Vector2 target = new Vector2(-5, -12);
    Vector2 out = new Vector2();
    assertFalse(AutomapMarkerPolicy.projectPointer(source, target, true, out));
    assertEquals(target, out);
  }

  @Test void enhancedRanksAndDestinationsOverlayNativeCells() {
    assertTrue(AutomapMarkerPolicy.requiresColorOverlay(AutomapIconType.CHAMPION));
    assertTrue(AutomapMarkerPolicy.requiresColorOverlay(AutomapIconType.UNIQUE));
    assertTrue(AutomapMarkerPolicy.requiresColorOverlay(AutomapIconType.MINION));
    assertTrue(AutomapMarkerPolicy.requiresColorOverlay(AutomapIconType.BOSS));
    assertTrue(AutomapMarkerPolicy.requiresColorOverlay(AutomapIconType.QUEST));
    assertTrue(AutomapMarkerPolicy.requiresColorOverlay(AutomapIconType.ENTRANCE));
    assertFalse(AutomapMarkerPolicy.requiresColorOverlay(AutomapIconType.MONSTER));
  }

  @Test void enhancedDevelopmentOptionsDefaultOn() {
    assertEquals(Boolean.TRUE, Cvars.Client.Automap.ShowCorpses.get());
    assertEquals(Boolean.TRUE, Cvars.Client.Automap.ShowMissiles.get());
    assertEquals(Boolean.TRUE, Cvars.Client.Automap.ShowItems.get());
    assertEquals(Boolean.TRUE, Cvars.Client.Automap.ShowMonsterRanks.get());
    assertEquals(Boolean.TRUE, Cvars.Client.Automap.ShowQuestIndicators.get());
    assertEquals(Boolean.TRUE, Cvars.Client.Automap.ShowMinimapPointers.get());
  }
}
