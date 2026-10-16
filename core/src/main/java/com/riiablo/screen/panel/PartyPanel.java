package com.riiablo.screen.panel;

import java.util.Comparator;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;

import com.riiablo.Riiablo;
import com.riiablo.engine.client.ClientPartyState;
import com.riiablo.engine.server.party.Party;
import com.riiablo.engine.server.party.PartyRelation;
import com.riiablo.graphics.PaletteIndexedColorDrawable;
import com.riiablo.net.packet.d2gs.PartyOperation;
import com.riiablo.net.packet.d2gs.TradeOperation;
import com.riiablo.widget.Label;
import com.riiablo.widget.LabelButton;
import com.riiablo.screen.GameScreen;
import com.riiablo.util.Disposal;

/** Multiplayer party roster and actions backed by the authoritative D2GS protocol. */
public class PartyPanel extends SessionNetworkPanel {
  private static final float WIDTH = 320;
  private static final float HEIGHT = 432;

  private Table roster;
  private Label status;
  private final Array<Actor> rosterActors = new Array<>();
  private long displayedRevision = Long.MIN_VALUE;
  private long displayedRequestId;
  private boolean snapshotRequested;

  public PartyPanel() {
    this(Riiablo.game);
  }

  public PartyPanel(GameScreen owner) {
    super(owner);
    try { initialize(); }
    catch (RuntimeException | Error failure) { constructionFailed(failure); throw failure; }
  }

  private void initialize() {
    roster = table();
    status = label("", fonts.fontformal10, colors.grey);
    setSize(WIDTH, HEIGHT);
    setTouchable(Touchable.enabled);
    setBackground(new PaletteIndexedColorDrawable(colors.modal75));
    pad(12);
    defaults().growX();

    Label title = label(text("party_title"), fonts.font16, colors.gold);
    title.setAlignment(Align.center);
    add(title).height(28).row();

    Label hint = label(text("party_hint"), fonts.fontformal10, colors.grey);
    hint.setAlignment(Align.center);
    add(hint).height(18).row();

    roster.top().left();
    add(roster).grow().padTop(6).row();

    status.setAlignment(Align.center);
    status.setWrap(true);
    add(status).height(28).padTop(4).row();

    LabelButton close = actionButton(text("close"), () -> {
      if (owner != null) owner.setLeftPanel(null);
      else setVisible(false);
    });
    add(close).height(20).center();
    setVisible(false);
  }

  @Override
  public void setVisible(boolean visible) {
    if (visible && !isCurrentSession()) return;
    super.setVisible(visible);
    if (visible) {
      snapshotRequested = false;
      displayedRevision = Long.MIN_VALUE;
    }
  }

  @Override
  public void act(float delta) {
    if (!isCurrentSession()) return;
    super.act(delta);
    if (!isInteractive()) return;
    if (synchronizer == null || receiver == null) {
      if (displayedRevision != -1) showOffline();
      return;
    }
    if (!snapshotRequested) {
      snapshotRequested = true;
      long requestId = synchronizer.requestParty(PartyOperation.SNAPSHOT, -1);
      if (!isInteractive()) return;
      if (requestId == 0) status.setText(text("party_roster_failed"));
    }

    ClientPartyState state = receiver.partyState();
    if (state.revision() != displayedRevision) rebuild(state);
    if (state.lastRequestId() != 0 && state.lastRequestId() != displayedRequestId) {
      displayedRequestId = state.lastRequestId();
      status.setColor(state.lastSuccess() ? colors.green : colors.red);
      status.setText(state.lastSuccess()
          ? format("party_operation_completed", localizedOperationLabel(state.lastOperation()))
          : localizedFailureMessage(state.lastReason(), state.lastRetryAfterMillis()));
    }
  }

  private void showOffline() {
    retireRoster();
    try {
      Label label = content(label(text("party_multiplayer_only"), fonts.fontformal10, colors.grey));
      label.setWrap(true);
      label.setAlignment(Align.center);
      roster.add(label).width(WIDTH - 32).padTop(40);
      status.setText("");
      displayedRevision = -1;
    } catch (RuntimeException | Error failure) { rosterFailed(failure); throw failure; }
  }

  private void rebuild(ClientPartyState state) {
    retireRoster();
    try { buildRoster(state); displayedRevision = state.revision(); }
    catch (RuntimeException | Error failure) { rosterFailed(failure); throw failure; }
  }

  private void buildRoster(ClientPartyState state) {
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
      Label empty = content(label(text("party_no_players"), fonts.fontformal10, colors.grey));
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
    Table row = content(table());
    row.setBackground(new PaletteIndexedColorDrawable(colors.modal25));
    row.pad(5);
    row.defaults().left();

    String name = member.name + (self ? " (" + text("party_you") + ")" : "")
        + (member.leader ? " *" : "");
    Label nameLabel = content(label(name, fonts.fontformal11, relationColor(member.relation)));
    row.add(nameLabel).width(116).top();

    String details = format("party_level", Math.max(1, member.level));
    if (member.levelId >= 0) details += "  " + format("party_area", member.levelId);
    if (member.maxHp > 0) details += "\n" + format("party_health", member.hp, member.maxHp);
    Label detailsLabel = content(label(details, fonts.fontformal10, colors.grey));
    detailsLabel.setWrap(true);
    row.add(detailsLabel).width(86).top();

    Table actions = content(table());
    if (!self && member.online) {
      actions.add(content(actionButton(text("trade"), () -> requestTrade(member.entityId)))).right().row();
    }
    byte[] available = actionsFor(self, localPartyId, member.partyId, member.relation);
    if (available.length == 0) {
      Label relation = content(label(localizedRelationLabel(self, member.relation),
          fonts.fontformal10, relationColor(member.relation)));
      relation.setAlignment(Align.right);
      actions.add(relation).right();
    } else {
      for (byte operation : available) {
        actions.add(content(actionButton(localizedOperationLabel(operation),
            () -> request(operation, member.entityId)))).right().row();
      }
    }
    row.add(actions).growX().right().top();
    return row;
  }

  private void request(byte operation, int targetEntityId) {
    if (!isInteractive() || synchronizer == null) return;
    long requestId = synchronizer.requestParty(operation,
        operation == PartyOperation.LEAVE ? -1 : targetEntityId);
    if (!isInteractive()) return;
    status.setColor(requestId == 0 ? colors.red : colors.gold);
    status.setText(requestId == 0 ? text("request_send_failed") : text("request_sent"));
  }

  private void requestTrade(int targetEntityId) {
    if (!isInteractive() || synchronizer == null) return;
    long requestId = synchronizer.requestTrade(TradeOperation.REQUEST, targetEntityId,
        0, -1, 0, 0, 0);
    if (!isInteractive()) return;
    status.setColor(requestId == 0 ? colors.red : colors.gold);
    status.setText(requestId == 0 ? text("trade_request_send_failed")
        : text("trade_request_sent"));
  }

  private <T extends Actor> T content(T actor) { rosterActors.add(actor); return actor; }

  private void retireRoster() {
    displayedRevision = Long.MIN_VALUE;
    try (Disposal cleanup = new Disposal()) {
      cleanup.run(() -> retireActors(rosterActors));
      if (roster != null) cleanup.run(roster::clearChildren);
    }
  }

  private void rosterFailed(Throwable failure) {
    try { retireRoster(); }
    catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
  }

  @Override protected void clearView() {
    try { retireRoster(); }
    finally { roster = null; status = null; snapshotRequested = false; displayedRequestId = 0; }
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

  private String localizedRelationLabel(boolean self, int relation) {
    if (self) return text("party_you");
    switch (relation) {
      case PartyRelation.PARTY_MEMBER: return text("party_relation_member");
      case PartyRelation.INVITED: return text("party_relation_invited_you");
      case PartyRelation.INVITER: return text("party_relation_invited");
      case PartyRelation.HOSTILE: return text("party_relation_hostile");
      default: return text("party_relation_player");
    }
  }

  private com.badlogic.gdx.graphics.Color relationColor(int relation) {
    switch (relation) {
      case PartyRelation.PARTY_MEMBER: return colors.green;
      case PartyRelation.HOSTILE: return colors.red;
      case PartyRelation.INVITED:
      case PartyRelation.INVITER: return colors.gold;
      default: return colors.white;
    }
  }

  private String localizedOperationLabel(byte operation) {
    switch (operation) {
      case PartyOperation.INVITE: return text("party_action_invite");
      case PartyOperation.ACCEPT: return text("accept");
      case PartyOperation.DECLINE: return text("decline");
      case PartyOperation.CANCEL: return text("cancel");
      case PartyOperation.LEAVE: return text("party_action_leave");
      case PartyOperation.HOSTILE: return text("party_relation_hostile");
      case PartyOperation.UNHOSTILE: return text("party_action_unhostile");
      case PartyOperation.SNAPSHOT: return text("refresh");
      default: return text("party_action_generic");
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

  private String localizedFailureMessage(String reason, long retryAfterMillis) {
    if ("HOSTILE_COOLDOWN".equals(reason)) {
      long seconds = Math.max(1L, (Math.max(0L, retryAfterMillis) + 999L) / 1000L);
      return format("party_hostile_cooldown", seconds);
    }
    if ("HOSTILE_REJECTED".equals(reason)) return text("party_hostile_rejected");
    return localizedPartyReason(reason);
  }

  private String localizedPartyReason(String reason) {
    if (reason == null || reason.isEmpty()) return text("party_request_rejected");
    switch (reason) {
      case "PLAYER_NOT_FOUND": return text("party_error_player_not_found");
      case "INVITE_REJECTED": return text("party_error_invite_rejected");
      case "NO_PENDING_INVITATION": return text("party_error_no_invitation");
      case "INVITER_MISMATCH": return text("party_error_inviter_mismatch");
      case "ACCEPT_REJECTED": return text("party_error_accept_rejected");
      case "NOT_INVITER": return text("party_error_not_inviter");
      case "NOT_IN_PARTY": return text("party_error_not_in_party");
      case "NOT_HOSTILE": return text("party_error_not_hostile");
      case "INVALID_OPERATION": return text("party_error_invalid_operation");
      case "INVALID_TARGET": return text("party_error_invalid_target");
      case "SELF_TARGET": return text("party_error_self_target");
      case "TARGET_OFFLINE": return text("party_error_target_offline");
      default: return format("party_request_rejected_reason", reason);
    }
  }

}
