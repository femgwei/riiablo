package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.*;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.Engine;
import com.riiablo.presentation.component.Label;
import com.riiablo.engine.shared.component.Selectable;
import com.riiablo.engine.server.NativeHirelingExperienceTable;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.net.packet.d2gs.MercenaryP;
import com.riiablo.save.CharData;
import org.junit.jupiter.api.Test;

class ClientMercenarySnapshotTest extends RiiabloTest {
  @Test void repeatedHeaderDoesNotHealAndReplacementResetsOldAttributes() {
    CharData previous = Riiablo.charData;
    try {
      Riiablo.charData = CharData.obtain().clear().set(0, false, "MercSnapshot", Riiablo.AMAZON);
      ClientNetworkReceiver receiver = new ClientNetworkReceiver();
      long xp = NativeHirelingExperienceTable.load().thresholdForHireling(8, 30);
      receiver.applyMercenaryHeader(123, 2, 8, xp, 0);
      CharData.MercData saved = Riiablo.charData.getMerc();
      saved.getStats().base().put(Stat.hitpoints, 17f);
      saved.getStats().aggregate().put(Stat.hitpoints, 17f);
      saved.getStats().base().put(Stat.item_magicbonus, 123);
      receiver.applyMercenaryHeader(123, 2, 8, xp, 1);
      assertEquals(17f, saved.getStats().get(Stat.hitpoints).asFixed());
      assertEquals(1, saved.flags);
      receiver.applyMercenaryHeader(456, 3, 7, xp, 0);
      assertEquals(456, saved.seed);
      assertEquals(7, saved.type);
      assertEquals(0, saved.flags);
      assertNull(saved.getStats().base().get(Stat.item_magicbonus));
    } finally { Riiablo.charData = previous; }
  }

  @Test void snapshotBeforeOwnerIsRetainedWithoutRequiringARepeatedPacket() {
    com.riiablo.screen.GameScreen previous = Riiablo.game;
    Riiablo.game = null;
    World world = new World(new WorldConfigurationBuilder().with(new NetworkIdManager()).build());
    try {
      Harness receiver = new Harness();
      receiver.attach(world);
      int entity = world.create();
      world.getMapper(Selectable.class).create(entity);
      FlatBufferBuilder builder = new FlatBufferBuilder(128);
      builder.finish(MercenaryP.createMercenaryP(builder, 900, 1, 11, 80, 123, 2, 1000, 0));
      receiver.applyMercenarySnapshot(entity, MercenaryP.getRootAsMercenaryP(builder.dataBuffer()));
      Mercenary merc = world.getMapper(Mercenary.class).get(entity);
      assertEquals(Engine.INVALID_ENTITY, merc.ownerId);
      assertFalse(world.getMapper(Selectable.class).has(entity));
      assertEquals(11, merc.hirelingId);
      world.process();
      receiver.resolveMercenaryOwners();
      assertEquals(Engine.INVALID_ENTITY, merc.ownerId);
      int owner = world.create();
      receiver.syncIds.put(900, owner);
      world.process();
      receiver.resolveMercenaryOwners();
      assertEquals(owner, merc.ownerId);
      assertEquals(1000, merc.experience);
    } finally { world.dispose(); Riiablo.game = previous; }
  }

  @Test void staleSnapshotCannotOverwriteCommittedEcsReplica() {
    com.riiablo.screen.GameScreen previous = Riiablo.game;
    Riiablo.game = null;
    World world = new World(new WorldConfigurationBuilder().with(new NetworkIdManager()).build());
    try {
      Harness receiver = new Harness();
      receiver.attach(world);
      int entity = world.create();
      FlatBufferBuilder newer = new FlatBufferBuilder(128);
      newer.finish(MercenaryP.createMercenaryP(newer, 900, 1, 11, 80, 123, 2, 2000, 4));
      receiver.applyMercenarySnapshot(entity, MercenaryP.getRootAsMercenaryP(newer.dataBuffer()), 20, 2000);
      FlatBufferBuilder older = new FlatBufferBuilder(128);
      older.finish(MercenaryP.createMercenaryP(older, 900, 1, 11, 80, 999, 2, 1000, 1));
      receiver.applyMercenarySnapshot(entity, MercenaryP.getRootAsMercenaryP(older.dataBuffer()), 19, 2100);
      assertEquals(123, world.getMapper(Mercenary.class).get(entity).seed);
      assertEquals(2000, world.getMapper(Mercenary.class).get(entity).experience);
      assertEquals(20, receiver.authoritativeMercenary(entity).tick);
    } finally { world.dispose(); Riiablo.game = previous; }
  }

  @com.artemis.annotations.All
  private static class Harness extends ClientNetworkReceiver {
    void attach(World attached) {
      world = attached;
      mMercenary = world.getMapper(Mercenary.class);
      mLabel = world.getMapper(Label.class);
      mSelectable = world.getMapper(Selectable.class);
      syncIds = world.getSystem(NetworkIdManager.class);
    }
  }
}
