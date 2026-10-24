package com.riiablo.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import org.junit.jupiter.api.Test;

class D2SCheckpointRegistryTest extends RiiabloTest {
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
