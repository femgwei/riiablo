"""Deterministic, offline test strategy derived from Kolbot-SoloPlay.

This module intentionally contains no D2BS bindings, packet sender, process
access, or arbitrary scripting.  It turns the useful parts of SoloPlay's
Barbarian attack policy into bounded actions that our original observer can
later drive through its reviewed window/native adapters.
"""
from dataclasses import dataclass, asdict
from typing import Dict, List, Optional


SOLOPLAY_SOURCE = (
    "https://github.com/blizzhackers/kolbot-SoloPlay/blob/restructuring/"
    "Core/ClassAttackOverrides/BarbarianAttacks.js"
)

# Diablo II 1.10f skill IDs, cross-checked against this repository's Skills.txt
# ownership matrix.  These IDs are data identifiers, not executable offsets.
SKILLS = {
    "bash": 126,
    "howl": 130,
    "leap": 132,
    "double_swing": 133,
    "taunt": 137,
    "stun": 139,
    "concentrate": 144,
    "battle_cry": 146,
    "frenzy": 147,
    "whirlwind": 151,
    "war_cry": 154,
}


@dataclass(frozen=True)
class Target:
    gid: int
    class_id: int
    distance: int
    attackable: bool = True
    curseable: bool = True
    stunned: bool = False
    special: bool = False
    helpful_curse: bool = False
    scareable: bool = True
    level: int = 1
    dead: bool = False


@dataclass(frozen=True)
class BarbState:
    level: int = 1
    mana: int = 100
    hp_percent: int = 100
    max_near_monsters: int = 4
    attack_skill: int = SKILLS["bash"]
    secondary_skill: int = -1
    available: tuple = (126,)
    dual_wielding: bool = False
    weapon: bool = True
    skill_delay: bool = False
    block_wall: bool = False


def _has(state: BarbState, name: str) -> bool:
    return SKILLS[name] in state.available


def choose_actions(state: BarbState, targets: List[Target]) -> List[Dict]:
    """Return a small deterministic action list for one decision interval.

    The order follows the observable policy in SoloPlay: panic/howl, taunt or
    war cry setup, reposition, then the selected attack.  It deliberately
    models policy only; the original game remains the authority for damage,
    animation, AI and state transitions.
    """
    live = [t for t in targets if t.attackable and not t.dead]
    if not live:
        return [{"op": "idle", "reason": "no_attackable_targets"}]
    actions: List[Dict] = []
    near = [t for t in live if t.distance <= 6]
    if (_has(state, "howl") and state.attack_skill != SKILLS["whirlwind"]
            and state.mana >= 2 and state.hp_percent <= 85
            and len([t for t in near if t.scareable and t.level < state.level + 1])
            > state.max_near_monsters):
        actions.append({"op": "cast", "skill": SKILLS["howl"], "target": None,
                        "reason": "too_many_nearby_low_level_targets"})

    target = sorted(live, key=lambda t: (t.distance, t.gid))[0]
    if (_has(state, "battle_cry") and not state.skill_delay and target.distance <= 5
            and target.curseable and not target.helpful_curse):
        actions.append({"op": "cast", "skill": SKILLS["battle_cry"],
                        "target": target.gid, "reason": "curseable_target"})
    elif (_has(state, "war_cry") and target.distance <= 5 and not target.special
          and not target.stunned):
        actions.append({"op": "cast", "skill": SKILLS["war_cry"],
                        "target": target.gid, "reason": "stunnable_target"})

    skill = state.attack_skill
    if skill in (SKILLS["frenzy"], SKILLS["double_swing"]) and not state.dual_wielding:
        skill = state.secondary_skill if state.secondary_skill >= 0 else SKILLS["bash"]
    if skill in (SKILLS["bash"], SKILLS["stun"], SKILLS["concentrate"]):
        if not state.weapon:
            return actions + [{"op": "blocked", "skill": skill,
                               "reason": "weapon_required"}]
    if skill == SKILLS["whirlwind"]:
        actions.append({"op": "cast", "skill": skill, "target": target.gid,
                        "reason": "melee_whirlwind"})
        return actions
    if target.distance > 4:
        if (_has(state, "leap") and target.distance > 6 and not state.block_wall):
            actions.append({"op": "cast", "skill": SKILLS["leap"],
                            "target": target.gid, "reason": "close_distance"})
        else:
            actions.append({"op": "move", "target": target.gid,
                            "reason": "move_into_melee_range"})
    actions.append({"op": "cast", "skill": skill, "target": target.gid,
                    "reason": "selected_attack"})
    return actions


def scenario(name: str) -> Dict:
    """Return a bounded scenario manifest for the requested policy slice."""
    if name == "bash":
        state = BarbState(attack_skill=SKILLS["bash"], available=(SKILLS["bash"],))
        targets = [Target(1001, 0, 3)]
    elif name == "frenzy":
        state = BarbState(attack_skill=SKILLS["frenzy"], available=(SKILLS["frenzy"],),
                          dual_wielding=True)
        targets = [Target(1001, 0, 3)]
    elif name == "war_cry":
        state = BarbState(attack_skill=SKILLS["bash"], available=(SKILLS["bash"], SKILLS["war_cry"]))
        targets = [Target(1001, 0, 3), Target(1002, 0, 4), Target(1003, 0, 4)]
    elif name == "howl":
        state = BarbState(attack_skill=SKILLS["bash"], available=(SKILLS["bash"], SKILLS["howl"]),
                          hp_percent=80, max_near_monsters=2)
        targets = [Target(1000 + i, 0, 2, level=1) for i in range(1, 5)]
    else:
        raise ValueError("unknown strategy scenario: " + name)
    return {
        "schema": 1,
        "source": SOLOPLAY_SOURCE,
        "policy": "barbarian_attack_slice",
        "version_scope": "policy reference only; runtime target is pinned Diablo II 1.10f",
        "state": asdict(state),
        "targets": [asdict(target) for target in targets],
        "actions": choose_actions(state, targets),
        "expected_observations": [
            "player mode transition",
            "skill/target binding",
            "damage or state event",
            "authoritative game-frame continuity",
        ],
    }
