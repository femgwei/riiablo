# 2026-10-19 角色复活落点修复

用户日志中第四章角色 51 复活时找不到 TOWN_ENTRY_1/2，旧逻辑退回 (0,0)。
该坐标虽然位于群魔堡垒区域，却不是原生玩家出生点。

依据 D2MOO-reference/source/D2Game/src/GAME/Clients.cpp，原版通过
DUNGEON_FindActSpawnLocationEx 请求城镇 tile index 0；普通回城门使用 index 11。
现在在原生 DRLG 释放前分别保存玩家出生点与回城门位置，保留 Zone 平移和
game tile * 5 + 3 坐标换算。本地与 D2GS 复活共用玩家出生点，本地同时更新 MapWrapper。
找不到有效城镇落点时保留死亡状态，避免错误复活至原点。

验证：`:core:test --tests com.riiablo.map.d2moo.NativeTownPortalAnchorTest --no-daemon`
通过，覆盖 A2–A5 的玩家与门户标记分离及世界坐标换算。
实机需复测第四章死亡后 Esc 回城，以及其他章节的死亡回城。
