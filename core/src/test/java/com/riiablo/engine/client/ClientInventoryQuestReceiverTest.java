package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.component.Position;
import com.riiablo.io.ByteOutput;
import com.riiablo.item.*;
import com.riiablo.net.packet.d2gs.*;
import com.riiablo.save.CharData;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class ClientInventoryQuestReceiverTest extends RiiabloTest {
  @Test void incompleteInventoryBaselineRemainsPendingAndCanBeRetriedAtLowerRevision() throws Exception {
    try (Fixture f = new Fixture()) {
      f.receive(inventory(1, 20, bytes(item(701)), -1, null));
      f.receive(baseline(1, SnapshotBaselinePhase.BEGIN, 2));
      f.receive(inventory(0, 2, new byte[] {1}, -1, null));
      f.receive(baseline(1, SnapshotBaselinePhase.END, 2));
      assertTrue(f.receiver.pending());
      assertEquals(0, f.receiver.completedBaseline());
      assertEquals(701, f.data.getItems().getItems().first().id);
      f.receive(baseline(2, SnapshotBaselinePhase.BEGIN, 2));
      f.receive(inventory(0, 2, null, -1, null));
      f.receive(baseline(2, SnapshotBaselinePhase.END, 2));
      assertFalse(f.receiver.pending());
      assertEquals(2, f.receiver.completedBaseline());
      assertEquals(0, f.data.getItems().getItems().size);
      assertEquals(2, f.receiver.authoritativeInventory().revision);
    }
  }

  @Test void groundSnapshotDetachesBytesAndInvalidUpdateCannotChangePickupWindow() throws Exception {
    try (Fixture f = new Fixture()) {
      int entity = f.world.create(); f.receiver.syncIds.put(100, entity);
      com.riiablo.engine.server.component.Item component = f.world.getMapper(
          com.riiablo.engine.server.component.Item.class).create(entity).set(item(1));
      f.world.process();
      D2GS packet = ground(10, bytes(item(777)), 41);
      f.receive(packet);
      AuthoritativeGroundItemState.Snapshot snapshot = f.receiver.authoritativeGroundItem(entity);
      assertEquals(100, component.item.id);
      assertEquals(41, component.dropOwnerId);
      assertEquals(10, snapshot.tick);
      snapshot.itemData()[0] = 0;
      java.nio.ByteBuffer wire = packet.getByteBuffer();
      for (int i = wire.position(); i < wire.limit(); i++) wire.put(i, (byte) 0);
      assertEquals('J', snapshot.itemData()[0]);
      f.receive(ground(9, bytes(item(888)), 50));
      assertSame(snapshot, f.receiver.authoritativeGroundItem(entity));
      // Metadata-only updates retain the last committed bytes, even if artwork
      // or another presentation consumer has changed the mutable replica.
      component.item.code = "replica mutation";
      f.receive(ground(11, new byte[0], 55));
      assertEquals(55, component.dropOwnerId);
      assertEquals("cap", component.item.code);
      assertEquals(100, component.item.id);
      assertArrayEquals(snapshot.itemData(), f.receiver.authoritativeGroundItem(entity).itemData());
      AuthoritativeGroundItemState.Snapshot updated = f.receiver.authoritativeGroundItem(entity);
      f.receive(ground(12, new byte[] {0}, 99));
      assertSame(updated, f.receiver.authoritativeGroundItem(entity));
      assertEquals(55, component.dropOwnerId);
      assertEquals(100, component.item.id);
    }
  }

  static D2GS baseline(long id, int phase, long revision) {
    FlatBufferBuilder b = new FlatBufferBuilder(128);
    int marker = SnapshotBaseline.createSnapshotBaseline(b, id, id, 10, 400, phase,
        true, 0, 0, 0, 0, revision, 0, 0, 0, false);
    b.finish(D2GS.createD2GS(b, D2GSData.SnapshotBaseline, marker));
    return D2GS.getRootAsD2GS(b.dataBuffer());
  }
  static D2GS ground(long tick, byte[] bytes, int owner) {
    FlatBufferBuilder b = new FlatBufferBuilder(512);
    int encoded = ItemP.createDataVector(b, bytes);
    int item = ItemP.createItemP(b, encoded, owner, 1000, 7, 2000, true);
    int types = EntitySync.createComponentTypeVector(b, new byte[] {ComponentP.ItemP});
    int components = EntitySync.createComponentVector(b, new int[] {item});
    int sync = EntitySync.createEntitySync(b, 100,
        com.riiablo.engine.server.component.Class.Type.ITM.ordinal(), 0,
        types, components, tick, tick * 40, 0, 0, 0, -1);
    b.finish(D2GS.createD2GS(b, D2GSData.EntitySync, sync));
    return D2GS.getRootAsD2GS(b.dataBuffer());
  }
  @Test void completeSnapshotIsDetachedAndReplicaMutationDoesNotEditAuthority() throws Exception {
    try (Fixture f = new Fixture()) {
      D2GS packet = inventory(1, 3, bytes(item(701)), -1, null);
      f.receive(packet);
      AuthoritativeInventoryState.Snapshot committed = f.receiver.authoritativeInventory();
      assertEquals(3, committed.revision);
      assertEquals(701, f.data.getItems().getItems().first().id);
      byte[] bytes = committed.entries.get(0).itemData(); bytes[0] = 0;
      assertEquals('J', committed.entries.get(0).itemData()[0]);
      assertThrows(UnsupportedOperationException.class, () -> committed.entries.clear());
      java.nio.ByteBuffer buffer = packet.getByteBuffer();
      for (int i = buffer.position(); i < buffer.limit(); i++) buffer.put(i, (byte) 0);
      f.data.getItems().getItems().first().gridX = 5;
      assertEquals(0, committed.entries.get(0).x);
      assertEquals('J', committed.entries.get(0).itemData()[0]);
    }
  }

  @Test void corruptInventoryCannotPartiallyReplaceOwnershipOrAdvanceRevision() throws Exception {
    try (Fixture f = new Fixture()) {
      Item existing = item(900); f.data.getItems().addToInventory(existing);
      f.receive(inventory(1, 5, new byte[] {1, 2, 3}, 100, null));
      assertSame(existing, f.data.getItems().getItems().first());
      assertNull(f.receiver.authoritativeInventory());
      f.receive(inventory(1, 5, bytes(item(701)), -1, null));
      assertEquals(5, f.receiver.authoritativeInventory().revision);
      assertEquals(701, f.data.getItems().getItems().first().id);
    }
  }

  @Test void duplicateAndOlderResultsCannotRepeatOrRollbackACommittedTransaction() throws Exception {
    try (Fixture f = new Fixture()) {
      f.receive(inventory(1, 5, bytes(item(701)), -1, null));
      AuthoritativeInventoryState.Snapshot committed = f.receiver.authoritativeInventory();
      f.receive(inventory(1, 5, null, -1, null));
      f.receive(inventory(2, 4, null, -1, null));
      assertSame(committed, f.receiver.authoritativeInventory());
      assertEquals(1, f.data.getItems().getItems().size);
      f.receive(inventory(3, 5, null, -1, null));
      assertEquals(0, f.data.getItems().getItems().size, "a new same-revision correction is applicable");
    }
  }

  @Test void corruptGroundCorrectionDoesNotCommitInventoryAndValidGroundUsesServerIdentity() throws Exception {
    try (Fixture f = new Fixture()) {
      int entity = f.world.create(); f.receiver.syncIds.put(100, entity);
      com.riiablo.engine.server.component.Item ground = f.world.getMapper(
          com.riiablo.engine.server.component.Item.class).create(entity).set(item(1));
      f.world.getMapper(Position.class).create(entity); f.world.process();
      f.receive(inventory(1, 5, bytes(item(701)), 100, new byte[] {0}));
      assertNull(f.receiver.authoritativeInventory());
      assertEquals(0, f.data.getItems().getItems().size);
      f.receive(inventory(1, 5, bytes(item(701)), 100, bytes(item(999))));
      assertEquals(100, ground.item.id);
      assertEquals(41, ground.dropOwnerId);
      assertEquals(7, ground.dropPartyId);
      assertEquals(12f, f.world.getMapper(Position.class).get(entity).position.x);
    }
  }

  @Test void questHashMayDecreaseButUntimedAcknowledgementCannotUndoNewerRecords() throws Exception {
    try (Fixture f = new Fixture()) {
      f.playerSnapshot(10, 100L, 3);
      f.playerSnapshot(11, Long.MIN_VALUE, 7);
      assertEquals(7, f.data.getQuests(0)[0]);
      assertEquals(Long.MIN_VALUE, f.receiver.authoritativeQuests().revision);
      FlatBufferBuilder b = new FlatBufferBuilder(128);
      short[] records = new short[40]; records[0] = 3;
      int values = QuestResult.createQuestRecordsVector(b, records);
      int result = QuestResult.createQuestResult(b, 1, true, 0, QuestOperation.NPC_MESSAGE, 2, 304, 100L, values);
      b.finish(D2GS.createD2GS(b, D2GSData.QuestResult, result));
      f.receive(D2GS.getRootAsD2GS(b.dataBuffer()));
      assertEquals(7, f.data.getQuests(0)[0]);
      assertEquals(11, f.receiver.authoritativeQuests().tick);
      int[] copy = f.receiver.authoritativeQuests().records(); copy[0] = 0;
      assertEquals(7, f.receiver.authoritativeQuests().record(0));
    }
  }

  @Test void untimedGoldAcknowledgementCannotOverwriteStampedWallet() throws Exception {
    try (Fixture f = new Fixture()) {
      f.playerSnapshot(10, 100L, 3);
      FlatBufferBuilder b = new FlatBufferBuilder(128);
      int result = GoldResult.createGoldResult(b, 1, true, 0, GoldOperation.DEPOSIT, 5, 999, 999, -1);
      b.finish(D2GS.createD2GS(b, D2GSData.GoldResult, result));
      f.receive(D2GS.getRootAsD2GS(b.dataBuffer()));
      assertEquals(37, f.data.getStats().get(com.riiablo.attributes.Stat.gold).asInt());
      assertEquals(37, f.receiver.authoritativePlayerItems().gold);
    }
  }

  static Item item(int id) {
    Item item = new ItemGenerator().generate("cap"); item.id = id;
    item.quality = Quality.NORMAL;
    item.location = Location.STORED; item.storeLoc = StoreLoc.INVENTORY; item.bodyLoc = BodyLoc.NONE;
    return item;
  }
  static byte[] bytes(Item item) {
    ByteOutput output = ByteOutput.wrap(Unpooled.buffer());
    new ItemWriter().writeItem(item, output);
    byte[] bytes = new byte[output.buffer().readableBytes()];
    output.buffer().getBytes(output.buffer().readerIndex(), bytes); output.buffer().release();
    return bytes;
  }
  static D2GS inventory(long request, long revision, byte[] item, int groundId, byte[] ground) {
    FlatBufferBuilder b = new FlatBufferBuilder(512);
    int[] entries = new int[0];
    if (item != null) {
      int data = ItemMoveSnapshotEntry.createItemDataVector(b, item);
      entries = new int[] { ItemMoveSnapshotEntry.createItemMoveSnapshotEntry(b, 701, data,
          Location.STORED.ordinal(), StoreLoc.INVENTORY.ordinal(), BodyLoc.NONE.ordinal(), 0, 0, false) };
    }
    int snapshot = ItemMoveResult.createSnapshotVector(b, entries);
    int groundBytes = ground == null ? 0 : ItemMoveResult.createGroundItemDataVector(b, ground);
    int result = ItemMoveResult.createItemMoveResult(b, request, true, ItemMoveFailure.NONE,
        revision, ItemMoveOperation.CURSOR_TO_STORE, snapshot, groundId, groundBytes, 12f, 13f,
        41, 1000, 7, 2000, false);
    b.finish(D2GS.createD2GS(b, D2GSData.ItemMoveResult, result));
    return D2GS.getRootAsD2GS(b.dataBuffer());
  }

  private static final class Fixture implements AutoCloseable {
    final World world = new World(new WorldConfigurationBuilder().with(new NetworkIdManager()).build());
    final Harness receiver = new Harness();
    final CharData data = CharData.obtain().clear().set(0, false, "ItemReplica", Riiablo.AMAZON);
    final int player = world.create();
    Fixture() { receiver.attach(world); receiver.clientSession = new ClientNetworkSession(data, () -> player); world.process(); }
    void receive(D2GS packet) throws Exception { ClientPlayerRespawnResultTest.receive(receiver, packet); }
    void playerSnapshot(long tick, long revision, int value) throws Exception {
      FlatBufferBuilder b = new FlatBufferBuilder(256);
      short[] records = new short[40]; records[0] = (short) value;
      int values = PlayerP.createQuestRecordsVector(b, records);
      PlayerP.startPlayerP(b); PlayerP.addQuestRecords(b, values); PlayerP.addQuestRevision(b, revision);
      PlayerP.addWalletPresent(b, true); PlayerP.addGold(b, 37); PlayerP.addLevel(b, 1);
      b.finish(PlayerP.endPlayerP(b));
      Method apply = ClientNetworkReceiver.class.getDeclaredMethod("applyPlayerSnapshot", int.class, PlayerP.class, long.class, long.class);
      apply.setAccessible(true); apply.invoke(receiver, player, PlayerP.getRootAsPlayerP(b.dataBuffer()), tick, tick * 40);
    }
    @Override public void close() { world.dispose(); }
  }
  @com.artemis.annotations.All private static final class Harness extends ClientNetworkReceiver {
    void attach(World world) {
      this.world = world; syncIds = world.getSystem(NetworkIdManager.class);
      mItem = world.getMapper(com.riiablo.engine.server.component.Item.class);
      mPosition = world.getMapper(Position.class);
      cofs = org.mockito.Mockito.mock(com.riiablo.engine.server.CofManager.class);
    }
    boolean pending() throws Exception { return (Boolean) field("snapshotResyncPending"); }
    long completedBaseline() throws Exception {
      return ((SnapshotBaselineTransaction) field("baselineTransaction")).lastCompletedBaselineId();
    }
    private Object field(String name) throws Exception {
      java.lang.reflect.Field field = ClientNetworkReceiver.class.getDeclaredField(name);
      field.setAccessible(true); return field.get(this);
    }
  }
}
