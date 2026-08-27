package com.riiablo.engine.client;

import com.google.flatbuffers.FlatBufferBuilder;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.net.Socket;
import com.riiablo.net.packet.d2gs.CastSkillRequest;
import com.riiablo.net.packet.d2gs.D2GS;
import com.riiablo.net.packet.d2gs.D2GSData;
import com.riiablo.net.packet.d2gs.SpendSkillPointRequest;
import com.riiablo.net.packet.d2gs.SelectSkillRequest;
import java.util.concurrent.atomic.AtomicLong;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;

/** Sends player combat input to the authoritative D2GS server. */
public final class NetworkedActionSender {
  private static final String TAG = "NetworkedActionSender";
  private static final AtomicLong REQUEST_IDS = new AtomicLong(1);

  private NetworkedActionSender() {}

  public static boolean cast(Socket socket, int skillId, int targetServerId, Vector2 target) {
    if (socket == null || target == null || !Float.isFinite(target.x) || !Float.isFinite(target.y)) {
      return false;
    }
    FlatBufferBuilder builder = new FlatBufferBuilder(128);
    int request = CastSkillRequest.createCastSkillRequest(
        builder, Math.max(0, Math.min(0xFFFF, skillId)), targetServerId,
        target.x, target.y);
    int root = D2GS.createD2GS(builder, D2GSData.CastSkillRequest, request);
    D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    try {
      OutputStream output = socket.getOutputStream();
      WritableByteChannel channel = Channels.newChannel(output);
      channel.write(builder.dataBuffer());
      Gdx.app.log(TAG, String.format(
          "[NET_CAST] phase=send skill=%d target=%d targetPos=(%.2f,%.2f)",
          skillId, targetServerId, target.x, target.y));
      return true;
    } catch (Throwable t) {
      Gdx.app.error(TAG, "[NET_CAST] phase=send_failed skill=" + skillId, t);
      return false;
    }
  }

  /** Sends allocation intent only; the server owns validation and mutation. */
  public static boolean spendSkillPoint(Socket socket, int skillId) {
    if (socket == null || skillId < 0 || skillId > 0xFFFF) return false;
    long requestId = REQUEST_IDS.getAndIncrement() & 0xFFFF_FFFFL;
    FlatBufferBuilder builder = new FlatBufferBuilder(96);
    int request = SpendSkillPointRequest.createSpendSkillPointRequest(
        builder, requestId, skillId);
    int root = D2GS.createD2GS(builder, D2GSData.SpendSkillPointRequest, request);
    D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    try {
      WritableByteChannel channel = Channels.newChannel(socket.getOutputStream());
      channel.write(builder.dataBuffer());
      Gdx.app.log(TAG, "[SKILL_POINT_NET] phase=send request=" + requestId
          + " skill=" + skillId);
      return true;
    } catch (Throwable t) {
      Gdx.app.error(TAG, "[SKILL_POINT_NET] phase=send_failed skill=" + skillId, t);
      return false;
    }
  }

  /** Sends a left/right skill selection; aura state remains server-owned. */
  public static boolean selectSkill(Socket socket, int button, int skillId) {
    if (socket == null || button < 0 || button > 1 || skillId < 0 || skillId > 0xFFFF) {
      return false;
    }
    long requestId = REQUEST_IDS.getAndIncrement() & 0xFFFF_FFFFL;
    FlatBufferBuilder builder = new FlatBufferBuilder(96);
    int request = SelectSkillRequest.createSelectSkillRequest(
        builder, requestId, button, skillId);
    int root = D2GS.createD2GS(builder, D2GSData.SelectSkillRequest, request);
    D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    try {
      WritableByteChannel channel = Channels.newChannel(socket.getOutputStream());
      channel.write(builder.dataBuffer());
      Gdx.app.log(TAG, "[SKILL_SELECT_NET] phase=send request=" + requestId
          + " button=" + button + " skill=" + skillId);
      return true;
    } catch (Throwable t) {
      Gdx.app.error(TAG, "[SKILL_SELECT_NET] phase=send_failed skill=" + skillId, t);
      return false;
    }
  }
}
