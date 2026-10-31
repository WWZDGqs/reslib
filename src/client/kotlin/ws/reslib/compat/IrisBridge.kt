package ws.reslib.compat

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen
import org.slf4j.LoggerFactory
import ws.reslib.preset.ShaderResult
import ws.reslib.preset.ShaderStatus
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

object IrisBridge {
    private const val IRIS_CLASS = "net.irisshaders.iris.Iris"
    private const val IRIS_API_CLASS = "net.irisshaders.iris.api.v0.IrisApi"
    private val logger = LoggerFactory.getLogger("resourcepackslib")
    private val irisClass: Class<*>? by lazy { runCatching { Class.forName(IRIS_CLASS) }.getOrNull() }
    private val currentPackMethod by lazy {
        runCatching { irisClass?.getMethod("getCurrentPackName") }.getOrNull()
    }

    val available: Boolean
        get() = irisClass != null

    fun currentPack(): String? {
        val method = currentPackMethod ?: return null
        return runCatching { method.invoke(null) as String? }.getOrNull()
    }

    fun packExists(name: String): Boolean {
        val directory = shaderpacksDirectory() ?: return true
        return Files.exists(directory.resolve(name))
    }

    fun readOptions(pack: String?): Map<String, String> {
        val path = pack?.takeIf { it.isNotEmpty() }?.let(::optionsFile) ?: return emptyMap()
        if (!Files.isRegularFile(path)) {
            return emptyMap()
        }
        return runCatching {
            val properties = Properties()
            Files.newInputStream(path).use { properties.load(it) }
            properties.stringPropertyNames().associateWith { properties.getProperty(it) }
        }.getOrDefault(emptyMap())
    }

    fun writeOptions(pack: String?, options: Map<String, String>): Boolean {
        return !(pack.isNullOrEmpty() || options.isEmpty()) && runCatching {
            val path = optionsFile(pack) ?: return false
            Files.createDirectories(path.parent)
            val properties = Properties()
            options.forEach { (key, value) -> properties.setProperty(key, value) }
            Files.newOutputStream(path).use {
                properties.store(it, "Shader options restored by resourcepackslib")
            }
            true
        }.getOrDefault(false)
    }

    fun openShaderScreen(parent: Screen?): Boolean = runCatching {
        val api = Class.forName(IRIS_API_CLASS).getMethod("getInstance").invoke(null) ?: return false
        val screen = api.javaClass.getMethod("openMainIrisScreenObj", Any::class.java).invoke(api, parent) as? Screen
        screen?.let { MinecraftClient.getInstance().setScreen(it) } != null
    }.getOrDefault(false)

    fun apply(pack: String?): ShaderResult {
        val clazz = irisClass ?: return ShaderResult(ShaderStatus.IRIS_MISSING, pack)
        if (pack != null && !packExists(pack)) {
            return ShaderResult(ShaderStatus.PACK_MISSING, pack)
        }

        return runCatching {
            val config = clazz.getMethod("getIrisConfig").invoke(null) ?: error("Iris config unavailable")
            val configClass = config.javaClass
            configClass.getMethod("setShaderPackName", String::class.java).invoke(config, pack)
            configClass.getMethod("setShadersEnabled", java.lang.Boolean.TYPE).invoke(config, pack != null)
            configClass.getMethod("save").invoke(config)
            clazz.getMethod("reload").invoke(null)
            ShaderResult(if (pack == null) ShaderStatus.DISABLED else ShaderStatus.APPLIED, pack)
        }.getOrElse { throwable ->
            val cause = (throwable as? java.lang.reflect.InvocationTargetException)?.targetException ?: throwable
            logger.warn("Iris bridge failed, writing iris.properties instead", cause)
            val written = writeProperties(pack)
            ShaderResult(
                if (written) ShaderStatus.NEEDS_RESTART else ShaderStatus.FAILED,
                pack,
                cause.message,
            )
        }
    }

    private fun optionsFile(pack: String): Path? {
        val directory = shaderpacksDirectory() ?: return null
        return directory.resolve("$pack.txt")
    }

    private fun shaderpacksDirectory(): Path? {
        val clazz = irisClass ?: return null
        return runCatching { clazz.getMethod("getShaderpacksDirectory").invoke(null) as Path? }.getOrNull()
    }

    private fun writeProperties(pack: String?): Boolean = runCatching {
        val path = FabricLoader.getInstance().configDir.resolve("iris.properties")
        val properties = Properties()
        if (Files.isRegularFile(path)) {
            Files.newInputStream(path).use { properties.load(it) }
        }
        properties.setProperty("shaderPack", pack.orEmpty())
        properties.setProperty("enableShaders", if (pack == null) "false" else "true")
        Files.createDirectories(path.parent)
        Files.newOutputStream(path).use {
            properties.store(it, "Updated by resourcepackslib")
        }
        true
    }.getOrDefault(false)
}
