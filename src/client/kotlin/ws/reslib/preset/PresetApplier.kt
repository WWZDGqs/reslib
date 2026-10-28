package ws.reslib.preset

import net.minecraft.client.MinecraftClient
import org.slf4j.LoggerFactory
import ws.reslib.compat.IrisBridge
import ws.reslib.compat.SodiumBridge

object PresetApplier {
    private val logger = LoggerFactory.getLogger("resourcepackslib")

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

    fun apply(record: PresetRecord): ApplyReport {
        val client = MinecraftClient.getInstance()
        val manager = client.resourcePackManager
        manager.scanPacks()

        val available = manager.profiles.mapTo(mutableSetOf()) { it.id }
        val wanted = record.resourcePacks.filter { available.contains(it) }
        val missing = record.resourcePacks.filterNot { available.contains(it) }
        if (missing.isNotEmpty()) {
            logger.warn("Skipped {} preset entries that no longer exist: {}", missing.size, missing)
        }

        val changed = manager.enabledIds.toList() != wanted
        if (changed) {
            manager.setEnabledProfiles(wanted)
            client.options.refreshResourcePacks(manager)
            client.options.write()
            client.reloadResources()
        }

        val pack = record.shaderPack
        val optionsWritten = pack != null && IrisBridge.packExists(pack) &&
            IrisBridge.writeOptions(pack, record.shaderOptions)
        val shader = IrisBridge.apply(pack)
        val sodium = SodiumBridge.writeConfig(record.sodiumOptions)
        return ApplyReport(
            enabledPacks = wanted.size,
            missingPacks = missing,
            shader = shader,
            shaderOptions = if (optionsWritten) record.shaderOptions.size else 0,
            sodium = sodium,
        )
    }
}
