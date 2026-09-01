package com.riiablo.screen.panel;

import java.util.Comparator;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;

import com.riiablo.Riiablo;
import com.riiablo.engine.client.ClientNetworkReceiver;
import com.riiablo.engine.client.ClientNetworkSynchronizer;
import com.riiablo.engine.client.ClientPartyState;
import com.riiablo.engine.server.party.Party;
import com.riiablo.engine.server.party.PartyRelation;
import com.riiablo.graphics.PaletteIndexedColorDrawable;
import com.riiablo.net.packet.d2gs.PartyOperation;
import com.riiablo.widget.Label;
import com.riiablo.widget.LabelButton;

/** Multiplayer party roster and actions backed by the authoritative D2GS protocol. */
public class PartyPanel extends Table {
  private static final float WIDTH = 320;
  private static final float HEIGHT = 432;

  private final Table roster = new Table();
  private final Label status = new Label("", Riiablo.fonts.fontformal10, Riiablo.colors.grey);
  private ClientNetworkSynchronizer synchronizer;
  private ClientNetworkReceiver receiver;
  private long displayedRevision = Long.MIN_VALUE;
  private long displayedRequestId;
  private boolean snapshotRequested;

  public PartyPanel() {
    setSize(WIDTH, HEIGHT);
    setTouchable(Touchable.enabled);
    setBackground(new PaletteIndexedColorDrawable(Riiablo.colors.modal75));
    pad(12);
    defaults().growX();

    Label title = new Label("Party", Riiablo.fonts.font16, Riiablo.colors.gold);
    title.setAlignment(Align.center);
    add(title).height(28).row();

    Label hint = new Label("Players and relationships", Riiablo.fonts.fontformal10,
        Riiablo.colors.grey);
    hint.setAlignment(Align.center);
    add(hint).height(18).row();

    roster.top().left();
    add(roster).grow().padTop(6).row();

    status.setAlignment(Align.center);
    status.setWrap(true);
    add(status).height(28).padTop(4).row();

    LabelButton close = actionButton("Close", () -> {
      if (Riiablo.game != null) Riiablo.game.setLeftPanel(null);
      else setVisible(false);
    });
    add(close).height(20).center();
    setVisible(false);
  }

  public void setNetworkSystems(ClientNetworkSynchronizer synchronizer,
                                ClientNetworkReceiver receiver) {
    this.synchronizer = synchronizer;
    this.receiver = receiver;
    displayedRevision = Long.MIN_VALUE;
    snapshotRequested = false;
  }

  @Override
  public void setVisible(boolean visible) {
    super.setVisible(visible);
    if (visible) {
      snapshotRequested = false;
      displayedRevision = Long.MIN_VALUE;
    }
  }

  @Override
  public void act(float delta) {
    super.act(delta);
    if (!isVisible()) return;
    if (synchronizer == null || receiver == null) {
      if (displayedRevision != -1) showOffline();
      return;
    }
    if (!snapshotRequested) {
      snapshotRequested = true;
      long requestId = synchronizer.requestParty(PartyOperation.SNAPSHOT, -1);
      if (requestId == 0) status.setText("Unable to request party roster");
    }

    ClientPartyState state = receiver.partyState();
    if (state.revision() != displayedRevision) rebuild(state);
    if (state.lastRequestId() != 0 && state.lastRequestId() != displayedRequestId) {
      displayedRequestId = state.lastRequestId();
      status.setColor(state.lastSuccess() ? Riiablo.colors.green : Riiablo.colors.red);
      status.setText(state.lastSuccess()
          ? operationLabel(state.lastOperation()) + " completed"
          : failureMessage(state.lastReason(), state.lastRetryAfterMillis()));
    }
  }

  private void showOffline() {
    displayedRevision = -1;
    roster.clearChildren();
    Label label = new Label("Party actions are available in multiplayer games.",
        Riiablo.fonts.fontformal10, Riiablo.colors.grey);
    label.setWrap(true);
    label.setAlignment(Align.center);
    roster.add(label).width(WIDTH - 32).padTop(40);
    status.setText("");
  }

  private void rebuild(ClientPartyState state) {
    displayedRevision = state.revision();
    roster.clearChildren();
    int localServerId = synchronizer.serverPlayerId();
    Array<ClientPartyState.Member> players = new Array<>(state.members().size);
    for (IntMap.Entry<ClientPartyState.Member> entry : state.members().entries()) {
      players.add(entry.value);
    }
    players.sort(new Comparator<ClientPartyState.Member>() {
      @Override
      public int compare(ClientPartyState.Member a, ClientPartyState.Member b) {
        if (a.entityId == localServerId) return b.entityId == localServerId ? 0 : -1;
        if (b.entityId == localServerId) return 1;
        if (a.relation == PartyRelation.PARTY_MEMBER
            && b.relation != PartyRelation.PARTY_MEMBER) return -1;
        if (b.relation == PartyRelation.PARTY_MEMBER
            && a.relation != PartyRelation.PARTY_MEMBER) return 1;
        return a.name.compareToIgnoreCase(b.name);
      }
    });

    if (players.size == 0) {
      Label empty = new Label("No connected players", Riiablo.fonts.fontformal10,
          Riiablo.colors.grey);
      empty.setAlignment(Align.center);
      roster.add(empty).width(WIDTH - 32).padTop(40);
      return;
    }

    for (ClientPartyState.Member member : players) {
      boolean self = member.entityId == localServerId;
      roster.add(createPlayerRow(member, self, state.partyId()))
          .width(WIDTH - 24).padBottom(4).row();
    }
  }

  private Table createPlayerRow(ClientPartyState.Member member, boolean self, int localPartyId) {
    Table row = new Table();
    row.setBackground(new PaletteIndexedColorDrawable(Riiablo.colors.modal25));
    row.pad(5);
    row.defaults().left();

    String name = member.name + (self ? " (You)" : "") + (member.leader ? " *" : "");
    Label nameLabel = new Label(name, Riiablo.fonts.fontformal11, relationColor(member.relation));
    row.add(nameLabel).width(116).top();

    String details = "Lv " + Math.max(1, member.level);
    if (member.levelId >= 0) details += "  Area " + member.levelId;
    if (member.maxHp > 0) details += "\nHP " + member.hp + "/" + member.maxHp;
    Label detailsLabel = new Label(details, Riiablo.fonts.fontformal10, Riiablo.colors.grey);
    detailsLabel.setWrap(true);
    row.add(detailsLabel).width(86).top();

    Table actions = new Table();
    byte[] available = actionsFor(self, localPartyId, member.partyId, member.relation);
    if (available.length == 0) {
      Label relation = new Label(relationLabel(self, member.relation),
          Riiablo.fonts.fontformal10, relationColor(member.relation));
      relation.setAlignment(Align.right);
      actions.add(relation).right();
    } else {
      for (byte operation : available) {
        actions.add(actionButton(operationLabel(operation),
            () -> request(operation, member.entityId))).right().row();
      }
    }
    row.add(actions).growX().right().top();
    return row;
  }

  private void request(byte operation, int targetEntityId) {
    if (synchronizer == null) return;
    long requestId = synchronizer.requestParty(operation,
        operation == PartyOperation.LEAVE ? -1 : targetEntityId);
    status.setColor(requestId == 0 ? Riiablo.colors.red : Riiablo.colors.gold);
    status.setText(requestId == 0 ? "Unable to send request" : "Request sent...");
  }

  private LabelButton actionButton(String text, final Runnable action) {
    LabelButton button = new LabelButton(text, Riiablo.fonts.fontformal10, Riiablo.colors.white);
    button.addListener(new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        action.run();
      }
    });
    return button;
  }

  /** Pure action policy used by both the panel and regression tests. */
  public static byte[] actionsFor(boolean self, int localPartyId,
                                  int targetPartyId, int relation) {
    if (self) {
      return localPartyId == Party.INVALID_ID
          ? new byte[0] : new byte[] {PartyOperation.LEAVE};
    }
    switch (relation) {
      case PartyRelation.PARTY_MEMBER:
        return new byte[0];
      case PartyRelation.INVITED:
        return new byte[] {PartyOperation.ACCEPT, PartyOperation.DECLINE};
      case PartyRelation.INVITER:
        return new byte[] {PartyOperation.CANCEL};
      case PartyRelation.HOSTILE:
        return new byte[] {PartyOperation.UNHOSTILE};
      case PartyRelation.NONE:
      default:
        return targetPartyId == Party.INVALID_ID
            ? new byte[] {PartyOperation.INVITE, PartyOperation.HOSTILE}
            : new byte[] {PartyOperation.HOSTILE};
    }
  }

  private static String relationLabel(boolean self, int relation) {
    if (self) return "You";
    switch (relation) {
      case PartyRelation.PARTY_MEMBER: return "Party";
      case PartyRelation.INVITED: return "Invited you";
      case PartyRelation.INVITER: return "Invited";
      case PartyRelation.HOSTILE: return "Hostile";
      default: return "Player";
    }
  }

  private static com.badlogic.gdx.graphics.Color relationColor(int relation) {
    switch (relation) {
      case PartyRelation.PARTY_MEMBER: return Riiablo.colors.green;
      case PartyRelation.HOSTILE: return Riiablo.colors.red;
      case PartyRelation.INVITED:
      case PartyRelation.INVITER: return Riiablo.colors.gold;
      default: return Riiablo.colors.white;
    }
  }

  private static String operationLabel(byte operation) {
    switch (operation) {
      case PartyOperation.INVITE: return "Invite";
      case PartyOperation.ACCEPT: return "Accept";
      case PartyOperation.DECLINE: return "Decline";
      case PartyOperation.CANCEL: return "Cancel";
      case PartyOperation.LEAVE: return "Leave";
      case PartyOperation.HOSTILE: return "Hostile";
      case PartyOperation.UNHOSTILE: return "Unhostile";
      case PartyOperation.SNAPSHOT: return "Refresh";
      default: return "Party action";
    }
  }

  private static String humanize(String reason) {
    if (reason == null || reason.isEmpty()) return "Request rejected";
    String text = reason.replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
    return Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }

  static String failureMessage(String reason, long retryAfterMillis) {
    if ("HOSTILE_COOLDOWN".equals(reason)) {
      long seconds = Math.max(1L, (Math.max(0L, retryAfterMillis) + 999L) / 1000L);
      return "Please wait " + seconds + (seconds == 1L ? " second" : " seconds")
          + " before declaring hostility.";
    }
    if ("HOSTILE_REJECTED".equals(reason)) {
      return "Hostility requires both players at level 9, you in town, and no shared party.";
    }
    return humanize(reason);
  }
}
