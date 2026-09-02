package com.riiablo.server.d2gs;

import com.google.flatbuffers.FlatBufferBuilder;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
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

import com.artemis.Aspect;
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
import com.riiablo.engine.server.RoomActivationSystem;
import com.riiablo.engine.server.RoomEntityTrackingSystem;
import com.riiablo.engine.server.Actioneer;
import com.riiablo.engine.server.AuraEcsSystem;
import com.riiablo.engine.server.AnimStepper;
import com.riiablo.engine.server.MissileCollisionSystem;
import com.riiablo.engine.server.ServerSkillSystem;
import com.riiablo.engine.server.ServerPlayerDeathSystem;
import com.riiablo.engine.server.PlayerCorpseRetrievalSystem;
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
import com.riiablo.engine.server.ObjectCollisionUpdater;
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
import com.riiablo.engine.server.quest.QuestRequestCache;
import com.riiablo.engine.server.quest.QuestSnapshot;
import com.riiablo.engine.server.npc.NpcVendorSessionManager;
import com.riiablo.engine.server.npc.NpcServiceRequestCache;
import com.riiablo.engine.server.npc.NpcRepairService;
import com.riiablo.engine.server.party.Party;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.engine.server.party.PartyMember;
import com.riiablo.engine.server.party.PartyRelation;
import com.riiablo.engine.server.party.PartyRequestCache;
import com.riiablo.engine.server.party.PartyServiceProtocol;
import com.riiablo.engine.server.party.PartyGoldShareService;
import com.riiablo.engine.server.party.PvpCombatRules;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.VendorGenerator;
import com.riiablo.item.ItemWriter;
import com.riiablo.io.ByteOutput;
import io.netty.buffer.Unpooled;
import com.riiablo.map.Act1MapBuilder;
import com.riiablo.map.Act1MapBuilderD2MOD;
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
import com.riiablo.net.packet.d2gs.PartyOperation;
import com.riiablo.net.packet.d2gs.PartyRequest;
import com.riiablo.net.packet.d2gs.PartyResult;
import com.riiablo.net.packet.d2gs.PartyMemberSnapshot;
import com.riiablo.net.packet.d2gs.PlayerLifecycleOperation;
import com.riiablo.net.packet.d2gs.PlayerLifecycleRequest;
import com.riiablo.net.packet.d2gs.PlayerLifecycleResult;
import com.riiablo.net.packet.d2gs.QuestOperation;
import com.riiablo.net.packet.d2gs.QuestRequest;
import com.riiablo.net.packet.d2gs.QuestResult;
import com.riiablo.net.SizePrefixedPacketAccumulator;
import com.riiablo.save.CharData;
import com.riiablo.util.DebugUtils;

public class D2GS extends ApplicationAdapter {
  /** Embedded headless verification hook; normal dedicated-server clients never use it. */
  static volatile D2GS activeHeadlessInstance;

  static Vector2 headlessLevelPosition(int levelId) {
    D2GS server = activeHeadlessInstance;
    if (server == null || server.map == null || Riiablo.files == null || Gdx.app == null) {
      return null;
    }
    java.util.concurrent.CountDownLatch completed = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.atomic.AtomicReference<Vector2> result =
        new java.util.concurrent.atomic.AtomicReference<>();
    Gdx.app.postRunnable(() -> {
      try {
        result.set(findHeadlessLevelPosition(server, levelId));
      } finally {
        completed.countDown();
      }
    });
    try {
      return completed.await(5, java.util.concurrent.TimeUnit.SECONDS) ? result.get() : null;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    }
  }

  private static Vector2 findHeadlessLevelPosition(D2GS server, int levelId) {
    com.riiablo.codec.excel.Levels.Entry level = Riiablo.files.Levels.get(levelId);
    Map.Zone zone = level == null ? null : server.map.findZone(level);
    if (zone == null) return null;
    int centerX = zone.x() + zone.width() / 2;
    int centerY = zone.y() + zone.height() / 2;
    for (int radius = 0; radius <= 64; radius++) {
      for (int dy = -radius; dy <= radius; dy++) {
        for (int dx = -radius; dx <= radius; dx++) {
          if (Math.max(Math.abs(dx), Math.abs(dy)) != radius) continue;
          int x = centerX + dx;
          int y = centerY + dy;
          if (server.map.getZone(x, y) == zone
              && (server.map.flags(x, y) & DT1.Tile.FLAG_BLOCK_WALK) == 0) {
            return new Vector2(x, y);
          }
        }
      }
    }
    return new Vector2(centerX, centerY);
  }

  /** Finds a walkable same-room point in the native hireling follow band. */
  static Vector2 headlessMercenaryFollowPosition(int levelId, float originX, float originY) {
    D2GS server = activeHeadlessInstance;
    if (server == null || server.map == null || Riiablo.files == null || Gdx.app == null) {
      return null;
    }
    java.util.concurrent.CountDownLatch completed = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.atomic.AtomicReference<Vector2> result =
        new java.util.concurrent.atomic.AtomicReference<>();
    Gdx.app.postRunnable(() -> {
      try {
        result.set(findHeadlessMercenaryFollowPosition(
            server, levelId, originX, originY));
      } finally {
        completed.countDown();
      }
    });
    try {
      return completed.await(5, java.util.concurrent.TimeUnit.SECONDS) ? result.get() : null;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    }
  }

  private static Vector2 findHeadlessMercenaryFollowPosition(
      D2GS server, int levelId, float originX, float originY) {
    com.riiablo.codec.excel.Levels.Entry level = Riiablo.files.Levels.get(levelId);
    Map.Zone zone = level == null ? null : server.map.findZone(level);
    Map.RoomEx originRoom = zone == null ? null : zone.findRoomEx(originX, originY);
    if (zone == null) return null;
    Vector2 best = null;
    float bestDistance2 = Float.MAX_VALUE;
    int minX = originRoom != null ? originRoom.x : zone.x();
    int minY = originRoom != null ? originRoom.y : zone.y();
    int maxX = originRoom != null ? originRoom.x + originRoom.width : zone.x() + zone.width();
    int maxY = originRoom != null ? originRoom.y + originRoom.height : zone.y() + zone.height();
    for (int y = minY; y < maxY; y++) {
      for (int x = minX; x < maxX; x++) {
        float dx = x - originX;
        float dy = y - originY;
        float distance2 = dx * dx + dy * dy;
        if (distance2 < 30f * 30f || distance2 > 60f * 60f) continue;
        if (server.map.getZone(x, y) != zone
            || (server.map.flags(x, y) & DT1.Tile.FLAG_BLOCK_WALK) != 0) continue;
        if (originRoom != null && zone.findRoomEx(x, y) != originRoom) continue;
        if (distance2 < bestDistance2) {
          bestDistance2 = distance2;
          if (best == null) best = new Vector2();
          best.set(x, y);
        }
      }
    }
    return best;
  }

  /** Finds a native Blood Moor room whose deferred population contains a raisable pair. */
  static Vector2 headlessFallenShamanPosition(int levelId) {
    D2GS server = activeHeadlessInstance;
    if (server == null || server.map == null || Riiablo.files == null
        || Riiablo.files.monstats == null) return null;
    com.riiablo.codec.excel.Levels.Entry level = Riiablo.files.Levels.get(levelId);
    Map.Zone zone = level == null ? null : server.map.findZone(level);
    if (zone == null) return null;
    for (Map.RoomEx room : zone.getRoomsEx()) {
      for (Map.MonsterSpawn shaman : room.getPendingMonsterSpawns()) {
        com.riiablo.codec.excel.MonStats.Entry shamanRow =
            Riiablo.files.monstats.get(shaman.monsterId);
        if (shamanRow == null || !"fallenshaman1".equalsIgnoreCase(shamanRow.Id)) continue;
        for (Map.MonsterSpawn fallen : room.getPendingMonsterSpawns()) {
          com.riiablo.codec.excel.MonStats.Entry fallenRow =
              Riiablo.files.monstats.get(fallen.monsterId);
          if (fallenRow == null || !"fallen1".equalsIgnoreCase(fallenRow.Id)) continue;
          float dx = fallen.x - shaman.x;
          float dy = fallen.y - shaman.y;
          if (dx * dx + dy * dy > 14f * 14f) continue;
          for (int radius = 1; radius <= 4; radius++) {
            for (int oy = -radius; oy <= radius; oy++) {
              for (int ox = -radius; ox <= radius; ox++) {
                int x = Math.round(fallen.x) + ox;
                int y = Math.round(fallen.y) + oy;
                if (room.contains(x, y)
                    && (server.map.flags(x, y) & DT1.Tile.FLAG_BLOCK_WALK) == 0) {
                  return new Vector2(x, y);
                }
              }
            }
          }
          return new Vector2(fallen.x, fallen.y);
        }
      }
    }
    return null;
  }

  /** Returns a walkable observation point in the same native room as a corpse. */
  static Vector2 headlessRoomObservationPosition(int levelId, float originX, float originY) {
    D2GS server = activeHeadlessInstance;
    if (server == null || server.map == null || Riiablo.files == null) return null;
    com.riiablo.codec.excel.Levels.Entry level = Riiablo.files.Levels.get(levelId);
    Map.Zone zone = level == null ? null : server.map.findZone(level);
    Map.RoomEx room = zone == null ? null : zone.findRoomEx(originX, originY);
    if (room == null) return null;
    Vector2 best = null;
    float bestDistance2 = 0f;
    for (int y = room.y; y < room.y + room.height; y++) {
      for (int x = room.x; x < room.x + room.width; x++) {
        if ((server.map.flags(x, y) & DT1.Tile.FLAG_BLOCK_WALK) != 0) continue;
        float dx = x - originX;
        float dy = y - originY;
        float distance2 = dx * dx + dy * dy;
        if (distance2 >= 8f * 8f && distance2 > bestDistance2) {
          bestDistance2 = distance2;
          if (best == null) best = new Vector2();
          best.set(x, y);
        }
      }
    }
    return best;
  }

  /** Player XP, native reward flags and ground-item count for the Fallen regression. */
  static int[] headlessMonsterRewardState(int playerId, int monsterId) {
    D2GS server = activeHeadlessInstance;
    if (server == null || server.world == null || Gdx.app == null) {
      return new int[] {0, 0, 0};
    }
    java.util.concurrent.CountDownLatch completed = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.atomic.AtomicReference<int[]> result =
        new java.util.concurrent.atomic.AtomicReference<>(new int[] {0, 0, 0});
    Gdx.app.postRunnable(() -> {
      try {
        Player player = server.world.getMapper(Player.class).get(playerId);
        int experience = player == null || player.data == null ? 0
            : statInt(player.data.getStats(), com.riiablo.attributes.Stat.experience);
        com.riiablo.engine.server.component.NativeUnitFlags unitFlags =
            server.world.getMapper(com.riiablo.engine.server.component.NativeUnitFlags.class)
                .get(monsterId);
        int groundItems = server.world.getAspectSubscriptionManager().get(
            Aspect.all(com.riiablo.engine.server.component.Item.class)).getEntities().size();
        result.set(new int[] {
            experience,
            unitFlags == null ? 0 : unitFlags.flags(),
            groundItems
        });
      } finally {
        completed.countDown();
      }
    });
    try {
      return completed.await(5, java.util.concurrent.TimeUnit.SECONDS)
          ? result.get() : new int[] {0, 0, 0};
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new int[] {0, 0, 0};
    }
  }

  /** Test-only hook used by the windowless mercenary integration client. */
  static boolean headlessGrantFreeRogue(int playerId) {
    D2GS server = activeHeadlessInstance;
    if (server == null || server.world == null || Gdx.app == null) return false;
    java.util.concurrent.CountDownLatch completed = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.atomic.AtomicBoolean granted =
        new java.util.concurrent.atomic.AtomicBoolean(false);
    // Artemis worlds are single-threaded. The protocol driver runs on the
    // JavaExec main thread, so entity creation must be marshalled onto the
    // headless application's render thread just like an ordinary packet.
    Gdx.app.postRunnable(() -> {
      try {
        NativeMercenaryRewardSystem rewards =
            server.world.getSystem(NativeMercenaryRewardSystem.class);
        granted.set(rewards != null && rewards.grantFreeRogue(playerId));
      } finally {
        completed.countDown();
      }
    });
    try {
      return completed.await(5, java.util.concurrent.TimeUnit.SECONDS) && granted.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  /** Test-only authoritative death trigger for the hireling lifecycle test. */
  static boolean headlessKillMercenary(int playerId) {
    D2GS server = activeHeadlessInstance;
    if (server == null || server.world == null || Gdx.app == null) return false;
    java.util.concurrent.CountDownLatch completed = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.atomic.AtomicBoolean killed = new java.util.concurrent.atomic.AtomicBoolean(false);
    Gdx.app.postRunnable(() -> {
      try {
        NativeMercenaryRewardSystem rewards =
            server.world.getSystem(NativeMercenaryRewardSystem.class);
        int entityId = rewards == null ? Engine.INVALID_ENTITY
            : rewards.mercenaryEntityId(playerId);
        com.riiablo.engine.server.component.AttributesWrapper wrapper = entityId < 0 ? null
            : server.world.getMapper(com.riiablo.engine.server.component.AttributesWrapper.class)
                .get(entityId);
        com.riiablo.attributes.StatRef life = wrapper == null || wrapper.attrs == null ? null
            : wrapper.attrs.get(com.riiablo.attributes.Stat.hitpoints,
                com.riiablo.attributes.StatRef.obtain());
        if (life != null && life.asFixed() > 0f) {
          life.set(0);
          server.world.getSystem(EventSystem.class).dispatch(
              com.riiablo.engine.server.event.DeathEvent.obtain(playerId, entityId));
          killed.set(true);
        }
      } finally {
        completed.countDown();
      }
    });
    try {
      return completed.await(5, java.util.concurrent.TimeUnit.SECONDS) && killed.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  /** Test-only NPC-equivalent paid resurrection on the render thread. */
  static boolean headlessResurrectMercenary(int playerId) {
    D2GS server = activeHeadlessInstance;
    if (server == null || server.world == null || Gdx.app == null) return false;
    java.util.concurrent.CountDownLatch completed = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.atomic.AtomicBoolean resurrected =
        new java.util.concurrent.atomic.AtomicBoolean(false);
    Gdx.app.postRunnable(() -> {
      try {
        NativeMercenaryRewardSystem rewards =
            server.world.getSystem(NativeMercenaryRewardSystem.class);
        resurrected.set(rewards != null && rewards.resurrectMercenary(playerId));
      } finally {
        completed.countDown();
      }
    });
    try {
      return completed.await(5, java.util.concurrent.TimeUnit.SECONDS) && resurrected.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  static int[] headlessMercenaryLifecycleState(int playerId) {
    D2GS server = activeHeadlessInstance;
    if (server == null || server.world == null) {
      return new int[] {Engine.INVALID_ENTITY, 0, 0, 0, 0};
    }
    NativeMercenaryRewardSystem rewards =
        server.world.getSystem(NativeMercenaryRewardSystem.class);
    com.riiablo.engine.server.component.Player player =
        server.world.getMapper(com.riiablo.engine.server.component.Player.class).get(playerId);
    return rewards == null ? new int[] {Engine.INVALID_ENTITY, 0, 0, 0, 0}
        : new int[] {rewards.mercenaryEntityId(playerId), rewards.mercenaryState(playerId),
            rewards.resurrectionCost(playerId),
            player == null || player.data == null ? 0
                : com.riiablo.item.VendorPricing.availableGold(player.data),
            rewards.persistedMercenaryFlags(playerId)};
  }

  /** Places a live target one hit from death and the hireling one XP below level-up. */
  static boolean headlessPrepareMercenaryProgression(int playerId, int targetId) {
    D2GS server = activeHeadlessInstance;
    if (server == null || server.world == null || Gdx.app == null) return false;
    java.util.concurrent.CountDownLatch completed = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.atomic.AtomicBoolean prepared =
        new java.util.concurrent.atomic.AtomicBoolean(false);
    Gdx.app.postRunnable(() -> {
      try {
        NativeMercenaryRewardSystem rewards =
            server.world.getSystem(NativeMercenaryRewardSystem.class);
        int mercenaryId = rewards == null ? Engine.INVALID_ENTITY
            : rewards.mercenaryEntityId(playerId);
        com.riiablo.engine.server.component.Mercenary mercenary = mercenaryId < 0 ? null
            : server.world.getMapper(com.riiablo.engine.server.component.Mercenary.class)
                .get(mercenaryId);
        com.riiablo.engine.server.component.Player player = server.world.getMapper(
            com.riiablo.engine.server.component.Player.class).get(playerId);
        com.riiablo.engine.server.component.AttributesWrapper target = server.world.getMapper(
            com.riiablo.engine.server.component.AttributesWrapper.class).get(targetId);
        com.riiablo.engine.server.component.AttributesWrapper mercenaryAttributes =
            mercenaryId < 0 ? null : server.world.getMapper(
                com.riiablo.engine.server.component.AttributesWrapper.class).get(mercenaryId);
        if (mercenary == null || player == null || player.data == null
            || target == null || target.attrs == null || mercenaryAttributes == null
            || mercenaryAttributes.attrs == null) return;
        int ownerLevel = Math.max(1, player.data.getStats().aggregate()
            .getValue(com.riiablo.attributes.Stat.level, player.data.level & 0xFF));
        if (mercenary.level >= ownerLevel || mercenary.level >= 98) return;
        com.riiablo.engine.server.NativeHirelingExperienceTable table =
            com.riiablo.engine.server.NativeHirelingExperienceTable.load();
        long nextExperience = table.nextThreshold(mercenary.mercType, mercenary.level);
        if (nextExperience <= 0L || nextExperience > Integer.MAX_VALUE) return;
        long seededExperience = nextExperience - 1L;
        com.riiablo.save.CharData.MercData saved = player.data.getMerc();
        saved.xp = seededExperience;
        saved.getStats().base().put(com.riiablo.attributes.Stat.experience,
            (int) seededExperience);
        saved.getStats().base().put(com.riiablo.attributes.Stat.lastexp, 0);
        saved.getStats().aggregate().put(com.riiablo.attributes.Stat.experience,
            (int) seededExperience);
        saved.getStats().aggregate().put(com.riiablo.attributes.Stat.lastexp, 0);
        if (!rewards.synchronizeMercenaryProgress(
            playerId, mercenaryId, seededExperience, mercenary.level)) return;
        com.riiablo.attributes.StatRef life = target.attrs.get(
            com.riiablo.attributes.Stat.hitpoints, com.riiablo.attributes.StatRef.obtain());
        if (life == null || life.asFixed() <= 0f) return;
        life.set(1f);
        prepared.set(true);
      } finally {
        completed.countDown();
      }
    });
    try {
      return completed.await(5, java.util.concurrent.TimeUnit.SECONDS) && prepared.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  /** Stable progression snapshot used by the two-client native kill test. */
  static int[] headlessMercenaryProgressionState(int playerId) {
    D2GS server = activeHeadlessInstance;
    if (server == null || server.world == null || Gdx.app == null) {
      return emptyHeadlessMercenaryProgressionState();
    }
    java.util.concurrent.CountDownLatch completed = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.atomic.AtomicReference<int[]> result =
        new java.util.concurrent.atomic.AtomicReference<>(emptyHeadlessMercenaryProgressionState());
    Gdx.app.postRunnable(() -> {
      try {
        NativeMercenaryRewardSystem rewards =
            server.world.getSystem(NativeMercenaryRewardSystem.class);
        int mercenaryId = rewards == null ? Engine.INVALID_ENTITY
            : rewards.mercenaryEntityId(playerId);
        com.riiablo.engine.server.component.Mercenary mercenary = mercenaryId < 0 ? null
            : server.world.getMapper(com.riiablo.engine.server.component.Mercenary.class)
                .get(mercenaryId);
        com.riiablo.engine.server.component.Player player = server.world.getMapper(
            com.riiablo.engine.server.component.Player.class).get(playerId);
        com.riiablo.engine.server.component.AttributesWrapper wrapper = mercenaryId < 0 ? null
            : server.world.getMapper(com.riiablo.engine.server.component.AttributesWrapper.class)
                .get(mercenaryId);
        com.riiablo.attributes.Attributes attrs = wrapper == null ? null : wrapper.attrs;
        com.riiablo.save.CharData.MercData saved =
            player == null || player.data == null ? null : player.data.getMerc();
        result.set(new int[] {
            mercenaryId,
            mercenary == null ? 0 : mercenary.level,
            rewards == null ? 0 : rewards.mercenaryLevel(playerId),
            saved == null ? 0 : (int) saved.xp,
            rewards == null ? 0 : (int) rewards.mercenaryExperience(playerId),
            statInt(attrs, com.riiablo.attributes.Stat.experience),
            statInt(attrs, com.riiablo.attributes.Stat.level),
            statInt(attrs, com.riiablo.attributes.Stat.strength),
            statInt(attrs, com.riiablo.attributes.Stat.dexterity),
            Float.floatToIntBits(statFixed(attrs, com.riiablo.attributes.Stat.maxhp)),
            statInt(attrs, com.riiablo.attributes.Stat.armorclass),
            statInt(attrs, com.riiablo.attributes.Stat.nextexp),
            mercenary == null ? -1 : mercenary.skills[0],
            mercenary == null ? 0 : mercenary.skillLevels[0],
            saved == null ? 0 : statInt(saved.getStats(), com.riiablo.attributes.Stat.level),
            Float.floatToIntBits(saved == null ? 0f
                : statFixed(saved.getStats(), com.riiablo.attributes.Stat.maxhp)),
            rewards == null ? 0 : rewards.resurrectionCost(playerId),
            saved == null ? 0 : statInt(saved.getStats(), com.riiablo.attributes.Stat.lastexp),
            Float.floatToIntBits(statFixed(attrs, com.riiablo.attributes.Stat.hitpoints))
        });
      } finally {
        completed.countDown();
      }
    });
    try {
      return completed.await(5, java.util.concurrent.TimeUnit.SECONDS)
          ? result.get() : emptyHeadlessMercenaryProgressionState();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return emptyHeadlessMercenaryProgressionState();
    }
  }

  private static int statInt(com.riiablo.attributes.Attributes attrs, short stat) {
    com.riiablo.attributes.StatRef value = attrs == null ? null
        : attrs.get(stat, com.riiablo.attributes.StatRef.obtain());
    return value == null ? 0 : value.asInt();
  }

  private static float statFixed(com.riiablo.attributes.Attributes attrs, short stat) {
    com.riiablo.attributes.StatRef value = attrs == null ? null
        : attrs.get(stat, com.riiablo.attributes.StatRef.obtain());
    return value == null ? 0f : value.asFixed();
  }

  private static int[] emptyHeadlessMercenaryProgressionState() {
    return new int[] {Engine.INVALID_ENTITY, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, -1, 0, 0, 0, 0, 0, 0};
  }

  /**
   * Stable render-thread snapshot for the two-client hireling travel test.
   * Integer slots carry float bit patterns so the hook has no mutable ECS references.
   */
  static int[] headlessMercenaryTravelState(int playerId) {
    D2GS server = activeHeadlessInstance;
    if (server == null || server.world == null || Gdx.app == null) {
      return emptyHeadlessMercenaryTravelState();
    }
    java.util.concurrent.CountDownLatch completed = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.atomic.AtomicReference<int[]> result =
        new java.util.concurrent.atomic.AtomicReference<>(emptyHeadlessMercenaryTravelState());
    Gdx.app.postRunnable(() -> {
      try {
        NativeMercenaryRewardSystem rewards =
            server.world.getSystem(NativeMercenaryRewardSystem.class);
        com.riiablo.engine.server.MercenaryFollowSystem follow = server.world.getSystem(
            com.riiablo.engine.server.MercenaryFollowSystem.class);
        int mercenaryId = rewards == null ? Engine.INVALID_ENTITY
            : rewards.mercenaryEntityId(playerId);
        com.riiablo.engine.server.component.Position playerPosition = server.world.getMapper(
            com.riiablo.engine.server.component.Position.class).get(playerId);
        com.riiablo.engine.server.component.Position mercenaryPosition = mercenaryId < 0 ? null
            : server.world.getMapper(com.riiablo.engine.server.component.Position.class)
                .get(mercenaryId);
        com.riiablo.engine.server.component.MapWrapper playerMap = server.world.getMapper(
            com.riiablo.engine.server.component.MapWrapper.class).get(playerId);
        com.riiablo.engine.server.component.MapWrapper mercenaryMap = mercenaryId < 0 ? null
            : server.world.getMapper(com.riiablo.engine.server.component.MapWrapper.class)
                .get(mercenaryId);
        com.riiablo.engine.server.component.AttributesWrapper attributes = mercenaryId < 0 ? null
            : server.world.getMapper(com.riiablo.engine.server.component.AttributesWrapper.class)
                .get(mercenaryId);
        com.riiablo.attributes.StatRef life = attributes == null || attributes.attrs == null
            ? null : attributes.attrs.get(com.riiablo.attributes.Stat.hitpoints,
                com.riiablo.attributes.StatRef.obtain());
        com.riiablo.engine.server.component.Box2DBody box = mercenaryId < 0 ? null
            : server.world.getMapper(com.riiablo.engine.server.component.Box2DBody.class)
                .get(mercenaryId);
        float mercenaryX = mercenaryPosition == null ? Float.NaN : mercenaryPosition.position.x;
        float mercenaryY = mercenaryPosition == null ? Float.NaN : mercenaryPosition.position.y;
        int flags = mercenaryPosition == null || server.map == null ? -1
            : server.map.flags(mercenaryPosition.position);
        result.set(new int[] {
            mercenaryId,
            levelId(playerMap),
            levelId(mercenaryMap),
            follow == null ? 0 : follow.teleportCount(),
            follow == null ? 0 : follow.followCount(),
            Float.floatToIntBits(playerPosition == null ? Float.NaN : playerPosition.position.x),
            Float.floatToIntBits(playerPosition == null ? Float.NaN : playerPosition.position.y),
            Float.floatToIntBits(mercenaryX),
            Float.floatToIntBits(mercenaryY),
            flags,
            mercenaryMap == null ? -1 : mercenaryMap.roomId,
            Float.floatToIntBits(life == null ? 0f : life.asFixed()),
            Float.floatToIntBits(box == null || box.body == null
                ? Float.NaN : box.body.getPosition().x),
            Float.floatToIntBits(box == null || box.body == null
                ? Float.NaN : box.body.getPosition().y),
            playerMap != null && mercenaryMap != null && playerMap.map == mercenaryMap.map ? 1 : 0,
            follow == null ? Engine.INVALID_ENTITY : follow.lastMercenary(),
            follow == null ? Engine.INVALID_ENTITY : follow.lastOwner(),
            mercenaryMap != null && mercenaryMap.zone != null
                && mercenaryMap.zone.hasNativeRoomTopology() ? 1 : 0
        });
      } finally {
        completed.countDown();
      }
    });
    try {
      return completed.await(5, java.util.concurrent.TimeUnit.SECONDS)
          ? result.get() : emptyHeadlessMercenaryTravelState();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return emptyHeadlessMercenaryTravelState();
    }
  }

  private static int levelId(com.riiablo.engine.server.component.MapWrapper wrapper) {
    return wrapper == null || wrapper.zone == null || wrapper.zone.level == null
        ? -1 : wrapper.zone.level.Id;
  }

  private static int[] emptyHeadlessMercenaryTravelState() {
    return new int[] {Engine.INVALID_ENTITY, -1, -1, 0, 0,
        Float.floatToIntBits(Float.NaN), Float.floatToIntBits(Float.NaN),
        Float.floatToIntBits(Float.NaN), Float.floatToIntBits(Float.NaN),
        -1, -1, 0, Float.floatToIntBits(Float.NaN), Float.floatToIntBits(Float.NaN),
        0, Engine.INVALID_ENTITY, Engine.INVALID_ENTITY, 0};
  }

  static int[] headlessMercenaryCastState() {
    D2GS server = activeHeadlessInstance;
    if (server == null || server.world == null) {
      return emptyHeadlessMercenaryState();
    }
    com.riiablo.engine.server.MercenarySkillSystem skills =
        server.world.getSystem(com.riiablo.engine.server.MercenarySkillSystem.class);
    com.riiablo.engine.server.ServerSkillSystem skillServer =
        server.world.getSystem(com.riiablo.engine.server.ServerSkillSystem.class);
    com.riiablo.engine.server.MissileCollisionSystem collisions =
        server.world.getSystem(com.riiablo.engine.server.MissileCollisionSystem.class);
    return skills == null ? emptyHeadlessMercenaryState()
        : new int[] {skills.castCount(), skills.lastTarget(), skills.processCount(),
            skills.blockStage(), skills.lastSkill(),
            skillServer == null ? 0 : skillServer.mercenaryMissileCount(),
            collisions == null ? 0 : collisions.mercenaryCollisionCount(),
            collisions == null ? 0 : collisions.mercenaryDamageCount(),
            skillServer == null ? 0 : skillServer.mercenarySkillDoCount(),
            skillServer == null ? 0 : skillServer.mercenaryConfiguredMissiles(),
            skillServer == null ? 0 : skillServer.mercenaryLastSrvDoFunc(),
            collisions == null ? Engine.INVALID_ENTITY : collisions.mercenaryLastDamageTarget(),
            Float.floatToIntBits(collisions == null ? 0f : collisions.mercenaryLastDamageBefore()),
            Float.floatToIntBits(collisions == null ? 0f : collisions.mercenaryLastDamageAfter())};
  }

  private static int[] emptyHeadlessMercenaryState() {
    return new int[] {0, Engine.INVALID_ENTITY, 0, 0, Engine.INVALID_ENTITY,
        0, 0, 0, 0, 0, 0, Engine.INVALID_ENTITY, 0, 0};
  }
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
  final BlockingQueue<Packet> outPackets = new ArrayBlockingQueue<>(8192);
  final IntIntMap player = new IntIntMap();
  final long[] nextMovementLogTime = new long[MAX_CLIENTS];

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
  final com.riiablo.engine.server.party.PartyManager partyManager =
      new com.riiablo.engine.server.party.PartyManager();
  final NpcVendorSessionManager npcVendors = new NpcVendorSessionManager();
  final NpcServiceRequestCache npcRequestCache = new NpcServiceRequestCache();
  final AuthoritativeItemMoveService authoritativeItems = new AuthoritativeItemMoveService();
  final ItemMoveRequestCache itemMoveRequestCache = new ItemMoveRequestCache();
  final SkillPointRequestCache skillPointRequestCache = new SkillPointRequestCache();
  final PartyRequestCache partyRequestCache = new PartyRequestCache();
  final QuestRequestCache questRequestCache = new QuestRequestCache();

  protected ComponentMapper<Networked> mNetworked;

  D2GS(FileHandle home, int seed, int diff) {
    this.home = home;
    this.seed = seed;
    this.diff = diff;
  }

  @Override
  public void create() {
    activeHeadlessInstance = this;
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
        .with(new com.riiablo.engine.server.MercenaryFollowSystem())
        .with(new com.riiablo.engine.server.MercenarySkillSystem())
        .with(new ServerMonsterCorpseSystem())
        .with(new AuraEcsSystem())
        .with(new ServerPlayerDeathSystem())
        .with(new PlayerCorpseRetrievalSystem())
        .with(new ServerSkillSystem())
        .with(new DeathRewardSystem())
        .with(new SequenceHandler())
        // Apply a newly queued attack mode before advancing animation. If the
        // old neutral animation wraps first, its Finished event can otherwise
        // consume the fresh attack sequence before the attack keyframe runs.
        .with(new AnimStepper())
        .with(new ObjectCollisionUpdater())
        .with(new MissileCollisionSystem())
        .with(new StateUpdater())
        .with(new ExperienceManager())
        .with(new com.riiablo.engine.server.party.PartyMemberSyncSystem())

        .with(new VendorGenerator())
        .with(new RoomEntityTrackingSystem())
        .with(new RoomActivationSystem())
        .with(new AIStepper())
        .with(new Pathfinder())

        // D2GS is the authoritative owner of player/monster movement modes.
        // Without this, client movement changes position and velocity while
        // the server keeps broadcasting the initial TN/NU mode.
        .with(new com.riiablo.engine.server.VelocityModeChanger(false, true))

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
        .register("partyManager", partyManager)
        ;
    Riiablo.engine = world = new World(config);

    world.inject(map);
    map.setEntityFactory(factory);
    world.inject(Act1MapBuilder.INSTANCE);
    world.inject(Act1MapBuilderD2MOD.INSTANCE);

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
        Client[] shuttingDown;
        synchronized (clients) {
          shuttingDown = clients.clone();
        }
        // Never join while holding the clients monitor: Client.run() enters
        // Disconnect(), which also updates that array during normal shutdown.
        for (Client client : shuttingDown) {
          if (client != null) {
            client.kill = true;
            client.closeSocket();
            try {
              client.join();
            } catch (Throwable ignored) {}
          }
        }
        synchronized (clients) {
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
    if (server != null) server.dispose();
    try {
      if (connectionListener != null) connectionListener.join();
    } catch (Throwable ignored) {}
    if (Riiablo.assets != null) Riiablo.assets.dispose();
    if (activeHeadlessInstance == this) activeHeadlessInstance = null;
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
    byte dataType = packet.data.dataType();
    int authenticatedPlayer = player.get(packet.id, Engine.INVALID_ENTITY);
    if (isLegacyItemRequest(dataType) && isPlayerDead(authenticatedPlayer)) {
      Gdx.app.log(TAG, "[ITEM_MOVE] phase=reject_legacy connection=" + packet.id
          + " player=" + authenticatedPlayer + " reason=player_dead type="
          + D2GSData.name(dataType));
      return;
    }
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
      case D2GSData.PartyRequest:
        PartyRequest(packet);
        break;
      case D2GSData.SwapBeltItem:
        SwapBeltItem(packet);
        break;
      case D2GSData.ItemMoveRequest:
        ItemMoveRequest(packet);
        break;
      case D2GSData.PlayerLifecycleRequest:
        PlayerLifecycleRequest(packet);
        break;
      case D2GSData.QuestRequest:
        QuestRequest(packet);
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

    NativeMercenaryRewardSystem mercenaryRewards =
        world.getSystem(NativeMercenaryRewardSystem.class);
    if (charData.hasMerc() && (mercenaryRewards == null
        || !mercenaryRewards.restorePersistedMercenary(entityId))) {
      Gdx.app.error(TAG, "[MERC_RESTORE] phase=failed player=" + entityId
          + " type=" + (charData.getMerc().type & 0xFFFF)
          + " seed=" + Integer.toUnsignedString(charData.getMerc().seed));
    }

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
    broadcastPartySnapshots(PartyOperation.SNAPSHOT, entityId, -1, -1);
  }

  private void Synchronize(int id, int entityId) {
    sync.syncAllTo(id);
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

  private synchronized void Disconnect(int id) {
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
      partyRequestCache.clear(id);
      questRequestCache.clear(id);
      authoritativeItems.reset(entityId);
      partyManager.removePlayer(entityId);

      NativeMercenaryRewardSystem mercenaryRewards =
          world.getSystem(NativeMercenaryRewardSystem.class);
      if (mercenaryRewards != null) mercenaryRewards.unloadPersistedMercenary(entityId);

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
    broadcastPartySnapshots(PartyOperation.LEAVE, entityId, -1, -1);
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
    if (isPlayerDead(entityId)) {
      Gdx.app.log(TAG, "[NET_MOVE] phase=reject connection=" + packet.id
          + " player=" + entityId + " reason=player_dead");
      return;
    }
    sync.sync(entityId, packet.data);
    long now = TimeUtils.millis();
    if (now >= nextMovementLogTime[packet.id]) {
      nextMovementLogTime[packet.id] = now + 1000L;
      com.riiablo.engine.server.component.Position position = world.getMapper(
          com.riiablo.engine.server.component.Position.class).get(entityId);
      com.riiablo.engine.server.component.Velocity velocity = world.getMapper(
          com.riiablo.engine.server.component.Velocity.class).get(entityId);
      com.riiablo.engine.server.component.CofReference cof = world.getMapper(
          com.riiablo.engine.server.component.CofReference.class).get(entityId);
      Gdx.app.log(TAG, String.format(
          "[NET_MOVE] phase=server_receive connection=%d player=%d pos=(%.2f,%.2f) "
              + "velocity=(%.2f,%.2f) mode=%d",
          packet.id, entityId,
          position != null ? position.position.x : Float.NaN,
          position != null ? position.position.y : Float.NaN,
          velocity != null ? velocity.velocity.x : Float.NaN,
          velocity != null ? velocity.velocity.y : Float.NaN,
          cof != null ? cof.mode & 0xFF : -1));
    }
  }

  /** Handles untrusted combat input; all damage and projectile creation stays on the server. */
  private void CastSkillRequest(Packet packet) {
    int entityId = getPlayerEntityId(packet);
    CastSkillRequest request = (CastSkillRequest) packet.data.data(new CastSkillRequest());
    if (isPlayerDead(entityId)) {
      Gdx.app.log(TAG, "[NET_CAST] phase=reject player=" + entityId
          + " skill=" + request.skillId() + " reason=player_dead");
      return;
    }
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
  /** Authenticated party boundary. Source identity is always derived from the connection. */
  private void PartyRequest(Packet packet) {
    PartyRequest request = (PartyRequest) packet.data.data(new PartyRequest());
    PartyRequestCache.Intent intent = new PartyRequestCache.Intent(
        request.operation(), request.targetEntityId());
    PartyRequestCache.Entry cached = partyRequestCache.lookup(packet.id, request.requestId());
    if (cached != null) {
      if (cached.matches(intent)) {
        outPackets.offer(Packet.obtain(1 << packet.id, ByteBuffer.wrap(cached.response())));
      } else {
        int source = player.get(packet.id, Engine.INVALID_ENTITY);
        sendPartyResult(packet.id, request.requestId(), request.operation(), source,
            request.targetEntityId(), PartyServiceProtocol.Result.reject("REQUEST_ID_REUSED"), false);
      }
      return;
    }

    int source = player.get(packet.id, Engine.INVALID_ENTITY);
    PartyServiceProtocol.Result result;
    if ((request.operation() == PartyOperation.HOSTILE
        || request.operation() == PartyOperation.UNHOSTILE)
        && !hostilityRequestAllowed(source, request.targetEntityId())) {
      result = PartyServiceProtocol.Result.reject("HOSTILE_REJECTED");
    } else {
      result = PartyServiceProtocol.execute(
          partyManager, source, request.operation(), request.targetEntityId(),
          entityId -> clientForEntity(entityId) >= 0);
    }
    sendPartyResult(packet.id, request.requestId(), request.operation(), source,
        request.targetEntityId(), result, true, intent);

    if (result.success && request.operation() != PartyOperation.SNAPSHOT) {
      // Every client receives a personalized roster; relations and pending
      // invitations are intentionally evaluated from that client's viewpoint.
      broadcastPartySnapshots(request.operation(), source, request.targetEntityId(), packet.id);
    }
    Gdx.app.log(TAG, "[PARTY] source=" + source + " target=" + request.targetEntityId()
        + " operation=" + request.operation() + " success=" + result.success
        + " reason=" + result.reason + " retryAfterMillis=" + result.retryAfterMillis);
  }

  private int clientForEntity(int entityId) {
    for (IntIntMap.Entry entry : player.entries()) {
      if (entry.value == entityId && (connected & (1 << entry.key)) != 0) return entry.key;
    }
    return -1;
  }

  /** D2MOO PARTYSCREEN_ToggleHostile gate: both players level 9+ and source
   * must currently be in a town room. */
  private boolean hostilityRequestAllowed(int sourceEntityId, int targetEntityId) {
    if (sourceEntityId < 0 || targetEntityId < 0 || sourceEntityId == targetEntityId) return false;
    Player source = world.getMapper(Player.class).get(sourceEntityId);
    Player target = world.getMapper(Player.class).get(targetEntityId);
    if (source == null || target == null || source.data == null || target.data == null) return false;
    int sourceLevel = Math.max(1, source.data.level);
    int targetLevel = Math.max(1, target.data.level);
    com.riiablo.engine.server.component.MapWrapper wrapper = world.getMapper(
        com.riiablo.engine.server.component.MapWrapper.class).get(sourceEntityId);
    return PvpCombatRules.canDeclareHostility(sourceLevel, targetLevel,
        wrapper != null && wrapper.zone != null && wrapper.zone.isTown());
  }

  private void broadcastPartySnapshots(byte operation, int sourceEntityId,
                                       int targetEntityId, int excludedClientId) {
    for (int clientId = 0; clientId < MAX_CLIENTS; clientId++) {
      if (clientId == excludedClientId || (connected & (1 << clientId)) == 0
          || player.get(clientId, Engine.INVALID_ENTITY) == Engine.INVALID_ENTITY) continue;
      sendPartyResult(clientId, 0, operation, sourceEntityId, targetEntityId,
          PartyServiceProtocol.Result.success(), false);
    }
  }

  private void sendPartyResult(int clientId, long requestId, byte operation,
                               int sourceEntityId, int targetEntityId,
                               PartyServiceProtocol.Result result, boolean cacheResponse) {
    sendPartyResult(clientId, requestId, operation, sourceEntityId, targetEntityId,
        result, cacheResponse, new PartyRequestCache.Intent(operation, targetEntityId));
  }

  private void sendPartyResult(int clientId, long requestId, byte operation,
                               int sourceEntityId, int targetEntityId,
                               PartyServiceProtocol.Result result, boolean cacheResponse,
                               PartyRequestCache.Intent intent) {
    FlatBufferBuilder builder = new FlatBufferBuilder(4096);
    int reasonOffset = builder.createString(result.reason == null ? "" : result.reason);
    int viewerEntityId = player.get(clientId, Engine.INVALID_ENTITY);
    int[] members = new int[player.size];
    int count = 0;
    for (IntIntMap.Entry entry : player.entries()) {
      int entityId = entry.value;
      if (entityId == Engine.INVALID_ENTITY || (connected & (1 << entry.key)) == 0) continue;
      com.riiablo.engine.server.component.Player playerComponent = world.getMapper(Player.class).get(entityId);
      com.riiablo.save.CharData data = playerComponent == null ? null : playerComponent.data;
      String name = data == null || data.name == null ? "" : data.name;
      int classId = data == null ? 0 : data.charClass;
      PartyMember member = null;
      Party party = partyManager.getPartyForPlayer(entityId);
      if (party != null) member = party.getMember(entityId);
      int level = member == null ? (data == null ? 1 : data.level) : member.level;
      int hp = member == null ? 0 : member.currentHp;
      int maxHp = member == null ? 0 : member.maxHp;
      int mana = member == null ? 0 : member.currentMana;
      int maxMana = member == null ? 0 : member.maxMana;
      int levelId = member == null ? -1 : member.levelId;
      int x = member == null ? 0 : member.x;
      int y = member == null ? 0 : member.y;
      boolean alive = member == null || member.alive;
      boolean online = member == null || member.online;
      int partyId = partyManager.getPartyId(entityId);
      int relation = partyManager.getRelation(viewerEntityId, entityId);
      int nameOffset = builder.createString(name);
      members[count++] = PartyMemberSnapshot.createPartyMemberSnapshot(builder, entityId,
          nameOffset, classId, level, hp, maxHp, mana, maxMana, levelId, x, y,
          alive, online, party != null && party.isLeader(entityId), partyId, relation);
    }
    int[] roster = java.util.Arrays.copyOf(members, count);
    int membersOffset = PartyResult.createMembersVector(builder, roster);
    int resultOffset = PartyResult.createPartyResult(builder, requestId, result.success,
        reasonOffset, operation, sourceEntityId, targetEntityId,
        partyManager.getPartyId(viewerEntityId), membersOffset, result.retryAfterMillis);
    int root = com.riiablo.net.packet.d2gs.D2GS.createD2GS(builder,
        D2GSData.PartyResult, resultOffset);
    com.riiablo.net.packet.d2gs.D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    ByteBuffer response = builder.dataBuffer();
    byte[] bytes = new byte[response.remaining()];
    response.duplicate().get(bytes);
    if (cacheResponse) partyRequestCache.put(clientId, requestId, intent, bytes);
    outPackets.offer(Packet.obtain(1 << clientId, ByteBuffer.wrap(bytes)));
  }

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
    if (reason == null && isPlayerDead(playerId)) reason = "PLAYER_DEAD";
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
    } else if (reason == null
        && service == com.riiablo.engine.server.npc.NpcServiceProtocol.Service.HIRE) {
      NativeMercenaryRewardSystem mercenaries =
          world.getSystem(NativeMercenaryRewardSystem.class);
      if (mercenaries == null) {
        reason = "MERCENARY_SERVICE_UNAVAILABLE";
      } else if (operation == com.riiablo.engine.server.npc.NpcServiceProtocol.Operation.OPEN) {
        // The client may open Kashya's hire panel before choosing a candidate.
      } else if (operation == com.riiablo.engine.server.npc.NpcServiceProtocol.Operation.HIRE) {
        if (!mercenaries.hireAct1Rogue(playerId)) reason = "HIRE_REJECTED";
        else resultItemId = mercenaries.mercenaryEntityId(playerId);
      } else {
        reason = "OPERATION_NOT_IMPLEMENTED";
      }
    } else if (reason == null
        && service == com.riiablo.engine.server.npc.NpcServiceProtocol.Service.RESURRECT) {
      NativeMercenaryRewardSystem mercenaries =
          world.getSystem(NativeMercenaryRewardSystem.class);
      if (mercenaries == null) {
        reason = "MERCENARY_SERVICE_UNAVAILABLE";
      } else if (!mercenaries.hasDeadMercenary(playerId)) {
        reason = "MERCENARY_NOT_DEAD";
      } else if (operation == com.riiablo.engine.server.npc.NpcServiceProtocol.Operation.OPEN) {
        resultItemId = mercenaries.mercenaryEntityId(playerId);
      } else if (operation
          == com.riiablo.engine.server.npc.NpcServiceProtocol.Operation.RESURRECT) {
        int cost = mercenaries.resurrectionCost(playerId);
        if (!mercenaries.resurrectMercenary(playerId)) reason = "RESURRECT_REJECTED";
        else {
          resultItemId = mercenaries.mercenaryEntityId(playerId);
          Gdx.app.log(TAG, "[MERC_LIFECYCLE] phase=npc_resurrect player=" + playerId
              + " npc=" + npcId + " entity=" + resultItemId + " cost=" + cost);
        }
      } else {
        reason = "OPERATION_NOT_IMPLEMENTED";
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

  /** Handles idempotent server-authoritative quest dialogue and object intents. */
  private void QuestRequest(Packet packet) {
    QuestRequest request = (QuestRequest) packet.data.data(new QuestRequest());
    QuestRequestCache.Intent intent = new QuestRequestCache.Intent(
        (byte) request.operation(), request.targetEntityId(), request.messageIndex());
    QuestRequestCache.Entry completed = questRequestCache.lookup(packet.id, request.requestId());
    if (completed != null) {
      if (completed.matches(intent)) {
        outPackets.offer(Packet.obtain(1 << packet.id, ByteBuffer.wrap(completed.response())));
        Gdx.app.log(TAG, "[QUEST_NET] phase=replay client=" + packet.id
            + " request=" + request.requestId());
      } else {
        sendQuestResult(packet.id, request.requestId(), false, "REQUEST_ID_REUSED",
            intent, false);
      }
      return;
    }

    int playerId = player.get(packet.id, Engine.INVALID_ENTITY);
    String reason = null;
    if (playerId == Engine.INVALID_ENTITY) reason = "PLAYER_NOT_FOUND";
    else if (isPlayerDead(playerId)) reason = "PLAYER_DEAD";
    else if (request.operation() == QuestOperation.SNAPSHOT) {
      // A result snapshot is useful after reconnect or client-side correction.
    } else if (request.operation() == QuestOperation.NPC_MESSAGE) {
      reason = validateQuestNpc(playerId, request.targetEntityId(), request.messageIndex());
      if (reason == null) {
        world.getSystem(EventSystem.class).dispatch(
            com.riiablo.engine.server.event.NpcQuestMessageEvent.obtain(
                playerId, request.targetEntityId(), request.messageIndex()));
      }
    } else if (request.operation() == QuestOperation.OBJECT_INTERACTION) {
      reason = validateQuestObject(playerId, request.targetEntityId());
      if (reason == null) {
        world.getSystem(ObjectInteractor.class).interact(playerId, request.targetEntityId());
      }
    } else {
      reason = "UNSUPPORTED_OPERATION";
    }

    boolean success = reason == null;
    sendQuestResult(packet.id, request.requestId(), success, reason, intent, true);
    Gdx.app.log(TAG, "[QUEST_NET] player=" + playerId + " request="
        + request.requestId() + " operation=" + request.operation()
        + " target=" + request.targetEntityId() + " message=" + request.messageIndex()
        + " result=" + (success ? "OK" : reason));
  }

  private String validateQuestNpc(int playerId, int npcId, int messageIndex) {
    if (messageIndex < 0 || messageIndex > 255) return "INVALID_MESSAGE";
    com.riiablo.engine.server.component.Monster npc =
        world.getMapper(com.riiablo.engine.server.component.Monster.class).get(npcId);
    if (npc == null || npc.monstats == null || !npc.monstats.npc || !npc.monstats.interact) {
      return "NPC_NOT_FOUND";
    }
    if (levelIdOf(playerId) < 0 || levelIdOf(playerId) != levelIdOf(npcId)) {
      return "NPC_WRONG_LEVEL";
    }
    com.riiablo.engine.server.component.Position source =
        world.getMapper(com.riiablo.engine.server.component.Position.class).get(playerId);
    com.riiablo.engine.server.component.Position target =
        world.getMapper(com.riiablo.engine.server.component.Position.class).get(npcId);
    if (source == null || target == null
        || !com.riiablo.engine.server.npc.NpcServiceProtocol.inRange(
            source.position, target.position)) return "NPC_OUT_OF_RANGE";
    return null;
  }

  private String validateQuestObject(int playerId, int objectId) {
    com.riiablo.engine.server.component.Object object =
        world.getMapper(com.riiablo.engine.server.component.Object.class).get(objectId);
    if (object == null || object.base == null
        || !isNetworkQuestObject(
            com.riiablo.engine.server.object.NativeQuestObjectResolver.resolve(object.base))) {
      return "QUEST_OBJECT_NOT_FOUND";
    }
    if (levelIdOf(playerId) < 0 || levelIdOf(playerId) != levelIdOf(objectId)) {
      return "QUEST_OBJECT_WRONG_LEVEL";
    }
    com.riiablo.engine.server.component.Interactable interactable =
        world.getMapper(com.riiablo.engine.server.component.Interactable.class).get(objectId);
    com.riiablo.engine.server.component.Position source =
        world.getMapper(com.riiablo.engine.server.component.Position.class).get(playerId);
    com.riiablo.engine.server.component.Position target =
        world.getMapper(com.riiablo.engine.server.component.Position.class).get(objectId);
    if (interactable == null || source == null || target == null) return "QUEST_OBJECT_INACTIVE";
    float range = Math.max(1f, interactable.range) + 2f;
    return source.position.dst2(target.position) <= range * range
        ? null : "QUEST_OBJECT_OUT_OF_RANGE";
  }

  private static boolean isNetworkQuestObject(
      com.riiablo.engine.server.object.NativeQuestObjectResolver.Type type) {
    return type == com.riiablo.engine.server.object.NativeQuestObjectResolver.Type.TOWER_TOME
        || type == com.riiablo.engine.server.object.NativeQuestObjectResolver.Type.CAIRN_STONE
        || type == com.riiablo.engine.server.object.NativeQuestObjectResolver.Type.CAIN_GIBBET
        || type == com.riiablo.engine.server.object.NativeQuestObjectResolver.Type.INIFUSS_TREE
        || type == com.riiablo.engine.server.object.NativeQuestObjectResolver.Type.HORADRIC_MALUS;
  }

  private void sendQuestResult(int clientId, long requestId, boolean success, String reason,
                               QuestRequestCache.Intent intent, boolean cacheResponse) {
    int playerId = player.get(clientId, Engine.INVALID_ENTITY);
    Player component = playerId == Engine.INVALID_ENTITY ? null
        : world.getMapper(Player.class).get(playerId);
    short[] records = QuestSnapshot.records(component == null ? null : component.data);
    long revision = QuestSnapshot.revision(records);
    FlatBufferBuilder builder = new FlatBufferBuilder(256);
    int reasonOffset = builder.createString(reason == null ? "" : reason);
    int recordsOffset = QuestResult.createQuestRecordsVector(builder, records);
    int result = QuestResult.createQuestResult(builder, requestId, success, reasonOffset,
        intent.operation, intent.targetEntityId, intent.messageIndex, revision, recordsOffset);
    int root = com.riiablo.net.packet.d2gs.D2GS.createD2GS(
        builder, D2GSData.QuestResult, result);
    com.riiablo.net.packet.d2gs.D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    ByteBuffer response = builder.dataBuffer();
    byte[] bytes = new byte[response.remaining()];
    response.duplicate().get(bytes);
    if (cacheResponse) questRequestCache.put(clientId, requestId, intent, bytes);
    outPackets.offer(Packet.obtain(1 << clientId, ByteBuffer.wrap(bytes)));
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

  private boolean isPlayerDead(int entityId) {
    return entityId != Engine.INVALID_ENTITY
        && world.getMapper(com.riiablo.engine.server.component.PlayerCorpse.class)
            .has(entityId);
  }

  private static boolean isLegacyItemRequest(byte dataType) {
    return dataType >= D2GSData.GroundToCursor && dataType <= D2GSData.SwapBeltItem;
  }

  /** Authenticated RESPAWN request; all position and vitals mutation remains on D2GS. */
  private void PlayerLifecycleRequest(Packet packet) {
    PlayerLifecycleRequest request = (PlayerLifecycleRequest) packet.data.data(
        new PlayerLifecycleRequest());
    int entityId = getPlayerEntityId(packet);
    ServerPlayerDeathSystem deaths = world.getSystem(ServerPlayerDeathSystem.class);
    String reason = null;
    boolean success = false;
    if (request.operation() != PlayerLifecycleOperation.RESPAWN) {
      reason = "INVALID_OPERATION";
    } else if (!deaths.isPlayerDead(entityId)) {
      reason = "PLAYER_NOT_DEAD";
    } else if (!deaths.canRespawn(entityId)) {
      reason = "DEATH_ANIMATION_ACTIVE";
    } else if (!(success = deaths.respawnAtTown(entityId))) {
      reason = "RESPAWN_FAILED";
    }

    com.riiablo.engine.server.component.Position position = world.getMapper(
        com.riiablo.engine.server.component.Position.class).get(entityId);
    float x = position != null ? position.position.x : 0f;
    float y = position != null ? position.position.y : 0f;
    FlatBufferBuilder builder = new FlatBufferBuilder(128);
    int reasonOffset = builder.createString(reason == null ? "OK" : reason);
    int result = PlayerLifecycleResult.createPlayerLifecycleResult(builder,
        request.requestId(), success, reasonOffset, request.operation(), entityId, x, y);
    int root = com.riiablo.net.packet.d2gs.D2GS.createD2GS(
        builder, D2GSData.PlayerLifecycleResult, result);
    com.riiablo.net.packet.d2gs.D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    outPackets.offer(Packet.obtain(1 << packet.id, builder.dataBuffer()));
    if (success) sync.process(entityId);
    Gdx.app.log(TAG, "[PLAYER_RESPAWN] phase=" + (success ? "accept" : "reject")
        + " connection=" + packet.id + " player=" + entityId
        + " request=" + request.requestId() + " reason="
        + (reason == null ? "OK" : reason));
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
    int authenticatedPlayerId = player.get(packet.id, Engine.INVALID_ENTITY);
    if (isPlayerDead(authenticatedPlayerId)) {
      sendItemMoveResult(packet.id, intent, false, ItemMoveFailure.PLAYER_DEAD,
          authoritativeItems.revision(authenticatedPlayerId), false, false);
      Gdx.app.log(TAG, "[ITEM_MOVE] phase=reject connection=" + packet.id
          + " player=" + authenticatedPlayerId + " reason=player_dead");
      return;
    }
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
        int playerPartyId = partyManager.getPartyId(playerEntityId);
        com.badlogic.gdx.utils.Array<PartyGoldShareService.Recipient> recipients =
            groundItem != null && "gld".equalsIgnoreCase(groundItem.code)
                && com.riiablo.engine.server.item.GroundDropOwnership
                    .isPartyShareGold(groundEntity)
                ? partyGoldRecipients(playerEntityId) : null;
        if (recipients != null && recipients.size > 1) {
          outcome = authoritativeItems.pickupSharedGold(playerEntityId, playerPartyId,
              character, intent, groundItem, recipients);
        } else {
          outcome = authoritativeItems.pickup(playerEntityId, playerPartyId,
              character, intent, groundItem);
        }
      }
      if (outcome.success && outcome.consumeGroundEntity) world.delete(groundEntity);
    } else if (operation == ItemMoveOperation.CURSOR_TO_GROUND) {
      com.riiablo.engine.server.component.Position position = playerEntityId == Engine.INVALID_ENTITY
          ? null : world.getMapper(com.riiablo.engine.server.component.Position.class).get(playerEntityId);
      outcome = authoritativeItems.drop(playerEntityId, character, intent, item -> {
        if (position != null) {
          int droppedEntity = factory.createItem(item, position.position.x, position.position.y);
          if (droppedEntity >= 0) {
            com.riiablo.engine.server.item.GroundDropOwnership.register(droppedEntity,
                playerEntityId, partyManager.getPartyId(playerEntityId), 10_000L, 10_000L);
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

  private com.badlogic.gdx.utils.Array<PartyGoldShareService.Recipient> partyGoldRecipients(
      int pickerEntityId) {
    com.badlogic.gdx.utils.Array<PartyGoldShareService.Recipient> recipients =
        new com.badlogic.gdx.utils.Array<>();
    short partyId = partyManager.getPartyId(pickerEntityId);
    if (partyId == com.riiablo.engine.server.party.Party.INVALID_ID) return recipients;
    com.riiablo.engine.server.party.Party party = partyManager.getParty(partyId);
    if (party == null) return recipients;
    Player picker = world.getMapper(Player.class).get(pickerEntityId);
    if (picker == null || picker.data == null) return recipients;
    recipients.add(new PartyGoldShareService.Recipient(pickerEntityId, picker.data, true));
    int levelId = levelIdOf(pickerEntityId);
    if (levelId < 0) return recipients;
    for (com.riiablo.engine.server.party.PartyMember member : party.getMembers()) {
      if (member == null || member.entityId == pickerEntityId
          || !member.online || !member.alive) continue;
      if (levelIdOf(member.entityId) != levelId) continue;
      Player target = world.getMapper(Player.class).get(member.entityId);
      if (target == null || target.data == null) continue;
      recipients.add(new PartyGoldShareService.Recipient(member.entityId, target.data, true));
    }
    return recipients;
  }

  private int levelIdOf(int entityId) {
    com.riiablo.engine.server.component.MapWrapper wrapper =
        world.getMapper(com.riiablo.engine.server.component.MapWrapper.class).get(entityId);
    return wrapper == null || wrapper.zone == null || wrapper.zone.level == null
        ? -1 : wrapper.zone.level.Id;
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
    final byte[] readBuffer = new byte[1 << 16];
    final SizePrefixedPacketAccumulator inbound =
        new SizePrefixedPacketAccumulator(1 << 16, 1 << 22,
            (1 << 22) + (1 << 16) + Integer.BYTES);
    volatile boolean kill = false;

    Client(int id, Socket socket) {
      super(clientThreads, generateClientName());
      TAG = D2GS.TAG + "{" + id + "}";
      this.id = id;
      this.socket = socket;
    }

    public synchronized void send(Packet packet) throws IOException {
      if (kill || socket == null || !socket.isConnected()) return;
      WritableByteChannel out = Channels.newChannel(socket.getOutputStream());
      packet.buffer.mark();
      while (packet.buffer.hasRemaining()) out.write(packet.buffer);
      packet.buffer.reset();
      if ((connected & (1 << id)) == 0 && packet.data.dataType() == D2GSData.Connection) {
        connected |= (1 << id);
      }
    }

    @Override
    public void run() {
      while (!kill) {
        try {
          int read = socket.getInputStream().read(readBuffer);
          if (read == -1) {
            kill = true;
            break;
          }
          if (read == 0) continue;
          inbound.append(readBuffer, 0, read);
          inbound.drain(this::receive);
        } catch (Throwable t) {
          Gdx.app.log(TAG, "[NET_FRAME] phase=server_receive_error action=disconnect", t);
          kill = true;
        }
      }

      closeSocket();
      Disconnect(id);
    }

    /** Serializes disposal against send() so the libGDX socket cannot vanish mid-write. */
    private synchronized void closeSocket() {
      if (socket == null) return;
      Gdx.app.log(TAG, "closing socket to " + socket.getRemoteAddress());
      socket.dispose();
      socket = null;
    }

    private void receive(ByteBuffer frame) {
      ByteBuffer copy = ByteBuffer.allocate(frame.remaining());
      copy.put(frame.duplicate()).flip();
      Packet packet = Packet.obtainPayload(id, copy);
      if (DEBUG_RECEIVED_PACKETS && !ignoredPackets.get(packet.data.dataType())) {
        Gdx.app.log(TAG, "received " + D2GSData.name(packet.data.dataType())
            + " packet from " + socket.getRemoteAddress());
      }
      try {
        boolean success = packets.offer(packet, 5, TimeUnit.MILLISECONDS);
        if (!success) {
          Gdx.app.log(TAG, "failed to add to queue -- closing " + socket.getRemoteAddress());
          kill = true;
          return;
        }
        if (packet.data.dataType() != D2GSData.Ping) return;

        Ping ping = (Ping) packet.data.data(new Ping());
        FlatBufferBuilder builder = new FlatBufferBuilder(0);
        int dataOffset = Ping.createPing(builder, ping.tickCount(), ping.sendTime(), 0, true);
        int root = com.riiablo.net.packet.d2gs.D2GS.createD2GS(
            builder, D2GSData.Ping, dataOffset);
        com.riiablo.net.packet.d2gs.D2GS.finishSizePrefixedD2GSBuffer(builder, root);
        Packet response = Packet.obtain(1 << packet.id, builder.dataBuffer());
        if (DEBUG_SENT_PACKETS && !ignoredPackets.get(packet.data.dataType())) {
          Gdx.app.log(TAG, "dispatching " + D2GSData.name(packet.data.dataType())
              + " ACK packet to " + String.format("0x%08X", packet.id));
        }
        send(response);
      } catch (InterruptedException t) {
        Thread.currentThread().interrupt();
        kill = true;
      } catch (IOException t) {
        throw new GdxRuntimeException("Unable to send ping ACK", t);
      }
    }
  }
}
