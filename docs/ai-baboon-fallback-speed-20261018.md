# Baboon escape fallback speed — 2026-10-18

D2MOO AiThink.cpp Fn011 sets (path type 2, nVel, distance override 0), then attempts Escape. On failed noncombat escape it calls WalkCloseToUnit(self,5) without another SetVelocity. MonsterMode.cpp copies and clears pAiParam nVelocity before path construction; the fallback's new mode change therefore does not receive that consumed bonus.

Java incorrectly called walkCloseToSelf(5,nVel), reapplying the escape bonus to the fallback walk. Changed only this argument to zero. Successful escape still gets its run/velocity-derived bonus. Radius-five coordinates, four seed draws, target identity and no-retry behavior remain unchanged.

Extended BaboonEscapeFallbackTest to require zero speed bonus after accepted/rejected fallback, with a fixture check ensuring nonzero source bonus. Added a successful-escape control case across all three difficulties. Before the fix, accepted fallback failed with expected 0.0 versus actual 0.71, while rejection and successful-escape controls passed.

Limits: decision-level speed parameter only, verified through mocked path acceptance and real ECS Velocity values. Native mode construction, shared movement execution, event deletion/control flags and gameplay still require separate validation. No shared executor files changed and no actual-game or post-fix arena run was performed. The original real-map/client immobility is not proven resolved. This corrects the earlier escape-fallback batch's assumption that the original bonus should be reapplied.

After correction, the focused Scarab/Baboon regression run passed 49 tests. No full suite claim.
