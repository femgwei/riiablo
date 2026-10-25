package com.riiablo.engine.server.ai;

import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.Engine;

/** AITHINK_Fn029_BatDemon and its special-AI transition callback Unk029. */
public class BatDemon extends NativeDecisionAI {
  private int phase, loops, regenBonus;
  @Override void writeRuntime(java.io.DataOutputStream out) throws java.io.IOException {
    super.writeRuntime(out);
    out.writeInt(phase);
    out.writeInt(loops);
    out.writeInt(regenBonus);
  }
  @Override void readRuntime(java.io.DataInputStream in, boolean apply) throws java.io.IOException {
    super.readRuntime(in, apply);
    int savedPhase = in.readInt();
    int savedLoops = in.readInt();
    int savedRegenBonus = in.readInt();
    if (apply) {
      phase = savedPhase;
      loops = savedLoops;
      regenBonus = savedRegenBonus;
    }
  }

  public BatDemon(int entityId) { super(entityId); }

  @Override public boolean updateSpecialAiControl(float delta) {
    com.riiablo.engine.server.component.UnitStates states = mUnitStates.get(entityId);
    boolean controlled = states != null && states.stateList != null
        && (states.stateList.hasState(com.riiablo.engine.server.state.StateId.TERROR)
            || states.stateList.hasState(com.riiablo.engine.server.state.StateId.TAUNT)
            || states.stateList.hasState(com.riiablo.engine.server.state.StateId.DIMVISION));
    if (controlled && mCofReference.has(entityId)
        && mCofReference.get(entityId).mode == Engine.Monster.MODE_S4) {
      phase = 1;
      return false;
    }
    if (controlled && phase == 4) {
      phase = 1;
      startMode(Engine.Monster.MODE_S3, Engine.INVALID_ENTITY);
      return true;
    }
    return super.updateSpecialAiControl(delta);
  }
  private void regen(int delta) {
    if (!mAttributesWrapper.has(entityId)) return;
    Attributes attrs = mAttributesWrapper.get(entityId).attrs;
    int base = attrs.base().getValue(Stat.hpregen, 0);
    int aggregate = attrs.getValue(Stat.hpregen, 0);
    attrs.base().put(Stat.hpregen, base + delta);
    attrs.aggregate().put(Stat.hpregen, aggregate + delta);
  }
  private void modeWait(int mode, int frames) {
    startMode(mode, Engine.INVALID_ENTITY);
    if (mode == Engine.Monster.MODE_S4)
      mSequence.get(entityId).mode2 = Engine.Monster.MODE_S4;
    delayDecision(frames);
  }
  @Override protected boolean actionBlocksDecision() {
    // S4 is a held roosting mode, not an action that owns the AI clock.
    return mCasting.has(entityId) || mSequence.has(entityId)
        && mSequence.get(entityId).mode1 != Engine.Monster.MODE_S4;
  }
  @Override public void kill() {
    if (regenBonus != 0) { regen(-regenBonus); regenBonus = 0; }
    super.kill();
  }
  @Override protected void thinkWithoutTarget() { decide(Engine.INVALID_ENTITY, Float.MAX_VALUE, false); }
  @Override protected void think(int targetId, float distance, boolean combat) {
    decide(targetId, distance, combat);
  }
  private void decide(int targetId, float distance, boolean combat) {
    int health = nativeLifePercent(entityId);
    if (phase == 0 || phase == 4) {
      modeWait(Engine.Monster.MODE_S3, 8);
      phase = 1; loops = 0;
      int value = mAttributesWrapper.has(entityId)
          ? mAttributesWrapper.get(entityId).attrs.getValue(Stat.hpregen, 0) : 0;
      regenBonus = value * params[4] / 8;
      regen(regenBonus);
      return;
    }
    if (phase == 1) {
      if (loops > 1 && (combat || distance < 7 || nativeUncontrolled()
          || distance < 14 && health > 50)) {
        if (regenBonus != 0) { regen(-regenBonus); regenBonus = 0; }
        modeWait(Engine.Monster.MODE_S2, 6); phase = 3;
      } else { modeWait(Engine.Monster.MODE_S4, loops != 0 ? 15 : 10); loops++; }
      return;
    }
    if (targetId == Engine.INVALID_ENTITY) { idle(10); return; }
    if (phase == 2 || phase == 3) {
      if (health < params[0] && escape(targetId, 15, 0)) { phase = 4; return; }
      if (phase == 2) {
        if (rollAiPercentage() < 33) { approach(targetId, 0); phase = 3; }
        else if (rollAiPercentage() < 15) walkClose(6, 0);
        else idle(10);
        return;
      }
      if (!combat) { approach(targetId, 0); return; }
      if (loops > 0) { attack(targetId, Engine.Monster.MODE_A2); loops = 0; return; }
      if (nativeUncontrolled() && rollAiChance(params[1]) && escape(targetId, 12, 0)) {
        phase = 2; return;
      }
      if (!rollAiChance(params[2])) idle(10);
      else attack(targetId, rollAiChance(params[3]) ? Engine.Monster.MODE_A2 : Engine.Monster.MODE_A1);
      return;
    }
    if (distance >= 15) { phase = 4; idle(15); }
    else { approach(targetId, 0); phase = 3; }
  }
}
