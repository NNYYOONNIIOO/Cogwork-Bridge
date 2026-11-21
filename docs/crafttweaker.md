# CraftTweaker 配方联动

适用于 Minecraft 1.12.2 / Cleanroom、Cogwork Bridge 和 CraftTweaker 4.1.20.699。
CraftTweaker 是可选联动：把 CraftTweaker JAR 放在实例的 `mods`，脚本放在该实例的 `scripts/*.zs`。
没有安装 CraftTweaker 时，本模组仍可运行。发行包不内嵌 CraftTweaker。

开发环境优先使用 `libs/CraftTweaker2-1.12-4.1.20.699.jar`；不存在时从作者 Maven 获取同版本及其 API/ZenScript 依赖，便于 CI 构建。

使用普通配方加载器，**不要**使用 `#loader preinit`。修改后重启游戏；本联动不实现 `/ct reload` 的回滚、JEI 动态重建或服务端脚本自动同步。服务器和客户端应安装相同的模组及脚本。错误和显式调用 `dump()` 的输出写入 `crafttweaker.log`。

## 配方类型

| 类型字符串 | 机器 / 加工 |
| --- | --- |
| `pressing` | 动力冲压机单件压制 |
| `milling` | 石磨；按原机制也可被粉碎轮采用 |
| `crushing` | 粉碎轮 |
| `cutting` | 动力锯；删除也会屏蔽此机器的去皮及 FFD 切石回退 |
| `deploying` | 机械手，支持传送带和置物台 |
| `item_application` | 手持物品对世界方块应用，也可由机械手执行 |
| `sandpaper_polishing` | 手持砂纸及机械手砂纸打磨 |
| `splashing` / `haunting` | 鼓风水洗 / 缠魂 |
| `smoking` / `blasting` | 鼓风烟熏 / 冶炼；删除只影响鼓风加工，不删除原版熔炉配方 |
| `filling` / `emptying` | 注液器 / 分液池；删除只影响机器加工，保留手持容器的流体交互 |
| `mixing` / `compacting` | 工作盆搅拌 / 压缩，含炼药和普通工作台压缩回退 |
| `mechanical_crafting` | 动力合成器，含其采用的普通工作台配方回退 |
| `assembly` | 流水线装配，支持机械手、注液、压制步骤及循环 |

普通工作台和熔炉配方使用 CraftTweaker 自带的 `recipes`、`furnace` API。此处不增加一套重复接口。

## 导入

```zenscript
import mods.cogworkbridge.Recipes;
import mods.cogworkbridge.Assembly;
import mods.cogworkbridge.AssemblyStep;
```

无命名空间的配方名自动使用 `cogworkbridge:`。同类型同名新增会替换旧配方。脚本按执行顺序生效：通常先删除，再添加替代配方。新增配方优先于导入配方和内置回退。

## 金属锭与矿辞产物

导入的机器配方会把未附带 NBT 的金属锭原料扩展为其 `ingot…` 矿辞候选，原料与手持材料槽均适用，JEI 同步显示这些候选。工作台配方使用 Forge 矿辞原料。CRT 输入可直接写 `<ore:ingotBrass>` 等；显式指定的物品/NBT/条件仍按脚本匹配。

所有加工配方的物品产物参数，以及 `Assembly.add` 的最终产物数组，现在也可写矿辞，并支持 `* 数量`：

```zenscript
// 用矿辞产物替代原黄铜搅拌配方，保留铜 + 锌、加热、产出 2 个锭的规则。
Recipes.remove("mixing", <ore:ingotBrass>);
Recipes.addBasin("mixing", "alloys/brass", [<ore:ingotCopper>, <ore:ingotZinc>],
    [], <ore:ingotBrass> * 2, null, 1);
```

矿辞产物在脚本加载时按候选顺序选取第一个满足条件、具有具体 metadata 且堆叠数量合法的物品，之后机器与 JEI 都使用同一个确定产物。它不是随机产物，也不会同时产出多个候选；若要固定为某模组的锭，填写该物品 ID。矿辞为空或没有合法成员时报告脚本错误。已有物品 ID 产物继续支持 NBT。

输入和输出的数量限制不变；物品应用的产物仍须为一个方块，流水线的中间物品仍须指定具体物品。普通工作台和熔炉的产物仍使用 CRT 自带 API。

### 单输入配方

```zenscript
Recipes.add("pressing", "example/iron_sheet", <cogworkbridge:iron_sheet>, <ore:ingotIron>);

// type、name、input、outputs、每个产物的独立概率、基础耗时（tick）
Recipes.addRecipe("crushing", "example/gravel", <minecraft:gravel>,
    [<minecraft:sand>, <minecraft:flint>], [1.0, 0.25], 200);
Recipes.add("smoking", "example/smoked_carrot", <minecraft:baked_potato>, <minecraft:carrot>);
```

`add` 适用 `pressing`、`milling`、`crushing`、`cutting`、`sandpaper_polishing`、四种鼓风加工；默认耗时 100 tick。
`addRecipe` 同样适用这些类型。压制和砂纸只支持一个必得产物；其余支持 1–9 个产物，概率范围 `(0, 1]`，每个产物的数量不得超过该物品最大堆叠数。耗时用于石磨、粉碎轮、动力锯和鼓风加工；压制/手持砂纸沿用机器行程/使用动画时长。

所有单输入/持有物槽每次消耗 **1 个**；不要把 `<ore:ingotIron> * 2` 放入这些槽，参数校验会报错。批量输入请使用工作盆或流水线多步骤。

### 机械手与世界物品应用

```zenscript
Recipes.addDeploying("example/deployer", <minecraft:gold_ingot>,
    <minecraft:iron_ingot>, <minecraft:redstone>, false);
Recipes.addApplication("item_application", "example/casing", <cogworkbridge:andesite_casing>,
    <ore:stripped_logs>, <cogworkbridge:andesite_alloy>, true);
```

最后一个参数 `keepHeld` 为 true 时不消耗持有物。`item_application` 产物必须是一个方块，使用产物的 metadata 放置；`BlockEntityTag` 按原版放置权限应用。世界中的输入按该方块的物品/meta 匹配，不把任意方块实体数据自动转换成输入物品 NBT。
此接口不实现脚本物品变换器、工具耐久变换或额外容器返还。

### 注液与分液

```zenscript
Recipes.addFilling("example/wet_sponge", <minecraft:sponge:1>, <minecraft:sponge>, <liquid:water> * 500);
Recipes.addEmptying("example/sponge_drain", <minecraft:sponge>, <liquid:water> * 500, <minecraft:sponge:1>);
```

注液最多 1000 mB，分液最多 1500 mB。参数为确定性物品产物和明确的流体（支持流体 NBT）；不支持流体矿辞/tag。分液需要指定返还物品。

### 工作盆

```zenscript
Recipes.addBasin("mixing", "example/alloy", [<ore:ingotIron> * 2, <minecraft:redstone>],
    [<liquid:water> * 250], <minecraft:gold_ingot> * 2, null, 1);
Recipes.addBasin("compacting", "example/compress", [<minecraft:coal> * 4],
    [], <minecraft:diamond>, null, 0);
```

参数顺序：type、name、物品输入数组、流体输入数组、物品产物或 null、流体产物或 null、热量。
热量 `0` 无要求、`1` 加热、`2` 超强加热。两种产物至少设置一种，也可同时产出物品和流体。
物品输入合计最多 64 个，支持在不同输入槽分摊扣除。现有工作盆有两个 1000 mB 输入罐、两个 1000 mB 输出罐；单配方最多两种流体输入，每种不超过 1000 mB，一个流体产物不超过 1000 mB。
当前执行器支持一个确定性物品产物和一个确定性流体产物，不支持多种物品产物或概率盆产物。

### 动力合成

```zenscript
Recipes.addMechanical("example/large_grid", <minecraft:diamond>, [
    [<minecraft:iron_ingot>, null, null, <minecraft:iron_ingot>],
    [null, <minecraft:gold_ingot>, <minecraft:gold_ingot>, null]
], true);
```

二维数组必须为矩形，宽高各 1–32；null 为空格。最后一个参数控制是否接受镜像，每个格消耗一个物品。自定义动力合成不额外产生工作台容器返还。

### 流水线装配

```zenscript
Assembly.add("example/assembly", <minecraft:iron_ingot>, <cogworkbridge:incomplete_precision_mechanism>, [
    AssemblyStep.deploy(<minecraft:redstone>, false),
    AssemblyStep.fill(<liquid:water> * 250),
    AssemblyStep.press()
], 2, [<minecraft:diamond>, <minecraft:flint>], [9.0, 1.0]);
```

参数顺序：name、初始原料、中间物品、步骤数组、循环次数、最终产物数组、各产物权重。
支持 1–6 个步骤、1–64 次循环、1–9 个最终产物。**最终按相对权重选择一个产物**，与单步加工的独立概率不同。
中间物品数量必须为 1；流程 ID 和进度写入独立 NBT，不更改该物品注册。物品 tooltip 会显示当前进度与后续步骤。
步骤不足、顺序错误或流程已删除时不会把带进度的中间物品当成普通原料继续加工。自定义流程显示于已有的流水线装配 JEI 分类。

## 删除

```zenscript
Recipes.remove("pressing", <cogworkbridge:iron_sheet>); // 任一产物匹配则删整个配方
Recipes.removeInput("crushing", <minecraft:gravel>);   // 任一原料候选匹配
Recipes.removeWithHeld("deploying", <minecraft:iron_ingot>, <minecraft:redstone>);
Recipes.removeFluidInput("mixing", <liquid:water>);
Recipes.removeFluidOutput("emptying", <liquid:water>);
Recipes.removeByName("pressing", "create:pressing/iron_ingot");
Recipes.removeAll("smoking");
Recipes.removeByName("assembly", "cogworkbridge:precision");
// Recipes.removeAll("assembly");
// Recipes.dump(); // 仅显式调用时将当前已注册的配方 ID 写入 crafttweaker.log
```

流体删除比较流体类型和 NBT，忽略参数中的 mB 数量；物品删除忽略堆叠数量，保留脚本的 meta/NBT 条件。
按输入/输出删除会删除**整个配方及其候选集合**，不是只从一个矿辞候选列表里剔除某一种材料。

上游导入配方 ID 使用 `create:<类型>/<路径>`，去除 `.json`。可用 `Recipes.dump()` 查找，避免猜测名字。
自动生成的跨模组矿物配方名以 `cogworkbridge:generated/` 开头；这类配方建议按输入/输出删除。
内置流水线名字是 `cogworkbridge:precision`、`cogworkbridge:sturdy_sheet`、`cogworkbridge:track`。
原版工作台回退可按其注册 ID 删除在动力合成/盆压缩中的使用，也可使用按输入/输出/全部删除。

删除会同步到机器入口和启动时的 JEI 注册；内置去皮、砂纸、鼓风熔炉、容器、炼药及工作台回退不会重新启用已删除的加工。
随后添加的自定义配方仍然生效。单独删除 `crushing` 会同时屏蔽该删除条件命中的石磨回退，但保留石磨本身。

## 矿辞与匹配边界

`<ore:...>` 使用 CraftTweaker 的实时匹配，JEI 使用启动时可枚举的候选物品。
`withTag` / 精确 NBT / `only` 条件按 CraftTweaker `IIngredient.matches` 执行；JEI 显示的是该条件提供的代表物品，不运行任意回调生成新的示例 NBT。
至少要有一个已注册的候选物品；空矿辞会报错。`transform`、`transformNew`、自定义容器返还不被此接口支持，会明确拒绝，而不会忽略后产生错误配方。

示例文件见 [examples/cogworkbridge.zs](examples/cogworkbridge.zs)。复制进 `scripts` 后会实际更改配方，不会由模组自动安装该示例。
