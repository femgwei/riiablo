package com.riiablo.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import org.junit.jupiter.api.Test;

class D2SCheckpointRegistryTest extends RiiabloTest {
  @Test
  void runtimePotionStateIsCapturedAndInstalledBeforeTheFactoryOrBinding() {
    Binding source = new Binding(newCharacter("PotionHero"));
    CharData.PotionCheckpoint potion = new CharData.PotionCheckpoint(-12345, 60, 9, 17, 13);
    source.data.restorePotionCheckpoint(potion);
    D2SCheckpointRegistry registry = new D2SCheckpointRegistry();
    registry.register(3, source);
    D2SCheckpoint archive = registry.capture();
    assertEquals(potion.canonical(), archive.entry(3).potionCheckpoint().canonical());
    D2SCheckpointRegistry target = new D2SCheckpointRegistry((id, entry, decoded) -> {
      assertEquals(potion.canonical(), decoded.capturePotionCheckpoint().canonical());
      return new Binding(decoded);
    });
    assertTrue(target.restore(archive).success());
    assertEquals(potion.canonical(), target.get(3).get().capturePotionCheckpoint().canonical());
    // A legacy D2S has no runtime metadata; absence must not reuse the live timer.
    D2SCheckpoint legacy = D2SCheckpoint.of(java.util.Collections.singletonMap(3,
        D2SCheckpoint.entry(3, 0, archive.entry(3).bytes())));
    assertNull(legacy.entry(3).potionCheckpoint());
    assertTrue(target.restore(legacy).success());
    assertEquals("potion-v1:0:0:0:0:0", target.get(3).get().capturePotionCheckpoint().canonical());
  }

  @Test
  void invalidPotionStateRejectsAndFailedBindingPreservesLiveRuntime() {
    assertThrows(IllegalArgumentException.class,
        () -> new CharData.PotionCheckpoint(0, -1, 10, 0, 0));
    assertThrows(IllegalArgumentException.class,
        () -> new CharData.PotionCheckpoint(0, 1, 0, 0, 0));
    assertThrows(IllegalArgumentException.class,
        () -> new CharData.PotionCheckpoint(0, 0, 0, 0, -1));
    Binding first = new Binding(newCharacter("FirstHero"));
    Binding second = new Binding(newCharacter("SecondHero"));
    D2SCheckpointRegistry registry = new D2SCheckpointRegistry();
    registry.register(1, first);
    registry.register(2, second);
    D2SCheckpoint archived = registry.capture();
    CharData.PotionCheckpoint live = new CharData.PotionCheckpoint(123, 30, 90, 60, 45);
    first.data.restorePotionCheckpoint(live);
    second.fail = true;
    assertTrue(registry.restore(archived).rolledBack);
    assertEquals(live.canonical(), first.data.capturePotionCheckpoint().canonical());
  }

  @Test
  void unregisterRemovesDeletedPlayerAndAllowsRecycledEntityId() {
    D2SCheckpointRegistry registry = new D2SCheckpointRegistry();
    registry.register(3, new Binding(newCharacter("OldHero")));
    registry.unregister(3);
    assertTrue(registry.capture().entries().isEmpty());
    registry.register(3, new Binding(newCharacter("NewHero")));
    assertEquals("NewHero", registry.get(3).get().name);
    assertEquals(1, registry.capture().entries().size());
  }

  @Test
  void capturesValidatedBytesAndRestoresCharacterBinding() {
    Binding binding = new Binding(newCharacter("CheckpointHero"));
    binding.data.level = 42;
    D2SCheckpointRegistry registry = new D2SCheckpointRegistry();
    registry.register(17, binding);

    D2SCheckpoint checkpoint = registry.capture();
    binding.data.level = 77;
    D2SCheckpointRegistry.RestoreResult result = registry.restore(checkpoint);

    assertTrue(result.success());
    assertEquals(42, binding.data.level);
    assertEquals(1, checkpoint.entries().size());
    assertEquals(checkpoint.entry(17).checksum(),
        D2SReader.INSTANCE.calculateChecksum(
            com.riiablo.io.ByteInput.wrap(checkpoint.entry(17).bytes())));
  }

  @Test
  void missingBindingRequiresExplicitFactoryAndCanBeRecreated() {
    Binding source = new Binding(newCharacter("FactoryHero"));
    D2SCheckpointRegistry original = new D2SCheckpointRegistry();
    original.register(3, source);
    D2SCheckpoint checkpoint = original.capture();

    D2SCheckpointRegistry restored = new D2SCheckpointRegistry(
        (entityId, entry, decoded) -> new Binding(decoded));
    restored.restore(checkpoint);

    assertEquals("FactoryHero", restored.get(3).get().name);
    assertEquals(source.data.level, restored.get(3).get().level);
  }

  @Test
  void failedBindingLeavesPreviouslyInstalledDataInPlace() {
    Binding first = new Binding(newCharacter("FirstHero"));
    Binding second = new Binding(newCharacter("SecondHero"));
    D2SCheckpointRegistry registry = new D2SCheckpointRegistry();
    registry.register(1, first);
    registry.register(2, second);
    D2SCheckpoint checkpoint = registry.capture();
    CharData before = first.data;
    second.fail = true;

    D2SCheckpointRegistry.RestoreResult result = registry.restore(checkpoint);

    assertTrue(!result.success());
    assertTrue(result.rolledBack);
    assertEquals(before, first.data);
  }

  private static CharData newCharacter(String name) {
    return CharData.obtain().set(Riiablo.NORMAL, true, name, Riiablo.AMAZON);
  }

  private static final class Binding implements D2SCheckpointRegistry.Binding {
    CharData data;
    boolean fail;

    Binding(CharData data) { this.data = data; }
    @Override public CharData get() { return data; }
    @Override public void set(CharData data) {
      if (fail) throw new IllegalStateException("injected binding failure");
      this.data = data;
    }
    @Override public int difficulty() { return Riiablo.NORMAL; }
  }
}
