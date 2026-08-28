package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;
import com.riiablo.engine.server.component.AIWrapper;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.Running;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.map.RenderSystem;
import com.riiablo.skill.SkillCodes;

@Wire(failOnNull = false)
@All({AIWrapper.class, Position.class, Monster.class})
public class AIStepper extends IteratingSystem {
  private static final Logger log = LogManager.getLogger(AIStepper.class);

  protected ComponentMapper<AIWrapper> mAIWrapper;
  protected ComponentMapper<Casting> mCasting;
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<Sequence> mSequence;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<Pathfind> mPathfind;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<Running> mRunning;

//  protected ComponentMapper<Interactable> mInteractable;
//  protected ComponentMapper<Size> mSize;

  protected RenderSystem renderer;

// NOTE: Handled by EntityFactory
//  @Override
//  protected void inserted(int entityId) {
//    Monster monster = mMonster.get(entityId);
//    AIWrapper aiWrapper = mAIWrapper.get(entityId);
//    AI ai = aiWrapper.ai = AI.findAI(entityId, monster);
//    world.getInjector().inject(ai);
//    ai.initialize();
//    if (monster.monstats.interact) {
//      mInteractable.create(entityId).set(mSize.get(entityId).size, ai);
//    }
//  }

  @Override
  protected void process(int entityId) {
    if (renderer != null && !renderer.withinRadius(mPosition.get(entityId).position)) return;
    Monster monster = mMonster.get(entityId);
    if (monster.spawnZone != null && mMapWrapper.has(entityId)
        && mMapWrapper.get(entityId).zone != null
        && mMapWrapper.get(entityId).zone != monster.spawnZone) {
      // Native AI is room/activation scoped. Once a path accidentally crosses
      // a generated level seam, stop the authoritative movement immediately;
      // do not allow the monster to continue toward town or another level.
      if (mPathfind.has(entityId)) mPathfind.remove(entityId);
      if (mRunning.has(entityId)) mRunning.remove(entityId);
      if (mVelocity.has(entityId)) mVelocity.get(entityId).velocity.setZero();
      log.debug("[MONSTER_BOUNDARY] entity={} monster={} spawnZone={} currentZone={} action=halt",
          entityId, monster.monstats != null ? monster.monstats.Id : "unknown",
          monster.spawnZone.level != null ? monster.spawnZone.level.Id : -1,
          mMapWrapper.get(entityId).zone.level != null
              ? mMapWrapper.get(entityId).zone.level.Id : -1);
      return;
    }
    boolean hadCasting = mCasting.has(entityId);
    int previousSkill = hadCasting ? mCasting.get(entityId).skillId : -1;
    int previousTarget = hadCasting ? mCasting.get(entityId).targetId : -1;

    AIWrapper wrapper = mAIWrapper.get(entityId);
    wrapper.ai.update(world.delta);

    if (!mCasting.has(entityId)) return;
    Casting casting = mCasting.get(entityId);
    if (hadCasting
        && casting.skillId == previousSkill
        && casting.targetId == previousTarget) {
      return;
    }

    byte currentMode = mCofReference.has(entityId) ? mCofReference.get(entityId).mode : -1;
    byte requestedMode = mSequence.has(entityId) ? mSequence.get(entityId).mode1 : -1;
    String marker = casting.skillId == SkillCodes.attack
        ? "[MONSTER_ATTACK]" : "[MONSTER_SKILL]";
    log.info("{} phase=decision entity={} monster={} ai={} skill={} currentMode={} "
            + "requestedMode={} target={} replaced={}",
        marker,
        entityId,
        monster.monstats != null ? monster.monstats.Id : "unknown",
        wrapper.ai.getClass().getSimpleName(),
        casting.skillId,
        (int) currentMode,
        (int) requestedMode,
        casting.targetId,
        hadCasting);
  }
}
