package com.riiablo.engine.server.ai;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;

import com.badlogic.gdx.ai.fsm.StateMachine;
import com.badlogic.gdx.ai.msg.Telegram;

import com.badlogic.gdx.math.Vector2;

import com.riiablo.Riiablo;
import com.riiablo.engine.Engine;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.NativeAiCommand;
import com.riiablo.engine.server.component.NativeTargeting;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Running;
import com.riiablo.engine.server.component.Sequence;

/**
 * Fallen AI implementation matching D2MOD's AITHINK_Fn006_Fallen logic.
 * 
 * D2MOD AI Parameters:
 * - params[0] = FALLEN_AI_PARAM_COMMAND_ATTACK_CHANCE_PCT (command minions to attack chance)
 * - params[1] = FALLEN_AI_PARAM_APPROACH_DISTANCE (approach distance)
 * - params[2] = FALLEN_AI_PARAM_ATTACK_CHANCE_PCT (attack chance)
 * - params[3] = FALLEN_AI_PARAM_ATTACK1_OR_2_CHANCE_PCT (A1 vs A2 chance)
 */
public class Fallen extends AI {
  private static final Logger log = LogManager.getLogger(Fallen.class);
  
  enum State implements com.badlogic.gdx.ai.fsm.State<Integer> {
    IDLE,
    WANDER,
    APPROACH,
    ATTACK,
    ESCAPE,  // Escaping from nearby corpses
    DEAD;

    @Override public void enter(Integer entityId) {}
    @Override public void update(Integer entityId) {}
    @Override public void exit(Integer entityId) {}
    @Override public boolean onMessage(Integer entityId, Telegram telegram) {
      return false;
    }
  }

  protected ComponentMapper<Class> mClass;
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<com.riiablo.engine.server.component.Sequence> mSequence;
  protected ComponentMapper<com.riiablo.engine.server.component.Velocity> mVelocity;
  protected ComponentMapper<Running> mRunning;
  protected ComponentMapper<NativeAiCommand> mNativeAiCommand;

  private EntitySubscription monsterEntities;  // All monsters to check for death animation

  final Vector2 tmpVec2 = new Vector2();
  final Vector2 tmpVec2_2 = new Vector2();  // Second temporary vector for escape calculation

  final StateMachine<Integer, State> stateMachine;
  float nextAction;
  float time;
  
  // AI state tracking (similar to D2MOD's dwAiParam[0])
  boolean aiParam0 = false;  // Used to track command state

  public Fallen(int entityId) {
    super(entityId);
    stateMachine = new CheckpointStateMachine<>(entityId, State.IDLE);
  }

  @Override
  public void kill() {
    if (stateMachine.getCurrentState() == State.DEAD) return;
    if (mNativeAiCommand.has(entityId)) mNativeAiCommand.remove(entityId);
    pathfinder.findPath(entityId, null);
    stateMachine.changeState(State.DEAD);
    mSequence.create(entityId).sequence(Engine.Monster.MODE_DT, Engine.Monster.MODE_DD);
    playSound(monsound + "_death_1");
  }

  @Override
  public void initialize() {
    super.initialize();
    monsterEntities = Riiablo.engine.getAspectSubscriptionManager().get(Aspect
            .all(Class.class, Monster.class, Position.class, CofReference.class));
  }

  /**
   * Check for a monster in death animation (MODE_DT) at native distance < 15.
   * D2MOD: Checks adjacent rooms for corpses in MONMODE_DEATH within 15 tiles.
   * D2MOD checks ppRoomList[j]->nLastDeadGUIDs[i] which are monsters in MODE_DT.
   */
  private boolean checkNearbyCorpse() {
    if (!mPosition.has(entityId)) return false;
    Vector2 entityPos = mPosition.get(entityId).position;
    
    // Native-topology maps also require membership in the room's four killed
    // GUID slots. Detached/legacy fixtures retain coordinate-only behavior.
    IntBag monsters = monsterEntities.getEntities();
    for (int i = 0, size = monsters.size(); i < size; i++) {
      int monsterId = monsters.get(i);
      if (monsterId == entityId) continue; // Skip self
      if (!mPosition.has(monsterId)) continue;
      if (!mMonster.has(monsterId)) continue;
      if (!isDeathInRoomScope(monsterId, entityPos)) continue;
      
      // Native D2 checks the room's last-dead list without restricting the
      // corpse to the Fallen AI class. Any nearby monster death can trigger
      // the flee reaction (the room list itself is the scope limiter).
      // Check if monster is in death animation (MODE_DT)
      if (mCofReference.has(monsterId)) {
        byte mode = mCofReference.get(monsterId).mode;
        if (mode == Engine.Monster.MODE_DT) {
          Vector2 corpsePos = mPosition.get(monsterId).position;
          float distance = nativeAiDistance(entityPos, corpsePos);
          if (distance < 15f) {
            // Debug log disabled
            // log.debug("Fallen {} detected nearby Fallen corpse: monsterId={}, distance={}", 
            //     entityId, monsterId, String.format("%.2f", distance));
            return true;
          }
        }
      }
    }
    return false;
  }

  /** Native ppRoomList scope; detached fixtures retain coordinate-only checks. */
  private boolean isDeathInRoomScope(int corpseId, Vector2 origin) {
    if (!mMapWrapper.has(entityId) || !mMapWrapper.has(corpseId)) return true;
    MapWrapper source = mMapWrapper.get(entityId);
    MapWrapper corpse = mMapWrapper.get(corpseId);
    if (source.map != null && corpse.map != null && source.map != corpse.map) return false;
    if (source.zone == null || corpse.zone == null) return true;
    if (source.zone != corpse.zone) return false;
    if (!source.zone.hasNativeRoomTopology()) return true;
    Vector2 position = mPosition.get(corpseId).position;
    if (!source.zone.areRoomsAdjacent(origin.x, origin.y, position.x, position.y)) return false;
    com.riiablo.map.Map.RoomEx room = source.zone.findRoomEx(position.x, position.y);
    com.riiablo.engine.server.CofManager modes = Riiablo.engine.getSystem(
        com.riiablo.engine.server.CofManager.class);
    return room != null && modes != null && modes.isRecentRoomDeath(room, corpseId);
  }

  /** Use spawn ownership; nearby independent packs must never elect each other. */
  private boolean isLeader() {
    return monster.minionOwnerId == entityId
        || (monster.minionOwnerId < 0 && monster.nativePackLeader);
  }

  private boolean isFallenPackMember(int candidate) {
    if (!mMonster.has(candidate) || !mPosition.has(candidate)) return false;
    Monster value = mMonster.get(candidate);
    return value.monstats != null && "Fallen".equalsIgnoreCase(value.monstats.AI)
        && (!mNativeUnitFlags.has(candidate)
            || NativeTargeting.canBeAttacked(mNativeUnitFlags.get(candidate)));
  }

  private boolean sameNativeZone(int candidate) {
    if (!mMapWrapper.has(entityId) || !mMapWrapper.has(candidate)) return true;
    return mMapWrapper.get(entityId).zone == mMapWrapper.get(candidate).zone;
  }

  private int commandMinions(int targetId) {
    int commanded = 0;
    IntBag candidates = monsterEntities.getEntities();
    for (int i = 0, size = candidates.size(); i < size; i++) {
      int candidate = candidates.get(i);
      if (!isFallenPackMember(candidate) || !sameNativeZone(candidate)) continue;
      if (candidate != entityId && mMonster.get(candidate).minionOwnerId != entityId) continue;
      mNativeAiCommand.create(candidate).copy(
          Riiablo.engine, entityId, NativeAiCommand.ATTACK, targetId);
      commanded++;
    }
    log.info("[FALLEN_COMMAND] phase=attack owner={} target={} members={}",
        entityId, targetId, commanded);
    return commanded;
  }

  private boolean executeCommand(int targetId, Vector2 targetPos, boolean inCombat) {
    if (!mNativeAiCommand.has(entityId)) return false;
    NativeAiCommand command = mNativeAiCommand.get(entityId);
    // Fn006 command 1 carries an action, not a bound target or issuer
    // lifetime. Use this think's already validated target/combat result.
    // Do not dereference the diagnostic ids stored when the order was copied.
    if (!command.isAttack()) {
      if (!command.pop()) mNativeAiCommand.remove(entityId);
      return false;
    }
    if (!inCombat) {
      if (!moveTo(targetPos, false, 0, true, targetId)) {
        if (!command.pop()) mNativeAiCommand.remove(entityId);
      }
      stateMachine.changeState(State.APPROACH);
      return true;
    }
    if (!aiRandomBoolean(params[2] / 100f)) {
      time = 5f * com.riiablo.codec.Animation.FRAME_DURATION;
      return true;
    }
    // Fn006 keeps command 1 across successful attacks. It is released by
    // invalidation or a failed approach, not consumed as a one-shot action.
    aiParam0 = false;
    stopForAttack();
    lookAt(targetId);
    stateMachine.changeState(State.ATTACK);
    byte mode = aiRandomBoolean(params[3] / 100f)
        ? Engine.Monster.MODE_A2 : Engine.Monster.MODE_A1;
    mSequence.create(entityId).sequence(mode, Engine.Monster.MODE_NU);
    mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
    playSound(monsound + "_attack_1");
    time = aiRandom(1f, 2);
    return true;
  }

  /**
   * Check if monster is in special AI state (sub_6FCF2E70).
   * D2MOD: Returns true if AI state == 3 || AI state == 19.
   * For now, we'll use a simplified check.
   */
  private boolean checkSpecialAiState() {
    com.riiablo.engine.server.component.NativeAiState memory = Riiablo.engine.getMapper(
        com.riiablo.engine.server.component.NativeAiState.class).get(entityId);
    return memory != null && memory.isUncontrolled();
  }

  /**
   * Attempt to escape from nearby corpse.
   * D2MOD: D2GAME_AICORE_Escape_6FCD0560 with distance 12.
   * D2MOD: AITACTICS_SetVelocity(pUnit, 0, 50, 0) - sets velocity parameter to 50 (speed modifier)
   * D2MOD: D2GAME_AICORE_Escape uses MONMODE_WALK (not MONMODE_RUN), so escape keeps walk animation
   * The velocity parameter (50) is used to make the path movement faster, but animation stays WL
   * 
   * In riiablo: We keep WL mode (no Running component) to match D2MOD behavior.
   * This avoids FARNHTH (Fallen Run) COF lookup failures since D2 table doesn't have that entry.
   */
  private boolean tryEscape(int targetId) {
    if (targetId == Engine.INVALID_ENTITY) {
      // Debug log disabled
      // log.debug("Fallen {} cannot escape: no target", entityId);
      return false;
    }
    if (!mPosition.has(targetId)) {
      // Debug log disabled
      // log.debug("Fallen {} cannot escape: target {} has no position", entityId, targetId);
      return false;
    }
    if (!mVelocity.has(entityId)) {
      // Debug log disabled
      // log.debug("Fallen {} cannot escape: no velocity component", entityId);
      return false;
    }
    
    Vector2 entityPos = mPosition.get(entityId).position;
    Vector2 targetPos = mPosition.get(targetId).position;
    
    // The shipped 1.10f Escape routine (D2Game RVA 0xA0560) compares
    // integer coordinates independently and adds sign(delta) * 12 on each
    // axis. A normalized vector shortens diagonal escape destinations and
    // invents a fractional direction when native coordinates are aligned.
    int x = (int) entityPos.x, y = (int) entityPos.y;
    Vector2 escapePos = tmpVec2_2.set(
        x + Integer.compare(x, (int) targetPos.x) * 12,
        y + Integer.compare(y, (int) targetPos.y) * 12);
    
    // Debug log disabled
    // log.info("Fallen {} attempting to escape: target={}, escapeDistance={}, escapePos=({}, {})", 
    //     entityId, targetId, String.format("%.2f", escapeDistance), 
    //     String.format("%.2f", escapePos.x), String.format("%.2f", escapePos.y));
    
    // D2GAME_AICORE_Escape uses MONMODE_WALK with a temporary +50%
    // velocity stat and asks pathfinding for a collision-safe fallback.
    return moveTo(escapePos, false, 50, true, Engine.INVALID_ENTITY);
  }

  /**
   * Check if monster is in combat (within melee range).
   * D2MOD: bCombat = UNITS_IsInMeleeRange(pUnit, pTarget, 0)
   * D2MOD: UNITS_GetMeleeRange(pUnit1) + nRangeBonus + 1 >= nDistance
   * Note: D2MOD uses MeleeRng + 1, and also checks collision
   */
  private boolean isInCombat(int targetId) {
    return isInNativeMeleeRange(targetId);
  }

  @Override
  public void update(float delta) {
    stateMachine.update();
    if (stateMachine.getCurrentState() == State.DEAD) {
      return;
    }

    nextAction -= delta;
    // The native escape action owns movement until its path completes;
    // neither the corpse reaching DD nor a think timer replaces that path.
    if (stateMachine.getCurrentState() == State.ESCAPE) {
      if (mPathfind.has(entityId)) return;
      if (mVelocity.has(entityId)) mVelocity.get(entityId).clearModeSpeedBonus();
      stateMachine.changeState(State.IDLE);
      time = 0;
    }
    time -= delta;
    if (time > 0) {
      return;
    }

    time = SLEEP;

    // D2MOD: Check if in death animation, return early
    if (mCofReference.has(entityId)) {
      byte mode = mCofReference.get(entityId).mode;
      if (mode == Engine.Monster.MODE_DT || mode == Engine.Monster.MODE_DD) {
        return;
      }
    }

    // D2MOD: Check for nearby corpses and escape if found
    // This check happens every AI update, so it will catch nearby Fallen deaths quickly
    if (checkNearbyCorpse()) {
      aiParam0 = true;  // Set flag like D2MOD's dwAiParam[0] = 1
      if (mNativeAiCommand.has(entityId)) {
        NativeAiCommand command = mNativeAiCommand.get(entityId);
        if (!command.pop()) mNativeAiCommand.remove(entityId);
      }
      
      // Find target to escape from (player)
      float[] escapeDistance = { Float.MAX_VALUE };
      int targetId = findNearestTargetWithAidist(escapeDistance);
      
      // D2MOD: AITACTICS_SetVelocity(pUnit, 0, 50, 0) and D2GAME_AICORE_Escape
      // Debug log disabled
      // log.info("Fallen {} detected nearby corpse, attempting to escape", entityId);
      if (tryEscape(targetId)) {
        // Remove Sequence component if exists to allow VelocityModeChanger to set walk/run mode
        if (mSequence.has(entityId)) {
          Sequence seq = mSequence.get(entityId);
          if (seq.mode1 == Engine.Monster.MODE_NU && seq.mode2 == Engine.Monster.MODE_NU) {
            mSequence.remove(entityId);
          }
        }
        stateMachine.changeState(State.ESCAPE);
        // 5% chance to play sound (1/20)
        if (aiRandom(20) < 1) {
          // Use flee sounds (fallen_flee_1 to fallen_flee_5) instead of attack sound
          int fleeSoundIndex = aiRandom(1, 5);
          playSound(monsound + "_flee_" + fleeSoundIndex);
        }
        // Movement completion releases ESCAPE above. Native Fn006 does not
        // roll an additional random 1..2 second cooldown after Escape.
        time = 0;
        // Debug log disabled
        // log.info("Fallen {} escape state activated", entityId);
        return;
      } else {
        // Debug log disabled
        // log.warn("Fallen {} failed to escape from nearby corpse", entityId);
      }
    }

    // D2MOD: If not in NEUTRAL mode, go to NEUTRAL
    // But only if not moving (no Pathfind component) to avoid interfering with movement animations
    if (mCofReference.has(entityId) && !mPathfind.has(entityId)) {
      byte mode = mCofReference.get(entityId).mode;
      if (mode != Engine.Monster.MODE_NU) {
        // Set to neutral mode (via sequence)
        mSequence.create(entityId).sequence(Engine.Monster.MODE_NU, Engine.Monster.MODE_NU);
        time = 10f * com.riiablo.codec.Animation.FRAME_DURATION;  // 10 frames idle
        return;
      }
    }

    // D2MOO resolves targets from the active target-node list and applies
    // aiDist/town/dead/map filtering. Never scan every player globally here:
    // that makes Fallen groups pursue a player through the Rogue Encampment.
    float[] nativeTargetDistance = { Float.MAX_VALUE };
    int targetId = findNearestTargetWithAidist(nativeTargetDistance);
    float targetDistance = nativeTargetDistance[0];

    if (targetId == Engine.INVALID_ENTITY) {
      // No target, idle behavior
      switch (stateMachine.getCurrentState()) {
        case IDLE:
          if (nextAction < 0) {
            pathfinder.findPath(entityId, null);
            stateMachine.changeState(State.WANDER);
          }
          break;
        case WANDER:
          if (!mPathfind.has(entityId)) {
            nextAction = aiRandom(0f, 1);
            stateMachine.changeState(State.IDLE);
          } else {
            Vector2 dst = tmpVec2.set(mPosition.get(entityId).position);
            dst.add(aiRandom(-5, 5), aiRandom(-5, 5));
            pathfinder.findPath(entityId, dst);
          }
          break;
        default:
          stateMachine.changeState(State.IDLE);
          break;
      }
      return;
    }

    Vector2 targetPos = mPosition.get(targetId).position;
    boolean bCombat = isInCombat(targetId);
    float meleeRng = 1f + monster.monstats2.MeleeRng;
    
    // Calculate ranged attack range if monster has ranged attack capability
    float rangedRng = 0f;
    if ((monster.monstats.MissA1 != null && !monster.monstats.MissA1.isEmpty()) ||
        (monster.monstats.MissA2 != null && !monster.monstats.MissA2.isEmpty())) {
      String missileName = null;
      if (monster.monstats.MissA1 != null && !monster.monstats.MissA1.isEmpty()) {
        missileName = monster.monstats.MissA1;
      } else if (monster.monstats.MissA2 != null && !monster.monstats.MissA2.isEmpty()) {
        missileName = monster.monstats.MissA2;
      }
      if (missileName != null) {
        com.riiablo.codec.excel.Missiles.Entry missile = files().Missiles.get(missileName);
        if (missile != null) {
          rangedRng = missile.Range - 2f;
          if (rangedRng < meleeRng) {
            rangedRng = meleeRng + 5f;
          }
        }
      }
    }

    if (executeCommand(targetId, targetPos, bCombat)) return;

    // D2MOD: If not in combat and checkSpecialAiState, walk to target
    if (!bCombat && checkSpecialAiState()) {
      // Remove Sequence component if exists to allow VelocityModeChanger to set walk/run mode
      if (mSequence.has(entityId)) {
        Sequence seq = mSequence.get(entityId);
        // Only remove if it's forcing NEUTRAL mode (which would prevent walking animation)
        if (seq.mode1 == Engine.Monster.MODE_NU && seq.mode2 == Engine.Monster.MODE_NU) {
          mSequence.remove(entityId);
        }
      }
      pathfinder.findPath(entityId, targetPos, false, targetId);
      stateMachine.changeState(State.APPROACH);
      time = aiRandom(1f, 2);
      return;
    }

    // D2MOD: If distance < 15 and is leader, command minions to attack (30% chance)
    if (targetDistance < 15f && isLeader() && aiRandomBoolean(params[0] / 100f)) {
      commandMinions(targetId);
      aiParam0 = true;
      // The native command also tells the leader's pack to approach.  It must
      // not turn into an attack animation merely because the target is inside
      // the command radius: a target can still be several subtiles away from
      // the leader's actual melee footprint (the common narrow-passage case).
      // Only enter the attack sequence once the footprint-aware combat check
      // says the leader can really hit; otherwise fall through to the normal
      // approach code and create a movement path.
      if (bCombat) {
        lookAt(targetId);
        mSequence.create(entityId).sequence(Engine.Monster.MODE_S2, Engine.Monster.MODE_NU);
        mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
        time = aiRandom(1f, 2);
        return;
      }
    }

    // D2MOD: If not in combat
    if (!bCombat) {
      // If distance <= APPROACH_DISTANCE, walk to target
      // But stop before reaching melee range to prevent overlapping
      if (targetDistance <= params[1] && targetDistance > meleeRng) {
        // Remove Sequence component if exists to allow VelocityModeChanger to set walk/run mode
        if (mSequence.has(entityId)) {
          Sequence seq = mSequence.get(entityId);
          if (seq.mode1 == Engine.Monster.MODE_NU && seq.mode2 == Engine.Monster.MODE_NU) {
            mSequence.remove(entityId);
          }
        }
        pathfinder.findPath(entityId, targetPos, false, targetId);
        stateMachine.changeState(State.APPROACH);
        time = aiRandom(1f, 2);
        return;
      } else if (targetDistance <= meleeRng) {
        // Already in melee range, stop movement
        pathfinder.findPath(entityId, null);
        return;
      }
      
      // 30% chance to walk close, otherwise idle
      if (aiRandomBoolean(0.3f) && targetDistance > meleeRng) {
        // Remove Sequence component if exists to allow VelocityModeChanger to set walk/run mode
        if (mSequence.has(entityId)) {
          Sequence seq = mSequence.get(entityId);
          if (seq.mode1 == Engine.Monster.MODE_NU && seq.mode2 == Engine.Monster.MODE_NU) {
            mSequence.remove(entityId);
          }
        }
        pathfinder.findPath(entityId, targetPos, false, targetId);
        stateMachine.changeState(State.APPROACH);
      } else {
        stateMachine.changeState(State.IDLE);
        time = 10f * com.riiablo.codec.Animation.FRAME_DURATION;  // 10 frames idle
      }
      return;
    }

    // D2MOD: In combat
    // Stop movement when in melee range to prevent overlapping
    pathfinder.findPath(entityId, null);
    
    // If aiParam0 is false, check attack chance
    if (!aiParam0) {
      // Check attack chance (params[2])
      if (!aiRandomBoolean(params[2] / 100f)) {
        // 30% chance to use skill2, otherwise idle
        if (aiRandomBoolean(0.3f)) {
          mSequence.create(entityId).sequence(Engine.Monster.MODE_S2, Engine.Monster.MODE_NU);
          mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
        } else {
          time = 10f * com.riiablo.codec.Animation.FRAME_DURATION;  // 10 frames idle
        }
        return;
      }
    }

    // Attack (A1 or A2 based on params[3])
    aiParam0 = false;
    stopForAttack();
    lookAt(targetId);
    stateMachine.changeState(State.ATTACK);
    byte attackMode = aiRandomBoolean(params[3] / 100f) ? Engine.Monster.MODE_A2 : Engine.Monster.MODE_A1;
    mSequence.create(entityId).sequence(attackMode, Engine.Monster.MODE_NU);
    mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
    playSound(monsound + "_attack_1");
    time = aiRandom(1f, 2);
  }

  /** Recorded native WL -> A2 entry clears the path and fits the current cell. */
  private void stopForAttack() {
    stopMovement();
    Position position = mPosition.get(entityId);
    if (position != null && position.hasNativePrecision()) {
      // Do not infer native coordinates from legacy floating-point positions.
      // Fitting low words preserves the occupied cell and its collision footprint.
      position.setNativePrecision(
          com.d2moo.common.path.D2PathStep.fitToFp16Center(position.nativePrecisionX()),
          com.d2moo.common.path.D2PathStep.fitToFp16Center(position.nativePrecisionY()));
      invalidateNativePathCache();
    }
  }

  @Override
  public String getState() {
    return stateMachine.getCurrentState().name();
  }
}

