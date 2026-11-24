package com.riiablo.presentation;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;
import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Networked;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.event.SkillStartEvent;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.skill.SkillFormula;
import net.mostlyoriginal.api.event.common.Subscribe;
import com.artemis.systems.IteratingSystem;

/** Skill CltDo24 creates independent client fire/ice; missile CltDo remains 1. */
@All(Position.class)
public final class InfernoPresentationSystem extends IteratingSystem {
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Missile> mMissile;
  protected ComponentMapper<Networked> mNetworked;
  protected ComponentMapper<Casting> mCasting;
  protected ComponentMapper<Sequence> mSequence;
  @Wire(name = "factory") protected EntityFactory factory;
  @Wire(name = "clientContext") protected ClientGameplayContext clientContext;
  @Wire(failOnNull = false) protected OverlayManager overlays;
  private final IntMap<D2Seed> seeds = new IntMap<>();
  private final IntMap<Long> arcticStarts = new IntMap<>();
  private final java.util.List<PendingArctic> pendingArctic = new java.util.ArrayList<>();
  private final IntSet arcticBornThisPass = new IntSet();
  private long pass;
  private static final class PendingArctic {
    SkillDoEvent event;
    long action, queuedPass;
  }
  public static boolean isDamageCarrier(Missile missile) {
    return NativeInfernoPresentation.handles(missile) && !missile.presentationOnly;
  }
  @Override protected void process(int entityId) {}
  @Override protected void removed(int entityId) {
    seeds.remove(entityId); arcticStarts.remove(entityId);
    pendingArctic.removeIf(p -> p.event.entityId == entityId);
  }
  @Override protected void begin() {
    pass++;
    arcticBornThisPass.clear();
    java.util.Iterator<PendingArctic> iterator = pendingArctic.iterator();
    while (iterator.hasNext()) {
      PendingArctic pending = iterator.next();
      if (pending.queuedPass >= pass) continue;
      iterator.remove();
      Casting casting = mCasting.get(pending.event.entityId);
      if (casting == null || casting.actionId != pending.action || casting.skillId != 230
          || !mPosition.has(pending.event.entityId)) continue;
      // CltDo24 resolves the caster's current target when consumed (1A950),
      // not the target from the preceding animation keyframe. Released input
      // already retains Casting's last held aim in Actioneer.
      pending.event.targetId = casting.targetId;
      pending.event.targetVec = new Vector2(casting.targetVec);
      emit(pending.event, true);
    }
  }
  boolean arcticBornThisPass(int entityId) { return arcticBornThisPass.contains(entityId); }

  /** Original CltSt15 creates one ONCE overlay and start sound before the first stream. */
  @Subscribe public void onSkillStart(SkillStartEvent event) {
    if (event.skillId != 230 || event.cltstfunc != 15 || !mPosition.has(event.entityId)) return;
    long action = mCasting.has(event.entityId) ? mCasting.get(event.entityId).actionId : 0;
    Long previous = arcticStarts.get(event.entityId);
    if (action != 0 && previous != null && previous == action) return;
    com.riiablo.codec.excel.NativeSkills.Entry row = clientContext.files().NativeSkills != null
        ? clientContext.files().NativeSkills.get(event.skillId) : null;
    if (row == null) return;
    String overlay = row.string("prgoverlay"), sound = row.string("prgsound");
    if (overlays != null && overlay != null && !overlay.isEmpty()) overlays.set(event.entityId, overlay);
    if (clientContext.audio() != null && sound != null && !sound.isEmpty()) clientContext.audio().play(sound, true);
    if (action != 0) arcticStarts.put(event.entityId, action);
  }

  @Subscribe public void onSkillDo(SkillDoEvent event) {
    if ((event.skillId != 41 && event.skillId != 230) || !mPosition.has(event.entityId)) return;
    Sequence sequence = mSequence.get(event.entityId);
    Casting casting = mCasting.get(event.entityId);
    if (event.skillId == 230 && sequence != null && sequence.nativeArcticBlast && casting != null) {
      // D2Client+A8C9D consumes last update's action before advancing the
      // current SQ frame. Do not move the server's damage/mana callback.
      PendingArctic pending = new PendingArctic();
      pending.event = SkillDoEvent.obtain(event.entityId, event.skillId, -1,
          null, event.srvdofunc, event.cltdofunc);
      pending.action = casting.actionId; pending.queuedPass = pass;
      pendingArctic.add(pending);
      return;
    }
    emit(event, false);
  }

  private void emit(SkillDoEvent event, boolean consumedAction) {
    com.riiablo.codec.excel.Skills.Entry skill = clientContext.files().skills.get(event.skillId);
    if (skill == null) return;
    D2Seed seed = seeds.get(event.entityId);
    if (seed == null) { seed = new D2Seed(event.entityId, 666); seeds.put(event.entityId, seed); }
    int level = mPlayer.has(event.entityId) && mPlayer.get(event.entityId).data != null
        ? Math.max(1, mPlayer.get(event.entityId).data.getSkill(event.skillId)) : 1;
    boolean alternate = Seed.rollLimitedRandomNumber(seed, 2) != 0;
    String name = alternate && skill.cltmissileb != null && !skill.cltmissileb.isEmpty()
        ? skill.cltmissileb : skill.cltmissilea;
    com.riiablo.codec.excel.Missiles.Entry row = clientContext.files().Missiles.get(name);
    if (row == null) return;
    Vector2 origin = mPosition.get(event.entityId).position;
    // Artemis removes deleted units from subscriptions before reclaiming their
    // pooled components at World.process end. isActive/mapper.has alone can
    // still expose an unlinked target during this update (native ADEC0 clears it).
    Vector2 target = event.targetId >= 0 && subscription.getActiveEntityIds().get(event.targetId)
        && mPosition.has(event.targetId)
        ? mPosition.get(event.targetId).position : event.targetVec;
    Vector2 direction = target == null ? new Vector2(Vector2.X) : new Vector2(target).sub(origin);
    if (direction.isZero()) direction.set(Vector2.X);
    int id = factory.createMissile(row, direction.nor(), origin, event.entityId, level);
    if (id < 0 || !mMissile.has(id)) return;
    Missile visual = mMissile.get(id);
    visual.skillId = event.skillId; visual.damageLevel = level;
    visual.presentationOnly = true; visual.authoritative = false; visual.persistent = false;
    visual.nativeLifetimeFrames = Math.max(1, SkillFormula.evaluate(skill.calc1, skill, level));
    visual.range = 0;
    // The selected row already owns the A/B resource; do not roll per-missile again.
    visual.presentationSeedKnown = true; visual.presentationSeed = seed.getNLowSeed();
    mNetworked.remove(id);
    if (event.skillId == 230 && consumedAction) arcticBornThisPass.add(id);
  }
}
