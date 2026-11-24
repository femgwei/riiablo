package com.riiablo.engine.server;

import com.artemis.Aspect;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonWriter;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.presentation.component.AnimationWrapper;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Test-only stage observations in the actual Arctic cast and presentation pipeline. */
public final class ArcticBlastPublicationOriginalAnalysis {
  public static void main(String[] args) throws Exception {
    RiiabloTest.setup();
    try {
      Map<String,Object> output = new LinkedHashMap<>();
      output.put("legacy_fixture_order", simulate(false));
      output.put("client_assembly_relative_order", simulate(true));
      output.put("production_gameplay_changed", false);
      output.put("presentation_phase", "consume_prior_action_then_repeat_first_local_missile_update");
      output.put("production_presentation_changed", true);
      StringWriter text = new StringWriter();
      JsonWriter writer = new JsonWriter(text); writer.setOutputType(JsonWriter.OutputType.json);
      write(writer, output); writer.flush();
      Files.writeString(Path.of(args[0]), text + "\n");
    } finally { RiiabloTest.teardown(); }
  }

  static Map<String,Object> simulate(boolean clientAssemblyRelativeOrder) {
    try (ArcticBlastSequenceIntegrationTest.Fixture f = new ArcticBlastSequenceIntegrationTest.Fixture(clientAssemblyRelativeOrder)) {
      List<Object> stages = new ArrayList<>();
      List<Integer> allocated = new ArrayList<>();
      List<List<String>> stacks = new ArrayList<>();
      f.factory.allocationObserver = id -> {
        Missile m = f.world.getMapper(Missile.class).get(id);
        if (!isArctic(m)) return;
        allocated.add(id);
        stages.add(snapshot(f, "factory_return_before_client_flags", id, allocated.size()));
        if (stacks.isEmpty()) stacks.add(StackWalker.getInstance().walk(stream -> stream
            .filter(frame -> frame.getClassName().startsWith("com.riiablo."))
            .map(frame -> frame.getClassName() + "." + frame.getMethodName()).limit(18)
            .collect(java.util.stream.Collectors.toList())));
      };
      f.world.getAspectSubscriptionManager().get(Aspect.all(Missile.class))
          .addSubscriptionListener(new EntitySubscription.SubscriptionListener() {
            @Override public void inserted(IntBag entities) {
              for (int i = 0; i < entities.size(); i++) {
                int id = entities.get(i); Missile m = f.world.getMapper(Missile.class).get(id);
                if (isArctic(m) && m.presentationOnly)
                  stages.add(snapshot(f, "missile_subscription_inserted", id, allocated.size()));
              }
            }
            @Override public void removed(IntBag entities) {}
          });
      f.start();
      int zero = -1;
      for (int tick = 1; tick <= 55; tick++) {
        f.input(tick <= 25, new Vector2(4530,5642));
        f.step();
        if (zero < 0 && f.reference().sequenceFrame == 0) zero = f.probe.tick;
        stages.add(snapshot(f, "world_process_finished", -1, allocated.size()));
      }
      Map<String,Object> result = new LinkedHashMap<>();
      result.put("time_zero_tick", zero);
      result.put("client_assembly_relative_order", clientAssemblyRelativeOrder);
      result.put("production_gameplay_changed", false);
      result.put("stages", stages); result.put("allocation_stack", stacks);
      result.put("callback_ticks", new ArrayList<>(f.probe.callbacks));
      result.put("final_streams", f.liveStreams());
      result.put("scope", "Local allocation hook, actual Artemis insertion and completed world updates; no original stack or GPU submission claim");
      return result;
    }
  }

  private static boolean isArctic(Missile m) {
    return m != null && m.missile != null &&
        ("arcticblast1".equals(m.missile.Missile) || "arcticblast2".equals(m.missile.Missile));
  }

  private static Map<String,Object> snapshot(ArcticBlastSequenceIntegrationTest.Fixture f,
      String phase, int subject, int allocationCount) {
    Map<String,Object> result = new LinkedHashMap<>();
    result.put("phase", phase); result.put("tick", f.probe.tick); result.put("subject", subject);
    result.put("allocation_count", allocationCount);
    Sequence s = f.world.getMapper(Sequence.class).get(f.caster);
    result.put("sequence_cursor", s == null ? null : s.nativeArcticCursor);
    result.put("sequence_remaining", s == null ? null : s.nativeArcticRemaining);
    result.put("source_frame", f.reference().sequenceFrame);
    List<Object> missiles = new ArrayList<>();
    IntBag active = f.world.getAspectSubscriptionManager().get(Aspect.all(Missile.class)).getEntities();
    Set<Integer> ids = new LinkedHashSet<>();
    for (int i = 0; i < active.size(); i++) ids.add(active.get(i));
    if (subject >= 0) ids.add(subject);
    for (int id : ids) {
      Missile m = f.world.getMapper(Missile.class).get(id);
      if (!isArctic(m)) continue;
      Map<String,Object> state = new LinkedHashMap<>();
      state.put("entity", id); state.put("missile", m.missile.Missile);
      state.put("native_age", m.nativeFrame); state.put("presentation_only", m.presentationOnly);
      Position at = f.world.getMapper(Position.class).get(id);
      state.put("x_fixed", Math.round(at.position.x * 65536.0));
      state.put("y_fixed", Math.round(at.position.y * 65536.0));
      AnimationWrapper wrapper = f.world.getMapper(AnimationWrapper.class).get(id);
      state.put("animation_frame", wrapper == null ? null : wrapper.animation.getFrame());
      missiles.add(state);
    }
    result.put("missiles", missiles);
    return result;
  }

  private static void write(JsonWriter writer,Object value) throws Exception {
    if(value instanceof Map) {
      writer.object(); for(Map.Entry<?,?> e:((Map<?,?>)value).entrySet()) {
        writer.name(e.getKey().toString()); write(writer,e.getValue());
      } writer.pop();
    } else if(value instanceof Iterable) {
      writer.array(); for(Object e:(Iterable<?>)value) write(writer,e); writer.pop();
    } else writer.value(value);
  }
}
