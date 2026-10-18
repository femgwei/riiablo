# AI 三项依赖联合推进（2026-10-18，未整体完成）

## 本批实现

- 固定点：移植 `D2Common/src/Path/Step.cpp::sub_6FDACEC0` 的普通非导弹、
  碰撞检测分支为 `D2PathStep.stepUnit`，并提供 `Position.stepNativeUnit`。
  输入必须是可信 raw fp16 位置、原生每帧速度向量和路径点；不从 float 反推来源。
  覆盖同格跳过碰撞迁移、零向量格心归位、按最大速度分量截取路径点、快速移动
  中间格探测、碰撞回到最后安全格心、十个 SavedSteps 上限。
  回调可直接连接 `D2CollisionGrid.tryTeleportUnit`，以保留失败后的占位恢复。
- 碰撞：玩家死亡产生的独立遗留尸体写入 CORPSE 3×3 查询层；服务端与本地
  客户端创建时均立即登记并复制死亡地图空间。玩家身上的死亡信息不是第二具
  尸体；复活/回城不带走旧尸体占位。删除或移除 Corpse 组件后清理。
  原版依据：`D2Game/src/PLAYER/PlrModes.cpp` 的 `PATH_SetUnitDeadCollision(..., 1)`，
  以及 `D2Common/src/Path/Path.cpp` 的死亡碰撞实现。
- 表现：`AnimationStepper` 补上 `nativeMummy` 的权威源帧跟随；持续喷火的跟随
  不再依赖仍持有 Sequence 组件。现有 `AnimStepper.processNativeMummy` 是服务端
  源帧依据；不是重新设计怪物思考或伤害逻辑。

## 不能宣称完成的边界

- 新步进 API 尚未接管生产调度。路径 flags、动态路径点游标、房间重缓存、距离
  计数、特殊恢复分支及权威原生速度向量来源仍需接通；Box2D/float 不自动升级。
  不能把仅有 raw 坐标或浮点 Velocity 当作完整原生路径输入。
- 生产碰撞仍为实体投影层。这里的玩家尸体修复没有将生产系统替换成共享
  16 位网格；重叠所有者之间的位互清差异仍在。测试分别验证原生共享网格规则
  和现有实体投影规则，不把两者等同。
- 木乃伊/喷火的资源级无渲染回归不等于所有 MonSeq、COF、DCC 或 GL 视觉验收。
- 未完成自然地图“出生→接敌→移动碰撞→动作→死亡/复活”整链验收。

## 验证

定向测试及架构回归日志：`build/ai-three-dependencies-validation.log`；
最终代码复跑日志：`build/ai-three-dependencies-final.log`。
最终 48 项定向用例全部通过，失败/错误/跳过均为 0；architectureRegression 通过。
覆盖 raw 步进 7、既有方向/精度 5、客户端动画 11、怪物尸体 6、通用 MonSeq 2、
人工地形追踪 6、玩家尸体 4、活体玩家标记 3、旧移动 2、房间接缝 2。
玩家尸体用例包含真实服务端 DeathEvent 的当帧登记；旧客户端创建路径做代码接入，
尚无该路径完整死亡/回城/拾尸的图形验收。
首轮曾因把 Class.PLR 等同于活 Player 而漏掉可渲染尸体，已改为实际 Player
组件判定并在最终复跑覆盖。地形/接缝用例为人工 fixture，不是自然地图验收。
未运行整个 core/AI 测试集；架构任务中的部分模块测试为 UP-TO-DATE。
本文件记录的是三项的可复核增量，不是三项一次性全部完成的声明。
