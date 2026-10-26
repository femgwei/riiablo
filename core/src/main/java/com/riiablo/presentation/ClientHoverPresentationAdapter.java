package com.riiablo.presentation;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import com.badlogic.gdx.Input;
import com.riiablo.engine.client.ClientHoverBounds;
import com.riiablo.engine.client.ClientHoverPresentationPort;
import com.riiablo.engine.server.component.Item;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.presentation.component.AnimationWrapper;
import com.riiablo.presentation.component.BBoxWrapper;
import com.riiablo.presentation.component.Label;
import com.riiablo.presentation.component.Overlay;

/** Owns presentation component access used by gameplay hover selection. */
public final class ClientHoverPresentationAdapter extends BaseSystem
    implements ClientHoverPresentationPort {
  protected ComponentMapper<BBoxWrapper> mBBoxWrapper;
  protected ComponentMapper<AnimationWrapper> mAnimationWrapper;
  protected ComponentMapper<Label> mLabel;
  protected ComponentMapper<Overlay> mOverlay;
  protected ComponentMapper<Item> mItem;

  @Wire(name = "clientContext", failOnNull = false)
  protected ClientGameplayContext clientContext ;

  @Override public ClientHoverBounds bounds(int entityId) {
    if (!mBBoxWrapper.has(entityId) || mBBoxWrapper.get(entityId).box == null) return null;
    com.riiablo.codec.util.BBox box = mBBoxWrapper.get(entityId).box;
    return new ClientHoverBounds(box.xMin, box.yMax, box.width, box.height);
  }

  @Override public boolean containsLabel(int entityId, float screenX, float screenY) {
    if (!mLabel.has(entityId) || mLabel.get(entityId).actor == null) return false;
    return containsLabelPoint(mLabel.get(entityId).actor, screenX, screenY);
  }

  static boolean containsLabelPoint(com.badlogic.gdx.scenes.scene2d.Actor actor,
      float screenX, float screenY) {
    float x = actor.getX();
    float y = actor.getY();
    return x <= screenX && screenX <= x + actor.getWidth()
        && y <= screenY && screenY <= y + actor.getHeight();
  }

  @Override public void setHighlighted(int entityId, boolean highlighted) {
    if (mAnimationWrapper.has(entityId)) {
      mAnimationWrapper.get(entityId).animation.setHighlighted(highlighted);
    }
    if (mOverlay.has(entityId)) {
      mOverlay.get(entityId).animation.setHighlighted(highlighted);
    }
  }

  @Override public boolean isUnsummonSelected() {
    return clientContext != null && clientContext.charData() != null
        && (clientContext.charData().getAction(Input.Buttons.LEFT) == SkillId.UNSUMMON
            || clientContext.charData().getAction(Input.Buttons.RIGHT) == SkillId.UNSUMMON);
  }

  @Override protected void processSystem() {}
}
