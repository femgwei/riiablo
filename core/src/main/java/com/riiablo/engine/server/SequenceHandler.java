package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.NativeObjectState;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.WhirlwindRuntime;
import com.riiablo.engine.server.event.AnimDataFinishedEvent;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.Weapons;
import com.riiablo.Riiablo;
import com.riiablo.item.Item;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Type;

import net.mostlyoriginal.api.event.common.Subscribe;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

@All({CofReference.class, Sequence.class, AnimData.class})
public class SequenceHandler extends IteratingSystem {
  private static final Logger log = LogManager.getLogger(SequenceHandler.class);
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<NativeObjectState> mNativeObjectState;
  protected ComponentMapper<Sequence> mSequence;
  protected ComponentMapper<AnimData> mAnimData;
  protected ComponentMapper<Casting> mCasting;
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<WhirlwindRuntime> mWhirlwindRuntime;
  protected ComponentMapper<Monster> mMonster;

  protected CofManager cofs;

  @Subscribe
  public void onAnimDataFinished(AnimDataFinishedEvent event) {
    if (!mSequence.has(event.entityId)) return;
    Sequence sequence = mSequence.get(event.entityId);
    NativeObjectState shrine = mNativeObjectState.get(event.entityId);
    if (shrine != null && shrine.kind == com.riiablo.map.NativePresetObjectResolver.Kind.SHRINE) {
      AnimData anim = mAnimData.get(event.entityId);
      log.info("[SHRINE_ANIM] phase=finished entity={} mode1={} mode2={} cofMode={} "
              + "frame={} frames={} activated={} persistentMode={} sequenceStillPresent=true",
          event.entityId, sequence.mode1, sequence.mode2,
          mCofReference.get(event.entityId).mode, anim == null ? -1 : anim.frame,
          anim == null ? -1 : anim.numFrames, shrine.activated, shrine.currentMode);
    }
    Casting casting = mCasting.get(event.entityId);
    if (casting != null && casting.dragonTalonInitialized
        && casting.dragonTalonRemainingKicks > 0
        && casting.dragonTalonKickProcessed) {
      sequence.sequence(sequence.mode1, sequence.mode2);
      mAnimData.get(event.entityId).override = -1;
      com.riiablo.logger.LogManager.getLogger(SequenceHandler.class).info(
          "[ASSASSIN_DRAGON_TALON] phase=repeat_animation entity={} remaining={}",
          event.entityId, casting.dragonTalonRemainingKicks);
      return;
    }
    if (casting != null && casting.dragonClawInitialized
        && casting.dragonClawRemainingStrikes > 0
        && casting.dragonClawStrikeProcessed) {
      byte nextMode = com.riiablo.engine.Engine.Player.MODE_S4;
      sequence.sequence(nextMode, sequence.mode2);
      mAnimData.get(event.entityId).override = -1;
      com.riiablo.logger.LogManager.getLogger(SequenceHandler.class).info(
          "[ASSASSIN_DRAGON_CLAW] phase=second_animation entity={} remaining={} mode={}",
          event.entityId, casting.dragonClawRemainingStrikes, (int) nextMode);
      return;
    }
    if (casting != null && casting.dragonFlightInitialized
        && casting.dragonFlightWarped
        && !casting.dragonFlightKickProcessed) {
      byte nextMode = com.riiablo.engine.Engine.Player.MODE_KK;
      sequence.sequence(nextMode, sequence.mode2);
      mAnimData.get(event.entityId).override = -1;
      com.riiablo.logger.LogManager.getLogger(SequenceHandler.class).info(
          "[ASSASSIN_DRAGON_FLIGHT] phase=kick_animation entity={} mode={}",
          event.entityId, (int) nextMode);
      return;
    }
    if (casting != null && casting.jabRemainingStrikes > 0
        && casting.jabStrikeProcessed) {
      casting.jabStrikeProcessed = false;
      restartAmazonStrikeAnimation(event.entityId, casting,
          com.riiablo.engine.Engine.Player.MODE_A2, "JAB");
      return;
    }
    if (casting != null && casting.fendInitialized
        && casting.fendRemainingStrikes > 0
        && casting.fendStrikeProcessed) {
      casting.fendStrikeProcessed = false;
      restartFendAnimation(event.entityId, casting, sequence.mode1);
      return;
    }
    if (casting != null && casting.furyInitialized
        && casting.furyRemainingStrikes > 0
        && casting.furyStrikeProcessed) {
      sequence.sequence(sequence.mode1, sequence.mode2);
      mAnimData.get(event.entityId).override = -1;
      com.riiablo.logger.LogManager.getLogger(SequenceHandler.class).info(
          "[DRUID_FURY] phase=repeat_animation source={} remaining={} nextTarget={}",
          event.entityId, casting.furyRemainingStrikes, casting.furyCurrentTargetId);
      return;
    }
    if (casting != null && mWhirlwindRuntime.has(event.entityId)) {
      sequence.sequence(sequence.mode1, sequence.mode2);
      mAnimData.get(event.entityId).override = -1;
      com.riiablo.logger.LogManager.getLogger(SequenceHandler.class).debug(
          "[WHIRLWIND] phase=repeat_animation entity={}", event.entityId);
      return;
    }
    // Log sequence transition for debugging
    com.riiablo.logger.Logger log = com.riiablo.logger.LogManager.getLogger(SequenceHandler.class);
    log.trace("Sequence finished for entity {}: mode1={} -> mode2={}", event.entityId, sequence.mode1, sequence.mode2);
    log.info("=== SequenceHandler.onAnimDataFinished ===");
    log.info("Entity: {}, Removing Sequence, mode1={} -> mode2={}", event.entityId, sequence.mode1, sequence.mode2);
    cofs.setMode(event.entityId, sequence.mode2);
    mAnimData.get(event.entityId).override = -1;
    mSequence.remove(event.entityId);
    log.info("After removal: Has Sequence: {}", mSequence.has(event.entityId));
  }

  @Override
  protected void process(int entityId) {
    Sequence sequence = mSequence.get(entityId);
    Casting casting = mCasting.get(entityId);
    boolean sequenceWasStarted = sequence.started;
    Monster monster = mMonster.get(entityId);
    boolean monsterMelee = monster != null && casting != null
        && casting.skillId == com.riiablo.skill.SkillCodes.attack
        && Monster.isMeleeMode(sequence.mode1);
    if (monsterMelee && !sequence.started) {
      AnimData anim = mAnimData.get(entityId);
      log.info("[MONSTER_MELEE] phase=sequence_start entity={} monster={} mode1={} mode2={} "
              + "cofMode={} animFrame={} animFrames={} speed={} keyframes={} target={} skill={}",
          entityId, monster.monstats != null ? monster.monstats.Id : "unknown",
          Monster.modeName(sequence.mode1), Monster.modeName(sequence.mode2),
          Monster.modeName(mCofReference.get(entityId).mode),
          anim.frame, anim.numFrames, anim.speed,
          anim.keyframes != null ? anim.keyframes.length : 0,
          casting.targetId, casting.skillId);
    }
    if (monsterMelee && sequence.started
        && mCofReference.get(entityId).mode != sequence.mode1) {
      log.warn("[MONSTER_MELEE] phase=sequence_mode_mismatch entity={} monster={} "
              + "requestedMode={} actualMode={} target={} animFrame={} animFrames={} keyframes={}",
          entityId, monster.monstats != null ? monster.monstats.Id : "unknown",
          Monster.modeName(sequence.mode1), Monster.modeName(mCofReference.get(entityId).mode),
          casting.targetId, mAnimData.get(entityId).frame, mAnimData.get(entityId).numFrames,
          mAnimData.get(entityId).keyframes != null ? mAnimData.get(entityId).keyframes.length : 0);
    }
    if (!sequence.started) {
      if (casting != null && casting.dragonTalonInitialized) {
        casting.dragonTalonKickProcessed = false;
      }
      if (casting != null && casting.dragonClawInitialized) {
        casting.dragonClawStrikeProcessed = false;
      }
      if (casting != null && casting.furyInitialized) {
        casting.furyStrikeProcessed = false;
      }
      if (casting != null && casting.fendInitialized) {
        casting.fendStrikeProcessed = false;
      }
      sequence.started = true;
      NativeObjectState shrine = mNativeObjectState.get(entityId);
      if (shrine != null && shrine.kind == com.riiablo.map.NativePresetObjectResolver.Kind.SHRINE) {
        AnimData anim = mAnimData.get(entityId);
        log.info("[SHRINE_ANIM] phase=started entity={} mode1={} mode2={} cofMode={} "
                + "frame={} frames={} activated={} persistentMode={}",
            entityId, sequence.mode1, sequence.mode2, mCofReference.get(entityId).mode,
            anim == null ? -1 : anim.frame, anim == null ? -1 : anim.numFrames,
            shrine.activated, shrine.currentMode);
      }
      // D2 starts each action at frame zero. Force the COF event even when a
      // repeated action uses the same mode (for example consecutive Throws),
      // otherwise it inherits the previous frame and can skip its keyframe.
      cofs.setMode(entityId, sequence.mode1, true);
      mAnimData.get(entityId).override = -1;
    } else if (!sequence.nativeJab && mCofReference.get(entityId).mode != sequence.mode1) {
      // Log sequence start for debugging
      com.riiablo.logger.Logger log = com.riiablo.logger.LogManager.getLogger(SequenceHandler.class);
      log.trace("Starting sequence for entity {}: setting mode to {}", entityId, sequence.mode1);
      cofs.setMode(entityId, sequence.mode1);
      mAnimData.get(entityId).override = -1;
    }
    if (sequence.nativeJab) {
      // D2Common's UNITS_UpdateAttackAnimRateAndVelocity applies the fixed
      // -30 sequence penalty once to the logical SQ cursor.  AnimStepper
      // advances that cursor and selects the exact SequenceTbls point.
      AnimData animData = mAnimData.get(entityId);
      // The native sequence speed is latched when SQ starts.  A later point
      // may switch the presentation COF to A2, but D2 keeps dwSeqSpeed from
      // the sequence initialization instead of recalculating it from A2's
      // animation record.
      if (!sequenceWasStarted && animData != null) {
        sequence.nativeJabSpeed = playerAttackAnimationSpeed(entityId, animData.speed, 30);
      }
      if (animData != null) animData.override = 0;
      return;
    }
    if (casting != null && (casting.jabRemainingStrikes > 0
        || casting.jabStrikeProcessed)) {
      // D2's hard-coded Jab sequence selects roughly one third of each A1/A2
      // animation. Advancing the complete COFs at 3x preserves its native
      // three-thrust cadence while retaining their real attack keyframes.
      AnimData animData = mAnimData.get(entityId);
      animData.override = Math.max(1, animData.speed * 3);
    }
    if (casting != null && mPlayer.has(entityId)) {
      AnimData animData = mAnimData.get(entityId);
      boolean strafe = casting.strafeInitialized;
      // D2MOO routes both normal A1/A2 attacks and the SQ/Strafe sequence
      // through UNITS_UpdateAttackAnimRateAndVelocity.  Previously only
      // Strafe installed an override here, leaving ordinary bow attacks at
      // the raw COF speed (256).  That made Strafe appear dramatically slower
      // even with the same weapon and IAS.  Apply the same native attack-rate
      // calculation to every player attack mode that allows attack-rate
      // modulation; Strafe uses the separate hard-coded rollback formula.
      boolean attackRateMode = strafe
          || sequence.mode1 == com.riiablo.engine.Engine.Player.MODE_A1
          || sequence.mode1 == com.riiablo.engine.Engine.Player.MODE_A2;
      if (attackRateMode) {
        if (strafe) {
          StrafeTiming timing = resolveStrafeTiming(entityId, animData.speed);
          animData.override = timing.animationSpeed;
          if (!sequenceWasStarted) {
            log.info("[STRAFE_TIMING] phase=start entity={} weapon={} crossbow={} wsm={} "
                    + "iasRaw={} effectiveIAS={} eias={} animSpeed={} baseFrame={} "
                    + "actionFrame={} A={} B={} C={} rollbackFrame={}",
                entityId, timing.weaponCode, timing.crossbow, timing.wsm,
                timing.totalIAS, timing.effectiveIAS, timing.eias, timing.animationSpeed,
                timing.baseFrame, timing.actionFrame, timing.initialFrame,
                timing.middleFrame, timing.lastFrame, timing.rollbackFrame);
          }
        } else {
          // Ordinary A1/A2 attacks use the regular attack-rate path. Strafe
          // has its own native rollback formula above and must not receive
          // the normal sequence penalty.
          animData.override = playerAttackAnimationSpeed(entityId, animData.speed, 0);
        }
        if (!sequenceWasStarted) {
          log.info("[PLAYER_ATTACK_ANIM] phase=start entity={} mode={} skill={} "
                  + "baseSpeed={} sequencePenalty={} finalSpeed={}",
              entityId, sequence.mode1, casting.skillId, animData.speed,
              strafe ? -1 : 0, animData.override);
        }
      }
    }
  }

  /**
   * D2's sub_6FCBCFD0(Param6) does not replay Strafe from frame zero. It
   * seeks to the rollback sequence's calculated middle start frame and
   * schedules the next attack event, producing the rapid bow-firing cadence.
   */
  void restartStrafeAnimation(int entityId, Casting casting) {
    AnimData anim = mAnimData.get(entityId);
    // Broadcast a forced same-mode restart so the client seeks its resident
    // animation as well. AnimDataResolver may reset the server record while
    // handling this event; the precise rollback is applied immediately after.
    cofs.setMode(entityId, mSequence.get(entityId).mode1, true);
    anim = mAnimData.get(entityId);
    StrafeTiming timing = resolveStrafeTiming(entityId, anim.speed);
    int attackFrame = firstAttackFrame(anim);
    // The rollback sequence rewinds to the formula's middle start frame. The
    // keyframe clamp is only a safety guard for incomplete/custom COFs; it
    // must not replace the native A/B/C timing on a valid player animation.
    int restartFrame = timing.rollbackFrame;
    if (attackFrame >= 0) restartFrame = Math.min(restartFrame, Math.max(0, attackFrame - 1));
    int maxFrame = Math.max(0, (anim.numFrames >>> 8) - 1);
    restartFrame = Math.min(Math.max(0, restartFrame), maxFrame);
    // AnimData.frame is 24.8 fixed point. The old path wrote an integer here,
    // effectively seeking to a fraction of a frame and making every repeat
    // depend on the simulation tick rather than the rollback formula.
    anim.frame = restartFrame << 8;
    anim.lastKeyframeIndex = Math.max(-1, (anim.frame >>> 8) - 1);
    anim.override = timing.animationSpeed;
    // Keep SequenceHandler from invoking CofManager's ordinary frame-zero
    // mode transition on the next tick; the client receives the forced mode
    // restart and applies the same seek in CofLayerLoader.
    mSequence.get(entityId).started = true;
    log.info("[STRAFE_ANIM] phase=rollback entity={} index={} remaining={} frame={} "
            + "attackFrame={} rollbackFrame={} A={} B={} C={} speed={}", entityId,
        casting.strafeArrowIndex, casting.strafeRemainingArrows, anim.frame,
        attackFrame, timing.rollbackFrame, timing.initialFrame, timing.middleFrame,
        timing.lastFrame, anim.override);
  }

  private int firstAttackFrame(AnimData anim) {
    if (anim.keyframes == null) return -1;
    for (int i = 0; i < anim.keyframes.length; i++) {
      if (anim.keyframes[i] == com.riiablo.engine.Engine.KEYFRAME_ATK) return i;
    }
    return -1;
  }

  /** Restart Jab/Fend at the next attack window instead of replaying a full COF. */
  private void restartAmazonStrikeAnimation(int entityId, Casting casting,
      byte mode, String skillName) {
    Sequence sequence = mSequence.get(entityId);
    if (sequence == null || !mAnimData.has(entityId)) return;
    sequence.sequence(mode, sequence.mode2);
    cofs.setMode(entityId, mode, true);
    AnimData anim = mAnimData.get(entityId);
    int attackFrame = firstAttackFrame(anim);
    if (attackFrame < 0) {
      log.warn("[AMAZON_{}_ANIM] entity={} mode={} reason=attack_keyframe_missing",
          skillName, entityId, (int) mode);
      anim.override = playerAttackAnimationSpeed(entityId, anim.speed, 0);
      return;
    }
    // D2's native Jab SQ does not rewind the follow-up A2 thrust to the
    // attack marker.  The 1HS sequence starts each A2 segment at frame 4 and
    // reaches its attack event at frame 9/10; using attackFrame - 1 here
    // starts at the hit pose and produces the reported half-animation.
    // Fend's sequence uses the normal beginning of its selected COF.
    int restartFrame = "JAB".equals(skillName) ? 4 : 0;
    int maxFrame = Math.max(0, (anim.numFrames >>> 8) - 1);
    anim.frame = Math.min(restartFrame, maxFrame) << 8;
    anim.lastKeyframeIndex = Math.max(-1, (anim.frame >>> 8) - 1);
    anim.override = playerAttackAnimationSpeed(entityId, anim.speed, 0);
    sequence.started = true;
    log.info("[AMAZON_{}_ANIM] phase=restart entity={} mode={} attackFrame={} frame={} "
            + "speed={} remaining={}", skillName, entityId, (int) mode, attackFrame,
        anim.frame, anim.override,
        "JAB".equals(skillName) ? casting.jabRemainingStrikes : casting.fendRemainingStrikes);
  }

  /**
   * D2MOO's Fend SrvDo013 calls sub_6FCBCFD0 with Skills.txt Param2.  The
   * 1.10f row sets Param2 to 60 (percent frame rollback), so Fend's next
   * target is reached by rewinding the current attack sequence rather than
   * starting another complete A1 animation.  Jab uses a different native SQ
   * table and must not share this timing path.
   */
  private void restartFendAnimation(int entityId, Casting casting, byte mode) {
    Sequence sequence = mSequence.get(entityId);
    if (sequence == null || !mAnimData.has(entityId)) return;
    cofs.setMode(entityId, mode, true);
    AnimData anim = mAnimData.get(entityId);
    int rollbackPercent = 60;
    com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(casting.skillId);
    if (skill != null && skill.Param != null && skill.Param.length > 2
        && skill.Param[2] > 0) {
      rollbackPercent = Math.min(100, skill.Param[2]);
    }
    int maxFrame = Math.max(0, (anim.numFrames >>> 8) - 1);
    int restartFrame = maxFrame * rollbackPercent / 100;
    int attackFrame = firstAttackFrame(anim);
    if (attackFrame >= 0) restartFrame = Math.min(restartFrame, Math.max(0, attackFrame - 1));
    anim.frame = restartFrame << 8;
    anim.lastKeyframeIndex = Math.max(-1, restartFrame - 1);
    anim.override = playerAttackAnimationSpeed(entityId, anim.speed, 0);
    sequence.started = true;
    log.info("[AMAZON_FEND_ANIM] phase=rollback entity={} mode={} rollbackPercent={} "
            + "frame={} attackFrame={} remaining={}", entityId, (int) mode,
        rollbackPercent, restartFrame, attackFrame, casting.fendRemainingStrikes);
  }

  /**
   * Native Strafe rollback timing. Unlike ordinary A1/A2 attacks, Strafe's
   * middle-arrow cadence is calculated by the hard-coded rollback sequence;
   * there is no B-frame table in skills.txt or missiles.txt.
   */
  private StrafeTiming resolveStrafeTiming(int entityId, int baseSpeed) {
    int totalIAS = 0;
    int attackRateModifier = 0;
    String weaponCode = "none";
    int wsm = 0;
    boolean crossbow = false;
    if (mAttributesWrapper.has(entityId)
        && mAttributesWrapper.get(entityId).attrs != null) {
      com.riiablo.attributes.Attributes attrs = mAttributesWrapper.get(entityId).attrs;
      StatRef ias = attrs.get(Stat.item_fasterattackrate, StatRef.obtain());
      if (ias == null) ias = attrs.remaining().get(Stat.item_fasterattackrate, StatRef.obtain());
      if (ias != null) totalIAS = ias.asInt();
      StatRef attackRate = attrs.get(Stat.attackrate, StatRef.obtain());
      if (attackRate != null) attackRateModifier = attackRate.asInt() - 100;
    }
    Item weapon = null;
    if (mPlayer.has(entityId) && mPlayer.get(entityId).data != null) {
      weapon = mPlayer.get(entityId).data.getItems().getEquippedRangedWeapon();
    }
    if (weapon != null) {
      weaponCode = weapon.base instanceof Weapons.Entry
          ? ((Weapons.Entry) weapon.base).code : "unknown";
      crossbow = weapon.type != null && weapon.type.is(Type.XBOW);
      if (weapon.base instanceof Weapons.Entry) {
        // Weapons.txt stores the signed attack-speed modifier used by D2's
        // normal attack code. Keep that sign: a fast weapon is negative WSM.
        wsm = -((Weapons.Entry) weapon.base).speed;
      }
      // In reduced test entities the aggregate item stat may be absent. Read
      // the weapon's own IAS as the native fallback, without double counting
      // an aggregate value already present on the character.
      if (totalIAS == 0 && weapon.attrs != null) {
        StatRef ias = weapon.attrs.get(Stat.item_fasterattackrate, StatRef.obtain());
        if (ias == null) ias = weapon.attrs.base().get(Stat.item_fasterattackrate, StatRef.obtain());
        if (ias != null) totalIAS = ias.asInt();
      }
    }
    totalIAS += attackRateModifier;
    return calculateStrafeTiming(baseSpeed, totalIAS, wsm, crossbow, weaponCode);
  }

  static StrafeTiming calculateStrafeTiming(int baseSpeed, int totalIAS, int wsm,
      boolean crossbow) {
    return calculateStrafeTiming(baseSpeed, totalIAS, wsm, crossbow, "unknown");
  }

  private static StrafeTiming calculateStrafeTiming(int baseSpeed, int totalIAS, int wsm,
      boolean crossbow, String weaponCode) {
    int clampedIAS = Math.max(-119, totalIAS);
    int effectiveIAS = 120 * clampedIAS / (120 + clampedIAS);
    int eias = effectiveIAS - wsm;
    int speed = Math.max(1, 256 * (100 + eias) / 100);
    int baseFrame = crossbow ? 20 : 14;
    int actionFrame = crossbow ? 9 : 6;
    int initialFrame = ceilDiv(actionFrame * 256, speed);
    int middleStart = floorHalf((initialFrame * speed) / 256);
    int middleFrame = ceilDiv((actionFrame - middleStart) * 256, speed);
    int lastStart = floorHalf(middleStart + (middleFrame * speed) / 256);
    int lastFrame = ceilDiv((baseFrame - lastStart) * 256, speed);
    int animationSpeed = Math.max(1, (int) ((long) Math.max(1, baseSpeed) * speed / 256));
    return new StrafeTiming(weaponCode, crossbow, totalIAS, effectiveIAS, wsm, eias,
        animationSpeed, baseFrame, actionFrame, initialFrame, middleFrame, lastFrame,
        middleStart);
  }

  private static int ceilDiv(int numerator, int denominator) {
    return (numerator + denominator - 1) / denominator;
  }

  private static int floorHalf(int value) {
    return value / 2;
  }

  static final class StrafeTiming {
    final String weaponCode;
    final boolean crossbow;
    final int totalIAS;
    final int effectiveIAS;
    final int wsm;
    final int eias;
    final int animationSpeed;
    final int baseFrame;
    final int actionFrame;
    final int initialFrame;
    final int middleFrame;
    final int lastFrame;
    final int rollbackFrame;

    StrafeTiming(String weaponCode, boolean crossbow, int totalIAS, int effectiveIAS,
        int wsm, int eias, int animationSpeed, int baseFrame, int actionFrame,
        int initialFrame, int middleFrame, int lastFrame, int rollbackFrame) {
      this.weaponCode = weaponCode;
      this.crossbow = crossbow;
      this.totalIAS = totalIAS;
      this.effectiveIAS = effectiveIAS;
      this.wsm = wsm;
      this.eias = eias;
      this.animationSpeed = animationSpeed;
      this.baseFrame = baseFrame;
      this.actionFrame = actionFrame;
      this.initialFrame = initialFrame;
      this.middleFrame = middleFrame;
      this.lastFrame = lastFrame;
      this.rollbackFrame = rollbackFrame;
    }
  }

  /**
   * D2Common's UNITS_UpdateAttackAnimRateAndVelocity for player attacks.
   *
   * <p>The normal A1/A2 modes use the calculated attack rate directly. Strafe
   * does not call this helper: its native rollback sequence has a separate
   * EIAS/WSM calculation above.</p>
   */
  private int playerAttackAnimationSpeed(int entityId, int baseSpeed,
      int sequencePenalty) {
    // Mirrors D2Common's UNITS_UpdateAttackAnimRateAndVelocity:
    // nRate = effective IAS + STAT_ATTACKRATE - 30 (SEQUENCE), clamped to
    // [15,175], then multiplied by the animation table's base speed.
    int attackRate = 100;
    if (mAttributesWrapper.has(entityId)
        && mAttributesWrapper.get(entityId).attrs != null) {
      StatRef base = mAttributesWrapper.get(entityId).attrs
          .get(Stat.attackrate, StatRef.obtain());
      if (base != null) attackRate = base.asInt();
    }
    boolean aggregateIas = false;
    int fasterAttackRate = 0;
    if (mAttributesWrapper.has(entityId)
        && mAttributesWrapper.get(entityId).attrs != null) {
      com.riiablo.attributes.Attributes attrs = mAttributesWrapper.get(entityId).attrs;
      StatRef ias = attrs.get(Stat.item_fasterattackrate, StatRef.obtain());
      // ItemStatCost entries that are not character-base stats remain in the
      // character's remaining list after ItemData.updateStats(). D2 still
      // includes them in STATLIST_UnitGetItemStatOrSkillStatValue().
      if (ias == null) ias = attrs.remaining().get(Stat.item_fasterattackrate, StatRef.obtain());
      if (ias != null) {
        fasterAttackRate = ias.asInt();
        aggregateIas = true;
      }
    }
    if (mPlayer.has(entityId) && mPlayer.get(entityId).data != null) {
      // D2's attack-rate helper uses the active weapon for both bow and
      // spear/javelin attacks.  Restricting this lookup to bows makes Jab,
      // Impale and Fend fall back to WSM=0, which leaves their long A1/A2 COF
      // at the raw 256 speed and is visibly slower than vanilla.
      Item weapon = mPlayer.get(entityId).data.getItems().getEquippedRangedWeapon();
      if (weapon == null) weapon = mPlayer.get(entityId).data.getItems().getEquipped(BodyLoc.RARM);
      if (weapon == null) weapon = mPlayer.get(entityId).data.getItems().getEquipped(BodyLoc.LARM);
      if (weapon != null) {
        if (weapon.base instanceof Weapons.Entry) {
          int weaponAttackRate = itemStatInt(weapon, Stat.attackrate,
              -((Weapons.Entry) weapon.base).speed);
          attackRate += weaponAttackRate;
        }
        StatRef ias = !aggregateIas && weapon.attrs != null
            ? weapon.attrs.get(Stat.item_fasterattackrate, StatRef.obtain()) : null;
        if (ias == null && !aggregateIas && weapon.attrs != null) {
          ias = weapon.attrs.base().get(Stat.item_fasterattackrate, StatRef.obtain());
        }
        if (ias != null) fasterAttackRate = ias.asInt();
      }
    }
    if (fasterAttackRate > 0) {
      fasterAttackRate = 120 * fasterAttackRate / (fasterAttackRate + 120);
    }
    int rate = attackRate + fasterAttackRate - sequencePenalty;
    rate = Math.max(15, Math.min(175, rate));
    return Math.max(1, baseSpeed * rate / 100);
  }

  private static int itemStatInt(Item item, short stat, int fallback) {
    if (item == null || item.attrs == null) return fallback;
    StatRef ref = item.attrs.get(stat, StatRef.obtain());
    if (ref == null) ref = item.attrs.base().get(stat, StatRef.obtain());
    return ref == null ? fallback : ref.asInt();
  }
}
