# 三合一拆解台模组：1.20.1 / 1.21.1 / 1.21.11 三分支对比报告

> 本文档全部基于 git 命令实证（`git branch -a`、`git log`、`git show <ref>:<path>`、`git diff <refA>:<pathA> <refB>:<pathB>`），不切换分支、不修改任何源码。

## 对比所用 ref 清单

| 版本 | ref | 说明 |
|---|---|---|
| 1.20.1 | `1.20.1`（= `origin/1.20.1`） | 独立历史线，纯 Fabric 单模块 |
| 1.21.1 | `1.21.1`（= `origin/1.21.1`） | 双子模块重构线（Fabric + NeoForge） |
| 1.21.11 | `HEAD`（本地分支 `1.21.11`，= `origin/1.21.11`） | 1.21.11 移植完成态 |

**调查中发现的意外情况（如实记录）：**

1. 任务描述中的 `port/1.21.11` 分支**不存在**；当前分支实际名为 `1.21.11`，即 1.21.11 移植分支本体。
2. 任务描述称工作区有未提交改动（NeoForge UncraftingScreen.java、README 等），但 `git status --porcelain` 输出为空，**工作区实际干净**，`HEAD` 提交即最新状态，无需额外注明未提交内容。
3. `1.20.1` 分支与 `1.21.1`/`1.21.11` 分支**历史不共享**（1.20.1 最新提交 `a69b8c6` 为孤立的 "Initial commit"，非 1.21.1 祖先），两线在 1.21.1 重构时被重写/重新提交。
4. 仓库根目录存在未跟踪的 `net/` 目录（已被 `.gitignore` 中的 `/net` 规则忽略，系 IDE 反编译产物），不属于任何分支内容。

---

## 1. 分支概览

| 分支 | 用途 | 最新提交 | Minecraft 版本 | 模组版本 | 映射 / 加载器 | 结构 |
|---|---|---|---|---|---|---|
| `1.20.1` | 1.20.1 稳定线（纯 Fabric） | `a69b8c6` "Initial commit"（2026-07-21） | 1.20.1 | Fabric `mod_version=1.0.3` | Yarn `1.20.1+build.10`；Fabric Loader 0.18.4；Fabric API `0.92.9+1.20.1`；Java 17 | 单模块（根目录 `src/`） |
| `1.21.1` | 1.21.1 双端发布线 | `3156dd7` "1.0.4"（2026-08-19） | 1.21.1 | Fabric `1.0.4` / NeoForge `1.0.4` | Yarn `1.21.1+build.3`；Loader 0.18.4；Fabric API `0.116.13+1.21.1`；NeoForge 21.1.248；Parchment `1.21.1/2024.11.17`；Java 21；ModDevGradle 2.0.143 | 双子模块（`Fabric/` + `NeoForge/`） |
| `1.21.11`（HEAD） | 1.21.11 移植线 | `412d86c` "try port to 1.21.11"（2026-08-21） | 1.21.11 | Fabric `1.0.5-alpha` / NeoForge `1.0.5-alpha` | Yarn `1.21.11+build.6`；Loader 0.19.3；Fabric API `0.141.6+1.21.11`；NeoForge 21.11.45；Parchment `1.21.11/2025.12.20`；Java 21；ModDevGradle 2.0.144 | 双子模块（同 1.21.1） |

版本信息读取来源：各分支 `git show <ref>:gradle.properties`。

补充：
- `1.20.1` 的模组 ID 为旧格式 `three-in-one-uncrafting-table`（连字符）；`1.21.1` 起统一为 `three_in_one_uncrafting_table`（下划线），资源目录、语言键、配置文件名随之整体改名。
- `1.21.1` 起引入测试版识别（版本串含 alpha/beta 时 GUI 显示橙色警示横幅，见 `Fabric/.../block/UncraftingScreen.java` 的 `renderTestVersionWarning`）。
- NeoForge 子模块在 `1.21.1` 分支的 `0f3b5cc..b688eb8`（"try port to 1.21.1 neoforge"）系列提交中新增；`UncraftingRecipeIndex` 在提交 `9a2262d` 首次加入。

---

## 2. 业务实现差异（按功能维度）

说明：大部分业务演进实际发生在 **1.20.1 → 1.21.1 区间**（随双端重构一并落地）；**1.21.1 → 1.21.11 区间**以 API 移植为主，业务语义刻意保持不变（移植注释多处声明"保持原语义"）。

| 功能维度 | 1.20.1（Fabric） | 1.21.1（Fabric + NeoForge） | 1.21.11（HEAD） | 变化区间与关键文件 |
|---|---|---|---|---|
| 拆解配方匹配 | 每次输入变化全量遍历合成/锻造/切石三类配方，逐配方 `getOutput` 新建 ItemStack 比较 | 新增 `UncraftingRecipeIndex`：配方加载后一次性建「产物物品 → 配方」索引并缓存结果，以 RecipeManager 实例身份做缓存失效 | 索引保留；Fabric 改遍历 `ServerRecipeManager.values()` 分桶（`listAllOfType` 已移除）；NeoForge 改用 `recipeMap().byType(...)` | **1.20.1→1.21.1**（提交 `9a2262d`，P1 性能修复）；1.21.1→1.21.11 仅 API 适配。文件：`Fabric/.../block/UncraftingRecipeIndex.java`、`NeoForge/.../block/UncraftingRecipeIndex.java` |
| 经验计算 | 基础公式（合成 ×0.8−数量、锻造 ×1.5、切石 ×0.2）+ 耐久加价 + 附魔加价（按附魔条数 ×2）；`ec5bfb5` 修复"0xp 无法设置"（移除 `experienceCost < 1 强制置 1`，引入 `xpCostMultiplier`） | 公式重构为 `Math.max(0, ...)` 防负数；耐久/附魔加价抽成公共方法 `applyDamageAndEnchantmentCost`；配置实时读取（不再构造时快照）；附魔加价受"附魔转移"开关控制 | 不变 | **1.20.1→1.21.1**。文件：`Fabric/.../block/UncraftingTableBlockEntity.java`（NeoForge 端同逻辑） |
| 附魔转移 | 基于 NBT（`getEnchantments()` NbtList + `EnchantedBookItem.addEnchantment`），无开关 | 迁移到数据组件（`ItemEnchantmentsComponent` + `DataComponentTypes.STORED_ENCHANTMENTS`）；新增 `enableEnchantmentTransfer` 开关；关闭时附魔保留在装备上不转移 | 不变（组件 API 微调） | **1.20.1→1.21.1**。文件：`UncraftingTableBlockEntity.java`、`config/ModConfig.java` |
| 纹饰/锻造模板拆解 | 通过 `SmithingTrimRecipeAccessor`（template/base/addition 三字段 Accessor）拆纹饰配方；模板/材料物品直接取自 `TrimPattern.templateItem()` / `TrimMaterial.ingredient()` | 同左（`templateItem()`/`ingredient()` 仍在），仅包路径 `net.minecraft.item.trim.*` | 纹饰系统重构：smithing_trim 按图案拆成多个配方，`TrimPattern`/`TrimMaterial` 不再持有物品；改为索引期构建「纹饰图案→模板物品」「纹饰材料→材料物品」两张反查表（材料经 `PROVIDES_TRIM_MATERIAL` 组件反查物品注册表）；Fabric 新增 `SmithingTrimRecipePatternAccessor`，NeoForge 经 AT 开放 `SmithingTrimRecipe.pattern` | **1.21.1→1.21.11**。文件：`UncraftingRecipeIndex.java`（`recordTemplateItem`/`buildMaterialItemMap`）、`UncraftingTableBlockEntity.java`（`fillSmithingOutput`）、`Fabric/.../mixin/SmithingTrimRecipePatternAccessor.java`、`NeoForge/.../META-INF/accesstransformer.cfg` |
| 黑名单 | 无 | 新增 `blacklistItems`（按物品注册表 ID 匹配，禁止匹配任何配方含纹饰路径，关闭 GUI 时原样归还） | 不变（NeoForge 端曾因 ModConfigSpec 列表 API 问题修复过，体现为 `List<? extends String>` 读取方式） | **1.20.1→1.21.1**。文件：`config/ModConfig.java`、`UncraftingTableBlockEntity.isItemBlacklisted` |
| 脚本配方开关 | 无 | 新增 `enableKubeJSRecipes` / `enableCraftTweakerRecipes`（按配方 ID 命名空间过滤，索引层生效） | 不变 | **1.20.1→1.21.1**。文件：`UncraftingRecipeIndex.isScriptRecipeAllowed`、`config/ModConfig.java` |
| quickMove 与 BlockEntity 联动 | `onQuickTransfer` 后统一调 `onOutputChanged(movedStack)`（按移动后堆调用） | Fabric：输出槽只按**实际取出数量**调用一次 `onOutputChanged(takenStack)`，与 NeoForge 端语义对齐；`onOutputChanged` 对空堆提前返回（防止输入被消耗前错误补货刷物品） | 不变（仅 `player.getWorld()` → `getEntityWorld()` 等 API 改名） | **1.20.1→1.21.1**。文件：`Fabric/.../block/UncraftingScreenHandler.java` |
| 批量广播（P2） | 无：`markDirty` 每次直接 `world.updateListeners`，逐槽位变更约 18 次重复广播 | 新增 `runBatched`（`batchDepth`/`batchDirty` 可嵌套批处理），批末合并为一次 `updateListeners`；覆盖 `searchRecipeToOutput`/`clearOutputSlots`/`closeInventory`/`updateOutputSlots` | 不变 | **1.20.1→1.21.1**。文件：`Fabric/.../block/UncraftingTableBlockEntity.java`（NeoForge 端同名机制） |
| 一键收取 | `closeInventory`：已取产物时把输出槽塞回背包/掉落；未取时归还输入与书本，但**未清空预览输出槽**（存在原料+产物同时归还的刷物品隐患） | `closeInventory` 重写：加服务端校验 + `runBatched` 包裹；已取过产物（`outputGetCount>0`）→归还剩余产物与残留输入；未取过→清空预览输出槽仅归还输入；书本原样归还；统一走 `returnToPlayer` | 不变 | **1.20.1→1.21.1**。文件：`UncraftingTableBlockEntity.closeInventory` |
| 容器距离 | `pos.isWithinDistance(..., 4.5)` | 改为与原版容器一致的 8 格（平方距离 ≤64），Fabric/NeoForge 两端对齐 | 不变 | **1.20.1→1.21.1**。文件：`UncraftingTableBlockEntity.canPlayerUse` |
| 配方切换/GUI 交互 | 左/中/右三按钮（上一配方/一键收取/下一配方）+ 悬浮提示 | 同左；新增测试版警示横幅；书本槽幽灵物品渲染移除（1.20.1 有 `drawItemInSlot` 幽灵书，1.21.1 删除） | 按钮事件签名迁移（Fabric `mouseClicked(Click, boolean)`；NeoForge `mouseClicked(MouseButtonEvent, boolean)`）；音效 `PositionedSoundInstance.master` → `ui` | 1.20.1→1.21.1 为功能演进；1.21.1→1.21.11 为 API 迁移。文件：`block/UncraftingScreen.java`（两端） |

---

## 3. 代码层面差异

### 3.1 Fabric 侧

| 维度 | 1.20.1 | 1.21.1 | 1.21.11（HEAD） |
|---|---|---|---|
| 注册方式 | `Registry.register(Registries.X, Identifier, ...)` + `FabricBlockSettings.copyOf` / `FabricItemSettings` / `FabricBlockEntityTypeBuilder` | `AbstractBlock.Settings.copy(...)` + `Item.Settings()` + 原版 `BlockEntityType.Builder.create`；`ExtendedScreenHandlerType<>((syncId, inv, buf) -> ...)` 内联读 `BlockPos` | 1.21.2+ 要求 Settings 携带 `RegistryKey`：新增 `UNCRAFTING_TABLE_KEY`/`UNCRAFTING_TABLE_ITEM_KEY` 常量；`BlockItem` 加 `useBlockPrefixedTranslationKey()` 保持翻译键不变；`BlockEntityType.Builder` 移除，改回 Fabric API `FabricBlockEntityTypeBuilder`。文件：`Fabric/.../ThreeInOneUncraftingTable.java` |
| GUI 渲染 API | `new Identifier(...)`；`drawTexture` 不带纹理尺寸参数；`renderBackground(context)` | `Identifier.of(...)`；`drawTexture` 需补 256,256 纹理尺寸；`renderBackground(context, mouseX, mouseY, delta)` | `drawTexture` 首参渲染层 `RenderPipelines.GUI_TEXTURED`；`mouseClicked(double,double,int)` → `mouseClicked(Click, boolean)`；`drawText` 对 alpha=0 颜色直接跳过，颜色常量改为带不透明 alpha（标题 `4210752`→`0xFF404040`，经验提示 `8453920/16736352`→`0xFF80FF20/0xFFFF6060`）。文件：`Fabric/.../block/UncraftingScreen.java` |
| 配方系统 | `RecipeManager.listAllOfType`；`recipe.getOutput(registryManager)`；`Ingredient.getMatchingStacks()`；纹饰用 `SmithingTrimRecipeAccessor` + `SmithingTransformRecipeAccessor`（template/base/addition 三 Accessor） | `listAllOfType` 返回 `RecipeEntry`；`getResult(registryManager)`；Mixin 仅留 `SmithingTransformRecipeAccessor`（`SmithingTrimRecipeAccessor` 于提交 `0f3b5cc` 删除） | `ServerRecipeManager.values()` 遍历分桶；`Recipe#getResult` 移除 → 按类型经 Accessor 读取：新增 `ShapedResultAccessor`/`ShapelessResultAccessor`/`SingleStackRecipeAccessor`（切石 result 仅 protected）、重写 `SmithingTransformRecipeAccessor`（改读 `TransmuteRecipeResult`）、新增 `SmithingTrimRecipePatternAccessor`；有序配方原料变 `List<Optional<Ingredient>>`；无序配方原料列表经 `ShapelessIngredientsAccessor` 读取；`Ingredient.getMatchingStacks()` 移除 → `getMatchingItems()` 流构造堆。文件：`Fabric/.../mixin/*.java`、`threeinoneuncraftingtable.mixins.json`、`block/UncraftingTableBlockEntity.java` |
| 配置文件 | JSON（Gson），`config/three-in-one-uncrafting-table.json`；仅 6 项开关 | JSON（Gson），改名 `config/three_in_one_uncrafting_table.json`；新增脚本开关 + 黑名单共 9 项 | 同 1.21.1，另加 `JsonParseException` 捕获兜底：用户手改导致 JSON 损坏时回退默认配置并告警，避免启动崩溃。文件：`Fabric/.../config/ModConfig.java` |
| 屏幕打开数据 | `writeScreenOpeningData(ServerPlayerEntity, PacketByteBuf)` 手写 `writeBlockPos` | `ExtendedScreenHandlerFactory<BlockPos>` + `getScreenOpeningData` 返回 `BlockPos`（`BlockPos.PACKET_CODEC`） | 同 1.21.1；`getScreenOpeningData` 加 `@NullMarked` 注解适配 jspecify 空值作用域 |
| 资源文件 | 目录 `assets/three-in-one-uncrafting-table/`，物品模型 `models/item/uncrafting_table.json`（parent 指向方块模型）；配方目录 `data/.../recipes/`，原料 `{"item": "..."}` 对象格式 | 目录改下划线；`models/item/` 保留；配方目录 `recipe/`，原料仍为对象格式 | `models/item/` 删除，改为 1.21.2+ 的 `assets/.../items/uncrafting_table.json`（`{"model": {"type": "minecraft:model", ...}}`）；配方 ingredients 改为纯字符串格式（`"minecraft:smithing_table"`）。文件：`Fabric/src/main/resources/` 下对应 JSON |
| fabric.mod.json | id `three-in-one-uncrafting-table`；`minecraft: ~1.20.1`；`java: >=17` | id 改下划线；`minecraft: ~1.21.1`；`java: >=21`；`fabricloader: >=0.18.4` | `minecraft: ~1.21.11`；`fabricloader: >=0.19.3` |
| 构建脚本 | 根 `build.gradle` 直接应用 `fabric-loom-remap`，Java 17 | 根 build.gradle 变 subprojects 父配置（Java 21 toolchain + manifest 属性）；`Fabric/build.gradle` 独立应用 loom | `gradle.properties`：Yarn `1.21.11+build.6`、Loader 0.19.3、Fabric API `0.141.6+1.21.11`、`fabric_mod_version=1.0.5-alpha`；`Fabric/build.gradle` 相对 1.21.1 无变化 |

### 3.2 NeoForge 侧

NeoForge 子模块自 1.21.1 起存在，1.20.1 无对应内容。

| 维度 | 1.21.1 | 1.21.11（HEAD） |
|---|---|---|
| 注册方式 | `DeferredRegister.Blocks/Items/BLOCK_ENTITIES/MENUS`；`BLOCKS.register(id, () -> new UncraftingTableBlock(Properties.ofFullCopy(...)))`；`BlockEntityType.Builder.of(...).build(null)`（带 `@SuppressWarnings("ConstantConditions")`） | 1.21.11 中 `Properties` 构造方块时必须已绑定 block id：改用 `BLOCKS.registerBlock("uncrafting_table", UncraftingTableBlock::new, () -> Properties.ofFullCopy(...))` id 绑定重载注入；`BlockEntityType.Builder` 移除，直接 `new BlockEntityType<>(factory, block)`。文件：`NeoForge/.../ThreeInOneUncraftingTable.java` |
| GUI 渲染 API | `ResourceLocation.fromNamespaceAndPath`；`guiGraphics.blit(TEXTURE, ...)`；`guiGraphics.renderTooltip(...)`；`mouseClicked(double,double,int)` | `ResourceLocation` 改名 `Identifier`；`blit` 首参渲染层 `RenderPipelines.GUI_TEXTURED` 且 UV 坐标 float 化；tooltip 改 `setTooltipForNextFrame`；`mouseClicked(MouseButtonEvent, boolean)`；文本颜色补 alpha（`0xFF404040` / `0xFF80FF20` / `0xFFFF6060`）。文件：`NeoForge/.../block/UncraftingScreen.java` |
| 配方系统 | `RecipeManager.getAllRecipesFor(type)`；`getResultItem(registryAccess)`；`Ingredient.getItems()`；`SmithingTransformRecipe` 的 template/base/addition 经 `accesstransformer.cfg` 开放 | `level.recipeAccess()` + `recipeManager.recipeMap().byType(...)`；`Recipe#getResultItem` 移除 → result 字段经 AT 直读（`resultItemOf` switch 按 ShapedRecipe/ShapelessRecipe/SingleItemRecipe/SmithingTransformRecipe 取 `result` 字段）；`Ingredient.getItems()` 移除 → `items()` 流取首个匹配物品；`RecipeHolder.id()` 返回 `ResourceKey`，命名空间经 `id().identifier().getNamespace()`。文件：`NeoForge/.../block/UncraftingRecipeIndex.java`、`UncraftingTableBlockEntity.java` |
| AccessTransformer | `META-INF/accesstransformer.cfg`：开放 `SmithingTransformRecipe` 的 `template/base/addition` 三字段 | template/base/addition 官方已 public（Optional 化）故移除；新增开放 `ShapedRecipe.result`、`ShapelessRecipe.result`、`ShapelessRecipe.ingredients`、`SingleItemRecipe.result`、`SmithingTransformRecipe.result`、`SmithingTrimRecipe.pattern` 共 6 条 |
| 配置文件 | 官方标准 TOML（`ModConfigSpec`），生成 `config/three_in_one_uncrafting_table-common.toml`，注释由 FML 自动写入；9 项配置与 Fabric JSON 对齐 | 不变（`NeoForge/.../config/ModConfig.java` 在两分支间无 diff） |
| 容器 API | `ItemStackHandler` + `SlotItemHandler`（Input/Output/Book 三个自定义槽类联动 `onOutputChanged`/`onInputChanged`） | 语义保留不迁移新容器 API；`ItemStackHandler`/`SlotItemHandler` 在 NeoForge 1.21.9+ 标记 forRemoval，整类/逐处 `@SuppressWarnings("removal")` 抑制（实测 javac/IDEA 仅认 `removal` key）；import 无法被抑制处改全限定名。文件：`UncraftingTableBlockEntity.java`、`UncraftingScreenHandler.java`、`UncraftingScreen.java` |
| 空值注解 | 包级 `@ParametersAreNonnullByDefault` + `@MethodsReturnNonnullByDefault`（`package-info.java`） | 迁移到 `@org.jspecify.annotations.NullMarked`；可空返回显式 `@Nullable`。文件：`NeoForge/.../block/package-info.java` |
| 资源/元数据 | `models/item/uncrafting_table.json`；`neoforge.mods.toml` 模板占位符（`${neo_version}`、`${minecraft_version_range}` 等由 gradle 注入） | `items/uncrafting_table.json` 替代 item 模型；`neoforge.mods.toml` 文件本身无 diff（版本范围经 `gradle.properties` 的 `[1.21.11]` 注入） |
| 构建脚本 | ModDevGradle `2.0.143`；neo_version `21.1.248`；Parchment `1.21.1/2024.11.17` | ModDevGradle `2.0.144`；datagen 任务 `data()` → `clientData()`；neo_version `21.11.45`；Parchment `1.21.11/2025.12.20`。文件：`NeoForge/build.gradle`、`gradle.properties` |

### 3.3 仓库级差异（跨两端）

| 维度 | 1.20.1 | 1.21.1 / 1.21.11 |
|---|---|---|
| 目录结构 | 单模块根 `src/` | `settings.gradle` `include("Fabric", "NeoForge")`；根 build.gradle 为 subprojects 公共配置 |
| CI | `actions/* @v4`，JDK 25（待确认：该组合在 1.20.1 分支实际配置为 java-version '25'，疑似超前配置），产物 `build/libs/` | checkout@v6 / wrapper-validation@v6 / setup-java@v5（JDK 21）/ upload-artifact@v7；分 Fabric/NeoForge 双产物。文件：`.github/workflows/build.yml` |
| 文档 | README 图片链接指向 `1.20.1` 分支路径 | 图片链接改指 `1.21.1` 分支 `image/` 目录；新增 Modrinth/CurseForge 链接小节 |

---

## 4. 可同步的部分（跨分支回移/移植建议）

按优先级排列。方向约定：`从 → 到`。

| 优先级 | 改动 | 同步方向 | 涉及文件 | 兼容性注意事项 |
|---|---|---|---|---|
| P1 | Fabric 配置解析 `JsonParseException` 兜底（配置文件损坏时回退默认而非启动崩溃） | 1.21.11 → 1.21.1 及 1.20.1 | `Fabric/.../config/ModConfig.java`（1.20.1 为 `src/.../config/ModConfig.java`） | 纯增量 try-catch，三版本 Gson 均可用；1.20.1 端字段集不同（无黑名单/脚本开关），仅移植 catch 块即可 |
| P1 | GUI 文本颜色补不透明 alpha（`0xFF404040`、`0xFF80FF20`、`0xFFFF6060`） | 1.21.11 → 1.21.1 及 1.20.1 | `Fabric/.../block/UncraftingScreen.java`；`NeoForge/.../block/UncraftingScreen.java`（仅 1.21.1 有 NeoForge 端） | 旧版 `drawText`/`drawString` 对无 alpha 颜色会自动补不透明，带 `0xFF` 前缀在旧版渲染结果完全一致，可安全回移；属于防御性修复，防止未来渲染管线行为变化 |
| P2 | Fabric 一键收取 `closeInventory` 防刷修复 + `returnToPlayer` 统一归还 | 1.21.1 → 1.20.1 | `src/.../block/UncraftingTableBlockEntity.java`（1.20.1 路径） | 1.20.1 无 `runBatched`，若回移需一并移植批量广播机制或去掉批处理包裹；涉及 `outputGetCount` 语义，需整段替换并回归测试 |
| P2 | quickMove 按实际取出数量调用 `onOutputChanged(takenStack)` + 空堆提前返回（防刷联动） | 1.21.1 → 1.20.1 | `src/.../block/UncraftingScreenHandler.java`、`src/.../block/UncraftingTableBlockEntity.java` | 依赖 1.21.1 重构后的 `onOutputChanged` 结构；1.20.1 的 `onOutputChanged` 嵌套较深，需先同步 `outputGetCount` 分支重构，改动面较大 |
| P2 | 黑名单 + KubeJS/CraftTweaker 脚本配方开关 + 附魔转移开关（功能级特性） | 1.21.1 → 1.20.1 | `config/ModConfig.java`、`UncraftingTableBlockEntity.java`、`UncraftingRecipeIndex.java`（1.20.1 无此类，需先移植索引或改写为内联过滤） | 1.20.1 无 `UncraftingRecipeIndex`，脚本配方过滤需改在 `findMatchingRecipes` 遍历处按 `Recipe.getId().getNamespace()` 实现；配置文件为旧文件名/旧字段集，注意老用户配置兼容 |
| P2 | 配方索引 `UncraftingRecipeIndex` + `runBatched` 批量广播（P1/P2 性能修复） | 1.21.1 → 1.20.1 | `block/UncraftingRecipeIndex.java`（新增）、`UncraftingTableBlockEntity.java` | 1.20.1 API 为 `Recipe`（无 `RecipeEntry`）、`getOutput`、`listAllOfType`，索引类需按 1.20.1 API 重写；收益明确（大量配方整合包场景），但工作量最大，建议单独排期 |
| P2 | README 图片链接修正与 Modrinth/CurseForge 链接 | 1.21.1 → 1.20.1 | `README.md`、`README_zh.md` | 链接中的分支名需按目标分支改写为 `1.20.1`；已验证 `git ls-tree 1.20.1 image/` 确认 1.20.1 分支存在 `image/` 目录（1.png~6.gif），故仅改写链接中的分支名即可，图片路径可保持不变；另需把中文 README 链接的 `blob/main` 改为 `blob/1.20.1` |
| P2 | CI 工作流修正（JDK 版本与产物路径） | 1.21.1 → 1.20.1 | `.github/workflows/build.yml` | 1.20.1 分支为 Java 17 项目，setup-java 应为 '17' 而非直接套用 '21'；产物路径仍为 `build/libs/`（单模块） |
| — | 不建议回移项 | — | — | 1.21.11 的 RegistryKey 注册、RenderPipelines 渲染层、配方 Accessor/AT 改造、items/ 模型均为 1.21.2+ 专属 API，旧版不存在对应接口，无法回移 |

---

## 5. 主要演进节点定位（版本区间速查）

| 演进节点 | 发生区间 | 实证依据 |
|---|---|---|
| 模组 ID/资源目录连字符 → 下划线改名 | 1.20.1 → 1.21.1 | `git diff 1.20.1:src/... 1.21.1:Fabric/src/...`、fabric.mod.json |
| NBT 附魔 → 数据组件附魔 + 附魔转移开关 | 1.20.1 → 1.21.1 | `git diff 1.20.1:src/.../block/UncraftingTableBlockEntity.java 1.21.1:Fabric/src/.../block/UncraftingTableBlockEntity.java` |
| 黑名单、脚本配方开关 | 1.20.1 → 1.21.1 | ModConfig 两分支 diff |
| 配方索引 UncraftingRecipeIndex（P1） | 1.20.1 → 1.21.1（提交 `9a2262d`） | `git log --diff-filter=A` |
| runBatched 批量广播（P2） | 1.20.1 → 1.21.1 | 1.21.1 Fabric BE 中新增代码块 |
| NeoForge 子模块 + TOML 配置 + AccessTransformer | 1.20.1 → 1.21.1（提交 `0f3b5cc..b688eb8`） | `git log -- NeoForge` |
| 死代码清理（SmithingTrimRecipeAccessor 删除、outputCounter 移除、幽灵书渲染移除） | 1.20.1 → 1.21.1 | `git log --diff-filter=D`（提交 `0f3b5cc`）及上述跨路径 diff |
| 纹饰反查表、配方 result Accessor/AT 改造、渲染管线化、GUI alpha 修复、JsonParse 兜底 | 1.21.1 → 1.21.11 | `git diff 1.21.1 HEAD` |
