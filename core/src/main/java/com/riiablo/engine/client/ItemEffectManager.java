package com.riiablo.engine.client;

import com.artemis.BaseEntitySystem;
import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.badlogic.gdx.utils.IntMap;
import com.riiablo.Riiablo;
import com.riiablo.codec.Animation;
import com.riiablo.engine.client.component.AnimationWrapper;
import com.riiablo.engine.server.component.Item;

@All({Item.class, AnimationWrapper.class})
public class ItemEffectManager extends BaseEntitySystem {
  protected ComponentMapper<Item> mItem;
  protected ComponentMapper<AnimationWrapper> mAnimationWrapper;
  private final IntMap<Animation.AnimationListener> dropSoundListeners = new IntMap<>();

  @Override
  protected void inserted(int entityId) {
    com.riiablo.item.Item item = mItem.get(entityId).item;
    final String sound = item.getDropSound();
    Animation.AnimationListener listener = new Animation.AnimationListener() {
      @Override
      public void onTrigger(Animation animation, int frame) {
        Riiablo.audio.play(sound, true);
        animation.removeAnimationListener(frame, this);
      }
    };
    dropSoundListeners.put(entityId, listener);
    replayDrop(entityId);
  }

  /** Replays the native ground-item bounce and its two drop sounds in place. */
  public boolean replayDrop(int entityId) {
    if (!mItem.has(entityId) || !mAnimationWrapper.has(entityId)) return false;
    com.riiablo.item.Item item = mItem.get(entityId).item;
    Animation animation = mAnimationWrapper.get(entityId).animation;
    Animation.AnimationListener listener = dropSoundListeners.get(entityId);
    if (item == null || listener == null) return false;
    int soundFrame = item.getDropFxFrame();
    if (!animation.containsAnimationListener(soundFrame, listener)) {
      animation.addAnimationListener(soundFrame, listener);
    }
    animation.restart();
    Riiablo.audio.play("item_flippy", true);
    return true;
  }

  @Override
  protected void removed(int entityId) {
    dropSoundListeners.remove(entityId);
  }

  @Override
  protected void processSystem() {}
}
