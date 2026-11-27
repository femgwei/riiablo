# 白骨装甲：setfunc6 出现链与三组错相护盾

后续原版采集已成功，并发现本轮接续阶段停留第0帧造成一帧相位误差；
修复和47个完整三组绘制批次回归见
`necromancer-bone-armor-original-draw-analysis-2026-11-26.md`。
下文的未采集状态及年龄12/20/28、相位16/8/0是本轮历史结论，已由后续记录修正。

本轮继续亡灵巫师技能动画核对，处理 Bone Armor（68）。此前
`necromancer-bone-armor-presentation-analysis-2026-11-24.md` 将它描述成与
Cyclone Armor 共用 setfunc8，这一结论错误。本轮先核对有效表和固定版本 DLL，
再修正客户端路径，未将已有研究当作从未处理。

## 确定的问题

1. 实际 1.10F `bonearmor` 是 state14、setfunc6/remfunc3，`nooverlays=1`。
   旧 StateOverlaySystem 将它送入只接受 setfunc8 的层选择器，返回空数组，
   并跳过普通 castoverlay 分支。因此上一轮只验证数量公式，并未证明白骨护盾能显示。
2. 原版不是直接建立三组相同相位的护盾。它先播放 BoneShieldEmerge，
   完成后建立前后循环层；另外两组由空资源计时器延迟启动。
3. 通用覆盖层按同状态、同资源去重，会使三组同名护盾互相替换。
   原版创建器专门允许 overlay140..142 重复，不能沿用普通去重规则。
4. 白骨装甲并未使用原版覆盖层的速率/混合策略；即使补建普通覆盖层，
   也会落入旧的每秒16帧和 luminosity 路径。
5. StateSerializer 和 ClientUnitSnapshotWriter 仅为飓风装甲保留精确的当前吸收量、
   最大量。白骨装甲接收端缺少选段所需数据，且旧 runtimeValue 为 int16。

## 本机原版离线依据

固定目标：`G:/BaiduNetdiskDownload/Diablo II 1.10F/D2Client.dll`。
SHA-256：`94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282`。
沿用 reverse-engineer-anything 的证据区分；当前无 REA MCP，使用本机 PE/Capstone
只读反汇编。这是离线依据，不是原版绘制观测，也不是 REA Evidence 记录。

复现脚本 `tools/original-observer/analyze_bone_armor_static.py`；完整记录
`tools/original-observer/necromancer-bone-armor-static-1.10f-2026-11-26.json`。

- Set 分发表 RVA F3EE0[6] -> AC1B0；Remove 分发表 F3F60[3] -> ABF60。
- setfunc6 读取 bonearmor(132)、bonearmormax(133)、shortparam1(355)，
  仍采用整数 thirds 阈值。该数量算法共用不意味着状态函数相同。
- 数量增加时清理旧链。数量3创建 overlay3/null1，mode7；数量至少2创建
  overlay4/null2，mode7；再创建 castoverlay，mode4，携带 overlay1、overlay2。
- 更新函数 RVA B7E80，以 AnimRate<<4 的 8.8 游标推进。
  mode4 完成分支 B8026 移除出现层，建立前后 mode3 循环层。
  mode7 完成分支 B80F4 移除空计时器，建立140 ->141/142的 mode4链。
- 创建器 BBBAD 对140..142跳过同ID替换，允许独立动画同时存在。
- 数量下降时逐次移除链表头的前后层；remfunc3清理出现、前后及两个空计时器。

实际 MPQ 数据在无窗口测试中再次读取：

- 140 bonearmor_cast / BoneShieldEmerge：12帧，AnimRate16，PreDraw=false。
- 141 bonearmor_front / BoneShieldFront：24帧，AnimRate16，PreDraw=false。
- 142 bonearmor_back / BoneShieldBack：24帧，AnimRate16，PreDraw=true。
- 143 bonearmor_null1 / null：2帧，AnimRate4。
- 144 bonearmor_null2 / null：2帧，AnimRate2。

可见层使用 Trans3/additive。两个空计时器分别持续8、16个25Hz模拟帧，
每组出现层持续12帧。热资源无窗口生产链路在第12、20、28帧进入循环层；
第28帧三组前后层的相位为16、8、0，每24帧循环。
这些是按原版代码/资源重建的 Riiablo 时钟结果，不是原版实测时长。

资源 SHA-256：

- Emerge：6dec9253d0b9410e5523fa57c64426b060f3f4e3239c51dba707edf765ff0e30。
- Front：3ce03677e9d66508ab4bf535d3ed54f5b433b7704a0b59283c386b9a9d5f9690。
- Back：7bfd0f1a95e5776b0e75cf323a31f9b2f750cf423d2f1af8ec610944f2ab3acb。
- null：8b78d914150fce6050919846b829d03376d0461b10db7fa76fe690a667cc2e23。

## 实现与有界测试

新增 NativeBoneArmorPresentation，与飓风装甲的选层分支分开。数量和修订号按实体保存；
每组覆盖层带 boneArmorGroup，同名资源只在相同组内替换。OverlayManager 负责阶段转换，
OverlayStepper 跳过这些自带时钟的层，避免重复推进。使用真实 DCC 头长度，
出现末帧完整保留一个模拟帧，热缓存转换立即加载下一阶段，前后层同相位播放。

数量下降保留较早组的动画和资源，只清理较高组；数量增加重建全部链。
状态或 UnitStates 移除时清理所有组，取消未完成计时器，保留无关状态/命中层。
延迟加载时等待资产就绪再启动该阶段，不重复请求资源。原版冷加载时序未实测，
不将这一等待策略宣称为精确等价。

同级满量重施保留表现修订号；恢复吸收量导致组数增加时重建，换等级使用新修订号。
依据既有 D2MOO SrvDo018 同级复用状态列表的证据，未改变本轮伤害或吸收公式。

复用 StateP 已有的两个 int32 向量保存白骨装甲当前/最大吸收量，保留原字段名和布局，
无需变更协议结构。直接序列化与独立快照清空原网络缓冲区、重建副本两条路径均检查。
旧服务器缺少这些值时仍不能还原准确阶段，未猜测协同技能等级。

`BoneArmorPresentationTest` 7项检查真实有效表/资源、三组出现与循环相位、损耗阈值、
资源释放、不重启、重施、状态/组件移除、冷资产、较大快照值。
使用真实 World/StateOverlaySystem/OverlayManager/OverlayStepper，AssetManager/DCC
为受控夹具，不创建窗口、纹理或 GPU。额外覆盖既有装甲、状态覆盖层和共享资源生命周期。

初轮7项中3项失败已保留：一项暴露精确快照值未同步，已修复；一项是资产 mock
未声明 contains 导致卸载断言失败，补齐夹具；一项错误期待同级满量重施重播，
按既有 SrvDo018 依据改为保留时钟并增加换级重建检查。

## 原版后台采集尝试与清理

新增 `necromancer-bone-armor-d2gfx`，CXNecro技能68/20级，隐藏启动，目标为最小化绘制采集，
沿用定向 HWND 消息，不请求前台、不移动 OS 光标、不发送全局输入。
本轮没有得到原版游戏内绘制样本：

- 20261126-161020-a03d1cdb：进入阶段 latest.tmp 原子替换报 WinError5，未进入场景。
- 20261126-161416-74d69549：清理上次进程等待超时；随后核实该 PID 已退出。
- 20261126-161832-a98ed665：新进程进入菜单后隐藏状态检查失败，
  `Target no longer hidden/non-iconic`，流程停止，未运行 D2Gfx 采集器。

没有把这些尝试或离线 DLL 分析算成绘制成功；individual_runtime_status
保持 attempted_hidden_capture_failed_before_scene，批次 runtime_status 保持 not_run_in_class_batch。

新增严格固定形状的 `necromancer-loader-cleanup` 任务，通过既有接收器仅关闭目标加载器，
不启动新进程、不进入菜单。第一次因 entry_only 未提供 batches 触发工具 KeyError，
已修复空批次处理；最终清理 run 20261126-162140-c4747a38 passed，
PID16572退出，broker回到ready，active/child_pid为空、cleanup_blocked=false。

## 验证记录和剩余项

JDK17，显式固定 D2_HOME、-Pd2Home 到 1.10F，沿用 Gradle 缓存；
core 与 server:d2gs 分开执行。命令、逐项 XML、各轮准确数量、源码散列、工具日志和
清理状态见 `tools/original-observer/necromancer-bone-armor-validation-2026-11-26/validation.json`。

最终定向 core 65项、server:d2gs 7项、Python 17项，共89项通过，无失败、错误或跳过。
core含7项新白骨装甲用例、17项共享覆盖层资源生命周期用例及41项相关回归。
清理任务还通过无目标时不启动进程的 smoke 检查、非固定任务形状拒绝检查。
收尾未发现原版进程，broker=ready、active/child_pid为空，`git diff --check` 通过。

扩大资源生命周期检查时，8项地面闪光、任务标记、区域标题夹具因未注入 clientContext
在初始化时发生空指针；这些路径未由本轮修改，报告保留，不标为整个扩大测试通过。
最终定向检查仅选择技能及实际覆盖层资源用例。

仍缺原版实际施法/出现/受击/耗尽/重施绘制、最终 GPU 混合与前后遮挡对照。
尤其受击发生在出现阶段时，原版按同ID链表顺序删除，而本次按重建的组身份取消高组链，
属于合理重建的清理策略，需后续运行时核对，未声称逐帧等价。
未提交、未推送，保留既有修改、.tools、.gradle-user 和接收器 heartbeat。
