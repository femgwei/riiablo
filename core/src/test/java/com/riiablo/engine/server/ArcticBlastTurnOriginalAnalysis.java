package com.riiablo.engine.server;

import com.artemis.Aspect;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonWriter;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.Velocity;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Native input delivery and local input delivery are compared at actual target changes. */
public final class ArcticBlastTurnOriginalAnalysis {
  public static void main(String[] args)throws Exception {
    RiiabloTest.setup();
    try {
      Map<String,Object> result=simulate();
      StringWriter text=new StringWriter();JsonWriter w=new JsonWriter(text);
      w.setOutputType(JsonWriter.OutputType.json);write(w,result);w.flush();
      Files.writeString(Path.of(args[0]),text+"\n");
    } finally {RiiabloTest.teardown();}
  }
  static Map<String,Object> simulate() {
    try(ArcticBlastSequenceIntegrationTest.Fixture f=new ArcticBlastSequenceIntegrationTest.Fixture()) {
      List<Map<String,Object>> births=new ArrayList<>();
      f.world.getAspectSubscriptionManager().get(Aspect.all(Missile.class)).addSubscriptionListener(new EntitySubscription.SubscriptionListener() {
        @Override public void inserted(IntBag ids) {
          for(int i=0;i<ids.size();i++) {
            int id=ids.get(i);Missile m=f.world.getMapper(Missile.class).get(id);
            if(m==null||!m.presentationOnly||m.skillId!=230)continue;
            Vector2 v=f.world.getMapper(Velocity.class).get(id).velocity;
            Map<String,Object> b=new LinkedHashMap<>();b.put("tick",f.probe.tick);b.put("entity",id);
            b.put("vx_fixed",Math.round(v.x*65536/25));b.put("vy_fixed",Math.round(v.y*65536/25));
            b.put("age",m.nativeFrame);births.add(b);
          }
        }
        @Override public void removed(IntBag ids) {}
      });
      f.start();int turned=-1,release=-1;
      for(int tick=1;tick<=100;tick++) {
        // Match the observed target change, not PostMessage delivery latency:
        // four published units, then next action pending before consumption.
        if(turned<0&&births.size()==4&&f.probe.callbacks.size()==5)turned=tick;
        f.input(release<0,turned<0?new Vector2(4530,5642):new Vector2(4522,5650));f.step();
        if(release<0&&births.size()>=15){release=tick;f.input(false,Vector2.Zero);}
      }
      if(turned<0||release<0)throw new IllegalStateException("Turn/release was not reached");
      Map<String,Object> result=new LinkedHashMap<>();result.put("births",births);
      result.put("turn_tick",turned);result.put("release_tick",release);
      result.put("final_streams",f.liveStreams());result.put("final_mode",(int)f.reference().mode);
      result.put("overlay_requests",f.overlays.requests);
      result.put("scope","Actual cast and held-input port, client assembly relative order; no manual SkillDo");
      return result;
    }
  }
  private static void write(JsonWriter w,Object value)throws Exception {
    if(value instanceof Map){w.object();for(Map.Entry<?,?>e:((Map<?,?>)value).entrySet()){w.name(e.getKey().toString());write(w,e.getValue());}w.pop();}
    else if(value instanceof Iterable){w.array();for(Object e:(Iterable<?>)value)write(w,e);w.pop();}else w.value(value);
  }
}
