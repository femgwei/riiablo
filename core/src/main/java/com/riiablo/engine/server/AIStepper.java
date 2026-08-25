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

    Monster monster = mMonster.get(entityId);
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
