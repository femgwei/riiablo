package com.riiablo.engine.server.component;

import com.artemis.PooledComponent;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;

/** MonsterData.dwAiState (previous mode/hit memory), not AiControl special state. */
@Transient
@PooledWeaver
public class NativeAiState extends PooledComponent {
  public int previousMode = 1;
  public boolean isUncontrolled() { return previousMode == 3 || previousMode == 19; }
  @Override protected void reset() { previousMode = 1; }

  /** MONSTERS ModeChange preserves GH/KB memory across neutral transitions. */
  public void onModeChange(int oldMode) {
    if (oldMode == 1) return;
    if (previousMode >= 16) previousMode -= 16;
    else if (!(previousMode == 13 && oldMode == 3)) previousMode = oldMode;
  }
}
