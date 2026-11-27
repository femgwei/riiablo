# Necromancer Teeth presentation analysis (2026-11-24)

Teeth (skill 67) was the first genuinely uncovered entry after reconciling the
class manifest with the prior Bone Prison/Bone Spear records. Its server path
already uses native `SrvDo008`: `calc1` determines the projectile count,
`calc3` supplies the centre group (falling back to the full count), and each
authoritative `teeth` missile owns direction, collision, lifetime, and cleanup.

The presentation gap was duplicate client fan risk. `SkillCastHandler` routes
`cltDoFunc=8` through the legacy `cltmissilea` fan, but the
`shouldReuseServerMissile` gate must classify Teeth by its shared `srvdofunc=8`
on both local and network clients. The new regression
`teethReusesAuthoritativeSrvDo008FanOnLocalAndNetworkCasts` proves that gate
for the real Skills.txt row. This keeps the server-created fan as the sole
visual/combat chain, including expiry and removal.

`runtime_status=not_run_in_class_batch`: no original runtime was started.
`comparison_status=static_and_replay_gate_added`: this is a focused client
ownership regression, not pixel or GPU equivalence. The attempted JDK 17
Gradle run was blocked by the existing wrapper cache lock/file absence at
`F:\\Gradle\\wrapper\\dists\\gradle-7.3-bin\\...zip.lck`; no test pass is
claimed.
