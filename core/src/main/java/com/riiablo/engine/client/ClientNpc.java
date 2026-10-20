package com.riiablo.engine.client;

import com.riiablo.engine.server.ai.Npc;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.exception.ExceptionUtils;
import com.artemis.Aspect;
import java.util.Locale;

import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;

import com.artemis.ComponentMapper;
import net.mostlyoriginal.api.event.common.EventSystem;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntSet;

import com.riiablo.Riiablo;
import com.riiablo.audio.Audio;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.client.DialogManager;
import com.riiablo.engine.client.MenuManager;
import com.riiablo.engine.client.ClientNetworkSynchronizer;
import com.riiablo.engine.server.quest.NativeMercenaryRewardSystem;
import com.riiablo.engine.server.pet.MercenaryManager;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.PathWrapper;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.event.NpcInteractionEvent;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.item.Item;
import com.riiablo.item.VendorGenerator;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.map.DS1;
import com.riiablo.save.CharData;
import com.riiablo.screen.panel.VendorPanel;
import com.riiablo.widget.NpcDialogBox;
import com.riiablo.widget.NpcMenu;

public final class ClientNpc extends Npc {
  private static final Logger log = LogManager.getLogger(ClientNpc.class);
  private NpcMenu menu;
  private MenuManager menuManager;
  private DialogManager dialogManager;
  protected VendorGenerator vendors;
  private Array<Item> vendorStock;
  private String vendorStockType;
  public ClientNpc(int entityId) { super(entityId); }
  @Override public void initialize() {
    super.initialize();
    name = monstats.NameStr.equalsIgnoreCase("dummy") ? monstats.Id : Riiablo.string.lookup(monstats.NameStr);
  }
  /** Stable, language-independent prefix used for speech and sound table keys. */
  private String speechPrefix() {
    String id = monstats != null ? monstats.Id : name;
    if (id == null) return "";
    id = id.trim().toLowerCase(Locale.ROOT);
    // MonStats uses variants such as warriv1/warriv2, while speech.txt
    // keys are shared by the base NPC (warriv, akara, cain, ...).
    int end = id.length();
    while (end > 0 && Character.isDigit(id.charAt(end - 1))) end--;
    return end == 0 ? id : id.substring(0, end);
  }

  public void createMenu(MenuManager menuManager, final DialogManager dialogManager) {
    Validate.validState(menu == null, "menu already initialized!");
    this.menuManager = menuManager;
    this.dialogManager = dialogManager;
    menu = menuManager.createMenu(entityId, name);

    final int entType = monstats.hcIdx;
    if (TALKERS.contains(entType)) {
      // talk
      menu.addItem(3381, menu.createSubMenu(3381)
          // introduction
          .addItem(3399, new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
              String id = speechPrefix() + "_act1_intro";
              dialogManager.setDialog(new NpcDialogBox(id, new NpcDialogBox.DialogCompletionListener() {
                @Override
                public void onCompleted(NpcDialogBox d) {
                  dialogManager.setDialog(null);
                }
              }));
            }
          })
          // gossip
          .addItem(3395, new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
              String id = speechPrefix() + "_act1_gossip_1";
              dialogManager.setDialog(new NpcDialogBox(id, new NpcDialogBox.DialogCompletionListener() {
                @Override
                public void onCompleted(NpcDialogBox d) {
                  dialogManager.setDialog(null);
                }
              }));
            }
          })
          .addItem("Quest", questClick())
          .addCancel(null)
          .build());
    } else if (com.riiablo.engine.server.quest.QuestDialogue.supportsNpc(
        com.riiablo.engine.server.quest.QuestPlayerView.npcType(monstats))) {
      // The legacy TALKERS list is mostly A1. Later acts still need a quest
      // entry, but must not acquire fake Act-I intro/gossip speech keys.
      menu.addItem("Quest", questClick());
    }

    if (REPAIRERS.contains(entType)) {
      menu.addItem(3334, new ClickListener() { // trade/repair
        @Override
        public void clicked(InputEvent event, float x, float y) {
          openVendor(VendorPanel.SMITHY, com.riiablo.net.packet.d2gs.NpcServiceType.TRADE);
        }
      });
    } else if (TRADERS.contains(entType)) {
      menu.addItem(3396, new ClickListener() { // trade
        @Override
        public void clicked(InputEvent event, float x, float y) {
          openVendor(VendorPanel.TRADER, com.riiablo.net.packet.d2gs.NpcServiceType.TRADE);
        }
      });
    }

    if (RESURRECTERS.contains(entType)
        && hasDeadMercenary(Riiablo.game == null ? Engine.INVALID_ENTITY : Riiablo.game.player)) {
      menu.addItem(resurrectionLabel(Riiablo.game.player), new ClickListener() {
        @Override
        public void clicked(InputEvent event, float x, float y) {
          resurrectMercenary(menuManager);
        }
      });
    }
    if (HIRERERS.contains(entType)) {
      menu.addItem(3397, new ClickListener() { // hire
        @Override
        public void clicked(InputEvent event, float x, float y) {
          hireMercenary(entType);
        }
      });
    }

    if (GAMBLERS.contains(entType)) {
      menu.addItem(3398, new ClickListener() { // gamble
        @Override
        public void clicked(InputEvent event, float x, float y) {
          openVendor(VendorPanel.GAMBLER, com.riiablo.net.packet.d2gs.NpcServiceType.GAMBLE);
        }
      });
    }

    menu.addCancel(new NpcMenu.CancellationListener() {
        @Override
        public void onCancelled() {
          mInteractable.get(entityId).count--;
          actionTimer = 4;
        }
      })
      .build();
  }

  private ClickListener questClick() {
    return new ClickListener() {
      @Override public void clicked(InputEvent event, float x, float y) {
        QuestDialogController quests = Riiablo.engine.getSystem(QuestDialogController.class);
        if (quests != null && Riiablo.game != null) {
          menuManager.setMenu(null, Engine.INVALID_ENTITY);
          quests.openQuestDialog(Riiablo.game.player, entityId);
        }
      }
    };
  }

  /** Refreshes the hireling menu so a death after the first interaction is visible. */
  private void refreshHirelingMenu() {
    if (!RESURRECTERS.contains(monstats == null ? -1 : monstats.hcIdx)
        || menuManager == null || dialogManager == null) return;
    if (menu != null) menu.remove();
    menu = null;
    createMenu(menuManager, dialogManager);
  }

  private boolean hasDeadMercenary(int playerId) {
    if (playerId == Engine.INVALID_ENTITY) return false;
    NativeMercenaryRewardSystem rewards = Riiablo.engine == null ? null
        : Riiablo.engine.getSystem(NativeMercenaryRewardSystem.class);
    if (rewards != null && rewards.hasDeadMercenary(playerId)) return true;
    if (Riiablo.charData != null && Riiablo.charData.hasMerc()
        && (Riiablo.charData.getMerc().flags & MercenaryManager.FLAG_DEAD) != 0) return true;
    if (mercenaries == null || mMercenary == null || mCorpse == null) return false;
    IntBag entities = mercenaries.getEntities();
    int[] ids = entities.getData();
    for (int i = 0; i < entities.size(); i++) {
      Mercenary merc = mMercenary.get(ids[i]);
      if (merc != null && merc.ownerId == playerId && mCorpse.has(ids[i])) return true;
    }
    return false;
  }

  private String resurrectionLabel(int playerId) {
    Mercenary merc = null;
    if (mercenaries != null && mMercenary != null) {
      IntBag entities = mercenaries.getEntities();
      int[] ids = entities.getData();
      for (int i = 0; i < entities.size(); i++) {
        Mercenary candidate = mMercenary.get(ids[i]);
        if (candidate != null && candidate.ownerId == playerId) {
          merc = candidate;
          break;
        }
      }
    }
    int mercType = merc == null ? MercenaryManager.MERC_TYPE_ROGUE : merc.mercType;
    int nameId = merc == null ? 0 : merc.nameId;
    int level = merc == null ? 1 : Math.max(1, merc.level);
    NativeMercenaryRewardSystem rewards = Riiablo.engine == null ? null
        : Riiablo.engine.getSystem(NativeMercenaryRewardSystem.class);
    if (rewards != null) {
      int serviceLevel = rewards.mercenaryLevel(playerId);
      if (serviceLevel > 0) level = serviceLevel;
    }
    if (merc == null && Riiablo.charData != null && Riiablo.charData.hasMerc()) {
      CharData.MercData data = Riiablo.charData.getMerc();
      mercType = com.riiablo.engine.server.NativeHirelingExperienceTable.mercenaryType(data.type & 0xFFFF);
      nameId = data.name & 0xFFFF;
    }
    String name = com.riiablo.screen.panel.MercenaryHud.resolveMercenaryName(mercType, nameId);
    int cost = MercenaryManager.nativeResurrectionCost(level);
    String template = Riiablo.bundle == null ? null : Riiablo.bundle.get("mercenary_resurrect");
    if (template == null || template.isEmpty() || template.startsWith("ERROR:")) {
      return "复活 " + name + ": " + cost;
    }
    return Riiablo.bundle.format("mercenary_resurrect", name, cost);
  }

  private void hireMercenary(int npcType) {
    if (Riiablo.game == null) return;
    if (menuManager != null) menuManager.setMenu(null, Engine.INVALID_ENTITY);
    if (Riiablo.game.hireMercenaryPanel != null)
      Riiablo.game.hireMercenaryPanel.open(entityId, npcType);
  }

  private void resurrectMercenary(MenuManager menus) {
    if (Riiablo.game == null) return;
    int playerId = Riiablo.game.player;
    if (Riiablo.game.vendorPanel != null && Riiablo.game.vendorPanel.isNetworked()) {
      ClientNetworkSynchronizer network = Riiablo.engine == null ? null
          : Riiablo.engine.getSystem(ClientNetworkSynchronizer.class);
      if (network != null) {
        network.requestNpcService(entityId,
            com.riiablo.net.packet.d2gs.NpcServiceType.RESURRECT,
            com.riiablo.net.packet.d2gs.NpcServiceOperation.RESURRECT,
            0, -1, 0);
      }
    } else {
      NativeMercenaryRewardSystem rewards = Riiablo.engine == null ? null
          : Riiablo.engine.getSystem(NativeMercenaryRewardSystem.class);
      if (rewards != null) rewards.resurrectMercenary(playerId);
    }
    if (menus != null) menus.setMenu(null, Engine.INVALID_ENTITY);
  }

  private void openVendor(int flags, byte service) {
    if (Riiablo.game.vendorPanel.isNetworked()) {
      Riiablo.game.vendorPanel.configNetwork(
          flags, entityId, service, Riiablo.files.Npc.get(monstats.Id));
      Riiablo.game.setLeftPanel(Riiablo.game.vendorPanel);
      return;
    }
    Array<Item> items;
    try {
      if (service == com.riiablo.net.packet.d2gs.NpcServiceType.GAMBLE) {
        // Gambling is player-specific and rerolls when the panel is opened.
        items = vendors.generateGamble();
      } else {
        if (vendorStock == null || !monstats.Id.equals(vendorStockType)) {
          int difficulty = Riiablo.charData == null ? Riiablo.NORMAL : Riiablo.charData.diff;
          vendorStock = vendors.generate(monstats.Id, difficulty);
          vendorStockType = monstats.Id;
        }
        items = vendorStock;
      }
    } catch (Throwable t) {
      items = new Array<>(false, 0, Item.class);
      log.error("Failed to generate vendor items: entityId={}, monsterId={}, error={}",
          entityId, monstats.Id, ExceptionUtils.getRootCauseMessage(t), t);
    }
    Riiablo.game.vendorPanel.config(
        flags, items, Riiablo.files.Npc.get(monstats.Id), service, vendors);
    Riiablo.game.setLeftPanel(Riiablo.game.vendorPanel);
  }

  /** Called when the last player leaves the town and native vendor stock rerolls. */
  public void clearVendorStock() {
    vendorStock = null;
    vendorStockType = null;
  }


  @Override protected void presentInteraction() {
    // D2 attaches sound event 10, not Sounds.txt index 10. Retail 1.10f
    // D2Client 0x6FB51920 dispatches it to 0x6FB5197A: sound 2673 (shrine_refill).
    // Keep this client-side trigger unconditional for healer NPCs: the authoritative
    // stat update may already be reflected in the client snapshot by the time
    // this interaction callback runs.
    if (isHealer(monstats == null ? -1 : monstats.hcIdx) && Riiablo.audio != null) {
      Riiablo.audio.play("shrine_refill", true);
    }

    // TODO: need some kind of static method that can take in some state params, e.g., character
    //       class, player mode and spit out the proper file index.
    //       I.e., akara_act1_intro -> akara_act1_intro_sor automatically if it exists
    String id = speechPrefix() + "_greeting_1";
    Audio.Instance instance = Riiablo.audio.play(id, false);
    if (instance == null) {
      id = speechPrefix() + "_greeting_inactive_1";
      Riiablo.audio.play(id, false);
    }

    refreshHirelingMenu();
  }
  @Override protected void faceNearbyPlayer() {
    if (Riiablo.game == null) return;
    int player = Riiablo.game.player;
    if (mPosition.has(player) && mPosition.get(entityId).position.dst(mPosition.get(player).position) <= 8) lookAt(player);
  }
}
