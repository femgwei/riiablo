package com.riiablo.engine.server;

import com.artemis.Aspect;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonWriter;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Sequence;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Release after actual client missile insertions; no synthetic SkillDo events or count compensation. */
public final class ArcticBlastBirthBoundaryOriginalAnalysis {
  public static void main(String[] args)throws Exception {
    RiiabloTest.setup();
    try {
      Map<String,Object> result=new LinkedHashMap<>();
      result.put("source_run_id",new JsonReader().parse(Files.readString(Path.of(args[0]))).getString("run_id"));
      List<Object> cases=new ArrayList<>();
      for(int count:new int[]{3,15})cases.add(simulate(count));
      result.put("cases",cases);
      result.put("elapsed_interval_reference_run",new JsonReader().parse(Files.readString(Path.of(args[2]))).getString("run_id"));
      List<Object> timed=new ArrayList<>();
      for(int hold:new int[]{16,40})timed.add(simulateElapsedIntervals(hold));
      result.put("elapsed_interval_cases",timed);
      result.put("scope","Actual Actioneer and client missile subscription; release on Nth inserted presentation missile, without manual SkillDo or extra emissions");
      StringWriter text=new StringWriter();JsonWriter w=new JsonWriter(text);w.setOutputType(JsonWriter.OutputType.json);write(w,result);w.flush();
      Files.writeString(Path.of(args[1]),text+"\n");
    } finally {RiiabloTest.teardown();}
  }
  static Map<String,Object> simulate(int count) {
    try(ArcticBlastSequenceIntegrationTest.Fixture f=new ArcticBlastSequenceIntegrationTest.Fixture()) {
      List<Map<String,Object>> births=new ArrayList<>();
      EntitySubscription subscription=f.world.getAspectSubscriptionManager().get(Aspect.all(Missile.class));
      subscription.addSubscriptionListener(new EntitySubscription.SubscriptionListener() {
        @Override public void inserted(IntBag entities) {
          for(int i=0;i<entities.size();i++) {
            int id=entities.get(i);Missile m=f.world.getMapper(Missile.class).get(id);
            if(m==null||m.skillId!=230||!m.presentationOnly)continue;
            Map<String,Object> birth=new LinkedHashMap<>();birth.put("tick",f.probe.tick);
            birth.put("entity",id);birth.put("missile",m.missile.Missile);birth.put("native_age",m.nativeFrame);
            births.add(birth);
          }
        }
        @Override public void removed(IntBag entities) {}
      });
      f.start();int release=-1;Map<String,Object> boundary=new LinkedHashMap<>();
      for(int tick=1;tick<=120;tick++) {
        f.input(release<0,new Vector2(4530,5642));f.step();
        if(release<0&&births.size()>=count) {
          if(births.size()!=count)throw new IllegalStateException("Skipped requested local birth boundary");
          release=tick;Sequence s=f.world.getMapper(Sequence.class).get(f.caster);
          boundary.put("observed_births",births.size());boundary.put("sequence_cursor",s.nativeArcticCursor);
          boundary.put("source_frame",f.reference().sequenceFrame);boundary.put("remaining",s.nativeArcticRemaining);
          f.input(false,new Vector2(4530,5642));
        }
      }
      if(release<0)throw new IllegalStateException("No native client birth boundary reached");
      Map<String,Object> result=new LinkedHashMap<>();result.put("release_after_births",count);
      result.put("release_tick",release);result.put("boundary",boundary);result.put("births",births);
      result.put("final_mode",(int)f.reference().mode);result.put("final_streams",f.liveStreams());
      result.put("callback_ticks",new ArrayList<>(f.probe.callbacks));
      return result;
    }
  }

  /** Hold duration starts at the initialized source-frame-zero pose, not its publication tick. */
  static Map<String,Object> simulateElapsedIntervals(int hold) {
    try(ArcticBlastSequenceIntegrationTest.Fixture f=new ArcticBlastSequenceIntegrationTest.Fixture()) {
      List<Map<String,Object>> births=new ArrayList<>();
      f.world.getAspectSubscriptionManager().get(Aspect.all(Missile.class)).addSubscriptionListener(new EntitySubscription.SubscriptionListener() {
        @Override public void inserted(IntBag bag) {
          for(int i=0;i<bag.size();i++) {
            Missile m=f.world.getMapper(Missile.class).get(bag.get(i));
            if(m!=null&&m.skillId==230&&m.presentationOnly) {
              Map<String,Object>b=new LinkedHashMap<>();b.put("tick",f.probe.tick);b.put("entity",bag.get(i));births.add(b);
            }
          }
        }
        @Override public void removed(IntBag bag) {}
      });
      f.start();f.input(true,new Vector2(4530,5642));f.step();
      if(f.reference().sequenceFrame!=0)throw new IllegalStateException("No initialized Arctic Blast birth pose");
      int zero=f.probe.tick;
      for(int tick=1;tick<=hold+40;tick++) {f.input(tick<=hold,new Vector2(4530,5642));f.step();}
      Map<String,Object> result=new LinkedHashMap<>();result.put("hold_elapsed_intervals",hold);
      result.put("time_zero_tick",zero);result.put("time_zero_source_frame",0);
      result.put("release_tick",zero+hold);result.put("births",births);
      result.put("callback_ticks",new ArrayList<>(f.probe.callbacks));result.put("final_streams",f.liveStreams());
      result.put("time_reference","First initialized S1 frame0 pose; initialization is not an elapsed interval");
      return result;
    }
  }
  private static void write(JsonWriter w,Object value)throws Exception {
    if(value instanceof Map){w.object();for(Map.Entry<?,?>e:((Map<?,?>)value).entrySet()){w.name(e.getKey().toString());write(w,e.getValue());}w.pop();}
    else if(value instanceof Iterable){w.array();for(Object e:(Iterable<?>)value)write(w,e);w.pop();}else w.value(value);
  }
}
