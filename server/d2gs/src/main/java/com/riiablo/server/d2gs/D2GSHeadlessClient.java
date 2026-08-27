package com.riiablo.server.d2gs;

import com.google.flatbuffers.FlatBufferBuilder;
import com.badlogic.gdx.Gdx;
import com.riiablo.engine.Engine;
import com.riiablo.net.packet.d2gs.CastSkillRequest;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.Connection;
import com.riiablo.net.packet.d2gs.D2GSData;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.net.packet.d2gs.MonsterP;
import com.riiablo.net.packet.d2gs.PositionP;
import com.riiablo.net.packet.d2gs.VitalsP;
import com.riiablo.skill.SkillCodes;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * Windowless protocol client for an authoritative D2GS combat smoke test.
 *
 * <p>The client sends the same FlatBuffers connection and cast packets as the
 * game client. It can start an embedded headless D2GS, waits for a monster
 * snapshot, moves the test player next to that monster through the existing
 * client sync path, attacks it, and requires an authoritative life decrease.
 * This deliberately exercises the TCP boundary instead of calling combat ECS
 * systems directly.</p>
 */
public final class D2GSHeadlessClient {
  private static final String TAG = "[HEADLESS_COMBAT]";
  private static final int DEFAULT_PORT = 6114;
  private static final int MAX_PACKET_SIZE = 1 << 20;

  private final Config config;
  private final Map<Integer, Snapshot> monsters = new HashMap<>();
  private int playerId = Engine.INVALID_ENTITY;
  private int missileSnapshots;

  private D2GSHeadlessClient(Config config) {
    this.config = config;
  }

  public static void main(String[] args) throws Exception {
    Config config = Config.parse(args);
    boolean startedServer = config.home != null;
    if (startedServer) {
      log("server_start", "home=" + config.home + " seed=" + config.seed);
      D2GS.main(new String[] {
          "-home", config.home.getAbsolutePath(),
          "-seed", Integer.toString(config.seed),
          "-diff", Integer.toString(config.difficulty)
      });
    }

    try {
      new D2GSHeadlessClient(config).run();
    } finally {
      if (startedServer && Gdx.app != null) Gdx.app.exit();
    }
  }

  private void run() throws Exception {
    waitForServer();
    byte[] d2s = Files.readAllBytes(config.save.toPath());
    CharacterHeader character = CharacterHeader.read(d2s);
    log("connect", "server=" + config.host + ':' + config.port
        + " character=" + character.name + " class=" + character.charClass);

    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(config.host, config.port), config.connectTimeoutMillis);
      socket.setTcpNoDelay(true);
      socket.setSoTimeout(500);
      try (DataInputStream input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
           OutputStream output = new BufferedOutputStream(socket.getOutputStream())) {
        send(output, connectionPacket(character, d2s));
        awaitConnection(input, System.currentTimeMillis() + config.testTimeoutMillis);

        Snapshot target = awaitTarget(input, System.currentTimeMillis() + config.testTimeoutMillis);
        float initialLife = target.life;
        log("target", String.format(
            "player=%d monster=%d monsterClass=%d position=(%.2f,%.2f) life=%.2f",
            playerId, target.entityId, target.monsterClass, target.x, target.y, initialLife));

        boolean damaged = attackUntilDamaged(input, output, target, initialLife);
        if (!damaged) {
          throw new IllegalStateException("authoritative target life did not decrease after "
              + config.attempts + " cast attempts");
        }

        Snapshot result = monsters.get(target.entityId);
        log("pass", String.format(
            "player=%d target=%d skill=%d life=%.2f->%.2f missiles=%d",
            playerId, target.entityId, config.skillId, initialLife,
            result == null ? 0f : result.life, missileSnapshots));
      }
    }
  }

  private void waitForServer() throws Exception {
    long deadline = System.currentTimeMillis() + config.serverTimeoutMillis;
    Throwable last = null;
    while (System.currentTimeMillis() < deadline) {
      try (Socket probe = new Socket()) {
        probe.connect(new InetSocketAddress(config.host, config.port), 250);
        log("server_ready", "server=" + config.host + ':' + config.port);
        return;
      } catch (Throwable t) {
        last = t;
        Thread.sleep(100);
      }
    }
    throw new IOException("D2GS did not open " + config.host + ':' + config.port, last);
  }

  private void awaitConnection(DataInputStream input, long deadline) throws Exception {
    while (System.currentTimeMillis() < deadline) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(input);
      if (packet == null) continue;
      if (packet.dataType() == D2GSData.Connection) {
        Connection connection = (Connection) packet.data(new Connection());
        if (connection.charName() == null) {
          playerId = connection.entityId();
          log("connected", "player=" + playerId);
          return;
        }
      }
      consume(packet);
    }
    throw new IOException("timed out waiting for the D2GS connection acknowledgement");
  }

  private Snapshot awaitTarget(DataInputStream input, long deadline) throws Exception {
    while (System.currentTimeMillis() < deadline) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(input);
      if (packet == null) continue;
      consume(packet);
      for (Snapshot snapshot : monsters.values()) {
        if (snapshot.hasPosition && snapshot.hasVitals && snapshot.life > 0f) return snapshot;
      }
    }
    throw new IOException("timed out waiting for a live monster snapshot");
  }

  private boolean attackUntilDamaged(
      DataInputStream input, OutputStream output, Snapshot selected, float initialLife)
      throws Exception {
    long deadline = System.currentTimeMillis() + config.testTimeoutMillis;
    for (int attempt = 1; attempt <= config.attempts && System.currentTimeMillis() < deadline;
         attempt++) {
      Snapshot target = monsters.get(selected.entityId);
      if (target == null || !target.hasPosition) break;
      float playerX = target.x - 1f;
      float playerY = target.y;
      send(output, positionPacket(playerId, playerX, playerY));
      send(output, castPacket(config.skillId, target.entityId, target.x, target.y));
      log("cast", String.format(
          "attempt=%d skill=%d target=%d targetPos=(%.2f,%.2f)",
          attempt, config.skillId, target.entityId, target.x, target.y));

      long attemptDeadline = Math.min(deadline, System.currentTimeMillis() + 1800L);
      while (System.currentTimeMillis() < attemptDeadline) {
        com.riiablo.net.packet.d2gs.D2GS packet = readPacket(input);
        if (packet == null) continue;
        consume(packet);
        Snapshot current = monsters.get(selected.entityId);
        if (current != null && (current.life < initialLife || current.dead)) return true;
      }
    }
    return false;
  }

  private void consume(com.riiablo.net.packet.d2gs.D2GS packet) {
    if (packet.dataType() != D2GSData.EntitySync) return;
    EntitySync sync = (EntitySync) packet.data(new EntitySync());
    if (sync.type() == 5 && findComponent(sync, ComponentP.MissileP) >= 0) {
      missileSnapshots++;
      return;
    }
    if (sync.type() != 1) return;

    Snapshot snapshot = monsters.get(sync.entityId());
    if (snapshot == null) {
      snapshot = new Snapshot(sync.entityId());
      monsters.put(sync.entityId(), snapshot);
    }
    int index = findComponent(sync, ComponentP.MonsterP);
    if (index >= 0) {
      MonsterP monster = (MonsterP) sync.component(new MonsterP(), index);
      snapshot.monsterClass = monster.monsterId();
    }
    index = findComponent(sync, ComponentP.PositionP);
    if (index >= 0) {
      PositionP position = (PositionP) sync.component(new PositionP(), index);
      snapshot.x = position.x();
      snapshot.y = position.y();
      snapshot.hasPosition = true;
    }
    index = findComponent(sync, ComponentP.VitalsP);
    if (index >= 0) {
      VitalsP vitals = (VitalsP) sync.component(new VitalsP(), index);
      snapshot.life = vitals.hitpoints();
      snapshot.dead = vitals.dead();
      snapshot.hasVitals = true;
    }
  }

  private static int findComponent(EntitySync sync, byte type) {
    for (int i = 0; i < sync.componentLength(); i++) {
      if (sync.componentType(i) == type) return i;
    }
    return -1;
  }

  private static com.riiablo.net.packet.d2gs.D2GS readPacket(DataInputStream input)
      throws IOException {
    try {
      int size = readLittleEndianInt(input);
      if (size <= 0 || size > MAX_PACKET_SIZE) {
        throw new IOException("invalid D2GS packet size " + size);
      }
      byte[] payload = new byte[size];
      input.readFully(payload);
      return com.riiablo.net.packet.d2gs.D2GS.getRootAsD2GS(
          ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN));
    } catch (SocketTimeoutException ignored) {
      return null;
    }
  }

  private static int readLittleEndianInt(DataInputStream input) throws IOException {
    int b0 = input.read();
    if (b0 < 0) throw new EOFException("D2GS closed the connection");
    int b1 = input.read();
    int b2 = input.read();
    int b3 = input.read();
    if ((b1 | b2 | b3) < 0) throw new EOFException("incomplete D2GS size prefix");
    return b0 | b1 << 8 | b2 << 16 | b3 << 24;
  }

  private static ByteBuffer connectionPacket(CharacterHeader character, byte[] d2s) {
    FlatBufferBuilder builder = new FlatBufferBuilder(Math.max(1024, d2s.length + 256));
    int name = builder.createString(character.name);
    byte[] components = new byte[16];
    byte[] transforms = new byte[16];
    byte[] alphas = new byte[16];
    for (int i = 0; i < alphas.length; i++) alphas[i] = (byte) 0xFF;
    int componentsOffset = Connection.createCofComponentsVector(builder, components);
    int transformsOffset = Connection.createCofTransformsVector(builder, transforms);
    int alphasOffset = Connection.createCofAlphasVector(builder, alphas);
    int d2sOffset = Connection.createD2sVector(builder, d2s);
    Connection.startConnection(builder);
    Connection.addCharClass(builder, character.charClass);
    Connection.addCharName(builder, name);
    Connection.addCofComponents(builder, componentsOffset);
    Connection.addCofTransforms(builder, transformsOffset);
    Connection.addCofAlphas(builder, alphasOffset);
    Connection.addD2s(builder, d2sOffset);
    int connection = Connection.endConnection(builder);
    int root = com.riiablo.net.packet.d2gs.D2GS.createD2GS(
        builder, D2GSData.Connection, connection);
    com.riiablo.net.packet.d2gs.D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    return builder.dataBuffer();
  }

  private static ByteBuffer positionPacket(int playerId, float x, float y) {
    FlatBufferBuilder builder = new FlatBufferBuilder(128);
    int position = PositionP.createPositionP(builder, x, y);
    int types = EntitySync.createComponentTypeVector(builder,
        new byte[] {ComponentP.PositionP});
    int components = EntitySync.createComponentVector(builder, new int[] {position});
    int sync = EntitySync.createEntitySync(builder, playerId, 2, 0, types, components);
    int root = com.riiablo.net.packet.d2gs.D2GS.createD2GS(
        builder, D2GSData.EntitySync, sync);
    com.riiablo.net.packet.d2gs.D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    return builder.dataBuffer();
  }

  private static ByteBuffer castPacket(int skillId, int targetId, float x, float y) {
    FlatBufferBuilder builder = new FlatBufferBuilder(128);
    int request = CastSkillRequest.createCastSkillRequest(
        builder, skillId, targetId, x, y);
    int root = com.riiablo.net.packet.d2gs.D2GS.createD2GS(
        builder, D2GSData.CastSkillRequest, request);
    com.riiablo.net.packet.d2gs.D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    return builder.dataBuffer();
  }

  private static void send(OutputStream output, ByteBuffer buffer) throws IOException {
    byte[] bytes = new byte[buffer.remaining()];
    buffer.get(bytes);
    output.write(bytes);
    output.flush();
  }

  private static void log(String phase, String details) {
    System.out.println(TAG + " phase=" + phase + ' ' + details);
  }

  private static final class Snapshot {
    final int entityId;
    int monsterClass = -1;
    float x;
    float y;
    float life;
    boolean dead;
    boolean hasPosition;
    boolean hasVitals;

    Snapshot(int entityId) {
      this.entityId = entityId;
    }
  }

  private static final class CharacterHeader {
    final String name;
    final int charClass;

    CharacterHeader(String name, int charClass) {
      this.name = name;
      this.charClass = charClass;
    }

    static CharacterHeader read(byte[] bytes) {
      if (bytes.length < 0x29
          || (bytes[0] & 0xFF) != 0x55 || (bytes[1] & 0xFF) != 0xAA
          || (bytes[2] & 0xFF) != 0x55 || (bytes[3] & 0xFF) != 0xAA) {
        throw new IllegalArgumentException("save is not a valid D2S file");
      }
      int end = 0x14;
      while (end < 0x24 && bytes[end] != 0) end++;
      String name = new String(bytes, 0x14, end - 0x14, StandardCharsets.US_ASCII);
      if (name.isEmpty()) throw new IllegalArgumentException("D2S character name is empty");
      return new CharacterHeader(name, bytes[0x28] & 0xFF);
    }
  }

  private static final class Config {
    String host = "127.0.0.1";
    int port = DEFAULT_PORT;
    File home;
    File save;
    int seed = 12345;
    int difficulty;
    int skillId = SkillCodes.attack;
    int attempts = 20;
    int connectTimeoutMillis = 2000;
    int serverTimeoutMillis = 180000;
    int testTimeoutMillis = 60000;

    static Config parse(String[] args) throws IOException {
      Config config = new Config();
      for (int i = 0; i < args.length; i++) {
        String arg = args[i];
        if ("--host".equals(arg)) config.host = value(args, ++i, arg);
        else if ("--port".equals(arg)) config.port = integer(args, ++i, arg);
        else if ("--home".equals(arg)) config.home = new File(value(args, ++i, arg));
        else if ("--save".equals(arg)) config.save = new File(value(args, ++i, arg));
        else if ("--seed".equals(arg)) config.seed = integer(args, ++i, arg);
        else if ("--difficulty".equals(arg)) config.difficulty = integer(args, ++i, arg);
        else if ("--skill".equals(arg)) config.skillId = integer(args, ++i, arg);
        else if ("--attempts".equals(arg)) config.attempts = integer(args, ++i, arg);
        else if ("--server-timeout".equals(arg)) {
          config.serverTimeoutMillis = integer(args, ++i, arg) * 1000;
        } else if ("--test-timeout".equals(arg)) {
          config.testTimeoutMillis = integer(args, ++i, arg) * 1000;
        } else if ("--help".equals(arg)) {
          usage();
          System.exit(0);
        } else {
          throw new IllegalArgumentException("unknown argument " + arg);
        }
      }

      if (config.home == null) {
        String env = System.getenv("D2_HOME");
        if (env != null && !env.isEmpty()) config.home = new File(env);
      }
      if (config.home != null && !new File(config.home, "d2data.mpq").isFile()) {
        throw new IOException("--home is not a valid D2 installation: " + config.home);
      }
      if (config.home != null && config.port != DEFAULT_PORT) {
        throw new IllegalArgumentException("embedded D2GS currently listens on port "
            + DEFAULT_PORT + "; omit --home when testing an external server port");
      }
      if (config.save == null && config.home != null) {
        config.save = firstSave(new File(config.home, "Save"));
      }
      if (config.save == null || !config.save.isFile()) {
        throw new IOException("provide --save <character.d2s>, or put a save in <home>/Save");
      }
      return config;
    }

    private static File firstSave(File directory) {
      File[] saves = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".d2s"));
      return saves == null || saves.length == 0 ? null : saves[0];
    }

    private static String value(String[] args, int index, String option) {
      if (index >= args.length) throw new IllegalArgumentException(option + " requires a value");
      return args[index];
    }

    private static int integer(String[] args, int index, String option) {
      return Integer.parseInt(value(args, index, option));
    }

    private static void usage() {
      System.out.println("Usage: D2GSHeadlessClient [--home <D2 dir>] [--save <file.d2s>]"
          + " [--host 127.0.0.1] [--port 6114] [--skill 0] [--attempts 20]");
    }
  }
}
