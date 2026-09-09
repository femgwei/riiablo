package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.server.ai.AI;
import com.riiablo.engine.server.component.AIWrapper;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.component.PathWrapper;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Running;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.Size;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.Target;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.map.Map;
import org.junit.jupiter.api.Test;

class SummonedPetSystemTest {
  @Test
  void timedPetExpiresInNativeFrames() {
    World world = new World(new WorldConfigurationBuilder()
        .with(new SummonedPetSystem()).build());
    try {
      int owner = world.create();
      world.getMapper(Player.class).create(owner);
      int pet = world.create();
      world.getMapper(SummonedPet.class).create(pet)
          .set(owner, "dopplezon", 28, 1, true, 25);
      world.setDelta(0.5f);
      world.process();
      assertTrue(world.getEntityManager().isActive(pet));
      world.process();
      assertFalse(world.getEntityManager().isActive(pet));
    } finally {
      world.dispose();
    }
  }

  @Test
  void petIsRemovedWhenOwnerNoLongerExists() {
    World world = new World(new WorldConfigurationBuilder()
        .with(new SummonedPetSystem()).build());
    try {
      int pet = world.create();
      world.getMapper(SummonedPet.class).create(pet)
          .set(99, "valkyrie", 32, 1, false, 0);
      world.process();
      assertFalse(world.getEntityManager().isActive(pet));
    } finally {
      world.dispose();
    }
  }

  @Test
  void petIsRemovedWhenOwnerDies() {
    World world = new World(new WorldConfigurationBuilder()
        .with(new SummonedPetSystem()).build());
    try {
      int owner = world.create();
      world.getMapper(Player.class).create(owner);
      // PlayerCorpse is the authoritative death marker produced by
      // ServerPlayerDeathSystem; no ItemStatCost/resource bootstrap is
      // required for this lifecycle test.
      world.getMapper(com.riiablo.engine.server.component.PlayerCorpse.class)
          .create(owner).playerId = owner;
      int pet = world.create();
      world.getMapper(SummonedPet.class).create(pet)
          .set(owner, "spiritwolf", 227, 1, false, 0);
      world.process();
      assertFalse(world.getEntityManager().isActive(pet));
    } finally {
      world.dispose();
    }
  }

  @Test
  void nativeDruidPetListsShareWolfAndSpiritFamilies() {
    assertTrue(com.riiablo.engine.server.pet.PetType.sameNativeList("spiritwolf", "fenris"));
    assertTrue(com.riiablo.engine.server.pet.PetType.sameNativeList("fenris", "grizzly"));
    assertTrue(com.riiablo.engine.server.pet.PetType.sameNativeList("oak sage", "heart of wolverine"));
    assertTrue(com.riiablo.engine.server.pet.PetType.sameNativeList("poison creeper", "solar creeper"));
    assertEquals("fenris", com.riiablo.engine.server.pet.PetType.canonical("Dire Wolf"));
    assertFalse(com.riiablo.engine.server.pet.PetType.sameNativeType("spiritwolf", "fenris"));
    assertTrue(com.riiablo.engine.server.pet.PetType.sameNativeType("Oak Sage", "totem"));
    assertTrue(com.riiablo.engine.server.pet.PetType.warpsWithOwner("spiritwolf"));
    assertTrue(com.riiablo.engine.server.pet.PetType.warpsWithOwner("vine"));
    assertFalse(com.riiablo.engine.server.pet.PetType.warpsWithOwner("raven"));
  }

  @Test
  void crossZoneWarpClearsOldMovementCombatAndAiIntent() {
    World world = new World(new WorldConfigurationBuilder()
        .with(new SummonedPetSystem()).build());
    try {
      OpenZone oldZone = new OpenZone();
      OpenZone ownerZone = new OpenZone();
      int owner = createOwner(world, ownerZone, 40f, 50f);
      int pet = createPet(world, owner, oldZone, 2f, 3f, "skeleton");

      world.getMapper(Pathfind.class).create(pet).targetEntityId = 91;
      world.getMapper(PathWrapper.class).create(pet);
      world.getMapper(Target.class).create(pet).target = 91;
      world.getMapper(Casting.class).create(pet).set(0, 91, new Vector2(9f, 9f));
      world.getMapper(Sequence.class).create(pet).sequence((byte) 1, (byte) 0);
      world.getMapper(Running.class).create(pet);
      Velocity velocity = world.getMapper(Velocity.class).create(pet).set(4f, 6f);
      velocity.velocity.set(2f, 1f);
      velocity.setModeSpeedBonusPercent(80f);
      TestAI ai = new TestAI(pet);
      world.getMapper(AIWrapper.class).create(pet).ai = ai;

      world.process();

      assertTrue(world.getEntityManager().isActive(pet));
      assertSame(ownerZone, world.getMapper(MapWrapper.class).get(pet).zone);
      assertNotEquals(new Vector2(40f, 50f), world.getMapper(Position.class).get(pet).position,
          "landing must not overlap the owner footprint");
      assertFalse(world.getMapper(Pathfind.class).has(pet));
      assertFalse(world.getMapper(PathWrapper.class).has(pet));
      assertFalse(world.getMapper(Target.class).has(pet));
      assertFalse(world.getMapper(Casting.class).has(pet));
      assertFalse(world.getMapper(Sequence.class).has(pet));
      assertFalse(world.getMapper(Running.class).has(pet));
      assertTrue(world.getMapper(Velocity.class).has(pet));
      assertEquals(Vector2.Zero, velocity.velocity);
      assertEquals(0f, velocity.modeSpeedBonusMultiplier);
      assertTrue(ai.ownerWarped);
    } finally {
      world.dispose();
    }
  }

  @Test
  void nonWarpPetIsRemovedAcrossZoneBoundary() {
    World world = new World(new WorldConfigurationBuilder()
        .with(new SummonedPetSystem()).build());
    try {
      int owner = createOwner(world, new OpenZone(), 10f, 10f);
      int pet = createPet(world, owner, new OpenZone(), 2f, 2f, "raven");
      world.process();
      assertFalse(world.getEntityManager().isActive(pet));
    } finally {
      world.dispose();
    }
  }

  @Test
  void distantMobilePetRegroupsInsideSameZone() {
    World world = new World(new WorldConfigurationBuilder()
        .with(new SummonedPetSystem()).build());
    try {
      OpenZone zone = new OpenZone();
      int owner = createOwner(world, zone, 60f, 60f);
      int pet = createPet(world, owner, zone, 0f, 0f, "skeletonmage");
      world.process();

      Vector2 result = world.getMapper(Position.class).get(pet).position;
      assertTrue(result.dst(new Vector2(60f, 60f)) <= 8f);
      assertTrue(result.dst(new Vector2(60f, 60f)) >= 2f);
    } finally {
      world.dispose();
    }
  }

  @Test
  void stationaryBoneWallDoesNotRegroupInsideSameZone() {
    World world = new World(new WorldConfigurationBuilder()
        .with(new SummonedPetSystem()).build());
    try {
      OpenZone zone = new OpenZone();
      int owner = createOwner(world, zone, 100f, 100f);
      int pet = createPet(world, owner, zone, 0f, 0f, "bonewall");
      world.getMapper(SummonedPet.class).get(pet).boneWall = true;
      world.process();

      assertEquals(new Vector2(0f, 0f), world.getMapper(Position.class).get(pet).position);
    } finally {
      world.dispose();
    }
  }

  @Test
  void nonAdjacentRoomWithoutPathUsesOwnerTrailFallback() {
    assertTrue(SummonedPetSystem.shouldRegroup(true, 29f, false, false, true));
    assertFalse(SummonedPetSystem.shouldRegroup(true, 29f, false, true, true));
    assertFalse(SummonedPetSystem.shouldRegroup(true, 29f, true, false, true));
    assertTrue(SummonedPetSystem.shouldRegroup(true, 51f, true, true, true));
    assertFalse(SummonedPetSystem.shouldRegroup(true, 51f, true, true, false));
  }

  @Test
  void ownerDistanceUsesNativeIntegerApproximationAndPetFootprint() {
    assertEquals(49, SummonedPetSystem.nativeOwnerDistance(
        new Vector2(0f, 0f), new Vector2(50f, 0f), 1));
    assertEquals(51, SummonedPetSystem.nativeOwnerDistance(
        new Vector2(0f, 0f), new Vector2(52f, 0f), 1));
    assertEquals(73, SummonedPetSystem.nativeOwnerDistance(
        new Vector2(0f, 0f), new Vector2(50f, 50f), 1));
  }

  @Test
  void landingSearchExpandsBeyondInitialEightSubtiles() {
    Map.Zone zone = new Map.Zone() {
      @Override
      public boolean findFreeCoordinates(Vector2 coordinates, int unitSize,
          int maxDistance, boolean allowNeighborRooms, Vector2 result) {
        if (Math.max(Math.abs(coordinates.x - 30f), Math.abs(coordinates.y - 30f)) < 12f) {
          return false;
        }
        result.set(coordinates);
        return true;
      }
    };
    Vector2 result = new Vector2();
    assertTrue(SummonedPetSystem.findOwnerLanding(
        zone, new Vector2(30f, 30f), 1, result, new Vector2()));
    assertEquals(12f,
        Math.max(Math.abs(result.x - 30f), Math.abs(result.y - 30f)));
  }

  private static int createOwner(World world, Map.Zone zone, float x, float y) {
    int owner = world.create();
    world.getMapper(Player.class).create(owner);
    world.getMapper(Position.class).create(owner).position.set(x, y);
    world.getMapper(MapWrapper.class).create(owner).set(null, zone);
    return owner;
  }

  private static int createPet(World world, int owner, Map.Zone zone,
      float x, float y, String petType) {
    int pet = world.create();
    world.getMapper(SummonedPet.class).create(pet)
        .set(owner, petType, 70, 1, false, 0);
    world.getMapper(Position.class).create(pet).position.set(x, y);
    world.getMapper(MapWrapper.class).create(pet).set(null, zone);
    world.getMapper(Size.class).create(pet).size = 1;
    return pet;
  }

  private static class OpenZone extends Map.Zone {
    @Override
    public boolean findFreeCoordinates(Vector2 coordinates, int unitSize,
        int maxDistance, boolean allowNeighborRooms, Vector2 result) {
      result.set(coordinates);
      return true;
    }
  }

  private static class TestAI extends AI {
    boolean ownerWarped;

    TestAI(int entityId) {
      super(entityId);
    }

    @Override
    public void onOwnerWarp() {
      super.onOwnerWarp();
      ownerWarped = true;
    }
  }
}
