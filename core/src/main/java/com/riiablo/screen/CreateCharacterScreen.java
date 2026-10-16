package com.riiablo.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.riiablo.CharacterClass;
import com.riiablo.Client;
import com.riiablo.Riiablo;
import com.riiablo.codec.Animation;
import com.riiablo.codec.DC6;
import com.riiablo.graphics.BlendMode;
import com.riiablo.graphics.PaletteIndexedBatch;
import com.riiablo.loader.DC6Loader;
import com.riiablo.save.CharData;
import com.riiablo.save.D2S;
import com.riiablo.save.D2SWriter;
import com.riiablo.widget.AnimationWrapper;
import com.riiablo.widget.CharacterCreateButton;
import com.riiablo.widget.Label;
import com.riiablo.widget.TextButton;
import com.riiablo.widget.TextField;
import com.riiablo.util.Disposal;

public class CreateCharacterScreen extends ScreenAdapter {
  private final MenuScreenResources resources = new MenuScreenResources(Riiablo.assets, Riiablo.input);
  private final Client ownerClient = Riiablo.client;
  final AssetDescriptor<DC6> CharacterCreateDescriptor = new AssetDescriptor<>("data\\global\\ui\\FrontEnd\\CharacterCreate.DC6", DC6.class, DC6Loader.DC6Parameters.COMBINE);
  TextureRegion CharacterCreate;

  final AssetDescriptor<DC6> fireDescriptor = new AssetDescriptor<>("data\\global\\ui\\FrontEnd\\fire.DC6", DC6.class);
  Animation fire;

  final AssetDescriptor<DC6> MediumSelButtonBlankDescriptor = new AssetDescriptor<>("data\\global\\ui\\FrontEnd\\MediumSelButtonBlank.dc6", DC6.class);
  final AssetDescriptor<Sound> buttonDescriptor = new AssetDescriptor<>("data\\global\\sfx\\cursor\\button.wav", Sound.class);

  final AssetDescriptor<DC6> textboxDescriptor = new AssetDescriptor<>("data\\global\\ui\\FrontEnd\\textbox.dc6", DC6.class);
  private TextField tfCharName;
  private Table charOptions;

  private Stage stage;
  private InputProcessor inputProcessor;
  private Client.ScreenBoundsListener screenBoundsListener;
  private Button btnExit;
  private Button btnOK;

  private Label                 selectedName;
  private Label                 selectedDescription;
  private CharacterCreateButton selected;

  private CharacterCreateButton amazon, assassin, necromancer, barbarian, paladin, sorceress, druid;
  private AnimationWrapper fireWrapper;
  /** Optional flow callback used by online character selection screens. */
  private final Runnable onCreated;

  public CreateCharacterScreen() {
    this(null);
  }

  /**
   * Creates the character editor with a completion callback.  The default
   * constructor keeps the original single-player behavior; online callers
   * provide a callback so the active socket/session is not replaced by a
   * local GameScreen after writing the D2S file.
   */
  public CreateCharacterScreen(Runnable onCreated) {
    this.onCreated = onCreated;
    try {
      create();
    } catch (RuntimeException | Error failure) {
      resources.fail(failure);
    }
  }

  private void create() {
    resources.load(CharacterCreateDescriptor);
    resources.load(fireDescriptor);
    resources.load(MediumSelButtonBlankDescriptor);
    resources.load(textboxDescriptor);
    resources.load(buttonDescriptor);

    stage = resources.own(new Stage(Riiablo.viewport, Riiablo.batch));

    resources.assets.finishLoadingAsset(CharacterCreateDescriptor);
    CharacterCreate = resources.assets.get(CharacterCreateDescriptor).getTexture();

    resources.assets.finishLoadingAsset(fireDescriptor);
    fire = Animation.builder()
        .layer(resources.assets.get(fireDescriptor), BlendMode.LUMINOSITY)
        .build();
    fireWrapper = new AnimationWrapper(fire);
    fireWrapper.setPosition(stage.getWidth() / 2 - 22, stage.getHeight() / 2);

    tfCharName = new TextField("", new TextField.TextFieldStyle() {{
      resources.assets.finishLoadingAsset(textboxDescriptor);
      DC6 pages = resources.assets.get(textboxDescriptor);
      background = new TextureRegionDrawable(pages.getTexture(0)) {{
        final float padding = 4;
        setLeftWidth(padding);
        setTopHeight(padding);
        setRightWidth(padding);
        setBottomHeight(padding);
      }};
      font = Riiablo.fonts.fontformal12;
      fontColor = Riiablo.colors.white;
      cursor = new TextureRegionDrawable(Riiablo.textures.white);
    }});
    tfCharName.setOnlyFontChars(true);
    tfCharName.setMaxLength(Riiablo.MAX_NAME_LENGTH);
    tfCharName.setTextFieldListener(new com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldListener() {
      @Override
      public void keyTyped(com.badlogic.gdx.scenes.scene2d.ui.TextField textField, char c) {
        if (!resources.isVisible()) return;
        btnOK.setVisible(D2S.isOriginalNameCompatible(textField.getText()));
      }
    });

    charOptions = new Table() {{
      final float SPACING = 2;
      add(new Label(5125, Riiablo.fonts.font16, Riiablo.colors.gold)).space(SPACING).left().row();
      add(tfCharName).space(SPACING).row();
      pack();
    }};
    charOptions.setVisible(false);

    final TextButton.TextButtonStyle style = new TextButton.TextButtonStyle() {{
      resources.assets.finishLoadingAsset(MediumSelButtonBlankDescriptor);
      DC6 pages = resources.assets.get(MediumSelButtonBlankDescriptor);
      up   = new TextureRegionDrawable(pages.getTexture(0));
      down = new TextureRegionDrawable(pages.getTexture(1));
      font = Riiablo.fonts.fontexocet10;
    }};
    ChangeListener clickListener = new ChangeListener() {
      @Override
      public void changed(ChangeEvent event, Actor actor) {
        if (!resources.isVisible()) return;
        if (actor == btnExit) {
          Riiablo.client.popScreen();
        } else if (actor == btnOK) {
          if (selected == null) return;
          if (!D2S.isOriginalNameCompatible(tfCharName.getText())) return;
          // Create and initialize new character
          CharData charData = Riiablo.charData.clear().set(Riiablo.NORMAL, true, tfCharName.getText(), (byte) selected.charClass.id);
          // Initialize character with starting stats from CharStats.txt
          initializeNewCharacter(charData, selected.charClass);
          // Save character to file
          boolean saved = D2SWriter.INSTANCE.save(charData);
          if (saved) {
            if (onCreated != null) {
              onCreated.run();
            } else {
              Riiablo.client.clearAndSet(new GameEntryLoadingScreen(charData));
            }
          } else {
            // TODO: Show error message to user
            Gdx.app.error("CreateCharacterScreen", "Failed to save character!");
          }
        }
      }
    };
    btnExit = resources.own(new TextButton(5101, style));
    btnExit.addListener(clickListener);
    btnExit.setPosition(20, 20, Align.bottomLeft);
    btnOK = resources.own(new TextButton(5102, style));
    btnOK.addListener(clickListener);
    btnOK.setPosition(stage.getWidth() - 20, 20, Align.bottomRight);
    btnOK.setVisible(false);

    selectedName = new Label(5127, Riiablo.fonts.font42);
    selectedName.setPosition(stage.getWidth() / 2, stage.getHeight() - 10, Align.top | Align.center);
    selectedName.setVisible(true);
    selectedDescription = new Label("", Riiablo.fonts.font16);
    selectedDescription.setVisible(false);
    selected = null;
    ClickListener selectedListener = new ClickListener(Input.Buttons.LEFT) {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        if (!resources.isVisible()) return;
        CharacterCreateButton newSelected = (CharacterCreateButton) event.getListenerActor();
        if (newSelected.getState() == CharacterCreateButton.State.BW) {
          selectedName.setVisible(false);
          selectedDescription.setVisible(false);
          btnOK.setVisible(false);
          charOptions.setVisible(false);
          selected = null;
          return;
        }

        if (selected != null && selected.isSelected()) {
          selected.deselect();
        }

        selected = newSelected;
        selectedName.setVisible(true);
        selectedName.setText(Riiablo.string.lookup(selected.charClass.name));
        selectedName.setSize(selectedName.getPrefWidth(), selectedName.getPrefHeight());
        selectedName.setPosition(stage.getWidth() / 2, stage.getHeight() - 10, Align.top | Align.center);
        selectedDescription.setText(Riiablo.string.lookup(selected.charClass.description));
        selectedDescription.setWidth(300);
        selectedDescription.setAlignment(Align.center);
        selectedDescription.setWrap(true);
        selectedDescription.setHeight(selectedDescription.getPrefHeight());
        selectedDescription.setPosition(stage.getWidth() / 2, stage.getHeight() - selectedName.getHeight() - 10, Align.top | Align.center);
        selectedDescription.setVisible(true);
        btnOK.setVisible(D2S.isOriginalNameCompatible(tfCharName.getText()));
        charOptions.setVisible(true);
        stage.setKeyboardFocus(tfCharName);
        fixActorOrder();
      }
    };

    int x = (int) stage.getWidth() / 2 - 11;
    int y = (int) (stage.getHeight() / 2) - (CharacterCreate.getRegionHeight() / 2) + 265;//225;
    amazon = ownCharacter(CharacterClass.AMAZON);
    amazon.setPosition(x - 210, y - 20);
    amazon.addListener(selectedListener);

    assassin = ownCharacter(CharacterClass.ASSASSIN);
    assassin.setPosition(x - 120, y - 25);
    assassin.addListener(selectedListener);

    necromancer = ownCharacter(CharacterClass.NECROMANCER);
    necromancer.setPosition(x - 80, y);
    necromancer.addListener(selectedListener);

    barbarian = ownCharacter(CharacterClass.BARBARIAN);
    barbarian.setPosition(x, y + 5);
    barbarian.addListener(selectedListener);

    paladin = ownCharacter(CharacterClass.PALADIN);
    paladin.setPosition(x + 195, y - 25);
    paladin.addListener(selectedListener);

    sorceress = ownCharacter(CharacterClass.SORCERESS);
    sorceress.setPosition(x + 115, y - 5);
    sorceress.addListener(selectedListener);

    druid = ownCharacter(CharacterClass.DRUID);
    druid.setPosition(x + 305, y - 50);
    druid.addListener(selectedListener);

    amazon.loadRemainingAssets();
    assassin.loadRemainingAssets();
    necromancer.loadRemainingAssets();
    barbarian.loadRemainingAssets();
    paladin.loadRemainingAssets();
    sorceress.loadRemainingAssets();
    druid.loadRemainingAssets();


    stage.addActor(barbarian);
    stage.addActor(necromancer);
    stage.addActor(sorceress);
    stage.addActor(assassin);
    stage.addActor(paladin);
    stage.addActor(amazon);
    stage.addActor(druid);
    stage.addActor(fireWrapper);
    stage.addActor(selectedName);
    stage.addActor(selectedDescription);
    stage.addActor(btnExit);
    stage.addActor(btnOK);
    stage.addActor(charOptions);
    fixActorOrder();

    inputProcessor = new InputMultiplexer() {{
      addProcessor(new InputAdapter() {
        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
          if (!resources.isVisible()) return false;
          if (selected != null && selected.isAnimating()) {
            return true;
          }

          return super.touchDown(screenX, screenY, pointer, button);
        }

        @Override
        public boolean keyDown(int keycode) {
          if (!resources.isVisible()) return false;
          switch (keycode) {
            case Input.Keys.ENTER:
              if (D2S.isOriginalNameCompatible(tfCharName.getText())) btnOK.toggle();
              return true;

            case Input.Keys.ESCAPE:
              btnExit.toggle();
              return true;

            default:
              return super.keyDown(keycode);
          }
        }
      });
      addProcessor(stage);
    }};
    resources.subscribe(() -> {}, () -> {
      try (Disposal cleanup = new Disposal()) {
        cleanup.run(stage::unfocusAll);
        // libGDX otherwise cancels held-key repetition only on a key-up or a subsequent draw.
        cleanup.run(() -> tfCharName.getDefaultInputListener().keyUp(null, Input.Keys.UNKNOWN));
      }
    });
    resources.subscribe(() -> resources.input.addProcessor(inputProcessor),
        () -> resources.input.removeProcessor(inputProcessor));
    screenBoundsListener = new Client.ScreenBoundsListener() {
      @Override
      public void updateScreenBounds(float x, float y, float width, float height) {
        if (!resources.isVisible()) return;
        charOptions.setPosition(stage.getWidth() / 2, y + 20, Align.bottom | Align.center);
      }
    };
    resources.subscribe(() -> ownerClient.addScreenBoundsListener(screenBoundsListener),
        () -> ownerClient.removeScreenBoundsListener(screenBoundsListener));
  }

  private CharacterCreateButton ownCharacter(CharacterClass type) {
    CharacterCreateButton button = resources.ownResource(new CharacterCreateButton(type, resources.assets));
    button.setInteractionEnabled(false);
    resources.subscribe(() -> button.setInteractionEnabled(true), () -> button.setInteractionEnabled(false));
    return button;
  }

  @Override public void show() {
    resources.show();
    screenBoundsListener.updateScreenBounds(0, 0, 0, 0);
  }

  private void fixActorOrder() {
    selectedName.toFront();
    selectedDescription.toFront();
    fireWrapper.toFront();
    btnExit.toFront();
    btnOK.toFront();
    charOptions.toFront();
  }

  @Override
  public void hide() {
    resources.hide();
  }

  @Override
  public void dispose() {
    selected = null;
    resources.dispose();
  }

  @Override
  public void render(float delta) {
    if (!resources.isVisible()) return;
    PaletteIndexedBatch b = Riiablo.batch;
    b.begin(Riiablo.palettes.fechar);

    int x = (int) stage.getWidth() / 2;
    int y = (int) stage.getHeight() / 2;
    int xOff = (CharacterCreate.getRegionWidth()  / 2);
    int yOff = (CharacterCreate.getRegionHeight() / 2);
    b.draw(CharacterCreate, x - xOff, y - yOff);
    b.end();

    stage.act(delta);
    stage.draw();
  }

  /**
   * Initialize a new character with starting stats from CharStats.txt
   */
  public static void initializeNewCharacter(CharData charData, CharacterClass charClass) {
    // Get starting stats from CharStats.txt
    com.riiablo.codec.excel.CharStats.Entry stats = charClass.entry();

    // Set base attributes
    com.riiablo.attributes.StatListRef base = charData.getStats().base();
    base.put(com.riiablo.attributes.Stat.strength, stats.str);
    base.put(com.riiablo.attributes.Stat.energy, stats._int); // _int is the energy/intelligence field
    base.put(com.riiablo.attributes.Stat.dexterity, stats.dex);
    base.put(com.riiablo.attributes.Stat.vitality, stats.vit);
    base.put(com.riiablo.attributes.Stat.statpts, 0);
    base.put(com.riiablo.attributes.Stat.newskills, 0);

    // Calculate derived stats
    int maxHp = stats.vit + stats.hpadd;
    int maxMana = stats._int; // _int is the energy/intelligence field
    int maxStamina = stats.stamina;

    // StatList applies ItemStatCost.ValShift itself. Passing pre-shifted values
    // here used to double-shift vitals in memory and forced the old writer to
    // rely on an accidental asInt() cancellation.
    base.put(com.riiablo.attributes.Stat.hitpoints, maxHp);
    base.put(com.riiablo.attributes.Stat.maxhp, maxHp);
    base.put(com.riiablo.attributes.Stat.mana, maxMana);
    base.put(com.riiablo.attributes.Stat.maxmana, maxMana);
    base.put(com.riiablo.attributes.Stat.stamina, maxStamina);
    base.put(com.riiablo.attributes.Stat.maxstamina, maxStamina);
    base.put(com.riiablo.attributes.Stat.level, 1);
    base.put(com.riiablo.attributes.Stat.experience, 0);
    base.put(com.riiablo.attributes.Stat.gold, 0);
    base.put(com.riiablo.attributes.Stat.goldbank, 0);

    // Panels read from aggregated stats (agg). At this point we've only populated base,
    // so sync base -> agg now to avoid null StatRef reads during GameScreen construction.
    charData.getStats().reset();

    // Set starting town to Act 1 Rogue Encampment
    charData.towns[Riiablo.NORMAL] = 0;
    charData.towns[Riiablo.NIGHTMARE] = 0;
    charData.towns[Riiablo.HELL] = 0;

    // Only the difficulty being created owns the initial town waypoint.
    charData.activateWaypoint(charData.diff, Riiablo.ACT1, 0);

    // Set map seed
    charData.mapSeed = (int) System.currentTimeMillis();

    // D2Game creates these from the ten CharStats item/itemloc/itemcount groups.
    charData.initializeStartItems(stats);

    // Skills are handled by CharData.update() automatically
  }
}
