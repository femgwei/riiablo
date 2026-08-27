package com.riiablo.server.d2gs;

import com.google.flatbuffers.ByteBufferUtil;
import java.nio.ByteBuffer;

import com.badlogic.gdx.utils.TimeUtils;

import com.riiablo.net.packet.d2gs.D2GS;

public class Packet {
    public int id;
    public long time;
    public ByteBuffer buffer;
    public D2GS data;

    public static Packet obtain(int id, ByteBuffer buffer) {
      Packet packet = new Packet();
      packet.id = id;
      packet.time = TimeUtils.millis();
      packet.buffer = buffer;
      packet.data = D2GS.getRootAsD2GS(ByteBufferUtil.removeSizePrefix(buffer));
      return packet;
    }

    /** Creates a received packet whose buffer no longer contains a size prefix. */
    public static Packet obtainPayload(int id, ByteBuffer payload) {
      Packet packet = new Packet();
      packet.id = id;
      packet.time = TimeUtils.millis();
      packet.buffer = payload;
      packet.data = D2GS.getRootAsD2GS(payload.duplicate());
      return packet;
    }
  }
