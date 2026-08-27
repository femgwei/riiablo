package com.riiablo.server.d2gs;

import com.google.flatbuffers.FlatBufferBuilder;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.lang3.ArrayUtils;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.utils.BitVector;
import net.mostlyoriginal.api.event.common.EventSystem;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.net.ServerSocket;
import com.badlogic.gdx.net.Socket;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.TimeUtils;

import com.riiablo.COFs;
import com.riiablo.Files;
import com.riiablo.Riiablo;
import com.riiablo.audio.ServerAudio;
import com.riiablo.codec.Animation;
import com.riiablo.codec.D2;
import com.riiablo.codec.StringTBLs;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.AIStepper;
import com.riiablo.engine.server.Actioneer;
import com.riiablo.engine.server.AuraEcsSystem;
import com.riiablo.engine.server.AnimStepper;
import com.riiablo.engine.server.MissileCollisionSystem;
import com.riiablo.engine.server.ServerSkillSystem;
import com.riiablo.engine.server.ServerPlayerDeathSystem;
import com.riiablo.engine.server.ServerMonsterCorpseSystem;
import com.riiablo.engine.server.SequenceHandler;
import com.riiablo.engine.server.StateUpdater;
import com.riiablo.attributes.ExperienceManager;
import com.riiablo.engine.server.AnimDataResolver;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.DeathRewardSystem;
import com.riiablo.engine.server.ItemInteractor;
import com.riiablo.engine.server.ItemManager;
import com.riiablo.engine.server.ObjectInitializer;
import com.riiablo.engine.server.ObjectInteractor;
import com.riiablo.engine.server.object.NativeObjectDropSystem;
import com.riiablo.engine.server.object.NativeShrineSystem;
import com.riiablo.engine.server.Pathfinder;
import com.riiablo.engine.server.LeapSystem;
import com.riiablo.engine.server.SerializationManager;
import com.riiablo.engine.server.ServerEntityFactory;
import com.riiablo.engine.server.ServerItemManager;
import com.riiablo.engine.server.item.AuthoritativeItemMoveService;
import com.riiablo.engine.server.item.ItemMoveIntent;
import com.riiablo.engine.server.item.ItemMoveRequestCache;
import com.riiablo.engine.server.player.PlayerStatsManager;
import com.riiablo.engine.server.player.SkillPointRequestCache;
import com.riiablo.engine.server.ServerNetworkIdManager;
import com.riiablo.engine.server.VelocityAdder;
import com.riiablo.engine.server.WarpInteractor;
import com.riiablo.engine.server.component.Networked;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.quest.Act1QuestSystem;
import com.riiablo.engine.server.quest.NativeMercenaryRewardSystem;
import com.riiablo.engine.server.quest.NativeCountessRewardSystem;
import com.riiablo.engine.server.quest.NativeCharsiImbueSystem;
import com.riiablo.engine.server.npc.NpcVendorSessionManager;
import com.riiablo.engine.server.npc.NpcServiceRequestCache;
import com.riiablo.engine.server.npc.NpcRepairService;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.VendorGenerator;
import com.riiablo.item.ItemWriter;
import com.riiablo.io.ByteOutput;
import io.netty.buffer.Unpooled;
import com.riiablo.map.Act1MapBuilder;
import com.riiablo.map.DS1;
import com.riiablo.map.DS1Loader;
import com.riiablo.map.DT1;
import com.riiablo.map.DT1Loader;
import com.riiablo.map.Map;
import com.riiablo.map.MapManager;
import com.riiablo.mpq.MPQFileHandleResolver;
import com.riiablo.net.packet.d2gs.BeltToCursor;
import com.riiablo.net.packet.d2gs.BodyToCursor;
import com.riiablo.net.packet.d2gs.Connection;
import com.riiablo.net.packet.d2gs.CastSkillRequest;
import com.riiablo.net.packet.d2gs.SpendSkillPointRequest;
import com.riiablo.net.packet.d2gs.SelectSkillRequest;
import com.riiablo.net.packet.d2gs.SpendSkillPointResult;
import com.riiablo.net.packet.d2gs.CursorToBelt;
import com.riiablo.net.packet.d2gs.CursorToBody;
import com.riiablo.net.packet.d2gs.CursorToGround;
import com.riiablo.net.packet.d2gs.CursorToStore;
import com.riiablo.net.packet.d2gs.D2GSData;
import com.riiablo.net.packet.d2gs.Disconnect;
import com.riiablo.net.packet.d2gs.GroundToCursor;
import com.riiablo.net.packet.d2gs.Ping;
import com.riiablo.net.packet.d2gs.NpcServiceRequest;
import com.riiablo.net.packet.d2gs.NpcServiceResult;
import com.riiablo.net.packet.d2gs.NpcServiceStock;
import com.riiablo.net.packet.d2gs.StoreToCursor;
import com.riiablo.net.packet.d2gs.SwapBeltItem;
import com.riiablo.net.packet.d2gs.SwapBodyItem;
import com.riiablo.net.packet.d2gs.SwapStoreItem;
import com.riiablo.net.packet.d2gs.ItemMoveRequest;
import com.riiablo.net.packet.d2gs.ItemMoveResult;
import com.riiablo.net.packet.d2gs.ItemMoveSnapshotEntry;
import com.riiablo.net.packet.d2gs.ItemMoveOperation;
import com.riiablo.net.packet.d2gs.ItemMoveFailure;
import com.riiablo.save.CharData;
import com.riiablo.util.DebugUtils;

public class D2GS extends ApplicationAdapter {
  private static final String TAG = "D2GS";

  private static final boolean DEBUG                  = true;
  private static final boolean DEBUG_RECEIVED_CACHE   = DEBUG && !true;
  private static final boolean DEBUG_RECEIVED_PACKETS = DEBUG && true;
  private static final boolean DEBUG_SENT_PACKETS     = DEBUG && true;

  private static final int PORT = 6114;
  private static final int MAX_CLIENTS = Riiablo.MAX_PLAYERS;

  public static void main(String[] args) {
    Options options = new Options()
        .addOption("home", true, "directory containing D2 MPQ files")
        .addOption("seed", true, "seed used to generate map")
        .addOption("diff", true, "difficulty (0-2)");

    CommandLine cmd = null;
    try {
      CommandLineParser parser = new DefaultParser();
      cmd = parser.parse(options, args);
    } catch (Throwable t) {
      System.err.println(t.getMessage());
      System.out.println("Failed to start server instance!");
      return;
    }

    FileHandle home = null;
    if (cmd != null && cmd.hasOption("home")) {
      home = new FileHandle(cmd.getOptionValue("home"));
      if (!home.child("d2data.mpq").exists()) {
        throw new GdxRuntimeException("home does not refer to a valid D2 installation");
      }
    } else {
      home = new FileHandle(System.getProperty("user.home")).child("diablo");
      System.out.println("Home not specified, using " + home);
      if (!home.exists() || !home.child("d2data.mpq").exists()) {
        throw new GdxRuntimeException("home does not refer to a valid D2 installation");
      }
    }

    int seed = 0;
    if (cmd.hasOption("seed")) {
      String seedArg = cmd.getOptionValue("seed");
      try {
        seed = Integer.parseInt(seedArg);
      } catch (Throwable t) {
        System.err.println("Invalid seed provided: " + seedArg);
      }
    }

    int diff = 0;
    if (cmd.hasOption("diff")) {
      String diffArg = cmd.getOptionValue("diff");
      try {
        diff = Integer.parseInt(diffArg);
      } catch (Throwable t) {
        System.err.println("Invalid diff provided: " + diffArg);
      }
    }

    HeadlessApplicationConfiguration config = new HeadlessApplicationConfiguration();
    config.updatesPerSecond = (int) Animation.FRAMES_PER_SECOND;
    new HeadlessApplication(new D2GS(home, seed, diff), config);
  }

  ServerSocket server;
  Thread connectionListener;
  volatile boolean kill = false;
  ThreadGroup clientThreads;
  final Client[] clients = new Client[MAX_CLIENTS];
  int numClients = 0;
  int connected = 0;

  final BlockingQueue<Packet> packets = new ArrayBlockingQueue<>(32);
  final Collection<Packet> cache = new ArrayList<>(1024);
  final BlockingQueue<Packet> outPackets = new ArrayBlockingQueue<>(1024);
  final IntIntMap player = new IntIntMap();

  static final BitVector ignoredPackets = new BitVector(D2GSData.names.length); {
    ignoredPackets.set(D2GSData.EntitySync);
  }

  FileHandle home;
  int seed;
  int diff;

  World world;
  Map map;

  EntityFactory factory;
  ItemManager itemManager;
  MapManager mapManager;
  NetworkSynchronizer sync;
  final NpcVendorSessionManager npcVendors = new NpcVendorSessionManager();
  final NpcServiceRequestCache npcRequestCache = new NpcServiceRequestCache();
  final AuthoritativeItemMoveService authoritativeItems = new AuthoritativeItemMoveService();
  final ItemMoveRequestCache itemMoveRequestCache = new ItemMoveRequestCache();
  final SkillPointRequestCache skillPointRequestCache = new SkillPointRequestCache();

  protected ComponentMapper<Networked> mNetworked;

  D2GS(FileHandle home, int seed, int diff) {
    this.home = home;
    this.seed = seed;
    this.diff = diff;
  }

  @Override
  public void create() {
    Gdx.app.setLogLevel(Application.LOG_DEBUG);

    final Calendar calendar = Calendar.getInstance();
    DateFormat format = DateFormat.getDateTimeInstance();
    Gdx.app.log(TAG, format.format(calendar.getTime()));

    try {
      InetAddress address = InetAddress.getLocalHost();
      Gdx.app.log(TAG, "IP Address: " + address.getHostAddress() + ":" + PORT);
      Gdx.app.log(TAG, "Host Name: " + address.getHostName());
    } catch (UnknownHostException e) {
      Gdx.app.error(TAG, e.getMessage(), e);
    }

    Riiablo.home = home = Gdx.files.absolute(home.path());
    if (!home.exists() || !home.child("d2data.mpq").exists()) {
      throw new GdxRuntimeException("home does not refer to a valid D2 installation. Copy MPQs to " + home);
    }

    Riiablo.mpqs = new MPQFileHandleResolver();
    Riiablo.assets = new AssetManager();
    Riiablo.files = new Files(Riiablo.assets);
    Riiablo.cofs = new COFs(Riiablo.assets); // TODO: not needed in prod
    Riiablo.string = new StringTBLs(Riiablo.mpqs); // TODO: not needed in prod
    Riiablo.anim = D2.loadFromFile(Riiablo.mpqs.resolve("data\\global\\eanimdata.d2"));
    Riiablo.audio = new ServerAudio(Riiablo.assets);

    // set DT1 to headless mode
    DT1.loadData = false;
    Riiablo.assets.setLoader(DS1.class, new DS1Loader(Riiablo.mpqs));
    Riiablo.assets.setLoader(DT1.class, new DT1Loader(Riiablo.mpqs));

    if (seed == 0) {
      Gdx.app.log(TAG, "Generating seed...");
      seed = 0;
      Gdx.app.log(TAG, "seed=" + seed);
    }

    Gdx.app.log(TAG, "Generating map...");
    map = new Map(seed, diff);
    mapManager = new MapManager();
    Gdx.app.log(TAG, "  generating act 1...");
    long start = TimeUtils.millis();
    map.generate(0);
    Gdx.app.log(TAG, "  act 1 generated in " + (TimeUtils.millis() - start) + "ms");

    Gdx.app.log(TAG, "Loading act 1...");
    map.load();
    map.finishLoading();

    factory = new ServerEntityFactory();
    itemManager = new ServerItemManager();
    mapManager = new MapManager();
    sync = new NetworkSynchronizer();
    WorldConfigurationBuilder builder = new WorldConfigurationBuilder()
        .with(new EventSystem())
        .with(new Act1QuestSystem())
        .with(new NativeMercenaryRewardSystem())
        .with(new NativeCountessRewardSystem())
        .with(new NativeCharsiImbueSystem())
        .with(new ServerNetworkIdManager())
        .with(new SerializationManager())
        .with(mapManager)
        .with(itemManager)
        .with(new ItemGenerator())
        .with(new CofManager())
        .with(new ObjectInitializer())
        .with(new ObjectInteractor(), new WarpInteractor(), new ItemInteractor())
        .with(new NativeObjectDropSystem())
        .with(new NativeShrineSystem())

        .with(new Actioneer())
        .with(new ServerMonsterCorpseSystem())
        .with(new AuraEcsSystem())
        .with(new ServerPlayerDeathSystem())
        .with(new ServerSkillSystem())
        .with(new DeathRewardSystem())
        .with(new SequenceHandler())
        // Apply a newly queued attack mode before advancing animation. If the
        // old neutral animation wraps first, its Finished event can otherwise
        // consume the fresh attack sequence before the attack keyframe runs.
        .with(new AnimStepper())
        .with(new MissileCollisionSystem())
        .with(new StateUpdater())
        .with(new ExperienceManager())

        .with(new VendorGenerator())
        .with(new AIStepper())
        .with(new Pathfinder())

        .with(new VelocityAdder()) // FIXME: temp until proper physics implemented
        .with(new LeapSystem())

        .with(factory)
        .with(sync)
        .with(new AnimDataResolver())
        ;
    WorldConfiguration config = builder.build()
        .register("map", map)
        .register("factory", factory)
        .register("player", player)
        .register("outPackets", outPackets)
        ;
    Riiablo.engine = world = new World(config);

    world.inject(map);
    world.inject(Act1MapBuilder.INSTANCE);

    map.generate();
    mapManager.createEntities();

    mNetworked = world.getMapper(Networked.class);
    world.delta = Animation.FRAME_DURATION;

    clientThreads = new ThreadGroup("D2GSClients");

    Gdx.app.log(TAG, "Starting server...");
    server = Gdx.net.newServerSocket(Net.Protocol.TCP, PORT, null);
    connectionListener = new Thread(new Runnable() {
      @Override
      public void run() {
        while (!kill) {
          Gdx.app.log(TAG, "waiting...");
          Socket socket;
          try {
            socket = server.accept(null);
          } catch (Throwable t) {
            // dispose() closes the listening socket to wake accept(). That is
            // an ordinary shutdown path, not a server failure.
            if (kill) break;
            Gdx.app.error(TAG, "Unable to accept client", t);
            continue;
          }
          Gdx.app.log(TAG, "connection from " + socket.getRemoteAddress());
          if (numClients >= MAX_CLIENTS) {
            // TODO: send server is full message
            socket.dispose();
          } else {
            try {
              synchronized (clients) {
                int id = ArrayUtils.indexOf(clients, null);
                assert id != ArrayUtils.INDEX_NOT_FOUND : "numClients=" + numClients + " but no index available";
                Gdx.app.log(TAG, "assigned " + socket.getRemoteAddress() + " to " + id);
                Client client = clients[id] = new Client(id, socket);
                numClients++;
                //connected |= (1 << id);
                client.start();
              }
            } catch (Throwable t) {
              Gdx.app.error(TAG, t.getMessage(), t);
              socket.dispose();
            }
          }
        }

        Gdx.app.log(TAG, "killing child threads...");
        synchronized (clients) {
          for (Client client : clients) {
            if (client != null) {
              client.kill = true;
              client.socket.dispose();
              try {
                client.join();
              } catch (Throwable ignored) {}
            }
          }
          numClients = 0;
          connected = 0;
        }

        Gdx.app.log(TAG, "killing thread...");
      }
    });
    connectionListener.setName("D2GS Connection Listener");
    connectionListener.start();
  }

  @Override
  public void dispose() {
    Gdx.app.log(TAG, "Shutting down...");
    kill = true;
    server.dispose();
    try {
      connectionListener.join();
    } catch (Throwable ignored) {}
    Riiablo.assets.dispose();
  }

  @Override
  public void render() {
    cache.clear();
    int cached = packets.drainTo(cache);
    if (DEBUG_RECEIVED_CACHE && cached > 0) Gdx.app.log(TAG, "processing " + cached + " packets");
    for (Packet packet : cache) {
      if (DEBUG_RECEIVED_PACKETS && !ignoredPackets.get(packet.data.dataType())) Gdx.app.log(TAG, "processing " + D2GSData.name(packet.data.dataType()) + " packet from " + packet.id);
      process(packet);
    }

    world.process();

    cache.clear();
    outPackets.drainTo(cache);
    for (Packet packet : cache) {
      if (DEBUG_SENT_PACKETS && !ignoredPackets.get(packet.data.dataType())) Gdx.app.log(TAG, "dispatching " + D2GSData.name(packet.data.dataType()) + " packet to " + String.format("0x%08X", packet.id));
      for (int i = 0, flag = 1; i < MAX_CLIENTS; i++, flag <<= 1) {
        if ((packet.id & flag) == flag && ((connected & flag) == flag || packet.data.dataType() == D2GSData.Connection)) {
          Client client = clients[i];
          if (client == null) continue;
          try {
            if (DEBUG_SENT_PACKETS && !ignoredPackets.get(packet.data.dataType())) Gdx.app.log(TAG, "  dispatching packet to " + i);
            client.send(packet);
          } catch (Throwable t) {
            Gdx.app.error(TAG, t.getMessage(), t);
          }
        }
      }
    }
  }

  private void process(Packet packet) {
    switch (packet.data.dataType()) {
      case D2GSData.Connection:
        Connection(packet);
        break;
      case D2GSData.EntitySync:
        Synchronize(packet);
        break;
      case D2GSData.GroundToCursor:
        GroundToCursor(packet);
        break;
      case D2GSData.CursorToGround:
        CursorToGround(packet);
        break;
      case D2GSData.StoreToCursor:
        StoreToCursor(packet);
        break;
      case D2GSData.CursorToStore:
        CursorToStore(packet);
        break;
      case D2GSData.SwapStoreItem:
        SwapStoreItem(packet);
        break;
      case D2GSData.BodyToCursor:
        BodyToCursor(packet);
        break;
      case D2GSData.CursorToBody:
        CursorToBody(packet);
        break;
      case D2GSData.SwapBodyItem:
        SwapBodyItem(packet);
        break;
      case D2GSData.BeltToCursor:
        BeltToCursor(packet);
        break;
      case D2GSData.CursorToBelt:
        CursorToBelt(packet);
        break;
      case D2GSData.CastSkillRequest:
        CastSkillRequest(packet);
        break;
      case D2GSData.SelectSkillRequest:
        SelectSkillRequest(packet);
        break;
      case D2GSData.SpendSkillPointRequest:
        SpendSkillPointRequest(packet);
        break;
      case D2GSData.NpcServiceRequest:
        NpcServiceRequest(packet);
        break;
      case D2GSData.SwapBeltItem:
        SwapBeltItem(packet);
        break;
      case D2GSData.ItemMoveRequest:
        ItemMoveRequest(packet);
        break;
      case D2GSData.Ping:
        Ping(packet);
        break;
      default:
        Gdx.app.error(TAG, "Unknown packet type: " + packet.data.dataType());
    }
  }

  private void Connection(Packet packet) {
    Connection connection = (Connection) packet.data.data(new Connection());
    String charName = connection.charName();
    int charClass = connection.charClass();
    Gdx.app.log(TAG, "Connection from " + clients[packet.id].socket.getRemoteAddress() + " : " + charName);

    byte[] cofComponents = new byte[16];
    connection.cofComponentsAsByteBuffer().get(cofComponents);
    Gdx.app.log(TAG, "  " + DebugUtils.toByteArray(cofComponents));

    byte[] cofAlphas = new byte[16];
    connection.cofAlphasAsByteBuffer().get(cofAlphas);
    Gdx.app.log(TAG, "  " + Arrays.toString(cofAlphas));
    Gdx.app.log(TAG, "  >" + Arrays.toString(com.riiablo.util.ArrayUtils.toFloatingPoint(cofAlphas)));

    byte[] cofTransforms = new byte[16];
    connection.cofTransformsAsByteBuffer().get(cofTransforms);
    Gdx.app.log(TAG, "  " + DebugUtils.toByteArray(cofTransforms));

    ByteBuffer d2sData = connection.d2sAsByteBuffer();
    CharData charData = CharData.loadFromBuffer(diff, d2sData);
    // Rebuild equipment-derived attributes and native skills after loading a
    // remote save.  Without this, a valid starting Amazon has a javelin in her
    // hand but the authoritative skill map never gains Throw, causing every
    // network throw request to be rejected as skill_not_owned.
    charData.update();
    Gdx.app.log(TAG, "  " + charData);

    Vector2 origin = map.find(Map.ID.TOWN_ENTRY_1);
    if (origin == null) origin = map.find(Map.ID.TOWN_ENTRY_2);
    if (origin == null) origin = map.find(Map.ID.TP_LOCATION);
    int entityId = factory.createPlayer(charData, origin);
    player.put(packet.id, entityId);
    Gdx.app.log(TAG, "  entityId=" + entityId);

    FlatBufferBuilder builder = new FlatBufferBuilder();
    Connection.startConnection(builder);
    Connection.addEntityId(builder, entityId);
    int connectionOffset = Connection.endConnection(builder);
    int offset = com.riiablo.net.packet.d2gs.D2GS.createD2GS(builder, D2GSData.Connection, connectionOffset);
    com.riiablo.net.packet.d2gs.D2GS.finishSizePrefixedD2GSBuffer(builder, offset);
    Packet response = Packet.obtain(1 << packet.id, builder.dataBuffer());
    outPackets.offer(response);

    Synchronize(packet.id, entityId);

    BroadcastConnect(packet.id, connection, charData, entityId);
  }

  private void Synchronize(int id, int entityId) {

  }

  private void BroadcastConnect(int id, Connection connection, CharData charData, int entityId) {
    FlatBufferBuilder builder = new FlatBufferBuilder();
    int charNameOffset = builder.createString(charData.name);

    byte[] components = new byte[16];
    connection.cofComponentsAsByteBuffer().get(components);
    int componentsOffset = Connection.createCofComponentsVector(builder, components);

    byte[] alphas = new byte[16];
    connection.cofAlphasAsByteBuffer().get(alphas);
    int alphasOffset = Connection.createCofAlphasVector(builder, alphas);

    byte[] transforms = new byte[16];
    connection.cofTransformsAsByteBuffer().get(transforms);
    int transformsOffset = Connection.createCofTransformsVector(builder, transforms);

    Connection.startConnection(builder);
    Connection.addEntityId(builder, entityId);
    Connection.addCharClass(builder, charData.charClass);
    Connection.addCharName(builder, charNameOffset);
    Connection.addCofComponents(builder, componentsOffset);
    Connection.addCofAlphas(builder, alphasOffset);
    Connection.addCofTransforms(builder, transformsOffset);
    int connectionOffset = Connection.endConnection(builder);
    int offset = com.riiablo.net.packet.d2gs.D2GS.createD2GS(builder, D2GSData.Connection, connectionOffset);
    com.riiablo.net.packet.d2gs.D2GS.finishSizePrefixedD2GSBuffer(builder, offset);

    Packet broadcast = Packet.obtain(~(1 << id), builder.dataBuffer());
    boolean success = outPackets.offer(broadcast);
    assert success;
  }

  private void Disconnect(int id) {
    int entityId = player.get(id, Engine.INVALID_ENTITY);
    if (entityId != Engine.INVALID_ENTITY) {
      FlatBufferBuilder builder = new FlatBufferBuilder();
      int disconnectOffset = Disconnect.createDisconnect(builder, entityId);
      int offset = com.riiablo.net.packet.d2gs.D2GS.createD2GS(builder, D2GSData.Disconnect, disconnectOffset);
      com.riiablo.net.packet.d2gs.D2GS.finishSizePrefixedD2GSBuffer(builder, offset);
      Packet broadcast = Packet.obtain(~(1 << id), builder.dataBuffer());
      outPackets.offer(broadcast);
      npcRequestCache.clear(id);
      npcVendors.clearPlayer(entityId);
      itemMoveRequestCache.clearConnection(id);
      skillPointRequestCache.clearConnection(id);
      authoritativeItems.reset(entityId);

      world.delete(entityId);
      player.remove(id, Engine.INVALID_ENTITY);
    } else {
      Gdx.app.log(TAG, "client " + id + " disconnected before character handshake");
    }
    synchronized (clients) {
      clients[id] = null;
      numClients--;
      connected &= ~(1 << id);
    }
  }

  private void Ping(Packet packet) {
    Ping ping = (Ping) packet.data.data(new Ping());
    FlatBufferBuilder builder = new FlatBufferBuilder(0);
    int dataOffset = Ping.createPing(builder, ping.tickCount(), ping.sendTime(), TimeUtils.millis() - packet.time, false);
    int root = com.riiablo.net.packet.d2gs.D2GS.createD2GS(builder, D2GSData.Ping, dataOffset);
    com.riiablo.net.packet.d2gs.D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    Packet response = Packet.obtain(1 << packet.id, builder.dataBuffer());
    outPackets.offer(response);
  }

  private void Synchronize(Packet packet) {
    int entityId = player.get(packet.id, Engine.INVALID_ENTITY);
    assert entityId != Engine.INVALID_ENTITY;
    sync.sync(entityId, packet.data);
  }

  /** Handles untrusted combat input; all damage and projectile creation stays on the server. */
  private void CastSkillRequest(Packet packet) {
    int entityId = getPlayerEntityId(packet);
    CastSkillRequest request = (CastSkillRequest) packet.data.data(new CastSkillRequest());
    int targetId = request.targetId();
    if (targetId != Engine.INVALID_ENTITY
        && !world.getMapper(com.riiablo.engine.server.component.Class.class).has(targetId)) {
      Gdx.app.log(TAG, "[NET_CAST] phase=reject player=" + entityId
          + " skill=" + request.skillId() + " reason=unknown_target target=" + targetId);
      return;
    }
    if (Riiablo.files.skills.get(request.skillId()) == null) {
      Gdx.app.log(TAG, "[NET_CAST] phase=reject player=" + entityId
          + " skill=" + request.skillId() + " reason=unknown_skill");
      return;
    }
    com.riiablo.engine.server.component.Player playerComponent =
        world.getMapper(com.riiablo.engine.server.component.Player.class).get(entityId);
    // Normal attack is a native D2 action (skill id 0), not a learned row in
    // the character's skill table.  Remote CharData instances can therefore
    // legitimately report zero for getSkill(attack) until their local item
    // listeners have rebuilt the derived skill map.  Keep the server
    // authoritative, but accept this built-in action explicitly.
    boolean builtInSkill = request.skillId() == com.riiablo.skill.SkillCodes.attack;
    if (playerComponent == null || playerComponent.data == null
        || (!builtInSkill && playerComponent.data.getSkill(request.skillId()) <= 0)) {
      Gdx.app.log(TAG, "[NET_CAST] phase=reject player=" + entityId
          + " skill=" + request.skillId() + " reason=skill_not_owned");
      return;
    }
    float x = request.targetX();
    float y = request.targetY();
    if (!Float.isFinite(x) || !Float.isFinite(y)) {
      Gdx.app.log(TAG, "[NET_CAST] phase=reject player=" + entityId
          + " skill=" + request.skillId() + " reason=invalid_target_position");
      return;
    }
    if (targetId != Engine.INVALID_ENTITY) {
      com.riiablo.engine.server.component.Position targetPosition = world.getMapper(
          com.riiablo.engine.server.component.Position.class).get(targetId);
      if (targetPosition == null) {
        Gdx.app.log(TAG, "[NET_CAST] phase=reject player=" + entityId
            + " skill=" + request.skillId() + " reason=target_has_no_position");
        return;
      }
      // Never trust a client-supplied aim point for an entity target.
      x = targetPosition.position.x;
      y = targetPosition.position.y;
    }
    Vector2 playerPosition = world.getMapper(
        com.riiablo.engine.server.component.Position.class).get(entityId).position;
    if (playerPosition.dst2(x, y) > 2500f) {
      Gdx.app.log(TAG, "[NET_CAST] phase=reject player=" + entityId
          + " skill=" + request.skillId() + " reason=target_position_out_of_bounds");
      return;
    }
    if (request.skillId() == com.riiablo.skill.SkillCodes.attack
        && targetId != Engine.INVALID_ENTITY) {
      com.riiablo.item.Item weapon = playerComponent.data.getItems().getEquipped(
          com.riiablo.item.BodyLoc.RARM);
      if (weapon == null) {
        weapon = playerComponent.data.getItems().getEquipped(com.riiablo.item.BodyLoc.LARM);
      }
      boolean rangedWeapon = weapon != null && weapon.type != null
          && (weapon.type.is(com.riiablo.item.Type.BOW)
              || weapon.type.is(com.riiablo.item.Type.XBOW));
      if (!rangedWeapon && !world.getSystem(Actioneer.class)
          .isInMeleeRange(entityId, targetId, 3)) {
        Gdx.app.log(TAG, "[NET_CAST] phase=reject player=" + entityId
            + " skill=" + request.skillId() + " reason=melee_out_of_range target=" + targetId);
        return;
      }
    }
    Gdx.app.log(TAG, String.format(
        "[NET_CAST] phase=accept player=%d skill=%d target=%d targetPos=(%.2f,%.2f)",
        entityId, request.skillId(), targetId, x, y));
    Actioneer actioneer = world.getSystem(Actioneer.class);
    actioneer.cast(entityId, request.skillId(), targetId, new Vector2(x, y));
    com.riiablo.engine.server.component.CofReference cof = world.getMapper(
        com.riiablo.engine.server.component.CofReference.class).get(entityId);
    com.riiablo.engine.server.component.AnimData anim = world.getMapper(
        com.riiablo.engine.server.component.AnimData.class).get(entityId);
    com.riiablo.engine.server.component.Sequence sequence = world.getMapper(
        com.riiablo.engine.server.component.Sequence.class).get(entityId);
    Gdx.app.log(TAG, String.format(
        "[NET_CAST] phase=queued player=%d casting=%s sequence=%s token=%s mode=%d wclass=%s "
            + "animFrame=%d animFrames=%d animSpeed=%d lastKeyframe=%d keyframes=%d activeKeyframes=%s",
        entityId, actioneer.hasCasting(entityId), sequence != null,
        cof != null ? cof.token : "none", cof != null ? cof.mode : -1,
        cof != null ? com.riiablo.engine.Engine.getWClass(cof.wclass) : "none",
        anim != null ? anim.frame : -1, anim != null ? anim.numFrames : -1,
        anim != null ? anim.speed : -1, anim != null ? anim.lastKeyframeIndex : -1,
        anim != null && anim.keyframes != null ? anim.keyframes.length : 0,
        summarizeKeyframes(anim)));
  }

  /** Handles server-authoritative action selection and aura activation. */
  private void SelectSkillRequest(Packet packet) {
    int entityId = getPlayerEntityId(packet);
    SelectSkillRequest request = (SelectSkillRequest) packet.data.data(new SelectSkillRequest());
    int button = request.button();
    int skillId = request.skillId();
    Player playerComponent = entityId == Engine.INVALID_ENTITY ? null
        : world.getMapper(Player.class).get(entityId);
    CharData data = playerComponent != null ? playerComponent.data : null;
    com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(skillId);
    boolean builtIn = skillId == com.riiablo.skill.SkillCodes.attack;
    String reason = null;
    if (data == null) reason = "PLAYER_NOT_FOUND";
    else if (button != com.badlogic.gdx.Input.Buttons.LEFT
        && button != com.badlogic.gdx.Input.Buttons.RIGHT) reason = "INVALID_BUTTON";
    else if (skill == null) reason = "UNKNOWN_SKILL";
    else if (!builtIn && data.getSkill(skillId) <= 0) reason = "SKILL_NOT_OWNED";
    else if (button == com.badlogic.gdx.Input.Buttons.LEFT && !skill.leftskill) {
      reason = "LEFT_SKILL_FORBIDDEN";
    }
    if (reason != null) {
      Gdx.app.log(TAG, "[SKILL_SELECT_NET] phase=reject connection=" + packet.id
          + " request=" + request.requestId() + " player=" + entityId
          + " button=" + button + " skill=" + skillId + " reason=" + reason);
      return;
    }

    data.setAction(button, skillId);
    AuraEcsSystem auraSystem = world.getSystem(AuraEcsSystem.class);
    boolean auraActive = false;
    if (button == com.badlogic.gdx.Input.Buttons.RIGHT) {
      if (skill.aura) auraActive = auraSystem.selectAura(entityId, skillId);
      else auraSystem.clearAura(entityId);
    }
    Gdx.app.log(TAG, "[SKILL_SELECT_NET] phase=accept connection=" + packet.id
        + " request=" + request.requestId() + " player=" + entityId
        + " button=" + button + " skill=" + skillId + " aura=" + auraActive);
  }

  /** Handles an idempotent, server-authoritative skill allocation request. */
  private void SpendSkillPointRequest(Packet packet) {
    SpendSkillPointRequest request = (SpendSkillPointRequest) packet.data.data(
        new SpendSkillPointRequest());
    SkillPointRequestCache.Entry cached = skillPointRequestCache.lookup(
        packet.id, request.requestId());
    if (cached != null) {
      if (cached.skillId == request.skillId()) {
        outPackets.offer(Packet.obtain(1 << packet.id, ByteBuffer.wrap(cached.response())));
        Gdx.app.log(TAG, "[SKILL_POINT_NET] phase=replay connection=" + packet.id
            + " request=" + request.requestId() + " skill=" + request.skillId());
      } else {
        sendSkillPointResult(packet.id, request.requestId(), false, "REQUEST_ID_REUSED",
            request.skillId(), 0, 0, false);
      }
      return;
    }

    int entityId = getPlayerEntityId(packet);
    Player playerComponent = entityId == Engine.INVALID_ENTITY ? null
        : world.getMapper(Player.class).get(entityId);
    CharData data = playerComponent != null ? playerComponent.data : null;
    String reason = PlayerStatsManager.INSTANCE.validateSkillPoint(data, request.skillId());
    boolean success = PlayerStatsManager.SKILL_OK.equals(reason)
        && PlayerStatsManager.INSTANCE.spendSkillPoint(data, request.skillId());
    if (!success && PlayerStatsManager.SKILL_OK.equals(reason)) reason = "UPDATE_REJECTED";
    int level = data != null ? data.getBaseSkillLevel(request.skillId()) : 0;
    int points = data != null
        ? PlayerStatsManager.INSTANCE.getAvailableSkillPoints(data) : 0;
    sendSkillPointResult(packet.id, request.requestId(), success, reason,
        request.skillId(), level, points, true);
    if (success && entityId != Engine.INVALID_ENTITY) sync.process(entityId);
    Gdx.app.log(TAG, "[SKILL_POINT_NET] phase=" + (success ? "accept" : "reject")
        + " connection=" + packet.id + " player=" + entityId
        + " request=" + request.requestId() + " skill=" + request.skillId()
        + " level=" + level + " points=" + points + " reason=" + reason);
  }

  private void sendSkillPointResult(int clientId, long requestId, boolean success,
      String reason, int skillId, int skillLevel, int skillPoints, boolean cache) {
    FlatBufferBuilder builder = new FlatBufferBuilder(128);
    int reasonOffset = builder.createString(reason == null ? "" : reason);
    int result = SpendSkillPointResult.createSpendSkillPointResult(builder,
        requestId, success, reasonOffset, skillId,
        Math.max(0, Math.min(0xFF, skillLevel)),
        Math.max(0, Math.min(0xFFFF, skillPoints)));
    int root = com.riiablo.net.packet.d2gs.D2GS.createD2GS(
        builder, D2GSData.SpendSkillPointResult, result);
    com.riiablo.net.packet.d2gs.D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    ByteBuffer response = builder.dataBuffer();
    byte[] bytes = new byte[response.remaining()];
    response.duplicate().get(bytes);
    if (cache) skillPointRequestCache.put(clientId, requestId, skillId, bytes);
    outPackets.offer(Packet.obtain(1 << clientId, ByteBuffer.wrap(bytes)));
  }

  private static String summarizeKeyframes(com.riiablo.engine.server.component.AnimData anim) {
    if (anim == null || anim.keyframes == null) return "none";
    int frames = Math.min(anim.numFrames >>> 8, anim.keyframes.length);
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < frames; i++) {
      if (anim.keyframes[i] == com.riiablo.engine.Engine.KEYFRAME_NIL) continue;
      if (out.length() > 0) out.append(',');
      out.append(i).append(':').append((int) anim.keyframes[i]);
    }
    return out.length() == 0 ? "none" : out.toString();
  }

  /**
   * Authenticated multiplayer NPC boundary. Player identity and gold are
   * resolved on the server; client supplied prices or item payloads are never
   * accepted. Atomic service mutations are enabled incrementally after OPEN.
   */
  private void NpcServiceRequest(Packet packet) {
    NpcServiceRequest request = (NpcServiceRequest) packet.data.data(new NpcServiceRequest());
    NpcServiceRequestCache.Intent requestIntent = NpcServiceRequestCache.intent(
        request.npcEntityId(), request.serviceType(), request.operation(),
        request.itemId(), request.itemIndex(), request.stockRevision());
    NpcServiceRequestCache.Entry completed = npcRequestCache.lookup(packet.id, request.requestId());
    if (completed != null) {
      if (completed.matches(requestIntent)) {
        outPackets.offer(Packet.obtain(1 << packet.id, ByteBuffer.wrap(completed.response())));
        Gdx.app.log(TAG, "[NPC_SERVICE] phase=replay client=" + packet.id
            + " request=" + request.requestId());
      } else {
        int cachedPlayerId = player.get(packet.id, Engine.INVALID_ENTITY);
        com.riiablo.engine.server.component.Player cachedPlayer = cachedPlayerId == Engine.INVALID_ENTITY
            ? null : world.getMapper(com.riiablo.engine.server.component.Player.class).get(cachedPlayerId);
        sendNpcServiceResult(packet.id, request.requestId(), false, "REQUEST_ID_REUSED",
            wallet(cachedPlayer, com.riiablo.attributes.Stat.gold),
            wallet(cachedPlayer, com.riiablo.attributes.Stat.goldbank),
            null, 0, null, cachedPlayer == null ? null : cachedPlayer.data,
            requestIntent, false);
      }
      return;
    }
    int playerId = player.get(packet.id, Engine.INVALID_ENTITY);
    int npcId = request.npcEntityId();
    com.riiablo.engine.server.component.Position playerPosition = playerId == Engine.INVALID_ENTITY
        ? null : world.getMapper(com.riiablo.engine.server.component.Position.class).get(playerId);
    com.riiablo.engine.server.component.Position npcPosition =
        world.getMapper(com.riiablo.engine.server.component.Position.class).get(npcId);
    com.riiablo.engine.server.component.Monster npc =
        world.getMapper(com.riiablo.engine.server.component.Monster.class).get(npcId);

    com.riiablo.engine.server.npc.NpcServiceProtocol.Service service = decodeNpcService(request.serviceType());
    com.riiablo.engine.server.npc.NpcServiceProtocol.Operation operation = decodeNpcOperation(request.operation());
    com.riiablo.engine.server.npc.NpcDialogManager.NpcDefinition definition = npc == null || npc.monstats == null
        ? null : new com.riiablo.engine.server.npc.NpcDialogManager().getNpcDefinition(npc.monstats.hcIdx);
    boolean serviceAvailable = definition != null && service != null && operation != null
        && npcOffers(definition, service)
        && com.riiablo.engine.server.npc.NpcServiceProtocol.supports(service, operation);
    boolean inRange = playerPosition != null && npcPosition != null
        && com.riiablo.engine.server.npc.NpcServiceProtocol.inRange(playerPosition.position, npcPosition.position);
    String reason = com.riiablo.engine.server.npc.NpcServiceProtocol.rejectReason(
        playerId != Engine.INVALID_ENTITY, definition != null, inRange, serviceAvailable, true);
    com.riiablo.engine.server.component.Player playerComponent = playerId == Engine.INVALID_ENTITY
        ? null : world.getMapper(com.riiablo.engine.server.component.Player.class).get(playerId);
    com.riiablo.engine.server.npc.NpcVendorSessionManager.Session session = null;
    int resultItemId = 0;
    byte[] resultItemData = null;
    if (reason == null && (service == com.riiablo.engine.server.npc.NpcServiceProtocol.Service.TRADE
        || service == com.riiablo.engine.server.npc.NpcServiceProtocol.Service.GAMBLE)) {
      try {
        session = npcVendors.open(npcId, npc.monstats.Id,
            world.getSystem(VendorGenerator.class),
            service == com.riiablo.engine.server.npc.NpcServiceProtocol.Service.GAMBLE,
            Riiablo.files.Npc.get(npc.monstats.Id), diff, playerId,
            operation == com.riiablo.engine.server.npc.NpcServiceProtocol.Operation.OPEN);
      } catch (Throwable t) {
        reason = "STOCK_GENERATION_FAILED";
        Gdx.app.error(TAG, "[NPC_SERVICE] stock generation failed for npc=" + npcId, t);
      }
      if (reason == null && session.revision != request.stockRevision()
          && operation != com.riiablo.engine.server.npc.NpcServiceProtocol.Operation.OPEN) {
        reason = "STALE_STOCK";
      }
      if (reason == null && operation == com.riiablo.engine.server.npc.NpcServiceProtocol.Operation.BUY) {
        if (playerComponent == null || playerComponent.data == null) reason = "PLAYER_NOT_FOUND";
        else {
          com.riiablo.item.Item item = npcVendors.find(session, request.itemId());
          if (item == null) reason = "UNKNOWN_STOCK_ITEM";
          else {
            resultItemId = item.id;
            int price = npcVendors.buy(session, playerComponent.data, request.itemId());
            if (price <= 0) reason = "BUY_REJECTED";
            else resultItemData = serializeItem(item);
          }
        }
      } else if (reason == null && operation == com.riiablo.engine.server.npc.NpcServiceProtocol.Operation.SELL) {
        if (playerComponent == null || playerComponent.data == null) reason = "PLAYER_NOT_FOUND";
        else if (npcVendors.sell(session, playerComponent.data, request.itemIndex()) <= 0) reason = "SELL_REJECTED";
      } else if (reason == null && operation != com.riiablo.engine.server.npc.NpcServiceProtocol.Operation.OPEN) {
        reason = "OPERATION_NOT_IMPLEMENTED";
      }
    } else if (reason == null
        && service == com.riiablo.engine.server.npc.NpcServiceProtocol.Service.REPAIR) {
      if (playerComponent == null || playerComponent.data == null) {
        reason = "PLAYER_NOT_FOUND";
      } else if (operation == com.riiablo.engine.server.npc.NpcServiceProtocol.Operation.OPEN) {
        // The combined trade/repair panel already owns its trade stock. OPEN
        // only establishes that the repair service is valid for this NPC.
      } else {
        NpcRepairService.Result result;
        com.riiablo.codec.excel.Npc.Entry pricing = Riiablo.files.Npc.get(npc.monstats.Id);
        if (operation == com.riiablo.engine.server.npc.NpcServiceProtocol.Operation.REPAIR_ITEM) {
          result = NpcRepairService.repairItem(
              playerComponent.data, pricing, request.itemIndex(), request.itemId());
        } else if (operation == com.riiablo.engine.server.npc.NpcServiceProtocol.Operation.REPAIR_ALL) {
          result = NpcRepairService.repairAll(playerComponent.data, pricing);
        } else {
          result = null;
        }
        if (result == null) reason = "OPERATION_NOT_IMPLEMENTED";
        else {
          reason = result.reason;
          resultItemId = result.itemId;
        }
      }
    } else if (reason == null) {
      reason = "OPERATION_NOT_IMPLEMENTED";
    }
    boolean success = reason == null;
    int gold = wallet(playerComponent, com.riiablo.attributes.Stat.gold);
    int goldBank = wallet(playerComponent, com.riiablo.attributes.Stat.goldbank);
    sendNpcServiceResult(packet.id, request.requestId(), success, reason, gold, goldBank,
        session, resultItemId, resultItemData, playerComponent == null ? null : playerComponent.data,
        requestIntent, true);
    Gdx.app.log(TAG, "[NPC_SERVICE] player=" + playerId + " npc=" + npcId
        + " service=" + service + " operation=" + operation + " result="
        + (success ? "OK" : reason));
  }

  private void sendNpcServiceResult(int clientId, long requestId, boolean success, String reason,
                                     int gold, int goldBank, NpcVendorSessionManager.Session session,
                                     int itemId, byte[] itemData, CharData character,
                                     NpcServiceRequestCache.Intent requestIntent, boolean cacheResponse) {
    FlatBufferBuilder builder = new FlatBufferBuilder(4096);
    int reasonOffset = builder.createString(reason == null ? "" : reason);
    int itemDataOffset = itemData == null ? 0 : NpcServiceResult.createItemDataVector(builder, itemData);
    int stockOffset = 0;
    if (session != null) {
      int[] stockEntries = new int[session.stock.size];
      for (int i = 0; i < session.stock.size; i++) {
        com.riiablo.item.Item item = session.stock.get(i);
        byte[] data = serializeItem(item);
        int dataOffset = NpcServiceStock.createItemDataVector(builder, data);
        stockEntries[i] = NpcServiceStock.createNpcServiceStock(builder, item.id, dataOffset,
            npcVendors.price(session, item, character));
      }
      stockOffset = NpcServiceResult.createStockVector(builder, stockEntries);
    }
    int resultOffset = NpcServiceResult.createNpcServiceResult(
        builder, requestId, success, reasonOffset, Math.max(0, gold),
        session == null ? 0 : session.revision, itemId, itemDataOffset, stockOffset,
        Math.max(0, goldBank));
    int root = com.riiablo.net.packet.d2gs.D2GS.createD2GS(
        builder, D2GSData.NpcServiceResult, resultOffset);
    com.riiablo.net.packet.d2gs.D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    ByteBuffer response = builder.dataBuffer();
    byte[] bytes = new byte[response.remaining()];
    response.duplicate().get(bytes);
    if (cacheResponse) npcRequestCache.put(clientId, requestId, requestIntent, bytes);
    outPackets.offer(Packet.obtain(1 << clientId, ByteBuffer.wrap(bytes)));
  }

  private static int wallet(com.riiablo.engine.server.component.Player player, short stat) {
    if (player == null || player.data == null || player.data.getStats() == null
        || player.data.getStats().get(stat) == null) return 0;
    return Math.max(0, player.data.getStats().get(stat).asInt());
  }

  private static byte[] serializeItem(com.riiablo.item.Item item) {
    if (item == null) return new byte[0];
    io.netty.buffer.ByteBuf buffer = Unpooled.buffer(512);
    try {
      ByteOutput out = ByteOutput.wrap(buffer);
      new ItemWriter().writeItem(item, out);
      byte[] data = new byte[out.bytesWritten()];
      buffer.getBytes(0, data);
      return data;
    } catch (Throwable t) {
      Gdx.app.error(TAG, "Failed to serialize NPC stock item " + item.id, t);
      return new byte[0];
    } finally {
      buffer.release();
    }
  }

  private static com.riiablo.engine.server.npc.NpcServiceProtocol.Service decodeNpcService(byte value) {
    com.riiablo.engine.server.npc.NpcServiceProtocol.Service[] values =
        com.riiablo.engine.server.npc.NpcServiceProtocol.Service.values();
    return value >= 0 && value < values.length ? values[value] : null;
  }

  private static com.riiablo.engine.server.npc.NpcServiceProtocol.Operation decodeNpcOperation(byte value) {
    com.riiablo.engine.server.npc.NpcServiceProtocol.Operation[] values =
        com.riiablo.engine.server.npc.NpcServiceProtocol.Operation.values();
    return value >= 0 && value < values.length ? values[value] : null;
  }

  private static boolean npcOffers(com.riiablo.engine.server.npc.NpcDialogManager.NpcDefinition npc,
                                   com.riiablo.engine.server.npc.NpcServiceProtocol.Service service) {
    switch (service) {
      case TRADE: return npc.isVendor;
      case GAMBLE: return npc.canGamble;
      case REPAIR: return npc.canRepair;
      case HIRE:
      case RESURRECT: return npc.canHire;
      default: return false;
    }
  }

  private int getPlayerEntityId(Packet packet) {
    int entityId = player.get(packet.id, Engine.INVALID_ENTITY);
    assert entityId != Engine.INVALID_ENTITY;
    return entityId;
  }

  private void GroundToCursor(Packet packet) {
    int entityId = getPlayerEntityId(packet);
    GroundToCursor groundToCursor = (GroundToCursor) packet.data.data(new GroundToCursor());
    itemManager.groundToCursor(entityId, groundToCursor.itemId());

    packet.id = (1 << packet.id);
    outPackets.offer(packet);
  }

  private void CursorToGround(Packet packet) {
    int entityId = getPlayerEntityId(packet);
    CursorToGround cursorToGround = (CursorToGround) packet.data.data(new CursorToGround());
    itemManager.cursorToGround(entityId);

    packet.id = (1 << packet.id);
    outPackets.offer(packet);
  }

  private void StoreToCursor(Packet packet) {
    int entityId = getPlayerEntityId(packet);
    StoreToCursor storeToCursor = (StoreToCursor) packet.data.data(new StoreToCursor());
    itemManager.storeToCursor(entityId, storeToCursor.itemId());

    packet.id = (1 << packet.id);
    outPackets.offer(packet);
  }

  private void CursorToStore(Packet packet) {
    int entityId = getPlayerEntityId(packet);
    CursorToStore cursorToStore = (CursorToStore) packet.data.data(new CursorToStore());
    itemManager.cursorToStore(entityId, cursorToStore.storeLoc(), cursorToStore.x(), cursorToStore.y());

    packet.id = (1 << packet.id);
    outPackets.offer(packet);
  }

  private void SwapStoreItem(Packet packet) {
    int entityId = getPlayerEntityId(packet);
    SwapStoreItem swapStoreItem = (SwapStoreItem) packet.data.data(new SwapStoreItem());
    itemManager.swapStoreItem(entityId, swapStoreItem.itemId(), swapStoreItem.storeLoc(), swapStoreItem.x(), swapStoreItem.y());

    packet.id = (1 << packet.id);
    outPackets.offer(packet);
  }

  private void BodyToCursor(Packet packet) {
    int entityId = getPlayerEntityId(packet);
    BodyToCursor bodyToCursor = (BodyToCursor) packet.data.data(new BodyToCursor());
    itemManager.bodyToCursor(entityId, bodyToCursor.bodyLoc(), bodyToCursor.merc());

    packet.id = (1 << packet.id);
    outPackets.offer(packet);
  }

  private void CursorToBody(Packet packet) {
    int entityId = getPlayerEntityId(packet);
    CursorToBody cursorToBody = (CursorToBody) packet.data.data(new CursorToBody());
    itemManager.cursorToBody(entityId, cursorToBody.bodyLoc(), cursorToBody.merc());

    packet.id = (1 << packet.id);
    outPackets.offer(packet);
  }

  private void SwapBodyItem(Packet packet) {
    int entityId = getPlayerEntityId(packet);
    SwapBodyItem swapBodyItem = (SwapBodyItem) packet.data.data(new SwapBodyItem());
    itemManager.swapBodyItem(entityId, swapBodyItem.bodyLoc(), swapBodyItem.merc());

    packet.id = (1 << packet.id);
    outPackets.offer(packet);
  }

  private void BeltToCursor(Packet packet) {
    int entityId = getPlayerEntityId(packet);
    BeltToCursor beltToCursor = (BeltToCursor) packet.data.data(new BeltToCursor());
    itemManager.beltToCursor(entityId, beltToCursor.itemId());

    packet.id = (1 << packet.id);
    outPackets.offer(packet);
  }

  private void CursorToBelt(Packet packet) {
    int entityId = getPlayerEntityId(packet);
    CursorToBelt cursorToBelt = (CursorToBelt) packet.data.data(new CursorToBelt());
    itemManager.cursorToBelt(entityId, cursorToBelt.x(), cursorToBelt.y());

    packet.id = (1 << packet.id);
    outPackets.offer(packet);
  }

  private void SwapBeltItem(Packet packet) {
    int entityId = getPlayerEntityId(packet);
    SwapBeltItem swapBeltItem = (SwapBeltItem) packet.data.data(new SwapBeltItem());
    itemManager.swapBeltItem(entityId, swapBeltItem.itemId());

    packet.id = (1 << packet.id);
    outPackets.offer(packet);
  }

  /** Handles the unified server-authoritative item protocol. */
  private void ItemMoveRequest(Packet packet) {
    ItemMoveRequest request = (ItemMoveRequest) packet.data.data(new ItemMoveRequest());
    byte operation = request.operation();
    ItemMoveIntent intent = new ItemMoveIntent(request.requestId(), request.revision(), operation,
        request.itemId(), request.groundEntityId(), request.storeLoc(), request.x(), request.y(),
        request.bodyLoc(), request.merc());
    ItemMoveRequestCache.Entry cached = itemMoveRequestCache.lookup(packet.id, request.requestId());
    Gdx.app.log(TAG, "[ITEM_PICKUP] phase=request client=" + packet.id
        + " request=" + request.requestId() + " op=" + operation
        + " item=" + request.itemId() + " ground=" + request.groundEntityId()
        + " revision=" + request.revision());
    if (cached != null) {
      if (cached.matches(intent)) {
        outPackets.offer(Packet.obtain(1 << packet.id, ByteBuffer.wrap(cached.response)));
      } else {
        sendItemMoveResult(packet.id, intent, false, ItemMoveFailure.REQUEST_ID_REUSED,
            authoritativeItems.revision(player.get(packet.id, Engine.INVALID_ENTITY)), false, false);
      }
      return;
    }

    int playerEntityId = player.get(packet.id, Engine.INVALID_ENTITY);
    Player playerComponent = playerEntityId == Engine.INVALID_ENTITY ? null
        : world.getMapper(Player.class).get(playerEntityId);
    CharData character = playerComponent == null ? null : playerComponent.data;
    AuthoritativeItemMoveService.Outcome outcome;
    int groundEntity = request.groundEntityId();
    if (operation == ItemMoveOperation.GROUND_TO_CURSOR) {
      com.riiablo.engine.server.component.Item ground = groundEntity < 0 ? null : mItemSafe(groundEntity);
      com.riiablo.item.Item groundItem = ground == null ? null : ground.item;
      com.riiablo.engine.server.component.Position groundPosition = groundEntity < 0 ? null
          : world.getMapper(com.riiablo.engine.server.component.Position.class).get(groundEntity);
      com.riiablo.engine.server.component.Position playerPosition = playerEntityId == Engine.INVALID_ENTITY
          ? null : world.getMapper(com.riiablo.engine.server.component.Position.class).get(playerEntityId);
      if (groundItem != null && (groundPosition == null || playerPosition == null
          || playerPosition.position.dst2(groundPosition.position) > 100f)) {
        Gdx.app.log(TAG, "[ITEM_PICKUP] phase=distance_check client=" + packet.id
            + " ground=" + groundEntity + " distance="
            + (groundPosition != null && playerPosition != null
                ? playerPosition.position.dst(groundPosition.position) : Float.NaN)
            + " result=too_far");
        outcome = new AuthoritativeItemMoveService.Outcome(false, ItemMoveFailure.GROUND_ITEM_TOO_FAR,
            authoritativeItems.revision(playerEntityId));
      } else {
        Gdx.app.log(TAG, "[ITEM_PICKUP] phase=resolve_ground client=" + packet.id
            + " player=" + playerEntityId + " ground=" + groundEntity
            + " itemCode=" + (groundItem != null ? groundItem.code : "null"));
        outcome = authoritativeItems.pickup(playerEntityId, character, intent, groundItem);
      }
      if (outcome.success && outcome.consumeGroundEntity) world.delete(groundEntity);
    } else if (operation == ItemMoveOperation.CURSOR_TO_GROUND) {
      com.riiablo.engine.server.component.Position position = playerEntityId == Engine.INVALID_ENTITY
          ? null : world.getMapper(com.riiablo.engine.server.component.Position.class).get(playerEntityId);
      outcome = authoritativeItems.drop(playerEntityId, character, intent, item -> {
        if (position != null) {
          int droppedEntity = factory.createItem(item, position.position.x, position.position.y);
          if (droppedEntity >= 0) {
            com.riiablo.engine.server.item.GroundDropOwnership.register(droppedEntity, playerEntityId, 10_000L);
          }
        }
      });
    } else {
      outcome = authoritativeItems.apply(playerEntityId, character, intent);
    }
    Gdx.app.log(TAG, "[ITEM_PICKUP] phase=result client=" + packet.id
        + " success=" + outcome.success + " failure=" + outcome.failure
        + " revision=" + outcome.revision + " consumeGround=" + outcome.consumeGroundEntity);
    sendItemMoveResult(packet.id, intent, outcome.success, outcome.failure, outcome.revision, true,
        operation == ItemMoveOperation.GROUND_TO_CURSOR && !outcome.consumeGroundEntity);
  }

  private com.riiablo.engine.server.component.Item mItemSafe(int entityId) {
    try {
      return world.getMapper(com.riiablo.engine.server.component.Item.class).get(entityId);
    } catch (Throwable ignored) {
      return null;
    }
  }

  private void sendItemMoveResult(int clientId, ItemMoveIntent intent, boolean success,
                                  byte failure, long revision, boolean cacheResponse,
                                  boolean includeGroundCorrection) {
    FlatBufferBuilder builder = new FlatBufferBuilder(8192);
    int[] entries = new int[0];
    int playerEntityId = player.get(clientId, Engine.INVALID_ENTITY);
    Player playerComponent = playerEntityId == Engine.INVALID_ENTITY ? null
        : world.getMapper(Player.class).get(playerEntityId);
    if (playerComponent != null && playerComponent.data != null) {
      com.riiablo.save.ItemData data = playerComponent.data.getItems();
      entries = new int[data.getItems().size];
      int entryCount = 0;
      for (int i = 0; i < data.getItems().size; i++) {
        com.riiablo.item.Item item = data.getItems().get(i);
        if (item == null) continue;
        int itemData = serializeItemVector(builder, item);
        entries[entryCount++] = ItemMoveSnapshotEntry.createItemMoveSnapshotEntry(builder,
            item.id, itemData, item.location == null ? -1 : item.location.ordinal(),
            item.storeLoc == null ? -1 : item.storeLoc.ordinal(),
            item.bodyLoc == null ? -1 : item.bodyLoc.ordinal(), item.gridX, item.gridY, false);
      }
      if (entryCount != entries.length) entries = java.util.Arrays.copyOf(entries, entryCount);
    }
    int snapshot = ItemMoveResult.createSnapshotVector(builder, entries);
    int groundEntityId = -1;
    int groundItemData = 0;
    float groundX = 0f;
    float groundY = 0f;
    if (includeGroundCorrection && intent.groundEntityId >= 0) {
      com.riiablo.engine.server.component.Item ground = mItemSafe(intent.groundEntityId);
      com.riiablo.engine.server.component.Position position =
          world.getMapper(com.riiablo.engine.server.component.Position.class).get(intent.groundEntityId);
      if (ground != null && ground.item != null && position != null) {
        groundEntityId = intent.groundEntityId;
        groundItemData = serializeItemVector(builder, ground.item);
        groundX = position.position.x;
        groundY = position.position.y;
      }
    }
    int result = ItemMoveResult.createItemMoveResult(builder, intent.requestId, success, failure,
        revision, intent.operation, snapshot, groundEntityId, groundItemData, groundX, groundY);
    int root = com.riiablo.net.packet.d2gs.D2GS.createD2GS(builder, D2GSData.ItemMoveResult, result);
    com.riiablo.net.packet.d2gs.D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    ByteBuffer response = builder.dataBuffer();
    byte[] bytes = new byte[response.remaining()];
    response.duplicate().get(bytes);
    if (cacheResponse) itemMoveRequestCache.put(clientId, intent, bytes);
    outPackets.offer(Packet.obtain(1 << clientId, ByteBuffer.wrap(bytes)));
  }

  private static int serializeItemVector(FlatBufferBuilder builder, com.riiablo.item.Item item) {
    return builder.createByteVector(serializeItem(item));
  }

  static String generateClientName() {
    return String.format("Client-%08X", MathUtils.random(1, Integer.MAX_VALUE - 1));
  }

  private class Client extends Thread {
    final String TAG;

    int id;
    Socket socket;
    ByteBuffer buffer = ByteBuffer.allocate(8192);
    volatile boolean kill = false;

    Client(int id, Socket socket) {
      super(clientThreads, generateClientName());
      TAG = D2GS.TAG + "{" + id + "}";
      this.id = id;
      this.socket = socket;
    }

    public void send(Packet packet) throws IOException {
      if (!socket.isConnected()) return;
      WritableByteChannel out = Channels.newChannel(socket.getOutputStream());
      packet.buffer.mark();
      out.write(packet.buffer);
      packet.buffer.reset();
      if ((connected & (1 << id)) == 0 && packet.data.dataType() == D2GSData.Connection) {
        connected |= (1 << id);
      }
    }

    @Override
    public void run() {
      while (!kill) {
        try {
          buffer.clear();
          buffer.mark();
          ReadableByteChannel in = Channels.newChannel(socket.getInputStream());
          if (in.read(buffer) == -1) {
            kill = true;
            break;
          }
          buffer.limit(buffer.position());
          buffer.reset();

          ByteBuffer copy = (ByteBuffer) ByteBuffer.wrap(new byte[buffer.limit()]).put(buffer).rewind();
          Packet packet = Packet.obtain(id, copy);
          if (DEBUG_RECEIVED_PACKETS && !ignoredPackets.get(packet.data.dataType())) Gdx.app.log(TAG, "received " + D2GSData.name(packet.data.dataType()) + " packet from " + socket.getRemoteAddress());
          boolean success = packets.offer(packet, 5, TimeUnit.MILLISECONDS);
          if (!success) {
            Gdx.app.log(TAG, "failed to add to queue -- closing " + socket.getRemoteAddress());
            kill = true;
          } else if (packet.data.dataType() == D2GSData.Ping) {
            try {
              Ping ping = (Ping) packet.data.data(new Ping());
              FlatBufferBuilder builder = new FlatBufferBuilder(0);
              int dataOffset = Ping.createPing(builder, ping.tickCount(), ping.sendTime(), 0, true);
              int root = com.riiablo.net.packet.d2gs.D2GS.createD2GS(builder, D2GSData.Ping, dataOffset);
              com.riiablo.net.packet.d2gs.D2GS.finishSizePrefixedD2GSBuffer(builder, root);
              Packet response = Packet.obtain(1 << packet.id, builder.dataBuffer());
              if (DEBUG_SENT_PACKETS && !ignoredPackets.get(packet.data.dataType())) Gdx.app.log(TAG, "dispatching " + D2GSData.name(packet.data.dataType()) + " ACK packet to " + String.format("0x%08X", packet.id));
              send(response);
            } catch (Throwable t) {
              Gdx.app.log(TAG, t.getMessage(), t);
            }
          }
        } catch (Throwable t) {
          Gdx.app.log(TAG, t.getMessage(), t);
          kill = true;
        }
      }

      Gdx.app.log(TAG, "closing socket to " + socket.getRemoteAddress());
      if (socket != null) socket.dispose();
      Disconnect(id);
    }
  }
}
