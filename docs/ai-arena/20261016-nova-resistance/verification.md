# Nova resistance joint-suite diagnosis — PARTIAL

本批只修改Nova测试，不修改生产伤害、怪物概率或原版数据。

## 根因与修复范围

旧断言要求1级Nova经过50%抗性后每次至少造成1HP伤害，但当前生产代码的实际组合为：

- `CombatSystem.rollNativeMissileDamage` 使用 `[min,max)`，真实Nova行得到整数1–19；
- `CombatSystem.applyElementalResistance` 以整数执行 `damage * (100-resistance) / 100`；
- 因此抽到1时，50%抗性后成为0。旧断言排除了当前模型合法的结果，
  是否失败取决于之前测试留下的全局随机状态，不是飞刃扣血导致Nova碰撞丢失。

新测试对seed0–63各建立无抗性和50%抗性两个独立World，使用相同随机种子、真实技能行、
真实碰撞/伤害路径；要求无抗性原始伤害1–19，逐对核验当前整数抗性结果。
同时仍要求100%免疫目标不掉血、两目标各被claim一次、后续重叠轨迹不重复伤害，
并要求样本确实覆盖零伤害和非零伤害。没有只扩大HP允许区间来让缺失碰撞通过。
每个样本暂用独立RandomXS128，finally恢复调用者生成器对象；新增测试还核验调用者
生成器的两段内部状态完全不变，避免新夹具继续污染其他测试。

这只是当前整数伤害模型的回归契约，**不是原版固定点伤害验收**。
D2MOO `UNIT/SUnitDmg.cpp` 的闪电伤害使用 `<<8`，原版保留8位小数；
当前将1点经50%抗性变成0而非0.5的生产差距仍未修复，本批不把它表述为原版正确。
未来修复生产固定点结算时，应同步升级此处回归契约。

## 证据与复现

- `baseline-tests.log` / `baseline-nova.xml`：固定提交源码重现原联合套件失败，
  core共167项、Nova7项中1失败，失败HP仍为1000。
- `first.log` / `first/TEST-*.xml`：刺客、飞刃Blood Mana、普通Blood Mana、Succubus/诅咒、
  Nova、光环、ServerSkillSystem和MonsterAiArena在同次联合调用中验证。
- `repeat.log` / `repeat/TEST-*.xml`：清理测试输出后独立测试进程复跑，未拆开刺客与Nova。
- `verify.ps1` / `verification.log`：核对每轮全部测试零失败/错误/跳过、64组成对样本、
  旧断言反例、独立复跑样本一致及源码快照一致性。

最终两轮各182项联合测试通过，未将刺客和Nova拆组；64组样本逐条复跑一致。
seed21/44均为原始1点、50%抗性后0点，其余62组为非零抗性伤害。
这些统计没有将种子循环计为额外测试方法，也不表示生产固定点伤害已完成。

基准提交见`source-base-sha.txt`；`prepare-source.ps1 -Repaired`用git archive创建源码快照，
仅覆盖本批测试文件；不传Repaired可重现旧测试。
buildDir、JavaCompile/sourceSet输出与project cache均隔离。
`run-tests.ps1 -Pass first/repeat`拒绝覆盖证据，清理测试结果后运行整个联合选择集。
自然场沿用前批证据，本批生产行为未改动，不重复宣称新增自然场验收；
也未验收共享工作区其他聊天的修改、真实GL/TCP、地形及原生共享单位RNG。
