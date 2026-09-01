package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.Aspect;
import com.artemis.EntitySubscription;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.utils.IntArray;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Levels;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import org.junit.jupiter.api.Test;

class KillCreditResolverTest {
  @Test
  void resolvesMercenaryToItsPlayerOwner() {
    World world = new World(new WorldConfigurationBuilder().build());
    try {
      int owner = createPlayer(world, 1, 0f, 0f);
      int hireling = world.create();
      world.getMapper(Mercenary.class).create(hireling).ownerId = owner;
      KillCreditResolver resolver = resolver(world, null);

      assertEquals(owner, resolver.ownerOf(owner));
      assertEquals(owner, resolver.ownerOf(hireling));
      assertEquals(-1, resolver.ownerOf(world.create()));
    } finally {
      world.dispose();
    }
  }

  @Test
  void partyExperienceUsesVictimCenteredNativeRange() {
    PartyManager parties = new PartyManager();
    World world = new World(new WorldConfigurationBuilder().build());
    try {
      int owner = createPlayer(world, 2, 0f, 0f);
      int member = createPlayer(world, 2, 80.01f, 0f);
      int victim = world.create();
      world.getMapper(Position.class).create(victim).position.set(0f, 0f);
      assertTrue(parties.sendInvitation(owner, member));
      assertTrue(parties.acceptInvitation(member));
      EntitySubscription players = world.getAspectSubscriptionManager().get(
          Aspect.all(Player.class));
      KillCreditResolver resolver = resolver(world, parties);

      IntArray outside = resolver.eligibleExperiencePlayers(owner, victim, 2, players);
      assertTrue(outside.contains(owner));
      assertFalse(outside.contains(member));

      world.getMapper(Position.class).get(member).position.set(80f, 0f);
      IntArray boundary = resolver.eligibleExperiencePlayers(owner, victim, 2, players);
      assertTrue(boundary.contains(owner));
      assertTrue(boundary.contains(member));
      assertEquals(2, boundary.size);
    } finally {
      world.dispose();
    }
  }

  private static KillCreditResolver resolver(World world, PartyManager parties) {
    return new KillCreditResolver(
        world.getMapper(Player.class), world.getMapper(Mercenary.class),
        world.getMapper(MapWrapper.class), world.getMapper(Position.class), parties);
  }

  private static int createPlayer(World world, int levelId, float x, float y) {
    int id = world.create();
    world.getMapper(Player.class).create(id).data =
        CharData.obtain().set(Riiablo.NORMAL, false, "P" + id, Riiablo.AMAZON);
    world.getMapper(Position.class).create(id).position.set(x, y);
    Levels.Entry level = new Levels.Entry();
    level.Id = levelId;
    Map.Zone zone = new Map.Zone();
    zone.level = level;
    world.getMapper(MapWrapper.class).create(id).zone = zone;
    return id;
  }
}
