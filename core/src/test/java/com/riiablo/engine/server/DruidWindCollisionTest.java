package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.state.StateId;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class DruidWindCollisionTest extends RiiabloTest {
  @Test void twisterContactAppliesNativeTenFrameStun() {
    try (Fixture f = new Fixture("Twister", "twister", 10.2f)) {
      f.world.process();
      assertNotNull(f.world.getMapper(UnitStates.class).get(f.target)
          .stateList.getState(StateId.STUNNED));
      assertEquals(10, f.world.getMapper(UnitStates.class).get(f.target)
          .stateList.getState(StateId.STUNNED).duration);
    }
  }

  @Test void tornadoFifteenFrameAreaPulseRemainsIndependentOfContactHitGate() {
    try (Fixture f = new Fixture("Tornado", "tornado", 12f)) {
      f.world.getMapper(UnitStates.class).create(f.target).init(f.world, f.target)
          .stateList.addState(StateId.JUSTHIT, 100, 1, f.owner);
      f.missile.hitTargets.add(f.target);
      f.world.process(); // countdown75 -> first independent area pulse
      float first = f.life();
      assertTrue(first >= 965f && first <= 975f);
      for (int i = 0; i < 14; i++) f.world.process();
      assertEquals(first, f.life(), 0.001f);
      f.world.process(); // countdown60 -> second independent area pulse
      float second = f.life();
      assertTrue(first - second >= 25f && first - second <= 35f);
      assertTrue(f.missile.hitTargets.contains(f.target));
    }
  }

  private static final class Fixture implements AutoCloseable {
    final World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new MissileCollisionSystem()).build());
    final int owner;
    final int target;
    final Missile missile;

    Fixture(String skillName, String missileName, float targetX) {
      world.setDelta(1f / 25f);
      owner = world.create();
      world.getMapper(Player.class).create(owner);
      world.getMapper(Position.class).create(owner).position.set(8, 10);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes();
      target = world.create();
      MonStats.Entry stats = new MonStats.Entry();
      stats.Velocity = 6;
      world.getMapper(Monster.class).create(target).set(stats, new MonStats2.Entry());
      world.getMapper(Position.class).create(target).position.set(targetX, 10);
      world.getMapper(AttributesWrapper.class).create(target).attrs = attributes();
      Skills.Entry skill = Riiablo.files.skills.get(skillName);
      Missiles.Entry row = Riiablo.files.Missiles.get(missileName);
      int id = world.create();
      missile = world.getMapper(Missile.class).create(id)
          .set(row, new Vector2(10, 10), row.Range).setOwner(owner);
      world.getMapper(Position.class).create(id).position.set(10, 10);
      world.getMapper(Velocity.class).create(id);
      MissileDamageResolver.initializeSkill(Riiablo.files, missile, skill,
          world.getMapper(AttributesWrapper.class).get(owner).attrs, 1, n -> 0);
    }

    float life() { return world.getMapper(AttributesWrapper.class).get(target).attrs.get(Stat.hitpoints).asFixed(); }
    @Override public void close() { world.dispose(); }
  }

  private static Attributes attributes() {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.level, 20);
    attrs.base().put(Stat.hitpoints, 1000f);
    attrs.base().put(Stat.maxhp, 1000f);
    attrs.reset();
    return attrs;
  }
}
