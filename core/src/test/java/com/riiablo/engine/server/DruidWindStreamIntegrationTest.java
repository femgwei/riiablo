package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.save.CharData;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DruidWindStreamIntegrationTest extends RiiabloTest {
  @Test void nativeInfernoAndArcticShareFirstCallbackRewindRefreshAndExpiry() {
    for (int skillId : new int[] {SkillId.INFERNO, SkillId.ARCTIC_BLAST}) {
      try (Fixture f = new Fixture()) {
        Skills.Entry skill = Riiablo.files.skills.get(skillId);
        AnimData anim = f.world.getMapper(AnimData.class).create(f.caster);
        anim.frame = 10 << 8;
        assertTrue(f.system.startInfernoChannel(f.caster, skill, -1, new Vector2(20, 0)));
        assertEquals(10 << 8, anim.frame, "initial start does not rewind before its first keyframe");
        f.cast(skill);
        assertTrue(f.children.isEmpty());
        assertEquals(9 << 8, anim.frame);
        assertEquals(8, anim.lastKeyframeIndex);
        f.cast(skill);
        assertEquals(1, f.children.size());
        assertEquals(8 << 8, anim.frame);
        assertTrue(f.system.startInfernoChannel(f.caster, skill, -1, new Vector2(20, 0)));
        assertEquals(6 << 8, anim.frame,
            "native refresh rewinds once in StartInferno and once in DoInferno");
        assertEquals(2, f.children.size());
        com.riiablo.engine.server.state.StateList states = f.world.getMapper(UnitStates.class)
            .get(f.caster).stateList;
        for (int i = 0; i < 6; i++) states.update();
        assertFalse(states.hasState(StateId.INFERNO));
        f.cast(skill);
        assertEquals(2, f.children.size(), "expired channel cannot keep emitting");
        assertEquals(6 << 8, anim.frame, "expired channel releases the animation loop");
      }
    }
  }

  @Test void arcticStartRefreshFirstTickAndFractionalStreamSnapshot() {
    try (Fixture f = new Fixture()) {
      Skills.Entry skill = Riiablo.files.skills.get(SkillId.ARCTIC_BLAST);
      com.riiablo.attributes.StatRef mana = f.world.getMapper(AttributesWrapper.class)
          .get(f.caster).attrs.get(Stat.mana);
      float beforeMana = mana.asFixed();
      assertTrue(f.system.startArcticBlast(f.caster, skill, -1, new Vector2(20, 0)));
      float cost = com.riiablo.engine.server.skill.NativeSkillResolver.manaCost(skill, 1);
      assertEquals(beforeMana - cost, mana.asFixed(), .00001f);
      UnitState channel = f.world.getMapper(UnitStates.class).get(f.caster)
          .stateList.getState(StateId.INFERNO);
      assertEquals(20, channel.duration);
      f.cast(skill);
      assertTrue(f.children.isEmpty(), "native first do callback only arms Param1");
      f.cast(skill);
      assertEquals(1, f.children.size());
      assertEquals(beforeMana - cost, mana.asFixed(), .00001f,
          "native usemanaondo is false: per-frame stream callbacks do not spend again");
      Missile stream = f.children.get(0);
      assertEquals(SkillId.ARCTIC_BLAST, stream.skillId);
      assertEquals(SkillFormula.evaluate(skill.calc1, skill, 1), stream.nativeLifetimeFrames);
      assertEquals(0f, stream.range);
      assertTrue(stream.fixedElementalRate);
      assertEquals(com.riiablo.engine.server.combat.CombatSystem.DAMAGE_COLD, stream.fixedElementalType);
      assertTrue(stream.elementalMinRateFixed > 0);
      assertTrue(stream.elementalMinRateFixed < 256, "native HitShift preserves sub-life cold damage");
      assertTrue(stream.onHitStateDuration > 0);
      assertTrue(f.system.startArcticBlast(f.caster, skill, -1, new Vector2(20, 0)));
      assertEquals(6, channel.duration);
      assertEquals(beforeMana - 2 * cost, mana.asFixed(), .00001f);
      assertEquals(2, f.children.size(), "refresh immediately executes the armed stream callback");
    }
  }

  @Test void nativeArcticStartManaGateAllowsLowManaOnlyWhileRefreshing() {
    try (Fixture f = new Fixture()) {
      Skills.Entry skill = Riiablo.files.skills.get(SkillId.ARCTIC_BLAST);
      com.riiablo.attributes.StatRef mana = f.world.getMapper(AttributesWrapper.class)
          .get(f.caster).attrs.get(Stat.mana);
      mana.set(5f);
      assertFalse(f.system.startArcticBlast(f.caster, skill, -1, new Vector2(20, 0)));
      assertFalse(f.world.getMapper(UnitStates.class).get(f.caster).stateList.hasState(StateId.INFERNO));
      mana.set(6f);
      assertTrue(f.system.startArcticBlast(f.caster, skill, -1, new Vector2(20, 0)));
      mana.set(1f);
      assertTrue(f.system.startArcticBlast(f.caster, skill, -1, new Vector2(20, 0)));
      mana.set(0f);
      assertFalse(f.system.startArcticBlast(f.caster, skill, -1, new Vector2(20, 0)));
    }
  }

  @Test void nativeWindCountAndIndependentSeededChargedBoltPathsReachLocalAuthority() {
    for (int skillId : new int[] {SkillId.TWISTER, SkillId.TORNADO}) {
      try (Fixture f = new Fixture()) {
        Skills.Entry skill = Riiablo.files.skills.get(skillId);
        f.cast(skill);
        assertEquals(SkillFormula.evaluate(skill.calc1, skill, 1), f.children.size());
        for (int i = 0; i < f.children.size(); i++) {
          Missile child = f.children.get(i);
          assertEquals(skillId, child.skillId);
          assertTrue(child.authoritative);
          assertFalse(child.presentationOnly);
          assertTrue(child.chargedBoltPath);
          assertEquals(2f, child.chargedBoltNextTurnDistance);
          assertTrue(child.nativeLifetimeFrames > 0 && child.nativeLifetimeFrames <= 77);
          assertTrue(child.damageSnapshot);
          if (i > 0) assertNotEquals(f.children.get(i - 1).chargedBoltSeedLow, child.chargedBoltSeedLow);
        }
      }
    }
  }

  @Test void cycloneArmorReachesSharedDefensiveDispatch() {
    try (Fixture f = new Fixture()) {
      f.cast(Riiablo.files.skills.get(235));
      UnitState armor = f.world.getMapper(UnitStates.class).get(f.caster)
          .stateList.getState(StateId.CYCLONEARMOR);
      assertNotNull(armor);
      assertEquals(235, armor.skillId);
      assertTrue(armor.runtimeValue > 0, "Cyclone Armor installs a live absorb pool");
    }
  }

  private static final class Fixture implements AutoCloseable {
    final EntityFactory factory = mock(EntityFactory.class);
    final ServerSkillSystem system = new ServerSkillSystem(true);
    final List<Missile> children = new ArrayList<>();
    final World world;
    final int caster;
    Fixture() {
      world = new World(new WorldConfigurationBuilder()
          .with(new net.mostlyoriginal.api.event.common.EventSystem(), system, factory).build()
          .register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
      when(factory.createMissile(any(Missiles.Entry.class), any(Vector2.class),
          any(Vector2.class), anyInt(), anyInt())).thenAnswer(call -> {
            int id = world.create();
            Missiles.Entry row = call.getArgument(0);
            Missile missile = world.getMapper(Missile.class).create(id)
                .set(row, call.getArgument(2), row.Range).setOwner(call.getArgument(3));
            world.getMapper(Position.class).create(id).position.set(call.getArgument(2));
            world.getMapper(Velocity.class).create(id).velocity.set((Vector2) call.getArgument(1));
            children.add(missile);
            return id;
          });
      caster = world.create();
      CharData data = CharData.createRemote("wind", (byte) Riiablo.DRUID);
      for (int skill : new int[] {41, 230, 235, 240, 245}) data.setSkillLevel(skill, 1);
      world.getMapper(Player.class).create(caster).data = data;
      world.getMapper(Position.class).create(caster);
      world.getMapper(UnitStates.class).create(caster).init(world, caster);
      Attributes attrs = Attributes.obtainStandard();
      attrs.base().put(Stat.hitpoints, 1000);
      attrs.base().put(Stat.mana, 1000);
      attrs.base().put(Stat.maxmana, 1000);
      attrs.reset();
      world.getMapper(AttributesWrapper.class).create(caster).attrs = attrs;
      world.process();
    }
    void cast(Skills.Entry skill) {
      system.onSkillDo(SkillDoEvent.obtain(caster, skill.Id, -1, new Vector2(20, 0),
          skill.srvdofunc, skill.cltdofunc));
    }
    @Override public void close() { world.dispose(); }
  }
}
