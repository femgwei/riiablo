package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.combat.NativeLeechMath;
import com.riiablo.engine.server.event.*;
import com.riiablo.engine.server.skill.DruidSkills;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import java.util.ArrayList;
import java.util.List;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HungerIntegrationTest extends RiiabloTest {
  @ParameterizedTest @CsvSource({"wolf,100,false", "bear,100,false", "wolf,0,false", "bear,100,true"})
  void realAttackKeyframeSettlesPhysicalLeechAndOneAuthoritativeCue(
      String shape, int drain, boolean fullPools) {
    Actioneer actioneer = new Actioneer(); EntityFactory factory = mock(EntityFactory.class);
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), actioneer, new Pathfinder(), factory).build()
        .register("factory", factory).register("map", new Map(0,0)));
    try {
      Skills.Entry skill = Riiablo.files.skills.get(242);
      int source = world.create(), target = world.create();
      Attributes attacker = attributes(fullPools ? 1000 : 10, 1000);
      attacker.base().put(Stat.mindamage, 40); attacker.base().put(Stat.maxdamage, 40);
      attacker.base().put(Stat.strength, 100); attacker.base().put(Stat.tohit, 100000);
      attacker.reset();
      world.getMapper(AttributesWrapper.class).create(source).attrs = attacker;
      world.getMapper(com.riiablo.engine.server.component.Class.class).create(source).type =
          com.riiablo.engine.server.component.Class.Type.PLR;
      CharData character = CharData.createRemote("hunger", (byte) Riiablo.DRUID); character.setSkillLevel(242,1);
      world.getMapper(Player.class).create(source).data = character;
      world.getMapper(UnitStates.class).create(source).init(source).stateList
          .addState("wolf".equals(shape) ? StateId.WOLF : StateId.BEAR, 2000, 1, source);
      world.getMapper(Position.class).create(source).position.set(0,0);
      world.getMapper(AttributesWrapper.class).create(target).attrs = attributes(10000,10000);
      world.getMapper(com.riiablo.engine.server.component.Class.class).create(target).type =
          com.riiablo.engine.server.component.Class.Type.MON;
      MonStats.Entry mon = new MonStats.Entry(); mon.Drain = new int[] {drain,drain,drain};
      world.getMapper(Monster.class).create(target).monstats = mon;
      world.getMapper(UnitStates.class).create(target).init(target);
      world.getMapper(Position.class).create(target).position.set(1,0);
      List<DamageAppliedEvent> hits = new ArrayList<>();
      world.getSystem(EventSystem.class).registerEvents(new java.lang.Object() {
        @Subscribe public void applied(DamageAppliedEvent event) { hits.add(event); }
      });
      world.process();
      for (int tries=0; tries<64 && hits.isEmpty(); tries++) {
        world.getMapper(Casting.class).create(source).set(242,target,new Vector2(1,0));
        assertEquals(fullPools ? 1000f : 10f, attacker.get(Stat.hitpoints).asFixed());
        actioneer.onAnimDataKeyframe(AnimDataKeyframeEvent.obtain(source,Engine.KEYFRAME_ATK));
      }
      assertEquals(1,hits.size(), "a successful native hit must be reachable");
      DamageAppliedEvent hit = hits.get(0);
      assertEquals(20f,hit.physicalDamage); assertEquals(20f,hit.damage);
      assertEquals(9980f,world.getMapper(AttributesWrapper.class).get(target).attrs.get(Stat.hitpoints).asFixed());
      int life = DruidSkills.getHungerLifeLeech(skill,1,name -> 0);
      int mana = DruidSkills.getHungerManaLeech(skill,1,name -> 0);
      float expectedLife = fullPools ? 0 : NativeLeechMath.percentage(20*256,10000*256,life,1,drain)/256f;
      float expectedMana = fullPools ? 0 : NativeLeechMath.percentage(20*256,10000*256,mana,1,drain)/256f;
      assertEquals(expectedLife,hit.lifeLeeched); assertEquals(expectedMana,hit.manaLeeched);
      assertEquals((fullPools ? 1000f : 10f)+expectedLife,attacker.get(Stat.hitpoints).asFixed());
      assertEquals((fullPools ? 1000f : 10f)+expectedMana,attacker.get(Stat.mana).asFixed());
      if (drain == 0) assertNull(hit.leechOverlay);
      else assertTrue("steallife".equals(hit.leechOverlay) || "stealmana".equals(hit.leechOverlay),
          "dual leech chooses one cue, including when both resource pools are full");
      System.out.println("HUNGER_HIT shape="+shape+" physical="+hit.physicalDamage+" drain="+drain
          +" full="+fullPools+" life="+hit.lifeLeeched+" mana="+hit.manaLeeched+" overlay="+hit.leechOverlay);
    } finally { world.dispose(); }
  }

  private static Attributes attributes(int current, int maximum) {
    Attributes attrs=Attributes.obtainStandard();
    attrs.base().put(Stat.hitpoints,(float)current); attrs.base().put(Stat.maxhp,(float)maximum);
    attrs.base().put(Stat.mana,(float)current); attrs.base().put(Stat.maxmana,(float)maximum);
    attrs.base().put(Stat.level,1); attrs.base().put(Stat.armorclass,0); attrs.reset(); return attrs;
  }
}
