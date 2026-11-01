# ⚠️ 源码与发布产物脱节 —— 重要

## 现状

| | 内容 |
|---|---|
| **实际发布物** | `release/mtr-next-test-build20261101-1.20.1-fabric.jar`（361714 B） |
| **来源** | GPT-6 提供的已验证修复版二进制（`mtr-next-4to3-car-0.70.0-renderfix.10`），**只改元数据，103 个 .class 字节级未改动** |
| **本目录源码** | `fabric/src/main/java/` —— **33 个类，早于修复版，缺少 70 个类** |

**GPT-6 版有、本地源码完全没有的包**：
- `mtrnext/geometry/` —— `BoardingBridge`、`VehicleGeometryPolicy`、`VehicleGeometryStore`
- `mtrnext/client/geometry/` —— `VehicleGeometryPublisher`
- `mtrnext/client/render/` 的显示系统 —— `BuiltInDisplayRenderer`、`DisplayRenderPlan`、`DisplayClippedVertexConsumer`、`DisplayContext`、`DisplayGeometry`、`DisplayText`、`DisplayTextLayout`、`HostDisplayContext`
- `mtrnext/client/render/` 的连接面 —— `ConnectionGeometry`、`ConnectionRenderPlan`
- `mtrnext/client/render/` 的门/乘客 —— `DoorSidePlan`、`PassengerAreas`、`PassengerHeightResolver`、`PassengerRidingBridge`
- `mtrnext/client/render/RenderSemantics` —— 材质语义
- `mtrnext/formation/FormationHostData`
- 6 个新 mixin —— `TrainFormationMixin`、`TrainDoorScanMixin`、`TrainServerBoardingMixin`、`VehicleRidingServerBoardingMixin`、`TrainClientPassengerMixin`、`VehicleRidingClientAccessor`

**本地源码有、JAR 没有的**：
- `mtrnext/mixin/ItemNodeModifierBaseDebugMixin`（刷子调试 mixin，GPT-6 版移除了）

## 后果

> ⚠️ **`gradle build` 会用这份旧源码覆盖 `release/` 里的融合产物**，已修复的渲染问题会回归。

## 正确做法

### 日常开发（改代码）
1. 在本目录改 `fabric/src/main/java/`
2. `gradle clean jar` 构建
3. 产物在 `build/libs/mtr-next-test-<version>-1.20.1-fabric.jar`
4. **注意**：这份产物基于旧源码，**不含** GPT-6 的修复 —— 如果要发布到测试线，**必须先同步 GPT-6 源码**

### 同步 GPT-6 源码时
1. 反编译参考：`docs/_gpt6-decompiled/`（含 `DIFF-REPORT.md` 差异分析）
2. 用真实源码替换 `fabric/src/main/java/`（反编译产物**不能直接编译**，需人工重写）
3. `gradle clean jar` 重建
4. 覆盖 `release/` 下的 jar
5. 比对新旧 class，确认修复点都在
6. 发测试线（Modrinth `Ss5n00sU` + GitHub tag）

### 只发版不改代码
直接用 `release/` 下的 jar，重打包改元数据即可（**不要跑 gradle build**）。

## 参考

完整架构说明与 API 踩坑清单见 `F:\vibe coding工程\mtr-handover\MTR-Next-系列交接.md`。
