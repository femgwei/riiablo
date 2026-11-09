# 野蛮人跳跃 / 跳跃攻击：起跳、飞行与落地

用户反馈角色像直接平移，缺少起跳和腾空。已先读取职业绘制进度及 APK 参考批次记录，
再核对当前动作链、参考包 JS / 图集和 D2MOO 玩家序列表。

## 已确认原因与依据

- 当前玩家 SQ 回退到 `seqtrans=A1`，没有使用野蛮人 S1。LeapSystem 同时从施法开始
  立即平移；逻辑 AnimData 的第 8 帧冻结也没有传递给普通玩家的 AnimationStepper。
- 参考包 `itemArt-BOATIR-s.js` 的 `xY` / `SY`（约 31492 行）播放 S1，前后各留动作段，
  中间线性移动。`Game-BrCacnVd.js::animateHero`（约 18580 行）将动作时间映射到完整
  图集帧。`classes.json` 的 barbarian S1 为 15 帧；已直接查看该图集，存在蹲身、
  腾空和落地姿势，没有凭空添加第二条高度曲线。
- D2MOO `D2Common/src/DataTbls/SequenceTbls.cpp` 的玩家序列 13 / 14 同样从 S1 开始，
  point 5 启动移动，point 11 维护落地，跳跃攻击在 S1 之后接武器相关的 A1 尾段。
  `D2Game/src/SKILLS/SkillBar.cpp::SKILLS_Leap` 在玩家飞行时回卷至 point 10。
  这是静态源码 / 资源核对，没有运行 APK 或采集原版窗口逐帧数据。

## 本轮实现

- 玩家两个技能均选择 S1，起跳动作到 point 5 前保持地面起点；飞行中推进至 point 10
  后停留，移动完成才恢复落地段。跳砍随后按原生武器类别选择 A1 来源帧和挥击回调点。
- 飞行区间采用参考包的线性平移，保留现有落点检查和按距离计算的飞行时长；没有照搬
  Survivors 固定 0.6 秒整段动作或自定义范围伤害。S1 本身负责腾空姿势与图像位移。
- 权威移动完成一次性派发 LeapLandEvent，供本地落地 dust 使用；补齐 CltSt30 的入口。
- CofReferenceP 追加可选 `sequenceFrame=-1`，传递 S1 空中停帧及 A1 来源帧。
  普通动作继续走原有动画时钟；新实体插入及异步 COF/DCC 加载保留帧定位。
  远程 LeapSystem 不再改写复制坐标。客户端和服务端应同时更新以获得完整新表现。
- 怪物的 NativeLeapMotion、沙地跳跃者第 8 帧保持 / 第 12 帧落地逻辑保持原路径。

本轮针对玩家动作显示和移动时序；玩家 SrvDo78 的伤害、普通 Leap 的范围击退和原生
完整路径 / 碰撞规则不因此宣称完成。网络字段仅传递来源帧，未新增网络落地音效事件。
双机窗口的手感、网络插值延迟下的画面一致性仍需用户远程验证。

## 验证

使用 JDK 17 和本机 1.10f MPQ。新测试覆盖实际 cast 链、起跳停留、飞行保持、落地一次、
武器 A1 尾段、结束清理、真实 S1 资源、无本地 Sequence 的远端帧定位，以及旧包默认值。
同批回归 Leap、怪物动作、原生路径、动画加载与服务端到客户端序列化。
最终 9 个 suite、57 项测试通过，失败 / 错误 / 跳过均为 0，包含新增长短距离实际 cast
检查和 5 项新测试。`verifyModuleBoundaries`、桌面端 / D2GS 编译及 `git diff --check`
通过。日志：`build/player-leap-closeout.log`、`build/player-leap-compile.log`。

扩大执行 `architectureRegression` 时，其依赖 `mapEntityBoundaryCheck` 因当前 HEAD
已有的 MapManager 三条 Jerhyn / Hratli / Larzuk 创建适配器偏差而失败；本轮没有修改
MapManager，也没有更新检查基线。该组合命令因此返回 1，不能记为完整架构检查通过；
57 项动作 / 联网测试在该命令中已经完成且全部通过，编译随后独立确认通过。
