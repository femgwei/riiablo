package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.serializer.StateSerializer;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.pet.PetType;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.skill.DruidSkills;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.save.CharData;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.net.packet.d2gs.StateP;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** ECS wiring for authoritative Druid SrvDo114/115/119 summon creation. */
class DruidSummonIntegrationTest extends RiiabloTest {
  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.CsvSource({"1,1068,true", "5,1068,true", "1,5,false"})
  void ravenSummonAiAttackKeyframeReachesPhysicalSettlement(int level, int seed, boolean hit) {
    RecordingFactory factory = new RecordingFactory();
    MonsterModeMeleeIntegrationTest.Probe probe = new MonsterModeMeleeIntegrationTest.Probe();
    CofManager cofs = new CofManager();
    World previous = Riiablo.engine;
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), probe, new Actioneer(), new Pathfinder(), cofs,
            new ServerSkillSystem(true), new StateUpdater(), factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0))
        .register("serverContext", new ServerGameplayContext(Riiablo.files, 1234)));
    Riiablo.engine = world;
    try {
      int owner = createPlayer(world, "raven-combat", 10, 10);
      CharData data = world.getMapper(Player.class).get(owner).data;
      data.setSkillLevel(SkillId.RAVEN, level);
      world.getMapper(AttributesWrapper.class).get(owner).attrs.base().put(Stat.level, 99);
      world.getMapper(AttributesWrapper.class).get(owner).attrs.reset();
      com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(SkillId.RAVEN);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(owner, SkillId.RAVEN,
          Engine.INVALID_ENTITY, new Vector2(10, 10), skill.srvdofunc, skill.cltdofunc));
      int raven = factory.lastEntity;
      assertEquals(-1, world.getMapper(AttributesWrapper.class).get(raven).attrs
          .getValue(Stat.item_stupidity, 0), "Raven's negative blind-target stat is intentional");
      com.riiablo.codec.excel.MonStats.Entry row = Riiablo.files.monstats.get("druidhawk");
      world.getMapper(Monster.class).get(raven).set(row, Riiablo.files.monstats2.get(row.MonStatsEx));
      world.getMapper(com.riiablo.engine.server.component.Class.class).create(raven).type =
          com.riiablo.engine.server.component.Class.Type.MON;
      world.getMapper(com.riiablo.engine.server.component.Angle.class).create(raven);
      world.getMapper(com.riiablo.engine.server.component.Size.class).create(raven).size = 1;
      world.getMapper(com.riiablo.engine.server.component.CofReference.class).create(raven).mode =
          Engine.Monster.MODE_NU;
      world.getMapper(com.riiablo.engine.server.component.MovementModes.class).create(raven)
          .set(Engine.Monster.MODE_NU, Engine.Monster.MODE_WL, Engine.Monster.MODE_RN);
      int target = world.create();
      initUnit(world, target, 11, 10);
      com.riiablo.codec.excel.MonStats.Entry victimRow = Riiablo.files.monstats.get(0);
      world.getMapper(Monster.class).create(target).set(victimRow,
          Riiablo.files.monstats2.get(victimRow.MonStatsEx));
      world.getMapper(com.riiablo.engine.server.component.CofReference.class).create(target)
          .set(victimRow.Code, Engine.Monster.MODE_NU);
      world.getMapper(com.riiablo.engine.server.component.AnimData.class).create(target).numFrames = 512;
      world.getMapper(com.riiablo.engine.server.component.Class.class).create(target).type =
          com.riiablo.engine.server.component.Class.Type.MON;
      world.getMapper(com.riiablo.engine.server.component.Size.class).create(target).size = 1;
      Attributes victim = world.getMapper(AttributesWrapper.class).get(target).attrs;
      victim.base().put(Stat.hitpoints, 10000f);
      victim.base().put(Stat.maxhp, 10000f);
      victim.reset();
      world.process();
      com.riiablo.engine.server.ai.Raven ai = new com.riiablo.engine.server.ai.Raven(raven);
      world.getInjector().inject(ai);
      ai.initialize();
      for (int tick = 0; tick < 500 && !world.getMapper(
          com.riiablo.engine.server.component.Casting.class).has(raven); tick++) ai.update(1f / 25f);
      com.riiablo.engine.server.component.Casting cast = world.getMapper(
          com.riiablo.engine.server.component.Casting.class).get(raven);
      org.junit.jupiter.api.Assertions.assertNotNull(cast, "Raven AI must start its ordinary A1");
      assertEquals(target, cast.targetId);
      assertEquals(com.riiablo.skill.SkillCodes.attack, cast.skillId);
      assertEquals(10000 * 256, victim.get(Stat.hitpoints).encodedValues(),
          "starting A1 must not apply damage before its action keyframe");
      org.junit.jupiter.api.Assertions.assertNull(world.getMapper(UnitStates.class).get(target)
          .stateList.getState(StateId.DIMVISION), "starting A1 must not blind before the hit roll");
      cofs.setMode(raven, Engine.Monster.MODE_A1, true);
      // A reproducible successful hit, without replacing the combat resolver.
      world.getMapper(Monster.class).get(raven).rngState = seed;
      world.getSystem(EventSystem.class).dispatch(
          com.riiablo.engine.server.event.AnimDataKeyframeEvent.obtain(raven, Engine.KEYFRAME_ATK));
      if (!hit) {
        org.junit.jupiter.api.Assertions.assertNull(probe.applied);
        assertEquals(10000 * 256, victim.get(Stat.hitpoints).encodedValues());
        org.junit.jupiter.api.Assertions.assertNull(world.getMapper(UnitStates.class).get(target)
            .stateList.getState(StateId.DIMVISION), "a missed peck must not trigger blindness");
        return;
      }
      org.junit.jupiter.api.Assertions.assertNotNull(probe.applied,
          "production A1 keyframe must reach DamageAppliedEvent");
      assertEquals(raven, probe.applied.attacker);
      assertEquals(target, probe.applied.victim);
      int lost = 10000 * 256 - victim.get(Stat.hitpoints).encodedValues();
      assertEquals(probe.applied.lifeLost, lost / 256f);
      assertEquals(Engine.Monster.MODE_NU, world.getMapper(
          com.riiablo.engine.server.component.CofReference.class).get(target).mode,
          "the high-HP control must receive soft hit, not unconditional GH");
      assertEquals(19, world.getMapper(com.riiablo.engine.server.component.NativeAiState.class)
          .get(target).previousMode);
      // Native Raven level-one packets occupy the 2..4 physical range.
      int min = level + 1, max = level + 3;
      assertTrue(lost >= min * 256 && lost <= max * 256,
          "skill damage must survive summon initialization and the real A1 pipeline: " + lost);
      assertEquals(0f, probe.request.fireDamage);
      UnitState blindness = world.getMapper(UnitStates.class).get(target).stateList
          .getState(StateId.DIMVISION);
      org.junit.jupiter.api.Assertions.assertNotNull(blindness,
          "a successful Raven A1 must run its blind-target item event");
      assertEquals(SkillId.DIM_VISION, blindness.skillId);
      assertEquals(raven, blindness.sourceEntityId);
      assertTrue(blindness.duration > 0 && blindness.needsSync);
      assertBlindStateReachesClientAndExpires(world, target, blindness);
    } finally {
      world.dispose();
      Riiablo.engine = previous;
    }
  }

  private static void syncBlindState(World authority, int target, World client, int replica) {
    StateSerializer serializer = new StateSerializer();
    FlatBufferBuilder builder = new FlatBufferBuilder(256);
    int stateOffset = serializer.putData(builder, authority.getMapper(UnitStates.class).get(target));
    int types = EntitySync.createComponentTypeVector(builder, new byte[] {ComponentP.StateP});
    int components = EntitySync.createComponentVector(builder, new int[] {stateOffset});
    builder.finish(EntitySync.createEntitySync(builder, target, 0, 0, types, components,
        0L, 0L, 0L, 0L, 0L, -1));
    serializer.getData(EntitySync.getRootAsEntitySync(builder.dataBuffer()), 0,
        client.getMapper(UnitStates.class).get(replica));
  }

  private static void assertBlindStateReachesClientAndExpires(World authority, int target, UnitState blind) {
    BlindOverlays overlays = new BlindOverlays();
    World client = new World(new WorldConfigurationBuilder().with(new EventSystem(),
        new CofManager(), overlays, new com.riiablo.presentation.StateOverlaySystem())
        .build().register("clientContext", com.riiablo.presentation.ClientGameplayContext.fromGlobals()));
    try {
      int replica = client.create();
      client.getMapper(UnitStates.class).create(replica).init(replica);
      syncBlindState(authority, target, client, replica);
      UnitState received = client.getMapper(UnitStates.class).get(replica).stateList.getState(StateId.DIMVISION);
      org.junit.jupiter.api.Assertions.assertNotNull(received);
      assertEquals(blind.sourceEntityId, received.sourceEntityId);
      assertEquals(blind.skillId, received.skillId);
      assertEquals(blind.level, received.level);
      assertEquals(blind.duration, received.duration);
      client.process();
      org.junit.jupiter.api.Assertions.assertNull(overlays.persistent,
          "the persistent marker must wait for the initial curse animation");
      assertEquals(69, Riiablo.files.Overlay.index(overlays.activation),
          "native capture starts with cast overlay 69");
      client.process();
      org.junit.jupiter.api.Assertions.assertNull(overlays.persistent,
          "an active cast animation must continue to hold the persistent marker");
      overlays.playing = false;
      client.process();
      assertEquals(129, Riiablo.files.Overlay.index(overlays.persistent),
          "native capture retains Dim Vision overlay 129 after the cast finishes");
      int frames = blind.duration + 1;
      authority.setDelta(1f / 25f);
      for (int i = 0; i < frames; i++) authority.process();
      org.junit.jupiter.api.Assertions.assertNull(authority.getMapper(UnitStates.class).get(target)
          .stateList.getState(StateId.DIMVISION));
      syncBlindState(authority, target, client, replica);
      client.process();
      org.junit.jupiter.api.Assertions.assertNull(overlays.persistent,
          "the client must clear the overlay when authority removes blindness");
    } finally {
      client.dispose();
    }
  }

  @com.artemis.annotations.All(com.riiablo.presentation.component.Overlay.class)
  private static final class BlindOverlays extends com.riiablo.presentation.OverlayManager {
    String persistent, activation;
    boolean playing;
    @Override public void setPersistent(int entity, int state, String overlay) {
      if (state == StateId.DIMVISION) persistent = overlay;
    }
    @Override public void clearPersistent(int entity, int state) {
      if (state == StateId.DIMVISION) persistent = null;
    }
    @Override public void set(int entity, String overlay) { activation = overlay; playing = true; }
    @Override public boolean isOneShotActive(int entity, String overlay) { return playing; }
  }

  @Test
  void allNativeDruidSummonFunctionsCreateOwnedEntities() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("druid", (byte) Riiablo.DRUID);
      world.getMapper(Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(10, 10);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes(20, 100);

      int[] ids = {SkillId.RAVEN, SkillId.POISON_CREEPER, SkillId.OAK_SAGE,
          SkillId.SUMMON_SPIRIT_WOLF, SkillId.CARRION_VINE,
          SkillId.HEART_OF_WOLVERINE, SkillId.SUMMON_DIRE_WOLF,
          SkillId.SOLAR_CREEPER, SkillId.SPIRIT_OF_BARBS, SkillId.SUMMON_GRIZZLY};
      for (int id : ids) {
        com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(id);
        data.setSkillLevel(id, 8);
        world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
            owner, id, Engine.INVALID_ENTITY, new Vector2(12, 10), skill.srvdofunc, 0));
        assertEquals(owner, world.getMapper(SummonedPet.class).get(factory.lastEntity).ownerId);
        assertEquals(PetType.canonical(skill.pettype), factory.lastPetType);
      }
      assertEquals(ids.length, factory.created);
      assertTrue(factory.lastMaximum >= 1);
    } finally {
      world.dispose();
    }
  }

  @Test
  void vinesInstallNativeSumSkillLevelAndVineBeastStateWithoutAuraProjection() {
    int[] vineSkills = {SkillId.POISON_CREEPER, SkillId.CARRION_VINE, SkillId.SOLAR_CREEPER};
    for (int id : vineSkills) {
      com.riiablo.codec.excel.Skills.Entry vine = Riiablo.files.skills.get(id);
      assertEquals(StateId.VINE_BEAST, DruidSkills.getSummonAuraState(vine));
      assertTrue(DruidSkills.getSummonAuraSkill(Riiablo.files, vine) == null,
          "Vines use the Vine Attack SumSkill, not a party aura row");
      assertEquals("Vine Attack", vine.sumskill[0]);
      com.riiablo.codec.excel.Skills.Entry attack = Riiablo.files.skills.get(vine.sumskill[0]);
      assertTrue(attack != null && attack.Id >= 0);
      assertEquals(8, DruidSkills.getSummonGrantedSkillLevel(Riiablo.files, vine, 8, attack.Id, name -> 0),
          "D2MOO SumSk1Calc=lvl must preserve the parent vine skill level");
    }
  }

  @Test
  void summonBaseLevelUsesNativeCalc2InsteadOfOwnerLevelHeuristic() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("druid-level", (byte) Riiablo.DRUID);
      data.setSkillLevel(SkillId.RAVEN, 8);
      world.getMapper(Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(10, 10);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes(20, 100);

      com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(SkillId.RAVEN);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, SkillId.RAVEN, Engine.INVALID_ENTITY, new Vector2(10, 10),
          skill.srvdofunc, skill.cltdofunc));

      int expected = Math.max(1, SkillFormula.evaluate(skill.calc2, skill, 8,
          name -> 0, name -> Riiablo.files.skills.get(name), 20));
      int actual = world.getMapper(AttributesWrapper.class).get(factory.lastEntity)
          .attrs.get(Stat.level).asInt();
      assertEquals(expected, actual,
          "D2MOO SrvDo114 uses Skills.txt Calc2 for the summoned base level");
    } finally {
      world.dispose();
    }
  }

  @Test
  void spiritAuraProjectsToOwnerWithPetSourceIdentity() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new StateUpdater(), factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("druid-aura", (byte) Riiablo.DRUID);
      data.setSkillLevel(SkillId.OAK_SAGE, 8);
      world.getMapper(Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(10, 10);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes(20, 100);
      world.getMapper(UnitStates.class).create(owner).init(owner);

      com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(SkillId.OAK_SAGE);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, SkillId.OAK_SAGE, Engine.INVALID_ENTITY, new Vector2(10, 10),
          skill.srvdofunc, skill.cltdofunc));
      int source = factory.lastEntity;

      world.setDelta(1f / 25f);
      world.process();

      UnitState aura = world.getMapper(UnitStates.class).get(owner).stateList
          .getStateLayer(StateId.OAKSAGE, source, SkillId.OAK_SAGE);
      UnitState sourceAura = world.getMapper(UnitStates.class).get(source).stateList
          .getState(StateId.OAKSAGECONTROL);
      assertTrue(aura != null,
          "Oak Sage must project a source-owned aura layer to its owner");
      assertTrue(sourceAura != null, "the source carries the native controller state");
      assertTrue(!world.getMapper(UnitStates.class).get(source).stateList.hasState(StateId.OAKSAGE),
          "the aura source must not receive its own beneficiary overlay");
      assertEquals(source, aura.sourceEntityId);
      assertEquals(SkillId.OAK_SAGE, aura.skillId);
      assertTrue(aura.maxLifeModifier > 0,
          "Oak Sage owner layer must carry its native max-life modifier source="
              + (sourceAura != null ? sourceAura.maxLifeModifier : -1)
              + " skillStats=" + skill.aurastat[0] + " calc=" + skill.aurastatcalc[0]
              + " auraState=" + skill.aurastate);
    } finally {
      world.dispose();
    }
  }

  @Test
  void spiritAurasUseLinkedNativeAuraRowsAndRevokeWhenOutOfRangeOrDismissed() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new StateUpdater(), factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("druid-aura-values", (byte) Riiablo.DRUID);
      world.getMapper(Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(10, 10);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes(20, 100);
      world.getMapper(UnitStates.class).create(owner).init(owner);

      int[] skills = {SkillId.OAK_SAGE, SkillId.HEART_OF_WOLVERINE, SkillId.SPIRIT_OF_BARBS};
      int[] sources = new int[skills.length];
      int noAuraPet = createSummon(world, owner, 10, 10);
      com.riiablo.codec.excel.MonStats.Entry noAura = new com.riiablo.codec.excel.MonStats.Entry();
      noAura.noAura = true;
      world.getMapper(Monster.class).create(noAuraPet).monstats = noAura;
      for (int i = 0; i < skills.length; i++) {
        data.setSkillLevel(skills[i], 8);
        com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(skills[i]);
        world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
            owner, skills[i], Engine.INVALID_ENTITY, new Vector2(10, 10),
            skill.srvdofunc, skill.cltdofunc));
        sources[i] = factory.lastEntity;
      }
      world.setDelta(1f / 25f);
      world.process();

      UnitStates ownerStates = world.getMapper(UnitStates.class).get(owner);
      UnitState oak = ownerStates.stateList.getStateLayer(
          StateId.OAKSAGE, sources[0], SkillId.OAK_SAGE);
      UnitState wolverine = ownerStates.stateList.getStateLayer(
          StateId.WOLVERINE, sources[1], SkillId.HEART_OF_WOLVERINE);
      UnitState barbs = ownerStates.stateList.getStateLayer(
          StateId.BARBS, sources[2], SkillId.SPIRIT_OF_BARBS);
      int[] beneficiaryStates = {StateId.OAKSAGE, StateId.WOLVERINE, StateId.BARBS};
      int[] controllerStates = {StateId.OAKSAGECONTROL, StateId.WOLVERINECONTROL, StateId.BARBSCONTROL};
      for (int i = 0; i < sources.length; i++) {
        com.riiablo.engine.server.state.StateList sourceStates =
            world.getMapper(UnitStates.class).get(sources[i]).stateList;
        assertTrue(sourceStates.hasState(controllerStates[i]));
        assertTrue(!sourceStates.hasState(beneficiaryStates[i]), "source is excluded from its own aura scan");
        assertTrue(!world.getMapper(UnitStates.class).get(noAuraPet).stateList.hasState(beneficiaryStates[i]),
            "native noAura monsters cannot receive the projected aura");
      }
      assertEquals(65, oak.maxLifeModifier,
          "Oak Sage must use linked Oak Sage Aura ln34");
      assertEquals(74, wolverine.resolvedAttackModifier(),
          "Wolverine must use linked aura attack ln34");
      assertEquals(69, wolverine.resolvedDamageModifier(),
          "Wolverine must use linked aura damage ln56");
      assertEquals(120, barbs.runtimeValue,
          "Spirit of Barbs must use linked aura thorns ln34");
      assertEquals(120, barbs.getStatContributionValue(Stat.thorns_percent),
          "the projected aura must carry the stat consumed by melee reflection");

      int attacker = world.create();
      world.getMapper(Monster.class).create(attacker);
      Attributes attackerAttrs = attributes(20, 100);
      world.getMapper(AttributesWrapper.class).create(attacker).attrs = attackerAttrs;
      world.getSystem(EventSystem.class).dispatch(
          com.riiablo.engine.server.event.DamageEvent.obtainMelee(attacker, owner, 10, 10));
      assertEquals(88f, attackerAttrs.get(Stat.hitpoints).asFixed(), .001f,
          "native Spirit of Barbs reflects 120% of the physical melee packet exactly once");

      world.getMapper(Position.class).get(sources[1]).position.set(100, 100);
      world.process();
      assertTrue(ownerStates.stateList.getStateLayer(
          StateId.WOLVERINE, sources[1], SkillId.HEART_OF_WOLVERINE) == null,
          "moving the spirit outside its aura range must revoke its owner layer");

      world.delete(sources[2]);
      world.process();
      assertTrue(ownerStates.stateList.getStateLayer(
          StateId.BARBS, sources[2], SkillId.SPIRIT_OF_BARBS) == null,
          "removing the spirit must revoke its source-owned layer");
      world.getSystem(EventSystem.class).dispatch(
          com.riiablo.engine.server.event.DamageEvent.obtainMelee(attacker, owner, 10, 10));
      assertEquals(88f, attackerAttrs.get(Stat.hitpoints).asFixed(), .001f,
          "a dismissed spirit must stop reflecting damage");
    } finally {
      world.dispose();
    }
  }

  @Test
  void spiritAuraCannotCrossMapZoneEvenWhenCoordinatesOverlap() {
    RecordingFactory factory = new RecordingFactory();
    com.riiablo.map.Map map = new com.riiablo.map.Map(0, 0);
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new StateUpdater(), factory)
        .build().register("factory", factory).register("map", map));
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("druid-zone", (byte) Riiablo.DRUID);
      data.setSkillLevel(SkillId.OAK_SAGE, 8);
      world.getMapper(Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(10, 10);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes(20, 100);
      world.getMapper(UnitStates.class).create(owner).init(owner);

      com.riiablo.map.Map.Zone ownerZone = new com.riiablo.map.Map.Zone();
      com.riiablo.map.Map.Zone otherZone = new com.riiablo.map.Map.Zone();
      world.getMapper(MapWrapper.class).create(owner).set(map, ownerZone);

      com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(SkillId.OAK_SAGE);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, SkillId.OAK_SAGE, Engine.INVALID_ENTITY, new Vector2(10, 10),
          skill.srvdofunc, skill.cltdofunc));
      int source = factory.lastEntity;
      world.getMapper(MapWrapper.class).create(source).set(map, ownerZone);
      world.setDelta(1f / 25f);
      world.process();
      assertTrue(world.getMapper(UnitStates.class).get(owner).stateList
          .getStateLayer(StateId.OAKSAGE, source, SkillId.OAK_SAGE) != null);

      // Keep the same coordinates but move only the spirit to another zone.
      world.getMapper(MapWrapper.class).get(source).set(map, otherZone);
      world.process();
      assertTrue(world.getMapper(UnitStates.class).get(owner).stateList
          .getStateLayer(StateId.OAKSAGE, source, SkillId.OAK_SAGE) == null,
          "a spirit aura must be revoked when source and owner are in different zones");
    } finally {
      world.dispose();
    }
  }

  @Test
  void spiritAuraTargetsOnlyOwnerPartyMercenaryAndSummonThenRevokesOnDeath() {
    RecordingFactory factory = new RecordingFactory();
    PartyManager parties = new PartyManager();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new StateUpdater(), factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0))
        .register("partyManager", parties));
    try {
      int owner = createPlayer(world, "aura-owner", 10, 10);
      int partyAlly = createPlayer(world, "aura-ally", 11, 10);
      int outsider = createPlayer(world, "aura-outsider", 12, 10);
      int ownerMerc = createMercenary(world, owner, 13, 10);
      int ownerSummon = createSummon(world, owner, 14, 10);
      int outsiderSummon = createSummon(world, outsider, 15, 10);
      int partyMerc = createMercenary(world, partyAlly, 11, 11);
      int partySummon = createSummon(world, partyAlly, 12, 11);
      short party = parties.createParty(owner);
      assertTrue(party >= 0 && parties.joinParty(party, partyAlly));

      CharData ownerData = world.getMapper(Player.class).get(owner).data;
      ownerData.setSkillLevel(SkillId.OAK_SAGE, 8);
      com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(SkillId.OAK_SAGE);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, SkillId.OAK_SAGE, Engine.INVALID_ENTITY, new Vector2(10, 10),
          skill.srvdofunc, skill.cltdofunc));
      int source = factory.lastEntity;
      world.setDelta(1f / 25f);
      world.process();

      assertAuraLayer(world, owner, source, true);
      assertAuraLayer(world, partyAlly, source, true);
      assertAuraLayer(world, ownerMerc, source, true);
      assertAuraLayer(world, ownerSummon, source, true);
      assertAuraLayer(world, partyMerc, source, true);
      assertAuraLayer(world, partySummon, source, true);
      assertAuraLayer(world, outsider, source, false);
      assertAuraLayer(world, outsiderSummon, source, false);

      life(world, source, 0f);
      world.process();
      assertAuraLayer(world, owner, source, false);
      assertAuraLayer(world, partyAlly, source, false);
      assertAuraLayer(world, ownerMerc, source, false);
      assertAuraLayer(world, ownerSummon, source, false);
      assertAuraLayer(world, partyMerc, source, false);
      assertAuraLayer(world, partySummon, source, false);
    } finally {
      world.dispose();
    }
  }

  @Test
  void spiritAuraSnapshotPreservesIndependentSourceLayersAcrossReconnect() {
    UnitStates authority = new UnitStates().init(77);
    UnitState first = authority.stateList.addStateLayer(
        StateId.OAKSAGE, 0, 8, 101, SkillId.OAK_SAGE);
    first.maxLifeModifier = 65;
    UnitState second = authority.stateList.addStateLayer(
        StateId.OAKSAGE, 0, 4, 202, SkillId.OAK_SAGE);
    second.maxLifeModifier = 35;

    StateSerializer serializer = new StateSerializer();
    FlatBufferBuilder builder = new FlatBufferBuilder(256);
    int stateOffset = serializer.putData(builder, authority);
    int typeOffset = EntitySync.createComponentTypeVector(
        builder, new byte[] {ComponentP.StateP});
    int componentOffset = EntitySync.createComponentVector(builder, new int[] {stateOffset});
    int root = EntitySync.createEntitySync(builder, 77, 0, 0, typeOffset, componentOffset,
        0L, 0L, 0L, 0L, 0L, -1);
    builder.finish(root);

    UnitStates replica = new UnitStates().init(77);
    serializer.getData(EntitySync.getRootAsEntitySync(builder.dataBuffer()), 0, replica);
    assertEquals(2, replica.stateList.size(),
        "reconnect must not collapse same-state spirit layers");
    UnitState restoredFirst = replica.stateList.getStateLayer(
        StateId.OAKSAGE, 101, SkillId.OAK_SAGE);
    UnitState restoredSecond = replica.stateList.getStateLayer(
        StateId.OAKSAGE, 202, SkillId.OAK_SAGE);
    assertTrue(restoredFirst != null && restoredSecond != null);
    assertEquals(65, restoredFirst.maxLifeModifier);
    assertEquals(35, restoredSecond.maxLifeModifier);
  }

  @Test
  void allThreeSpiritsReachPartyHirelingsAndPetsButNotOutsiders() {
    RecordingFactory factory = new RecordingFactory();
    PartyManager parties = new PartyManager();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new StateUpdater(), factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)).register("partyManager", parties));
    try {
      int owner = createPlayer(world, "spirits-owner", 10, 10);
      int ally = createPlayer(world, "spirits-ally", 11, 10);
      int outsider = createPlayer(world, "spirits-outsider", 12, 10);
      int merc = createMercenary(world, ally, 11, 11);
      int pet = createSummon(world, ally, 12, 11);
      int excluded = createSummon(world, outsider, 12, 12);
      assertTrue(parties.joinParty(parties.createParty(owner), ally));
      int[] skills = {SkillId.OAK_SAGE, SkillId.HEART_OF_WOLVERINE, SkillId.SPIRIT_OF_BARBS};
      int[] states = {StateId.OAKSAGE, StateId.WOLVERINE, StateId.BARBS};
      world.setDelta(1f / 25f);
      for (int i = 0; i < skills.length; i++) {
        world.getMapper(Player.class).get(owner).data.setSkillLevel(skills[i], 8);
        com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(skills[i]);
        world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
            owner, skills[i], Engine.INVALID_ENTITY, new Vector2(10, 10),
            skill.srvdofunc, skill.cltdofunc));
        int source = factory.lastEntity;
        world.process();
        for (int target : new int[] {ally, merc, pet}) {
          assertTrue(world.getMapper(UnitStates.class).get(target).stateList
              .getStateLayer(states[i], source, skills[i]) != null);
        }
        assertTrue(world.getMapper(UnitStates.class).get(excluded).stateList
            .getStateLayer(states[i], source, skills[i]) == null);
        world.delete(source);
        world.process();
        assertTrue(world.getMapper(UnitStates.class).get(pet).stateList
            .getStateLayer(states[i], source, skills[i]) == null);
      }
    } finally {
      world.dispose();
    }
  }

  @Test
  void legacySpiritAuraSnapshotWithoutSourceVectorsFallsBackSafely() {
    FlatBufferBuilder builder = new FlatBufferBuilder(256);
    int stateIds = StateP.createStateIdVector(builder,
        new short[] {(short) StateId.OAKSAGE, (short) StateId.OAKSAGE});
    int durations = StateP.createDurationVector(builder, new int[] {0, 0});
    int levels = StateP.createLevelVector(builder, new byte[] {4, 8});
    int maxLife = StateP.createMaxLifeModifierVector(builder, new short[] {35, 65});
    // Deliberately omit sourceEntityId/skillId and the newer optional vectors:
    // this is the shape emitted by a pre-source-layer client.
    int stateOffset = StateP.createStateP(builder, stateIds, durations, levels,
        0, 0, 0, 0, maxLife, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    int typeOffset = EntitySync.createComponentTypeVector(builder,
        new byte[] {ComponentP.StateP});
    int componentOffset = EntitySync.createComponentVector(builder,
        new int[] {stateOffset});
    int root = EntitySync.createEntitySync(builder, 77, 0, 0, typeOffset,
        componentOffset, 0L, 0L, 0L, 0L, 0L, -1);
    builder.finish(root);

    UnitStates replica = new UnitStates().init(77);
    new StateSerializer().getData(
        EntitySync.getRootAsEntitySync(builder.dataBuffer()), 0, replica);

    assertEquals(1, replica.stateList.size(),
        "legacy clients cannot represent independent source layers");
    UnitState restored = replica.stateList.getState(StateId.OAKSAGE);
    assertTrue(restored != null);
    assertEquals(8, restored.level);
    assertEquals(65, restored.maxLifeModifier);
    assertEquals(-1, restored.sourceEntityId);
    assertEquals(-1, restored.skillId);
  }

  @Test
  void reusedSpiritEntityIdCannotResurrectTheOldAuraLayer() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new StateUpdater(), factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int owner = createPlayer(world, "reuse-owner", 10, 10);
      CharData data = world.getMapper(Player.class).get(owner).data;
      data.setSkillLevel(SkillId.OAK_SAGE, 8);
      com.riiablo.codec.excel.Skills.Entry oak = Riiablo.files.skills.get(SkillId.OAK_SAGE);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, SkillId.OAK_SAGE, Engine.INVALID_ENTITY, new Vector2(10, 10),
          oak.srvdofunc, oak.cltdofunc));
      int oldSource = factory.lastEntity;
      world.setDelta(1f / 25f);
      world.process();
      assertAuraLayer(world, owner, oldSource, true);

      world.delete(oldSource);
      world.process();
      assertAuraLayer(world, owner, oldSource, false);

      int reused = world.create();
      assertEquals(oldSource, reused, "Artemis should reuse the released source id in this fixture");
      initUnit(world, reused, 10, 10);
      world.getMapper(SummonedPet.class).create(reused)
          .set(owner, "wolf", SkillId.HEART_OF_WOLVERINE, 4, false, 0);
      UnitState wolverine = world.getMapper(UnitStates.class).get(reused).stateList
          .addStateLayer(StateId.WOLVERINE, 0, 4, owner, SkillId.HEART_OF_WOLVERINE);
      wolverine.skillId = SkillId.HEART_OF_WOLVERINE;
      world.process();

      assertAuraLayer(world, owner, reused, false);
      UnitState newAura = world.getMapper(UnitStates.class).get(owner).stateList
          .getStateLayer(StateId.WOLVERINE, reused, SkillId.HEART_OF_WOLVERINE);
      assertTrue(newAura != null, "the replacement spirit may only publish its own aura");
    } finally {
      world.dispose();
    }
  }

  private static void assertAuraLayer(World world, int target, int source, boolean expected) {
    UnitState state = world.getMapper(UnitStates.class).get(target).stateList
        .getStateLayer(StateId.OAKSAGE, source, SkillId.OAK_SAGE);
    assertEquals(expected, state != null, "unexpected Oak Sage layer target=" + target);
  }

  private static int createPlayer(World world, String name, float x, float y) {
    int id = world.create();
    world.getMapper(Player.class).create(id).data =
        CharData.createRemote(name, (byte) Riiablo.DRUID);
    initUnit(world, id, x, y);
    return id;
  }

  private static int createMercenary(World world, int owner, float x, float y) {
    int id = world.create();
    world.getMapper(Mercenary.class).create(id).ownerId = owner;
    initUnit(world, id, x, y);
    return id;
  }

  private static int createSummon(World world, int owner, float x, float y) {
    int id = world.create();
    world.getMapper(SummonedPet.class).create(id)
        .set(owner, "wolf", SkillId.SUMMON_SPIRIT_WOLF, 1, false, 0);
    initUnit(world, id, x, y);
    return id;
  }

  private static void initUnit(World world, int id, float x, float y) {
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(1, 100);
    world.getMapper(UnitStates.class).create(id).init(id);
  }

  private static void life(World world, int id, float value) {
    world.getMapper(AttributesWrapper.class).get(id).attrs.get(Stat.hitpoints).set(value);
  }

  private static Attributes attributes(int level, float hp) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.level, level);
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, hp);
    attrs.reset();
    return attrs;
  }

  private static final class RecordingFactory extends EntityFactory {
    int created;
    int lastEntity = Engine.INVALID_ENTITY;
    int lastMaximum;
    String lastPetType;

    @Override public int createSummonedPet(int ownerId,
        com.riiablo.codec.excel.MonStats.Entry summon, String petType, int skillId,
        int skillLevel, int petMax, boolean passive, int durationFrames, float x, float y) {
      created++;
      lastMaximum = petMax;
      lastPetType = PetType.canonical(petType);
      lastEntity = world.create();
      world.getMapper(Monster.class).create(lastEntity).monstats = summon;
      world.getMapper(Position.class).create(lastEntity).position.set(x, y);
      world.getMapper(AttributesWrapper.class).create(lastEntity).attrs = attributes(1, 10);
      world.getMapper(UnitStates.class).create(lastEntity).init(lastEntity);
      world.getMapper(SummonedPet.class).create(lastEntity)
          .set(ownerId, lastPetType, skillId, skillLevel, passive, durationFrames);
      return lastEntity;
    }

    @Override public int createPlayer(CharData data, Vector2 position) { return Engine.INVALID_ENTITY; }
    @Override public int createDynamicObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMonster(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createWarp(int index, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createItem(com.riiablo.item.Item item, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMissile(int id, Vector2 angle, Vector2 position) { return Engine.INVALID_ENTITY; }
  }
}
