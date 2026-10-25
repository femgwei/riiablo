package com.riiablo.engine.server;

import com.artemis.World;
import com.artemis.annotations.Wire;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Files;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.state.*;

/** Existing SrvDo133 death/remove hook with an explicit checkpoint identity. */
public final class NativePregnancyLifecycle {
  private NativePregnancyLifecycle() {}
  private static final class Binding {
    @Wire(name = "factory", failOnNull = false) EntityFactory factory;
    @Wire(name = "serverContext", failOnNull = false) ServerGameplayContext context;
  }

  public static void bind(UnitState state, World world, int target, EntityFactory factory,
      Files files, Skills.Entry skill) {
    NativePathIdentity identity = world.getMapper(NativePathIdentity.class).get(target);
    state.removalCallback = new Callback(world, target, factory, files, skill,
        identity == null ? null : identity.token());
  }

  public static Callback restored(World world, int target, int skillId, int lifetime) {
    Binding binding = new Binding(); world.inject(binding);
    Files files = ServerGameplayContext.files(binding.context);
    Skills.Entry skill = files == null || files.skills == null ? null : files.skills.get(skillId);
    if (binding.factory == null || skill == null || skill.srvdofunc != 133) {
      throw new IllegalStateException("pregnancy runtime host/skill missing");
    }
    NativePathIdentity identity = world.getMapper(NativePathIdentity.class).get(target);
    if (lifetime < 0 || lifetime > 2 || lifetime == 1 && (identity == null || identity.token() == null
        || !identity.token().isCurrent(world, target))) {
      throw new IllegalStateException("pregnancy lifetime missing");
    }
    return new Callback(world, target, binding.factory, files, skill, lifetime == 0 ? null
        : lifetime == 1 ? identity.token() : NativePathIdentity.retiredToken(world, target));
  }

  public static final class Callback implements UnitState.RemovalCallback {
    private final World world;
    private final int target;
    private final EntityFactory factory;
    private final Files files;
    private final Skills.Entry skill;
    private final NativePathIdentity.Token token;
    private Callback(World world, int target, EntityFactory factory, Files files,
        Skills.Entry skill, NativePathIdentity.Token token) {
      this.world = world; this.target = target; this.factory = factory;
      this.files = files; this.skill = skill; this.token = token;
    }
    public int checkpointLifetime(World current, int id, int skillId) {
      Binding binding = new Binding(); current.inject(binding);
      if (current != world || id != target || skill == null || skill.Id != skillId
          || factory == null || factory != binding.factory
          || files != ServerGameplayContext.files(binding.context)
          || skill.srvdofunc != 133 || files.skills.get(skillId) != skill) {
        throw new IllegalStateException("pregnancy callback owner mismatch");
      }
      return token == null ? 0 : token.isCurrent(world, target) ? 1 : 2;
    }
    @Override public void removed(StateList states, UnitState removed) {
      if (factory == null || !world.getEntityManager().isActive(target)
          || token != null && !token.isCurrent(world, target)
          || !world.getMapper(Position.class).has(target) || positiveLife()) return;
      MonStats.Entry summon = skill.summon != null && !skill.summon.isEmpty()
          ? files.monstats.get(skill.summon) : null;
      if (summon == null) summon = files.monstats.get("painworm1");
      if (summon == null) return;
      Vector2 position = world.getMapper(Position.class).get(target).position;
      int spawned = factory.createMonster(summon, position.x, position.y);
      int mode = skill.summode != null && !skill.summode.isEmpty()
          ? files.MonMode.index(skill.summode) : Engine.Monster.MODE_NU;
      if (spawned >= 0 && mode >= 0 && mode < 16) {
        CofManager cofs = world.getSystem(CofManager.class);
        if (cofs != null) cofs.setMode(spawned, (byte) mode, true);
      }
    }
    private boolean positiveLife() {
      AttributesWrapper wrapper = world.getMapper(AttributesWrapper.class).get(target);
      StatRef life = wrapper == null || wrapper.attrs == null ? null
          : wrapper.attrs.get(Stat.hitpoints, StatRef.obtain());
      return life != null && life.asFixed() > 0f;
    }
  }
}
