package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.NativeAiCommand;
import org.junit.jupiter.api.Test;

class FallenLeaderCommandTest extends RiiabloTest {
  @Test void deletingTheIssuerDoesNotCancelAnExistingNativeAttackOrder() {
    try (Fixture f = fixture()) {
      f.ai.params[0] = 0;
      f.ai.params[2] = 100;
      int issuer = member(f, -1, 40);
      f.world.getMapper(NativeAiCommand.class).create(f.actor)
          .copy(f.world, issuer, NativeAiCommand.ATTACK, f.target);
      f.world.delete(issuer);
      f.world.process();
      f.ai.update(.04f);
      assertNotNull(f.casting());
      assertTrue(f.world.getMapper(NativeAiCommand.class).has(f.actor));
    }
  }

  @Test void failedApproachReleasesTheCurrentOrder() {
    try (Fixture f = new Fixture("fallen1", 0, 8)) {
      f.world.getMapper(CofReference.class).create(f.actor).mode = 1;
      f.ai.params[0] = 0;
      f.world.getMapper(NativeAiCommand.class).create(f.actor)
          .copy(f.world, f.actor, NativeAiCommand.ATTACK, f.target);
      org.mockito.Mockito.when(f.paths.findPath(
          org.mockito.ArgumentMatchers.eq(f.actor), org.mockito.ArgumentMatchers.any(),
          org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.eq(f.target)))
          .thenReturn(false);
      f.world.process();
      f.ai.update(.04f);
      assertNull(f.casting());
      assertFalse(f.world.getMapper(NativeAiCommand.class).has(f.actor));
    }
  }

  @Test void packOrderUsesTheCurrentAiTargetInsteadOfItsCopiedTarget() {
    verifyRetarget(false);
  }

  @Test void deletingTheCopiedTargetDoesNotCancelAnOtherwiseValidPackOrder() {
    verifyRetarget(true);
  }

  private void verifyRetarget(boolean deleteCopiedTarget) {
    try (Fixture f = fixture()) {
      ((Fallen) f.ai).monster.setMinionOwner(f.actor);
      f.ai.params[0] = 0; // Do not mask cancellation by generating a new order.
      f.ai.params[2] = 100;
      int oldTarget = member(f, -1, 40);
      f.world.getMapper(NativeAiCommand.class).create(f.actor)
          .copy(f.world, f.actor, NativeAiCommand.ATTACK, oldTarget);
      if (deleteCopiedTarget) f.world.delete(oldTarget);
      f.world.process();
      f.ai.update(.04f);
      assertNotNull(f.casting(), "current nearby hostile target must be attacked");
      assertEquals(f.target, f.casting().targetId);
      assertTrue(f.world.getMapper(NativeAiCommand.class).has(f.actor),
          "the copied target is not part of native command 1's validity");
    }
  }

  @Test void successfulCommandedAttackKeepsThePackOrderUntilItIsInvalidated() {
    try (Fixture f = fixture()) {
      ((Fallen) f.ai).monster.setMinionOwner(f.actor);
      f.ai.params[2] = 100;
      NativeAiCommand command = f.world.getMapper(NativeAiCommand.class).create(f.actor);
      command.copy(f.world, f.actor, NativeAiCommand.ATTACK, f.target);
      f.world.process();
      f.ai.update(.04f);
      assertNotNull(f.casting(), "the order must actually initiate an attack");
      assertTrue(f.world.getMapper(NativeAiCommand.class).has(f.actor),
          "Fn006 retains command 1 after an attack; success does not consume it");
      assertEquals(NativeAiCommand.ATTACK, command.command);
    }
  }

  @Test void nearbyUnownedFallenDoNotInventALeader() {
    try (Fixture f = fixture()) {
      int neighbor = member(f, -1, 11);
      f.world.process();
      f.ai.update(.04f);
      assertFalse(f.world.getMapper(NativeAiCommand.class).has(neighbor));
    }
  }

  @Test void leaderCommandsOwnedMembersWithoutRecruitingNearbyOtherPacks() {
    try (Fixture f = fixture()) {
      ((Fallen) f.ai).monster.setMinionOwner(f.actor);
      int owned = member(f, f.actor, 12);
      int unowned = member(f, -1, 11);
      int otherPack = member(f, 9999, 13);
      f.world.process();
      f.ai.update(.04f);
      NativeAiCommand command = f.world.getMapper(NativeAiCommand.class).get(owned);
      assertNotNull(command);
      assertEquals(f.actor, command.ownerId);
      assertEquals(f.target, command.targetId);
      assertFalse(f.world.getMapper(NativeAiCommand.class).has(unowned));
      assertFalse(f.world.getMapper(NativeAiCommand.class).has(otherPack));
    }
  }

  @Test void ownedMemberOutsideLocalClusterStillReceivesPackCommand() {
    try (Fixture f = fixture()) {
      ((Fallen) f.ai).monster.setNativePack(42, true);
      int owned = member(f, f.actor, 40);
      f.world.process();
      f.ai.update(.04f);
      assertNotNull(f.world.getMapper(NativeAiCommand.class).get(owned));
    }
  }

  private Fixture fixture() {
    Fixture f = new Fixture("fallen1", 0, 1);
    f.world.getMapper(Class.class).create(f.actor);
    f.world.getMapper(CofReference.class).create(f.actor).mode = 1;
    f.ai.params[0] = 100;
    f.ai.params[2] = 0;
    return f;
  }

  private int member(Fixture f, int owner, float x) {
    int id = f.ally("fallen1", 100, x, 10);
    f.world.getMapper(Monster.class).get(id).setMinionOwner(owner);
    f.world.getMapper(Class.class).create(id);
    f.world.getMapper(CofReference.class).create(id).mode = 1;
    return id;
  }
}
