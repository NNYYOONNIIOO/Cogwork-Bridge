# 可配置粉碎粗矿

Cogwork Bridge 可以通过配置新增真正的粉碎粗矿物品，并按矿物辞典自动补配方。无需新增 Java 代码或安装 CraftTweaker；安装 CraftTweaker 后，也可使用普通配方脚本调整这些物品的加工方式。

## 配置示例

文件位于实例的 `config/cogworkbridge/crushed_ores.cfg`，首次使用本功能的版本启动时会生成。可提前创建文件；修改后完全退出并重启游戏。客户端与服务器须使用相同的材料定义，注册名在存档使用后应保持不变。

```text
general {
    B:autoRecipes=true

    S:materials <
        tungsten|Tungsten|A8A8B0|粉碎钨矿
        cobalt|Cobalt|3F66C5|粉碎钴矿
    >
}
```

每行格式为 `注册名|矿辞后缀及别名|六位RGB颜色|显示名称`：

- 注册名使用小写字母、数字和下划线。上例生成 `cogworkbridge:crushed_raw_tungsten` 和 `cogworkbridge:crushed_raw_cobalt`，每种材料是独立物品；不能覆盖内置物品或重复注册。
- 矿辞后缀区分大小写，不包括 `ore` 等前缀。多个别名使用英文逗号，例如 `tungsten|Tungsten,Wolfram|A8A8B0|粉碎钨矿` 会同时查找 `oreTungsten` 和 `oreWolfram` 等。
- 颜色为不带 `#` 的六位十六进制 RGB。通用模型引用必需前置 Create JAR 的银色粉碎粗矿纹理，再乘以此颜色；不打包上游纹理。资源包可替换 `assets/cogworkbridge/models/item/configured_crushed_ore.json` 这个通用模型。
- 显示名称可以填写中文或英文；留空时使用 `Crushed Raw <首个矿辞后缀>`。资源包若提供 `item.cogworkbridge.crushed_raw_tungsten.name` 等语言键，其翻译优先于配置名。

默认材料列表为空，不额外生成示例物品。原有锇、铂、银、锡、铅、汞、铝、铀、镍九种兼容材料继续使用既有逻辑，不需要在配置中重复声明。

配置物品即使暂时没有匹配矿物也会注册并显示在创造栏，方便后续配方脚本引用。无效条目会在日志中给出警告并跳过；修正条目后重新启动。

## 自动适配范围

`autoRecipes=true` 时，按每个后缀及别名查找已安装模组的矿辞：

| 已存在的矿辞 | 自动补充的配方 |
| --- | --- |
| `oreTungsten` | 粉碎轮：1 粉碎钨矿，另有独立 75% 概率增加 1 粉碎钨矿、75% 概率获得 1 经验颗粒；基础耗时 400 tick |
| `rawTungsten` | 粉碎轮：1 粉碎钨矿，75% 概率获得 1 经验颗粒；400 tick |
| `blockRawTungsten` | 粉碎轮：9 粉碎钨矿，75% 概率获得 9 经验颗粒；400 tick |
| `nuggetTungsten` | 水洗：1 粉碎钨矿 → 9 钨粒；100 tick |
| `ingotTungsten` | 熔炉及 FFD 高炉：1 粉碎钨矿 → 1 钨锭；经验 0.1，高炉耗时 100 tick |

只为实际存在的矿辞补配方；不会凭空生成矿石、粗矿、粒或锭，不会扫描所有矿辞自动创建材料名称。粉碎和水洗跳过已有相同输入的导入配方。多个粒/锭候选按配置的别名顺序及矿辞候选顺序选取第一个具有具体 metadata 的物品，不随机输出；JEI 使用相同结果。

新增粉碎粗矿还会注册 `crushedTungsten`、`crushedRawTungsten` 等矿辞，每个别名均注册。设置 `autoRecipes=false` 会关闭所有**配置新增材料**的自动配方，只生成物品、模型和矿辞；内置材料不受此设置影响。

## CRT 示例

先将钨加入上面的配置，再把以下脚本放到实例的 `scripts/crushed_ores.zs`。使用普通加载器，不要写 `#loader preinit`。配置负责早期注册物品；普通 CRT 配方脚本不会创建新物品。修改配置或脚本都需要重启；示例不会自动安装。

```zenscript
import mods.cogworkbridge.Recipes;

// 将钨矿的粉碎产量改为必得 2 个。所引用的矿辞须已有成员。
Recipes.removeInput("crushing", <ore:oreTungsten>);
Recipes.addRecipe("crushing", "compat/tungsten", <ore:oreTungsten>,
    [<cogworkbridge:crushed_raw_tungsten> * 2], [1.0], 400);

// 自定义水洗产物。<ore:nuggetTungsten> 也可以换成明确的物品 ID。
Recipes.removeInput("splashing", <cogworkbridge:crushed_raw_tungsten>);
Recipes.addRecipe("splashing", "compat/tungsten_washing",
    <cogworkbridge:crushed_raw_tungsten>, [<ore:nuggetTungsten> * 9], [1.0], 100);
```

这些 API 和已有机器配方使用相同的矿辞匹配、删除规则及 JEI 展示。若使用别名，同一个自动粉碎配方包含多个别名候选；按输入删除会删除整条配方，替换时应把需要的别名物品也纳入输入矿辞。`autoRecipes=false` 时可以只保留新增语句，自行定义加工链；普通熔炉配方使用 CraftTweaker 自带 `furnace` API。本页机器脚本接口不管理 FFD 高炉配方，自动高炉配方随 `autoRecipes` 一起关闭。

完整机器脚本接口见 [CraftTweaker 配方联动](crafttweaker.md)。
