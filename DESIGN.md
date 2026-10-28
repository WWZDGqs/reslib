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
