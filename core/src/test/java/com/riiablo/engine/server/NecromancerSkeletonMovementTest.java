package com.riiablo.engine.server;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.D2;
import com.riiablo.codec.excel.MonStats;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.*;
import com.riiablo.presentation.AnimationStepper;
import com.riiablo.presentation.component.AnimationWrapper;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NecromancerSkeletonMovementTest extends RiiabloTest {
  @Test void skeletonGaitTracksVelocityForLocalMovement() {
    D2 saved = Riiablo.anim;
    com.riiablo.Colors savedColors = Riiablo.colors;
    Riiablo.anim = D2.loadFromFile(Riiablo.mpqs.resolve("data/global/eanimdata.d2"));
    Riiablo.colors = new com.riiablo.Colors();
    try {
      for (String name : new String[] {"necroskeleton", "necromage"}) {
        World world = new World(new WorldConfigurationBuilder().with(new EventSystem(),
            new CofManager(), new AnimDataResolver(), new VelocityModeChanger(false, true),
            new AnimationStepper()).build());
        try {
          MonStats.Entry row = Riiablo.files.monstats.get(name);
          int id = world.create();
          world.getMapper(com.riiablo.engine.server.component.Class.class).create(id).type =
              com.riiablo.engine.server.component.Class.Type.MON;
          world.getMapper(Monster.class).create(id).monstats = row;
          CofReference ref = world.getMapper(CofReference.class).create(id);
          ref.set(row.Code, Engine.Monster.MODE_WL);
          ref.wclass = Engine.WEAPON_HTH;
          world.getMapper(MovementModes.class).create(id).set(Engine.Monster.MODE_NU,
              Engine.Monster.MODE_WL, Engine.Monster.MODE_RN);
          Velocity velocity = world.getMapper(Velocity.class).create(id).setMonsterFromNativeTable(row.Velocity);
          velocity.velocity.set(velocity.speed(false), 0);
          com.riiablo.codec.Animation animation = world.getMapper(AnimationWrapper.class).create(id).animation;
          animation.setCOF(com.riiablo.codec.COF.loadFromFile(
              Riiablo.mpqs.resolve("data/global/monsters/SK/COF/SKWLHTH.cof")));
          world.setDelta(1f / 25f);
          for (int i = 0; i < 25; i++) world.process();
          assertEquals(Engine.Monster.MODE_WL, ref.mode);
          assertEquals(96, animation.getFrameDelta(), name + " base 75% gait");
          assertEquals(1, animation.getFrame(), name + " advances 9.375 frames in 25 ticks");
          velocity.velocity.scl(2);
          world.process();
          assertEquals(192, animation.getFrameDelta(), name + " catches up with faster movement");
          assertEquals(Engine.Monster.MODE_WL, ref.mode);
          velocity.velocity.setZero();
          world.process();
          assertEquals(128, animation.getFrameDelta(), "neutral animation restores the base rate");
          assertEquals(Engine.Monster.MODE_NU, ref.mode);
        } finally { world.dispose(); }
      }
    } finally { Riiablo.anim = saved; Riiablo.colors = savedColors; }
  }
  @Test void inspectNativeSkeletonMovementData() {
    D2 data = D2.loadFromFile(Riiablo.mpqs.resolve("data/global/eanimdata.d2"));
    D2 retail = D2.loadFromFile(Riiablo.mpqs.resolve("data/global/animdata.d2"));
    System.out.println("RETAIL_SK_WL " + retail.getEntry("SKWLHTH"));
    System.out.println("RETAIL_SK_A1 " + retail.getEntry("SKA1HTH"));
    System.out.println("E_SK_A1 " + data.getEntry("SKA1HTH"));
    for (String name : new String[] {"necroskeleton", "necromage"}) {
      MonStats.Entry row = Riiablo.files.monstats.get(name);
      assertNotNull(row);
      MonStats.Entry base = Riiablo.files.monstats.get(row.BaseId);
      System.out.println("SKELETON_DATA " + name + " base=" + row.BaseId
          + " code=" + row.Code + " velocity=" + row.Velocity + " run=" + row.Run
          + " AI=" + row.AI + " baseCode=" + base.Code + " baseVelocity=" + base.Velocity);
      for (String code : new String[] {row.Code, base.Code}) {
        for (String weapon : new String[] {"HTH", "1HS", "STF"}) {
          for (String mode : new String[] {"WL", "RN", "NU"}) {
            D2.Entry entry = data.getEntry(code + mode + weapon);
            System.out.println("SKELETON_ANIM " + code + mode + weapon + "=" + entry);
          }
        }
      }
    }
  }
}
