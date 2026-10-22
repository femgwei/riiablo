package com.riiablo.screen.panel;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;
import com.riiablo.Riiablo;
import com.riiablo.codec.DC6;
import com.riiablo.widget.Button;
import com.riiablo.util.Disposal;
import com.riiablo.util.AssetReferenceScope;

public class MobilePanel extends Table implements Disposable, EscapeController {
  private final AssetManager assets = java.util.Objects.requireNonNull(Riiablo.assets, "assets");
  private final AssetReferenceScope references = new AssetReferenceScope(assets);
  private boolean disposed;
  private final PanelSession session;
  public void bindWorld(com.artemis.World world) { session.bindWorld(world); }
  final AssetDescriptor<DC6> minipanelbtnDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\minipanelbtn.DC6", DC6.class);

  Button btnCharacter;
  Button btnInventory;
  Button btnSkillTree;
  Button btnParty;
  Button btnMap;
  Button btnMessages;
  Button btnQuests;
  Button btnEscapeMenu;
  Button btnSwapWeapons;

  public MobilePanel() {
    this(Riiablo.game);
  }

  public MobilePanel(com.riiablo.screen.GameScreen owner) {
    session = new PanelSession(owner);
    try {
      initialize();
      session.finish(this);
    } catch (RuntimeException | Error failure) {
      try { dispose(); }
      catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
      throw failure;
    }
  }

  private void initialize() {
    references.load(minipanelbtnDescriptor);
    assets.finishLoadingAsset(minipanelbtnDescriptor);
    final DC6 minipanelbtn = assets.get(minipanelbtnDescriptor);
    ClickListener clickListener = new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        if (!isCurrentSession() || session.owner() == null) return;
        com.riiablo.screen.GameScreen game = session.owner();
        Actor actor = event.getListenerActor();
        if (actor == btnCharacter) {
          Actor panel = game.characterPanel;
          game.setLeftPanel(panel.isVisible() ? null : panel);
        } else if (actor == btnSwapWeapons) {
          game.requestWeaponSwap();
        } else if (actor == btnInventory) {
          Actor panel = game.inventoryPanel;
          game.setRightPanel(panel.isVisible() ? null : panel);
        } else if (actor == btnSkillTree) {
          Actor panel = game.spellsPanel;
          game.setRightPanel(panel.isVisible() ? null : panel);
        } else if (actor == btnParty) {
          Actor panel = game.partyPanel;
          game.setLeftPanel(panel.isVisible() ? null : panel);
        } else if (actor == btnMap) {

        } else if (actor == btnMessages) {
          game.input.setVisible(!game.input.isVisible());
          game.input.getStage().setKeyboardFocus(game.input);
        } else if (actor == btnQuests) {
          Actor panel = game.questsPanel;
          game.setLeftPanel(panel.isVisible() ? null : panel);
        } else if (actor == btnEscapeMenu) {
          if (game.escapePanel.isVisible()) game.escapePanel.close();
          else game.escapePanel.open();
        }
      }
    };
    btnCharacter = new Button(new Button.ButtonStyle() {{
      up   = new TextureRegionDrawable(minipanelbtn.getTexture(0));
      down = new TextureRegionDrawable(minipanelbtn.getTexture(1));
    }});
    btnCharacter.addListener(clickListener);
    btnInventory = new Button(new Button.ButtonStyle() {{
      up   = new TextureRegionDrawable(minipanelbtn.getTexture(2));
      down = new TextureRegionDrawable(minipanelbtn.getTexture(3));
    }});
    btnInventory.addListener(clickListener);
    btnSkillTree = new Button(new Button.ButtonStyle() {{
      up   = new TextureRegionDrawable(minipanelbtn.getTexture(4));
      down = new TextureRegionDrawable(minipanelbtn.getTexture(5));
    }});
    btnSkillTree.addListener(clickListener);
    btnParty = new Button(new Button.ButtonStyle() {{
      up   = new TextureRegionDrawable(minipanelbtn.getTexture(6));
      down = new TextureRegionDrawable(minipanelbtn.getTexture(7));
    }});
    btnParty.addListener(clickListener);
    btnMap = new Button(new Button.ButtonStyle() {{
      up   = new TextureRegionDrawable(minipanelbtn.getTexture(8));
      down = new TextureRegionDrawable(minipanelbtn.getTexture(9));
    }});
    btnMap.addListener(clickListener);
    session.own(btnMap); // This optional control is intentionally not attached to the layout.
    btnMessages = new Button(new Button.ButtonStyle() {{
      up   = new TextureRegionDrawable(minipanelbtn.getTexture(10));
      down = new TextureRegionDrawable(minipanelbtn.getTexture(11));
    }});
    btnMessages.addListener(clickListener);
    btnQuests = new Button(new Button.ButtonStyle() {{
      up   = new TextureRegionDrawable(minipanelbtn.getTexture(12));
      down = new TextureRegionDrawable(minipanelbtn.getTexture(13));
    }});
    btnQuests.addListener(clickListener);
    btnEscapeMenu = new Button(new Button.ButtonStyle() {{
      up   = new TextureRegionDrawable(minipanelbtn.getTexture(14));
      down = new TextureRegionDrawable(minipanelbtn.getTexture(15));
    }});
    btnEscapeMenu.addListener(clickListener);
    btnSwapWeapons = new Button(new Button.ButtonStyle() {{
      up   = new TextureRegionDrawable(minipanelbtn.getTexture(16));
      down = new TextureRegionDrawable(minipanelbtn.getTexture(17));
    }});
    btnSwapWeapons.addListener(clickListener);
    final float size = 50;
    add(btnCharacter).size(size);
    add(btnParty).size(size);
    add(btnQuests).size(size);
    add(btnSwapWeapons).size(size);
    add().expandX();
    add(btnInventory).size(size);
    add(btnSkillTree).size(size);
    //add(btnMap).size(size);
    add(btnMessages).size(size);
    add(btnEscapeMenu).size(size);
    pack();
    setTouchable(Touchable.childrenOnly);
    //setDebug(true, true);
  }

  @Override
  public Actor getEscapeButton() {
    return isCurrentSession() ? btnEscapeMenu : null;
  }

  @Override
  public void dispose() {
    if (disposed) return;
    disposed = true;
    try (Disposal cleanup = new Disposal()) {
      cleanup.run(() -> setVisible(false));
      cleanup.run(() -> setTouchable(Touchable.disabled));
      cleanup.run(this::clearListeners);
      for (Button button : new Button[] {
          btnCharacter, btnInventory, btnSkillTree, btnParty, btnMap,
          btnMessages, btnQuests, btnEscapeMenu, btnSwapWeapons}) {
        if (button == null) continue;
        cleanup.run(button::clearListeners);
        // Escape is reparented directly onto Stage after the first frame.
        cleanup.run(button::remove);
        cleanup.dispose(button);
      }
      cleanup.run(this::clearChildren);
      cleanup.run(this::remove);
      cleanup.run(() -> session.dispose(this));
      cleanup.dispose(references);
    } finally {
      btnCharacter = null;
      btnInventory = null;
      btnSkillTree = null;
      btnParty = null;
      btnMap = null;
      btnMessages = null;
      btnQuests = null;
      btnEscapeMenu = null;
      btnSwapWeapons = null;
    }
  }

  @Override
  public void draw(Batch batch, float parentAlpha) {
    if (!isCurrentSession()) return;
    super.draw(batch, parentAlpha);
  }

  @Override
  public void act(float delta) {
    if (isCurrentSession()) super.act(delta);
  }

  private boolean isCurrentSession() { return !disposed && session.isCurrent(); }
  @Override public Actor hit(float x, float y, boolean touchable) {
    return isCurrentSession() ? super.hit(x, y, touchable) : null;
  }
}
