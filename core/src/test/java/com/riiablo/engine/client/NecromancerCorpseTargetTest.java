package com.riiablo.engine.client;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.camera.IsometricCamera;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.shared.component.Selectable;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NecromancerCorpseTargetTest extends RiiabloTest {
  @Test
  void corpseToolsCanSelectDeadSpritesWithoutMakingThemOrdinaryAttackTargets() {
    HoveredManager hover = new HoveredManager();
    IsometricCamera camera = new IsometricCamera() {
      @Override public Vector2 unproject(Vector2 value) { return value; }
      @Override public Vector2 toScreen(Vector2 value) { return value; }
    };
    ClientHoverPresentationPort presentation = new ClientHoverPresentationPort() {
      public ClientHoverBounds bounds(int id) { return new ClientHoverBounds(-8, 8, 16, 16); }
      public boolean containsLabel(int id, float x, float y) { return false; }
      public void setHighlighted(int id, boolean value) {}
      public boolean isUnsummonSelected() { return false; }
    };
    World world = new World(new WorldConfigurationBuilder().with(hover).build()
        .register("iso", camera).register("hoverPresentation", presentation));
    try {
      hover.setEnabled(false); // synchronize subscriptions without polling pointer input
      int owner = world.create();
      world.getMapper(Position.class).create(owner);
      int corpse = world.create();
      world.getMapper(Position.class).create(corpse).position.set(20, 20);
      Monster monster = world.getMapper(Monster.class).create(corpse);
      monster.monstats = Riiablo.files.monstats.get("fallen1");
      monster.monstats2 = Riiablo.files.monstats2.get(monster.monstats.MonStatsEx);
      Attributes attrs = Attributes.obtainStandard();
      attrs.base().put(Stat.hitpoints, 0);
      attrs.reset();
      world.getMapper(AttributesWrapper.class).create(corpse).attrs = attrs;
      Corpse marker = world.getMapper(Corpse.class).create(corpse)
          .reset(Corpse.DEFAULT_DURATION, true);
      // DeathHandler removed Selectable, but Corpse supplies the other subscription branch.
      world.process();
      assertEquals(corpse, hover.getHoveredAt(owner, 20, 20, true));
      assertEquals(Engine.INVALID_ENTITY, hover.getHoveredAt(owner, 20, 20, false));
      world.getMapper(Selectable.class).create(corpse);
      world.process();
      assertEquals(Engine.INVALID_ENTITY, hover.getHoveredAt(owner, 20, 20, false));
      marker.usable = false;
      assertEquals(Engine.INVALID_ENTITY, hover.getHoveredAt(owner, 20, 20, true));
      marker.usable = true;
      marker.fading = true;
      assertEquals(Engine.INVALID_ENTITY, hover.getHoveredAt(owner, 20, 20, true));
    } finally { world.dispose(); }
  }
}
