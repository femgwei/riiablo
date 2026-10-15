# D2MOO 上游 1.10f 修正移植：第一批

## 参考基线

- 本机 D2MOO 检出：`8322494ed1f715ad51552f169df76cf600fabc71`（2025-03-01）。
- 审查远端：`0c0bbd1e6bef9181a3da1c046523af622a925416`（2026-10-13）。
  相对旧检出有158个提交，含合并提交，并非全为本月新增。
- 只 fetch 远端引用，没有切换本机 D2MOO 检出。
- 目标为1.10f。本轮读取上游原版地址/反汇编证据，未独立反汇编本地DLL。

## 已移植

### Charged Bolt 的寿命比较顺序

依据 [8b424dc](https://github.com/ThePhrozenKeep/D2MOO/commit/8b424dcc47fb57b5af7ebbd36044e6923ec6cb93)，
1.10f `D2Game.0x6FD15E50` 使用 `cmp ebx,4Eh` 比较完整帧数：>=78则改为77，
之后才向路径距离传递字节参数。法师、Charged Bolt Sentry、Charged Strike 共用此策略。
修正了旧实现/测试/说明中300帧不受限制的错误，新增255/256边界。

刺客 `SkillAss.cpp::sub_6FCF6C10` 在该远端版本仍使用低字节比较；
由独立 `clawsOfThunderLifetimeFrames` 保留，不能随法师回调一并修改。
雷电爪第三层真实释放增加300帧夹具；Phoenix Strike完整整数策略不变。

### 鲜血石魔的分担目标

依据 [2bc0c20](https://github.com/ThePhrozenKeep/D2MOO/commit/2bc0c20e0778c8343da1bcadab43317feb224810)，
`EventFunc26_6FD0F5E0` 计算主人当前HP减去Param5分担量后，应写回主人，最低1HP，
不应覆盖石魔HP。传入伤害仍按原分担量减少，不按主人剩余HP重新限制分担量。

指定安装目录的原始 `Skills.txt` 与运行时均确认 `BloodGolem.Param5=10`。
此前“1.10f为0且复制主人生命”的说明有误，已订正。测试区分原表10%、显式0%、
显式50%及主人最低1HP；回调阶段的石魔HP保持原值，调用者随后扣除剩余伤害。

### DRLG 尚缺差异

依据 [b5b0888](https://github.com/ThePhrozenKeep/D2MOO/commit/b5b0888d75c42eedc02be492131064bbd9fbf210)
及 [1c7d34c](https://github.com/ThePhrozenKeep/D2MOO/commit/1c7d34c)：

- 桥梁候选行使用 `(random+i)%(height-2)+1`，只遍历内部行。
- A5路障上下边界沿X扫描使用width，左右边界继续使用height。
- 同一A5函数原版传入文件号 `2*(BloodyFoothills?1:0)-1`，Java原先在已经得到±1后
  又乘2；一并订正为1/-1，避免错误文件号及绕过-1的原生文件轮转。
- 泥路处理包含 `width+1` 列。
- 墙体链接不能复用地板链表。
- 状态引用查询在没有引用时返回-1；已有同级引用不再次执行初始化回调。
- 每条户外边重新清空packed值；已有方向位修正、房间降级及循环扫描予以保留。

新增不依赖游戏资源的回归，执行实际网格写入、链接插入及状态传播；仅预设尺寸
使用夹具。上游原版多seed全图一致性结果属于上游报告，不是riiablo本轮验收结果。

### Do085非法升级飞弹

依据 [9a701ba](https://github.com/ThePhrozenKeep/D2MOO/commit/9a701ba27047a9ee5132e31199b0ad215b46c9bb)，
`SrvDo085_UnholyBolt_ShamanFire` 的最终ID越界应返回失败。
Java解析失败返回null并终止该发射路径，不再静默回退基础missile。
回归覆盖正常萨满变体、缺失基础行、负数/超上界最终ID以及实际SkillDo不生成弹体。

## 验证

安装目录：`G:\BaiduNetdiskDownload\Diablo II 1.10F`。PowerShell：

```powershell
$env:D2_HOME = 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
$env:D2_TEST = $env:D2_HOME
.\gradlew.bat :core:test `
  --tests com.riiablo.engine.server.MissileNativePolicyTest `
  --tests com.riiablo.engine.server.NecromancerGolemSideEffectTest `
  --tests com.riiablo.engine.server.NativeNecromancerSummonDataTest `
  --tests com.riiablo.engine.server.ShamanFireballAuditTest `
  --tests com.riiablo.engine.server.AssassinMartialArtsTest `
  --tests com.riiablo.engine.server.AssassinSkillSpecializationTest `
  --tests 'com.d2moo.common.drlg.*' `
  --tests 'com.riiablo.map.Act5*' `
  --tests 'com.riiablo.map.d2moo.Act5*' `
  --tests com.riiablo.map.Act1NativeOrthAnchorTest --console=plain
```

最终扩展回归221项通过（30个测试类），失败/错误/跳过均0；`BUILD SUCCESSFUL`。
其中包含6项新增DRLG回归、雷电爪300帧真实释放、原始Skills.txt分担参数校验，
以及A1锚点/A5布局、地形和资源相关测试。`git diff --check`通过。
未运行全项目测试、132关卡多seed/三难度矩阵或真实TCP/GL实机验收。

## 待下一批处理

- `427946c`：入口/出生点WarpDist排除、失败重试和各调用者启用条件。
- `45b2f0c`：硬毛老鼠完整额外弹体分支、`SEIS`低种子和原生单位ID高种子。
- 碰撞、寻路、攻击速度、目标链等本月修正继续专项对照，不视为已移植。
- 不移植1.11+充能技能查找门槛、1.13+目标房间门槛或Ubers行为。
