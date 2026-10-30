# 自动动画绘制验证

运行（PowerShell，属性参数整体加引号）：

```powershell
.\gradlew.bat :desktop:offscreenBlockRender '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' --no-daemon
```

该任务使用隐藏 GL 窗口和 320×240 FBO，无需操作。结果在
`desktop/build/visual-tests/block/result.txt`，每个实际绘制帧输出一张 PNG。
要求进程成功退出且本轮 result.txt 为 PASS；RUNNING、FAIL、异常或超时都不算通过。
需要合法 1.10f MPQ 及 OpenGL 环境；CI 可以提供 `-Pd2Home` 指向自托管资源目录。

场景使用真实 AM/标枪/圆盾 COF/DCC、AnimData、生产 CombatSystem、HitReactionResolver、
SequenceHandler、AnimStepper、AnimationStepper 和 COF 图层加载系统。使用确定性随机源，
将 Fallen 的普通攻击基础伤害输入结算器，触发一次可播放 BL 的盾牌格挡。
每 40ms 推进一次模拟并渲染；不手动设置动画帧或修改动画速度。

验证内容：正确 BL 资源与盾牌层、单次动作的全部动画帧实际提交、每帧 FBO 像素相对
隐藏角色的空场景有变化、动作返回 NU，以及禁止绘制、隐藏盾牌和仅收集首帧时不能判为完整播放。
本场景提前排空资源队列以隔离磁盘/解码延迟，不覆盖真实地图裁剪、怪物 AI 出招、
网络同步、冷加载时序或所有职业/装备；这些需要各自的集成场景。

## 复用探针

动作启动后，用实体 ID、期望模式和 `CofReference.animationRestartId` 创建
`AnimationRenderProbe`。正常地图可以调用 `RenderSystem.setAnimationRenderProbe` 安装，
结束后传入 null 移除。专用 FBO 场景在 `Animation.draw` 后调用 probe.record，传入
真实模式、重启编号、已解析 COF 和 Animation。提交计数来自纹理层真实 batch.draw，
仅更新动画或加载纹理不会增加记录。

`complete()` 要求本次动作的整个 COF 帧范围都出现过有效提交；连续同模式动作不能拼帧。
可以在开始采样前调用 `requireComponents(mask)` 指定必须实际绘制的部件（本场景为 SH），
避免角色身体绘制正常、盾牌没有绘制却通过格挡验收。
正常实时渲染可能跳帧，所以此严格条件用于受控步进验收。图层提交不等于像素可见，
应像当前 FBO 场景一样另做像素差分；也应单独断言动作结束/回到目标模式。

此次场景发现并修复 CofUnloader 在同模式 restart 时释放 COF、而 Resolver/LayerLoader
仍保留该 COF 的生命周期不一致。对应 CofUnloaderRestartTest 覆盖保留及正常切换释放。
