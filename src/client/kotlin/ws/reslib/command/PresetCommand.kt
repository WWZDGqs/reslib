package ws.reslib.command

import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import ws.reslib.preset.PresetManager
import ws.reslib.ui.PresetScreen
import ws.reslib.util.Notify

object PresetCommand {
    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommandManager.literal("reslib")
                    .then(
                        ClientCommandManager.literal("list").executes { context ->
                            list(context.source)
                            1
                        }
                    )
                    .then(
                        ClientCommandManager.literal("open").executes {
                            val client = MinecraftClient.getInstance()
                            client.setScreen(PresetScreen(client.currentScreen))
                            1
                        }
                    )
                    .then(
                        ClientCommandManager.literal("save").then(
                            ClientCommandManager.argument("name", StringArgumentType.greedyString()).executes { context ->
                                save(context.source, StringArgumentType.getString(context, "name"))
                                1
                            }
                        )
                    )
                    .then(
                        ClientCommandManager.literal("apply").then(
                            ClientCommandManager.argument("name", StringArgumentType.greedyString()).executes { context ->
                                apply(context.source, StringArgumentType.getString(context, "name"))
                                1
                            }
                        )
                    )
                    .then(
                        ClientCommandManager.literal("delete").then(
                            ClientCommandManager.argument("name", StringArgumentType.greedyString()).executes { context ->
                                delete(context.source, StringArgumentType.getString(context, "name"))
                                1
                            }
                        )
                    )
                    .then(
                        ClientCommandManager.literal("rename").then(
                            ClientCommandManager.argument("old", StringArgumentType.string()).then(
                                ClientCommandManager.argument("new", StringArgumentType.string()).executes { context ->
                                    rename(
                                        context.source,
                                        StringArgumentType.getString(context, "old"),
                                        StringArgumentType.getString(context, "new"),
                                    )
                                    1
                                }
                            )
                        )
                    )
            )
        }
    }

    private fun list(source: FabricClientCommandSource) {
        val presets = PresetManager.presets()
        if (presets.isEmpty()) {
            source.sendFeedback(Text.translatable("command.resourcepackslib.empty"))
            return
        }
        source.sendFeedback(Text.translatable("command.resourcepackslib.list_header", presets.size))
        val lastApplied = PresetManager.lastAppliedId()
        presets.forEach { record ->
            val marker = if (record.id == lastApplied) "★ " else "  "
            source.sendFeedback(
                Text.literal("$marker${record.name}  [${record.shaderPack ?: "-"}]  材质 ${record.resourcePacks.size} · 配置 ${record.shaderOptions.size}")
            )
        }
    }

    private fun save(source: FabricClientCommandSource, name: String) {
        PresetManager.saveCurrent(name)
            .onSuccess { source.sendFeedback(Text.translatable("message.resourcepackslib.saved", it.name)) }
            .onFailure { source.sendError(Notify.error(it)) }
    }

    private fun apply(source: FabricClientCommandSource, name: String) {
        PresetManager.apply(name)
            .onSuccess { source.sendFeedback(Notify.applied(name, it)) }
            .onFailure { source.sendError(Notify.error(it)) }
    }

    private fun delete(source: FabricClientCommandSource, name: String) {
        if (PresetManager.delete(name)) {
            source.sendFeedback(Text.translatable("message.resourcepackslib.deleted", name))
        } else {
            source.sendError(Text.translatable("message.resourcepackslib.error.not_found"))
        }
    }

    private fun rename(source: FabricClientCommandSource, oldName: String, newName: String) {
        val record = PresetManager.find(oldName)
        if (record == null) {
            source.sendError(Text.translatable("message.resourcepackslib.error.not_found"))
            return
        }
        PresetManager.rename(record.id, newName)
            .onSuccess { source.sendFeedback(Text.translatable("message.resourcepackslib.renamed", oldName, newName)) }
            .onFailure { source.sendError(Notify.error(it)) }
    }
}
