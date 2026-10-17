package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.ComponentMapper;
import com.riiablo.engine.server.component.NativeAiState;
import org.junit.jupiter.api.Test;

class BigheadHitMemoryTest {
  @Test
  @SuppressWarnings("unchecked")
  void hitMemorySelectsForcedRangedBranchOnlyForNativeModes() {
    Bighead ai = new Bighead(7);
    ai.mNativeAiState = mock(ComponentMapper.class);
    NativeAiState memory = new NativeAiState();
    when(ai.mNativeAiState.get(7)).thenReturn(memory);
    for (int mode = 0; mode < 32; mode++) {
      memory.previousMode = mode;
      assertEquals(mode == 3 || mode == 19, ai.checkSpecialAiState(), "mode " + mode);
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void neutralTransitionPreservesHitMemoryAndAttackClearsIt() {
    Bighead ai = new Bighead(7);
    ai.mNativeAiState = mock(ComponentMapper.class);
    assertFalse(ai.checkSpecialAiState());
    NativeAiState memory = new NativeAiState();
    when(ai.mNativeAiState.get(7)).thenReturn(memory);
    memory.onModeChange(3);
    memory.onModeChange(1);
    assertTrue(ai.checkSpecialAiState());
    memory.onModeChange(4);
    assertFalse(ai.checkSpecialAiState());
  }
}
