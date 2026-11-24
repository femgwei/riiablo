package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.event.MissileImpactEvent;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DruidRecyclerRecoveryTest extends RiiabloTest {
  @ParameterizedTest @ValueSource(strings = {"CorpseCycler", "VineCycler"})
  void nativeRecoveryTruncatesWholeResourceAndPublishesOwnerOverlayExactlyOnce(String name) {
    try (Fixture f = new Fixture(name)) {
      int percent = SkillFormula.evaluate(f.skill.calc1, f.skill, 7,
          n -> f.data.getBaseSkillLevel(Riiablo.files.skills.index(n)),
          n -> Riiablo.files.skills.get(n));
      assertTrue(percent > 0, "effective native Calc1 must restore a resource: " + f.skill.calc1);
      f.advanceToTrigger();
      int initial = f.mana ? 50 : 100, maximum = f.mana ? 100 : 200;
      assertEquals((float) Math.min(maximum, initial + maximum * percent / 100),
          f.attrs.get(f.current).asFixed(), .0001f);
      assertTrue(f.missile.recyclerRecovered);
      assertEquals(1, f.probe.overlays);
      assertNotNull(f.probe.last);
      assertEquals(f.owner, f.probe.last.targetEntityId);
      assertEquals(f.row.ProgOverlay, f.probe.last.targetOverlay);
      float recovered = f.attrs.get(f.current).asFixed();
      f.world.process();
      assertEquals(recovered, f.attrs.get(f.current).asFixed());
      assertEquals(1, f.probe.overlays);
    }
  }

  @ParameterizedTest @ValueSource(strings = {"CorpseCycler", "VineCycler"})
  void fullResourcesAndDeadOwnersReceiveNeitherRecoveryNorOverlay(String name) {
    for (boolean dead : new boolean[] {false, true}) {
      try (Fixture f = new Fixture(name)) {
        if (dead) f.attrs.get(Stat.hitpoints).set(0f);
        else {
          float maximum = f.attrs.get(f.maximum).asFixed();
          f.attrs.get(f.current).set(maximum);
        }
        float before = f.attrs.get(f.current).asFixed();
        f.advanceToTrigger();
        assertEquals(before, f.attrs.get(f.current).asFixed());
        assertEquals(0, f.probe.overlays);
      }
    }
  }

  private static final class Fixture implements AutoCloseable {
    final Probe probe = new Probe();
    final World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), probe, new MissileCollisionSystem()).build());
    final Skills.Entry skill;
    final Missiles.Entry row;
    final Missile missile;
    final CharData data = CharData.createRemote("vine-recovery", (byte) Riiablo.DRUID);
    final Attributes attrs = Attributes.obtainStandard();
    final int owner;
    final boolean mana;
    final short current, maximum;
    Fixture(String name) {
      skill = Riiablo.files.skills.get(name);
      row = Riiablo.files.Missiles.get(skill.srvmissilea);
      mana = row.pSrvDoFunc == 33;
      current = mana ? Stat.mana : Stat.hitpoints;
      maximum = mana ? Stat.maxmana : Stat.maxhp;
      owner = world.create();
      data.setSkillLevel(SkillId.CARRION_VINE, 7);
      data.setSkillLevel(SkillId.SOLAR_CREEPER, 7);
      world.getMapper(Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(10, 10);
      attrs.base().put(Stat.hitpoints, 100.75f);
      attrs.base().put(Stat.maxhp, 200.75f);
      attrs.base().put(Stat.mana, 50.75f);
      attrs.base().put(Stat.maxmana, 100.75f);
      attrs.reset();
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attrs;
      int id = world.create();
      missile = world.getMapper(Missile.class).create(id).set(row, Vector2.Zero, row.Range).setOwner(owner);
      missile.skillId = skill.Id;
      missile.damageLevel = 7;
      world.getMapper(Position.class).create(id).position.set(11, 10);
      world.getMapper(Velocity.class).create(id);
      world.setDelta(1f / 25f);
    }
    void advanceToTrigger() {
      int frames = row.Range - row.Param[0];
      assertTrue(frames > 0);
      float before = attrs.get(current).asFixed();
      for (int i = 0; i < frames - 1; i++) {
        world.process();
        assertEquals(before, attrs.get(current).asFixed());
        assertEquals(0, probe.overlays);
      }
      world.process();
    }
    @Override public void close() { world.dispose(); }
  }

  public static final class Probe extends BaseSystem {
    int overlays;
    MissileImpactEvent last;
    @Subscribe public void onImpact(MissileImpactEvent e) {
      if (e.targetOverlay != null && !e.targetOverlay.isEmpty()) { overlays++; last = e; }
    }
    @Override protected void processSystem() {}
  }
}
