package com.riiablo.engine.client;

import com.badlogic.gdx.utils.IntMap;

import com.riiablo.engine.server.party.Party;
import com.riiablo.engine.server.party.PartyRelation;
import com.riiablo.net.packet.d2gs.PartyMemberSnapshot;
import com.riiablo.net.packet.d2gs.PartyResult;

/** Detached client copy of the most recent personalized D2GS party snapshot. */
public final class ClientPartyState {
  private final IntMap<Member> members = new IntMap<>();
  private long revision;
  private long lastRequestId;
  private boolean lastSuccess;
  private String lastReason = "";
  private byte lastOperation;
  private int sourceEntityId = -1;
  private int targetEntityId = -1;
  private int partyId = Party.INVALID_ID;
  private int incomingInviterId = -1;

  public void apply(PartyResult result) {
    members.clear();
    incomingInviterId = -1;
    for (int i = 0; i < result.membersLength(); i++) {
      PartyMemberSnapshot wire = result.members(i);
      Member member = new Member();
      member.entityId = wire.entityId();
      member.name = wire.name() == null ? "" : wire.name();
      member.classId = wire.classId();
      member.level = wire.level();
      member.hp = wire.hp();
      member.maxHp = wire.maxHp();
      member.mana = wire.mana();
      member.maxMana = wire.maxMana();
      member.levelId = wire.levelId();
      member.x = wire.x();
      member.y = wire.y();
      member.alive = wire.alive();
      member.online = wire.online();
      member.leader = wire.leader();
      member.partyId = wire.partyId();
      member.relation = wire.relation();
      members.put(member.entityId, member);
      if (member.relation == PartyRelation.INVITED) incomingInviterId = member.entityId;
    }
    if (result.requestId() != 0) {
      lastRequestId = result.requestId();
      lastSuccess = result.success();
      lastReason = result.reason() == null ? "" : result.reason();
      lastOperation = result.operation();
    }
    sourceEntityId = result.sourceEntityId();
    targetEntityId = result.targetEntityId();
    partyId = result.partyId();
    revision++;
  }

  public IntMap<Member> members() { return members; }
  public Member get(int serverEntityId) { return members.get(serverEntityId); }
  public long revision() { return revision; }
  public long lastRequestId() { return lastRequestId; }
  public boolean lastSuccess() { return lastSuccess; }
  public String lastReason() { return lastReason; }
  public byte lastOperation() { return lastOperation; }
  public int sourceEntityId() { return sourceEntityId; }
  public int targetEntityId() { return targetEntityId; }
  public int partyId() { return partyId; }
  public int incomingInviterId() { return incomingInviterId; }

  public static final class Member {
    public int entityId;
    public String name;
    public int classId;
    public int level;
    public int hp;
    public int maxHp;
    public int mana;
    public int maxMana;
    public int levelId;
    public int x;
    public int y;
    public boolean alive;
    public boolean online;
    public boolean leader;
    public int partyId;
    public int relation;
  }
}
