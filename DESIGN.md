# 注释

Fabric 1.21.11 客户端模组。把光影包、光影配置、钠配置与材质包组合存成一条命名记录，之后一键导入、切换、删除。

---

## 1. 结构

```
resourcepackslib-1.21.11/
├── build.gradle                     Loom 1.14 + Kotlin JVM 插件
├── gradle.properties                版本号集中管理
├── libs/maven                       本地依赖仓库
└── src/
    ├── main/resources/
    │   ├── fabric.mod.json          环境 client，只有 client 入口点
    │   └── assets/resourcepackslib/icon.png
    └── client/
        ├── kotlin/ws/reslib/
        │   ├── ResourcepacksLibClient.kt    入口：加载数据、注册按键与命令
        │   ├── preset/
        │   │   ├── PresetRecord.kt          数据类型（记录 / 文件根 / 快照 / 应用结果）
        │   │   ├── PresetStore.kt           JSON 读写、原子替换、损坏备份
        │   │   ├── PresetManager.kt         业务门面（增删改查 + 应用 + 落盘）
        │   │   └── PresetApplier.kt         采集当前状态 / 应用材质包与光影
        │   ├── compat/IrisBridge.kt         Iris 反射桥（光影包 + 包内配置）
        │   ├── compat/SodiumBridge.kt       Sodium 反射桥（界面入口 + 视频配置）
        │   ├── ui/PresetScreen.kt           管理界面（保存 / 导入 / 删除 / 翻页 / 快捷入口）
        │   ├── command/PresetCommand.kt     /reslib 客户端命令
        │   └── util/Notify.kt               提示文案组装
        └── resources/assets/resourcepackslib/lang/
            ├── zh_cn.json
            └── en_us.json
```

数据`PresetRecord` `PresetFile` 

存储`PresetStore` 

业务`PresetManager` 

适配`PresetApplier` `IrisBridge` `SodiumBridge` 

交互`PresetScreen` `PresetCommand` 界面与命令，只调 `PresetManager`

---

## 2. 依赖与版本兼容

### 2.1 编译期依赖

Minecraft：1.21.11

Yarn mappings：1.21.11+build.6

Fabric Loader：0.20.1

Fabric API：0.141.6+1.21.11，只用到 command-api-v2、key-binding-api-v1、lifecycle-events-v1

Fabric Language Kotlin：1.14.2+kotlin.2.4.21

Kotlin：2.4.21，jvmTarget 21

Fabric Loom：1.14-SNAPSHOT，fabric-loom-remap

JDK：21

### 2.2 可选依赖：Iris

iris 不进入 build.gradle，只在 fabric.mod.json 里 suggests。

iris 目标链：

```
net.irisshaders.iris.Iris#getIrisConfig()          → IrisConfig 实例
IrisConfig#setShaderPackName(String|null)          → 设置/清空光影包
IrisConfig#setShadersEnabled(boolean)              → 开关光影
IrisConfig#save()                                  → 写 config/iris.properties
Iris#reload()                                      → 立即重载生效
Iris#getCurrentPackName()                          → 采集当前光影名
Iris#getShaderpacksDirectory()                     → 校验包是否存在
```

每一次调用都包在 `runCatching` 里。任意一步失败即降级：直接改写 `config/iris.properties` 的 `shaderPack` / `enableShaders` 两个键，值在下一次启动生效，界面提示「需重启生效」。

#### 光影包内部配置

光影包自己的画质选项（阴影分辨率、体积光强度之类）Iris 存在 `shaderpacks/<包名>.txt`，格式是 Java Properties。API v0 没有任何读取/写入这些选项的入口，所以直接操作这个文件。

采集：读 `Iris#getShaderpacksDirectory()` 下的 `<包名>.txt`，`Properties.load` 后整表取回

还原：应用记录时先把整表写回同名 `.txt`，再调 `Iris#reload()`，Iris 加载光影时会读这个文件

缺省：文件不存在视为没改过画质，记录里就是空表，应用时跳过写入，不覆盖现有设置

包名就是 `shaderpacks/` 下的目录名或 zip 文件名（如 `ComplementaryReimagined_r5.5.1.zip`），对应配置文件是 `ComplementaryReimagined_r5.5.1.zip.txt`。

#### Iris 界面入口

`IrisApi#openMainIrisScreenObj(Object parent)` 返回 Iris 的光影选择界面，反射调用后 `client.setScreen(...)`。玩家不用退出管理界面再去按键开 Iris。

### 2.3 可选依赖：Sodium

Sodium 同样不进编译期依赖，`fabric.mod.json` 加 `suggests`，运行时用 `FabricLoader#isModLoaded("sodium")` 判断。

装了 Sodium 后原版视频设置界面被替换，所以面板上的「视频」按钮按版本降级尝试三种入口：

```
net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen#createScreen(Screen)   0.8.x（1.21.11 当前）
  ↓ 失败
net.caffeinemc.mods.sodium.client.gui.SodiumVideoOptionsScreen(Screen)           0.6.x
  ↓ 失败
me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI(Screen)                    0.5.x
  ↓ 失败
原版 VideoOptionsScreen(parent, client, client.options)
```

Sodium 用 Mojang 官方映射编译，与 yarn 命名在运行时是同一个类，反射传 `Screen` 实例即可。未装 Sodium 时按钮退回原版视频设置，「光影」按钮只在检测到 Iris 时显示。

#### Sodium 配置

Sodium 0.8 的选项落在 `config/sodium-options.json`，Gson 序列化，字段名小写下划线、忽略 private 字段：

```json
{
  "quality": { "hidden_fluid_culling": true, "pixel_filtering_mode": "NEAREST" },
  "performance": { "chunk_builder_threads": 0, "use_entity_culling": true },
  "advanced": { "cpu_render_ahead_limit": 3 },
  "debug": { "terrain_sorting_enabled": true },
  "notifications": { "has_seen_donation_prompt": false }
}
```

采集：整个文件读成 `JsonObject`，不建映射类，Sodium 每个小版本都在加字段，整表保存才不会漏

还原：先读当时文件做比较，内容相同直接跳过，不同则写 `sodium-options.json.tmp` 再替换

生效：只能重启，Sodium 运行时状态在 `Config` 的 Option 绑定里，改文件不刷新内存，强行反射替换 `OPTIONS` 会让绑定指向旧实例，不做伪 live 应用

记录里 `sodium_options` 为 `null` 表示保存时没装 Sodium，应用时直接跳过。`notifications` 这类标记也一并还原，属于整份配置快照的取舍。

### 2.4 版本敏感点

`GameOptions.resourcePacks`：1.21.11 仍是 `List<String>`，未改成 `SimpleOption`，直接读写字段

材质包应用：`ResourcePackManager#setEnabledProfiles` + `GameOptions#refreshResourcePacks` + `MinecraftClient#reloadResources`，复刻原版 `PackScreen` 的落盘路径

资源包 id：`vanilla`、`fabric`、`file/xxx.zip` 混排，随加载器与模组变化，应用前按 `manager.profiles` 过滤，缺失项跳过并提示，不整条失败

`KeyBinding.Category`：1.21.9 起由字符串改为对象，用 `KeyBinding.Category.create(Identifier)` 注册

GUI 渲染：`DrawContext` 传参风格，只依赖 `drawTextWithShadow` 等基础接口

全程不使用 Mixin，没有 `@Redirect` / `@Inject`，升级 1.21.x 时改动面只有上面几处。

### 2.5 本地依赖仓库

`maven.fabricmc.net` 在本机经常超时，三个 mod 依赖改成手动下载后内嵌进工程：

```
libs/maven/net/fabricmc/
├── fabric-loader/0.20.1/                        jar + pom（packaging jar）
├── fabric-api/fabric-api/0.141.6+1.21.11/       jar + pom（含 42 个子模块依赖）
├── fabric-api/<module>/<version>/               42 个子模块 jar + pom + module
└── fabric-language-kotlin/1.14.2+kotlin.2.4.21/ jar + pom（无外部依赖）
```

`build.gradle` 用 `exclusiveContent` 把 `net.fabricmc.fabric-api` 整组与 loader、FLK 两个坐标锁死到 `libs/maven`：

```gradle
repositories {
    exclusiveContent {
        forRepository { maven { name = "LocalBundled"; url = uri("libs/maven") } }
        filter {
            includeGroup("net.fabricmc.fabric-api")
            includeModule("net.fabricmc", "fabric-loader")
            includeModule("net.fabricmc", "fabric-language-kotlin")
        }
    }
}
```

普通 `maven {}` 排在前面无效：Gradle 已把这两个模块的元数据缓存在 Fabric 仓库名下，`--offline` 仍报 No cached version available，必须用 `exclusiveContent`

`fabric-api-deprecated` 未缓存：聚合 pom 依赖它，但该模块是空 stub，不在 fabric-api 的 44 个内嵌 jar 里，本地 pom 删掉这一条

FLK 的 module 元数据声明 kotlin-stdlib 依赖：会连带请求一串 Kotlin 构件，本地 pom 只写 `packaging jar`，Kotlin stdlib 由 Kotlin Gradle 插件自带

来源与校验：`fabric-loader` 走 BMCLAPI 镜像（bmclapi2.bangbang93.com/maven），`fabric-api` 与 FLK 走 Modrinth CDN，sha512 与 Modrinth 元数据一致（852d3682… / 7fb475c4…）。

Loom 自身仍需要 Minecraft 本体 jar 与 yarn 映射，那部分走 `~/.gradle/caches/fabric-loom` 的缓存，换机器首次构建依旧要能访问 `maven.fabricmc.net`。

---

## 3. 持久化方案

### 3.1 位置


<游戏目录>/config/resourcepackslib/presets.json


FabricLoader.getInstance().configDir 解析，跟随实例目录，与 options.txt、iris.properties 同级。

### 3.2 

```json
{
  "version": 3,
  "last_applied_id": "6f1a...c2",
  "presets": [
    {
      "id": "6f1a...c2",
      "name": "香草生存",
      "shader_pack": "ComplementaryReimagined_r5.5.1.zip",
      "shader_options": {
        "shadowDistance": "96.0",
        "volumetricLight": "true",
        "sunPathRotation": "-35.0"
      },
      "sodium_options": {
        "quality": { "hidden_fluid_culling": true, "pixel_filtering_mode": "NEAREST" },
        "performance": { "use_entity_culling": true, "chunk_builder_threads": 0 }
      },
      "resource_packs": ["vanilla", "file/Faithful32.zip", "file/FreshAnimations.zip"],
      "incompatible_resource_packs": [],
      "created_at": 1761446400000,
      "updated_at": 1761446400000
    }
  ]
}
```

`version`：int，格式版本，当前 3，旧文件缺的字段按空值处理

`last_applied_id`：string 或 null，最近一次导入的记录，界面打 ★

`id`：UUID，删除/重命名/应用的主键，与名称解耦

`name`：显示名，不超过 40 字符，不区分大小写唯一

`shader_pack`：`shaderpacks/` 下的目录名或 zip 文件名，null 表示关闭光影

`shader_options`：该光影包 `shaderpacks/<包名>.txt` 的完整键值，原样保存

`sodium_options`：`config/sodium-options.json` 的完整内容，null 表示保存时没装 Sodium

`resource_packs`：原样保存 `options.resourcePacks` 的顺序，末位优先级最高

`incompatible_resource_packs`：采集时的不兼容列表，仅作回显，应用时由原版重算

`created_at` / `updated_at`：毫秒时间戳

光影只存包名不存绝对路径，光影配置只存键值不存文件，Sodium 配置整份内嵌，材质包只存 id 不存文件路径，换机器、换实例目录、改 zip 位置都不影响记录本身。

### 3.3 写入策略

先写 `presets.json.tmp`，再 `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`，文件系统不支持原子移动时退回普通覆盖

读取失败（JSON 损坏、字段类型错）时把原文件改名为 `presets.broken-<时间戳>.json`，从空数据启动，不静默丢档

每次保存、应用、删除、重命名后立即落盘，没有延迟写，没有退出钩子

手写 JSON 解析（`JsonObject` 逐字段取），不依赖反射式反序列化，缺字段一律走默认值，方便日后加字段

---

## 4. 核心逻辑与接口

### 4.1 快照采集

```kotlin
fun capture(): Snapshot {
    val client = MinecraftClient.getInstance()
    val shaderPack = IrisBridge.currentPack()
    return Snapshot(
        shaderPack = shaderPack,
        shaderOptions = IrisBridge.readOptions(shaderPack),
        sodiumOptions = SodiumBridge.readConfig(),
        resourcePacks = client.options.resourcePacks.toList(),
        incompatibleResourcePacks = client.options.incompatibleResourcePacks.toList(),
    )
}
```

保存的就是此刻游戏里正在用的东西，不猜、不做名字映射。光影配置读 `<包名>.txt` 的整张表，Sodium 配置读整份 `sodium-options.json`，两边界面调过的每一项都会进记录。

### 4.2 保存 / 覆盖

```kotlin
PresetManager.saveCurrent(name): Result<PresetRecord>
```

`trim()` 后校验：非空、不超过 40 字符、总数不超过 64

按名称（忽略大小写）查重：命中则覆盖原记录，保留 `id` 与 `created_at`，只更新内容与 `updated_at`；未命中则新建 UUID

写入后把该记录设为 `last_applied_id`，刚存的组合就是当前状态

返回 `Result`，失败原因用固定字符串（empty name / name too long / preset limit reached / duplicate name）映射到 lang 键

### 4.3 导入（应用）

```kotlin
PresetManager.apply(record): Result<ApplyReport>
```

```
scanPacks()                                  刷新 manager.profiles
  ↓
filter { it in manager.profiles }            剔除失效 id，记录 missing
  ↓
enabledIds == wanted ?                       顺序完全一致则跳过材质重载
  ↓ 否
setEnabledProfiles(wanted)
options.refreshResourcePacks(manager)        同步 options.resourcePacks / incompatible
options.write()                              写 options.txt
reloadResources()                            资源重载
  ↓
writeOptions(pack, shader_options)           光影包存在时写回 shaderpacks/<包名>.txt
  ↓
IrisBridge.apply(pack)                       设置光影并重载，此时读到刚写入的配置
  ↓
SodiumBridge.writeConfig(sodium_options)     内容有差异才写 config/sodium-options.json
  ↓
last_applied_id = record.id，落盘
```

顺序与作用域和原版 `PackScreen` 点击「完成」时一致，不绕开原版状态机，避免 `options.txt` 与内存状态打架。光影配置必须写在 `Iris#reload()` 之前，否则 Iris 已经按旧值编译完着色器。

`ApplyReport` 返回 `enabledPacks`、`missingPacks`、`shader`、`shaderOptions`（实际写回的配置项数）、`sodium`，界面据此拼一句话提示：

```
已导入「香草生存」: 材质包 3 个, 光影 ComplementaryReimagined_r5.5.1.zip, 光影配置 27 项 (1 个材质包缺失) (钠配置已写入, 重启后生效)
```

### 4.4 切换

「切换」等于对另一条记录执行导入，没有独立状态机。`last_applied_id` 是唯一的活动标记，用于界面 ★ 与命令列表前缀，不参与逻辑判断，记录与游戏当前状态允许不一致（玩家可能手动改过材质）。

### 4.5 删除

```kotlin
PresetManager.deleteById(id): Boolean
PresetManager.delete(name): Boolean
```

按 id 删除，若删的是 `last_applied_id` 一并清空该字段。仅删记录，不回滚游戏当前的光影与材质。

### 4.6 重命名

```kotlin
PresetManager.rename(id, newName): Result<Unit>
```

与非自身记录比对名称唯一性，成功后刷新 `updated_at`。

### 4.7 关键接口

`PresetManager.init()` 客户端初始化时载入 JSON

`PresetManager.presets()` 只读副本，按插入顺序

`PresetManager.find(name)` 名称忽略大小写

`PresetManager.findById(id)` 按 id 查

`PresetManager.saveCurrent(name)` 保存或覆盖

`PresetManager.apply(record)` / `apply(name)` 导入

`PresetManager.rename(id, newName)` 重命名

`PresetManager.deleteById(id)` / `delete(name)` 删除

`PresetStore.load()` / `save(file)` 纯 IO

`PresetApplier.capture()` / `apply(record)` 对接原版

`IrisBridge.available` / `currentPack()` / `apply(pack)` 反射桥，失败降级

`IrisBridge.readOptions(pack)` / `writeOptions(pack, map)` 光影包内配置读写

`IrisBridge.openShaderScreen(parent)` 打开 Iris 光影界面

`SodiumBridge.available` / `openVideoSettings(parent)` 视频设置入口，失败返回 false

`SodiumBridge.readConfig()` / `writeConfig(json)` 整份 sodium-options.json 读写

线程模型：所有入口都在客户端主线程调用（按键 tick、命令、界面点击），`PresetManager` 内部用 `synchronized` 保护数据，文件读写同步执行，单文件几十 KB，开销可忽略，换来没有并发写档风险。

---

## 5. 交互入口

`]` 打开管理界面

`/reslib open` 同上

界面「保存当前」 用输入框名称保存或覆盖

界面「光影」 直接打开 Iris 光影选择界面，仅装了 Iris 时显示

界面「视频」 打开 Sodium 视频设置，未装 Sodium 则退回原版

界面每行「导入」/「删除」 单条操作

`/reslib save <名称>` 保存当前配置

`/reslib apply <名称>` 导入

`/reslib list` 列出全部，★ 标记最近导入

`/reslib delete <名称>` 删除

`/reslib rename <旧名> <新名>` 重命名，含空格需加引号

界面布局：顶部标题与「当前光影 / 记录数」，第二行名称输入框 + 保存 + 光影 + 视频，中部每页 6 条记录（序号、名称、光影名、材质包数量、光影配置项数、钠标记 + 导入/删除按钮），底部翻页与「完成」。

---

## 6. 已知限制

Iris 内部结构不是稳定 API，升级 Iris 后需要回归一次导入流程，失败时至少会降级到写 `iris.properties`，不会崩

导入光影会触发 Iris 重编译着色器，有可感知的卡顿，与手动在 Iris 界面切换等价

光影配置以整表覆盖 `shaderpacks/<包名>.txt` 的方式还原，记录里是空表时跳过写入，因此同一光影包只换材质包的记录不会把画质设置清空

只记录光影包内配置，不记录 Iris 的全局项（阴影距离上限、色彩空间等，仍在 `config/iris.properties`）

Sodium 界面靠反射找类，Sodium 大版本改动类名时会退化到原版视频设置，功能不丢但按钮语义变化

Sodium 配置只能落盘后重启生效，运行时读的是配置对象里的绑定值，改文件不刷新内存，硬替换 `OPTIONS` 会让绑定指向旧实例，风险大于收益

Sodium 配置整份还原，包含 `notifications` 里的看过捐赠提示这类标记

记录数量上限 64，名称上限 40 字符

---

English version

ResourcepacksLib design notes

Client-side Fabric mod for Minecraft 1.21.11. It saves a shader pack, that pack's options, the Sodium video options and the enabled resource pack list as one named preset, then applies, switches or deletes it in a single action.


1. Structure

resourcepackslib-1.21.11/
  build.gradle                     Loom 1.14 + Kotlin JVM plugin
  gradle.properties                version properties
  libs/maven                       local dependency repository
  src/
    main/resources/
      fabric.mod.json              client environment, single client entrypoint
      assets/resourcepackslib/icon.png
    client/
      kotlin/ws/reslib/
        ResourcepacksLibClient.kt  entry: load data, register keybind and commands
        preset/
          PresetRecord.kt          data types (record / file root / snapshot / report)
          PresetStore.kt           JSON IO, atomic replace, broken file backup
          PresetManager.kt         business facade (CRUD + apply + persist)
          PresetApplier.kt         capture current state, apply packs and shaders
        compat/IrisBridge.kt       Iris reflection bridge (pack + pack options)
        compat/SodiumBridge.kt     Sodium reflection bridge (screen entry + video options)
        ui/PresetScreen.kt         management screen
        command/PresetCommand.kt   /reslib client commands
        util/Notify.kt             message helpers
      resources/assets/resourcepackslib/lang/
        zh_cn.json
        en_us.json

Data: PresetRecord, PresetFile

Storage: PresetStore

Business: PresetManager

Adapters: PresetApplier, IrisBridge, SodiumBridge

Interaction: PresetScreen, PresetCommand, both call PresetManager only


2. Dependencies and version compatibility

2.1 Build time dependencies

Minecraft 1.21.11

Yarn mappings 1.21.11+build.6

Fabric Loader 0.20.1

Fabric API 0.141.6+1.21.11, only command-api-v2, key-binding-api-v1 and lifecycle-events-v1 are used

Fabric Language Kotlin 1.14.2+kotlin.2.4.21

Kotlin 2.4.21, jvmTarget 21

Fabric Loom 1.14-SNAPSHOT, fabric-loom-remap

JDK 21


2.2 Optional dependency: Iris

Iris is not in build.gradle, it is only listed under suggests in fabric.mod.json.

Iris target chain:

net.irisshaders.iris.Iris#getIrisConfig()          returns IrisConfig
IrisConfig#setShaderPackName(String|null)          set or clear the shader pack
IrisConfig#setShadersEnabled(boolean)              enable or disable shaders
IrisConfig#save()                                  write config/iris.properties
Iris#reload()                                      reload immediately
Iris#getCurrentPackName()                          read the current pack name
Iris#getShaderpacksDirectory()                     validate that a pack exists

Every call is wrapped in runCatching. If any step fails the mod degrades: it rewrites the shaderPack and enableShaders keys in config/iris.properties, the change takes effect on the next launch and the screen reports that a restart is required.


Shader pack options

A pack's own quality options are stored by Iris in shaderpacks/<pack>.txt as Java Properties. API v0 has no accessor for them, so the file is read and written directly.

Capture: read <pack>.txt under Iris#getShaderpacksDirectory(), load it with Properties and keep the whole table

Restore: write the whole table back to the same file, then call Iris#reload(); Iris reads that file while loading the pack

Default: a missing file means no options were ever changed, the record holds an empty map and the write is skipped on apply, so existing settings are not overwritten

The pack name is the folder or zip name under shaderpacks/, for example ComplementaryReimagined_r5.5.1.zip, and its option file is ComplementaryReimagined_r5.5.1.zip.txt.


Iris screen entry

IrisApi#openMainIrisScreenObj(Object parent) returns the Iris shader selection screen. It is called reflectively and handed to client.setScreen(...), so the player does not have to leave the management screen and press the Iris keybind.


2.3 Optional dependency: Sodium

Sodium is not a build time dependency either. It is listed under suggests in fabric.mod.json and detected at runtime with FabricLoader#isModLoaded("sodium").

Sodium replaces the vanilla video settings screen, so the Video button tries three entries in order:

net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen#createScreen(Screen)   0.8.x, current for 1.21.11
  fallback
net.caffeinemc.mods.sodium.client.gui.SodiumVideoOptionsScreen(Screen)           0.6.x
  fallback
me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI(Screen)                    0.5.x
  fallback
vanilla VideoOptionsScreen(parent, client, client.options)

Sodium is compiled with official Mojang mappings, which is the same class at runtime as the yarn names, so a Screen instance can be passed directly. Without Sodium the button falls back to the vanilla video settings screen, and the Shaders button is only shown when Iris is detected.


Sodium options

Sodium 0.8 stores its options in config/sodium-options.json, serialised with Gson, field names in lower case with underscores, private fields excluded.

Capture: the whole file is parsed into a JsonObject, no mapping class is generated, because Sodium adds fields in almost every minor release and saving the whole table avoids missing any

Restore: the current file is read first and compared, identical content is skipped, otherwise the new content is written to sodium-options.json.tmp and moved over the original

Apply: restart only. Sodium keeps its runtime state in the Option bindings of Config, writing the file does not refresh memory, and force-replacing OPTIONS reflectively would leave the bindings pointing at the old instance, so no fake live apply is attempted

When sodium_options is null in a record, Sodium was not installed when the preset was saved and the step is skipped on apply. Markers such as the ones under notifications are restored as well, which is the trade-off of taking a full config snapshot.


2.4 Version sensitive points

GameOptions.resourcePacks: still a List<String> in 1.21.11, not yet a SimpleOption, so the field is read and written directly

Resource pack apply: ResourcePackManager#setEnabledProfiles plus GameOptions#refreshResourcePacks plus MinecraftClient#reloadResources, mirroring what the vanilla PackScreen does when Done is pressed

Resource pack ids: vanilla, fabric and file/xxx.zip are mixed, and the set changes with loader and mods, so entries are filtered against manager.profiles before applying, missing entries are skipped and reported instead of failing the whole preset

KeyBinding.Category: changed from a string to an object in 1.21.9, registered with KeyBinding.Category.create(Identifier)

GUI rendering: DrawContext parameter style, only basic calls such as drawTextWithShadow are used

The mod uses no Mixin at all, no @Redirect and no @Inject, so a 1.21.x upgrade only touches the points listed above.


2.5 Local dependency repository

maven.fabricmc.net frequently times out on this machine, so the three mod dependencies were downloaded manually and embedded in the project:

libs/maven/net/fabricmc/
  fabric-loader/0.20.1/                        jar + pom, packaging jar
  fabric-api/fabric-api/0.141.6+1.21.11/       jar + pom, with 42 submodule dependencies
  fabric-api/<module>/<version>/               42 submodule jars, poms and modules
  fabric-language-kotlin/1.14.2+kotlin.2.4.21/ jar + pom, no external dependencies

build.gradle pins the whole net.fabricmc.fabric-api group plus fabric-loader and fabric-language-kotlin to libs/maven with exclusiveContent:

repositories {
    exclusiveContent {
        forRepository { maven { name = "LocalBundled"; url = uri("libs/maven") } }
        filter {
            includeGroup("net.fabricmc.fabric-api")
            includeModule("net.fabricmc", "fabric-loader")
            includeModule("net.fabricmc", "fabric-language-kotlin")
        }
    }
}

A plain maven {} block placed first does not work: Gradle has already cached the metadata of these two modules under the Fabric repository, so even --offline reports No cached version available. exclusiveContent is required.

fabric-api-deprecated is not cached: the aggregator pom depends on it, but that module is an empty stub and is not among the 44 jars nested inside fabric-api, so the dependency is removed from the local pom.

The FLK module metadata declares a kotlin-stdlib dependency, which would pull a chain of Kotlin artifacts, so the local pom only declares packaging jar and Kotlin stdlib comes from the Kotlin Gradle plugin itself.

Source and verification: fabric-loader came from the BMCLAPI mirror (bmclapi2.bangbang93.com/maven), fabric-api and FLK came from the Modrinth CDN, and both sha512 values match the Modrinth metadata (852d3682... and 7fb475c4...).

Loom still needs the Minecraft jar and the yarn mappings, which come from the ~/.gradle/caches/fabric-loom cache. On a new machine the first build still has to reach maven.fabricmc.net.


3. Storage

3.1 Location

<game directory>/config/resourcepackslib/presets.json

Resolved through FabricLoader.getInstance().configDir, so it follows the instance directory and sits next to options.txt and iris.properties.

3.2 Format

version: int, format version, currently 3, missing fields in older files are treated as empty

last_applied_id: string or null, the record applied last, marked with a star in the screen

id: UUID, primary key for delete, rename and apply, independent of the name

name: display name, at most 40 characters, unique ignoring case

shader_pack: folder or zip name under shaderpacks/, null disables shaders

shader_options: the complete key and value table of shaderpacks/<pack>.txt, stored verbatim

sodium_options: the complete content of config/sodium-options.json, null when Sodium was not installed at save time

resource_packs: the order of options.resourcePacks, stored verbatim, the last entry has the highest priority

incompatible_resource_packs: the incompatible list captured at save time, display only, recomputed by vanilla on apply

created_at and updated_at: millisecond timestamps

Only the pack name is stored, never an absolute path; shader options are stored as key and value pairs, not as a file; the Sodium config is embedded as a whole; resource packs are stored as ids only. Moving to another machine, another instance directory or relocating a zip does not affect the records.

3.3 Write strategy

Write presets.json.tmp first, then Files.move(... ATOMIC_MOVE, REPLACE_EXISTING); when the file system does not support atomic moves, fall back to a plain overwrite

On a read failure (broken JSON, wrong field types) rename the original to presets.broken-<timestamp>.json and start from empty data instead of silently losing the file

Persist immediately after every save, apply, delete and rename, no delayed writes and no shutdown hook

JSON is parsed by hand (JsonObject field by field) instead of reflective deserialisation, missing fields fall back to defaults, which keeps adding fields cheap


4. Core logic and interfaces

4.1 Snapshot capture

fun capture(): Snapshot {
    val client = MinecraftClient.getInstance()
    val shaderPack = IrisBridge.currentPack()
    return Snapshot(
        shaderPack = shaderPack,
        shaderOptions = IrisBridge.readOptions(shaderPack),
        sodiumOptions = SodiumBridge.readConfig(),
        resourcePacks = client.options.resourcePacks.toList(),
        incompatibleResourcePacks = client.options.incompatibleResourcePacks.toList(),
    )
}

What is saved is exactly what the game is using right now, no guessing and no name mapping. Shader options are the whole table from <pack>.txt, Sodium options are the whole sodium-options.json, so every value changed in either screen ends up in the record.

4.2 Save and overwrite

PresetManager.saveCurrent(name): Result<PresetRecord>

Validate after trim(): non empty, at most 40 characters, at most 64 presets in total

Look up by name ignoring case: a hit overwrites the existing record, keeping id and created_at and updating content and updated_at; a miss creates a new UUID

After writing, the record becomes last_applied_id, since the combination just saved is the current state

Returns Result, failure reasons use fixed strings (empty name, name too long, preset limit reached, duplicate name) mapped to lang keys

4.3 Apply

PresetManager.apply(record): Result<ApplyReport>

scanPacks()                                   refresh manager.profiles
  next
filter { it in manager.profiles }             drop dead ids, collect missing entries
  next
enabledIds == wanted ?                        skip the pack reload when the order matches
  if not
setEnabledProfiles(wanted)
options.refreshResourcePacks(manager)         sync options.resourcePacks and incompatible
options.write()                               write options.txt
reloadResources()                             reload resources
  next
writeOptions(pack, shader_options)            write shaderpacks/<pack>.txt when the pack exists
  next
IrisBridge.apply(pack)                        set the shader and reload, reads the file just written
  next
SodiumBridge.writeConfig(sodium_options)      write config/sodium-options.json only when it differs
  next
last_applied_id = record.id, persist

The order and scope match what the vanilla PackScreen does on Done; the vanilla state machine is not bypassed, so options.txt and the in memory state cannot disagree. Shader options must be written before Iris#reload(), otherwise Iris has already compiled the shaders with the old values.

ApplyReport returns enabledPacks, missingPacks, shader, shaderOptions (number of options actually written) and sodium, and the screen builds one line from it:

Applied "Vanilla survival": 3 pack(s), shader ComplementaryReimagined_r5.5.1.zip, 27 option(s) (1 pack missing) (Sodium config saved, restart to apply)

4.4 Switch

Switching means applying another record, there is no separate state machine. last_applied_id is only a marker for the star in the screen and the prefix in the command list, it does not drive logic, and the records are allowed to disagree with the current game state, since the player may have changed packs by hand.

4.5 Delete

PresetManager.deleteById(id): Boolean
PresetManager.delete(name): Boolean

Deletes by id and clears last_applied_id when that record was the last applied one. Only the record is removed, the current shaders and packs in game are left untouched.

4.6 Rename

PresetManager.rename(id, newName): Result<Unit>

Checks name uniqueness against all other records and refreshes updated_at on success.

4.7 Key interfaces

PresetManager.init() loads the JSON on client initialisation

PresetManager.presets() read only copy in insertion order

PresetManager.find(name) name lookup ignoring case

PresetManager.findById(id) lookup by id

PresetManager.saveCurrent(name) save or overwrite

PresetManager.apply(record) and apply(name) apply a record

PresetManager.rename(id, newName) rename

PresetManager.deleteById(id) and delete(name) delete

PresetStore.load() and save(file) pure IO

PresetApplier.capture() and apply(record) vanilla integration

IrisBridge.available, currentPack() and apply(pack) reflection bridge with fallback

IrisBridge.readOptions(pack) and writeOptions(pack, map) shader pack option IO

IrisBridge.openShaderScreen(parent) open the Iris shader screen

SodiumBridge.available and openVideoSettings(parent) video settings entry, returns false on failure

SodiumBridge.readConfig() and writeConfig(json) whole sodium-options.json IO

Thread model: every entry runs on the client thread (keybind tick, command, screen click), PresetManager guards its data with synchronized and file IO is synchronous. The file is a few dozen KB, so the cost is negligible and there is no risk of concurrent writes.


5. Entry points

] opens the management screen

/reslib open same as above

Screen button Save current saves or overwrites using the name in the text field

Screen button Shaders opens the Iris shader selection screen, shown only when Iris is installed

Screen button Video opens the Sodium video settings, or vanilla when Sodium is absent

Row buttons Apply and Delete operate on a single record

/reslib save <name> save the current setup

/reslib apply <name> apply a record

/reslib list list everything, the last applied record is marked with a star

/reslib delete <name> delete

/reslib rename <old> <new> rename, quote names that contain spaces

Screen layout: title and current shader / record count at the top, a name field plus Save, Shaders and Video on the second row, six records per page in the middle (index, name, shader name, pack count, shader option count, Sodium marker, Apply and Delete buttons), paging and Done at the bottom.


6. Known limits

The Iris internals are not a stable API, so the apply flow has to be re-tested after an Iris upgrade; on failure it still degrades to writing iris.properties instead of crashing

Applying a shader makes Iris recompile its shaders, which is a noticeable hitch, the same as switching in the Iris screen by hand

Shader options are restored by overwriting the whole shaderpacks/<pack>.txt table; records with an empty table skip the write, so a record that only changes packs for the same shader does not wipe the quality settings

Only per pack options are stored, Iris global settings (shadow distance cap, colour space and so on) stay in config/iris.properties

The Sodium screen is found by reflection, a Sodium major release that renames the class degrades to the vanilla video settings screen, nothing is lost except the button meaning

Sodium options only take effect after a restart, the runtime reads the bindings inside the config object, writing the file does not refresh memory and force-replacing OPTIONS would leave the bindings on the old instance, which is worse than the limitation

The Sodium config is restored as a whole, including markers such as the seen donation prompt under notifications

At most 64 records, names up to 40 characters
