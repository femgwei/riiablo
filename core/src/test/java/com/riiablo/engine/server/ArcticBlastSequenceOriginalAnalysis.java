package com.riiablo.engine.server;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.state.StateId;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Actual cast/input/animation/state pipeline; callbacks are never injected by the diagnostic. */
public final class ArcticBlastSequenceOriginalAnalysis {
  public static void main(String[] args) throws Exception {
    RiiabloTest.setup();
    try {
      JsonValue original=new JsonReader().parse(Files.readString(Path.of(args[0])));
      Map<String,Object> result=new LinkedHashMap<>();
      result.put("source_run_id",original.getString("run_id"));
      result.put("scope","Level99 naked Druid skill1, real local Actioneer/input-port/sequence/state/missile/animation pipeline; no GPU or injected skill callbacks");
      result.put("time_reference","initialized_S1_source_frame_zero");
      List<Object> cases=new ArrayList<>();
      for(int hold:new int[]{16,40})try(ArcticBlastSequenceIntegrationTest.Fixture f=new ArcticBlastSequenceIntegrationTest.Fixture()) {
        f.start();f.input(true,new Vector2(4530,5642));f.step();
        if(f.reference().sequenceFrame!=0)throw new IllegalStateException("Missing initial S1 pose");
        int zero=f.probe.tick;List<Object> states=new ArrayList<>();
        for(int tick=0;tick<=hold+40;tick++) {
          if(tick>0){f.input(tick<=hold,new Vector2(4530,5642));f.step();}
          Sequence s=f.world.getMapper(Sequence.class).get(f.caster);
          Map<String,Object> state=new LinkedHashMap<>();
          state.put("tick",tick);state.put("held",tick<=hold);
          state.put("mode",(int)f.reference().mode);
          state.put("source_frame",f.reference().sequenceFrame);
          state.put("sequence_cursor",s==null?null:s.nativeArcticCursor);
          state.put("sequence_remaining",s==null?null:s.nativeArcticRemaining);
          state.put("inferno_state",f.world.getMapper(UnitStates.class).get(f.caster).stateList.hasState(StateId.INFERNO));
          state.put("client_streams_alive",f.visuals());state.put("server_streams_alive",f.carriers());
          states.add(state);
        }
        Map<String,Object> cast=new LinkedHashMap<>();
        cast.put("hold_ticks",hold);cast.put("states",states);
        List<Integer> callbacks=new ArrayList<>();for(int tick:f.probe.callbacks)callbacks.add(tick-zero);
        cast.put("callback_ticks",callbacks);cast.put("time_zero_tick",zero);
        cases.add(cast);
      }
      result.put("cases",cases);
      result.put("limitations",List.of("Original client polling is non-atomic and its input clock is not frame-aligned to the local fixed-step requests",
          "Local state refresh adapter is covered for bounded hold/release; native server mana, damage, packets and all interrupt/FCR cases are not established",
          "Caster COF/source frame is exercised without body DCC GPU rendering; progress overlay and actual DCC direction remain unobserved"));
      StringWriter text=new StringWriter();JsonWriter writer=new JsonWriter(text);
      writer.setOutputType(JsonWriter.OutputType.json);write(writer,result);writer.flush();
      Files.writeString(Path.of(args[1]),text+"\n");
    } finally {RiiabloTest.teardown();}
  }
  private static void write(JsonWriter writer,Object value)throws Exception {
    if(value instanceof Map) {
      writer.object();for(Map.Entry<?,?> e:((Map<?,?>)value).entrySet()){writer.name(e.getKey().toString());write(writer,e.getValue());}writer.pop();
    } else if(value instanceof Iterable) {
      writer.array();for(Object e:(Iterable<?>)value)write(writer,e);writer.pop();
    } else writer.value(value);
  }
}
