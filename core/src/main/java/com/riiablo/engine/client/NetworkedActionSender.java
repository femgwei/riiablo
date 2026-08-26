package com.riiablo.engine.client;

import com.google.flatbuffers.FlatBufferBuilder;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.net.Socket;
import com.riiablo.net.packet.d2gs.CastSkillRequest;
import com.riiablo.net.packet.d2gs.D2GS;
import com.riiablo.net.packet.d2gs.D2GSData;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;

/** Sends player combat input to the authoritative D2GS server. */
public final class NetworkedActionSender {
  private static final String TAG = "NetworkedActionSender";

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
}
