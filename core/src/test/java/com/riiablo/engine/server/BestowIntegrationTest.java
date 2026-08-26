package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.engine.Engine;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.ai.GreaterMummy;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.MovementModes;
import com.riiablo.engine.EntityFactory;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import com.riiablo.map.Map;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Headless coverage for native SrvDo096 ZakarumHeal/Bestow. */
class BestowIntegrationTest extends RiiabloTest {
  @Test
  void unravelerBestowUsesNativeHealFallbackRange() {
    MonStats.Entry row = Riiablo.files.monstats.get("unraveler1");
    assertTrue(row != null && "Bestow".equals(row.Skill2));
    Skills.Entry bestow = Riiablo.files.skills.get(row.Skill2);
    assertEquals(96, bestow.srvdofunc);
    // Bestow's exported row intentionally has empty calc columns. Native
    // Skills.bin resolves the shared ZakarumHeal range (15 + 5*lvl .. 50).
    int[] range = Actioneer.resolveBestowPercentRange(bestow, row.Sk2lvl);
    assertEquals(20, range[0]);
    assertEquals(50, range[1]);
    assertEquals(1, range[2]);
    MathUtils.random.setSeed(0xBE57L);
    for (int i = 0; i < 32; i++) {
      int value = MathUtils.random(range[0], range[1] - 1);
      assertTrue(value >= 20 && value < 50);
    }
    System.out.println("[BESTOW_AUDIT] monster=" + row.Id + " skill=" + bestow.skill
        + " srvDoFunc=" + bestow.srvdofunc + " healRange=" + range[0] + ".."
        + range[1] + " fallbackZakarumHeal=" + range[2] + " status=PASS");
  }

  @Test
  void bestowTargetMustBeWoundedUndead() {
    MonStats.Entry candidateRow = new MonStats.Entry();
    candidateRow.lUndead = true;
    Monster candidate = new Monster().set(candidateRow, new MonStats2.Entry());
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.hitpoints, 40);
    attrs.base().put(Stat.maxhp, 100);
    attrs.reset();
    assertTrue(GreaterMummy.isBestowEligible(candidate, attrs));

    attrs.base().put(Stat.hitpoints, 100);
    attrs.reset();
    assertFalse(GreaterMummy.isBestowEligible(candidate, attrs));
    candidateRow.lUndead = false;
    assertFalse(GreaterMummy.isBestowEligible(candidate, attrs));
    System.out.println("[BESTOW_TARGET_AUDIT] woundedUndead=true fullHealth=false livingNonUndead=false status=PASS");
  }

  @Test
  void bestowKeyframeRestoresTargetLifeAuthoritatively() {
    MonStats.Entry sourceRow = Riiablo.files.monstats.get("unraveler1");
    MonStats2.Entry sourceRow2 = Riiablo.files.monstats2.get(sourceRow.MonStatsEx);
    Skills.Entry bestow = Riiablo.files.skills.get("Bestow");
    Actioneer actioneer = new Actioneer();
    NoopFactory factory = new NoopFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), actioneer, new Pathfinder(), factory)
        .build().register("map", new Map(0, 0)).register("factory", factory));
    try {
      int source = world.create();
      world.getMapper(Monster.class).create(source).set(sourceRow, sourceRow2);
      world.getMapper(Class.class).create(source).type = Class.Type.MON;
      world.getMapper(Position.class).create(source).position.set(10, 10);
      world.getMapper(Angle.class).create(source);
      world.getMapper(MovementModes.class).create(source).set(
          Engine.Monster.MODE_NU, Engine.Monster.MODE_WL, Engine.Monster.MODE_RN);
      world.getMapper(AttributesWrapper.class).create(source).attrs = life(100, 1f);

      int target = world.create();
      world.getMapper(Monster.class).create(target).set(sourceRow, sourceRow2);
      world.getMapper(Class.class).create(target).type = Class.Type.MON;
      world.getMapper(Position.class).create(target).position.set(11, 10);
      Attributes targetAttrs = life(100, 40f);
      world.getMapper(AttributesWrapper.class).create(target).attrs = targetAttrs;

      actioneer.cast(source, bestow.Id, target, new Vector2(11, 10));
      float before = targetAttrs.get(Stat.hitpoints).asFixed();
      MathUtils.random.setSeed(0xBE57L);
      world.getSystem(EventSystem.class).dispatch(
          com.riiablo.engine.server.event.AnimDataKeyframeEvent.obtain(source, Engine.KEYFRAME_ATK));
      float after = targetAttrs.get(Stat.hitpoints).asFixed();
      assertTrue(after > before && after <= 100f);
      System.out.println("[BESTOW_CHAIN] source=" + source + " target=" + target
          + " hp=" + before + "->" + after + " status=PASS");
    } finally {
      world.dispose();
    }
  }

  private static Attributes life(float max, float current) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.maxhp, max);
    attrs.base().put(Stat.hitpoints, current);
    attrs.base().put(Stat.level, 1);
    attrs.reset();
    return attrs;
  }

  private static final class NoopFactory extends EntityFactory {
    @Override public int createPlayer(CharData data, Vector2 position) { return -1; }
    @Override public int createDynamicObject(int act, int preset, float x, float y) { return -1; }
    @Override public int createStaticObject(int act, int object, float x, float y) { return -1; }
    @Override public int createStaticObjectByClassId(int object, float x, float y) { return -1; }
    @Override public int createMonster(int monster, float x, float y) { return -1; }
    @Override public int createWarp(int index, float x, float y) { return -1; }
    @Override public int createItem(Item item, float x, float y) { return -1; }
    @Override public int createMissile(int missile, Vector2 angle, Vector2 position) { return -1; }
  }
}
