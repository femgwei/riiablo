# Summon Spirit Wolf（召唤灵狼）阶段结论

原版 `AITHINK_Fn108_SpiritWolf` 与 Fenris 共用入口，但分支不同。Riiablo 已补齐可确认的 Spirit Wolf 差异：

- 使用 `Skills.txt PetMax=min(level, Param3)`，Param3=5；
- 目标视野使用 MonStats `aip4=20`，不再固定使用 24；
- 使用原版单位距离公式和主人距离分支；
- 主人超过远距离时使用 Teleport 2 技能链；
- 主人奔跑时灵狼用奔跑速度追随，主人走路时使用步行追随；
- 进入攻击范围后正常 A1，接近目标时使用奔跑速度；
- 城镇仍使用跟随/停顿分支；
- 主人死亡、删除和宠物生命周期继续由通用召唤系统管理。

定向验证：`NativeDruidSummonDataTest`、`SummonInitializationIntegrationTest`、`SummonedPetSystemTest`、`DruidSummonIntegrationTest` 共 24 项通过。

尚未做原版私有桌面灵狼专属采集；当前剩余主要是 Spirit Wolf 的攻击/Teleport 客户端动画时序，服务器逻辑和召唤生命周期已覆盖。
