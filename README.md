# Cogwork Bridge / 齿轮桥接器

[English](#english) | [简体中文](#简体中文)

## English

Cogwork Bridge brings Create machinery to **Minecraft 1.12.2** on Cleanroom. It requires the original Create 1.21.1 JAR for assets and recipe data while providing the 1.12.2 implementation of the machinery.

The project is in **Alpha**. Many devices are already available, but some features, recipes, interactions, and compatibility are still being developed. Cogwork Bridge does not yet have full parity with Create 1.21.1.

### Features

- **Power and transmission:** Generate power with hand cranks, water wheels, windmills, and steam engines, then connect machines with shafts, cogwheels, gearboxes, and other transmission components.
- **Processing:** Use mechanical presses, mixers, saws, millstones, crushing wheels, and mechanical crafters to process materials and craft items.
- **Logistics:** Move, filter, and distribute items using belts, funnels, tunnels, chutes, mechanical arms, and packagers.
- **Fluids:** Store, transport, and process fluids with pipes, mechanical pumps, tanks, hose pulleys, item drains, and spouts.
- **Contraptions:** Assemble moving structures with mechanical bearings, pistons, pulleys, minecart assemblers, and other components.
- **Controls and tools:** Manage and inspect your machines with redstone components, wireless redstone, displays, wrenches, engineer's goggles, and other tools.

Some recipes and interactions between machines are still incomplete.

### Requirements

| Component | Requirement |
| --- | --- |
| Minecraft | 1.12.2 |
| Mod loader | **Cleanroom Loader is required**; the project uses `0.6.13-alpha` as its build baseline |
| Java | Java 25; the current build uses Java 25 bytecode |
| Required mod | **Farmer's Future Delight**, mod ID: `farmers_future_delight` |
| Required upstream JAR | **Create for Minecraft 1.21.1**, installed separately in `mods/` |
| Optional mod | **JEI**, for supported machine recipes and item information |
| Optional tutorial runtime | **Ponder Legacy 1.7.1+**, installed as a separate JAR to enable Ponder scenes |

Use Cleanroom to launch the game. Standard Forge 1.12.2 cannot replace the required loader.

### Installation and Assets

1. Prepare Minecraft 1.12.2, Cleanroom Loader, and Java 25.
2. Place Cogwork Bridge, a compatible version of Farmer's Future Delight and the original Create 1.21.1 JAR in the instance's `mods` directory. Install JEI as well to browse supported recipes.
3. Launch using Cleanroom. Cogwork Bridge generates a `.meta` file beside the Create JAR and prevents its 1.21.1 mod code and Mixins from loading; it reads the archive's assets and recipe data.

Both clients and dedicated servers require the original JAR in `mods/` or `mods/1.12.2/`. The mod does not download it. If it is missing, Forge reports the required `create_1_21_1` dependency before loading Cogwork Bridge; this identifies the Create JAR for Minecraft 1.21.1. If the JAR cannot be safely isolated, startup reports the isolation error. Existing worlds made with the previous mod ID are not migrated.

Cogwork Bridge's release JAR does not include the upstream `assets/create/` directory. See the licensing section below for the copyright and terms applicable to Create's assets.

### Acknowledgements

With Ponder Legacy installed, hover a supported item and hold **W** to open its scenes. The Ponder catalogue, scene switching and replay controls are provided by Ponder Legacy. The current port includes the upstream scene catalogue; dedicated Create instructions and legacy data conversion are still being checked, so catalogue coverage does not imply full scene parity.

Thank you to the Ponder Legacy authors and contributors for providing the standalone tutorial runtime.

Thank you to [The Create Team / The Creators of Create](https://github.com/Creators-of-Create/Create) for creating and sharing Create. Cogwork Bridge references the gameplay, implementation, and recipe data of Create 1.21.1.

Thank you to [Siepert and the Create Legacy contributors](https://github.com/Siepert123/create-legacy) for sharing their 1.12.2 implementation, which provided logic and API references. Thank you to [Darkevilmac and the MovingWorld contributors](https://github.com/darkevilmac/MovingWorld) for sharing their source code; Cogwork Bridge uses and modifies parts of MovingWorld for contraptions. MovingWorld also credits code from Archimedes' Ships.

### Licenses

- **Original Cogwork Bridge contributions:** [MIT License](LICENSE).
- **Vendored MovingWorld code:** [Apache License 2.0](src/main/resources/META-INF/licenses/MovingWorld-LICENSE.txt).
- **Create reference material:** [Create 1.21.1 license overview](src/main/resources/META-INF/licenses/Create-1.21.1-LICENSE.md). Upstream code is MIT-licensed; assets under its `src/main/resources/assets/` directory are All Rights Reserved.
- **Create Legacy reference material:** [MIT License](src/main/resources/META-INF/licenses/Create-Legacy-LICENSE.txt).

See the [third-party notices](src/main/resources/META-INF/licenses/THIRD-PARTY-NOTICES.txt) for scope, attribution, and copyright details. The MIT license for Cogwork Bridge's original contributions does not replace the terms or rights of third-party material.

## 简体中文

齿轮桥接器（Cogwork Bridge）是面向 **Minecraft 1.12.2 + Cleanroom** 的 Create 附属模组：它需要 Create 1.21.1 原版 JAR 提供资源和配方数据，由本项目实现适用于 1.12.2 的机械设备。

项目目前处于 **Alpha 开发阶段**。已有多类设备可以使用，但部分功能、配方、设备联动和兼容性仍在完善中，尚未完全对齐 Create 1.21.1。

### 游戏内容

- **动力与传动**：使用手摇曲柄、水车、风车或蒸汽引擎等产生动力，通过传动杆、齿轮、齿轮箱和其他传动设备连接机器。
- **机械加工**：使用动力冲压机、搅拌器、动力锯、石磨、粉碎轮和动力合成器等处理材料、制作物品。
- **物流运输**：使用传送带、漏斗、隧道、溜槽、机械臂以及打包机等设备移动、筛选和分配物品。
- **流体处理**：通过流体管道、动力泵、储罐、软管滑轮、分液池和注液器等储存、输送或处理流体。
- **动态结构**：借助动力轴承、动力活塞、滑轮、矿车装配站等设备，让方块组成能够运动的结构。
- **控制与工具**：使用红石元件、无线红石、显示设备、扳手、工程师护目镜和其他工具控制及查看装置。

当前仍有一些配方和机器之间的联动尚未完成。

### 运行要求

| 项目 | 要求 |
| --- | --- |
| 游戏版本 | Minecraft 1.12.2 |
| 模组加载器 | **必须使用 Cleanroom Loader**；项目以 `0.6.13-alpha` 为构建基线 |
| Java | Java 25；当前构建产物使用 Java 25 字节码 |
| 必需模组 | **未世之乐（Farmer's Future Delight）**，模组 ID：`farmers_future_delight` |
| 必需原版文件 | 适用于 **Minecraft 1.21.1 的 Create 原版 JAR**，单独放入 `mods/` |
| 可选模组 | **JEI**，用于查看已接入的机器处理配方和物品信息 |
| 可选思索运行库 | **Ponder Legacy 1.7.1+**，单独安装 JAR 后启用思索场景 |

请使用 Cleanroom 启动游戏；普通 Forge 1.12.2 不能替代本项目要求的加载器。

### 安装与资源

1. 准备 Minecraft 1.12.2、Cleanroom Loader 和 Java 25。
2. 将齿轮桥接器、未世之乐的兼容版本和 Create 1.21.1 原版 JAR 一起放入游戏实例的 `mods` 目录；需要配方查询时可同时安装 JEI。
3. 使用 Cleanroom 启动。齿轮桥接器会在 Create JAR 旁生成 `.meta` 文件，阻止其 1.21.1 模组代码和 Mixin 加载，并读取其中的资源与配方数据。

客户端和专用服务器都必须将原版 JAR 放在 `mods/` 或 `mods/1.12.2/` 中，模组不会自动下载。缺少文件时，Forge 会提示缺少 `create_1_21_1` 前置，即 Minecraft 1.21.1 版 Create 的原版 JAR；无法安全隔离文件时会显示隔离错误。本次更名不迁移旧 modid 的存档。

Cogwork Bridge 的发布包不包含上游 `assets/create/` 目录。上游 Create 资源的版权和许可范围见下方说明。

### 致谢

安装 Ponder Legacy 后，将鼠标悬停在支持的物品上并长按 **W**，即可打开思索。目录、场景切换和重播使用 Ponder Legacy 提供的界面。本轮已接入上游场景目录；Create 专用演示指令与旧版数据转换仍在核对，目录覆盖不代表全部场景已完整对齐。

感谢 Ponder Legacy 的作者与贡献者提供独立的思索运行库。

感谢 [The Create Team / The Creators of Create](https://github.com/Creators-of-Create/Create) 开发并分享 Create。Cogwork Bridge 参考了 Create 1.21.1 的玩法、实现及配方数据。

感谢 [Siepert 和 Create Legacy 的贡献者](https://github.com/Siepert123/create-legacy)分享 1.12.2 版本的实现，为本项目提供逻辑与 API 参考。感谢 [Darkevilmac 和 MovingWorld 的贡献者](https://github.com/darkevilmac/MovingWorld)分享源码；本项目使用并修改了 MovingWorld 的部分代码来实现动态结构。MovingWorld 上游同时鸣谢了 Archimedes' Ships 的相关代码。

### 许可证

- **Cogwork Bridge 原创部分**：[MIT License](LICENSE)。
- **MovingWorld 内置代码**：[Apache License 2.0](src/main/resources/META-INF/licenses/MovingWorld-LICENSE.txt)。
- **Create 参考内容**：[Create 1.21.1 许可证说明](src/main/resources/META-INF/licenses/Create-1.21.1-LICENSE.md)。上游代码采用 MIT；上游 `src/main/resources/assets/` 中的资源保留所有权利。
- **Create Legacy 参考内容**：[MIT License](src/main/resources/META-INF/licenses/Create-Legacy-LICENSE.txt)。

各部分的适用范围、来源和版权说明见 [第三方许可声明](src/main/resources/META-INF/licenses/THIRD-PARTY-NOTICES.txt)。项目主体的 MIT 许可不会替代第三方内容原有的许可与权利。
