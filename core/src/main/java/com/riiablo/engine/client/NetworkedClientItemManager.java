package com.riiablo.engine.client;

import com.google.flatbuffers.FlatBufferBuilder;

import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.net.Socket;
import com.riiablo.Riiablo;
import com.riiablo.engine.server.component.Networked;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.StoreLoc;
import com.riiablo.net.packet.d2gs.BeltToCursor;
import com.riiablo.net.packet.d2gs.BodyToCursor;
import com.riiablo.net.packet.d2gs.CursorToBelt;
import com.riiablo.net.packet.d2gs.CursorToBody;
import com.riiablo.net.packet.d2gs.CursorToGround;
import com.riiablo.net.packet.d2gs.CursorToStore;
import com.riiablo.net.packet.d2gs.D2GS;
import com.riiablo.net.packet.d2gs.D2GSData;
import com.riiablo.net.packet.d2gs.GroundToCursor;
import com.riiablo.net.packet.d2gs.StoreToCursor;
import com.riiablo.net.packet.d2gs.SwapBeltItem;
import com.riiablo.net.packet.d2gs.SwapBodyItem;
import com.riiablo.net.packet.d2gs.SwapStoreItem;
import com.riiablo.net.packet.d2gs.ItemMoveRequest;
import com.riiablo.net.packet.d2gs.ItemMoveOperation;
import com.riiablo.net.packet.d2gs.ItemMoveResult;

import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;

public class NetworkedClientItemManager extends ClientItemManager {
  private static final String TAG = "NetworkedClientItemManager";

  protected ComponentMapper<Networked> mNetworked;

  @Wire(name = "client.socket")
  protected Socket socket;

  private int nextRequestId = 1;
  private long inventoryRevision;

  public long inventoryRevision() { return inventoryRevision; }
  public void onAuthoritativeResult(ItemMoveResult result) {
    if (result == null) return;
    inventoryRevision = result.revision();
    Gdx.app.log(TAG, "[ITEM_MOVE_RESULT] request=" + result.requestId()
        + " success=" + result.success() + " failure=" + result.failure()
        + " revision=" + inventoryRevision + " snapshot=" + result.snapshotLength());
  }

  private void wrapAndSend(FlatBufferBuilder builder, byte data_type, int dataOffset) {
    int root = D2GS.createD2GS(builder, data_type, dataOffset);
    D2GS.finishSizePrefixedD2GSBuffer(builder, root);

    try {
      OutputStream out = socket.getOutputStream();
      WritableByteChannel channelOut = Channels.newChannel(out);
      channelOut.write(builder.dataBuffer());
    } catch (Throwable t) {
      Gdx.app.error(TAG, t.getMessage(), t);
    }
  }

  private FlatBufferBuilder obtainBuilder() {
    return new FlatBufferBuilder(0);
  }

  private void send(byte operation, int itemId, int groundEntityId, int storeLoc,
                    int x, int y, int bodyLoc, boolean merc) {
    FlatBufferBuilder builder = obtainBuilder();
    int dataOffset = ItemMoveRequest.createItemMoveRequest(builder, nextRequestId++,
        inventoryRevision, operation, itemId, groundEntityId, storeLoc, x, y, bodyLoc, merc);
    wrapAndSend(builder, D2GSData.ItemMoveRequest, dataOffset);
  }

  private static int itemId(int index) {
    com.riiablo.save.ItemData data = Riiablo.charData == null ? null : Riiablo.charData.getItems();
    if (data == null || index < 0 || index >= data.getItems().size) return -1;
    com.riiablo.item.Item item = data.getItems().get(index);
    return item == null ? -1 : item.id;
  }

  @Override
  public void groundToCursor(int entityId) {
    int serverId = mNetworked.get(entityId).serverId;
    com.riiablo.engine.server.component.Item item = mItem.get(entityId);
    send(ItemMoveOperation.GROUND_TO_CURSOR, item == null || item.item == null ? -1 : item.item.id,
        serverId, -1, -1, -1, -1, false);
  }

  @Override
  public void cursorToGround() {
    send(ItemMoveOperation.CURSOR_TO_GROUND, -1, -1, -1, -1, -1, -1, false);
  }

  @Override
  public void storeToCursor(int i) {
    send(ItemMoveOperation.STORE_TO_CURSOR, itemId(i), -1, -1, -1, -1, -1, false);
  }

  @Override
  public void cursorToStore(StoreLoc storeLoc, int x, int y) {
    send(ItemMoveOperation.CURSOR_TO_STORE, -1, -1, storeLoc.ordinal(), x, y, -1, false);
  }

  @Override
  public void swapStoreItem(int i, StoreLoc storeLoc, int x, int y) {
    send(ItemMoveOperation.SWAP_STORE_ITEM, itemId(i), -1, storeLoc.ordinal(), x, y, -1, false);
  }

  @Override
  public void bodyToCursor(BodyLoc bodyLoc, boolean merc) {
    send(ItemMoveOperation.BODY_TO_CURSOR, -1, -1, -1, -1, -1, bodyLoc.ordinal(), merc);
  }

  @Override
  public void cursorToBody(BodyLoc bodyLoc, boolean merc) {
    send(ItemMoveOperation.CURSOR_TO_BODY, -1, -1, -1, -1, -1, bodyLoc.ordinal(), merc);
  }

  @Override
  public void swapBodyItem(BodyLoc bodyLoc, boolean merc) {
    send(ItemMoveOperation.SWAP_BODY_ITEM, -1, -1, -1, -1, -1, bodyLoc.ordinal(), merc);
  }

  @Override
  public void beltToCursor(int i) {
    send(ItemMoveOperation.BELT_TO_CURSOR, itemId(i), -1, -1, -1, -1, -1, false);
  }

  @Override
  public void cursorToBelt(int x, int y) {
    send(ItemMoveOperation.CURSOR_TO_BELT, -1, -1, -1, x, y, -1, false);
  }

  @Override
  public void swapBeltItem(int i) {
    send(ItemMoveOperation.SWAP_BELT_ITEM, itemId(i), -1, -1, -1, -1, -1, false);
  }
}
