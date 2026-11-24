package com.riiablo.engine.server;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonWriter;
import com.riiablo.RiiabloTest;
import com.riiablo.presentation.component.Overlay;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Real skill-start and overlay systems with DCC headers; no GPU or original audio trace. */
public final class ArcticBlastOverlayOriginalAnalysis {
  public static void main(String[] args)throws Exception {
    RiiabloTest.setup();
    try {
      Map<String,Object> result=new LinkedHashMap<>();
      result.put("source_run_id",new JsonReader().parse(Files.readString(Path.of(args[0]))).getString("run_id"));
      result.put("scope","Actual Actioneer SkillStart/OverlayManager/OverlayStepper lifecycle, loaded DCC header; no GPU or native audio observation");
      List<Object> cases=new ArrayList<>();
      for(int delay:new int[]{0,12,1000})try(ArcticBlastSequenceIntegrationTest.Fixture f=new ArcticBlastSequenceIntegrationTest.Fixture()) {
        f.overlayLoadAfter=delay;f.start();List<Object> states=new ArrayList<>();
        for(int tick=0;tick<45;tick++) {
          f.input(true,new Vector2(4530,5642));f.step();
          if(tick==30)f.overlayLoadAfter=0;
          Overlay layer=find(f.world.getMapper(Overlay.class).get(f.caster));
          Map<String,Object> s=new LinkedHashMap<>();s.put("tick",tick);s.put("present",layer!=null);
          if(layer!=null) {
            s.put("native_age",layer.nativeFrame);s.put("loaded",layer.isLoaded);s.put("persistent",layer.persistent);
            s.put("native_rendering",layer.usesNativeRendering());
            if(layer.isLoaded){s.put("frame",layer.animation.getFrame());s.put("frame_delta",layer.animation.getFrameDelta());s.put("blend_mode",layer.animation.getLayer(0).getBlendMode());}
          }
          states.add(s);
        }
        Map<String,Object> c=new LinkedHashMap<>();c.put("asset_delay_updates",delay);c.put("requests",f.overlays.requests);c.put("states",states);cases.add(c);
      }
      result.put("cases",cases);
      StringWriter text=new StringWriter();JsonWriter w=new JsonWriter(text);w.setOutputType(JsonWriter.OutputType.json);write(w,result);w.flush();
      Files.writeString(Path.of(args[1]),text+"\n");
    } finally {RiiabloTest.teardown();}
  }
  private static Overlay find(Overlay root) {
    if(root==null)return null;
    if(root.entry!=null&&"arctic_blast_cast".equals(root.entry.overlay))return root;
    for(Overlay o:root.layers)if(o.entry!=null&&"arctic_blast_cast".equals(o.entry.overlay))return o;
    return null;
  }
  private static void write(JsonWriter w,Object value)throws Exception {
    if(value instanceof Map){w.object();for(Map.Entry<?,?>e:((Map<?,?>)value).entrySet()){w.name(e.getKey().toString());write(w,e.getValue());}w.pop();}
    else if(value instanceof Iterable){w.array();for(Object e:(Iterable<?>)value)write(w,e);w.pop();}else w.value(value);
  }
}
