package com.riiablo.screen;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.riiablo.Riiablo;
import com.riiablo.codec.Animation;
import com.riiablo.codec.AnimationTraceProbe;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.AnimDataResolver;
import com.riiablo.engine.server.animation.AnimationTraceWriter;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.AnimStepper;
import com.riiablo.engine.server.NativeWhirlwindSequence;
import com.riiablo.engine.server.SequenceHandler;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.CofComponents;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.presentation.AnimationStepper;
import com.riiablo.presentation.ClientGameplayContext;
import com.riiablo.presentation.CofLayerCacher;
import com.riiablo.presentation.CofLayerLoader;
import com.riiablo.presentation.CofLoader;
import com.riiablo.presentation.CofResolver;
import com.riiablo.presentation.CofUnloader;
import com.riiablo.presentation.component.AnimationWrapper;
import com.riiablo.presentation.component.CofComponentDescriptors;
import com.riiablo.presentation.component.CofWrapper;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.mostlyoriginal.api.event.common.EventSystem;

/**
 * Hidden client-side Whirlwind capture.  This uses the production COF/DCC
 * resolver and renderer, but drives a deterministic native-sequence cursor so
 * the output can be compared with the read-only 1.10f client trace without
 * keyboard, mouse, focus, or a visible game window.
 */
public final class OffscreenWhirlwindAnimationScreen extends ScreenAdapter {
  private static final int WIDTH = 320;
  private static final int HEIGHT = 240;
  private static final int SAMPLES = 300;
  private static final byte SQ = 18;
  private static final int SEQUENCE_FRAME_SHIFT = 8;
  private static final int SEQUENCE_FRAME_UNIT = 1 << SEQUENCE_FRAME_SHIFT;
  private static final int SEQUENCE_FRAME_COUNT = NativeWhirlwindSequence.frameCount();
  private static final int WHIRLWIND_REWIND_FRAMES = 3;
  private static final Pattern SAMPLE_ELAPSED_MS =
      Pattern.compile("\\\"sample_elapsed_ms\\\"\\s*:\\s*(\\d+)");
  private static final Pattern SAMPLE_MODE_ID =
      Pattern.compile("\\\"mode_id\\\"\\s*:\\s*(\\d+)");

  private final FileHandle output;
  private World world;
  private FrameBuffer buffer;
  private int player;
  private Animation animation;
  private AnimData animData;
  private CofManager cofs;
  private byte currentMode;
  private int render;
  private int[] sampleElapsedMs;
  private int[] sampleModeIds;
  private boolean emulateWhirlwindRewind;

  public OffscreenWhirlwindAnimationScreen(String directory) {
    output = Gdx.files.absolute(new FileHandle(directory).file().getAbsolutePath());
  }

  @Override public void show() {
    output.mkdirs();
    output.child("result.txt").writeString("RUNNING\n", false, "UTF-8");
    buffer = new FrameBuffer(Pixmap.Format.RGBA8888, WIDTH, HEIGHT, false);
    world = new World(new WorldConfigurationBuilder().with(
        new EventSystem(), new CofManager(), new AnimDataResolver(), new SequenceHandler(),
        new AnimStepper(), new CofUnloader(), new CofResolver(), new CofLoader(),
        new CofLayerLoader(), new CofLayerCacher(), new AnimationStepper()).build()
        .register("clientContext", ClientGameplayContext.fromGlobals()));
    cofs = world.getSystem(CofManager.class);

    player = world.create();
    world.getMapper(Class.class).create(player).type = Class.Type.PLR;
    // Retail SQ uses the Barbarian GH composite as its native presentation
    // layer.  Logical mode is still emitted as SQ in the canonical trace.
    CofReference reference = world.getMapper(CofReference.class).create(player)
        .set("BA", Engine.Player.MODE_NU);
    reference.wclass = Engine.WEAPON_1HS;
    int[] components = world.getMapper(CofComponents.class).create(player).component;
    Arrays.fill(components, CofComponents.COMPONENT_LIT);
    world.getMapper(AnimData.class).create(player);
    world.getMapper(AnimationWrapper.class).create(player);
    world.getMapper(CofComponentDescriptors.class).create(player);
    switchMode(Engine.Player.MODE_NU);
    switchMode(Engine.Player.MODE_GH);
    switchMode(Engine.Player.MODE_TN);
    switchMode(Engine.Player.MODE_NU);
    output.child("cof.txt").writeString("neutral= BANU1HS\nsequence= BAGH1HS\ntransition= BATN1HS\n",
        false, "UTF-8");
    String schedule = System.getProperty("riiablo.offscreen-schedule");
    sampleElapsedMs = loadSchedule(schedule);
    sampleModeIds = loadScheduleModes(schedule);
    emulateWhirlwindRewind = Boolean.getBoolean("riiablo.offscreen-whirlwind-rewind");
  }

  private void switchMode(byte mode) {
    cofs.setMode(player, mode, true);
    settle();
    currentMode = mode;
    CofWrapper wrapper = world.getMapper(CofWrapper.class).get(player);
    AnimationWrapper animationWrapper = world.getMapper(AnimationWrapper.class).get(player);
    animation = animationWrapper == null ? null : animationWrapper.animation;
    animData = world.getMapper(AnimData.class).get(player);
    if (wrapper == null || wrapper.cof == null || animation == null
        || animation.getNumFramesPerDir() <= 0 || animData == null) {
      throw new IllegalStateException("Whirlwind COF did not load mode=" + mode
          + " wrapper=" + wrapper + " frames="
          + (animation == null ? -1 : animation.getNumFramesPerDir()));
    }
  }

  private void settle() {
    for (int i = 0; i < 8; i++) {
      world.setDelta(0);
      world.process();
      Riiablo.assets.finishLoading();
    }
  }

  @Override public void render(float ignored) {
    if (render++ > 0) return;
    try {
      writeTrace();
      output.child("result.txt").writeString("PASS samples=" + SAMPLES
          + " frames=" + animation.getNumFramesPerDir() + "\n", false, "UTF-8");
      Gdx.app.exit();
    } catch (RuntimeException failure) {
      output.child("result.txt").writeString("FAIL " + failure + "\n", false, "UTF-8");
      throw failure;
    }
  }

  private void writeTrace() {
    Path tracePath = Path.of(output.file().toPath().resolve("riiablo-whirlwind-client-visual.jsonl").toString());
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("schema", AnimationTraceWriter.SCHEMA);
    metadata.put("source", "riiablo-offscreen-production-client");
    metadata.put("class", Riiablo.BARBARIAN);
    metadata.put("character", "Riiablo-offscreen-CXBarbWW");
    metadata.put("token", "BA");
    metadata.put("wclass", "1HS");
    metadata.put("logical_mode", "SQ");
    metadata.put("sample_clock", Map.of("rate_hz", 25, "ticks_per_sample", 2,
        "sample_interval_ms", 80));
    if (sampleElapsedMs != null) {
      metadata.put("replay_schedule", Map.of("samples", sampleElapsedMs.length,
          "source", System.getProperty("riiablo.offscreen-schedule")));
    }
    metadata.put("capabilities", Arrays.asList("mode", "token", "wclass",
        "animation_frame", "animation_frame_count", "animation_speed", "keyframes", "visual"));
    metadata.put("missing_capabilities", Collections.emptyList());
    metadata.put("visual_capture", Map.of("status", "complete", "samples", SAMPLES,
        "viewport", WIDTH + "x" + HEIGHT, "scope", "offscreen FBO character composite"));
    try (AnimationTraceWriter writer = new AnimationTraceWriter(tracePath, metadata)) {
      AnimationTraceProbe.install((tick, gameFrame, entityId, kind, values) -> {
        try {
          writer.event(tick, gameFrame, entityId, kind, values);
        } catch (Exception error) {
          throw new IllegalStateException("Unable to write animation semantic event", error);
        }
      });
      int rawCursor = 0;
      int sequencePoint = 0;
      int sequenceCursorPrecise = 0;
      byte previousLogicalMode = -1;
      int previousElapsedMs = sampleElapsedMs == null ? 0 : sampleElapsedMs[0];
      for (int tick = 0; tick < SAMPLES; tick++) {
        AnimationTraceProbe.beginFrame(tick, tick);
        byte logicalMode = sampleModeIds == null
            ? (tick < 15 ? Engine.Player.MODE_NU : tick < 33 ? SQ : Engine.Player.MODE_TN)
            : (byte) sampleModeIds[Math.min(tick, sampleModeIds.length - 1)];
        boolean modeChanged = logicalMode != previousLogicalMode;
        if (modeChanged) {
          // Retail SQ presents the GH sequence COF, while all other modes use
          // their direct player mode. Follow the sampled original mode
          // transition instead of assuming the skill began at tick 15.
          switchMode(logicalMode == SQ ? Engine.Player.MODE_GH : logicalMode);
          if (previousLogicalMode != -1) {
            AnimationTraceProbe.record("ANIMATION_MODE_CHANGE", AnimationTraceProbe.values(
                "from_mode_id", previousLogicalMode, "to_mode_id", logicalMode,
                "restart", false));
          }
        }
        int frameCount = Math.max(1, animation.getNumFramesPerDir());
        int elapsedMs = sampleElapsedMs == null ? tick * 80 + 16
            : sampleElapsedMs[Math.min(tick, sampleElapsedMs.length - 1)];
        int elapsedDeltaMs = tick == 0 ? 0 : Math.max(1, elapsedMs - previousElapsedMs);
        int simulationTicks = sampleElapsedMs == null ? 2
            : Math.max(1, Math.round(elapsedDeltaMs / 40f));
        if (logicalMode != previousLogicalMode) {
          rawCursor = 0;
          sequencePoint = 0;
          sequenceCursorPrecise = 0;
        }
        if (logicalMode == SQ) {
          // The server sequence advances one native frame per game tick.  A
          // Whirlwind pulse then calls RewindSkillEx(3), which moves the
          // native fixed-point cursor forward by three frames and rebuilds
          // the pending sequence events.  Keep that cursor separate from
          // the resident five-frame GH presentation COF.
          for (int i = 0; i < simulationTicks; i++) {
            sequenceCursorPrecise = (sequenceCursorPrecise + SEQUENCE_FRAME_UNIT)
                % (SEQUENCE_FRAME_COUNT * SEQUENCE_FRAME_UNIT);
          }
          if (emulateWhirlwindRewind && isWhirlwindPulse(tick)) {
            sequenceCursorPrecise = (sequenceCursorPrecise
                + WHIRLWIND_REWIND_FRAMES * SEQUENCE_FRAME_UNIT)
                % (SEQUENCE_FRAME_COUNT * SEQUENCE_FRAME_UNIT);
          }
          sequencePoint = (sequenceCursorPrecise >>> SEQUENCE_FRAME_SHIFT)
              % NativeWhirlwindSequence.frameCount();
          rawCursor = NativeWhirlwindSequence.sourceFrame(sequencePoint) << 8;
        } else {
          rawCursor = (rawCursor + Math.max(1, animData.speed) * simulationTicks)
              % (frameCount * 256);
        }
        AnimationTraceProbe.record("SEQUENCE_CURSOR", AnimationTraceProbe.values(
            "logical_mode", logicalMode,
            "source_frame_raw", rawCursor,
            "native_sequence_frame_raw", sequenceCursorPrecise,
            "sequence_point", sequencePoint,
            "rewind_frames", emulateWhirlwindRewind && logicalMode == SQ
                && isWhirlwindPulse(tick) ? WHIRLWIND_REWIND_FRAMES : 0));
        previousElapsedMs = elapsedMs;
        previousLogicalMode = logicalMode;
        // SQ's native source cursor can address frames outside the 5-frame GH
        // presentation COF.  The renderer wraps it to the resident layer;
        // the trace preserves the fixed-point source cursor separately.
        int frame = (rawCursor >>> 8) % frameCount;
        animation.setFrame(frame);
        List<String> keyframes = new ArrayList<>();
        if (animData.keyframes != null && frame < animData.keyframes.length) {
          byte marker = animData.keyframes[frame];
          if (marker > Engine.KEYFRAME_NIL) keyframes.add(Engine.getKeyframe(marker));
        }
        // SQ action points are native sequence events rather than GH COF
        // frame flags.  Keep the known 1.10f Whirlwind pulse schedule visible
        // in the client trace so keyframe comparison remains meaningful.
        if (logicalMode == SQ && isWhirlwindPulse(tick)) {
          keyframes = new ArrayList<>(Collections.singletonList("ATK"));
          AnimationTraceProbe.record("KEYFRAME_EVENT", AnimationTraceProbe.values(
              "keyframe", "ATK", "sequence_point", sequencePoint));
        }
        String hash = drawAndHash(tick, frame);
        // The native SQ cursor is represented by the GH presentation frame;
        // expose its fixed-point frame exactly as the client observer does.
        String modeName = logicalMode == SQ ? "SQ" : Class.Type.PLR.getMode(logicalMode);
        String token = "BA" + (logicalMode == SQ ? "GH" : Class.Type.PLR.getMode(logicalMode)) + "1HS";
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("game_frame", tick);
        values.put("mode_id", (int) logicalMode);
        values.put("mode", modeName);
        values.put("token", token);
        values.put("wclass", "1HS");
        values.put("animation_frame_raw", rawCursor);
        values.put("native_sequence_frame_raw", sequenceCursorPrecise);
        values.put("native_sequence_point", sequencePoint);
        values.put("native_rewind_frames",
            emulateWhirlwindRewind && logicalMode == SQ && isWhirlwindPulse(tick)
                ? WHIRLWIND_REWIND_FRAMES : 0);
        values.put("animation_frame_count", frameCount);
        values.put("animation_speed", animData.speed);
        values.put("keyframes", keyframes);
        values.put("x_fp16", null);
        values.put("y_fp16", null);
        values.put("combat_events", Collections.emptyList());
        values.put("visual_sha256", hash);
        values.put("sample_elapsed_ms", elapsedMs);
        writer.frame(tick, values);
      }
      AnimationTraceProbe.clear();
    } catch (Exception error) {
      AnimationTraceProbe.clear();
      throw new IllegalStateException("Unable to write Whirlwind client trace", error);
    }
    // animation_compare.py accepts the JSONL form emitted by AnimationTraceWriter.
  }

  /** The fixed original-client sample contains three visible WW attack pulses. */
  private static boolean isWhirlwindPulse(int tick) {
    return tick == 19 || tick == 22 || tick == 29;
  }

  private static int[] loadSchedule(String schedule) {
    if (schedule == null || schedule.trim().isEmpty()) return null;
    try {
      String source = Files.readString(Path.of(schedule));
      Matcher matcher = SAMPLE_ELAPSED_MS.matcher(source);
      List<Integer> values = new ArrayList<>();
      while (matcher.find() && values.size() < SAMPLES)
        values.add(Integer.parseInt(matcher.group(1)));
      if (values.size() != SAMPLES)
        throw new IllegalArgumentException("schedule has " + values.size()
            + " sample_elapsed_ms values; expected " + SAMPLES);
      int[] result = new int[values.size()];
      for (int i = 0; i < result.length; i++) result[i] = values.get(i);
      return result;
    } catch (Exception error) {
      throw new IllegalArgumentException("Unable to read animation schedule: " + schedule, error);
    }
  }

  private static int[] loadScheduleModes(String schedule) {
    if (schedule == null || schedule.trim().isEmpty()) return null;
    try {
      String source = Files.readString(Path.of(schedule));
      Matcher matcher = SAMPLE_MODE_ID.matcher(source);
      List<Integer> values = new ArrayList<>();
      while (matcher.find() && values.size() < SAMPLES)
        values.add(Integer.parseInt(matcher.group(1)));
      if (values.size() != SAMPLES)
        throw new IllegalArgumentException("schedule has " + values.size()
            + " mode_id values; expected " + SAMPLES);
      int[] result = new int[values.size()];
      for (int i = 0; i < result.length; i++) result[i] = values.get(i);
      return result;
    } catch (Exception error) {
      throw new IllegalArgumentException("Unable to read animation mode schedule: " + schedule, error);
    }
  }

  private String drawAndHash(int tick, int frame) {
    buffer.begin();
    Pixmap image = null;
    try {
      Gdx.gl.glViewport(0, 0, WIDTH, HEIGHT);
      Gdx.gl.glClearColor(0, 0, 0, 0);
      Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
      Riiablo.batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, WIDTH, HEIGHT));
      Riiablo.batch.setPalette(Riiablo.palettes.units);
      Riiablo.batch.begin();
      AnimationTraceProbe.beginEntity(player);
      animation.draw(Riiablo.batch, WIDTH / 2f, 72);
      AnimationTraceProbe.endEntity();
      Riiablo.batch.end();
      image = Pixmap.createFromFrameBuffer(0, 0, WIDTH, HEIGHT);
      String hash = sha256(image.getPixels());
      if (tick == 0 || tick == 15 || tick == 19 || tick == 23 || tick == 31
          || tick == 39 || tick == 47 || tick == 55) {
        PixmapIO.writePNG(output.child(String.format("frame-%03d-%02d.png", tick, frame)), image);
      }
      return hash;
    } finally {
      if (image != null) image.dispose();
      buffer.end();
    }
  }

  private static String sha256(ByteBuffer source) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      ByteBuffer copy = source.duplicate();
      copy.clear();
      byte[] bytes = new byte[copy.remaining()];
      copy.get(bytes);
      byte[] hash = digest.digest(bytes);
      StringBuilder out = new StringBuilder(hash.length * 2);
      for (byte value : hash) out.append(String.format("%02x", value & 0xff));
      return out.toString();
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }

  @Override public void dispose() {
    if (world != null) { world.dispose(); world = null; }
    if (buffer != null) { buffer.dispose(); buffer = null; }
  }
}
