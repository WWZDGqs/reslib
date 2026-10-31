package ws.reslib.compat

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen
import org.slf4j.LoggerFactory
import ws.reslib.preset.SodiumStatus
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object SodiumBridge {
    private val logger = LoggerFactory.getLogger("resourcepackslib")
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    private const val CONFIG_FILE = "sodium-options.json"
    private const val VIDEO_SETTINGS_SCREEN = "net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen"
    private const val VIDEO_OPTIONS_SCREEN = "net.caffeinemc.mods.sodium.client.gui.SodiumVideoOptionsScreen"
    private const val LEGACY_OPTIONS_GUI = "me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI"

    val available: Boolean by lazy {
        FabricLoader.getInstance().isModLoaded("sodium")
    }

    fun configPath(): Path = FabricLoader.getInstance().configDir.resolve(CONFIG_FILE)

    fun readConfig(): JsonObject? = runCatching {
        JsonParser.parseString(Files.readString(configPath())) as? JsonObject
    }.getOrNull()

    fun writeConfig(config: JsonObject?): SodiumStatus {
        if (!available || config == null) {
            return SodiumStatus.UNAVAILABLE
        }
        if (readConfig() == config) {
            return SodiumStatus.UNCHANGED
        }
        return runCatching {
            val path = configPath()
            Files.createDirectories(path.parent)
            val temp = path.resolveSibling("$CONFIG_FILE.tmp")
            Files.writeString(temp, gson.toJson(config))
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
            SodiumStatus.WRITTEN
        }.getOrElse { throwable ->
            logger.warn("Failed to write Sodium config", throwable)
            SodiumStatus.FAILED
        }
    }

    fun openVideoSettings(parent: Screen?): Boolean {
        return available && (
            openScreen(VIDEO_SETTINGS_SCREEN, parent)
                || openScreen(VIDEO_OPTIONS_SCREEN, parent)
                || openScreen(LEGACY_OPTIONS_GUI, parent)
            )
    }

    private fun openScreen(name: String, parent: Screen?): Boolean = runCatching {
        val clazz = Class.forName(name)
        val screen = runCatching { clazz.getMethod("createScreen", Screen::class.java).invoke(null, parent) }
            .getOrNull() ?: clazz.getConstructor(Screen::class.java).newInstance(parent)
        (screen as? Screen)?.also { MinecraftClient.getInstance().setScreen(it) } != null
    }.getOrElse { throwable ->
        logger.debug("Sodium screen {} unavailable: {}", name, throwable.message)
        false
    }
}