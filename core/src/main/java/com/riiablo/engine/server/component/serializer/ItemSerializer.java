package com.riiablo.engine.server.component.serializer;

import com.google.flatbuffers.FlatBufferBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

import com.riiablo.engine.server.component.Item;
import com.riiablo.io.ByteInput;
import com.riiablo.io.ByteOutput;
import com.riiablo.item.ItemReader;
import com.riiablo.item.ItemWriter;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.net.packet.d2gs.ItemP;

public class ItemSerializer implements FlatBuffersSerializer<Item, ItemP> {
  public final ItemP table = new ItemP();

  protected final ItemReader itemReader;
  protected final ItemWriter itemWriter = new ItemWriter(); // TODO: inject

  public ItemSerializer() { this(null); }
  public ItemSerializer(com.riiablo.Files files) { itemReader = new ItemReader(files); }

  @Override
  public byte getDataType() {
    return ComponentP.ItemP;
  }

  @Override
  public int putData(FlatBufferBuilder builder, Item c) {
    ByteBuf buffer = Unpooled.buffer();
    ByteOutput out = ByteOutput.wrap(buffer);
    itemWriter.writeItem(c.item, out);
    byte[] itemBytes = ByteBufUtil.getBytes(buffer);
    int dataOffset = ItemP.createDataVector(builder, itemBytes);
    return ItemP.createItemP(builder, dataOffset, c.dropOwnerId,
        c.dropOwnerUntilMillis, c.dropPartyId, c.dropPartyUntilMillis,
        c.partyShareGold);
  }

  @Override
  public ItemP getTable(EntitySync sync, int j) {
    sync.component(table, j);
    return table;
  }

  @Override
  public Item getData(EntitySync sync, int j, Item c) {
    getTable(sync, j);
    ByteBuf buffer = Unpooled.wrappedBuffer(table.dataAsByteBuffer());
    c.item = itemReader.readItem(ByteInput.wrap(buffer));
    return c;
  }
}
