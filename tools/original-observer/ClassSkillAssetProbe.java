import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonWriter;
import com.riiablo.codec.DCC;
import com.riiablo.mpq.MPQFileHandleResolver;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

/** One offline extraction for all classes; runtime evidence remains a separate gate. */
public class ClassSkillAssetProbe {
  static List<Map<String,String>> table(MPQFileHandleResolver resolver, String name) {
    String[] lines = resolver.resolve("data/global/excel/" + name + ".txt")
        .readString("ISO-8859-1").split("\\r?\\n");
    String[] headers = lines[0].split("\\t", -1);
    List<Map<String,String>> rows = new ArrayList<>();
    for (int n=1;n<lines.length;n++) {
      String[] values = lines[n].split("\\t", -1); Map<String,String> row = new LinkedHashMap<>();
      for (int i=0;i<Math.min(headers.length,values.length);i++) if (!headers[i].isEmpty()) row.put(headers[i],values[i]);
      rows.add(row);
    }
    return rows;
  }
  static Map<String,Map<String,String>> index(List<Map<String,String>> rows, String key) {
    Map<String,Map<String,String>> out = new HashMap<>();
    for (Map<String,String> row:rows) out.put(row.getOrDefault(key,"").toLowerCase(Locale.ROOT),row);
    return out;
  }
  static void write(JsonWriter json, Object value) throws Exception {
    if (value instanceof Map) {
      json.object(); for (var entry:((Map<?,?>)value).entrySet()) {json.name(entry.getKey().toString());write(json,entry.getValue());} json.pop();
    } else if (value instanceof Iterable) {json.array();for(Object item:(Iterable<?>)value)write(json,item);json.pop();}
    else json.value(value);
  }
  static String kind(Map<String,String> skill) {
    if ("1".equals(skill.get("passive"))) return "passive";
    if ("11".equals(skill.get("srvstfunc"))) return "channel";
    if (!skill.getOrDefault("summon","").isEmpty()) return "summon";
    if (!skill.getOrDefault("aurastate","").isEmpty() || !skill.getOrDefault("auratargetstate","").isEmpty()) return "state";
    if (!skill.getOrDefault("srvmissilea","").isEmpty() || !skill.getOrDefault("srvmissile","").isEmpty()
        || !skill.getOrDefault("cltmissilea","").isEmpty()) return "missile";
    return "action_or_target";
  }
  public static void main(String[] args) throws Exception {
    HeadlessApplication app = new HeadlessApplication(new ApplicationAdapter(){});
    try {
      Path output=Path.of(args[1]);Files.createDirectories(output);
      MPQFileHandleResolver resolver = new MPQFileHandleResolver(new FileHandle(args[0]));
      List<Map<String,String>> skills=table(resolver,"skills");
      Map<String,Map<String,String>> missiles=index(table(resolver,"missiles"),"Missile");
      Map<String,Map<String,String>> states=index(table(resolver,"states"),"state");
      Map<String,Map<String,String>> overlays=index(table(resolver,"overlay"),"overlay");
      List<Object> classes=new ArrayList<>();
      Map<String,Object> resources=new TreeMap<>();
      int priority=0;
      for (String code:new String[]{"dru","nec","bar","pal","ass","ama","sor"}) {
        List<Object> entries=new ArrayList<>(); Map<String,Integer> counts=new TreeMap<>();
        for (Map<String,String> skill:skills) {
          if (!code.equalsIgnoreCase(skill.get("charclass"))) continue;
          Set<String> missileNames=new TreeSet<>(), overlayNames=new TreeSet<>(), stateNames=new TreeSet<>();
          for(var entry:skill.entrySet()) {
            String key=entry.getKey().toLowerCase(Locale.ROOT), value=entry.getValue().toLowerCase(Locale.ROOT);
            if (key.contains("missile") && missiles.containsKey(value)) missileNames.add(value);
            if (key.contains("overlay") && overlays.containsKey(value)) overlayNames.add(value);
            if (key.contains("state") && states.containsKey(value)) stateNames.add(value);
          }
          for(String state:stateNames) for(String value:states.get(state).values())
            if(overlays.containsKey(value.toLowerCase(Locale.ROOT))) overlayNames.add(value.toLowerCase(Locale.ROOT));
          Set<String> visited=new HashSet<>(); ArrayDeque<String> queue=new ArrayDeque<>(missileNames);
          while(!queue.isEmpty()) {
            String name=queue.remove();if(!visited.add(name))continue;
            Map<String,String> row=missiles.get(name);
            for(var e:row.entrySet()) if(e.getKey().toLowerCase(Locale.ROOT).contains("submissile")) {
              String child=e.getValue().toLowerCase(Locale.ROOT);
              if(missiles.containsKey(child)&&missileNames.add(child))queue.add(child);
            }
          }
          Set<String> paths=new TreeSet<>();
          for(String missile:missileNames) {
            String cel=missiles.get(missile).getOrDefault("CelFile","");
            if(!cel.isEmpty()&&!"null".equalsIgnoreCase(cel)) paths.add("data/global/missiles/"+cel+".dcc");
          }
          for(String overlay:overlayNames) {
            String cel=overlays.get(overlay).getOrDefault("Filename","");
            if(!cel.isEmpty())paths.add("data/global/overlays/"+cel+".dcc");
          }
          for(String path:paths) if(!resources.containsKey(path)) {
            Map<String,Object> info=new LinkedHashMap<>();
            try {
              byte[] bytes=resolver.resolve(path).readBytes();
              info.put("sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
              DCC dcc=DCC.loadFromArray(bytes);
              try{info.put("directions",dcc.getNumDirections());info.put("frames",dcc.getNumFramesPerDir());}
              finally{dcc.dispose();}
              info.put("status","header_collected");
            }catch(Exception error){info.put("status","needs_resource_review");info.put("error",error.getClass().getSimpleName());}
            resources.put(path,info);
          }
          String kind=kind(skill);counts.merge(kind,1,Integer::sum);
          Map<String,Object> entry=new LinkedHashMap<>();
          entry.put("skill_id",Integer.parseInt(skill.get("Id")));entry.put("skill",skill.get("skill"));
          entry.put("family",kind);entry.put("row",skill);entry.put("missiles",missileNames);
          entry.put("overlays",overlayNames);entry.put("states",stateNames);entry.put("resources",paths);
          entry.put("runtime_status","not_run_in_class_batch");entry.put("comparison_status","not_compared_in_class_batch");
          entry.put("required_cases",kind.equals("passive")?List.of("state_and_equipment"):
              kind.equals("channel")?List.of("start_hold_release","direction","interrupt"):
              kind.equals("state")?List.of("self_or_ally","refresh","expiry"):
              kind.equals("summon")?List.of("create","activity","despawn"):
              List.of("cast","direction","hit_or_expiry"));
          entries.add(entry);
        }
        Map<String,Object> group=new LinkedHashMap<>();group.put("class",code);group.put("priority",++priority);
        group.put("deferred",code.equals("ama")||code.equals("sor"));group.put("skills",entries);group.put("family_counts",counts);
        classes.add(group);
      }
      Map<String,Object> result=new LinkedHashMap<>();result.put("schema",1);result.put("scope","Offline effective MPQ tables and DCC headers; no runtime or visual parity claim");
      result.put("classes",classes);result.put("resources",resources);
      result.put("missile_rows",missiles);result.put("state_rows",states);result.put("overlay_rows",overlays);
      StringWriter text=new StringWriter();JsonWriter writer=new JsonWriter(text);writer.setOutputType(JsonWriter.OutputType.json);
      write(writer,result);writer.flush();Files.writeString(output.resolve("class-skill-inventory.json"),text+"\n");
    }finally{app.exit();}
  }
}
