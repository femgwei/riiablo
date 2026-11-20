package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import com.artemis.*;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class FirestormGroundDamageTest extends RiiabloTest {
  @Test void groundRemainsDamagingAfterMakersDisappearThenExpires() {
    ServerEntityFactory factory = new ServerEntityFactory();
    World world = new World(new WorldConfigurationBuilder().with(new EventSystem(),
        new ObjectInteractor(), new WarpInteractor(), new ItemInteractor(), new ItemManager(),
        new ServerSkillSystem(true), new MissileCollisionSystem(), factory).build()
        .register("factory", factory).register("map", new com.riiablo.map.Map(0, 0))
        .register("townPortalRegistry", new com.riiablo.engine.server.portal.TownPortalRegistry()));
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("firestorm-damage", (byte) Riiablo.DRUID);
      data.setSkillLevel(225, 1);
      world.getMapper(Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).initializeNativeCell(4523, 5642);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes();
      com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(225);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(owner, 225, -1,
          new Vector2(4531, 5642), skill.srvdofunc, skill.cltdofunc));
      world.setDelta(1f / 25f);
      for (int i = 0; i < 5; i++) world.process();
      IntBag ids = world.getAspectSubscriptionManager().get(Aspect.all(Missile.class)).getEntities();
      int[] active = java.util.Arrays.copyOf(ids.getData(), ids.size());
      int fires = 0;
      for (int id : active) {
        Missile m = world.getMapper(Missile.class).get(id);
        if ("firestormmaker".equals(m.missile.Missile)) world.delete(id);
        else {
          fires++;
          assertTrue(m.persistent);
          assertTrue(m.fixedElementalRate);
          assertTrue(m.damageSnapshot);
          assertTrue(m.elementalMinRateFixed > 0);
          assertEquals(41, m.elementalDamageRate);
          assertEquals(15, m.nativeLifetimeFrames);
        }
      }
      assertTrue(fires > 0);
      int target = world.create();
      world.getMapper(Position.class).create(target).initializeNativeCell(4524, 5642);
      com.riiablo.codec.excel.MonStats.Entry row = new com.riiablo.codec.excel.MonStats.Entry();
      row.Id = "firestorm-test-target";
      world.getMapper(Monster.class).create(target).set(row, new com.riiablo.codec.excel.MonStats2.Entry());
      Attributes life = attributes();
      world.getMapper(AttributesWrapper.class).create(target).attrs = life;
      world.getMapper(UnitStates.class).create(target).init(target);
      float before = life.get(Stat.hitpoints).asFixed();
      world.process();
      assertTrue(life.get(Stat.hitpoints).asFixed() < before,
          "ground fire must settle fractional fire damage after makers are removed");
      for (int i = 0; i < 20; i++) world.process();
      float after = life.get(Stat.hitpoints).asFixed();
      for (int i = 0; i < 10; i++) world.process();
      assertEquals(after, life.get(Stat.hitpoints).asFixed());
      assertEquals(0, world.getAspectSubscriptionManager().get(Aspect.all(Missile.class)).getEntities().size());
    } finally { world.dispose(); }
  }

  private static Attributes attributes() {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.level, 1);
    attrs.base().put(Stat.hitpoints, 100f);
    attrs.base().put(Stat.maxhp, 100f);
    attrs.base().put(Stat.mana, 1000);
    attrs.base().put(Stat.maxmana, 1000);
    attrs.reset();
    return attrs;
  }
}
