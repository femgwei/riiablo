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
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.*;
import com.riiablo.engine.server.combat.CombatSystem;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.component.Class;
import com.riiablo.presentation.*;
import com.riiablo.presentation.component.*;
import net.mostlyoriginal.api.event.common.EventSystem;

/** Real retail assets, production reaction/sequence systems and actual GL.
 * A controlled combat/render stage, deliberately independent of map generation.
 * The attacker is a deterministic monster attack packet; no mouse/input is used.
 */
public final class OffscreenBlockRenderScreen extends ScreenAdapter {
  private final FileHandle output;
  private World world;
  private FrameBuffer buffer;
  private boolean done;
  private int player;
  private int pixels;

  public OffscreenBlockRenderScreen(String directory) {
    output = Gdx.files.absolute(new FileHandle(directory).file().getAbsolutePath());
  }

  @Override public void show() {
    output.mkdirs();
    output.child("result.txt").writeString("RUNNING\n", false, "UTF-8");
    buffer = new FrameBuffer(Pixmap.Format.RGBA8888, 320, 240, false);
    world = new World(new WorldConfigurationBuilder().with(
        new EventSystem(), new CofManager(), new AnimDataResolver(), new SequenceHandler(),
        new AnimStepper(), new CofUnloader(), new CofResolver(), new CofLoader(),
        new CofLayerLoader(), new CofLayerCacher(), new AnimationStepper()).build()
        .register("clientContext", ClientGameplayContext.fromGlobals()));
    player = world.create();
    world.getMapper(Class.class).create(player).type = Class.Type.PLR;
    CofReference ref = world.getMapper(CofReference.class).create(player)
        .set("AM", Engine.Player.MODE_NU);
    ref.wclass = Engine.WEAPON_1HT;
    int[] components = world.getMapper(CofComponents.class).create(player).component;
    java.util.Arrays.fill(components, CofComponents.COMPONENT_LIT);
    components[com.riiablo.codec.COF.Component.RH] = Class.Type.PLR.getComponent("jav");
    components[com.riiablo.codec.COF.Component.SH] = Class.Type.PLR.getComponent("buc");
    world.getMapper(AnimData.class).create(player);
    world.getMapper(AnimationWrapper.class).create(player);
    world.getMapper(CofComponentDescriptors.class).create(player);
    settle();
  }

  /** Drain resource transitions without advancing the simulation clock. */
  private void settle() {
    for (int i = 0; i < 8; i++) {
      world.setDelta(0);
      world.process();
      Riiablo.assets.finishLoading();
    }
  }

  @Override public void render(float ignored) {
    if (done) return;
    done = true;
    try {
      verify();
    } catch (RuntimeException | AssertionError failure) {
      output.child("result.txt").writeString("FAIL " + failure + "\n", false, "UTF-8");
      throw failure;
    }
    Gdx.app.exit();
  }

  private void verify() {
    // Prewarm BL through the same presentation pipeline, then restore NU.
    // This test isolates playback from disk/decode latency.
    CofManager cofs = world.getSystem(CofManager.class);
    cofs.setMode(player, Engine.Player.MODE_BL, true);
    settle();
    cofs.setMode(player, Engine.Player.MODE_NU, true);
    settle();
    CombatSystem combat = new CombatSystem(bound -> 0);
    CombatSystem.AttackerData attacker = new CombatSystem.AttackerData();
    attacker.alwaysHit = true;
    com.riiablo.codec.excel.MonStats.Entry fallen = Riiablo.files.monstats.get("fallen1");
    attacker.minDamage = fallen.A1MinD[0];
    attacker.maxDamage = fallen.A1MaxD[0];
    CombatSystem.DefenderData defender = new CombatSystem.DefenderData();
    defender.isPlayer = true;
    defender.level = 1;
    defender.dexterity = 25;
    defender.currentLife = defender.maxLife = 100;
    defender.canBlock = true;
    defender.blockChance = 75;
    defender.currentFrame = 100;
    CombatSystem.CombatResult result = combat.calculateAttack(attacker, defender);
    require(result.blocked && result.blockAnimationAllowed, "combat did not permit BL");
    HitReactionResolver.queue(world, Riiablo.files, player, result, HitReactionResolver.Source.MELEE);
    settle();
    CofReference ref = world.getMapper(CofReference.class).get(player);
    require(ref.mode == Engine.Player.MODE_BL, "reaction did not select BL");
    Animation animation = world.getMapper(AnimationWrapper.class).get(player).animation;
    require(world.getMapper(CofDescriptor.class).get(player).descriptor.fileName
        .toLowerCase(java.util.Locale.ROOT).endsWith("ambl1ht.cof"), "wrong mode resource path");
    require(animation.getCOF() == Riiablo.assets.get(
        world.getMapper(CofDescriptor.class).get(player).descriptor), "wrong BL COF");
    require(animation.getLayer(com.riiablo.codec.COF.Component.SH) != null, "missing shield layer");
    int shieldMask = 1 << com.riiablo.codec.COF.Component.SH;
    AnimationRenderProbe probe = new AnimationRenderProbe(player, ref.mode, ref.animationRestartId)
        .requireComponents(shieldMask);
    AnimationRenderProbe missingShield = new AnimationRenderProbe(player, ref.mode, ref.animationRestartId)
        .requireComponents(shieldMask);
    AnimationRenderProbe suppressed = new AnimationRenderProbe(player, ref.mode, ref.animationRestartId);
    AnimationRenderProbe truncated = new AnimationRenderProbe(player, ref.mode, ref.animationRestartId);
    draw(animation, suppressed, -1, false);
    int ticks = 0;
    do {
      if (ref.mode == Engine.Player.MODE_BL) {
        animation.getLayer(com.riiablo.codec.COF.Component.SH).setAlpha(0);
        try { draw(animation, missingShield, ticks, true); }
        finally { animation.getLayer(com.riiablo.codec.COF.Component.SH).setAlpha(1); }
        draw(animation, probe, ticks, true);
        if (ticks == 0) truncated.record(player, ref.mode, ref.animationRestartId,
            animation.getCOF(), animation);
      }
      world.setDelta(0.04f);
      world.process();
      Riiablo.assets.finishLoading();
    } while (ref.mode == Engine.Player.MODE_BL && ++ticks < 100);
    require(ref.mode == Engine.Player.MODE_NU, "BL did not return to NU");
    require(probe.complete(), "incomplete body submissions: " + probe);
    require(!suppressed.complete(), "suppressed draw accepted");
    require(!truncated.complete(), "truncated animation accepted");
    require(!missingShield.complete(), "invisible shield accepted");
    String report = "PASS " + probe + "\npixels=" + pixels + " ticks=" + ticks
        + "\ncombatBlocked=true returnedToNeutral=true suppressedRejected=true truncatedRejected=true missingShieldRejected=true\n"
        + "scope=prewarmed production ECS animation stage; no map/culling or network coverage\n";
    output.child("result.txt").writeString(report, false, "UTF-8");
    Gdx.app.log("BLOCK_RENDER", report);
  }

  private void draw(Animation animation, AnimationRenderProbe probe, int tick, boolean submit) {
    buffer.begin();
    try {
      Gdx.gl.glClearColor(0, 0, 0, 0);
      Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
      Pixmap baseline = Pixmap.createFromFrameBuffer(0, 0, 320, 240);
      Riiablo.batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, 320, 240));
      Riiablo.batch.setPalette(Riiablo.palettes.units);
      Riiablo.batch.begin();
      if (submit) animation.draw(Riiablo.batch, 160, 100);
      Riiablo.batch.end();
      CofReference ref = world.getMapper(CofReference.class).get(player);
      if (submit) probe.record(player, ref.mode, ref.animationRestartId,
          world.getMapper(CofWrapper.class).get(player).cof, animation);
      Pixmap image = Pixmap.createFromFrameBuffer(0, 0, 320, 240);
      try {
        int changed = 0;
        for (int y = 0; y < 240; y++) for (int x = 0; x < 320; x++) {
          if (image.getPixel(x, y) != baseline.getPixel(x, y)) changed++;
        }
        if (!submit) {
          require(changed == 0 && !probe.complete(), "suppressed rendering produced false evidence");
          return;
        }
        require(changed > 0, "blank framebuffer at tick=" + tick + " frame=" + animation.getFrame());
        pixels += changed;
        PixmapIO.writePNG(output.child("block-" + tick + "-frame-" + animation.getFrame() + ".png"), image);
      } finally { image.dispose(); baseline.dispose(); }
    } finally { buffer.end(); }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  @Override public void dispose() {
    if (world != null) { world.dispose(); world = null; }
    if (buffer != null) { buffer.dispose(); buffer = null; }
  }
}
