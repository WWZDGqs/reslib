package ws.reslib

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.util.Identifier
import org.lwjgl.glfw.GLFW
import org.slf4j.LoggerFactory
import ws.reslib.command.PresetCommand
import ws.reslib.preset.PresetManager
import ws.reslib.preset.PresetStore
import ws.reslib.ui.PresetScreen

object ResourcepacksLibClient : ClientModInitializer {
    private val logger = LoggerFactory.getLogger("resourcepackslib")

    private val category = runCatching {
        KeyBinding.Category.create(Identifier.of("resourcepackslib", "main"))
    }.getOrElse { KeyBinding.Category.MISC }

    private val openKey = KeyBinding(
        "key.resourcepackslib.open",
        InputUtil.Type.KEYSYM,
        GLFW.GLFW_KEY_RIGHT_BRACKET,
        category,
    )

    override fun onInitializeClient() {
        PresetManager.init()
        KeyBindingHelper.registerKeyBinding(openKey)
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (openKey.wasPressed()) {
                client.setScreen(PresetScreen(client.currentScreen))
            }
        }
        PresetCommand.register()
        logger.info("Preset manager ready, data file: {}", PresetStore.file)
    }
}
