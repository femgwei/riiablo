package com.riiablo.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;

import com.riiablo.Riiablo;
import com.riiablo.codec.DC6;
import com.riiablo.codec.StringTBL;
import com.riiablo.graphics.PaletteIndexedBatch;
import com.riiablo.loader.DC6Loader;
import com.riiablo.net.Account;
import com.riiablo.save.D2S;
import com.riiablo.save.D2SReader;
import com.riiablo.widget.CharacterSelectButton;
import com.riiablo.widget.Label;
import com.riiablo.widget.TextButton;

public class SelectCharacterScreen2 extends ScreenAdapter {
  private static final String TAG = "SelectCharacterScreen2";

  final AssetDescriptor<DC6> characterselectscreenEXPDescriptor = new AssetDescriptor<>("data\\global\\ui\\CharSelect\\charselectbckg.dc6", DC6.class, DC6Loader.DC6Parameters.COMBINE);
  TextureRegion characterselectscreenEXP;

  final AssetDescriptor<DC6> MediumButtonBlankDescriptor = new AssetDescriptor<>("data\\global\\ui\\FrontEnd\\MediumButtonBlank.dc6", DC6.class);
  final AssetDescriptor<DC6> TallButtonBlankDescriptor = new AssetDescriptor<>("data\\global\\ui\\CharSelect\\TallButtonBlank.dc6", DC6.class);

  private Stage stage;
  private Button btnExit;
  private Button btnOK;
  private Button btnCreateNewCharacter;
  private Button btnDeleteCharacter;

  private CharacterSelectButton        selected;
  private Array<CharacterSelectButton> characters;

  private Account account;

  private Table deleteConfirm;
  private Button btnDeleteYes;
  private Button btnDeleteNo;

  public SelectCharacterScreen2(Account account) {
    this.account = account;
    load();

    stage = new Stage(Riiablo.viewport, Riiablo.batch);

    Riiablo.assets.finishLoadingAsset(characterselectscreenEXPDescriptor);
    characterselectscreenEXP = Riiablo.assets.get(characterselectscreenEXPDescriptor).getTexture();

    ChangeListener clickListener = new ChangeListener() {
      @Override
      public void changed(ChangeEvent event, Actor actor) {
        if (actor == btnExit) {
          Riiablo.client.popScreen();
        } else if (actor == btnOK) {
          assert selected != null;
          Riiablo.client.pushScreen(new LobbyScreen(SelectCharacterScreen2.this.account, Riiablo.charData.clear().load(selected.getD2S())));
        } else if (actor == btnCreateNewCharacter) {
          Riiablo.client.pushScreen(new CreateCharacterScreen(() ->
              Riiablo.client.clearAndSet(new SelectCharacterScreen2(
                  SelectCharacterScreen2.this.account))));
        } else if (actor == btnDeleteCharacter) {
          toggleDeleteCharacterDialog(true);
        } else if (actor == btnDeleteYes) {
          deleteSelectedCharacter();
          toggleDeleteCharacterDialog(false);
        } else if (actor == btnDeleteNo) {
          toggleDeleteCharacterDialog(false);
        }
      }
    };
    TextButton.TextButtonStyle tallButtonStyle = new TextButton.TextButtonStyle() {{
      Riiablo.assets.finishLoadingAsset(TallButtonBlankDescriptor);
      DC6 pages = Riiablo.assets.get(TallButtonBlankDescriptor);
      up   = disabled = new TextureRegionDrawable(pages.getTexture(0));
      down = new TextureRegionDrawable(pages.getTexture(1));
      disabled = up;
      font = Riiablo.fonts.fontexocet10;
    }};
    btnDeleteCharacter = new TextButton(StringTBL.EXPANSION_OFFSET + 2744, tallButtonStyle);
    btnDeleteCharacter.addListener(clickListener);
    btnDeleteCharacter.setDisabled(true);
    btnCreateNewCharacter = new TextButton(StringTBL.EXPANSION_OFFSET + 2743, tallButtonStyle);
    btnCreateNewCharacter.addListener(clickListener);
    Table panel = new Table() {{
      final float SPACING = 4;
      add(btnDeleteCharacter).space(SPACING);
      add(btnCreateNewCharacter).space(SPACING);
      pack();
    }};
    panel.setPosition(stage.getWidth() / 2, 20, Align.bottom | Align.center);
    stage.addActor(panel);

    TextButton.TextButtonStyle mediumButtonStyle = new TextButton.TextButtonStyle() {{
      Riiablo.assets.finishLoadingAsset(MediumButtonBlankDescriptor);
      DC6 pages = Riiablo.assets.get(MediumButtonBlankDescriptor);
      up   = disabled = new TextureRegionDrawable(pages.getTexture(0));
      down = new TextureRegionDrawable(pages.getTexture(1));
      font = Riiablo.fonts.fontexocet10;
    }};

    btnExit = new TextButton(5101, mediumButtonStyle);
    btnExit.addListener(clickListener);
    btnExit.setPosition(20, 20, Align.bottomLeft);
    stage.addActor(btnExit);

    btnOK = new TextButton(5102, mediumButtonStyle);
    btnOK.addListener(clickListener);
    btnOK.setPosition(stage.getWidth() - 20, 20, Align.bottomRight);
    btnOK.setDisabled(true);
    stage.addActor(btnOK);

    createDeleteConfirm(mediumButtonStyle, clickListener);
    refreshCharacters();
  }

  private void createDeleteConfirm(TextButton.TextButtonStyle mediumButtonStyle, ChangeListener clickListener) {
    deleteConfirm = new Table();
    deleteConfirm.setFillParent(true);
    deleteConfirm.setVisible(false);
    deleteConfirm.setTouchable(Touchable.enabled);

    Table box = new Table();
    box.setBackground(Label.MODAL);

    Label label = new Label(1878, Riiablo.fonts.font16);
    label.setWrap(true);
    label.setAlignment(Align.center);

    btnDeleteYes = new TextButton("Yes", mediumButtonStyle);
    btnDeleteYes.addListener(clickListener);
    btnDeleteNo = new TextButton("No", mediumButtonStyle);
    btnDeleteNo.addListener(clickListener);

    box.add(label).width(320).pad(8).row();
    box.add(btnDeleteYes).width(120).pad(6);
    box.add(btnDeleteNo).width(120).pad(6);
    box.pack();

    deleteConfirm.add(box);
    stage.addActor(deleteConfirm);
  }

  private void toggleDeleteCharacterDialog(boolean show) {
    deleteConfirm.setVisible(show);
    btnOK.setDisabled(show || selected == null);
    btnDeleteCharacter.setDisabled(show || selected == null);
    btnCreateNewCharacter.setDisabled(show);
    btnExit.setDisabled(show);
  }

  private void deleteSelectedCharacter() {
    if (selected == null) return;
    FileHandle file = selected.getFile();
    if (file != null) {
      boolean deleted = file.delete();
      if (!deleted) {
        Gdx.app.error(TAG, "Failed to delete character file: " + file);
      }
    }
    refreshCharacters();
  }

  private void refreshCharacters() {
    if (characters != null) {
      for (CharacterSelectButton b : characters) {
        b.remove();
        b.dispose();
      }
    }
    selected = null;
    characters = new Array<>();

    FileHandle savesLocation = Riiablo.saves;
    Gdx.app.debug(TAG, "Accessing saves within " + savesLocation.toString());
    FileHandle[] saves = savesLocation.list(D2S.EXT);
    for (FileHandle save : saves) {
      Gdx.app.debug(TAG, "Loading " + save.toString());
      D2S d2s = D2SReader.INSTANCE.readD2S(save);
      CharacterSelectButton button = new CharacterSelectButton(d2s, save);
      button.addListener(new ClickListener() {
        @Override
        public void clicked(InputEvent event, float x, float y) {
          if (deleteConfirm.isVisible()) return;
          if (getTapCount() >= 2) {
            assert selected == event.getListenerActor();
            btnOK.toggle();
            return;
          }

          if (selected != null) selected.deselect();
          selected = (CharacterSelectButton) event.getListenerActor();
          selected.select();
          btnOK.setDisabled(false);
          btnDeleteCharacter.setDisabled(false);
        }
      });
      characters.add(button);
      stage.addActor(button);
      if (selected == null) {
        selected = button;
        selected.select();
      }
    }

    final int offsetX = 32;
    final int offsetY = 32;
    for (int i = 0, x, y = (int) stage.getHeight() - offsetY - CharacterSelectButton.HEIGHT; i < characters.size; i++) {
      x = (i & 1) == 0 ? offsetX : offsetX + CharacterSelectButton.WIDTH;
      CharacterSelectButton character = characters.get(i);
      character.setPosition(x, y);
      if ((i & 1) == 1) y -= CharacterSelectButton.HEIGHT;
    }

    boolean hasChars = characters.size > 0;
    btnOK.setDisabled(!hasChars);
    btnDeleteCharacter.setDisabled(!hasChars);
  }

  @Override
  public void show() {
    load();
    Riiablo.input.addProcessor(stage);
  }

  @Override
  public void hide() {
    Riiablo.input.removeProcessor(stage);
    dispose();
  }

  private void load() {
    CharacterSelectButton.loadBox();
    Riiablo.assets.load(characterselectscreenEXPDescriptor);
    Riiablo.assets.load(MediumButtonBlankDescriptor);
    Riiablo.assets.load(TallButtonBlankDescriptor);
  }

  @Override
  public void dispose() {
    CharacterSelectButton.unloadBox();
    for (CharacterSelectButton selectButton : characters) selectButton.dispose();
    Riiablo.assets.unload(characterselectscreenEXPDescriptor.fileName);
    Riiablo.assets.unload(MediumButtonBlankDescriptor.fileName);
    Riiablo.assets.unload(TallButtonBlankDescriptor.fileName);
  }

  @Override
  public void render(float delta) {
    PaletteIndexedBatch b = Riiablo.batch;
    b.begin(Riiablo.palettes.units);
    b.draw(characterselectscreenEXP, (stage.getWidth() / 2) - (characterselectscreenEXP.getRegionWidth() / 2), stage.getHeight() - characterselectscreenEXP.getRegionHeight());
    b.end();

    stage.act(delta);
    stage.draw();
  }
}
