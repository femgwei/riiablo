package com.riiablo.engine.server.party;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;

import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import org.junit.jupiter.api.Test;

class PartyManagerRuntimeTest extends RiiabloTest {
  @Test
  void authoritativePlayerSnapshotFeedsPartyMemberState() {
    PartyManager parties = new PartyManager();
    World world = new World(new WorldConfigurationBuilder()
        .with(new PartyMemberSyncSystem())
        .build()
        .register("partyManager", parties));
    try {
      int leader = createPlayer(world, 7, 33, 21, 14, 9);
      int member = createPlayer(world, 5, 18, 12, 4, 6);
      assertTrue(parties.sendInvitation(leader, member));
      assertTrue(parties.acceptInvitation(member));

      world.process();

      PartyMember snapshot = parties.getPartyForPlayer(member).getMember(member);
      assertEquals(5, snapshot.level);
      assertEquals(18, snapshot.currentHp);
      assertEquals(33, parties.getPartyForPlayer(leader).getMember(leader).currentHp);
      assertEquals(4, snapshot.x);
      assertEquals(6, snapshot.y);
      assertTrue(snapshot.online);
      assertTrue(snapshot.alive);
    } finally {
      world.dispose();
    }
  }

  @Test
  void disconnectCleanupRemovesMembershipInvitationsAndRelations() {
    PartyManager parties = new PartyManager();
    assertTrue(parties.sendInvitation(10, 11));
    assertTrue(parties.acceptInvitation(11));
    assertTrue(parties.declareHostility(10, 12));

    parties.removePlayer(10);

    assertEquals(Party.INVALID_ID, parties.getPartyId(10));
    assertEquals(Party.INVALID_ID, parties.getPartyId(11));
    assertEquals(PartyRelation.NONE, parties.getRelation(10, 12));
    assertFalse(parties.acceptInvitation(11));
  }

  private static int createPlayer(World world, int level, int hp, int maxHp, int x, int y) {
    int entity = world.create();
    world.getMapper(Player.class).create(entity);
    world.getMapper(Position.class).create(entity).position.set(x, y);
    world.getMapper(MapWrapper.class).create(entity).set(null, null);
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.level, level);
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, maxHp);
    attrs.base().put(Stat.mana, 8);
    attrs.base().put(Stat.maxmana, 10);
    attrs.reset();
    world.getMapper(AttributesWrapper.class).create(entity).attrs = attrs;
    return entity;
  }
}
