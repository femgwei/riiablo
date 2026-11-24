package com.riiablo.presentation;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonWriter;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.COF;
import com.riiablo.codec.DCC;
import com.riiablo.engine.server.*;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.CofTransforms;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.presentation.component.Overlay;
import com.riiablo.save.CharData;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.mostlyoriginal.api.event.common.EventSystem;

/** Runs current Enchant/overlay systems; reports differences without changing production. */
public final class EnchantOriginalAnalysis {
  public static void main(String[] args) throws Exception {
    RiiabloTest.setup();
    try {
      Map<String, Object> result = simulate();
      StringWriter text = new StringWriter(); JsonWriter writer = new JsonWriter(text);
      writer.setOutputType(JsonWriter.OutputType.json); write(writer, result); writer.flush();
      Files.writeString(Path.of(args[0]), text + "\n");
    } finally { RiiabloTest.teardown(); }
  }

  static Map<String, Object> simulate() {
    Riiablo.colors = new com.riiablo.Colors();
    AssetManager saved = Riiablo.assets;
    AssetManager assets = Riiablo.assets = mock(AssetManager.class);
    DCC dcc = spy(DCC.loadFromFile(Riiablo.mpqs.resolve("data/global/overlays/FireEnchant.dcc")));
    doNothing().when(dcc).loadDirection(anyInt());
    when(assets.contains(anyString())).thenReturn(true);
    when(assets.isLoaded(any(AssetDescriptor.class))).thenReturn(true);
    when(assets.get(any(AssetDescriptor.class))).thenReturn(dcc);
    RecordingOverlays overlays = new RecordingOverlays();
    ServerEntityFactory factory = new ServerEntityFactory();
    World world = new World(new WorldConfigurationBuilder().with(new EventSystem(), new CofManager(),
        new ItemManager(), new ObjectInteractor(), new WarpInteractor(), new ItemInteractor(),
        overlays, new StateOverlaySystem(), new OverlayStepper(), new ServerSkillSystem(true), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0))
        .register("townPortalRegistry", new com.riiablo.engine.server.portal.TownPortalRegistry())
        .register("clientContext", ClientGameplayContext.fromGlobals()));
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("enchant-probe", Riiablo.SORCERESS);
      data.setSkillLevel(52, 1);
      world.getMapper(Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(4523, 5642);
      Attributes attrs = Attributes.obtainStandard();
      for (short stat : new short[] {Stat.level, Stat.hitpoints, Stat.maxhp, Stat.mana, Stat.maxmana})
        attrs.base().put(stat, stat == Stat.level ? 1 : 1000);
      attrs.reset(); world.getMapper(AttributesWrapper.class).create(owner).attrs = attrs;
      CofTransforms colors = world.getMapper(CofTransforms.class).create(owner);
      colors.transform[COF.Component.RH] = 3; colors.transform[COF.Component.LH] = 7;
      List<Object> states = new ArrayList<>();
      Map<String, Object> result = new LinkedHashMap<>();
      world.setDelta(1f / 25f);
      for (int tick = 0; tick <= 100; tick++) {
        overlays.tick = tick;
        if (tick == 0 || tick == 45) {
          com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(52);
          world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(owner, 52, -1,
              new Vector2(4523, 5642), skill.srvdofunc, skill.cltdofunc));
        }
        world.process();
        UnitState state = world.getMapper(UnitStates.class).get(owner).stateList.getState(StateId.ENCHANT);
        if (tick == 0) {
          result.put("duration", state.duration);
          result.put("fire_min", state.getStatContributionValue(Stat.firemindam));
          result.put("fire_max", state.getStatContributionValue(Stat.firemaxdam));
          result.put("attack_rating_percent", state.getStatContributionValue(Stat.item_tohit_percent));
        }
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("tick", tick); sample.put("right_hand_transform", colors.transform[COF.Component.RH]);
        sample.put("left_hand_transform", colors.transform[COF.Component.LH]);
        Overlay visual = world.getMapper(Overlay.class).get(owner);
        if (visual != null && visual.entry != null) {
          sample.put("overlay", visual.entry.overlay); sample.put("loaded", visual.isLoaded);
          if (visual.isLoaded) {
            sample.put("frame", visual.animation.getFrame());
            sample.put("frame_duration", visual.animation.getFrameDuration());
            sample.put("native_rendering", visual.usesNativeRendering());
            sample.put("blend_mode", visual.animation.getLayer(0).getBlendMode());
          }
        }
        states.add(sample);
      }
      world.getMapper(UnitStates.class).get(owner).stateList.removeState(StateId.ENCHANT);
      world.process();
      result.put("restored_right_hand_transform", colors.transform[COF.Component.RH]);
      result.put("restored_left_hand_transform", colors.transform[COF.Component.LH]);
      result.put("activation_requests", overlays.requests);
      result.put("states", states);
      result.put("scope", "Two production Do025 casts at ticks 0 and 45; actual OverlayManager/StateOverlaySystem/OverlayStepper, MPQ DCC header, no GPU");
      result.put("limitations", Arrays.asList("SkillDo is injected directly; caster keyframes and start audio are not simulated",
          "Initial hand transforms are synthetic; original weapon pixels/palette tables are not compared",
          "State expiry is emulated by removing it after capture; no full 3600-tick production lifecycle",
          "No target attack damage, PvP, mastery, synergy, networking or different skill levels"));
      return result;
    } finally { world.dispose(); Riiablo.assets = saved; dcc.dispose(); }
  }

  @com.artemis.annotations.All(Overlay.class)
  static final class RecordingOverlays extends OverlayManager {
    int tick;
    final List<Object> requests = new ArrayList<>();
    @Override public void set(int entity, String overlay) {
      requests.add(Map.of("tick", tick, "overlay", overlay)); super.set(entity, overlay);
    }
  }
  private static void write(JsonWriter w, Object value) throws Exception {
    if (value instanceof Map) {
      w.object(); for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) { w.name(e.getKey().toString()); write(w, e.getValue()); } w.pop();
    } else if (value instanceof Iterable) { w.array(); for (Object item : (Iterable<?>) value) write(w, item); w.pop(); }
    else w.value(value);
  }
}
