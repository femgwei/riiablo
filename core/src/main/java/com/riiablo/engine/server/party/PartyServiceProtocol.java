package com.riiablo.engine.server.party;

import java.util.function.IntPredicate;

import com.riiablo.net.packet.d2gs.PartyOperation;

/** Validates and applies authenticated party intents to the authoritative runtime. */
public final class PartyServiceProtocol {
  private PartyServiceProtocol() {}

  public static Result execute(PartyManager parties, int sourceEntityId,
                               byte operation, int targetEntityId,
                               IntPredicate onlinePlayer) {
    if (sourceEntityId < 0 || !onlinePlayer.test(sourceEntityId)) {
      return Result.reject("PLAYER_NOT_FOUND");
    }

    switch (operation) {
      case PartyOperation.INVITE:
        if (!validTarget(sourceEntityId, targetEntityId, onlinePlayer)) {
          return invalidTarget(sourceEntityId, targetEntityId, onlinePlayer);
        }
        return parties.sendInvitation(sourceEntityId, targetEntityId)
            ? Result.success() : Result.reject("INVITE_REJECTED");

      case PartyOperation.ACCEPT: {
        int inviterId = parties.getInviter(sourceEntityId);
        if (inviterId < 0) return Result.reject("NO_PENDING_INVITATION");
        if (targetEntityId >= 0 && inviterId != targetEntityId) return Result.reject("INVITER_MISMATCH");
        if (!validTarget(sourceEntityId, inviterId, onlinePlayer)) {
          return invalidTarget(sourceEntityId, inviterId, onlinePlayer);
        }
        return parties.acceptInvitation(sourceEntityId)
            ? Result.success() : Result.reject("ACCEPT_REJECTED");
      }

      case PartyOperation.DECLINE: {
        int inviterId = parties.getInviter(sourceEntityId);
        if (inviterId < 0) return Result.reject("NO_PENDING_INVITATION");
        if (targetEntityId >= 0 && inviterId != targetEntityId) return Result.reject("INVITER_MISMATCH");
        if (!validTarget(sourceEntityId, inviterId, onlinePlayer)) {
          return invalidTarget(sourceEntityId, inviterId, onlinePlayer);
        }
        parties.declineInvitation(sourceEntityId);
        return Result.success();
      }

      case PartyOperation.CANCEL:
        if (!validTarget(sourceEntityId, targetEntityId, onlinePlayer)) {
          return invalidTarget(sourceEntityId, targetEntityId, onlinePlayer);
        }
        if (parties.getInviter(targetEntityId) != sourceEntityId) {
          return Result.reject("NOT_INVITER");
        }
        parties.cancelInvitation(sourceEntityId, targetEntityId);
        return Result.success();

      case PartyOperation.LEAVE:
        if (parties.getPartyId(sourceEntityId) == Party.INVALID_ID) {
          return Result.reject("NOT_IN_PARTY");
        }
        parties.leaveParty(sourceEntityId);
        return Result.success();

      case PartyOperation.HOSTILE:
        if (!validTarget(sourceEntityId, targetEntityId, onlinePlayer)) {
          return invalidTarget(sourceEntityId, targetEntityId, onlinePlayer);
        }
        return parties.declareHostility(sourceEntityId, targetEntityId)
            ? Result.success() : Result.reject("HOSTILE_REJECTED");

      case PartyOperation.UNHOSTILE:
        if (!validTarget(sourceEntityId, targetEntityId, onlinePlayer)) {
          return invalidTarget(sourceEntityId, targetEntityId, onlinePlayer);
        }
        if (parties.getRelation(sourceEntityId, targetEntityId) != PartyRelation.HOSTILE) {
          return Result.reject("NOT_HOSTILE");
        }
        parties.removeHostility(sourceEntityId, targetEntityId);
        return Result.success();

      case PartyOperation.SNAPSHOT:
        return Result.success();

      default:
        return Result.reject("INVALID_OPERATION");
    }
  }

  private static boolean validTarget(int source, int target, IntPredicate onlinePlayer) {
    return target >= 0 && target != source && onlinePlayer.test(target);
  }

  private static Result invalidTarget(int source, int target, IntPredicate onlinePlayer) {
    if (target < 0) return Result.reject("INVALID_TARGET");
    if (target == source) return Result.reject("SELF_TARGET");
    if (!onlinePlayer.test(target)) return Result.reject("TARGET_OFFLINE");
    return Result.reject("INVALID_TARGET");
  }

  public static final class Result {
    public final boolean success;
    public final String reason;

    private Result(boolean success, String reason) {
      this.success = success;
      this.reason = reason;
    }

    public static Result success() {
      return new Result(true, "");
    }

    public static Result reject(String reason) {
      return new Result(false, reason);
    }
  }
}
