package com.riiablo.engine;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Declarative phase/read-write contract for systems participating in a fixed tick.
 *
 * <p>The contract is deliberately independent from Artemis.  A headless runner can
 * register the systems it assembles and check an access before executing it, while
 * the existing runtime remains free to install the same systems in its current order.
 */
public final class SimulationPhaseContract {
  private final Map<String, Binding> bindings = new LinkedHashMap<>();
  private final Set<String> structuralResources = new LinkedHashSet<>();

  /** Marks a resource whose writes are only legal in {@code STRUCTURAL_COMMIT}. */
  public void markStructuralResource(String resource) {
    structuralResources.add(requireName(resource, "resource"));
  }

  public void register(String system, SimulationTickCoordinator.Phase phase,
      Collection<String> reads, Collection<String> writes) {
    register(system, phase, reads, writes, Collections.<String>emptySet());
  }

  /**
   * Registers one system exactly once.  Structural writes are explicit so a caller
   * cannot accidentally hide entity/component mutation in a normal simulation phase.
   */
  public void register(String system, SimulationTickCoordinator.Phase phase,
      Collection<String> reads, Collection<String> writes,
      Collection<String> structuralWrites) {
    String systemName = requireName(system, "system");
    Objects.requireNonNull(phase, "phase");
    Set<String> readSet = copyNames(reads, "reads");
    Set<String> writeSet = copyNames(writes, "writes");
    Set<String> structuralSet = copyNames(structuralWrites, "structuralWrites");
    if (!writeSet.containsAll(structuralSet)) {
      throw new IllegalArgumentException("structuralWrites must be included in writes");
    }
    for (String resource : structuralSet) {
      if (phase != SimulationTickCoordinator.Phase.STRUCTURAL_COMMIT) {
        throw new IllegalArgumentException(
            "structural resource may only be written in STRUCTURAL_COMMIT: " + resource);
      }
    }
    if (bindings.containsKey(systemName)) {
      throw new IllegalStateException("system already registered: " + systemName);
    }
    structuralResources.addAll(structuralSet);
    bindings.put(systemName, new Binding(systemName, phase, readSet, writeSet, structuralSet));
  }

  public Binding binding(String system) {
    return bindings.get(requireName(system, "system"));
  }

  public Set<String> systems() {
    return Collections.unmodifiableSet(new LinkedHashSet<>(bindings.keySet()));
  }

  public boolean mayRead(String system, SimulationTickCoordinator.Phase phase, String resource) {
    Binding binding = binding(system);
    return binding != null && binding.phase == phase && binding.reads.contains(resource);
  }

  public boolean mayWrite(String system, SimulationTickCoordinator.Phase phase, String resource) {
    Binding binding = binding(system);
    if (binding == null || binding.phase != phase || !binding.writes.contains(resource)) return false;
    return !structuralResources.contains(resource)
        || phase == SimulationTickCoordinator.Phase.STRUCTURAL_COMMIT;
  }

  public void assertMayRead(String system, SimulationTickCoordinator.Phase phase, String resource) {
    if (!mayRead(system, phase, resource)) {
      throw new IllegalStateException("undeclared phase read: " + system + " -> " + resource);
    }
  }

  public void assertMayWrite(String system, SimulationTickCoordinator.Phase phase, String resource) {
    if (!mayWrite(system, phase, resource)) {
      throw new IllegalStateException("undeclared phase write: " + system + " -> " + resource);
    }
  }

  public static final class Binding {
    public final String system;
    public final SimulationTickCoordinator.Phase phase;
    public final Set<String> reads;
    public final Set<String> writes;
    public final Set<String> structuralWrites;

    private Binding(String system, SimulationTickCoordinator.Phase phase,
        Set<String> reads, Set<String> writes, Set<String> structuralWrites) {
      this.system = system;
      this.phase = phase;
      this.reads = Collections.unmodifiableSet(reads);
      this.writes = Collections.unmodifiableSet(writes);
      this.structuralWrites = Collections.unmodifiableSet(structuralWrites);
    }
  }

  private static Set<String> copyNames(Collection<String> values, String name) {
    Objects.requireNonNull(values, name);
    Set<String> result = new LinkedHashSet<>();
    for (String value : values) result.add(requireName(value, name));
    return result;
  }

  private static String requireName(String value, String name) {
    if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name);
    return value;
  }
}
